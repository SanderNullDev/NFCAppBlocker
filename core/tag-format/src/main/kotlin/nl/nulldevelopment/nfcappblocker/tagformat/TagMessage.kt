package nl.nulldevelopment.nfcappblocker.tagformat

import java.util.UUID

/**
 * Builds and recognises the NDEF message the app writes to a tag (D-008, D-013).
 *
 * The MIME record comes first because Android matches intent filters against the first
 * record; the Android Application Record comes last, as Android recommends.
 */
object TagMessage {

    fun build(uuid: UUID): List<NdefRecord> = listOf(
        NdefRecord.mime(TagFormat.MIME_TYPE, TagPayload.encode(uuid)),
        NdefRecord.androidApplicationRecord(TagFormat.AAR_PACKAGE),
    )

    fun encode(uuid: UUID): ByteArray = NdefCodec.encode(build(uuid))

    /** Parses a raw NDEF message, e.g. the content of a Type 4 NDEF file or `NdefMessage.toByteArray()`. */
    fun parse(bytes: ByteArray): Result {
        val records = try {
            NdefCodec.decode(bytes)
        } catch (e: NdefFormatException) {
            return Result.Malformed(e.message ?: "Invalid NDEF message")
        }
        return parse(records)
    }

    /** Looks for the app's MIME record anywhere in [records]; other records are ignored. */
    fun parse(records: List<NdefRecord>): Result {
        val record = records.firstOrNull { it.isAppRecord() } ?: return Result.Foreign
        return when (val payload = TagPayload.parse(record.payload)) {
            is TagPayload.Result.Valid -> Result.Ours(payload.uuid)
            is TagPayload.Result.UnsupportedVersion -> Result.UnsupportedVersion(payload.version)
            is TagPayload.Result.Malformed -> Result.Malformed(payload.reason)
        }
    }

    // MIME types are case-insensitive (RFC 2045).
    private fun NdefRecord.isAppRecord(): Boolean =
        tnf == NdefRecord.Tnf.MIME_MEDIA && typeString.equals(TagFormat.MIME_TYPE, ignoreCase = true)

    sealed interface Result {
        /** A tag written by this app, carrying [uuid]. */
        data class Ours(val uuid: UUID) : Result

        /** Our MIME record, but written by a newer payload format this version can't read. */
        data class UnsupportedVersion(val version: Int) : Result

        /** Valid NDEF without our MIME record. */
        data object Foreign : Result

        /** Bytes that aren't valid NDEF, or our record with a broken payload. */
        data class Malformed(val reason: String) : Result
    }
}
