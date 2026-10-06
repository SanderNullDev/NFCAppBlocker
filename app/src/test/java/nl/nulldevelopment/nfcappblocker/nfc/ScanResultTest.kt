package nl.nulldevelopment.nfcappblocker.nfc

import nl.nulldevelopment.nfcappblocker.tagformat.NdefCodec
import nl.nulldevelopment.nfcappblocker.tagformat.NdefRecord
import nl.nulldevelopment.nfcappblocker.tagformat.TagMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ScanResultTest {

    private fun scan(
        ndefMessage: ByteArray?,
        uid: ByteArray = byteArrayOf(0x08, 0x1A, 0xFF.toByte(), 0x00),
        techs: List<String> = listOf("android.nfc.tech.IsoDep", "android.nfc.tech.Ndef"),
        processAgeMillis: Long = 60_000,
    ) = ScanResult.create(
        id = 1,
        timeMillis = 0,
        source = ScanSource.READER_MODE,
        ndefMessage = ndefMessage,
        uid = uid,
        techClassNames = techs,
        processAgeMillis = processAgeMillis,
    )

    @Test
    fun `our tag yields its uuid`() {
        val uuid = UUID.randomUUID()

        assertEquals(TagMessage.Result.Ours(uuid), scan(TagMessage.encode(uuid)).message)
    }

    @Test
    fun `a tag without an NDEF message has no parsed message`() {
        assertNull(scan(ndefMessage = null).message)
    }

    @Test
    fun `a foreign NDEF message is reported as foreign`() {
        val foreign = NdefCodec.encode(listOf(NdefRecord.mime("text/plain", "hi".toByteArray())))

        assertEquals(TagMessage.Result.Foreign, scan(foreign).message)
    }

    @Test
    fun `broken bytes are reported as malformed`() {
        assertInstanceOf(TagMessage.Result.Malformed::class.java, scan(byteArrayOf(0x01)).message)
    }

    @Test
    fun `uid is formatted as colon separated upper case hex`() {
        assertEquals("08:1A:FF:00", scan(ndefMessage = null).uidHex)
    }

    @Test
    fun `tech class names are shortened to their simple name`() {
        assertEquals(listOf("IsoDep", "Ndef"), scan(ndefMessage = null).techs)
    }

    @Test
    fun `a young process counts as a cold start`() {
        assertTrue(scan(ndefMessage = null, processAgeMillis = 450).isLikelyColdStart)
        assertFalse(
            scan(ndefMessage = null, processAgeMillis = ScanResult.COLD_START_THRESHOLD_MILLIS).isLikelyColdStart,
        )
    }
}
