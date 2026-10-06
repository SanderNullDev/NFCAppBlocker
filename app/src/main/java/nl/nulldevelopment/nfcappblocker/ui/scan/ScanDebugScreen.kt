package nl.nulldevelopment.nfcappblocker.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.nfc.ScanResult
import nl.nulldevelopment.nfcappblocker.nfc.ScanSource
import nl.nulldevelopment.nfcappblocker.nfc.WriteResult
import nl.nulldevelopment.nfcappblocker.tagformat.TagMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Arms or cancels writing the next tag. Only offered where reader mode is active. */
data class WriteControls(val armed: Boolean, val onArm: () -> Unit, val onCancel: () -> Unit)

/**
 * Spike B debug screen: the latest scan in detail and the recent scans below it.
 * Disposable once the real scan flow exists (Phase 4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDebugScreen(
    status: String,
    scans: List<ScanResult>,
    writeControls: WriteControls? = null,
    extraItems: LazyListScope.() -> Unit = {},
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.scan_debug_title)) }) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(status, style = MaterialTheme.typography.bodyLarge) }
            writeControls?.let { controls -> item { WriteControlsRow(controls) } }
            extraItems()
            item { SectionHeading(stringResource(R.string.scan_section)) }
            if (scans.isEmpty()) {
                item { Text(stringResource(R.string.scan_none_yet)) }
            } else {
                item {
                    // Polite live region so TalkBack announces each new scan.
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        SectionHeading(stringResource(R.string.scan_latest))
                        ScanCard(scans.first())
                    }
                }
                if (scans.size > 1) {
                    item { SectionHeading(stringResource(R.string.scan_earlier)) }
                    items(scans.drop(1), key = { it.id }) { ScanCard(it) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun WriteControlsRow(controls: WriteControls) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The button names its action, and the armed state is also spelled out in text.
        if (controls.armed) {
            OutlinedButton(onClick = controls.onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.write_cancel))
            }
            Text(
                stringResource(R.string.write_armed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            Button(onClick = controls.onArm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.write_arm))
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

@Composable
private fun ScanCard(scan: ScanResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (scan.write != null) {
                Text(writeText(scan.write), style = MaterialTheme.typography.titleSmall)
                writtenUuid(scan.write)?.let { Text(it.toString(), fontFamily = FontFamily.Monospace) }
                Text(stringResource(R.string.write_previous_content, outcomeText(scan.message)))
            } else {
                Text(outcomeText(scan.message), style = MaterialTheme.typography.titleSmall)
                (scan.message as? TagMessage.Result.Ours)?.let {
                    Text(it.uuid.toString(), fontFamily = FontFamily.Monospace)
                }
            }
            Text(stringResource(R.string.scan_source, sourceText(scan.source)))
            Text(
                stringResource(
                    if (scan.isLikelyColdStart) R.string.scan_process_cold else R.string.scan_process_warm,
                    scan.processAgeMillis,
                ),
            )
            Text(stringResource(R.string.scan_uid, scan.uidHex), fontFamily = FontFamily.Monospace)
            Text(stringResource(R.string.scan_techs, scan.techs.joinToString(", ")))
            Text(
                timeFormat.format(Date(scan.timeMillis)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun outcomeText(message: TagMessage.Result?): String = when (message) {
    null -> stringResource(R.string.scan_outcome_no_ndef)
    is TagMessage.Result.Ours -> stringResource(R.string.scan_outcome_ours)
    is TagMessage.Result.Foreign -> stringResource(R.string.scan_outcome_foreign)
    is TagMessage.Result.UnsupportedVersion -> stringResource(R.string.scan_outcome_unsupported, message.version)
    is TagMessage.Result.Malformed -> stringResource(R.string.scan_outcome_malformed, message.reason)
}

@Composable
private fun writeText(write: WriteResult): String = when (write) {
    is WriteResult.Written -> stringResource(R.string.write_result_written)
    is WriteResult.WrittenUnverified -> stringResource(R.string.write_result_unverified, write.reason)
    is WriteResult.VerificationFailed -> stringResource(R.string.write_result_mismatch, outcomeText(write.readBack))
    is WriteResult.ReadOnly -> stringResource(R.string.write_result_read_only)
    is WriteResult.TooSmall -> stringResource(R.string.write_result_too_small, write.capacity, write.needed)
    is WriteResult.NotNdefCapable -> stringResource(R.string.write_result_not_ndef)
    is WriteResult.TagLost -> stringResource(R.string.write_result_tag_lost)
    is WriteResult.Failed -> stringResource(R.string.write_result_failed, write.reason)
}

private fun writtenUuid(write: WriteResult) = when (write) {
    is WriteResult.Written -> write.uuid
    is WriteResult.WrittenUnverified -> write.uuid
    is WriteResult.VerificationFailed -> write.uuid
    else -> null
}

@Composable
private fun sourceText(source: ScanSource): String = stringResource(
    when (source) {
        ScanSource.DISPATCH_LAUNCH -> R.string.scan_source_dispatch_launch
        ScanSource.DISPATCH_NEW_INTENT -> R.string.scan_source_dispatch_new_intent
        ScanSource.READER_MODE -> R.string.scan_source_reader_mode
    },
)
