package nl.nulldevelopment.nfcappblocker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import nl.nulldevelopment.nfcappblocker.nfc.NfcTagEvents
import nl.nulldevelopment.nfcappblocker.tagformat.NdefCodec
import nl.nulldevelopment.nfcappblocker.tagformat.NdefRecord
import nl.nulldevelopment.nfcappblocker.tagformat.TagMessage
import java.util.UUID
import javax.inject.Inject

/**
 * Debug tag source (Phase 1): simulates holding a tag against the phone, for testing without
 * stickers. Debug builds only, and only adb or the system can send it (DUMP permission).
 *
 * ```
 * adb shell am broadcast -a nl.nulldevelopment.nfcappblocker.debug.TAP_TAG -p nl.nulldevelopment.nfcappblocker \
 *     --es uid 04:00:00:00:00:00:01 [--es content blank|foreign|unreadable|ours:<uuid>] [--ez read_only true]
 * ```
 * Without `content`, the existing virtual tag with that UID is tapped (a new one starts blank).
 */
@AndroidEntryPoint
class DebugTagReceiver : BroadcastReceiver() {

    @Inject lateinit var tagEvents: NfcTagEvents

    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getStringExtra(EXTRA_UID) ?: DEFAULT_UID
        val contentSpec = intent.getStringExtra(EXTRA_CONTENT)
        if (contentSpec != null) {
            val content = try {
                parseContent(contentSpec)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Ignored: ${e.message}")
                return
            }
            VirtualTags.set(uid, content, readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false))
        }
        val scanned = VirtualTags.scan(uid)
        Log.i(TAG, "Tap on virtual tag $uid: ${scanned.content}, writable=${scanned.isWritable}")
        tagEvents.onSimulatedScan(scanned)
    }

    private fun parseContent(spec: String): ByteArray? = when {
        spec == "blank" -> null
        spec == "foreign" -> NdefCodec.encode(listOf(NdefRecord.mime("text/plain", "Some other app".toByteArray())))
        spec == "unreadable" -> byteArrayOf(0x01, 0x02, 0x03)
        spec.startsWith("ours:") -> TagMessage.encode(UUID.fromString(spec.removePrefix("ours:")))
        else -> throw IllegalArgumentException("Unknown content \"$spec\"")
    }

    private companion object {
        const val TAG = "DebugTags"
        const val EXTRA_UID = "uid"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_READ_ONLY = "read_only"
        const val DEFAULT_UID = "04:00:00:00:00:00:01"
    }
}
