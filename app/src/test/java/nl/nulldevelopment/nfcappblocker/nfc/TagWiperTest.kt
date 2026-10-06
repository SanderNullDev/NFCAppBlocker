package nl.nulldevelopment.nfcappblocker.nfc

import nl.nulldevelopment.nfcappblocker.tagformat.TagMessage
import nl.nulldevelopment.nfcappblocker.testing.FakeNdefTarget
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TagWiperTest {

    private val uuid = UUID.fromString("33333333-3333-3333-3333-333333333333")

    private fun ours(target: FakeNdefTarget = FakeNdefTarget(stored = TagMessage.encode(uuid)), id: UUID = uuid) =
        ScannedTag("04:AA", TagContent.Ours(id), target.isWritable, target)

    @Test
    fun `wipes our tag to a single empty record and verifies it`() {
        val target = FakeNdefTarget(stored = TagMessage.encode(uuid))

        assertEquals(WipeResult.Wiped, TagWiper.wipe(ours(target), uuid))
        assertArrayEquals(byteArrayOf(0xD0.toByte(), 0x00, 0x00), target.stored)
        assertEquals(TagContent.Empty, TagContent.of(target.stored))
        assertTrue(target.closed)
    }

    @Test
    fun `refuses a tag with a different uuid or other content`() {
        val target = FakeNdefTarget()

        assertEquals(WipeResult.DifferentTag, TagWiper.wipe(ours(target, id = UUID.randomUUID()), uuid))
        assertEquals(WipeResult.DifferentTag, TagWiper.wipe(ScannedTag("04:AA", TagContent.Foreign, true, target), uuid))
        assertEquals(0, target.writeCount)
    }

    @Test
    fun `a read-only tag is not touched`() {
        val target = FakeNdefTarget(isWritable = false)

        assertEquals(WipeResult.ReadOnly, TagWiper.wipe(ours(target), uuid))
        assertEquals(0, target.writeCount)
    }

    @Test
    fun `losing the tag is reported`() {
        assertEquals(WipeResult.TagLost, TagWiper.wipe(ours(FakeNdefTarget(writeError = NdefIoException.TagLost())), uuid))
    }

    @Test
    fun `a tag that doesn't read back empty fails verification`() {
        val target = FakeNdefTarget(readOverride = TagMessage.encode(uuid))

        assertEquals(WipeResult.NotVerified, TagWiper.wipe(ours(target), uuid))
    }
}
