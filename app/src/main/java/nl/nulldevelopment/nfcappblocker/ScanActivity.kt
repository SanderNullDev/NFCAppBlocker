package nl.nulldevelopment.nfcappblocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.nfc.ScanSource
import nl.nulldevelopment.nfcappblocker.nfc.TagScans
import nl.nulldevelopment.nfcappblocker.ui.scan.ScanDebugScreen
import nl.nulldevelopment.nfcappblocker.ui.theme.NFCAppBlockerTheme

/**
 * Entry point for scans of our tags through Android's tag dispatch (MIME intent filter, with
 * the AAR making sure this app is chosen). Kept separate from [MainActivity] so that later a
 * scan can toggle blocking without opening the main UI (D-005). For now it shows the result.
 */
class ScanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on first creation; after a configuration change the intent was already handled.
        if (savedInstanceState == null) TagScans.onDispatchIntent(intent, ScanSource.DISPATCH_LAUNCH)
        enableEdgeToEdge()
        setContent {
            NFCAppBlockerTheme {
                val scans by TagScans.recent.collectAsStateWithLifecycle()
                ScanDebugScreen(status = stringResource(R.string.scan_status_dispatch), scans = scans)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        TagScans.onDispatchIntent(intent, ScanSource.DISPATCH_NEW_INTENT)
    }
}
