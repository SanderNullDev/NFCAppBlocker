package nl.nulldevelopment.nfcappblocker.nfc

import nl.nulldevelopment.nfcappblocker.tagformat.NdefCodec
import nl.nulldevelopment.nfcappblocker.tagformat.NdefRecord
import java.util.UUID

sealed interface WipeResult {
    data object Wiped : WipeResult

    /** The tag held isn't the one being wiped; nothing was written. */
    data object DifferentTag : WipeResult
    data object ReadOnly : WipeResult
    data object TagLost : WipeResult

    /** The write call returned, but the tag doesn't read back as empty. */
    data object NotVerified : WipeResult
    data class Failed(val reason: String) : WipeResult
}

/** Clears one of our tags by writing a single empty NDEF record. Blocking I/O. */
object TagWiper {
    /** A message with one empty record: the standard way to leave an NDEF tag blank. */
    val EMPTY_MESSAGE: ByteArray = NdefCodec.encode(listOf(NdefRecord(NdefRecord.Tnf.EMPTY, ByteArray(0), ByteArray(0))))

    fun wipe(tag: ScannedTag, expectedUuid: UUID): WipeResult {
        if (tag.content != TagContent.Ours(expectedUuid)) return WipeResult.DifferentTag
        val target = tag.target ?: return WipeResult.Failed("Tag doesn't support NDEF")
        target.use {
            if (!target.isWritable) return WipeResult.ReadOnly
            try {
                target.connect()
                target.write(EMPTY_MESSAGE)
                val readBack = target.read()
                return if (TagContent.of(readBack) == TagContent.Empty) WipeResult.Wiped else WipeResult.NotVerified
            } catch (_: NdefIoException.TagLost) {
                return WipeResult.TagLost
            } catch (e: NdefIoException.Failed) {
                return WipeResult.Failed(e.message.orEmpty())
            }
        }
    }
}
