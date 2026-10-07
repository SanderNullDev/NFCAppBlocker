package nl.nulldevelopment.nfcappblocker.feature.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.apps.InstalledApp
import nl.nulldevelopment.nfcappblocker.apps.InstalledAppsSource
import nl.nulldevelopment.nfcappblocker.apps.NeverBlockPolicy
import nl.nulldevelopment.nfcappblocker.blocking.BlockerServiceStatus
import nl.nulldevelopment.nfcappblocker.blocking.BlockingNotifications
import nl.nulldevelopment.nfcappblocker.blocking.blockList
import nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import java.time.Instant
import javax.inject.Inject

/** An app being blocked, as shown on the Status tab. */
data class BlockedApp(val packageName: String, val label: String)

sealed interface StatusState {
    data object Loading : StatusState

    data class Loaded(
        /** Null when nothing is blocking. */
        val activeProfileName: String?,
        val since: Instant?,
        val blockedApps: List<BlockedApp>,
        val serviceEnabled: Boolean,
        /** What the user still has to set up before a tag can start blocking. */
        val setup: Setup,
    ) : StatusState

    enum class Setup { NoProfiles, NoLinkedTags, Ready }
}

@HiltViewModel
class StatusViewModel @Inject constructor(
    blocking: BlockingRepository,
    profiles: ProfileRepository,
    tags: TagRepository,
    private val apps: InstalledAppsSource,
    private val serviceStatus: BlockerServiceStatus,
    private val notifications: BlockingNotifications,
) : ViewModel() {

    private class AppsData(val installed: List<InstalledApp>, val policy: NeverBlockPolicy)

    private val appsData = MutableStateFlow<AppsData?>(null)
    private val serviceEnabled = MutableStateFlow(serviceStatus.isEnabled())

    init {
        viewModelScope.launch { appsData.value = AppsData(apps.launcherApps(), apps.neverBlockPolicy()) }
    }

    /** The service can be switched on or off in Settings at any time; re-check when the user is back. */
    fun refresh() {
        serviceEnabled.value = serviceStatus.isEnabled()
        // The user may have just allowed notifications.
        notifications.refresh()
        viewModelScope.launch { appsData.value = AppsData(apps.launcherApps(), apps.neverBlockPolicy()) }
    }

    val state: StateFlow<StatusState> = combine(
        blocking.observeActive(),
        profiles.observeAll(),
        tags.observeAll(),
        appsData,
        serviceEnabled,
    ) { active, allProfiles, allTags, data, enabled ->
        if (data == null) return@combine StatusState.Loading
        val current = active.firstOrNull()
        val labels = data.installed.associate { it.packageName to it.label }
        StatusState.Loaded(
            activeProfileName = current?.profile?.name,
            since = current?.startedAt,
            blockedApps = blockList(active, data.policy).keys
                .map { BlockedApp(it, labels[it] ?: it) }
                .sortedBy { it.label.lowercase() },
            serviceEnabled = enabled,
            setup = when {
                allProfiles.isEmpty() -> StatusState.Setup.NoProfiles
                allTags.none { it.profileId != null } -> StatusState.Setup.NoLinkedTags
                else -> StatusState.Setup.Ready
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusState.Loading)
}
