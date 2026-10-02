package nl.nulldevelopment.nfcappblocker.tagformat

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.util.UUID

class TagPayloadTest {

    private val uuid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")

    @Test
    fun `encodes version byte followed by the uuid big endian`() {
        val expected = byteArrayOf(0x01) + (0..15).map { (it * 0x11).toByte() }.toByteArray()

        assertArrayEquals(expected, TagPayload.encode(uuid))
    }

    @Test
    fun `round trips random uuids`() {
        repeat(100) {
            val random = UUID.randomUUID()
            assertEquals(TagPayload.Result.Valid(random), TagPayload.parse(TagPayload.encode(random)))
        }
    }

    @Test
    fun `reports an unknown version even when the length differs`() {
        val payload = byteArrayOf(0x02, 0x00, 0x00)

        assertEquals(TagPayload.Result.UnsupportedVersion(2), TagPayload.parse(payload))
    }

    @Test
    fun `reports version bytes above 127 as unsigned`() {
        assertEquals(TagPayload.Result.UnsupportedVersion(0xFF), TagPayload.parse(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun `empty payload is malformed`() {
        assertInstanceOf(TagPayload.Result.Malformed::class.java, TagPayload.parse(ByteArray(0)))
    }

    @Test
    fun `truncated and oversized payloads are malformed`() {
        val valid = TagPayload.encode(uuid)

        assertInstanceOf(TagPayload.Result.Malformed::class.java, TagPayload.parse(valid.copyOf(16)))
        assertInstanceOf(TagPayload.Result.Malformed::class.java, TagPayload.parse(valid + 0x00))
    }
}
