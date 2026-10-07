package nl.nulldevelopment.nfcappblocker.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.blocking.BlockerServiceStatus
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingProgress
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStep
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStore
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import javax.inject.Inject

sealed interface OnboardingState {
    data object Loading : OnboardingState

    /** Done, or skipped because the app was already set up: the normal app shows. */
    data object Hidden : OnboardingState

    data class Showing(
        val step: OnboardingStep,
        /** 0-based position of [step], for the progress bar. */
        val index: Int,
        val total: Int,
        val serviceEnabled: Boolean,
    ) : OnboardingState
}

/** First-run onboarding (D-037). Decides whether it shows, and moves between its steps. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val store: OnboardingStore,
    private val serviceStatus: BlockerServiceStatus,
    private val profiles: ProfileRepository,
    private val tags: TagRepository,
) : ViewModel() {

    private val serviceEnabled = MutableStateFlow(serviceStatus.isEnabled())

    val state: StateFlow<OnboardingState> = combine(store.observe(), serviceEnabled) { progress, enabled ->
        when (progress) {
            OnboardingProgress.NotStarted -> OnboardingState.Loading
            OnboardingProgress.Done -> OnboardingState.Hidden
            is OnboardingProgress.InProgress -> {
                // A step saved by a later version, or removed since, starts over.
                val step = progress.step.takeIf { it in STEPS } ?: STEPS.first()
                OnboardingState.Showing(step, STEPS.indexOf(step), STEPS.size, enabled)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingState.Loading)

    init {
        viewModelScope.launch {
            if (store.observe().first() != OnboardingProgress.NotStarted) return@launch
            // Installs from before onboarding existed that already work skip it.
            if (isSetUp()) store.finish() else store.setStep(STEPS.first())
        }
    }

    private suspend fun isSetUp(): Boolean {
        if (!serviceStatus.isEnabled()) return false
        val withApps = profiles.observeAll().first().filter { it.packageNames.isNotEmpty() }.mapTo(mutableSetOf()) { it.id }
        return tags.observeAll().first().any { it.profileId in withApps }
    }

    /** The user may come back from Accessibility settings. */
    fun refresh() {
        serviceEnabled.value = serviceStatus.isEnabled()
    }

    fun next() {
        val current = (state.value as? OnboardingState.Showing)?.step ?: return
        val following = STEPS.getOrNull(STEPS.indexOf(current) + 1)
        viewModelScope.launch { if (following == null) store.finish() else store.setStep(following) }
    }

    fun back() {
        val current = (state.value as? OnboardingState.Showing)?.step ?: return
        val previous = STEPS.getOrNull(STEPS.indexOf(current) - 1) ?: return
        viewModelScope.launch { store.setStep(previous) }
    }

    companion object {
        /** The steps built so far; the rest of D-037 follows in the next step of Phase 7. */
        val STEPS = listOf(OnboardingStep.Welcome, OnboardingStep.Service)
    }
}
