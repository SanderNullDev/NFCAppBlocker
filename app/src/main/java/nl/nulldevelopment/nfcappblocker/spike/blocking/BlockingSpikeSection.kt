package nl.nulldevelopment.nfcappblocker.spike.blocking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.nulldevelopment.nfcappblocker.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BlockingSpikeState(
    val serviceEnabled: Boolean,
    val blockingOn: Boolean,
    val latencies: List<BlockingSpike.Latency>,
    val events: List<BlockingSpike.Event>,
)

/** Spike A controls and measurements, shown on the main screen. Throwaway. */
fun LazyListScope.blockingSpikeItems(
    state: BlockingSpikeState,
    onBlockingChange: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    item {
        Text(
            stringResource(R.string.blocking_spike_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(top = 8.dp)
                .semantics { heading() },
        )
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    if (state.serviceEnabled) R.string.blocking_service_on else R.string.blocking_service_off,
                ),
            )
            if (!state.serviceEnabled) {
                OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.blocking_open_settings))
                }
            }
        }
    }
    item { BlockingToggle(state.blockingOn, onBlockingChange) }
    item {
        Text(
            stringResource(R.string.blocking_latency_heading),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
    }
    if (state.latencies.isEmpty()) {
        item { Text(stringResource(R.string.blocking_none_yet)) }
    } else {
        items(state.latencies.size) { index ->
            val latency = state.latencies[index]
            Text(
                stringResource(
                    R.string.blocking_latency_entry,
                    timeFormat.format(Date(latency.timeMillis)),
                    latency.appName,
                    latency.millis,
                ),
            )
        }
    }
    item {
        Text(
            stringResource(R.string.blocking_events_heading),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
    }
    if (state.events.isEmpty()) {
        item { Text(stringResource(R.string.blocking_events_empty)) }
    } else {
        items(state.events.size) { index ->
            val event = state.events[index]
            Text(
                stringResource(
                    R.string.blocking_event_entry,
                    timeFormat.format(Date(event.timeMillis)),
                    event.text,
                    event.sinceBootMillis / 1_000,
                ),
            )
        }
    }
}

@Composable
private fun BlockingToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.blocking_toggle_title), style = MaterialTheme.typography.titleMedium)
                // State spelled out in text, not only by the switch.
                Text(
                    stringResource(
                        if (on) R.string.blocking_toggle_on else R.string.blocking_toggle_off,
                        BlockingSpike.TEST_APPS.values.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = on, onCheckedChange = null)
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
