package nl.nulldevelopment.nfcappblocker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.apps.AppPickerItem

// The app picker's search field and rows, shared by the block list editor and onboarding.

@Composable
fun AppSearchField(query: String, onChange: (String) -> Unit, sidePadding: Dp = 16.dp) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.profile_search_apps)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePadding, vertical = 8.dp),
    )
}

@Composable
fun AppPickerRow(app: AppPickerItem, onToggle: (Boolean) -> Unit, sidePadding: Dp = 16.dp) {
    // Removing is always allowed, so an app that became protected can still be taken out.
    val enabled = app.selectable || app.selected
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(value = app.selected, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
                .padding(horizontal = sidePadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(app.packageName)
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                appNote(app)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Checkbox(checked = app.selected, onCheckedChange = null, enabled = enabled)
        }
        // Lines up with the app name: icon (40dp) and gap (16dp) after the side padding.
        HorizontalDivider(Modifier.padding(start = sidePadding + 56.dp))
    }
}

/**
 * Why an app is unavailable or unusual, in words, so it's not shown by colour alone. Every app
 * that can't be blocked reads the same, "Always allowed": the reason (phone, Settings, launcher)
 * doesn't help when choosing.
 */
@Composable
private fun appNote(app: AppPickerItem): String? = when {
    !app.installed -> stringResource(R.string.profile_app_not_installed)
    app.neverBlockReason != null -> stringResource(R.string.never_block_always_allowed)
    else -> null
}
