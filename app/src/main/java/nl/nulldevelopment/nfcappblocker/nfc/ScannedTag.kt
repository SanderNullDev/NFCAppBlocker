package nl.nulldevelopment.nfcappblocker.nfc

import nl.nulldevelopment.nfcappblocker.tagformat.NdefCodec
import nl.nulldevelopment.nfcappblocker.tagformat.NdefFormatException
import nl.nulldevelopment.nfcappblocker.tagformat.NdefRecord
import nl.nulldevelopment.nfcappblocker.tagformat.TagMessage
import java.util.UUID

/** What a tag holds, as far as pairing cares. */
sealed interface TagContent {
    /** No NDEF message, or only empty records: safe to write without asking. */
    data object Empty : TagContent
    data class Ours(val uuid: UUID) : TagContent
    data class UnsupportedVersion(val version: Int) : TagContent
    data object Foreign : TagContent
    data object Unreadable : TagContent

    companion object {
        /** @param ndefMessage the raw NDEF message, or null when the tag holds none. */
        fun of(ndefMessage: ByteArray?): TagContent {
            if (ndefMessage == null) return Empty
            val records = try {
                NdefCodec.decode(ndefMessage)
            } catch (_: NdefFormatException) {
                return Unreadable
            }
            if (records.all { it.tnf == NdefRecord.Tnf.EMPTY }) return Empty
            return when (val result = TagMessage.parse(records)) {
                is TagMessage.Result.Ours -> Ours(result.uuid)
                is TagMessage.Result.UnsupportedVersion -> UnsupportedVersion(result.version)
                TagMessage.Result.Foreign -> Foreign
                is TagMessage.Result.Malformed -> Unreadable
            }
        }
    }
}

/**
 * A tag held against the phone, reduced to what pairing needs. [target] writes to it while it's
 * still in range; null when the tag type can't hold NDEF.
 */
class ScannedTag(
    val hardwareUid: String,
    val content: TagContent,
    val isWritable: Boolean,
    val target: NdefTarget?,
)
