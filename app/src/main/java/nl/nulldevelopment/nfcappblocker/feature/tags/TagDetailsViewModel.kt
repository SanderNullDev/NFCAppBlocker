package nl.nulldevelopment.nfcappblocker.feature.tags

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import nl.nulldevelopment.nfcappblocker.di.IoDispatcher
import nl.nulldevelopment.nfcappblocker.nfc.NfcTagEvents
import nl.nulldevelopment.nfcappblocker.nfc.ScanClaim
import nl.nulldevelopment.nfcappblocker.nfc.ScannedTag
import nl.nulldevelopment.nfcappblocker.nfc.TagWiper
import nl.nulldevelopment.nfcappblocker.nfc.WipeResult
import java.util.UUID

sealed interface TagDetailsState {
    data object Loading : TagDetailsState
    /** [profiles] are the choices for linking; [profile] is the one this tag is linked to. */
    data class Loaded(val tag: PairedTag, val profile: Profile?, val profiles: List<Profile>) : TagDetailsState

    /** Forgotten, wiped, or never existed. */
    data object Missing : TagDetailsState
}

sealed interface WipeState {
    data object Idle : WipeState
    data class WaitingForTag(val otherTagTapped: Boolean = false) : WipeState
    data object Wiping : WipeState
    data class Failed(val result: WipeResult) : WipeState
}

@HiltViewModel(assistedFactory = TagDetailsViewModel.Factory::class)
class TagDetailsViewModel @AssistedInject constructor(
    @Assisted private val uuid: UUID,
    private val repository: TagRepository,
    private val profileRepository: ProfileRepository,
    private val tagEvents: NfcTagEvents,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(uuid: UUID): TagDetailsViewModel
    }

    val state: StateFlow<TagDetailsState> = combine(repository.observe(uuid), profileRepository.observeAll()) { tag, profiles ->
        if (tag == null) {
            TagDetailsState.Missing
        } else {
            TagDetailsState.Loaded(tag, profiles.firstOrNull { it.id == tag.profileId }, profiles)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagDetailsState.Loading)

    private val _wipe = MutableStateFlow<WipeState>(WipeState.Idle)
    val wipe: StateFlow<WipeState> = _wipe.asStateFlow()
    private var wipeJob: Job? = null

    /** Wanted only while waiting to wipe. */
    private val claim = ScanClaim(tagEvents)

    fun setResumed(resumed: Boolean) = claim.setResumed(resumed)

    override fun onCleared() = claim.close()

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.rename(uuid, name.trim()) }
    }

    /** Links this tag to a profile (moving it if needed, D-003), or unlinks it with null. */
    fun setProfile(profileId: Long?) {
        viewModelScope.launch { repository.setProfile(uuid, profileId) }
    }

    /** Removes the tag from the list; the tag itself keeps its content. */
    fun forget() {
        viewModelScope.launch { repository.remove(uuid) }
    }

    /** Waits for this tag to be held against the phone, wipes it, then forgets it. */
    fun startWipe() {
        wipeJob?.cancel()
        _wipe.value = WipeState.WaitingForTag()
        claim.setWanted(true)
        wipeJob = viewModelScope.launch {
            tagEvents.scans.collect { tag -> if (claim.active) onTagForWipe(tag) }
        }
    }

    fun cancelWipe() {
        claim.setWanted(false)
        wipeJob?.cancel()
        _wipe.value = WipeState.Idle
    }

    private suspend fun onTagForWipe(tag: ScannedTag) {
        if (_wipe.value == WipeState.Wiping) return
        _wipe.value = WipeState.Wiping
        when (val result = withContext(ioDispatcher) { TagWiper.wipe(tag, uuid) }) {
            WipeResult.Wiped -> {
                repository.remove(uuid)
                claim.setWanted(false)
                wipeJob?.cancel()
                _wipe.value = WipeState.Idle
            }
            WipeResult.DifferentTag -> _wipe.value = WipeState.WaitingForTag(otherTagTapped = true)
            else -> _wipe.value = WipeState.Failed(result)
        }
    }
}
