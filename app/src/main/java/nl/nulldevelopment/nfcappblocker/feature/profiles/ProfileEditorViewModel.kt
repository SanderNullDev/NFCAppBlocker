package nl.nulldevelopment.nfcappblocker.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.nulldevelopment.nfcappblocker.apps.AppPickerItem
import nl.nulldevelopment.nfcappblocker.apps.InstalledApp
import nl.nulldevelopment.nfcappblocker.apps.InstalledAppsSource
import nl.nulldevelopment.nfcappblocker.apps.NeverBlockPolicy
import nl.nulldevelopment.nfcappblocker.apps.appPickerItems
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import java.util.UUID

/** A tag that could be linked to this profile, and the profile it currently belongs to, if any. */
data class TagChoice(val tag: PairedTag, val currentProfileName: String?)

sealed interface ProfileEditorState {
    data object Loading : ProfileEditorState

    /** Deleted, or never existed. */
    data object Missing : ProfileEditorState

    data class Loaded(
        val profileId: Long,
        val name: String,
        /** Rows for the app list, already filtered by [query]. */
        val apps: List<AppPickerItem>,
        val selectedCount: Int,
        val query: String,
        val linkedTags: List<PairedTag>,
        val otherTags: List<TagChoice>,
    ) : ProfileEditorState
}

/** Edits are saved as they're made; there's no separate save step. */
@HiltViewModel(assistedFactory = ProfileEditorViewModel.Factory::class)
class ProfileEditorViewModel @AssistedInject constructor(
    @Assisted private val profileId: Long,
    private val profiles: ProfileRepository,
    private val tags: TagRepository,
    private val apps: InstalledAppsSource,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(profileId: Long): ProfileEditorViewModel
    }

    private class AppsData(val installed: List<InstalledApp>, val policy: NeverBlockPolicy)

    private val appsData = MutableStateFlow<AppsData?>(null)
    private val query = MutableStateFlow("")

    /** Serialises app toggles, so quick taps don't overwrite each other's change. */
    private val appsMutex = Mutex()

    init {
        viewModelScope.launch { appsData.value = AppsData(apps.launcherApps(), apps.neverBlockPolicy()) }
    }

    val state: StateFlow<ProfileEditorState> = combine(
        profiles.observe(profileId),
        profiles.observeAll(),
        tags.observeAll(),
        appsData,
        query,
    ) { profile, allProfiles, allTags, data, query ->
        when {
            profile == null -> ProfileEditorState.Missing
            data == null -> ProfileEditorState.Loading
            else -> {
                val namesById = allProfiles.associate { it.id to it.name }
                ProfileEditorState.Loaded(
                    profileId = profile.id,
                    name = profile.name,
                    apps = appPickerItems(data.installed, profile.packageNames, data.policy, query),
                    selectedCount = profile.packageNames.size,
                    query = query,
                    linkedTags = allTags.filter { it.profileId == profile.id },
                    otherTags = allTags.filter { it.profileId != profile.id }
                        .map { TagChoice(it, it.profileId?.let(namesById::get)) },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileEditorState.Loading)

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { profiles.rename(profileId, name.trim()) }
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun setAppSelected(packageName: String, selected: Boolean) {
        viewModelScope.launch {
            appsMutex.withLock {
                val current = profiles.observe(profileId).filterNotNull().first().packageNames
                val policy = appsData.value?.policy
                // Protected apps can't be added, but can always be removed.
                if (selected && policy != null && !policy.canBlock(packageName)) return@withLock
                val updated = if (selected) current + packageName else current - packageName
                if (updated != current) profiles.setApps(profileId, updated)
            }
        }
    }

    /** Links a tag to this profile, moving it from another profile if needed (D-003). */
    fun linkTag(uuid: UUID) {
        viewModelScope.launch { tags.setProfile(uuid, profileId) }
    }

    fun unlinkTag(uuid: UUID) {
        viewModelScope.launch { tags.setProfile(uuid, null) }
    }

    fun delete() {
        viewModelScope.launch { profiles.delete(profileId) }
    }
}
