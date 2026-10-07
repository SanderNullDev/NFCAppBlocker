package nl.nulldevelopment.nfcappblocker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository
import nl.nulldevelopment.nfcappblocker.data.settings.SettingsRepository
import javax.inject.Inject

sealed interface SettingsState {
    data object Loading : SettingsState

    /** [countdownLocked] while a profile is blocking, so the countdown can't be shortened mid-block (Q-2). */
    data class Loaded(val countdownMinutes: Int, val countdownLocked: Boolean) : SettingsState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val blocking: BlockingRepository,
) : ViewModel() {

    val state: StateFlow<SettingsState> =
        combine(settings.observeCountdownMinutes(), blocking.observeActive()) { minutes, active ->
            SettingsState.Loaded(minutes, countdownLocked = active.isNotEmpty())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState.Loading)

    fun setCountdownMinutes(minutes: Int) {
        viewModelScope.launch {
            // Checked here as well as in the UI: the lock is the point, not the disabled slider.
            if (blocking.observeActive().first().isEmpty()) settings.setCountdownMinutes(minutes)
        }
    }
}
