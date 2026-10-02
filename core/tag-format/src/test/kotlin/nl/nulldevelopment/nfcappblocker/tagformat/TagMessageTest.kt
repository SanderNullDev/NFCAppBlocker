package nl.nulldevelopment.nfcappblocker.tagformat

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.util.UUID

class TagMessageTest {

    private val uuid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")

    @Test
    fun `encodes the exact bytes of D-008 with the mime record first and the aar last`() {
        val mimeType = "application/vnd.nulldev.nfcblocker".toByteArray(Charsets.US_ASCII)
        val aarType = "android.com:pkg".toByteArray(Charsets.US_ASCII)
        val aarPayload = "nl.nulldevelopment.nfcappblocker".toByteArray(Charsets.US_ASCII)
        val uuidPayload = byteArrayOf(0x01) + (0..15).map { (it * 0x11).toByte() }.toByteArray()

        val expected =
            byteArrayOf(0x92.toByte(), 0x22, 0x11) + mimeType + uuidPayload +
                byteArrayOf(0x54, 0x0F, 0x20) + aarType + aarPayload

        assertArrayEquals(expected, TagMessage.encode(uuid))
    }

    @Test
    fun `parses its own encoding`() {
        assertEquals(TagMessage.Result.Ours(uuid), TagMessage.parse(TagMessage.encode(uuid)))
    }

    @Test
    fun `finds our record when it isn't the first one`() {
        val records = listOf(NdefRecord.mime("text/plain", "hi".toByteArray())) + TagMessage.build(uuid)

        assertEquals(TagMessage.Result.Ours(uuid), TagMessage.parse(NdefCodec.encode(records)))
    }

    @Test
    fun `matches the mime type case-insensitively`() {
        val records = listOf(NdefRecord.mime("Application/VND.nulldev.NFCblocker", TagPayload.encode(uuid)))

        assertEquals(TagMessage.Result.Ours(uuid), TagMessage.parse(records))
    }

    @Test
    fun `a message without our mime record is foreign`() {
        val records = listOf(
            NdefRecord(NdefRecord.Tnf.WELL_KNOWN, "U".toByteArray(), byteArrayOf(0x04) + "example.com".toByteArray()),
            NdefRecord.androidApplicationRecord(TagFormat.AAR_PACKAGE),
        )

        assertEquals(TagMessage.Result.Foreign, TagMessage.parse(NdefCodec.encode(records)))
    }

    @Test
    fun `our mime type under a different tnf is foreign`() {
        val record = NdefRecord(NdefRecord.Tnf.EXTERNAL_TYPE, TagFormat.MIME_TYPE.toByteArray(), TagPayload.encode(uuid))

        assertEquals(TagMessage.Result.Foreign, TagMessage.parse(listOf(record)))
    }

    @Test
    fun `a newer payload version is reported as unsupported`() {
        val records = listOf(NdefRecord.mime(TagFormat.MIME_TYPE, byteArrayOf(0x02) + ByteArray(20)))

        assertEquals(TagMessage.Result.UnsupportedVersion(2), TagMessage.parse(records))
    }

    @Test
    fun `invalid ndef bytes are malformed`() {
        assertInstanceOf(TagMessage.Result.Malformed::class.java, TagMessage.parse(byteArrayOf(0x00, 0x01)))
    }

    @Test
    fun `our record with a truncated payload is malformed`() {
        val records = listOf(NdefRecord.mime(TagFormat.MIME_TYPE, byteArrayOf(0x01, 0x02)))

        assertInstanceOf(TagMessage.Result.Malformed::class.java, TagMessage.parse(records))
    }
}
