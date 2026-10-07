package nl.nulldevelopment.nfcappblocker

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.nulldevelopment.nfcappblocker.blocking.ScanNavigation
import nl.nulldevelopment.nfcappblocker.blocking.ScanToggleRunner
import nl.nulldevelopment.nfcappblocker.feature.pairing.AddTagScreen
import nl.nulldevelopment.nfcappblocker.feature.profiles.ProfileEditorScreen
import nl.nulldevelopment.nfcappblocker.feature.profiles.ProfileListScreen
import nl.nulldevelopment.nfcappblocker.feature.status.StatusScreen
import nl.nulldevelopment.nfcappblocker.feature.tags.TagDetailsScreen
import nl.nulldevelopment.nfcappblocker.feature.tags.TagListScreen
import nl.nulldevelopment.nfcappblocker.navigation.AddTagRoute
import nl.nulldevelopment.nfcappblocker.navigation.Navigator
import nl.nulldevelopment.nfcappblocker.navigation.ProfileEditorRoute
import nl.nulldevelopment.nfcappblocker.navigation.ProfilesRoute
import nl.nulldevelopment.nfcappblocker.navigation.StatusRoute
import nl.nulldevelopment.nfcappblocker.navigation.TagDetailsRoute
import nl.nulldevelopment.nfcappblocker.navigation.TagListRoute
import nl.nulldevelopment.nfcappblocker.navigation.rememberNavigationState
import nl.nulldevelopment.nfcappblocker.nfc.NfcTagEvents
import nl.nulldevelopment.nfcappblocker.ui.theme.NFCAppBlockerTheme
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tagEvents: NfcTagEvents
    @Inject lateinit var scanToggleRunner: ScanToggleRunner

    /** A screen to open, asked for by a scan; see [intentFor]. */
    private var pendingNavigation by mutableStateOf<ScanNavigation?>(null)

    private enum class ReaderStatus { ACTIVE, NFC_OFF, NO_NFC }

    private var readerStatus by mutableStateOf(ReaderStatus.ACTIVE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingNavigation = navigationFrom(intent)
        enableEdgeToEdge()
        setContent {
            NFCAppBlockerTheme {
                val navigationState = rememberNavigationState(StatusRoute, TABS.keys)
                val navigator = remember(navigationState) { Navigator(navigationState) }
                // Scans that couldn't toggle anything ask for a screen: from the runner while the app
                // is open, or through the intent when ScanActivity opened the app.
                LaunchedEffect(navigator) { scanToggleRunner.navigation.collect { pendingNavigation = it } }
                LaunchedEffect(pendingNavigation) {
                    when (val request = pendingNavigation) {
                        is ScanNavigation.OpenTag -> {
                            navigator.navigate(TagListRoute)
                            navigator.navigate(TagDetailsRoute(request.uuid.toString()))
                        }
                        ScanNavigation.AddTag -> {
                            navigator.navigate(TagListRoute)
                            navigator.navigate(AddTagRoute)
                        }
                        null -> Unit
                    }
                    pendingNavigation = null
                }
                val entryProvider = entryProvider {
                    entry<StatusRoute> {
                        StatusScreen(
                            onOpenProfiles = { navigator.navigate(ProfilesRoute) },
                            onOpenTags = { navigator.navigate(TagListRoute) },
                        )
                    }
                    entry<ProfilesRoute> {
                        ProfileListScreen(
                            onOpenProfile = { id -> navigator.navigate(ProfileEditorRoute(id)) },
                            onNewProfile = { navigator.navigate(ProfileEditorRoute(null)) },
                        )
                    }
                    entry<ProfileEditorRoute> { route ->
                        ProfileEditorScreen(profileId = route.profileId, onBack = navigator::goBack)
                    }
                    entry<TagListRoute> {
                        TagListScreen(
                            onAddTag = { navigator.navigate(AddTagRoute) },
                            onOpenTag = { uuid -> navigator.navigate(TagDetailsRoute(uuid.toString())) },
                        )
                    }
                    entry<AddTagRoute> {
                        AddTagScreen(
                            onBack = navigator::goBack,
                            // Replace "add tag" so Back from the details returns to the list.
                            onOpenTag = { uuid -> navigator.replace(TagDetailsRoute(uuid.toString())) },
                        )
                    }
                    entry<TagDetailsRoute> { route ->
                        TagDetailsScreen(uuid = UUID.fromString(route.uuid), onBack = navigator::goBack)
                    }
                }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            TABS.forEach { (route, tab) ->
                                NavigationBarItem(
                                    selected = route == navigationState.topLevelRoute,
                                    onClick = { navigator.navigate(route) },
                                    // The label names the tab, so the icon needs no description.
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(stringResource(tab.label)) },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    // The bar covers the bottom inset; each screen's own top bar handles the top.
                    val barPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
                    NavDisplay(
                        entries = navigationState.toDecoratedEntries(entryProvider),
                        onBack = navigator::goBack,
                        modifier = Modifier
                            .padding(barPadding)
                            .consumeWindowInsets(barPadding),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navigationFrom(intent)?.let { pendingNavigation = it }
    }

    private fun navigationFrom(intent: Intent): ScanNavigation? = when (intent.getStringExtra(EXTRA_OPEN)) {
        OPEN_TAG -> intent.getStringExtra(EXTRA_TAG_UUID)?.let { ScanNavigation.OpenTag(UUID.fromString(it)) }
        OPEN_ADD_TAG -> ScanNavigation.AddTag
        else -> null
    }

    /**
     * While the app is in the foreground, read tags with reader mode instead of tag dispatch, so
     * a scan goes to the screen that's open instead of relaunching an activity on top of it.
     */
    override fun onResume() {
        super.onResume()
        val adapter = NfcAdapter.getDefaultAdapter(this)
        readerStatus = when {
            adapter == null -> ReaderStatus.NO_NFC
            !adapter.isEnabled -> ReaderStatus.NFC_OFF
            else -> ReaderStatus.ACTIVE
        }
        adapter?.enableReaderMode(this, tagEvents::onTagDiscovered, READER_FLAGS, null)
    }

    override fun onPause() {
        NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)
        super.onPause()
    }

    private class Tab(val icon: ImageVector, @StringRes val label: Int)

    companion object {
        private const val EXTRA_OPEN = "open"
        private const val EXTRA_TAG_UUID = "tag_uuid"
        private const val OPEN_TAG = "tag"
        private const val OPEN_ADD_TAG = "add_tag"

        /** Opens the app on the screen a scan asked for. */
        fun intentFor(context: Context, navigation: ScanNavigation): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply {
                    when (navigation) {
                        is ScanNavigation.OpenTag -> putExtra(EXTRA_OPEN, OPEN_TAG).putExtra(EXTRA_TAG_UUID, navigation.uuid.toString())
                        ScanNavigation.AddTag -> putExtra(EXTRA_OPEN, OPEN_ADD_TAG)
                    }
                }

        private val TABS: Map<NavKey, Tab> = linkedMapOf(
            StatusRoute to Tab(Icons.Filled.Info, R.string.tab_status),
            ProfilesRoute to Tab(Icons.AutoMirrored.Filled.List, R.string.tab_profiles),
            TagListRoute to Tab(Icons.Filled.Lock, R.string.tab_tags),
        )

        private const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
    }
}
