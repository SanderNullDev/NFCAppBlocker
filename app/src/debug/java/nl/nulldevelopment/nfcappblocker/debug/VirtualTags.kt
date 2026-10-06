package nl.nulldevelopment.nfcappblocker.debug

import nl.nulldevelopment.nfcappblocker.nfc.NdefTarget
import nl.nulldevelopment.nfcappblocker.nfc.ScannedTag
import nl.nulldevelopment.nfcappblocker.nfc.TagContent

/**
 * Simulated tags for debug builds, kept in memory by hardware UID. Writes stick, so writing a
 * virtual tag and then tapping it again behaves like a real sticker.
 */
object VirtualTags {
    private class VirtualTag(var content: ByteArray?, val readOnly: Boolean) : NdefTarget {
        override val isWritable: Boolean get() = !readOnly
        override val maxSize: Int = NTAG215_NDEF_CAPACITY
        override fun connect() = Unit
        override fun write(message: ByteArray) {
            content = message
        }
        override fun read(): ByteArray? = content
        override fun close() = Unit
    }

    private const val NTAG215_NDEF_CAPACITY = 496
    private val tags = mutableMapOf<String, VirtualTag>()

    @Synchronized
    fun set(uid: String, content: ByteArray?, readOnly: Boolean) {
        tags[uid] = VirtualTag(content, readOnly)
    }

    /** A tap on the virtual tag with this UID; unknown UIDs start out blank and writable. */
    @Synchronized
    fun scan(uid: String): ScannedTag {
        val tag = tags.getOrPut(uid) { VirtualTag(content = null, readOnly = false) }
        return ScannedTag(uid, TagContent.of(tag.content), tag.isWritable, tag)
    }
}
