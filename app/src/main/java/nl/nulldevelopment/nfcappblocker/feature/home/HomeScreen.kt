package nl.nulldevelopment.nfcappblocker.feature.home

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.draw.clip
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
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenBlockList: (Long) -> Unit,
    onNewBlockList: () -> Unit,
    onAddTag: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
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
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = { AddButton(onNewBlockList = onNewBlockList, onAddTag = onAddTag) },
    ) { innerPadding ->
        val loaded = state as? HomeState.Loaded ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
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
                        SetupStep.Profile -> onNewBlockList()
                        SetupStep.Tag -> onAddTag()
                    }
                }
            }
            BlockingCard(loaded, onUnlock = { context.startActivity(EscapeActivity.intent(context, fromBlockScreen = false)) })
            StatsSection(loaded.period, loaded.stats, onPeriod = viewModel::setPeriod)
            BlockListsSection(loaded.blockLists, onOpen = onOpenBlockList)
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
private fun BlockingCard(state: HomeState.Loaded, onUnlock: () -> Unit) {
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
                Text(stringResource(R.string.status_not_blocking_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Decoration only: the heading says it in words.
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    stringResource(R.string.status_blocking, state.activeProfileName),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
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
            val since = state.since?.let { stringResource(R.string.status_since, timeFormat.format(it)) }
            val apps = state.blockedApps.joinToString(", ") { it.label }
            Text(
                listOfNotNull(since, apps.takeIf { it.isNotEmpty() }).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.status_blocking_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onUnlock, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.escape_start))
            }
        }
    }
}

/** The four numbers (D-044), with a switch between today, the last 7 days and all time. */
@Composable
private fun StatsSection(period: StatsPeriod, stats: UsageStats, onPeriod: (StatsPeriod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StatsPeriod.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == period,
                    onClick = { onPeriod(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, StatsPeriod.entries.size),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            when (option) {
                                StatsPeriod.Today -> R.string.stats_today
                                StatsPeriod.Week -> R.string.stats_week
                                StatsPeriod.AllTime -> R.string.stats_all_time
                            },
                        ),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(formatDuration(stats.timeBlocked), stringResource(R.string.stats_time_blocked), Modifier.weight(1f))
            StatTile(stats.timesBlocked.toString(), stringResource(R.string.stats_times_blocked), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stats.appsStopped.toString(), stringResource(R.string.stats_apps_stopped), Modifier.weight(1f))
            StatTile(stats.stoppedWithoutTag.toString(), stringResource(R.string.stats_without_tag), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    // Read as one item: "14 h 20 min, Time blocked".
    SoftCard(modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return if (hours == 0L) stringResource(R.string.stats_minutes, minutes) else stringResource(R.string.stats_hours_minutes, hours, minutes)
}

@Composable
private fun BlockListsSection(lists: List<BlockListSummary>, onOpen: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.profiles_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (lists.isEmpty()) {
            Text(stringResource(R.string.profiles_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        SoftCard(Modifier.fillMaxWidth()) {
            lists.forEachIndexed { index, list ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                BlockListRow(list, onClick = { onOpen(list.id) })
            }
        }
    }
}

@Composable
private fun BlockListRow(list: BlockListSummary, onClick: () -> Unit) {
    val apps = pluralStringResource(R.plurals.profiles_app_count, list.appCount, list.appCount)
    val tags = if (list.tagNames.isEmpty()) stringResource(R.string.home_no_tag) else stringResource(R.string.home_tags, list.tagNames.joinToString(", "))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(list.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(apps, tags, if (list.active) stringResource(R.string.home_blocking_now) else null).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** Add: a new block list, or a tag (D-043). */
@Composable
private fun AddButton(onNewBlockList: () -> Unit, onAddTag: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ExtendedFloatingActionButton(
            onClick = { open = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.home_add)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profiles_new)) },
                onClick = {
                    open = false
                    onNewBlockList()
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tags_add)) },
                onClick = {
                    open = false
                    onAddTag()
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}
