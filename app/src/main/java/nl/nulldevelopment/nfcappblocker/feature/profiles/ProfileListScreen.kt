package nl.nulldevelopment.nfcappblocker.feature.profiles

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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    onOpenProfile: (Long) -> Unit,
    onNewProfile: () -> Unit,
    viewModel: ProfileListViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.profiles_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewProfile,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.profiles_new)) },
            )
        },
    ) { innerPadding ->
        val list = profiles
        when {
            list == null -> Unit
            list.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.profiles_empty), style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    // Room for the floating button, so it never covers the last profile.
                    bottom = innerPadding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                items(list, key = { it.id }) { profile ->
                    ListItem(
                        headlineContent = { Text(profile.name) },
                        supportingContent = {
                            Text(pluralStringResource(R.plurals.profiles_app_count, profile.packageNames.size, profile.packageNames.size))
                        },
                        modifier = Modifier.clickable { onOpenProfile(profile.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
