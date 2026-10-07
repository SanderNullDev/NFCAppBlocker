package nl.nulldevelopment.nfcappblocker.feature.escape

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.ui.theme.NFCAppBlockerTheme

/**
 * Stopping blocking without a tag (Phase 5): the user waits out a countdown with this screen open.
 * Leaving it, or turning off the display, cancels it, so it can't be started and then ignored. The
 * display stays on meanwhile, so the screen timeout doesn't cancel it.
 */
@AndroidEntryPoint
class EscapeActivity : ComponentActivity() {

    private val viewModel: EscapeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this) { keepBlocking() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { if (it == EscapeState.Closed) finish() }
            }
        }
        enableEdgeToEdge()
        setContent {
            NFCAppBlockerTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                EscapeScreen(state, onKeepBlocking = ::keepBlocking)
            }
        }
    }

    /** Home, another app, the recents screen or the power button: the countdown is cancelled. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing) {
            viewModel.cancel()
            finish()
        }
    }

    private fun keepBlocking() {
        viewModel.cancel()
        // From the block screen, going back would only show the blocked app and block it again.
        if (intent.getBooleanExtra(EXTRA_FROM_BLOCK_SCREEN, false)) {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    companion object {
        private const val EXTRA_FROM_BLOCK_SCREEN = "from_block_screen"

        fun intent(context: Context, fromBlockScreen: Boolean): Intent =
            Intent(context, EscapeActivity::class.java).putExtra(EXTRA_FROM_BLOCK_SCREEN, fromBlockScreen)
    }
}

@Composable
private fun EscapeScreen(state: EscapeState, onKeepBlocking: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        val running = state as? EscapeState.Running ?: return@Surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.escape_title, running.profileName),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.escape_body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                // TalkBack hears the minutes left once a minute, not every second's change.
                val minutesLeft = pluralStringResource(R.plurals.escape_minutes_left, running.minutesLeft.toInt(), running.minutesLeft)
                Text(
                    formatTime(running.remainingSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = minutesLeft
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                LinearProgressIndicator(
                    progress = { running.fractionLeft },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {},
                )
                OutlinedButton(onClick = onKeepBlocking, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.escape_keep_blocking))
                }
            }
        }
    }
}

private fun formatTime(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
