package nl.nulldevelopment.nfcappblocker.blocking

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import nl.nulldevelopment.nfcappblocker.di.ApplicationScope
import nl.nulldevelopment.nfcappblocker.nfc.NfcTagEvents
import nl.nulldevelopment.nfcappblocker.nfc.ScannedTag
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Where the app should go after a scan that couldn't toggle anything. */
sealed interface ScanNavigation {
    data class OpenTag(val uuid: UUID) : ScanNavigation
    data object AddTag : ScanNavigation
}

/**
 * Handles every scan that no screen claimed (D-005): toggles blocking, gives feedback, and asks
 * the app to open the right screen when the tag can't toggle anything yet.
 */
@Singleton
class ScanToggleRunner @Inject constructor(
    tags: TagRepository,
    profiles: ProfileRepository,
    blocking: BlockingRepository,
    private val events: NfcTagEvents,
    private val feedback: ScanFeedback,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val toggle = ScanToggle(tags, profiles, blocking, elapsedMillis = SystemClock::elapsedRealtime)

    private val _navigation = MutableSharedFlow<ScanNavigation>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** For the open app; [ScanActivity][nl.nulldevelopment.nfcappblocker.ScanActivity] uses [handle]'s result instead. */
    val navigation: SharedFlow<ScanNavigation> = _navigation.asSharedFlow()

    /** Call once, at app start. */
    fun start() {
        scope.launch {
            events.unclaimedScans.collect { tag ->
                navigationFor(handle(tag))?.let { _navigation.tryEmit(it) }
            }
        }
    }

    suspend fun handle(tag: ScannedTag): ScanOutcome =
        toggle.onScan(tag.content, tag.hardwareUid).also(feedback::show)

    companion object {
        fun navigationFor(outcome: ScanOutcome): ScanNavigation? = when (outcome) {
            is ScanOutcome.TagNotLinked -> ScanNavigation.OpenTag(outcome.tag.uuid)
            is ScanOutcome.UnknownTagOfOurs -> ScanNavigation.AddTag
            else -> null
        }
    }
}
