package nl.nulldevelopment.nfcappblocker.feature.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.nfc.WipeResult
import nl.nulldevelopment.nfcappblocker.ui.components.BackButton
import nl.nulldevelopment.nfcappblocker.ui.components.NfcStatusNotice
import nl.nulldevelopment.nfcappblocker.ui.components.ProfileRadioList
import nl.nulldevelopment.nfcappblocker.ui.components.ResumedEffect
import nl.nulldevelopment.nfcappblocker.ui.components.rememberNfcStatus
import nl.nulldevelopment.nfcappblocker.ui.components.SoftCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDetailsScreen(
    uuid: UUID,
    onBack: () -> Unit,
    viewModel: TagDetailsViewModel = hiltViewModel<TagDetailsViewModel, TagDetailsViewModel.Factory>(
        creationCallback = { factory -> factory.create(uuid) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wipe by viewModel.wipe.collectAsStateWithLifecycle()
    ResumedEffect(viewModel::setResumed)

    // Forgotten or wiped: nothing left to show here.
    LaunchedEffect(state) { if (state == TagDetailsState.Missing) onBack() }

    val loaded = state as? TagDetailsState.Loaded
    val tag = loaded?.tag
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tag?.name.orEmpty()) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { innerPadding ->
        if (loaded == null || tag == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NameEditor(tag, onSave = viewModel::rename)
            TagInfo(tag)
            ProfileLink(loaded, onSelect = viewModel::setProfile)
            if (wipe == WipeState.Idle) {
                Actions(tag, onForget = viewModel::forget, onWipe = viewModel::startWipe)
            } else {
                WipePanel(tag, wipe, onCancel = viewModel::cancelWipe)
            }
        }
    }
}

@Composable
private fun NameEditor(tag: PairedTag, onSave: (String) -> Unit) {
    var name by rememberSaveable(tag.uuid, tag.name) { mutableStateOf(tag.name) }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.add_name_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { onSave(name) },
        enabled = name.isNotBlank() && name.trim() != tag.name,
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.details_save_name)) }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

@Composable
private fun TagInfo(tag: PairedTag) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.tags_added_on, dateFormat.format(tag.createdAt)))
        Text(stringResource(R.string.details_uuid, tag.uuid.toString()), fontFamily = FontFamily.Monospace)
        Text(
            tag.hardwareUid?.let { stringResource(R.string.details_hardware_uid, it) }
                ?: stringResource(R.string.details_hardware_uid_unknown),
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ProfileLink(state: TagDetailsState.Loaded, onSelect: (Long?) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Text(
        state.profile?.let { stringResource(R.string.details_profile, it.name) }
            ?: stringResource(R.string.details_profile_none),
        style = MaterialTheme.typography.bodyLarge,
    )
    if (state.profiles.isEmpty()) {
        Text(stringResource(R.string.link_no_profiles), style = MaterialTheme.typography.bodyMedium)
    } else {
        OutlinedButton(onClick = { choosing = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (state.profile == null) R.string.details_link_profile else R.string.details_change_profile))
        }
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.details_choose_profile)) },
            text = {
                ProfileRadioList(state.profiles, state.profile?.id, onSelect = { id ->
                    choosing = false
                    onSelect(id)
                })
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.add_cancel)) } },
        )
    }
}

@Composable
private fun Actions(tag: PairedTag, onForget: () -> Unit, onWipe: () -> Unit) {
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { confirmForget = true }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.details_forget))
    }
    OutlinedButton(onClick = onWipe, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.details_wipe))
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.details_forget_title, tag.name)) },
            text = { Text(stringResource(R.string.details_forget_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    onForget()
                }) { Text(stringResource(R.string.details_forget)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.add_cancel)) }
            },
        )
    }
}

@Composable
private fun WipePanel(tag: PairedTag, wipe: WipeState, onCancel: () -> Unit) {
    NfcStatusNotice(rememberNfcStatus())
    SoftCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (wipe) {
                is WipeState.WaitingForTag -> {
                    Text(stringResource(R.string.details_wipe_waiting), style = MaterialTheme.typography.bodyLarge)
                    if (wipe.otherTagTapped) Text(stringResource(R.string.details_wipe_other_tag, tag.name))
                }
                WipeState.Wiping -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.details_wiping), style = MaterialTheme.typography.bodyLarge)
                }
                is WipeState.Failed -> {
                    Text(wipeFailureText(wipe.result), style = MaterialTheme.typography.bodyLarge)
                    // Still listening: holding the tag again retries.
                    Text(stringResource(R.string.details_wipe_waiting))
                }
                WipeState.Idle -> Unit
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.add_cancel))
            }
        }
    }
}

@Composable
private fun wipeFailureText(result: WipeResult): String = when (result) {
    WipeResult.ReadOnly -> stringResource(R.string.details_wipe_failed_read_only)
    WipeResult.TagLost -> stringResource(R.string.details_wipe_failed_tag_lost)
    WipeResult.NotVerified -> stringResource(R.string.details_wipe_failed_not_verified)
    is WipeResult.Failed -> stringResource(R.string.details_wipe_failed_other, result.reason)
    WipeResult.Wiped, WipeResult.DifferentTag -> ""
}
