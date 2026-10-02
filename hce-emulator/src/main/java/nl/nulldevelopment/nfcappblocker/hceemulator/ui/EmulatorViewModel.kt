package nl.nulldevelopment.nfcappblocker.hceemulator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.hceemulator.EmulatorApplication
import nl.nulldevelopment.nfcappblocker.hceemulator.data.EmulatorRepository
import nl.nulldevelopment.nfcappblocker.hceemulator.data.EmulatorSettings
import nl.nulldevelopment.nfcappblocker.hceemulator.data.LogEntry
import nl.nulldevelopment.nfcappblocker.hceemulator.type4.toHex
import nl.nulldevelopment.nfcappblocker.tagformat.TagMessage
import java.util.UUID

data class EmulatorUiState(
    val settings: EmulatorSettings? = null,
    val emulationEnabled: Boolean = false,
    val ndefMessageHex: String = "",
    val ndefMessageSize: Int = 0,
    val log: List<LogEntry> = emptyList(),
)

class EmulatorViewModel(private val repository: EmulatorRepository) : ViewModel() {

    val uiState: StateFlow<EmulatorUiState> =
        combine(repository.settings, repository.emulationEnabled, repository.log) { settings, enabled, log ->
            val message = settings?.let { TagMessage.encode(it.activePreset.uuid) } ?: ByteArray(0)
            EmulatorUiState(settings, enabled, message.toHex(), message.size, log)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmulatorUiState())

    fun selectPreset(id: String) {
        viewModelScope.launch { repository.selectPreset(id) }
    }

    fun setUuid(presetId: String, uuid: UUID) {
        viewModelScope.launch { repository.setUuid(presetId, uuid) }
    }

    fun generateUuid(presetId: String) = setUuid(presetId, UUID.randomUUID())

    fun setEmulationEnabled(enabled: Boolean) = repository.setEmulationEnabled(enabled)

    fun clearLog() = repository.clearLog()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                EmulatorViewModel((this[APPLICATION_KEY] as EmulatorApplication).repository)
            }
        }
    }
}
