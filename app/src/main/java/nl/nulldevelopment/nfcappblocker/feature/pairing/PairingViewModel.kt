package nl.nulldevelopment.nfcappblocker.feature.pairing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import nl.nulldevelopment.nfcappblocker.di.IoDispatcher
import nl.nulldevelopment.nfcappblocker.nfc.NfcTagEvents
import nl.nulldevelopment.nfcappblocker.nfc.toScannedTag
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val repository: TagRepository,
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
            tagEvents.tags.collect { tag -> launch { flow.onTagScanned(tag.toScannedTag()) } }
        }
    }

    fun confirmOverwrite() = act { flow.confirmOverwrite() }
    fun adopt() = act { flow.adopt() }
    fun restart() = act { flow.restart() }

    fun rename(uuid: UUID, name: String) = act {
        if (name.isNotBlank()) repository.rename(uuid, name.trim())
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
