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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile
import nl.nulldevelopment.nfcappblocker.nfc.TagContent
import nl.nulldevelopment.nfcappblocker.ui.components.BackButton
import nl.nulldevelopment.nfcappblocker.ui.components.NfcStatusNotice
import nl.nulldevelopment.nfcappblocker.ui.components.ProfileRadioList
import nl.nulldevelopment.nfcappblocker.ui.components.ResumedEffect
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
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val nfcStatus = rememberNfcStatus()
    ResumedEffect(viewModel::setResumed)

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
                PairingStepContent(
                    state = state,
                    onConfirmOverwrite = viewModel::confirmOverwrite,
                    onAdopt = viewModel::adopt,
                    onRestart = viewModel::restart,
                    alreadyPaired = { tag ->
                        PairingMessage(stringResource(R.string.add_already_paired, tag.name))
                        PairingPrimaryAction(stringResource(R.string.add_open_tag)) { onOpenTag(tag.uuid) }
                        PairingSecondaryAction(stringResource(R.string.add_another), viewModel::restart)
                    },
                    paired = { tag ->
                        NameNewTag(
                            uuid = tag.uuid,
                            initialName = tag.name,
                            profiles = profiles,
                            onDone = { name, profileId ->
                                viewModel.finish(tag.uuid, name, profileId)
                                onBack()
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun NameNewTag(uuid: UUID, initialName: String, profiles: List<Profile>, onDone: (String, Long?) -> Unit) {
    var name by rememberSaveable(uuid) { mutableStateOf(initialName) }
    var profileId by rememberSaveable(uuid) { mutableStateOf<Long?>(null) }
    PairingMessage(stringResource(R.string.add_paired))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.add_name_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.add_choose_profile),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
    )
    if (profiles.isEmpty()) {
        PairingMessage(stringResource(R.string.link_no_profiles))
    } else {
        ProfileRadioList(profiles, profileId, onSelect = { profileId = it }, modifier = Modifier.fillMaxWidth())
    }
    PairingPrimaryAction(stringResource(R.string.add_done)) { onDone(name, profileId) }
}

