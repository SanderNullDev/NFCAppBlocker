package nl.nulldevelopment.nfcappblocker.feature.pairing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import nl.nulldevelopment.nfcappblocker.di.IoDispatcher
import nl.nulldevelopment.nfcappblocker.nfc.NfcTagEvents
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val repository: TagRepository,
    private val profileRepository: ProfileRepository,
    tagEvents: NfcTagEvents,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val flow = PairingFlow(
        repository = repository,
        defaultName = { context.getString(R.string.tag_default_name, it) },
        ioDispatcher = ioDispatcher,
    )

    val state: StateFlow<PairingState> = flow.state

    init {
        viewModelScope.launch {
            // Each scan in its own coroutine, so PairingFlow can drop a scan that arrives mid-write.
            tagEvents.scans.collect { tag -> launch { flow.onTagScanned(tag) } }
        }
    }

    fun confirmOverwrite() = act { flow.confirmOverwrite() }
    fun adopt() = act { flow.adopt() }
    fun restart() = act { flow.restart() }

    /** Profiles to offer for linking the new tag. */
    val profiles: StateFlow<List<Profile>> =
        profileRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saves the name and profile chosen for a newly added tag. */
    fun finish(uuid: UUID, name: String, profileId: Long?) = act {
        if (name.isNotBlank()) repository.rename(uuid, name.trim())
        if (profileId != null) repository.setProfile(uuid, profileId)
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
