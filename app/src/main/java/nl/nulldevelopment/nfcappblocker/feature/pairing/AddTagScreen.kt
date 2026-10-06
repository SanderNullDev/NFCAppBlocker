package nl.nulldevelopment.nfcappblocker.feature.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.nfc.TagContent
import nl.nulldevelopment.nfcappblocker.ui.components.BackButton
import nl.nulldevelopment.nfcappblocker.ui.components.NfcStatusNotice
import nl.nulldevelopment.nfcappblocker.ui.components.rememberNfcStatus
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTagScreen(
    onBack: () -> Unit,
    onOpenTag: (UUID) -> Unit,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nfcStatus = rememberNfcStatus()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_tag_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NfcStatusNotice(nfcStatus)
            // Polite live region: TalkBack announces each step of the flow as it changes.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (val s = state) {
                    PairingState.WaitingForTag -> Message(stringResource(R.string.add_waiting))
                    PairingState.Writing -> {
                        CircularProgressIndicator()
                        Message(stringResource(R.string.add_writing))
                    }
                    is PairingState.ConfirmOverwrite -> ConfirmOverwrite(
                        content = s.content,
                        onOverwrite = viewModel::confirmOverwrite,
                        onCancel = viewModel::restart,
                    )
                    is PairingState.WaitingForSameTag -> {
                        Message(stringResource(R.string.add_hold_same_tag))
                        if (s.otherTagTapped) Message(stringResource(R.string.add_other_tag))
                        OutlinedAction(stringResource(R.string.add_cancel), viewModel::restart)
                    }
                    is PairingState.AlreadyPaired -> {
                        Message(stringResource(R.string.add_already_paired, s.tag.name))
                        PrimaryAction(stringResource(R.string.add_open_tag)) { onOpenTag(s.tag.uuid) }
                        OutlinedAction(stringResource(R.string.add_another), viewModel::restart)
                    }
                    is PairingState.OursNotPaired -> {
                        Message(stringResource(R.string.add_ours_not_paired))
                        PrimaryAction(stringResource(R.string.add_adopt), viewModel::adopt)
                        if (s.canOverwrite) OutlinedAction(stringResource(R.string.add_new_id), viewModel::confirmOverwrite)
                    }
                    is PairingState.Paired -> NameNewTag(
                        uuid = s.tag.uuid,
                        initialName = s.tag.name,
                        onDone = { name ->
                            viewModel.rename(s.tag.uuid, name)
                            onBack()
                        },
                    )
                    is PairingState.Failed -> {
                        Message(failureText(s.reason))
                        PrimaryAction(stringResource(R.string.add_try_again), viewModel::restart)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmOverwrite(content: TagContent, onOverwrite: () -> Unit, onCancel: () -> Unit) {
    Text(
        stringResource(R.string.add_confirm_title),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Message(
        stringResource(
            when (content) {
                is TagContent.UnsupportedVersion -> R.string.add_confirm_newer
                TagContent.Unreadable -> R.string.add_confirm_unreadable
                else -> R.string.add_confirm_foreign
            },
        ),
    )
    Message(stringResource(R.string.add_confirm_body))
    PrimaryAction(stringResource(R.string.add_overwrite), onOverwrite)
    OutlinedAction(stringResource(R.string.add_cancel), onCancel)
}

@Composable
private fun NameNewTag(uuid: UUID, initialName: String, onDone: (String) -> Unit) {
    var name by rememberSaveable(uuid) { mutableStateOf(initialName) }
    Message(stringResource(R.string.add_paired))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.add_name_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    PrimaryAction(stringResource(R.string.add_done)) { onDone(name) }
}

@Composable
private fun failureText(reason: PairingFailure): String = when (reason) {
    PairingFailure.ReadOnly -> stringResource(R.string.add_failed_read_only)
    is PairingFailure.TooSmall -> stringResource(R.string.add_failed_too_small, reason.capacity, reason.needed)
    PairingFailure.NotNdefCapable -> stringResource(R.string.add_failed_not_ndef)
    PairingFailure.TagLost -> stringResource(R.string.add_failed_tag_lost)
    PairingFailure.NotVerified -> stringResource(R.string.add_failed_not_verified)
    is PairingFailure.Other -> stringResource(R.string.add_failed_other, reason.message)
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}

@Composable
private fun OutlinedAction(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}
