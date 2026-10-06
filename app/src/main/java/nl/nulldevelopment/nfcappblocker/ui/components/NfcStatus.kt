package nl.nulldevelopment.nfcappblocker.ui.components

import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import nl.nulldevelopment.nfcappblocker.R

enum class NfcStatus { ON, OFF, MISSING }

/** Re-checked on every resume, since the user may come back from NFC settings. */
@Composable
fun rememberNfcStatus(): NfcStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(NfcStatus.ON) }
    LifecycleResumeEffect(Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        status = when {
            adapter == null -> NfcStatus.MISSING
            !adapter.isEnabled -> NfcStatus.OFF
            else -> NfcStatus.ON
        }
        onPauseOrDispose { }
    }
    return status
}

/** Explains why no tag can be read, with a way out when NFC is merely off. Nothing when NFC is on. */
@Composable
fun NfcStatusNotice(status: NfcStatus, modifier: Modifier = Modifier) {
    if (status == NfcStatus.ON) return
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (status == NfcStatus.MISSING) R.string.nfc_missing else R.string.nfc_off),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (status == NfcStatus.OFF) {
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.nfc_open_settings)) }
            }
        }
    }
}
