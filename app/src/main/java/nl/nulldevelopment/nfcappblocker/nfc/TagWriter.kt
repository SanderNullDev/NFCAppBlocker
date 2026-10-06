package nl.nulldevelopment.nfcappblocker.nfc

import nl.nulldevelopment.nfcappblocker.tagformat.TagMessage
import java.io.Closeable
import java.util.UUID

/**
 * A tag that can hold an NDEF message, seen through the few operations writing needs.
 * Keeps [TagWriter] free of Android classes so it can be unit tested.
 */
interface NdefTarget : Closeable {
    /** False for a tag that is formatted but locked. */
    val isWritable: Boolean

    /** Capacity in bytes, or null for a blank tag whose capacity is only known after formatting. */
    val maxSize: Int?

    fun connect()

    /** Writes [message]; a blank tag is formatted with it. */
    fun write(message: ByteArray)

    /** Reads the NDEF message back from the tag, or null if it holds none. */
    fun read(): ByteArray?
}

/** Thrown by [NdefTarget] implementations, translated from Android's NFC exceptions. */
sealed class NdefIoException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class TagLost(cause: Throwable? = null) : NdefIoException("Tag was lost", cause)
    class Failed(message: String, cause: Throwable? = null) : NdefIoException(message, cause)
}

sealed interface WriteResult {
    /** Written, and reading it back returned the same UUID. */
    data class Written(val uuid: UUID) : WriteResult

    /** The write call succeeded, but reading it back failed, so the content is unconfirmed. */
    data class WrittenUnverified(val uuid: UUID, val reason: String) : WriteResult

    /** The write call succeeded, but the tag reads back something else. */
    data class VerificationFailed(val uuid: UUID, val readBack: TagMessage.Result?) : WriteResult

    data object ReadOnly : WriteResult
    data class TooSmall(val capacity: Int, val needed: Int) : WriteResult
    data object NotNdefCapable : WriteResult

    /** The tag moved away during the write; it may hold partial content. */
    data object TagLost : WriteResult
    data class Failed(val reason: String) : WriteResult
}

/** Writes our tag message (D-008, D-013) and verifies it by reading it back. Blocking I/O. */
object TagWriter {

    fun write(target: NdefTarget?, uuid: UUID): WriteResult {
        if (target == null) return WriteResult.NotNdefCapable
        val message = TagMessage.encode(uuid)
        target.use {
            if (!target.isWritable) return WriteResult.ReadOnly
            target.maxSize?.let { capacity ->
                if (capacity < message.size) return WriteResult.TooSmall(capacity, message.size)
            }

            try {
                target.connect()
                target.write(message)
            } catch (e: NdefIoException.TagLost) {
                return WriteResult.TagLost
            } catch (e: NdefIoException.Failed) {
                return WriteResult.Failed(e.message.orEmpty())
            }

            val readBack = try {
                target.read()
            } catch (e: NdefIoException) {
                return WriteResult.WrittenUnverified(uuid, e.message.orEmpty())
            }
            val parsed = readBack?.let(TagMessage::parse)
            return if (parsed == TagMessage.Result.Ours(uuid)) {
                WriteResult.Written(uuid)
            } else {
                WriteResult.VerificationFailed(uuid, parsed)
            }
        }
    }
}
