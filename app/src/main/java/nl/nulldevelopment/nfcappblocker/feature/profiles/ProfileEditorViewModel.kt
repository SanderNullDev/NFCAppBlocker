package nl.nulldevelopment.nfcappblocker.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import nl.nulldevelopment.nfcappblocker.di.IoDispatcher
import nl.nulldevelopment.nfcappblocker.feature.pairing.PairingFlow
import nl.nulldevelopment.nfcappblocker.feature.pairing.PairingState
import nl.nulldevelopment.nfcappblocker.nfc.NfcTagEvents
import nl.nulldevelopment.nfcappblocker.nfc.ScanClaim
import nl.nulldevelopment.nfcappblocker.util.DefaultNameFormats
import nl.nulldevelopment.nfcappblocker.util.firstUnusedName
import java.util.UUID

/** A tag that could be linked to this profile, and the profile it currently belongs to, if any. */
data class TagChoice(val tag: PairedTag, val currentProfileName: String?)

sealed interface ProfileEditorState {
    data object Loading : ProfileEditorState

    /** Deleted, or never existed. */
    data object Missing : ProfileEditorState

    data class Loaded(
        /** True until a new profile is saved for the first time. */
        val isNew: Boolean,
        val name: String,
        /** Rows for the app list, already filtered by [query]. */
        val apps: List<AppPickerItem>,
        val selectedCount: Int,
        val query: String,
        val linkedTags: List<PairedTag>,
        val otherTags: List<TagChoice>,
        /** The user changed something since opening; leaving then asks to discard. */
        val hasChanges: Boolean,
        /** A new profile can be saved untouched; an existing one only after a change. */
        val canSave: Boolean,
    ) : ProfileEditorState
}

/** "Scan a tag" in the profile editor; null when not scanning. */
sealed interface EditorScan {
    /** A step of pairing a new or unknown tag (D-019). */
    data class Pairing(val state: PairingState) : EditorScan

    /** The scanned tag is already in this profile; still listening. */
    data class AlreadyHere(val tagName: String) : EditorScan

    /** The scanned tag belongs to another profile; moving it needs the user's OK (D-003). */
    data class ConfirmMove(val tag: PairedTag, val fromProfile: String) : EditorScan

    /** A tag was just paired; it gets a name before it's linked. */
    data class NameNewTag(val tag: PairedTag) : EditorScan
}

/**
 * Edits a draft of a profile; nothing is stored until [save] (D-025). [profileId] is null for a
 * new profile, which is only created on its first save.
 */
@HiltViewModel(assistedFactory = ProfileEditorViewModel.Factory::class)
class ProfileEditorViewModel @AssistedInject constructor(
    @Assisted private val profileId: Long?,
    private val profiles: ProfileRepository,
    private val tags: TagRepository,
    private val apps: InstalledAppsSource,
    private val names: DefaultNameFormats,
    private val tagEvents: NfcTagEvents,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(profileId: Long?): ProfileEditorViewModel
    }

    private data class Draft(val name: String, val packageNames: Set<String>, val tagUuids: Set<UUID>)

    private class AppsData(val installed: List<InstalledApp>, val policy: NeverBlockPolicy)

    /** What's stored; null while loading. */
    private val saved = MutableStateFlow<Draft?>(null)
    private val draft = MutableStateFlow<Draft?>(null)
    private val appsData = MutableStateFlow<AppsData?>(null)
    private val query = MutableStateFlow("")
    private val missing = MutableStateFlow(false)

    private val _closed = MutableStateFlow(false)

    /** True once saved or deleted: the screen should close. */
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    init {
        viewModelScope.launch { appsData.value = AppsData(apps.launcherApps(), apps.neverBlockPolicy()) }
        viewModelScope.launch {
            val baseline = if (profileId == null) {
                Draft(firstUnusedName(profiles.observeAll().first().map { it.name }, names::profile), emptySet(), emptySet())
            } else {
                val profile = profiles.observe(profileId).first()
                if (profile == null) {
                    missing.value = true
                    return@launch
                }
                val linked = tags.observeAll().first().filter { it.profileId == profileId }.mapTo(mutableSetOf()) { it.uuid }
                Draft(profile.name, profile.packageNames, linked)
            }
            saved.value = baseline
            draft.value = baseline
        }
    }

    val state: StateFlow<ProfileEditorState> = combine(
        combine(saved, draft, missing, ::Triple),
        tags.observeAll(),
        profiles.observeAll(),
        appsData,
        query,
    ) { (saved, draft, missing), allTags, allProfiles, data, query ->
        when {
            missing -> ProfileEditorState.Missing
            // Deleted while open (only an existing profile can disappear).
            profileId != null && saved != null && allProfiles.none { it.id == profileId } -> ProfileEditorState.Missing
            saved == null || draft == null || data == null -> ProfileEditorState.Loading
            else -> {
                val namesById = allProfiles.associate { it.id to it.name }
                val hasChanges = draft.copy(name = draft.name.trim()) != saved
                ProfileEditorState.Loaded(
                    isNew = profileId == null,
                    name = draft.name,
                    apps = appPickerItems(data.installed, draft.packageNames, data.policy, query),
                    selectedCount = draft.packageNames.size,
                    query = query,
                    linkedTags = allTags.filter { it.uuid in draft.tagUuids },
                    otherTags = allTags.filter { it.uuid !in draft.tagUuids }
                        .map { TagChoice(it, it.profileId?.takeIf { id -> id != profileId }?.let(namesById::get)) },
                    hasChanges = hasChanges,
                    canSave = (hasChanges || profileId == null) && draft.name.isNotBlank(),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileEditorState.Loading)

    fun setName(name: String) = editDraft { it.copy(name = name) }

    fun setQuery(text: String) {
        query.value = text
    }

    fun setAppSelected(packageName: String, selected: Boolean) {
        // Protected apps can't be added, but can always be removed.
        if (selected && appsData.value?.policy?.canBlock(packageName) == false) return
        editDraft { it.copy(packageNames = if (selected) it.packageNames + packageName else it.packageNames - packageName) }
    }

    /** Links a tag in the draft; on save it moves here from any other profile (D-003). */
    fun linkTag(uuid: UUID) = editDraft { it.copy(tagUuids = it.tagUuids + uuid) }

    fun unlinkTag(uuid: UUID) = editDraft { it.copy(tagUuids = it.tagUuids - uuid) }

    private val _scan = MutableStateFlow<EditorScan?>(null)
    val scan: StateFlow<EditorScan?> = _scan.asStateFlow()
    private var scanJob: Job? = null
    private var pairing: PairingFlow? = null

    /** Wanted only while "Scan a tag" is open. */
    private val claim = ScanClaim(tagEvents)

    fun setResumed(resumed: Boolean) = claim.setResumed(resumed)

    override fun onCleared() = claim.close()

    /** Listens for a tag: a paired one is linked, an unknown one is paired first. */
    fun startScan() {
        stopScan()
        val flow = PairingFlow(tags, names::tag, ioDispatcher)
        pairing = flow
        claim.setWanted(true)
        scanJob = viewModelScope.launch {
            launch { tagEvents.scans.collect { tag -> if (claim.active) launch { flow.onTagScanned(tag) } } }
            flow.state.collect { state ->
                when (state) {
                    is PairingState.AlreadyPaired -> onKnownTag(state.tag)
                    is PairingState.Paired -> _scan.value = EditorScan.NameNewTag(state.tag)
                    else -> _scan.value = EditorScan.Pairing(state)
                }
            }
        }
    }

    fun stopScan() {
        claim.setWanted(false)
        scanJob?.cancel()
        scanJob = null
        pairing = null
        _scan.value = null
    }

    fun confirmOverwrite() = withPairing { it.confirmOverwrite() }
    fun adopt() = withPairing { it.adopt() }
    fun restartScan() = withPairing { it.restart() }

    /** From [EditorScan.ConfirmMove]: link the tag here; it moves on save. */
    fun confirmMove() {
        val move = _scan.value as? EditorScan.ConfirmMove ?: return
        linkTag(move.tag.uuid)
        stopScan()
    }

    /** From [EditorScan.NameNewTag]: the new tag is already stored; name it and link it here. */
    fun finishNewTag(name: String) {
        val tag = (_scan.value as? EditorScan.NameNewTag)?.tag ?: return
        viewModelScope.launch {
            if (name.isNotBlank() && name.trim() != tag.name) tags.rename(tag.uuid, name.trim())
        }
        linkTag(tag.uuid)
        stopScan()
    }

    private suspend fun onKnownTag(tag: PairedTag) {
        val inDraft = draft.value?.tagUuids?.contains(tag.uuid) == true
        val otherProfile = tag.profileId?.takeIf { it != profileId }
        when {
            inDraft -> _scan.value = EditorScan.AlreadyHere(tag.name)
            otherProfile != null -> {
                val name = profiles.observe(otherProfile).first()?.name.orEmpty()
                _scan.value = EditorScan.ConfirmMove(tag, name)
            }
            else -> {
                linkTag(tag.uuid)
                stopScan()
            }
        }
    }

    private fun withPairing(action: suspend (PairingFlow) -> Unit) {
        val flow = pairing ?: return
        viewModelScope.launch { action(flow) }
    }

    fun save() {
        val current = draft.value ?: return
        if (current.name.isBlank()) return
        viewModelScope.launch {
            // A linked tag may have been forgotten meanwhile; only link tags that still exist.
            val existing = tags.observeAll().first().mapTo(mutableSetOf()) { it.uuid }
            profiles.save(profileId, current.name.trim(), current.packageNames, current.tagUuids intersect existing)
            _closed.value = true
        }
    }

    fun delete() {
        val id = profileId ?: return
        viewModelScope.launch {
            profiles.delete(id)
            _closed.value = true
        }
    }

    private fun editDraft(change: (Draft) -> Draft) = draft.update { it?.let(change) }
}
