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
import nl.nulldevelopment.nfcappblocker.apps.InstalledAppsSource
import nl.nulldevelopment.nfcappblocker.blocking.BlockerServiceStatus
import nl.nulldevelopment.nfcappblocker.data.blocking.ActiveBlock
import nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingProgress
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStep
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStore
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import nl.nulldevelopment.nfcappblocker.data.settings.SettingsRepository
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import nl.nulldevelopment.nfcappblocker.feature.home.BlockedApp
import java.util.UUID
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
        /** The tag added during onboarding. */
        val tag: PairedTag? = null,
        /** The block list created during onboarding. */
        val profile: Profile? = null,
        /** [profile]'s apps by name, for "Try it". */
        val blockedApps: List<BlockedApp> = emptyList(),
        /** [profile] is blocking right now. */
        val blocking: Boolean = false,
        val countdownMinutes: Int = 0,
        /** Something is blocking, so the countdown length can't change (D-032). */
        val countdownLocked: Boolean = false,
    ) : OnboardingState
}

/** First-run onboarding (D-037). Decides whether it shows, and moves between its steps. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val store: OnboardingStore,
    private val serviceStatus: BlockerServiceStatus,
    private val profiles: ProfileRepository,
    private val tags: TagRepository,
    private val blocking: BlockingRepository,
    private val settings: SettingsRepository,
    private val apps: InstalledAppsSource,
) : ViewModel() {

    private val serviceEnabled = MutableStateFlow(serviceStatus.isEnabled())
    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())

    private val data = combine(tags.observeAll(), profiles.observeAll(), blocking.observeActive(), labels, ::Data)
    private val countdown = combine(settings.observeCountdownMinutes(), blocking.observeActive()) { minutes, active -> minutes to active.isNotEmpty() }

    val state: StateFlow<OnboardingState> = combine(store.observe(), serviceEnabled, data, countdown) { progress, enabled, data, (minutes, locked) ->
        when (progress) {
            OnboardingProgress.NotStarted -> OnboardingState.Loading
            OnboardingProgress.Done -> OnboardingState.Hidden
            is OnboardingProgress.InProgress -> {
                val tag = data.tags.firstOrNull { it.uuid == progress.tagUuid }
                val profile = data.profiles.firstOrNull { it.id == progress.profileId }?.takeIf { it.packageNames.isNotEmpty() }
                // Never past a step whose result is missing, e.g. the tag was forgotten meanwhile.
                val step = when {
                    progress.step !in STEPS -> STEPS.first()
                    progress.step > OnboardingStep.Tag && tag == null -> OnboardingStep.Tag
                    // "How to stop" works without a block list (the apps step can be skipped);
                    // "Try it" doesn't.
                    progress.step == OnboardingStep.TryIt && profile == null -> OnboardingStep.Apps
                    else -> progress.step
                }
                OnboardingState.Showing(
                    step = step,
                    index = STEPS.indexOf(step),
                    total = STEPS.size,
                    serviceEnabled = enabled,
                    tag = tag,
                    profile = profile,
                    blockedApps = profile?.packageNames.orEmpty()
                        .map { BlockedApp(it, data.labels[it] ?: it) }
                        .sortedBy { it.label.lowercase() },
                    blocking = profile != null && data.active.any { it.profile.id == profile.id },
                    countdownMinutes = minutes,
                    countdownLocked = locked,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingState.Loading)

    init {
        viewModelScope.launch {
            if (store.observe().first() != OnboardingProgress.NotStarted) return@launch
            // Installs from before onboarding existed that already work skip it.
            if (isSetUp()) store.finish() else store.setStep(STEPS.first())
        }
        viewModelScope.launch { labels.value = apps.launcherApps().associate { it.packageName to it.label } }
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
        val showing = state.value as? OnboardingState.Showing ?: return
        viewModelScope.launch {
            // Without a block list there's nothing to try, so onboarding ends after "How to stop".
            if (showing.step == OnboardingStep.Stopping && showing.profile == null) store.finish() else moveOn(showing.step)
        }
    }

    /** From the apps step: no block list for now; the home screen asks for one later (D-046). */
    fun skipApps() {
        viewModelScope.launch { store.setStep(OnboardingStep.Stopping) }
    }

    fun back() {
        val current = (state.value as? OnboardingState.Showing)?.step ?: return
        val previous = STEPS.getOrNull(STEPS.indexOf(current) - 1) ?: return
        viewModelScope.launch { store.setStep(previous) }
    }

    /** From the tag step: the tag that was added or recognised, with the name the user gave it. */
    fun tagChosen(uuid: UUID, name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty() && tags.find(uuid)?.name != trimmed) tags.rename(uuid, trimmed)
            store.setTag(uuid)
            moveOn(OnboardingStep.Tag)
        }
    }

    /** From the apps step: the block list now exists, linked to the tag. */
    fun appsSaved(profileId: Long) {
        viewModelScope.launch {
            store.setProfile(profileId)
            moveOn(OnboardingStep.Apps)
        }
    }

    /** Refused while anything is blocking, like in Settings (D-032). */
    fun setCountdownMinutes(minutes: Int) {
        viewModelScope.launch {
            if (blocking.observeActive().first().isEmpty()) settings.setCountdownMinutes(minutes)
        }
    }

    private suspend fun moveOn(from: OnboardingStep) {
        val following = STEPS.getOrNull(STEPS.indexOf(from) + 1)
        if (following == null) store.finish() else store.setStep(following)
    }

    private data class Data(
        val tags: List<PairedTag>,
        val profiles: List<Profile>,
        val active: List<ActiveBlock>,
        val labels: Map<String, String>,
    )

    companion object {
        val STEPS = OnboardingStep.entries.toList()
    }
}
