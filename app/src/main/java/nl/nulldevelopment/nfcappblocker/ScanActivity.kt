package nl.nulldevelopment.nfcappblocker

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.blocking.ScanToggleRunner
import nl.nulldevelopment.nfcappblocker.nfc.toScannedTag
import javax.inject.Inject

/**
 * Receives scans of our tags while the app isn't open (tag dispatch via the MIME filter; the AAR
 * makes Android pick this app). Shows nothing: it toggles blocking, gives feedback, and only
 * opens the app when the tag can't toggle anything yet.
 */
@AndroidEntryPoint
class ScanActivity : ComponentActivity() {

    @Inject lateinit var runner: ScanToggleRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val tag = intent.takeIf { it.action == NfcAdapter.ACTION_NDEF_DISCOVERED }
            ?.let { IntentCompat.getParcelableExtra(it, NfcAdapter.EXTRA_TAG, Tag::class.java) }
        if (tag == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val outcome = runner.handle(tag.toScannedTag())
            ScanToggleRunner.navigationFor(outcome)?.let { startActivity(MainActivity.intentFor(this@ScanActivity, it)) }
            finish()
        }
    }
}
