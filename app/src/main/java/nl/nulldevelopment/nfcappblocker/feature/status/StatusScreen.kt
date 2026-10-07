package nl.nulldevelopment.nfcappblocker.feature.status

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.ui.components.AppIcon
import nl.nulldevelopment.nfcappblocker.ui.components.ResumedEffect
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTags: () -> Unit,
    viewModel: StatusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(true) }
    ResumedEffect { resumed ->
        if (resumed) {
            viewModel.refresh()
            notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.status_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { innerPadding ->
        val loaded = state as? StatusState.Loaded ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!loaded.serviceEnabled) {
                Notice(
                    text = stringResource(
                        if (loaded.activeProfileName != null) R.string.status_service_off_while_blocking else R.string.status_service_off,
                    ),
                    hint = stringResource(R.string.status_service_off_hint),
                    button = stringResource(R.string.status_open_accessibility),
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    urgent = true,
                )
            }
            if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Notice(
                    text = stringResource(R.string.status_notifications_off),
                    hint = null,
                    button = stringResource(R.string.status_allow_notifications),
                    onClick = { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    urgent = false,
                )
            }
            BlockingCard(loaded)
            when (loaded.setup) {
                StatusState.Setup.NoProfiles -> SetupHint(stringResource(R.string.status_setup_no_profiles), stringResource(R.string.status_go_profiles), onOpenProfiles)
                StatusState.Setup.NoLinkedTags -> SetupHint(stringResource(R.string.status_setup_no_tags), stringResource(R.string.status_go_tags), onOpenTags)
                StatusState.Setup.Ready -> Unit
            }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

@Composable
private fun BlockingCard(state: StatusState.Loaded) {
    Card(Modifier.fillMaxWidth()) {
        // Polite live region: TalkBack announces when blocking starts or stops while this is open.
        Column(
            modifier = Modifier
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.activeProfileName == null) {
                Text(stringResource(R.string.status_not_blocking), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.status_not_blocking_hint), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    stringResource(R.string.status_blocking, state.activeProfileName),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                state.since?.let { Text(stringResource(R.string.status_since, timeFormat.format(it)), style = MaterialTheme.typography.bodyLarge) }
                Text(stringResource(R.string.status_blocking_hint), style = MaterialTheme.typography.bodyLarge)
                Text(
                    pluralStringResource(R.plurals.status_blocked_apps, state.blockedApps.size, state.blockedApps.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
                )
                state.blockedApps.forEach { app ->
                    Row(
                        modifier = Modifier.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(app.packageName, size = 32.dp)
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, hint: String?, button: String, onClick: () -> Unit, urgent: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (urgent) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val color = if (urgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
            Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
            hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = color) }
            Button(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(button) }
        }
    }
}

@Composable
private fun SetupHint(text: String, button: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(button) }
    }
}
