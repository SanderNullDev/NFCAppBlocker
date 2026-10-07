package nl.nulldevelopment.nfcappblocker.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.data.settings.CountdownLength
import nl.nulldevelopment.nfcappblocker.ui.components.BackButton
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) }, navigationIcon = { BackButton(onBack) })
        },
    ) { innerPadding ->
        val loaded = state as? SettingsState.Loaded ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.settings_countdown_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.settings_countdown_body), style = MaterialTheme.typography.bodyMedium)
            CountdownSlider(loaded.countdownMinutes, enabled = !loaded.countdownLocked, onChange = viewModel::setCountdownMinutes)
            if (loaded.countdownLocked) {
                Text(stringResource(R.string.settings_countdown_locked), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The countdown length, 1–20 minutes; also used in onboarding. */
@Composable
fun CountdownSlider(minutes: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    // Follows the drag locally and saves when it's let go, instead of writing on every step.
    var dragging by remember(minutes) { mutableFloatStateOf(minutes.toFloat()) }
    val shown = dragging.roundToInt()
    val label = pluralStringResource(R.plurals.settings_minutes, shown, shown)
    Text(label, style = MaterialTheme.typography.headlineSmall)
    Slider(
        value = dragging,
        onValueChange = { dragging = it },
        onValueChangeFinished = { onChange(dragging.roundToInt()) },
        valueRange = CountdownLength.MIN_MINUTES.toFloat()..CountdownLength.MAX_MINUTES.toFloat(),
        steps = CountdownLength.MAX_MINUTES - CountdownLength.MIN_MINUTES - 1,
        enabled = enabled,
        modifier = Modifier.semantics { stateDescription = label },
    )
}
