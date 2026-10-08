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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import nl.nulldevelopment.nfcappblocker.ui.components.NfcStatus
import nl.nulldevelopment.nfcappblocker.ui.components.ResumedEffect
import nl.nulldevelopment.nfcappblocker.ui.components.rememberNfcStatus
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
fun SettingsScreen(onBack: () -> Unit, onOpenTags: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
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
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            // Tags are rarely managed once set up, so they live here instead of on the home screen (D-043).
            SettingsRow(stringResource(R.string.settings_tags), stringResource(R.string.settings_tags_body), onOpenTags)
            // Moved here from the home screen, which only shows problems the user can fix (D-046).
            val context = LocalContext.current
            var notificationsOn by remember { mutableStateOf(true) }
            ResumedEffect { if (it) notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled() }
            SettingsRow(
                title = stringResource(R.string.settings_notifications),
                body = stringResource(if (notificationsOn) R.string.settings_notifications_on else R.string.settings_notifications_off),
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                },
            )
            val nfc = rememberNfcStatus()
            SettingsRow(
                title = stringResource(R.string.settings_nfc),
                body = stringResource(
                    when (nfc) {
                        NfcStatus.ON -> R.string.settings_nfc_on
                        NfcStatus.OFF -> R.string.settings_nfc_off
                        NfcStatus.MISSING -> R.string.nfc_missing
                    },
                ),
                onClick = if (nfc == NfcStatus.MISSING) null else { { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) } },
            )
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

@Composable
private fun SettingsRow(title: String, body: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}
