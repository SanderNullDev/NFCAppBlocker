package nl.nulldevelopment.nfcappblocker.spike.blocking

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.ui.theme.NFCAppBlockerTheme

/**
 * Shown instead of a blocked app (spike A). Never traps the user: the button and Back both go
 * to the home screen, never back into the blocked app.
 */
class BlockActivity : ComponentActivity() {

    private var blockedPackage by mutableStateOf("")
    private var eventUptimeMillis = 0L
    private var latencyRecorded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableTransitions()
        readExtras(intent)
        onBackPressedDispatcher.addCallback(this) { goHome() }
        enableEdgeToEdge()
        setContent {
            NFCAppBlockerTheme {
                BlockScreen(appName = BlockingSpike.appName(blockedPackage), onGoHome = ::goHome)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
    }

    /** Focus is the closest cheap signal for "the user can see the block screen now". */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !latencyRecorded && eventUptimeMillis > 0) {
            latencyRecorded = true
            BlockingSpike.recordLatency(blockedPackage, SystemClock.uptimeMillis() - eventUptimeMillis)
        }
    }

    /**
     * The blocked app stays visible for as long as the open animation runs, so the block screen
     * appears in a single frame instead. The theme does the same for older shell transitions.
     */
    private fun disableTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
    }

    private fun readExtras(intent: Intent) {
        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        eventUptimeMillis = intent.getLongExtra(EXTRA_EVENT_UPTIME, 0L)
        latencyRecorded = false
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "blocked_package"
        private const val EXTRA_EVENT_UPTIME = "event_uptime"

        fun intent(context: Context, blockedPackage: String, eventUptimeMillis: Long): Intent =
            Intent(context, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_PACKAGE, blockedPackage)
                .putExtra(EXTRA_EVENT_UPTIME, eventUptimeMillis)
    }
}

@Composable
private fun BlockScreen(appName: String, onGoHome: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
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
            Button(onClick = onGoHome, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.block_go_home))
            }
        }
    }
}
