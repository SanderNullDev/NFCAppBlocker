package nl.nulldevelopment.nfcappblocker.feature.pairing

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.nfc.NdefIoException
import nl.nulldevelopment.nfcappblocker.nfc.ScannedTag
import nl.nulldevelopment.nfcappblocker.nfc.TagContent
import nl.nulldevelopment.nfcappblocker.tagformat.NdefCodec
import nl.nulldevelopment.nfcappblocker.tagformat.NdefRecord
import nl.nulldevelopment.nfcappblocker.tagformat.TagMessage
import nl.nulldevelopment.nfcappblocker.testing.FakeNdefTarget
import nl.nulldevelopment.nfcappblocker.testing.FakeTagRepository
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.CoroutineContext

class PairingFlowTest {

    private val newUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val existingUuid = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val repository = FakeTagRepository()

    private fun TestScope.flow() = PairingFlow(
        repository = repository,
        defaultName = { "Tag $it" },
        ioDispatcher = StandardTestDispatcher(testScheduler),
        newUuid = { newUuid },
    )

    private fun scanned(
        content: TagContent,
        uid: String = "04:AA",
        target: FakeNdefTarget? = FakeNdefTarget(),
        writable: Boolean = target?.isWritable ?: false,
    ) = ScannedTag(uid, content, writable, target)

    private fun paired(uuid: UUID, name: String) = PairedTag(uuid, name, null, Instant.EPOCH)

    @Nested
    inner class BlankTags {
        @Test
        fun `a blank tag is written right away and stored with its hardware uid`() = runTest {
            val flow = flow()
            val target = FakeNdefTarget()

            flow.onTagScanned(scanned(TagContent.Empty, uid = "04:1D", target = target))

            val state = flow.state.value as PairingState.Paired
            assertEquals(newUuid, state.tag.uuid)
            assertEquals("Tag 1", state.tag.name)
            assertEquals("04:1D", state.tag.hardwareUid)
            assertArrayEquals(TagMessage.encode(newUuid), target.stored)
            assertEquals(listOf(state.tag), repository.tags.value)
        }

        @Test
        fun `shows writing while the write is in progress`() = runTest {
            val flow = flow()
            flow.state.test {
                assertEquals(PairingState.WaitingForTag, awaitItem())
                flow.onTagScanned(scanned(TagContent.Empty))
                assertEquals(PairingState.Writing, awaitItem())
                assertTrue(awaitItem() is PairingState.Paired)
            }
        }

        @Test
        fun `the default name is the first unused number`() = runTest {
            repository.tags.value = listOf(
                paired(UUID.randomUUID(), "Tag 1"),
                paired(UUID.randomUUID(), "Tag 3"),
            )
            val flow = flow()

            flow.onTagScanned(scanned(TagContent.Empty))

            assertEquals("Tag 2", (flow.state.value as PairingState.Paired).tag.name)
        }
    }

    @Nested
    inner class TagsWithOtherContent {
        @Test
        fun `asks before overwriting and writes nothing yet`() = runTest {
            val flow = flow()
            val target = FakeNdefTarget()

            flow.onTagScanned(scanned(TagContent.Foreign, target = target))

            assertEquals(PairingState.ConfirmOverwrite(TagContent.Foreign), flow.state.value)
            assertEquals(0, target.writeCount)
        }

        @Test
        fun `after confirming, the same tag held again is overwritten`() = runTest {
            val flow = flow()
            val target = FakeNdefTarget()
            flow.onTagScanned(scanned(TagContent.Foreign, uid = "04:AA", target = target))

            flow.confirmOverwrite()
            assertEquals(PairingState.WaitingForSameTag(), flow.state.value)

            flow.onTagScanned(scanned(TagContent.Foreign, uid = "04:AA", target = target))
            assertTrue(flow.state.value is PairingState.Paired)
            assertEquals(1, target.writeCount)
        }

        @Test
        fun `a different tag held after confirming is not overwritten`() = runTest {
            val flow = flow()
            flow.onTagScanned(scanned(TagContent.Foreign, uid = "04:AA"))
            flow.confirmOverwrite()
            val other = FakeNdefTarget()

            flow.onTagScanned(scanned(TagContent.Foreign, uid = "04:BB", target = other))

            assertEquals(PairingState.WaitingForSameTag(otherTagTapped = true), flow.state.value)
            assertEquals(0, other.writeCount)
        }

        @Test
        fun `unreadable and newer-version content also need confirmation`() = runTest {
            val flow = flow()

            flow.onTagScanned(scanned(TagContent.Unreadable))
            assertEquals(PairingState.ConfirmOverwrite(TagContent.Unreadable), flow.state.value)

            flow.onTagScanned(scanned(TagContent.UnsupportedVersion(2)))
            assertEquals(PairingState.ConfirmOverwrite(TagContent.UnsupportedVersion(2)), flow.state.value)
        }

        @Test
        fun `a read-only tag fails without asking`() = runTest {
            val flow = flow()

            flow.onTagScanned(scanned(TagContent.Foreign, target = FakeNdefTarget(isWritable = false)))

            assertEquals(PairingState.Failed(PairingFailure.ReadOnly), flow.state.value)
        }

        @Test
        fun `a tag that can't hold NDEF fails`() = runTest {
            val flow = flow()

            flow.onTagScanned(scanned(TagContent.Empty, target = null))

            assertEquals(PairingState.Failed(PairingFailure.NotNdefCapable), flow.state.value)
        }

        @Test
        fun `confirming without a pending question does nothing`() = runTest {
            val flow = flow()

            flow.confirmOverwrite()

            assertEquals(PairingState.WaitingForTag, flow.state.value)
        }
    }

    @Nested
    inner class OurTags {
        @Test
        fun `a tag that is already stored is recognised`() = runTest {
            val existing = paired(existingUuid, "Desk")
            repository.tags.value = listOf(existing)
            val flow = flow()

            flow.onTagScanned(scanned(TagContent.Ours(existingUuid)))

            assertEquals(PairingState.AlreadyPaired(existing), flow.state.value)
        }

        @Test
        fun `an unknown tag of ours can be adopted without writing`() = runTest {
            val flow = flow()
            val target = FakeNdefTarget()
            flow.onTagScanned(scanned(TagContent.Ours(existingUuid), uid = "04:CC", target = target))
            assertEquals(PairingState.OursNotPaired(existingUuid, canOverwrite = true), flow.state.value)

            flow.adopt()

            val tag = (flow.state.value as PairingState.Paired).tag
            assertEquals(existingUuid, tag.uuid)
            assertEquals("04:CC", tag.hardwareUid)
            assertEquals(0, target.writeCount)
        }

        @Test
        fun `a read-only tag of ours can be adopted but not overwritten`() = runTest {
            val flow = flow()
            flow.onTagScanned(scanned(TagContent.Ours(existingUuid), target = FakeNdefTarget(isWritable = false)))
            assertEquals(PairingState.OursNotPaired(existingUuid, canOverwrite = false), flow.state.value)

            flow.confirmOverwrite()
            assertEquals(PairingState.OursNotPaired(existingUuid, canOverwrite = false), flow.state.value)

            flow.adopt()
            assertTrue(flow.state.value is PairingState.Paired)
        }

        @Test
        fun `an unknown tag of ours can be given a new uuid instead`() = runTest {
            val flow = flow()
            val target = FakeNdefTarget()
            flow.onTagScanned(scanned(TagContent.Ours(existingUuid), uid = "04:CC", target = target))

            flow.confirmOverwrite()
            flow.onTagScanned(scanned(TagContent.Ours(existingUuid), uid = "04:CC", target = target))

            assertEquals(newUuid, (flow.state.value as PairingState.Paired).tag.uuid)
        }
    }

    @Nested
    inner class Failures {
        @Test
        fun `a tag moving away during the write is reported and nothing is stored`() = runTest {
            val flow = flow()

            flow.onTagScanned(scanned(TagContent.Empty, target = FakeNdefTarget(writeError = NdefIoException.TagLost())))

            assertEquals(PairingState.Failed(PairingFailure.TagLost), flow.state.value)
            assertTrue(repository.tags.value.isEmpty())
        }

        @Test
        fun `a write that doesn't read back correctly is not stored`() = runTest {
            val flow = flow()
            val other = NdefCodec.encode(listOf(NdefRecord.mime("text/plain", "x".toByteArray())))

            flow.onTagScanned(scanned(TagContent.Empty, target = FakeNdefTarget(readOverride = other)))

            assertEquals(PairingState.Failed(PairingFailure.NotVerified), flow.state.value)
            assertTrue(repository.tags.value.isEmpty())
        }

        @Test
        fun `a tag that turns out too small is reported with both sizes`() = runTest {
            val flow = flow()

            flow.onTagScanned(scanned(TagContent.Empty, target = FakeNdefTarget(maxSize = 48)))

            val needed = TagMessage.encode(newUuid).size
            assertEquals(PairingState.Failed(PairingFailure.TooSmall(48, needed)), flow.state.value)
        }

        @Test
        fun `restart goes back to waiting and forgets a confirmed overwrite`() = runTest {
            val flow = flow()
            val target = FakeNdefTarget()
            flow.onTagScanned(scanned(TagContent.Foreign, uid = "04:AA", target = target))
            flow.confirmOverwrite()

            flow.restart()
            flow.onTagScanned(scanned(TagContent.Foreign, uid = "04:AA", target = target))

            assertEquals(PairingState.ConfirmOverwrite(TagContent.Foreign), flow.state.value)
            assertEquals(0, target.writeCount)
        }

        @Test
        fun `a new tap after a result starts over with that tag`() = runTest {
            val flow = flow()
            flow.onTagScanned(scanned(TagContent.Foreign, target = FakeNdefTarget(isWritable = false)))

            flow.onTagScanned(scanned(TagContent.Empty))

            assertTrue(flow.state.value is PairingState.Paired)
        }
    }

    @Nested
    inner class ContentClassification {
        @Test
        fun `no message or only empty records count as empty`() {
            assertEquals(TagContent.Empty, TagContent.of(null))
            val emptyRecord = NdefCodec.encode(listOf(NdefRecord(NdefRecord.Tnf.EMPTY, ByteArray(0), ByteArray(0))))
            assertEquals(TagContent.Empty, TagContent.of(emptyRecord))
        }

        @Test
        fun `our message, a newer version, foreign content and garbage are told apart`() {
            assertEquals(TagContent.Ours(newUuid), TagContent.of(TagMessage.encode(newUuid)))
            val newer = NdefCodec.encode(listOf(NdefRecord.mime(TagMessage.build(newUuid)[0].typeString, byteArrayOf(2))))
            assertEquals(TagContent.UnsupportedVersion(2), TagContent.of(newer))
            val foreign = NdefCodec.encode(listOf(NdefRecord.mime("text/plain", "hi".toByteArray())))
            assertEquals(TagContent.Foreign, TagContent.of(foreign))
            assertEquals(TagContent.Unreadable, TagContent.of(byteArrayOf(0x01)))
        }
    }

    @Test
    fun `a scan arriving during a write is dropped, not queued`() = runTest {
        // The write's I/O is queued on a dispatcher that only runs when the test releases it.
        val io = HeldDispatcher()
        val flow = PairingFlow(repository, { "Tag $it" }, io, { newUuid })
        val first = FakeNdefTarget()
        val second = FakeNdefTarget()

        val writing = launch { flow.onTagScanned(scanned(TagContent.Empty, uid = "04:01", target = first)) }
        try {
            runCurrent()
            assertEquals(PairingState.Writing, flow.state.value)

            // Must return straight away. If it queued behind the write, it would still be waiting here.
            val secondScan = launch { flow.onTagScanned(scanned(TagContent.Empty, uid = "04:02", target = second)) }
            runCurrent()
            assertTrue(secondScan.isCompleted, "second scan should be dropped, not queued")
        } finally {
            // Lets the held write finish, so a failed assertion ends the test instead of hanging it.
            io.release()
        }
        writing.join()

        assertTrue(flow.state.value is PairingState.Paired)
        assertEquals(1, first.writeCount)
        assertFalse(second.connected)
        assertEquals(1, repository.tags.value.size)
    }

    private class HeldDispatcher : CoroutineDispatcher() {
        private val queued = mutableListOf<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queued += block
        }

        fun release() {
            while (queued.isNotEmpty()) queued.removeAt(0).run()
        }
    }
}
