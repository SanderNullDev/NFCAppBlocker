package nl.nulldevelopment.nfcappblocker.tagformat

/**
 * A single NDEF record, independent of `android.nfc` so it can be tested on the JVM.
 *
 * Byte arrays are compared by content.
 */
class NdefRecord(
    val tnf: Tnf,
    val type: ByteArray,
    val payload: ByteArray,
    val id: ByteArray = ByteArray(0),
) {
    val typeString: String get() = type.toString(Charsets.US_ASCII)

    override fun equals(other: Any?): Boolean =
        other is NdefRecord &&
            tnf == other.tnf &&
            type.contentEquals(other.type) &&
            payload.contentEquals(other.payload) &&
            id.contentEquals(other.id)

    override fun hashCode(): Int {
        var result = tnf.hashCode()
        result = 31 * result + type.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + id.contentHashCode()
        return result
    }

    override fun toString(): String =
        "NdefRecord(tnf=$tnf, type=$typeString, payload=${payload.size} bytes, id=${id.size} bytes)"

    /** Type Name Format, the 3-bit field in the record header. */
    enum class Tnf(val code: Int) {
        EMPTY(0x00),
        WELL_KNOWN(0x01),
        MIME_MEDIA(0x02),
        ABSOLUTE_URI(0x03),
        EXTERNAL_TYPE(0x04),
        UNKNOWN(0x05),
        UNCHANGED(0x06),
        RESERVED(0x07);

        companion object {
            fun fromCode(code: Int): Tnf = entries.first { it.code == code }
        }
    }

    companion object {
        fun mime(mimeType: String, payload: ByteArray): NdefRecord =
            NdefRecord(Tnf.MIME_MEDIA, mimeType.toByteArray(Charsets.US_ASCII), payload)

        fun androidApplicationRecord(packageName: String): NdefRecord =
            NdefRecord(
                Tnf.EXTERNAL_TYPE,
                TagFormat.AAR_TYPE.toByteArray(Charsets.US_ASCII),
                packageName.toByteArray(Charsets.UTF_8),
            )
    }
}
