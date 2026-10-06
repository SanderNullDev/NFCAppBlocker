package nl.nulldevelopment.nfcappblocker.nfc

import nl.nulldevelopment.nfcappblocker.tagformat.TagMessage

/** How a scan reached the app. */
enum class ScanSource {
    /** Android's tag dispatch started [nl.nulldevelopment.nfcappblocker.ScanActivity]. */
    DISPATCH_LAUNCH,

    /** Tag dispatch delivered to an already open ScanActivity via onNewIntent. */
    DISPATCH_NEW_INTENT,

    /** Foreground scanning with NfcAdapter.enableReaderMode while MainActivity was resumed. */
    READER_MODE,
}

/**
 * One scanned tag, reduced to what the spike needs to show (spike B).
 *
 * @param message the parsed NDEF message, or null when the tag had no NDEF message at all.
 * @param uidHex the hardware UID. Random on every tap with HCE (D-011).
 * @param processAgeMillis how long the app process had been running when the scan arrived.
 * @param write the outcome when this scan was used to write the tag. [message] is then the
 *   content the tag held before writing.
 */
data class ScanResult(
    val id: Long,
    val timeMillis: Long,
    val source: ScanSource,
    val message: TagMessage.Result?,
    val uidHex: String,
    val techs: List<String>,
    val processAgeMillis: Long,
    val write: WriteResult? = null,
) {
    /** True when the scan itself most likely started the process, i.e. a cold launch. */
    val isLikelyColdStart: Boolean get() = processAgeMillis < COLD_START_THRESHOLD_MILLIS

    companion object {
        const val COLD_START_THRESHOLD_MILLIS = 3_000L

        fun create(
            id: Long,
            timeMillis: Long,
            source: ScanSource,
            ndefMessage: ByteArray?,
            uid: ByteArray,
            techClassNames: List<String>,
            processAgeMillis: Long,
            write: WriteResult? = null,
        ) = ScanResult(
            id = id,
            timeMillis = timeMillis,
            source = source,
            message = ndefMessage?.let(TagMessage::parse),
            uidHex = uid.joinToString(":") { "%02X".format(it.toInt() and 0xFF) },
            techs = techClassNames.map { it.substringAfterLast('.') },
            processAgeMillis = processAgeMillis,
            write = write,
        )
    }
}
