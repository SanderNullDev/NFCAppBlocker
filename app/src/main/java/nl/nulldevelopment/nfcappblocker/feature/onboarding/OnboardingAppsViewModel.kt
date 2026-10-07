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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.apps.AppPickerItem
import nl.nulldevelopment.nfcappblocker.apps.InstalledApp
import nl.nulldevelopment.nfcappblocker.apps.InstalledAppsSource
import nl.nulldevelopment.nfcappblocker.apps.NeverBlockPolicy
import nl.nulldevelopment.nfcappblocker.apps.appPickerItems
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import nl.nulldevelopment.nfcappblocker.util.DefaultNameFormats
import nl.nulldevelopment.nfcappblocker.util.firstUnusedName
import java.util.UUID
import javax.inject.Inject

data class OnboardingAppsState(val apps: List<AppPickerItem>, val selectedCount: Int, val query: String)

/** The onboarding apps step: picks the apps for the tag's block list. */
@HiltViewModel
class OnboardingAppsViewModel @Inject constructor(
    private val installed: InstalledAppsSource,
    private val profiles: ProfileRepository,
    private val tags: TagRepository,
    private val names: DefaultNameFormats,
) : ViewModel() {

    private class AppsData(val installed: List<InstalledApp>, val policy: NeverBlockPolicy)

    private val appsData = MutableStateFlow<AppsData?>(null)
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val query = MutableStateFlow("")
    private var loadedFor: Long? = null

    val state: StateFlow<OnboardingAppsState?> = combine(appsData, selected, query) { data, chosen, text ->
        data?.let { OnboardingAppsState(appPickerItems(it.installed, chosen, it.policy, text), chosen.size, text) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { appsData.value = AppsData(installed.launcherApps(), installed.neverBlockPolicy()) }
    }

    /** Starts from the apps of the block list made earlier in onboarding, after going back. */
    fun load(profileId: Long?) {
        if (profileId == null || profileId == loadedFor) return
        loadedFor = profileId
        viewModelScope.launch {
            profiles.observe(profileId).first()?.let { selected.value = it.packageNames }
        }
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun setSelected(packageName: String, chosen: Boolean) {
        if (chosen && appsData.value?.policy?.canBlock(packageName) == false) return
        selected.update { if (chosen) it + packageName else it - packageName }
    }

    /**
     * Saves the block list with the chosen apps, linked to [tagUuid] (moving it from another block
     * list, D-003). Creates it when [profileId] is null. Calls [onSaved] with its id.
     */
    fun save(tagUuid: UUID, profileId: Long?, onSaved: (Long) -> Unit) {
        val chosen = selected.value
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            val existing = profileId?.let { profiles.observe(it).first() }
            val name = existing?.name ?: firstUnusedName(profiles.observeAll().first().map { it.name }, names::profile)
            // Keeps tags already linked to an existing block list; a new one gets only this tag.
            val linked = existing?.let { list -> tags.observeAll().first().filter { it.profileId == list.id }.mapTo(mutableSetOf()) { it.uuid } }.orEmpty()
            onSaved(profiles.save(existing?.id, name, chosen, linked + tagUuid))
        }
    }
}
