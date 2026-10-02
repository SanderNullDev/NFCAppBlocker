package nl.nulldevelopment.nfcappblocker.hceemulator.ui

import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.hceemulator.EmulatorApplication
import nl.nulldevelopment.nfcappblocker.hceemulator.service.NdefHceService
import nl.nulldevelopment.nfcappblocker.hceemulator.ui.theme.HceEmulatorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HceEmulatorTheme {
                EmulatorScreen()
            }
        }
        preferOurServiceWhileResumed()
    }

    /**
     * Another app on this phone may also register the NDEF AID. While the emulator is in the
     * foreground and enabled, ask Android to route that AID to us without a chooser.
     */
    private fun preferOurServiceWhileResumed() {
        val adapter = NfcAdapter.getDefaultAdapter(this) ?: return
        val cardEmulation = CardEmulation.getInstance(adapter)
        val service = ComponentName(this, NdefHceService::class.java)
        val repository = (application as EmulatorApplication).repository
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    repository.emulationEnabled.collect { enabled ->
                        if (enabled) {
                            cardEmulation.setPreferredService(this@MainActivity, service)
                        } else {
                            cardEmulation.unsetPreferredService(this@MainActivity)
                        }
                    }
                } finally {
                    cardEmulation.unsetPreferredService(this@MainActivity)
                }
            }
        }
    }
}
