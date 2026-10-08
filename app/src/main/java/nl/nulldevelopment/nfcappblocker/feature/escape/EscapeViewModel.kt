package nl.nulldevelopment.nfcappblocker.feature.escape

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.nulldevelopment.nfcappblocker.blocking.CountdownState
import nl.nulldevelopment.nfcappblocker.blocking.EscapeCountdown
import nl.nulldevelopment.nfcappblocker.blocking.ScanFeedback
import nl.nulldevelopment.nfcappblocker.blocking.ScanOutcome
import nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository
import nl.nulldevelopment.nfcappblocker.data.history.SessionEnd
import nl.nulldevelopment.nfcappblocker.data.settings.SettingsRepository
import nl.nulldevelopment.nfcappblocker.util.ElapsedRealtime
import javax.inject.Inject

sealed interface EscapeState {
    data object Loading : EscapeState

    data class Running(
        val profileName: String,
        val remainingSeconds: Long,
        /** From 1 at the start to 0 at the end. */
        val fractionLeft: Float,
        /** Whole minutes left as of the last minute mark; changes once a minute, for TalkBack. */
        val minutesLeft: Long,
    ) : EscapeState

    /** Finished, cancelled, or blocking stopped or changed some other way: the screen closes. */
    data object Closed : EscapeState
}

/**
 * Stopping blocking without a tag (Phase 5): counts down the length from the settings, then stops
 * the profile that was blocking when it started, with the same feedback as a tag tap.
 */
@HiltViewModel
class EscapeViewModel @Inject constructor(
    private val blocking: BlockingRepository,
    private val settings: SettingsRepository,
    private val feedback: ScanFeedback,
    private val clock: ElapsedRealtime,
) : ViewModel() {

    private val _state = MutableStateFlow<EscapeState>(EscapeState.Loading)
    val state: StateFlow<EscapeState> = _state.asStateFlow()

    private var countdown: EscapeCountdown? = null
    private var job: Job? = null

    init {
        job = viewModelScope.launch { countDown() }
    }

    private suspend fun CoroutineScope.countDown() {
        val profile = blocking.observeActive().first().firstOrNull()?.profile ?: return close()
        val countdown = EscapeCountdown(settings.observeCountdownMinutes().first(), clock::millis)
        this@EscapeViewModel.countdown = countdown

        // A tag tap may stop blocking, or switch to another profile, while this counts down.
        val watcher = launch {
            blocking.observeActive().first { active -> active.none { it.profile.id == profile.id } }
            this@EscapeViewModel.cancel()
        }
        var minutesLeft = 0L
        while (true) {
            when (val current = countdown.state()) {
                is CountdownState.Running -> {
                    countdown.minutesToAnnounce()?.let { minutesLeft = it }
                    _state.value = EscapeState.Running(
                        profileName = profile.name,
                        remainingSeconds = current.remainingSeconds,
                        fractionLeft = current.remainingMillis.toFloat() / countdown.durationMillis,
                        minutesLeft = minutesLeft,
                    )
                    delay(countdown.millisUntilNextTick() ?: continue)
                }
                CountdownState.Finished -> {
                    watcher.cancel()
                    // The time was waited out; leaving now mustn't undo that halfway.
                    withContext(NonCancellable) {
                        blocking.deactivate(profile.id, SessionEnd.Unlock)
                        feedback.show(ScanOutcome.Stopped(profile))
                    }
                    return close()
                }
                CountdownState.Cancelled -> return close()
            }
        }
    }

    /** The user left the screen, or turned off the display: blocking goes on. */
    fun cancel() {
        countdown?.cancel()
        job?.cancel()
        close()
    }

    private fun close() {
        _state.value = EscapeState.Closed
    }
}
