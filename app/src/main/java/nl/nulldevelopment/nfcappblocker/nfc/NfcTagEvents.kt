package nl.nulldevelopment.nfcappblocker.nfc

import android.nfc.Tag
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tags held against the phone while the main screen is in the foreground (reader mode), plus
 * simulated ones from the debug tag source. Screens that need a tag (adding, wiping) collect
 * [scans] while they're shown; when nobody collects, scans are dropped.
 */
@Singleton
class NfcTagEvents @Inject constructor() {
    private val _scans = newFlow<ScannedTag>()
    val scans: SharedFlow<ScannedTag> = _scans.asSharedFlow()

    private val _platformTags = newFlow<Tag>()

    /** Real tags only, for the Phase 0 test tools that work with Android's [Tag] directly. */
    val platformTags: SharedFlow<Tag> = _platformTags.asSharedFlow()

    /** Called from the reader-mode callback, on a binder thread. */
    fun onTagDiscovered(tag: Tag) {
        _platformTags.tryEmit(tag)
        _scans.tryEmit(tag.toScannedTag())
    }

    /** A simulated tap, from the debug tag source. */
    fun onSimulatedScan(tag: ScannedTag) {
        _scans.tryEmit(tag)
    }

    private fun <T> newFlow() = MutableSharedFlow<T>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}
