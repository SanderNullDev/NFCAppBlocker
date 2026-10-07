package nl.nulldevelopment.nfcappblocker.feature.status

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.feature.escape.EscapeActivity
import nl.nulldevelopment.nfcappblocker.ui.components.AppIcon
import nl.nulldevelopment.nfcappblocker.ui.components.NfcStatus
import nl.nulldevelopment.nfcappblocker.ui.components.ResumedEffect
import nl.nulldevelopment.nfcappblocker.ui.components.rememberNfcStatus
import nl.nulldevelopment.nfcappblocker.ui.components.SoftCard
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
    val nfcStatus = rememberNfcStatus()
    // Covers both the Android 13+ permission and notifications switched off in Settings.
    var notificationsAllowed by remember { mutableStateOf(true) }
    ResumedEffect { resumed ->
        if (resumed) {
            viewModel.refresh()
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
    // After a refusal Android may stop showing the prompt, so the next try opens the settings.
    var notificationsRefused by rememberSaveable { mutableStateOf(false) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
        notificationsRefused = !granted
        viewModel.refresh()
    }
    val allowNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsRefused) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    var explainingService by rememberSaveable { mutableStateOf(false) }

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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val checklist = setupChecklist(
                nfc = nfcStatus,
                serviceEnabled = loaded.serviceEnabled,
                notificationsAllowed = notificationsAllowed,
                hasProfileWithApps = loaded.hasProfileWithApps,
                hasLinkedTag = loaded.hasLinkedTag,
            )
            if (checklist != null) {
                SetupCard(checklist, nfcStatus) { step ->
                    when (step) {
                        SetupStep.Nfc -> context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                        SetupStep.Service -> explainingService = true
                        SetupStep.Notifications -> allowNotifications()
                        SetupStep.Profile -> onOpenProfiles()
                        SetupStep.Tag -> onOpenTags()
                    }
                }
            }
            BlockingCard(loaded, onStopWithoutTag = { context.startActivity(EscapeActivity.intent(context, fromBlockScreen = false)) })
        }
    }

    if (explainingService) {
        ServiceDisclosure(
            onContinue = {
                explainingService = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = { explainingService = false },
        )
    }
}

@Composable
private fun SetupCard(items: List<SetupItem>, nfcStatus: NfcStatus, onAction: (SetupStep) -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                pluralStringResource(R.plurals.setup_progress, items.size, items.count { it.done }, items.size),
                style = MaterialTheme.typography.bodyLarge,
            )
            items.forEachIndexed { index, item ->
                SetupRow(number = index + 1, item = item, nfcStatus = nfcStatus, onAction = { onAction(item.step) })
            }
        }
    }
}

@Composable
private fun SetupRow(number: Int, item: SetupItem, nfcStatus: NfcStatus, onAction: () -> Unit) {
    val stateText = stringResource(if (item.done) R.string.setup_done else R.string.setup_to_do)
    val noNfc = item.step == SetupStep.Nfc && nfcStatus == NfcStatus.MISSING
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // The check mark and the step number are decoration; the state is read out as text.
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (item.done) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            } else {
                Box(
                    Modifier
                        .size(26.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(number.toString(), style = MaterialTheme.typography.labelLarge) }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (noNfc) R.string.setup_nfc_missing_title else item.step.title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { stateDescription = stateText },
            )
            if (!item.done) {
                Text(stringResource(if (noNfc) R.string.setup_nfc_missing_body else item.step.body), style = MaterialTheme.typography.bodyMedium)
                // Without NFC hardware there's nothing to fix.
                if (!noNfc) {
                    OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(item.step.action)) }
                }
            }
        }
    }
}

private val SetupStep.title: Int
    get() = when (this) {
        SetupStep.Nfc -> R.string.setup_nfc_title
        SetupStep.Service -> R.string.setup_service_title
        SetupStep.Notifications -> R.string.setup_notifications_title
        SetupStep.Profile -> R.string.setup_profile_title
        SetupStep.Tag -> R.string.setup_tag_title
    }

private val SetupStep.body: Int
    get() = when (this) {
        SetupStep.Nfc -> R.string.setup_nfc_body
        SetupStep.Service -> R.string.setup_service_body
        SetupStep.Notifications -> R.string.setup_notifications_body
        SetupStep.Profile -> R.string.setup_profile_body
        SetupStep.Tag -> R.string.setup_tag_body
    }

private val SetupStep.action: Int
    get() = when (this) {
        SetupStep.Nfc -> R.string.nfc_open_settings
        SetupStep.Service -> R.string.setup_service_action
        SetupStep.Notifications -> R.string.setup_notifications_action
        SetupStep.Profile -> R.string.setup_profile_action
        SetupStep.Tag -> R.string.setup_tag_action
    }

/** Shown before Accessibility settings, so the user knows what they're switching on and why. */
@Composable
private fun ServiceDisclosure(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.disclosure_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.disclosure_what))
                Text(stringResource(R.string.disclosure_not))
                Text(stringResource(R.string.status_service_off_hint))
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.disclosure_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.disclosure_not_now)) } },
    )
}

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

@Composable
private fun BlockingCard(state: StatusState.Loaded, onStopWithoutTag: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        // Polite live region: TalkBack announces when blocking starts or stops while this is open.
        Column(
            modifier = Modifier
                .padding(20.dp)
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
                if (!state.serviceEnabled) {
                    // The setup checklist above has the button to fix it.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            stringResource(R.string.status_service_off_while_blocking),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                state.since?.let { Text(stringResource(R.string.status_since, timeFormat.format(it)), style = MaterialTheme.typography.bodyLarge) }
                Text(stringResource(R.string.status_blocking_hint), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onStopWithoutTag, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.escape_start))
                }
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
