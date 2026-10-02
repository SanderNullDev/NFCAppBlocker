package nl.nulldevelopment.nfcappblocker.hceemulator.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.hceemulator.service.NdefHceService
import nl.nulldevelopment.nfcappblocker.hceemulator.type4.describeCommand
import nl.nulldevelopment.nfcappblocker.hceemulator.type4.describeResponse
import nl.nulldevelopment.nfcappblocker.hceemulator.type4.toHex
import nl.nulldevelopment.nfcappblocker.tagformat.TagMessage
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

private val Context.dataStore by preferencesDataStore(name = "emulator")

/**
 * Single source of truth shared by the HCE service and the UI, which run in the same process.
 */
class EmulatorRepository(context: Context, scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val store = PresetStore(appContext.dataStore)
    private val serviceComponent = ComponentName(appContext, NdefHceService::class.java)

    /** Null while the presets are loading from disk. */
    val settings: StateFlow<EmulatorSettings?> = store.settings.stateIn(scope, SharingStarted.Eagerly, null)

    private val _emulationEnabled = MutableStateFlow(isServiceEnabled())
    val emulationEnabled: StateFlow<Boolean> = _emulationEnabled.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Newest entry first. */
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()
    private val nextLogId = AtomicLong()

    init {
        scope.launch { store.ensureDefaults() }
    }

    suspend fun awaitSettings(): EmulatorSettings = settings.filterNotNull().first()

    /** The NDEF message for the active preset, or null while presets are still loading. */
    fun currentNdefMessage(): ByteArray? = settings.value?.let { TagMessage.encode(it.activePreset.uuid) }

    suspend fun selectPreset(id: String) = store.setActivePreset(id)

    suspend fun setUuid(presetId: String, uuid: UUID) = store.setUuid(presetId, uuid)

    /**
     * Turns the HCE service component on or off. When off, Android no longer routes the NDEF
     * AID to this app, so the phone stops answering as a tag.
     */
    fun setEmulationEnabled(enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        appContext.packageManager.setComponentEnabledSetting(serviceComponent, state, PackageManager.DONT_KILL_APP)
        _emulationEnabled.value = isServiceEnabled()
        logEvent(if (enabled) "Emulation enabled" else "Emulation disabled")
    }

    fun logReceived(command: ByteArray) =
        append(LogEntry.Kind.RECEIVED, describeCommand(command), command.toHex())

    fun logSent(response: ByteArray) =
        append(LogEntry.Kind.SENT, describeResponse(response), response.toHex())

    fun logEvent(summary: String) = append(LogEntry.Kind.EVENT, summary)

    fun clearLog() {
        _log.value = emptyList()
    }

    private fun append(kind: LogEntry.Kind, summary: String, hex: String? = null) {
        val entry = LogEntry(nextLogId.getAndIncrement(), System.currentTimeMillis(), kind, summary, hex)
        _log.update { (listOf(entry) + it).take(MAX_LOG_ENTRIES) }
    }

    private fun isServiceEnabled(): Boolean =
        when (appContext.packageManager.getComponentEnabledSetting(serviceComponent)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            // DEFAULT means "as declared in the manifest", where the service is enabled.
            else -> true
        }

    private companion object {
        const val MAX_LOG_ENTRIES = 200
    }
}
