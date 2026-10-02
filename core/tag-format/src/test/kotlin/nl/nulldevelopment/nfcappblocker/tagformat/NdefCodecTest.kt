package nl.nulldevelopment.nfcappblocker.tagformat

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NdefCodecTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `encodes a single short record with MB, ME and SR set`() {
        val record = NdefRecord(NdefRecord.Tnf.MIME_MEDIA, "a/b".toByteArray(), bytes(0x01, 0x02))

        val encoded = NdefCodec.encode(listOf(record))

        assertArrayEquals(
            bytes(0xD2, 0x03, 0x02, 'a'.code, '/'.code, 'b'.code, 0x01, 0x02),
            encoded,
        )
    }

    @Test
    fun `encodes a long payload with a four byte length`() {
        val payload = ByteArray(300) { it.toByte() }
        val record = NdefRecord(NdefRecord.Tnf.UNKNOWN, ByteArray(0), payload)

        val encoded = NdefCodec.encode(listOf(record))

        assertArrayEquals(bytes(0xC5, 0x00, 0x00, 0x00, 0x01, 0x2C), encoded.copyOfRange(0, 6))
        assertEquals(6 + 300, encoded.size)
        assertEquals(listOf(record), NdefCodec.decode(encoded))
    }

    @Test
    fun `round trips multiple records including an id`() {
        val records = listOf(
            NdefRecord(NdefRecord.Tnf.WELL_KNOWN, "T".toByteArray(), "hello".toByteArray(), id = "id1".toByteArray()),
            NdefRecord.mime("text/plain", ByteArray(0)),
            NdefRecord.androidApplicationRecord("com.example"),
        )

        assertEquals(records, NdefCodec.decode(NdefCodec.encode(records)))
    }

    @Test
    fun `encoding an empty message is rejected`() {
        assertThrows<IllegalArgumentException> { NdefCodec.encode(emptyList()) }
    }

    @Test
    fun `decoding empty input fails`() {
        assertThrows<NdefFormatException> { NdefCodec.decode(ByteArray(0)) }
    }

    @Test
    fun `decoding fails when the first record lacks the message begin flag`() {
        assertThrows<NdefFormatException> { NdefCodec.decode(bytes(0x52, 0x01, 0x00, 'a'.code)) }
    }

    @Test
    fun `decoding fails when a later record has the message begin flag`() {
        val twoFirstRecords = bytes(0x92, 0x01, 0x00, 'a'.code, 0xD2, 0x01, 0x00, 'b'.code)

        assertThrows<NdefFormatException> { NdefCodec.decode(twoFirstRecords) }
    }

    @Test
    fun `decoding fails when the message end flag is missing`() {
        assertThrows<NdefFormatException> { NdefCodec.decode(bytes(0x92, 0x01, 0x00, 'a'.code)) }
    }

    @Test
    fun `decoding fails on truncated payload`() {
        assertThrows<NdefFormatException> { NdefCodec.decode(bytes(0xD2, 0x01, 0x05, 'a'.code, 0x01)) }
    }

    @Test
    fun `decoding fails on a payload length larger than the input`() {
        assertThrows<NdefFormatException> { NdefCodec.decode(bytes(0xC2, 0x00, 0x7F, 0xFF, 0xFF, 0xFF)) }
    }

    @Test
    fun `decoding fails on trailing bytes after the last record`() {
        assertThrows<NdefFormatException> { NdefCodec.decode(bytes(0xD2, 0x01, 0x00, 'a'.code, 0x00)) }
    }

    @Test
    fun `decoding rejects chunked records`() {
        assertThrows<NdefFormatException> { NdefCodec.decode(bytes(0xB2, 0x01, 0x00, 'a'.code)) }
    }
}
