package nl.nulldevelopment.nfcappblocker.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import nl.nulldevelopment.nfcappblocker.di.IoDispatcher
import nl.nulldevelopment.nfcappblocker.feature.pairing.PairingFlow
import nl.nulldevelopment.nfcappblocker.feature.pairing.PairingState
import nl.nulldevelopment.nfcappblocker.nfc.NfcTagEvents
import nl.nulldevelopment.nfcappblocker.nfc.ScanClaim
import nl.nulldevelopment.nfcappblocker.util.DefaultNameFormats
import javax.inject.Inject

/**
 * The onboarding tag step: pairs a new tag or recognises a paired one, with the same rules as
 * "Add tag" (D-019). Claims scans only while the step is on screen (D-028).
 */
@HiltViewModel
class OnboardingTagViewModel @Inject constructor(
    repository: TagRepository,
    names: DefaultNameFormats,
    private val tagEvents: NfcTagEvents,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val flow = PairingFlow(repository, names::tag, ioDispatcher)
    val state: StateFlow<PairingState> = flow.state

    private val claim = ScanClaim(tagEvents)

    init {
        viewModelScope.launch {
            tagEvents.scans.collect { tag -> if (claim.active) launch { flow.onTagScanned(tag) } }
        }
    }

    fun setResumed(resumed: Boolean) = claim.setResumed(resumed)

    /** True while the tag step is shown. */
    fun setListening(listening: Boolean) = claim.setWanted(listening)

    override fun onCleared() = claim.close()

    fun confirmOverwrite() = act { flow.confirmOverwrite() }
    fun adopt() = act { flow.adopt() }
    fun restart() = act { flow.restart() }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
