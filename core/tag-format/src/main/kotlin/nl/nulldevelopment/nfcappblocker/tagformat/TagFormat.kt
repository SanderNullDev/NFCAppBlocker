package nl.nulldevelopment.nfcappblocker.tagformat

/**
 * Constants that define what a tag written by this app looks like (see D-008 and D-013).
 *
 * Shared by the main app and the HCE emulator so both always agree on the format.
 */
object TagFormat {
    /** MIME type of the record that carries the tag's UUID. */
    const val MIME_TYPE = "application/vnd.nulldev.nfcblocker"

    /** Package name stored in the Android Application Record. */
    const val AAR_PACKAGE = "nl.nulldevelopment.nfcappblocker"

    /** External type name Android uses for Android Application Records. */
    const val AAR_TYPE = "android.com:pkg"

    /** Current version of the MIME record payload: one version byte followed by 16 UUID bytes. */
    const val PAYLOAD_VERSION: Byte = 0x01
}
