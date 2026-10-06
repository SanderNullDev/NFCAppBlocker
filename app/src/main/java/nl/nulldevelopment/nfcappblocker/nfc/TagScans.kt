package nl.nulldevelopment.nfcappblocker.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Process
import android.os.SystemClock
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Turns Android NFC events into [ScanResult]s and keeps the recent ones in memory.
 *
 * Spike-level (Phase 0): a process-wide object. It becomes an injected tag source once
 * Hilt is introduced in Phase 1.
 */
object TagScans {
    private const val MAX_SCANS = 50

    private val nextId = AtomicLong()
    private val _recent = MutableStateFlow<List<ScanResult>>(emptyList())

    /** Newest first. */
    val recent: StateFlow<List<ScanResult>> = _recent.asStateFlow()

    private val _writeArmed = MutableStateFlow(false)

    /** When true, the next reader-mode tag is written with a fresh UUID instead of only read. */
    val writeArmed: StateFlow<Boolean> = _writeArmed.asStateFlow()

    fun armWrite() {
        _writeArmed.value = true
    }

    fun cancelWrite() {
        _writeArmed.value = false
    }

    /** Handles an `NDEF_DISCOVERED` intent. Ignores intents that carry no tag. */
    fun onDispatchIntent(intent: Intent, source: ScanSource) {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return
        val message = IntentCompat
            .getParcelableArrayExtra(intent, NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            ?.firstOrNull() as NdefMessage?
        record(tag, message, source)
    }

    /**
     * Handles a tag from reader mode. Called on a binder thread, which is fine for the blocking
     * write. When not writing, it uses the NDEF message Android already read during discovery.
     * One armed write is used up by one tag, whatever the outcome.
     */
    fun onReaderModeTag(tag: Tag) {
        val contentBefore = Ndef.get(tag)?.cachedNdefMessage
        val write = if (_writeArmed.compareAndSet(expect = true, update = false)) {
            TagWriter.write(ndefTargetFor(tag), UUID.randomUUID())
        } else {
            null
        }
        record(tag, contentBefore, ScanSource.READER_MODE, write)
    }

    private fun record(tag: Tag, message: NdefMessage?, source: ScanSource, write: WriteResult? = null) {
        val scan = ScanResult.create(
            id = nextId.getAndIncrement(),
            timeMillis = System.currentTimeMillis(),
            source = source,
            ndefMessage = message?.toByteArray(),
            uid = tag.id ?: ByteArray(0),
            techClassNames = tag.techList.toList(),
            processAgeMillis = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime(),
            write = write,
        )
        _recent.update { (listOf(scan) + it).take(MAX_SCANS) }
    }
}
