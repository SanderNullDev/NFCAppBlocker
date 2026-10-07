package nl.nulldevelopment.nfcappblocker.feature.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStep
import nl.nulldevelopment.nfcappblocker.feature.pairing.PairingState
import nl.nulldevelopment.nfcappblocker.feature.pairing.PairingStepContent
import nl.nulldevelopment.nfcappblocker.feature.settings.CountdownSlider
import nl.nulldevelopment.nfcappblocker.feature.status.BlockedApp
import nl.nulldevelopment.nfcappblocker.ui.components.AppIcon
import nl.nulldevelopment.nfcappblocker.ui.components.AppPickerRow
import nl.nulldevelopment.nfcappblocker.ui.components.AppSearchField
import nl.nulldevelopment.nfcappblocker.ui.components.NfcStatusNotice
import nl.nulldevelopment.nfcappblocker.ui.components.ResumedEffect
import nl.nulldevelopment.nfcappblocker.ui.components.rememberNfcStatus
import java.util.UUID

/** Full-screen first-run steps (D-037); shown instead of the tabs until finished. */
@Composable
fun OnboardingScreen(state: OnboardingState.Showing, viewModel: OnboardingViewModel) {
    ResumedEffect { if (it) viewModel.refresh() }
    BackHandler(enabled = state.index > 0, onBack = viewModel::back)
    Surface(Modifier.fillMaxSize()) {
        when (state.step) {
            OnboardingStep.Welcome -> WelcomeStep(state, onNext = viewModel::next)
            OnboardingStep.Service -> ServiceStep(state, onNext = viewModel::next)
            OnboardingStep.Tag -> TagStep(state, onChosen = viewModel::tagChosen)
            OnboardingStep.Notifications -> NotificationsStep(state, onNext = viewModel::next)
            OnboardingStep.Apps -> AppsStep(state, onSaved = viewModel::appsSaved)
            OnboardingStep.Stopping -> StoppingStep(state, onChange = viewModel::setCountdownMinutes, onNext = viewModel::next)
            OnboardingStep.TryIt -> TryItStep(state, onFinish = viewModel::next)
        }
    }
}

@Composable
private fun WelcomeStep(state: OnboardingState.Showing, onNext: () -> Unit) {
    StepFrame(
        state = state,
        illustration = {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        },
        title = stringResource(R.string.onboarding_welcome_title),
        primary = stringResource(R.string.onboarding_get_started) to onNext,
    ) {
        BodyText(stringResource(R.string.onboarding_welcome_body))
        BodyText(stringResource(R.string.onboarding_welcome_steps))
    }
}

@Composable
private fun ServiceStep(state: OnboardingState.Showing, onNext: () -> Unit) {
    val context = LocalContext.current
    // Coming back from Settings with the service still off most likely means its switch was
    // greyed out (restricted settings), so the help for that opens by itself.
    var openedSettings by rememberSaveable { mutableStateOf(false) }
    var showWhy by rememberSaveable { mutableStateOf(false) }

    if (state.serviceEnabled) {
        StepFrame(
            state = state,
            illustration = { StepIcon(Icons.Filled.Check) },
            title = stringResource(R.string.onboarding_service_on_title),
            primary = stringResource(R.string.onboarding_next) to onNext,
        ) {
            BodyText(stringResource(R.string.onboarding_service_on_body))
        }
        return
    }

    StepFrame(
        state = state,
        illustration = { StepIcon(Icons.Filled.Lock) },
        title = stringResource(R.string.onboarding_service_title),
        primary = stringResource(R.string.onboarding_open_settings) to {
            openedSettings = true
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
    ) {
        BodyText(stringResource(R.string.onboarding_service_body))
        BodyText(stringResource(R.string.onboarding_service_how))
        TextButton(onClick = { showWhy = !showWhy }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.onboarding_service_why))
        }
        if (showWhy) {
            BodyText(stringResource(R.string.disclosure_what))
            BodyText(stringResource(R.string.disclosure_not))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && openedSettings) {
            RestrictedSettingsHelp(onOpenAppInfo = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            })
        }
    }
}

/**
 * Android 13+ greys out the accessibility switch of apps not installed from an app store until
 * the user allows restricted settings in App info. There's no public way to detect it.
 */
@Composable
private fun RestrictedSettingsHelp(onOpenAppInfo: () -> Unit) {
    Panel {
        Text(
            stringResource(R.string.onboarding_restricted_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        listOf(
            R.string.onboarding_restricted_step1,
            R.string.onboarding_restricted_step2,
            R.string.onboarding_restricted_step3,
            R.string.onboarding_restricted_step4,
        ).forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(step), style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.onboarding_open_app_info))
        }
    }
}

@Composable
private fun TagStep(state: OnboardingState.Showing, onChosen: (UUID, String) -> Unit) {
    val tagViewModel: OnboardingTagViewModel = hiltViewModel()
    val pairing by tagViewModel.state.collectAsStateWithLifecycle()
    val nfcStatus = rememberNfcStatus()
    ResumedEffect(tagViewModel::setResumed)
    DisposableEffect(tagViewModel) {
        tagViewModel.setListening(true)
        onDispose { tagViewModel.setListening(false) }
    }

    val found = when (val current = pairing) {
        is PairingState.Paired -> current.tag
        is PairingState.AlreadyPaired -> current.tag
        else -> null
    }
    if (found != null) {
        var name by rememberSaveable(found.uuid) { mutableStateOf(found.name) }
        StepFrame(
            state = state,
            illustration = { StepIcon(Icons.Filled.Check) },
            title = stringResource(if (pairing is PairingState.Paired) R.string.onboarding_tag_added else R.string.onboarding_tag_known),
            primary = stringResource(R.string.onboarding_next) to { onChosen(found.uuid, name) },
        ) {
            BodyText(stringResource(R.string.onboarding_tag_name_hint))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.add_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    StepFrame(
        state = state,
        illustration = { AppMark() },
        title = stringResource(R.string.onboarding_tag_title),
        primary = null,
    ) {
        NfcStatusNotice(nfcStatus)
        // Polite live region: TalkBack announces each step of pairing as it changes.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (pairing) {
                PairingState.WaitingForTag -> {
                    BodyText(stringResource(R.string.onboarding_tag_body))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.onboarding_tag_waiting), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> PairingStepContent(
                    state = pairing,
                    onConfirmOverwrite = tagViewModel::confirmOverwrite,
                    onAdopt = tagViewModel::adopt,
                    onRestart = tagViewModel::restart,
                    // Both are handled above.
                    alreadyPaired = {},
                    paired = {},
                )
            }
        }
    }
}

@Composable
private fun NotificationsStep(state: OnboardingState.Showing, onNext: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    ResumedEffect { if (it) allowed = NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted }

    if (allowed) {
        StepFrame(
            state = state,
            illustration = { StepIcon(Icons.Filled.Check) },
            title = stringResource(R.string.onboarding_notifications_on_title),
            primary = stringResource(R.string.onboarding_next) to onNext,
        ) {
            BodyText(stringResource(R.string.onboarding_notifications_body))
        }
        return
    }
    StepFrame(
        state = state,
        illustration = { StepIcon(Icons.Filled.Notifications) },
        title = stringResource(R.string.onboarding_notifications_title),
        primary = stringResource(R.string.setup_notifications_action) to {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        },
        secondary = stringResource(R.string.onboarding_skip) to onNext,
    ) {
        BodyText(stringResource(R.string.onboarding_notifications_body))
    }
}

@Composable
private fun AppsStep(state: OnboardingState.Showing, onSaved: (Long) -> Unit) {
    val appsViewModel: OnboardingAppsViewModel = hiltViewModel()
    val apps by appsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.profile?.id) { appsViewModel.load(state.profile?.id) }
    val tag = state.tag ?: return
    val count = apps?.selectedCount ?: 0

    StepFrame(
        state = state,
        illustration = null,
        title = stringResource(R.string.onboarding_apps_title),
        primary = (if (count == 0) stringResource(R.string.onboarding_apps_none) else pluralStringResource(R.plurals.onboarding_apps_next, count, count)) to {
            appsViewModel.save(tag.uuid, state.profile?.id, onSaved)
        },
        primaryEnabled = count > 0,
        scrolls = false,
    ) {
        BodyText(stringResource(R.string.onboarding_apps_body, tag.name))
        val loaded = apps ?: return@StepFrame
        AppSearchField(loaded.query, appsViewModel::setQuery)
        LazyColumn(Modifier.weight(1f)) {
            items(loaded.apps, key = { it.packageName }) { app ->
                AppPickerRow(app, onToggle = { appsViewModel.setSelected(app.packageName, it) })
            }
        }
    }
}

@Composable
private fun StoppingStep(state: OnboardingState.Showing, onChange: (Int) -> Unit, onNext: () -> Unit) {
    StepFrame(
        state = state,
        illustration = { StepIcon(Icons.Filled.Refresh) },
        title = stringResource(R.string.onboarding_stopping_title),
        primary = stringResource(R.string.onboarding_next) to onNext,
    ) {
        BodyText(stringResource(R.string.onboarding_stopping_tag, state.tag?.name.orEmpty()))
        BodyText(stringResource(R.string.onboarding_stopping_without))
        Panel {
            Text(
                stringResource(R.string.onboarding_stopping_length),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            CountdownSlider(state.countdownMinutes, enabled = !state.countdownLocked, onChange = onChange)
            Text(
                stringResource(if (state.countdownLocked) R.string.settings_countdown_locked else R.string.onboarding_stopping_later),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TryItStep(state: OnboardingState.Showing, onFinish: () -> Unit) {
    val tagName = state.tag?.name.orEmpty()
    StepFrame(
        state = state,
        illustration = { if (state.blocking) StepIcon(Icons.Filled.Check) else AppMark() },
        title = stringResource(if (state.blocking) R.string.onboarding_try_blocking_title else R.string.onboarding_try_title),
        primary = if (state.blocking) stringResource(R.string.onboarding_finish) to onFinish else null,
        secondary = if (state.blocking) null else stringResource(R.string.onboarding_skip) to onFinish,
    ) {
        // Polite live region: TalkBack announces when the tap started blocking.
        Column(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.blocking) {
                BodyText(stringResource(R.string.onboarding_try_blocking_body, tagName))
            } else {
                BodyText(stringResource(R.string.onboarding_try_body, tagName))
            }
        }
        BlockedAppList(
            heading = stringResource(if (state.blocking) R.string.onboarding_try_blocked_now else R.string.onboarding_try_will_block),
            apps = state.blockedApps,
        )
    }
}

@Composable
private fun BlockedAppList(heading: String, apps: List<BlockedApp>) {
    Panel {
        Text(heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        apps.forEach { app ->
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

/**
 * One onboarding step: progress at the top, an illustration, a heading and text, and the main
 * action at the bottom. The text scrolls when it doesn't fit, e.g. at large font sizes; a step
 * with its own list passes [scrolls] false and gives the list the remaining height.
 */
@Composable
private fun StepFrame(
    state: OnboardingState.Showing,
    illustration: (@Composable () -> Unit)?,
    title: String,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>? = null,
    primaryEnabled: Boolean = true,
    scrolls: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        StepProgress(state.index, state.total)
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(top = if (illustration != null) 40.dp else 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (illustration != null) {
                illustration()
                Spacer(Modifier.height(8.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
        if (primary != null) {
            Button(
                onClick = primary.second,
                enabled = primaryEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text(primary.first, style = MaterialTheme.typography.titleMedium) }
        }
        if (secondary != null) {
            TextButton(
                onClick = secondary.second,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) { Text(secondary.first) }
        }
    }
}

/** Segments, one per step; read out as "Step 2 of 7". */
@Composable
private fun StepProgress(index: Int, total: Int) {
    val description = stringResource(R.string.onboarding_progress, index + 1, total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun StepIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
    }
}

/** The app's padlock with the NFC symbol, for the steps about the tag. */
@Composable
private fun AppMark() {
    Image(
        painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 560.dp),
    )
}
