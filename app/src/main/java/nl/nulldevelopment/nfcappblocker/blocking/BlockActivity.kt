package nl.nulldevelopment.nfcappblocker.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.nulldevelopment.nfcappblocker.MainActivity
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.feature.escape.EscapeActivity
import nl.nulldevelopment.nfcappblocker.ui.theme.NFCAppBlockerTheme
import java.util.function.Consumer

/**
 * Shown over a blocked app (D-001). The window is translucent: the blocked app stays visible
 * behind a dark scrim, and blurred on devices with cross-window blur, so the brief moment the app
 * is visible reads as the block taking effect rather than as a glitch. Never traps the user: the
 * button and Back both go to the home screen, never back into the blocked app.
 */
class BlockActivity : ComponentActivity() {

    private var appName by mutableStateOf("")
    private var profileName by mutableStateOf("")

    /** Opened during onboarding: lead back to TikStop instead of the home screen, no unlocking. */
    private var duringOnboarding by mutableStateOf(false)

    /** False when the device has cross-window blur off or unsupported (the reference device). */
    private var blurAvailable by mutableStateOf(false)
    private var blurListener: Consumer<Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readExtras(intent)
        onBackPressedDispatcher.addCallback(this) { leave() }
        useFadeTransitions()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) enableBlur()
        enableEdgeToEdge()
        setContent {
            NFCAppBlockerTheme {
                BlockScreen(
                    appName = appName,
                    profileName = profileName,
                    blurAvailable = blurAvailable,
                    duringOnboarding = duringOnboarding,
                    onLeave = ::leave,
                    onStopWithoutTag = ::stopWithoutTag,
                )
            }
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurListener?.let(windowManager::removeCrossWindowBlurEnabledListener)
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
    }

    /**
     * Requests a fade instead of the default slide. On the reference device this had no visible
     * effect for a launch from the accessibility service (see the spike A findings in the plan).
     */
    private fun useFadeTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.block_fade_in, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.block_fade_out)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun enableBlur() {
        // The radius is set on the decor view, which doesn't exist yet in onCreate; some Android
        // builds then crash instead of ignoring it. Reading decorView creates it.
        window.decorView
        window.setBackgroundBlurRadius((BLUR_RADIUS_DP * resources.displayMetrics.density).toInt())
        // Called right away with the current state, and again when e.g. power saving toggles it.
        val listener = Consumer<Boolean> { blurAvailable = it }
        windowManager.addCrossWindowBlurEnabledListener(mainExecutor, listener)
        blurListener = listener
    }

    private fun readExtras(intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
        profileName = intent.getStringExtra(EXTRA_PROFILE_NAME).orEmpty()
        duringOnboarding = intent.getBooleanExtra(EXTRA_DURING_ONBOARDING, false)
    }

    /** The countdown takes this task's place, so finishing it shows the app that was blocked. */
    private fun stopWithoutTag() {
        startActivity(EscapeActivity.intent(this, fromBlockScreen = true))
        finish()
    }

    /** Never back into the blocked app: to TikStop during onboarding, otherwise the home screen. */
    private fun leave() {
        if (duringOnboarding) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        } else {
            goHome()
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "blocked_package"
        private const val EXTRA_PROFILE_NAME = "profile_name"
        private const val EXTRA_DURING_ONBOARDING = "during_onboarding"
        private const val BLUR_RADIUS_DP = 32

        fun intent(context: Context, blockedPackage: String, profileName: String, duringOnboarding: Boolean = false): Intent =
            Intent(context, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_PACKAGE, blockedPackage)
                .putExtra(EXTRA_PROFILE_NAME, profileName)
                .putExtra(EXTRA_DURING_ONBOARDING, duringOnboarding)
    }
}

/** Scrim over the blurred app; without blur it darkens the app more. */
private const val SCRIM_ALPHA_WITH_BLUR = 0.35f
private const val SCRIM_ALPHA_WITHOUT_BLUR = 0.92f

@Composable
private fun BlockScreen(
    appName: String,
    profileName: String,
    blurAvailable: Boolean,
    duringOnboarding: Boolean,
    onLeave: () -> Unit,
    onStopWithoutTag: () -> Unit,
) {
    val scrimAlpha by animateFloatAsState(
        targetValue = if (blurAvailable) SCRIM_ALPHA_WITH_BLUR else SCRIM_ALPHA_WITHOUT_BLUR,
        label = "scrim",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // A solid card keeps the text readable whatever the blocked app shows behind it.
        Card(
            modifier = Modifier.widthIn(max = 480.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.block_title, appName),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.block_body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                if (profileName.isNotEmpty()) {
                    Text(
                        stringResource(R.string.home_active, profileName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(onClick = onLeave, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (duringOnboarding) R.string.block_back_to_app else R.string.block_close))
                }
                // Unlocking makes no sense while trying blocking out during onboarding.
                if (!duringOnboarding) {
                    TextButton(onClick = onStopWithoutTag, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.escape_start))
                    }
                }
            }
        }
    }
}
