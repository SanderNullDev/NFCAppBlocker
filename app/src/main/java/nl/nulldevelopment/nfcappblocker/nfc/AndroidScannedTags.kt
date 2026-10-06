package nl.nulldevelopment.nfcappblocker.nfc

import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

/** Reads what pairing needs from a tag, using the NDEF message Android read during discovery. */
fun Tag.toScannedTag(): ScannedTag {
    val ndef = Ndef.get(this)
    val isWritable = ndef?.isWritable ?: (NdefFormatable.get(this) != null)
    return ScannedTag(
        hardwareUid = formatHardwareUid(id ?: ByteArray(0)),
        content = TagContent.of(ndef?.cachedNdefMessage?.toByteArray()),
        isWritable = isWritable,
        target = ndefTargetFor(this),
    )
}

/** Colon-separated upper-case hex, e.g. "04:1D:42:74:D4:2A:81". */
fun formatHardwareUid(uid: ByteArray): String = uid.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
