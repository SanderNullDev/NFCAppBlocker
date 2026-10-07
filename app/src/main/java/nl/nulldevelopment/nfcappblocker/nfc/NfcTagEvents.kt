package nl.nulldevelopment.nfcappblocker.nfc

import android.nfc.Tag
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes tags held against the phone while the app is open (reader mode), plus simulated ones
 * from the debug tag source. A screen that needs a tag (adding, wiping, scanning in the profile
 * editor) claims scans while it's visible and waiting; claimed scans go to [scans]. Any other
 * scan goes to [unclaimedScans], where it toggles blocking (D-005).
 */
@Singleton
class NfcTagEvents @Inject constructor() {
    private val claims = AtomicInteger()

    private val _scans = newFlow()
    val scans: SharedFlow<ScannedTag> = _scans.asSharedFlow()

    private val _unclaimedScans = newFlow()
    val unclaimedScans: SharedFlow<ScannedTag> = _unclaimedScans.asSharedFlow()

    fun claim() {
        claims.incrementAndGet()
    }

    fun release() {
        claims.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    /** Called from the reader-mode callback, on a binder thread. */
    fun onTagDiscovered(tag: Tag) = route(tag.toScannedTag())

    /** A simulated tap, from the debug tag source. */
    fun onSimulatedScan(tag: ScannedTag) = route(tag)

    private fun route(tag: ScannedTag) {
        if (claims.get() > 0) _scans.tryEmit(tag) else _unclaimedScans.tryEmit(tag)
    }

    private fun newFlow() = MutableSharedFlow<ScannedTag>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}

/**
 * A ViewModel's claim on scans: held only while its screen is resumed and it [wants][setWanted]
 * scans, so a screen left in another tab's back stack never takes a scan. Not thread safe; call
 * from the main thread.
 */
class ScanClaim(private val events: NfcTagEvents) {
    private var resumed = false
    private var wanted = false

    /** Whether claimed scans currently belong to this claim's owner. */
    var active = false
        private set

    fun setResumed(value: Boolean) {
        resumed = value
        update()
    }

    fun setWanted(value: Boolean) {
        wanted = value
        update()
    }

    fun close() {
        resumed = false
        update()
    }

    private fun update() {
        val should = resumed && wanted
        if (should == active) return
        if (should) events.claim() else events.release()
        active = should
    }
}
