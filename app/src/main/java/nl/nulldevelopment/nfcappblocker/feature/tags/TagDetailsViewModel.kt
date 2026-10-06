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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import nl.nulldevelopment.nfcappblocker.di.IoDispatcher
import nl.nulldevelopment.nfcappblocker.nfc.NfcTagEvents
import nl.nulldevelopment.nfcappblocker.nfc.ScannedTag
import nl.nulldevelopment.nfcappblocker.nfc.TagWiper
import nl.nulldevelopment.nfcappblocker.nfc.WipeResult
import nl.nulldevelopment.nfcappblocker.nfc.toScannedTag
import java.util.UUID

sealed interface TagDetailsState {
    data object Loading : TagDetailsState
    data class Loaded(val tag: PairedTag) : TagDetailsState

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
    private val tagEvents: NfcTagEvents,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(uuid: UUID): TagDetailsViewModel
    }

    val state: StateFlow<TagDetailsState> = repository.observe(uuid)
        .map { tag -> if (tag == null) TagDetailsState.Missing else TagDetailsState.Loaded(tag) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagDetailsState.Loading)

    private val _wipe = MutableStateFlow<WipeState>(WipeState.Idle)
    val wipe: StateFlow<WipeState> = _wipe.asStateFlow()
    private var wipeJob: Job? = null

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.rename(uuid, name.trim()) }
    }

    /** Removes the tag from the list; the tag itself keeps its content. */
    fun forget() {
        viewModelScope.launch { repository.remove(uuid) }
    }

    /** Waits for this tag to be held against the phone, wipes it, then forgets it. */
    fun startWipe() {
        wipeJob?.cancel()
        _wipe.value = WipeState.WaitingForTag()
        wipeJob = viewModelScope.launch {
            tagEvents.tags.collect { tag -> onTagForWipe(tag.toScannedTag()) }
        }
    }

    fun cancelWipe() {
        wipeJob?.cancel()
        _wipe.value = WipeState.Idle
    }

    private suspend fun onTagForWipe(tag: ScannedTag) {
        if (_wipe.value == WipeState.Wiping) return
        _wipe.value = WipeState.Wiping
        when (val result = withContext(ioDispatcher) { TagWiper.wipe(tag, uuid) }) {
            WipeResult.Wiped -> {
                repository.remove(uuid)
                wipeJob?.cancel()
                _wipe.value = WipeState.Idle
            }
            WipeResult.DifferentTag -> _wipe.value = WipeState.WaitingForTag(otherTagTapped = true)
            else -> _wipe.value = WipeState.Failed(result)
        }
    }
}
