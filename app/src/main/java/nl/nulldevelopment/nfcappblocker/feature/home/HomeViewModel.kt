package nl.nulldevelopment.nfcappblocker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.apps.InstalledApp
import nl.nulldevelopment.nfcappblocker.apps.InstalledAppsSource
import nl.nulldevelopment.nfcappblocker.apps.NeverBlockPolicy
import nl.nulldevelopment.nfcappblocker.blocking.BlockerServiceStatus
import nl.nulldevelopment.nfcappblocker.blocking.BlockingNotifications
import nl.nulldevelopment.nfcappblocker.blocking.blockList
import nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository
import nl.nulldevelopment.nfcappblocker.data.history.UsageHistory
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** An app being blocked, as shown on the home screen. */
data class BlockedApp(val packageName: String, val label: String)

/** A block list on the home screen (D-043). */
data class BlockListSummary(
    val id: Long,
    val name: String,
    val appCount: Int,
    /** Names of the tags linked to it, by name. */
    val tagNames: List<String>,
    /** It's blocking right now. */
    val active: Boolean,
    /** When it last started blocking; null if it never has (since history began). */
    val lastUsed: Instant?,
)

sealed interface HomeState {
    data object Loading : HomeState

    data class Loaded(
        /** Null when nothing is blocking. */
        val activeProfileName: String?,
        val since: Instant?,
        val blockedApps: List<BlockedApp>,
        val serviceEnabled: Boolean,
        /** At least one profile has apps to block. */
        val hasProfileWithApps: Boolean,
        /** At least one tag is linked to a profile. */
        val hasLinkedTag: Boolean,
        val blockLists: List<BlockListSummary>,
        val period: StatsPeriod,
        val stats: UsageStats,
        /** How long the running session has been blocking, updated once a minute. */
        val blockingFor: Duration?,
    ) : HomeState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    blocking: BlockingRepository,
    profiles: ProfileRepository,
    tags: TagRepository,
    private val apps: InstalledAppsSource,
    private val serviceStatus: BlockerServiceStatus,
    private val notifications: BlockingNotifications,
    history: UsageHistory,
    private val clock: Clock,
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

    private val period = MutableStateFlow(StatsPeriod.Week)

    fun setPeriod(value: StatsPeriod) {
        period.value = value
    }

    /** Ticks once a minute, so a running session's time keeps counting on screen. */
    private val now = flow {
        while (true) {
            emit(clock.instant())
            delay(60_000)
        }
    }

    private class StatsBundle(val period: StatsPeriod, val stats: UsageStats, val at: Instant, val lastUsed: Map<Long, Instant>)

    private val stats = combine(history.observeSessions(), history.observeBlockScreenViews(), period, now) { sessions, views, chosen, at ->
        StatsBundle(
            period = chosen,
            stats = usageStats(sessions, views, chosen, at, ZoneId.systemDefault()),
            at = at,
            lastUsed = sessions.groupBy { it.profileId }.mapValues { (_, list) -> list.maxOf { it.startedAt } },
        )
    }

    val state: StateFlow<HomeState> = combine(
        combine(blocking.observeActive(), profiles.observeAll(), tags.observeAll(), ::Triple),
        appsData,
        serviceEnabled,
        stats,
    ) { (active, allProfiles, allTags), data, enabled, bundle ->
        if (data == null) return@combine HomeState.Loading
        val current = active.firstOrNull()
        val labels = data.installed.associate { it.packageName to it.label }
        val activeIds = active.mapTo(mutableSetOf()) { it.profile.id }
        HomeState.Loaded(
            activeProfileName = current?.profile?.name,
            since = current?.startedAt,
            blockedApps = blockList(active, data.policy).keys
                .map { BlockedApp(it, labels[it] ?: it) }
                .sortedBy { it.label.lowercase() },
            serviceEnabled = enabled,
            hasProfileWithApps = allProfiles.any { it.packageNames.isNotEmpty() },
            hasLinkedTag = allTags.any { it.profileId != null },
            blockLists = allProfiles.map { profile ->
                BlockListSummary(
                    id = profile.id,
                    name = profile.name,
                    appCount = profile.packageNames.size,
                    tagNames = allTags.filter { it.profileId == profile.id }.map { it.name }.sortedBy { it.lowercase() },
                    active = profile.id in activeIds,
                    lastUsed = bundle.lastUsed[profile.id],
                )
            }.sortedWith(
                // The active one first, then most recently used, then the rest by name.
                compareByDescending<BlockListSummary> { it.active }
                    .thenByDescending { it.lastUsed }
                    .thenBy { it.name.lowercase() },
            ),
            period = bundle.period,
            stats = bundle.stats,
            blockingFor = current?.startedAt?.let { Duration.between(it, bundle.at).coerceAtLeast(Duration.ZERO) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState.Loading)
}
