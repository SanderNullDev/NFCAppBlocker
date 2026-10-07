package nl.nulldevelopment.nfcappblocker.feature.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStep
import nl.nulldevelopment.nfcappblocker.ui.components.ResumedEffect

/** Full-screen first-run steps (D-037); shown instead of the tabs until finished. */
@Composable
fun OnboardingScreen(state: OnboardingState.Showing, viewModel: OnboardingViewModel) {
    ResumedEffect { if (it) viewModel.refresh() }
    BackHandler(enabled = state.index > 0, onBack = viewModel::back)
    Surface(Modifier.fillMaxSize()) {
        when (state.step) {
            OnboardingStep.Welcome -> WelcomeStep(state, onNext = viewModel::next)
            OnboardingStep.Service -> ServiceStep(state, onNext = viewModel::next)
            // Not built yet (Phase 7, step 5); unreachable while STEPS leaves them out.
            else -> Unit
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

/**
 * One onboarding step: progress at the top, an illustration, a heading and text, and the main
 * action at the bottom. The text scrolls when it doesn't fit, e.g. at large font sizes.
 */
@Composable
private fun StepFrame(
    state: OnboardingState.Showing,
    illustration: @Composable () -> Unit,
    title: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
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
                .verticalScroll(rememberScrollState())
                .padding(top = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            illustration()
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
        Button(
            onClick = primary.second,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) { Text(primary.first, style = MaterialTheme.typography.titleMedium) }
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

/** Segments, one per step; read out as "Step 2 of 6". */
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

@Composable
private fun BodyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 560.dp),
    )
}
