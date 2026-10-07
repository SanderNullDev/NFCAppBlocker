package nl.nulldevelopment.nfcappblocker.blocking

import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.nfc.TagContent
import nl.nulldevelopment.nfcappblocker.testing.FakeBlockingRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeProfileRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeTagRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ScanToggleTest {

    private val desk = UUID.fromString("00000000-0000-0000-0000-00000000000d")
    private val bed = UUID.fromString("00000000-0000-0000-0000-00000000000b")
    private val tags = FakeTagRepository()
    private val profiles = FakeProfileRepository(tags)
    private val blocking = FakeBlockingRepository(profiles)
    private var clock = 10_000L
    private val toggle = ScanToggle(tags, profiles, blocking, elapsedMillis = { clock })

    private suspend fun scan(uuid: UUID, uid: String = "04:AA") = toggle.onScan(TagContent.Ours(uuid), uid)

    private suspend fun pair(uuid: UUID, name: String, profileId: Long?) {
        tags.tags.value = tags.tags.value + PairedTag(uuid, name, null, Instant.EPOCH, profileId)
    }

    private fun activeNames() = blocking.active.value.map { (id, _) -> profiles.profiles.value.single { it.id == id }.name }

    @Nested
    inner class Toggling {
        @Test
        fun `a linked tag starts its profile when nothing is active`() = runTest {
            val focus = profiles.create("Focus", setOf("com.instagram.android"))
            pair(desk, "Desk", focus)

            val outcome = scan(desk)

            assertEquals("Focus", (outcome as ScanOutcome.Started).profile.name)
            assertEquals(listOf("Focus"), activeNames())
        }

        @Test
        fun `the same tag again later stops it`() = runTest {
            val focus = profiles.create("Focus", setOf("com.instagram.android"))
            pair(desk, "Desk", focus)
            scan(desk)

            clock += 5_000
            val outcome = scan(desk)

            assertEquals("Focus", (outcome as ScanOutcome.Stopped).profile.name)
            assertTrue(activeNames().isEmpty())
        }

        @Test
        fun `any tag of the active profile stops it`() = runTest {
            val focus = profiles.create("Focus", setOf("com.instagram.android"))
            pair(desk, "Desk", focus)
            pair(bed, "Bed", focus)
            scan(desk)

            val outcome = scan(bed)

            assertTrue(outcome is ScanOutcome.Stopped)
        }

        @Test
        fun `a tag of another profile switches to that profile`() = runTest {
            val focus = profiles.create("Focus", setOf("com.instagram.android"))
            val evening = profiles.create("Evening", setOf("com.android.chrome"))
            pair(desk, "Desk", focus)
            pair(bed, "Bed", evening)
            scan(desk)

            val outcome = scan(bed) as ScanOutcome.Switched

            assertEquals("Focus", outcome.from.name)
            assertEquals("Evening", outcome.to.name)
            assertEquals(listOf("Evening"), activeNames())
        }

        @Test
        fun `a profile without apps isn't started and nothing changes`() = runTest {
            val focus = profiles.create("Focus", setOf("com.instagram.android"))
            val empty = profiles.create("Empty", emptySet())
            pair(desk, "Desk", focus)
            pair(bed, "Bed", empty)
            scan(desk)

            val outcome = scan(bed)

            assertEquals("Empty", (outcome as ScanOutcome.ProfileHasNoApps).profile.name)
            assertEquals(listOf("Focus"), activeNames())
        }
    }

    @Nested
    inner class TagsThatCantToggle {
        @Test
        fun `a paired tag without a profile is reported`() = runTest {
            pair(desk, "Desk", profileId = null)

            assertEquals("Desk", (scan(desk) as ScanOutcome.TagNotLinked).tag.name)
            assertTrue(activeNames().isEmpty())
        }

        @Test
        fun `a tag of ours that isn't stored is reported with its uuid`() = runTest {
            assertEquals(ScanOutcome.UnknownTagOfOurs(desk), scan(desk))
        }

        @Test
        fun `blank, foreign and unreadable tags aren't ours`() = runTest {
            assertEquals(ScanOutcome.NotOurTag(TagContent.Empty), toggle.onScan(TagContent.Empty, "04:01"))
            assertEquals(ScanOutcome.NotOurTag(TagContent.Foreign), toggle.onScan(TagContent.Foreign, "04:02"))
            assertEquals(ScanOutcome.NotOurTag(TagContent.Unreadable), toggle.onScan(TagContent.Unreadable, "04:03"))
        }
    }

    @Nested
    inner class Debounce {
        @Test
        fun `the same tag within two seconds is ignored, so one tap can't start and stop`() = runTest {
            val focus = profiles.create("Focus", setOf("com.instagram.android"))
            pair(desk, "Desk", focus)
            scan(desk)

            clock += 1_999
            assertEquals(ScanOutcome.Ignored, scan(desk))
            assertEquals(listOf("Focus"), activeNames())

            clock += 1
            assertTrue(scan(desk) is ScanOutcome.Stopped)
        }

        @Test
        fun `a different tag right away is not ignored`() = runTest {
            val focus = profiles.create("Focus", setOf("com.instagram.android"))
            val evening = profiles.create("Evening", setOf("com.android.chrome"))
            pair(desk, "Desk", focus)
            pair(bed, "Bed", evening)
            scan(desk)

            assertTrue(scan(bed) is ScanOutcome.Switched)
        }

        @Test
        fun `our tag is recognised by its uuid even when the hardware uid changes`() = runTest {
            val focus = profiles.create("Focus", setOf("com.instagram.android"))
            pair(desk, "Desk", focus)
            scan(desk, uid = "08:11:22:33")

            // HCE gives a random UID on every tap (D-011); it's still the same tag.
            assertEquals(ScanOutcome.Ignored, scan(desk, uid = "08:44:55:66"))
        }

        @Test
        fun `other tags are told apart by hardware uid`() = runTest {
            toggle.onScan(TagContent.Foreign, "04:01")

            assertEquals(ScanOutcome.Ignored, toggle.onScan(TagContent.Foreign, "04:01"))
            assertEquals(ScanOutcome.NotOurTag(TagContent.Foreign), toggle.onScan(TagContent.Foreign, "04:02"))
        }
    }
}
