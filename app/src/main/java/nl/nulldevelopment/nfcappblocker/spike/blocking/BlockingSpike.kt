package nl.nulldevelopment.nfcappblocker.spike.blocking

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private val Context.blockingSpikeStore by preferencesDataStore(name = "blocking_spike")

/**
 * State for spike A (Phase 0, throwaway): whether blocking is on, which apps it blocks, and
 * what the service did, for measuring latency and survival.
 *
 * The on/off flag is persisted so the reboot and process-death tests mean something. The logs
 * are deliberately in memory only: after a reboot an empty log with a fresh "connected" entry
 * shows the service came back on its own.
 */
object BlockingSpike {

    /** Hard-coded test list until block profiles exist (Phase 2). */
    val TEST_APPS = mapOf(
        "com.instagram.android" to "Instagram",
        "com.android.chrome" to "Chrome",
    )

    private const val MAX_ENTRIES = 20
    private val ENABLED = booleanPreferencesKey("enabled")

    fun enabled(context: Context): Flow<Boolean> =
        context.applicationContext.blockingSpikeStore.data.map { it[ENABLED] ?: false }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.blockingSpikeStore.edit { it[ENABLED] = enabled }
        logEvent(if (enabled) "Blocking turned on" else "Blocking turned off")
    }

    data class Event(val timeMillis: Long, val sinceBootMillis: Long, val text: String)

    data class Latency(val timeMillis: Long, val appName: String, val millis: Long)

    private val _events = MutableStateFlow<List<Event>>(emptyList())

    /** Newest first. */
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _latencies = MutableStateFlow<List<Latency>>(emptyList())

    /** Newest first. */
    val latencies: StateFlow<List<Latency>> = _latencies.asStateFlow()

    fun logEvent(text: String) {
        val event = Event(System.currentTimeMillis(), SystemClock.elapsedRealtime(), text)
        _events.update { (listOf(event) + it).take(MAX_ENTRIES) }
    }

    fun recordLatency(packageName: String, millis: Long) {
        val latency = Latency(System.currentTimeMillis(), appName(packageName), millis)
        _latencies.update { (listOf(latency) + it).take(MAX_ENTRIES) }
    }

    fun appName(packageName: String): String = TEST_APPS[packageName] ?: packageName
}
