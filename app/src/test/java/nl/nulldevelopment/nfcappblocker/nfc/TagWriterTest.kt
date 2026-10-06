package nl.nulldevelopment.nfcappblocker.nfc

import nl.nulldevelopment.nfcappblocker.tagformat.NdefCodec
import nl.nulldevelopment.nfcappblocker.testing.FakeNdefTarget
import nl.nulldevelopment.nfcappblocker.tagformat.NdefRecord
import nl.nulldevelopment.nfcappblocker.tagformat.TagMessage
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TagWriterTest {

    private val uuid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
    private val messageSize = TagMessage.encode(uuid).size

    @Test
    fun `writes our message to a formatted tag and verifies it`() {
        val target = FakeNdefTarget()

        assertEquals(WriteResult.Written(uuid), TagWriter.write(target, uuid))
        assertArrayEquals(TagMessage.encode(uuid), target.stored)
        assertTrue(target.closed)
    }

    @Test
    fun `writes to a blank tag whose capacity is unknown`() {
        assertEquals(WriteResult.Written(uuid), TagWriter.write(FakeNdefTarget(maxSize = null), uuid))
    }

    @Test
    fun `a tag type without NDEF support is reported`() {
        assertEquals(WriteResult.NotNdefCapable, TagWriter.write(null, uuid))
    }

    @Test
    fun `a read-only tag is not touched`() {
        val target = FakeNdefTarget(isWritable = false)

        assertEquals(WriteResult.ReadOnly, TagWriter.write(target, uuid))
        assertFalse(target.connected)
        assertTrue(target.closed)
    }

    @Test
    fun `a tag that is too small is reported with both sizes`() {
        val target = FakeNdefTarget(maxSize = messageSize - 1)

        assertEquals(WriteResult.TooSmall(messageSize - 1, messageSize), TagWriter.write(target, uuid))
        assertNull(target.stored)
    }

    @Test
    fun `a tag of exactly the message size is written`() {
        assertEquals(WriteResult.Written(uuid), TagWriter.write(FakeNdefTarget(maxSize = messageSize), uuid))
    }

    @Test
    fun `losing the tag while connecting or writing is reported as tag lost`() {
        assertEquals(WriteResult.TagLost, TagWriter.write(FakeNdefTarget(connectError = NdefIoException.TagLost()), uuid))
        assertEquals(WriteResult.TagLost, TagWriter.write(FakeNdefTarget(writeError = NdefIoException.TagLost()), uuid))
    }

    @Test
    fun `other write errors carry their reason`() {
        val target = FakeNdefTarget(writeError = NdefIoException.Failed("Format failed"))

        assertEquals(WriteResult.Failed("Format failed"), TagWriter.write(target, uuid))
        assertTrue(target.closed)
    }

    @Test
    fun `a failing read back leaves the write unverified`() {
        val target = FakeNdefTarget(readError = NdefIoException.TagLost())

        assertEquals(WriteResult.WrittenUnverified(uuid, "Tag was lost"), TagWriter.write(target, uuid))
    }

    @Test
    fun `a tag that reads back different content fails verification`() {
        val other = NdefCodec.encode(listOf(NdefRecord.mime("text/plain", "old".toByteArray())))

        val result = TagWriter.write(FakeNdefTarget(readOverride = other), uuid)

        assertEquals(WriteResult.VerificationFailed(uuid, TagMessage.Result.Foreign), result)
    }

    @Test
    fun `a tag that reads back a different uuid fails verification`() {
        val otherUuid = UUID.randomUUID()

        val result = TagWriter.write(FakeNdefTarget(readOverride = TagMessage.encode(otherUuid)), uuid)

        assertEquals(WriteResult.VerificationFailed(uuid, TagMessage.Result.Ours(otherUuid)), result)
    }

    @Test
    fun `a tag that reads back empty fails verification`() {
        val target = object : NdefTarget by FakeNdefTarget() {
            override fun read(): ByteArray? = null
        }

        assertInstanceOf(WriteResult.VerificationFailed::class.java, TagWriter.write(target, uuid))
    }
}
