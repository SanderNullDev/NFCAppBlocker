package nl.nulldevelopment.nfcappblocker.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.apps.AppPickerItem
import nl.nulldevelopment.nfcappblocker.apps.NeverBlockReason
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.feature.pairing.PairingMessage
import nl.nulldevelopment.nfcappblocker.feature.pairing.PairingPrimaryAction
import nl.nulldevelopment.nfcappblocker.feature.pairing.PairingStepContent
import nl.nulldevelopment.nfcappblocker.ui.components.AppIcon
import nl.nulldevelopment.nfcappblocker.ui.components.AppPickerRow
import nl.nulldevelopment.nfcappblocker.ui.components.AppSearchField
import nl.nulldevelopment.nfcappblocker.ui.components.BackButton
import nl.nulldevelopment.nfcappblocker.ui.components.NfcStatusNotice
import nl.nulldevelopment.nfcappblocker.ui.components.ResumedEffect
import nl.nulldevelopment.nfcappblocker.ui.components.rememberNfcStatus
import nl.nulldevelopment.nfcappblocker.ui.components.SoftCard
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    /** Null for a new profile, which is only created when saved. */
    profileId: Long?,
    onBack: () -> Unit,
    /** Opens a linked tag's details (D-043): tags are managed from here and from Settings. */
    onOpenTag: (UUID) -> Unit,
    viewModel: ProfileEditorViewModel = hiltViewModel<ProfileEditorViewModel, ProfileEditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(profileId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()
    ResumedEffect(viewModel::setResumed)
    // Saved, deleted or gone: close once.
    val shouldClose = closed || state == ProfileEditorState.Missing
    LaunchedEffect(shouldClose) { if (shouldClose) onBack() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val loaded = state as? ProfileEditorState.Loaded
    val hasChanges = loaded?.hasChanges == true
    val leave = { if (hasChanges) confirmDiscard = true else onBack() }
    // System Back asks too, so unsaved changes are never dropped silently.
    BackHandler(enabled = hasChanges && !shouldClose) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (loaded?.isNew == true) stringResource(R.string.profile_new_title) else loaded?.name.orEmpty())
                },
                navigationIcon = { BackButton(leave) },
                actions = {
                    if (loaded != null && !loaded.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.profile_delete))
                        }
                    }
                    TextButton(
                        onClick = viewModel::save,
                        enabled = loaded?.canSave == true,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.profile_save)) }
                },
            )
        },
    ) { innerPadding ->
        if (loaded == null) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            if (loaded.isActive) {
                item {
                    Text(
                        stringResource(R.string.profile_active_note),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item { NameField(profileId, loaded.name, viewModel::setName) }
            item { SectionHeading(stringResource(R.string.profile_tags_heading)) }
            if (loaded.linkedTags.isEmpty()) {
                item { Hint(stringResource(R.string.profile_no_tags)) }
            }
            items(loaded.linkedTags, key = { "tag-${it.uuid}" }) { tag ->
                LinkedTagRow(tag, onOpen = { onOpenTag(tag.uuid) }, onUnlink = { viewModel.unlinkTag(tag.uuid) })
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::startScan,
                        enabled = scan == null,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.profile_scan_tag)) }
                    LinkTagButton(loaded.otherTags, onLink = viewModel::linkTag)
                }
            }
            scan?.let { current -> item { ScanPanel(current, viewModel) } }
            item {
                SectionHeading(
                    pluralStringResource(R.plurals.profile_apps_heading, loaded.selectedCount, loaded.selectedCount),
                )
            }
            item { AppSearchField(loaded.query, viewModel::setQuery) }
            items(loaded.apps, key = { "app-${it.packageName}" }) { app ->
                AppPickerRow(app, onToggle = { selected -> viewModel.setAppSelected(app.packageName, selected) })
            }
        }
    }

    if (confirmDelete && loaded != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.profile_delete_title, loaded.name)) },
            text = {
                Text(stringResource(if (loaded.isActive) R.string.profile_delete_body_active else R.string.profile_delete_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.profile_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.add_cancel)) } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.profile_discard_title)) },
            text = { Text(stringResource(R.string.profile_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.profile_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.profile_keep_editing)) }
            },
        )
    }
}

@Composable
private fun NameField(profileId: Long?, savedName: String, onChange: (String) -> Unit) {
    // Local text state, so updates coming back from the ViewModel never move the cursor.
    var name by rememberSaveable(profileId) { mutableStateOf(savedName) }
    OutlinedTextField(
        value = name,
        onValueChange = {
            name = it
            onChange(it)
        },
        label = { Text(stringResource(R.string.add_name_label)) },
        isError = name.isBlank(),
        supportingText = if (name.isBlank()) {
            { Text(stringResource(R.string.profile_name_required)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun LinkedTagRow(tag: PairedTag, onOpen: () -> Unit, onUnlink: () -> Unit) {
    ListItem(
        headlineContent = { Text(tag.name) },
        modifier = Modifier.clickable(onClick = onOpen),
        trailingContent = {
            TextButton(onClick = onUnlink, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.profile_unlink_tag))
            }
        },
    )
}

@Composable
private fun LinkTagButton(choices: List<TagChoice>, onLink: (UUID) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var pendingMove by rememberSaveable { mutableStateOf<String?>(null) }
    OutlinedButton(
        onClick = { open = true },
        enabled = choices.isNotEmpty(),
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.profile_link_tag)) }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.profile_link_tag)) },
            text = {
                Column {
                    choices.forEach { choice ->
                        ListItem(
                            headlineContent = { Text(choice.tag.name) },
                            supportingContent = choice.currentProfileName?.let {
                                { Text(stringResource(R.string.profile_tag_in_other, it)) }
                            },
                            // Blend into the dialog instead of showing as white blocks.
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .toggleable(value = false, role = Role.Button, onValueChange = {
                                    open = false
                                    if (choice.currentProfileName != null) {
                                        pendingMove = choice.tag.uuid.toString()
                                    } else {
                                        onLink(choice.tag.uuid)
                                    }
                                }),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.add_cancel)) } },
        )
    }

    // Moving a tag away from another profile is said out loud before it happens (D-003).
    val moving = pendingMove?.let { id -> choices.firstOrNull { it.tag.uuid.toString() == id } }
    if (moving != null) {
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text(stringResource(R.string.profile_move_tag_title, moving.tag.name)) },
            text = { Text(stringResource(R.string.profile_move_tag_body, moving.currentProfileName.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    pendingMove = null
                    onLink(moving.tag.uuid)
                }) { Text(stringResource(R.string.profile_move_tag)) }
            },
            dismissButton = { TextButton(onClick = { pendingMove = null }) { Text(stringResource(R.string.add_cancel)) } },
        )
    }
}

@Composable
private fun ScanPanel(scan: EditorScan, viewModel: ProfileEditorViewModel) {
    SoftCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                // Polite live region: TalkBack announces each step as it changes.
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NfcStatusNotice(rememberNfcStatus())
            when (scan) {
                is EditorScan.Pairing -> PairingStepContent(
                    state = scan.state,
                    onConfirmOverwrite = viewModel::confirmOverwrite,
                    onAdopt = viewModel::adopt,
                    onRestart = viewModel::restartScan,
                    // Handled by the ViewModel, which turns a known tag into a link or a question.
                    alreadyPaired = {},
                    paired = {},
                )
                is EditorScan.AlreadyHere -> {
                    PairingMessage(stringResource(R.string.profile_scan_already_here, scan.tagName))
                    PairingMessage(stringResource(R.string.add_waiting))
                }
                is EditorScan.ConfirmMove -> {
                    PairingMessage(stringResource(R.string.profile_move_tag_title, scan.tag.name))
                    PairingMessage(stringResource(R.string.profile_move_tag_body, scan.fromProfile))
                    PairingPrimaryAction(stringResource(R.string.profile_move_tag), viewModel::confirmMove)
                }
                is EditorScan.NameNewTag -> NameScannedTag(scan.tag, onDone = viewModel::finishNewTag)
            }
            TextButton(onClick = viewModel::stopScan, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.add_cancel))
            }
        }
    }
}

@Composable
private fun NameScannedTag(tag: PairedTag, onDone: (String) -> Unit) {
    var name by rememberSaveable(tag.uuid) { mutableStateOf(tag.name) }
    PairingMessage(stringResource(R.string.add_paired))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.add_name_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    PairingPrimaryAction(stringResource(R.string.profile_scan_add_to_profile)) { onDone(name) }
}

