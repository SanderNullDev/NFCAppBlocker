package nl.nulldevelopment.nfcappblocker.feature.pairing

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.nfc.TagContent

/**
 * The text and buttons for one step of the pairing flow (D-019). Shared by "Add tag" and the
 * profile editor's "Scan a tag"; each decides what happens for a known tag and after pairing.
 */
@Composable
fun PairingStepContent(
    state: PairingState,
    onConfirmOverwrite: () -> Unit,
    onAdopt: () -> Unit,
    onRestart: () -> Unit,
    alreadyPaired: @Composable (PairedTag) -> Unit,
    paired: @Composable (PairedTag) -> Unit,
) {
    when (state) {
        PairingState.WaitingForTag -> PairingMessage(stringResource(R.string.add_waiting))
        PairingState.Writing -> {
            CircularProgressIndicator()
            PairingMessage(stringResource(R.string.add_writing))
        }
        is PairingState.ConfirmOverwrite -> {
            Text(stringResource(R.string.add_confirm_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            PairingMessage(
                stringResource(
                    when (state.content) {
                        is TagContent.UnsupportedVersion -> R.string.add_confirm_newer
                        TagContent.Unreadable -> R.string.add_confirm_unreadable
                        else -> R.string.add_confirm_foreign
                    },
                ),
            )
            PairingMessage(stringResource(R.string.add_confirm_body))
            PairingPrimaryAction(stringResource(R.string.add_overwrite), onConfirmOverwrite)
            PairingSecondaryAction(stringResource(R.string.add_cancel), onRestart)
        }
        is PairingState.WaitingForSameTag -> {
            PairingMessage(stringResource(R.string.add_hold_same_tag))
            if (state.otherTagTapped) PairingMessage(stringResource(R.string.add_other_tag))
            PairingSecondaryAction(stringResource(R.string.add_cancel), onRestart)
        }
        is PairingState.AlreadyPaired -> alreadyPaired(state.tag)
        is PairingState.OursNotPaired -> {
            PairingMessage(stringResource(R.string.add_ours_not_paired))
            PairingPrimaryAction(stringResource(R.string.add_adopt), onAdopt)
            if (state.canOverwrite) PairingSecondaryAction(stringResource(R.string.add_new_id), onConfirmOverwrite)
        }
        is PairingState.Paired -> paired(state.tag)
        is PairingState.Failed -> {
            PairingMessage(failureText(state.reason))
            PairingPrimaryAction(stringResource(R.string.add_try_again), onRestart)
        }
    }
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
fun PairingMessage(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
}

@Composable
fun PairingPrimaryAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}

@Composable
fun PairingSecondaryAction(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}
