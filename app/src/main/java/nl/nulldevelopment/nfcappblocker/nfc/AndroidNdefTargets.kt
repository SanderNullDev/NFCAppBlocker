package nl.nulldevelopment.nfcappblocker.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException

/**
 * Picks how to write to [tag]: as a formatted NDEF tag, or as a blank tag that gets formatted.
 * Returns null when the tag supports neither.
 */
fun ndefTargetFor(tag: Tag): NdefTarget? =
    Ndef.get(tag)?.let(::FormattedTarget) ?: NdefFormatable.get(tag)?.let { BlankTarget(tag, it) }

private class FormattedTarget(private val ndef: Ndef) : NdefTarget {
    // Both come from tag discovery, so they're available before connect().
    override val isWritable: Boolean get() = ndef.isWritable
    override val maxSize: Int get() = ndef.maxSize

    override fun connect() = translate { ndef.connect() }
    override fun write(message: ByteArray) = translate { ndef.writeNdefMessage(NdefMessage(message)) }
    override fun read(): ByteArray? = translate { ndef.ndefMessage?.toByteArray() }
    override fun close() = closeQuietly { ndef.close() }
}

private class BlankTarget(private val tag: Tag, private val formatable: NdefFormatable) : NdefTarget {
    override val isWritable: Boolean = true
    override val maxSize: Int? = null

    override fun connect() = translate { formatable.connect() }
    override fun write(message: ByteArray) = translate { formatable.format(NdefMessage(message)) }

    /** Android allows one connected technology at a time, so switch to Ndef to read back. */
    override fun read(): ByteArray? = translate {
        closeQuietly { formatable.close() }
        val ndef = Ndef.get(tag) ?: throw NdefIoException.Failed("Tag doesn't report NDEF after formatting")
        try {
            ndef.connect()
            ndef.ndefMessage?.toByteArray()
        } finally {
            closeQuietly { ndef.close() }
        }
    }

    override fun close() = closeQuietly { formatable.close() }
}

private inline fun <T> translate(block: () -> T): T = try {
    block()
} catch (e: TagLostException) {
    throw NdefIoException.TagLost(e)
} catch (e: IOException) {
    throw NdefIoException.Failed(e.message ?: "I/O error", e)
} catch (e: FormatException) {
    throw NdefIoException.Failed(e.message ?: "Invalid NDEF data", e)
}

private inline fun closeQuietly(block: () -> Unit) {
    try {
        block()
    } catch (_: IOException) {
        // Nothing useful to do when closing a connection to a tag that's already gone.
    }
}
