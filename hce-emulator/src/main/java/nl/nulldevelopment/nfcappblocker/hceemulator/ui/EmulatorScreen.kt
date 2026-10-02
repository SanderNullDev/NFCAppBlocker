package nl.nulldevelopment.nfcappblocker.hceemulator.ui

import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.nulldevelopment.nfcappblocker.hceemulator.R
import nl.nulldevelopment.nfcappblocker.hceemulator.data.LogEntry
import nl.nulldevelopment.nfcappblocker.hceemulator.data.TagPreset
import nl.nulldevelopment.nfcappblocker.hceemulator.data.parseUuidStrict
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorScreen(viewModel: EmulatorViewModel = viewModel(factory = EmulatorViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val nfcStatus = rememberNfcStatus()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { innerPadding ->
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
            if (nfcStatus != NfcStatus.ON) {
                item { NfcWarning(nfcStatus) }
            }
            item {
                EmulationToggle(enabled = state.emulationEnabled, onToggle = viewModel::setEmulationEnabled)
            }

            val settings = state.settings
            if (settings == null) {
                item { Text(stringResource(R.string.loading)) }
            } else {
                item { SectionHeading(stringResource(R.string.section_tag)) }
                item {
                    PresetSelector(
                        presets = settings.presets,
                        activeId = settings.activePresetId,
                        onSelect = viewModel::selectPreset,
                    )
                }
                item {
                    UuidEditor(
                        preset = settings.activePreset,
                        onSave = { viewModel.setUuid(settings.activePresetId, it) },
                        onGenerate = { viewModel.generateUuid(settings.activePresetId) },
                    )
                }
                item { NdefPreview(state.ndefMessageHex, state.ndefMessageSize) }
            }

            item { LogHeader(onClear = viewModel::clearLog, canClear = state.log.isNotEmpty()) }
            if (state.log.isEmpty()) {
                item { Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.log, key = { it.id }) { entry -> LogRow(entry) }
        }
    }
}

private enum class NfcStatus { ON, OFF, MISSING }

@Composable
private fun rememberNfcStatus(): NfcStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(NfcStatus.ON) }
    // Re-check on every resume: the user may come back from NFC settings.
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

@Composable
private fun NfcWarning(status: NfcStatus) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (status == NfcStatus.MISSING) R.string.nfc_missing else R.string.nfc_off),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (status == NfcStatus.OFF) {
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                    Text(stringResource(R.string.nfc_open_settings))
                }
            }
        }
    }
}

@Composable
private fun EmulationToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.emulation_title), style = MaterialTheme.typography.titleMedium)
                // The state is spelled out in text, not only shown by the switch position and color.
                Text(
                    stringResource(if (enabled) R.string.emulation_on else R.string.emulation_off),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSelector(presets: List<TagPreset>, activeId: String, onSelect: (String) -> Unit) {
    // Segmented buttons show a check mark on the selected item, so selection isn't color-only.
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        presets.forEachIndexed { index, preset ->
            SegmentedButton(
                selected = preset.id == activeId,
                onClick = { onSelect(preset.id) },
                shape = SegmentedButtonDefaults.itemShape(index, presets.size),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(preset.label)
            }
        }
    }
}

@Composable
private fun UuidEditor(preset: TagPreset, onSave: (java.util.UUID) -> Unit, onGenerate: () -> Unit) {
    val saved = preset.uuid.toString()
    var draft by rememberSaveable(preset.id, saved) { mutableStateOf(saved) }
    val parsed = parseUuidStrict(draft)
    val isError = parsed == null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(stringResource(R.string.uuid_label)) },
            isError = isError,
            supportingText = if (isError) {
                { Text(stringResource(R.string.uuid_error)) }
            } else {
                null
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { parsed?.let(onSave) },
                enabled = parsed != null && parsed != preset.uuid,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.uuid_save)) }
            OutlinedButton(onClick = onGenerate, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.uuid_generate))
            }
        }
    }
}

@Composable
private fun NdefPreview(hex: String, size: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(pluralStringResource(R.plurals.ndef_preview_title, size, size), style = MaterialTheme.typography.labelLarge)
        SelectionContainer {
            Text(hex, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun LogHeader(onClear: () -> Unit, canClear: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.section_log),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        TextButton(onClick = onClear, enabled = canClear, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.log_clear))
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

@Composable
private fun LogRow(entry: LogEntry) {
    val (kindLabel, descriptionRes) = when (entry.kind) {
        LogEntry.Kind.RECEIVED -> R.string.log_kind_received to R.string.log_received_description
        LogEntry.Kind.SENT -> R.string.log_kind_sent to R.string.log_sent_description
        LogEntry.Kind.EVENT -> R.string.log_kind_event to R.string.log_event_description
    }
    val spoken = stringResource(descriptionRes, entry.summary)
    Column {
        // TalkBack reads the summary only; hex dumps are noise when spoken.
        Row(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
            Text(
                stringResource(kindLabel),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
                entry.hex?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Text(
                    timeFormat.format(Date(entry.timeMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}
