package nl.nulldevelopment.nfcappblocker.feature.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagListScreen(
    onAddTag: () -> Unit,
    onOpenTag: (UUID) -> Unit,
    onOpenTestTools: () -> Unit,
    viewModel: TagListViewModel = hiltViewModel(),
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tags_title)) },
                actions = { TextButton(onClick = onOpenTestTools) { Text(stringResource(R.string.test_tools)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTag,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.tags_add)) },
            )
        },
    ) { innerPadding ->
        val list = tags
        when {
            list == null -> Unit
            list.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.tags_empty), style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    // Room for the floating button, so it never covers the last tag.
                    bottom = innerPadding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                items(list, key = { it.uuid }) { tag ->
                    TagRow(tag, onClick = { onOpenTag(tag.uuid) })
                    HorizontalDivider()
                }
            }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

@Composable
private fun TagRow(tag: PairedTag, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(tag.name) },
        supportingContent = { Text(stringResource(R.string.tags_added_on, dateFormat.format(tag.createdAt))) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
