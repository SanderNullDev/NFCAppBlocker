package nl.nulldevelopment.nfcappblocker.feature.home

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.annotation.PluralsRes
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import nl.nulldevelopment.nfcappblocker.ui.components.SoftCard
import nl.nulldevelopment.nfcappblocker.ui.components.rememberNfcStatus
import java.time.Duration
import java.time.LocalTime

/**
 * The one home screen (D-043, D-046): the wordmark and a greeting, at most one problem, the
 * blocking status, time blocked, and the block lists. Everything scrolls together.
 */
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
    var showingChecklist by rememberSaveable { mutableStateOf(false) }

    Scaffold(floatingActionButton = { AddButton(onNewBlockList = onNewBlockList, onAddTag = onAddTag) }) { innerPadding ->
        val loaded = state as? HomeState.Loaded ?: return@Scaffold
        val fix: (HomeProblem) -> Unit = { problem ->
            when (problem) {
                HomeProblem.ServiceOff -> explainingService = true
                HomeProblem.NfcOff -> context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                HomeProblem.NoBlockList -> onNewBlockList()
                HomeProblem.NoTag -> onAddTag()
            }
        }
        // The header is part of the content, so it scrolls away with it (D-045).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Greeting(loaded, onOpenSettings)
            homeProblem(loaded.serviceEnabled, nfcStatus, loaded.hasProfileWithApps, loaded.hasLinkedTag)?.let { problem ->
                ProblemBanner(
                    problem = problem,
                    urgent = problem == HomeProblem.ServiceOff && loaded.activeProfileName != null,
                    onFix = { fix(problem) },
                    onShowAll = { showingChecklist = true },
                )
            }
            StatusCard(loaded, onUnlock = { context.startActivity(EscapeActivity.intent(context, fromBlockScreen = false)) })
            StatsSection(loaded.period, loaded.stats, onPeriod = viewModel::setPeriod)
            BlockListsSection(loaded.blockLists, onOpen = onOpenBlockList)
        }

        if (showingChecklist) {
            val checklist = setupChecklist(nfcStatus, loaded.serviceEnabled, notificationsAllowed, loaded.hasProfileWithApps, loaded.hasLinkedTag)
            ModalBottomSheet(onDismissRequest = { showingChecklist = false }) {
                if (checklist != null) {
                    Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                        SetupCard(checklist, nfcStatus) { step ->
                            showingChecklist = false
                            when (step) {
                                SetupStep.Nfc -> context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                                SetupStep.Service -> explainingService = true
                                SetupStep.Notifications -> allowNotifications()
                                SetupStep.Profile -> onNewBlockList()
                                SetupStep.Tag -> onAddTag()
                            }
                        }
                    }
                }
            }
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

/** The greeting is the screen's only title, with Settings beside it; it scrolls with the rest (D-045). */
@Composable
private fun Greeting(state: HomeState.Loaded, onOpenSettings: () -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> R.string.home_greeting_morning
        in 12..17 -> R.string.home_greeting_afternoon
        in 18..23 -> R.string.home_greeting_evening
        else -> R.string.home_greeting_night
    }
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(greeting),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // The line names the tag to use, so there's no separate "not blocking" card (D-046).
        val line = when {
            state.activeProfileName != null -> stringResource(R.string.home_line_blocking)
            state.tagNames.size == 1 -> stringResource(R.string.home_line_free_tag, state.tagNames.single())
            state.tagNames.size > 1 -> stringResource(R.string.home_line_free_tags)
            else -> stringResource(R.string.home_line_free)
        }
        Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The most urgent problem in one slim banner (D-046). [urgent] when blocking is on but not
 * working; otherwise it's a gentle note. Tapping the text shows the full checklist.
 */
@Composable
private fun ProblemBanner(problem: HomeProblem, urgent: Boolean, onFix: () -> Unit, onShowAll: () -> Unit) {
    val container = if (urgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (urgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = RoundedCornerShape(16.dp), color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                stringResource(problem.message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onShowAll)
                    .padding(vertical = 12.dp),
            )
            TextButton(
                onClick = onFix,
                colors = ButtonDefaults.textButtonColors(contentColor = content),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(problem.action)) }
        }
    }
}

private val HomeProblem.message: Int
    get() = when (this) {
        HomeProblem.ServiceOff -> R.string.problem_service_off
        HomeProblem.NfcOff -> R.string.problem_nfc_off
        HomeProblem.NoBlockList -> R.string.problem_no_block_list
        HomeProblem.NoTag -> R.string.problem_no_tag
    }

private val HomeProblem.action: Int
    get() = when (this) {
        HomeProblem.ServiceOff -> R.string.problem_fix
        HomeProblem.NfcOff -> R.string.problem_fix
        HomeProblem.NoBlockList -> R.string.problem_create
        HomeProblem.NoTag -> R.string.problem_add
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


/** Only while blocking: sage, with the apps, the time so far and "Unlock now". */
@Composable
private fun StatusCard(state: HomeState.Loaded, onUnlock: () -> Unit) {
    // Polite live region: TalkBack announces when blocking starts or stops while this is open.
    val announce = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    // Only while blocking: when nothing blocks, the greeting's line says so (D-046).
    val name = state.activeProfileName ?: return
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(announce.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Text(stringResource(R.string.home_active, name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            // No start time: the counter below already says how long it has been running.
            AppNamesThatFit(
                names = state.blockedApps.map { it.label },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primaryContainer,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.blockingFor?.let { formatDuration(it) }.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onUnlock,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.escape_start)) }
            }
        }
    }
}

/**
 * The blocked apps' names, as many as fit in [maxLines] at the current width and font size,
 * ending with "and N others" for the rest, so a long block list doesn't fill the card.
 */
@Composable
private fun AppNamesThatFit(names: List<String>, style: TextStyle, color: Color, maxLines: Int = 3) {
    if (names.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val others = names.indices.associateWith { shown -> pluralStringResource(R.plurals.home_apps_more, names.size - shown, names.size - shown) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        val text = remember(names, width, style, others) {
            fun label(shown: Int) = if (shown == names.size) {
                names.joinToString(", ")
            } else {
                names.take(shown).joinToString(", ") + " " + others.getValue(shown)
            }
            // Most names first; fall back to fewer until it fits, and at least one.
            (names.size downTo 1).map(::label).firstOrNull { candidate ->
                !measurer.measure(candidate, style, maxLines = maxLines, constraints = Constraints(maxWidth = width)).hasVisualOverflow
            } ?: label(1)
        }
        Text(text, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Time blocked for the chosen period, large (D-046). The other three numbers fold out under
 * "Details", so they're there without filling the screen.
 */
@Composable
private fun StatsSection(period: StatsPeriod, stats: UsageStats, onPeriod: (StatsPeriod) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Light pills rather than a segmented button: calmer, and the chosen one stands out by
        // its fill and by its selected state for TalkBack.
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StatsPeriod.entries.forEach { option ->
                PeriodPill(
                    label = stringResource(
                        when (option) {
                            StatsPeriod.Today -> R.string.stats_today
                            StatsPeriod.Week -> R.string.stats_week
                            StatsPeriod.AllTime -> R.string.stats_all_time
                        },
                    ),
                    selected = option == period,
                    onClick = { onPeriod(option) },
                )
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp)) {
                Text(formatDuration(stats.timeBlocked), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(
                        when (period) {
                            StatsPeriod.Today -> R.string.stats_rest_today
                            StatsPeriod.Week -> R.string.stats_rest_week
                            StatsPeriod.AllTime -> R.string.stats_rest_all
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedVisibility(visible = expanded) {
                    Column(Modifier.padding(top = 16.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(Modifier.padding(bottom = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        DetailLine(stats.timesBlocked, R.plurals.stats_breaks_started)
                        DetailLine(stats.appsStopped, R.plurals.stats_temptations_caught)
                        DetailLine(stats.stoppedWithoutTag, R.plurals.stats_ended_early)
                    }
                }
                TextButton(
                    onClick = { expanded = !expanded },
                    // Lines the label up with the number above; the button has its own padding.
                    modifier = Modifier
                        .offset(x = (-12).dp)
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(if (expanded) R.string.stats_less else R.string.stats_details))
                    Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
            }
        }
    }
}

/** One number as a short sentence, the number in bold: "7 pauzes gestart" (D-046). */
@Composable
private fun DetailLine(value: Int, @PluralsRes text: Int) {
    val sentence = pluralStringResource(text, value, value)
    val number = value.toString()
    Text(
        buildAnnotatedString {
            append(sentence)
            val at = sentence.indexOf(number)
            if (at >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface), at, at + number.length)
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PeriodPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return if (hours == 0L) stringResource(R.string.stats_minutes, minutes) else stringResource(R.string.stats_hours_minutes, hours, minutes)
}

/** The two most recently used block lists, the rest behind "Show all" (most people use one or two). */
@Composable
private fun BlockListsSection(lists: List<BlockListSummary>, onOpen: (Long) -> Unit) {
    if (lists.isEmpty()) return
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) lists else lists.take(SHOWN_BLOCK_LISTS)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.profiles_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        SoftCard(Modifier.fillMaxWidth()) {
            shown.forEachIndexed { index, list ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                BlockListRow(list, onClick = { onOpen(list.id) })
            }
        }
        if (lists.size > SHOWN_BLOCK_LISTS) {
            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.offset(x = (-12).dp).heightIn(min = 48.dp)) {
                Text(if (showAll) stringResource(R.string.stats_less) else stringResource(R.string.home_show_all, lists.size))
                Icon(if (showAll) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
        }
    }
}

private const val SHOWN_BLOCK_LISTS = 2

@Composable
private fun BlockListRow(list: BlockListSummary, onClick: () -> Unit) {
    val apps = pluralStringResource(R.plurals.profiles_app_count, list.appCount, list.appCount)
    val tags = if (list.tagNames.isEmpty()) stringResource(R.string.home_no_tag) else list.tagNames.joinToString(", ")
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
                style = MaterialTheme.typography.bodySmall,
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
