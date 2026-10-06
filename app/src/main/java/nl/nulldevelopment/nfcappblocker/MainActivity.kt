package nl.nulldevelopment.nfcappblocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.nulldevelopment.nfcappblocker.feature.pairing.AddTagScreen
import nl.nulldevelopment.nfcappblocker.feature.profiles.ProfileListScreen
import nl.nulldevelopment.nfcappblocker.feature.tags.TagDetailsScreen
import nl.nulldevelopment.nfcappblocker.feature.tags.TagListScreen
import nl.nulldevelopment.nfcappblocker.navigation.AddTagRoute
import nl.nulldevelopment.nfcappblocker.navigation.Navigator
import nl.nulldevelopment.nfcappblocker.navigation.ProfilesRoute
import nl.nulldevelopment.nfcappblocker.navigation.TagDetailsRoute
import nl.nulldevelopment.nfcappblocker.navigation.TagListRoute
import nl.nulldevelopment.nfcappblocker.navigation.TestToolsRoute
import nl.nulldevelopment.nfcappblocker.navigation.rememberNavigationState
import nl.nulldevelopment.nfcappblocker.nfc.NfcTagEvents
import nl.nulldevelopment.nfcappblocker.nfc.TagScans
import nl.nulldevelopment.nfcappblocker.spike.blocking.BlockerAccessibilityService
import nl.nulldevelopment.nfcappblocker.spike.blocking.BlockingSpike
import nl.nulldevelopment.nfcappblocker.spike.blocking.BlockingSpikeState
import nl.nulldevelopment.nfcappblocker.spike.blocking.blockingSpikeItems
import nl.nulldevelopment.nfcappblocker.ui.scan.ScanDebugScreen
import nl.nulldevelopment.nfcappblocker.ui.scan.WriteControls
import nl.nulldevelopment.nfcappblocker.ui.theme.NFCAppBlockerTheme
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tagEvents: NfcTagEvents

    private enum class ReaderStatus { ACTIVE, NFC_OFF, NO_NFC }

    private var readerStatus by mutableStateOf(ReaderStatus.ACTIVE)
    private var accessibilityServiceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NFCAppBlockerTheme {
                val navigationState = rememberNavigationState(ProfilesRoute, TABS.keys)
                val navigator = remember(navigationState) { Navigator(navigationState) }
                val entryProvider = entryProvider {
                    entry<ProfilesRoute> { ProfileListScreen() }
                    entry<TagListRoute> {
                        TagListScreen(
                            onAddTag = { navigator.navigate(AddTagRoute) },
                            onOpenTag = { uuid -> navigator.navigate(TagDetailsRoute(uuid.toString())) },
                            onOpenTestTools = { navigator.navigate(TestToolsRoute) },
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
                    entry<TestToolsRoute> { TestTools() }
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

    @Composable
    private fun TestTools() {
        val scans by TagScans.recent.collectAsStateWithLifecycle()
        val writeArmed by TagScans.writeArmed.collectAsStateWithLifecycle()
        val blockingFlow = remember { BlockingSpike.enabled(this@MainActivity) }
        val blockingOn by blockingFlow.collectAsStateWithLifecycle(initialValue = false)
        val latencies by BlockingSpike.latencies.collectAsStateWithLifecycle()
        val events by BlockingSpike.events.collectAsStateWithLifecycle()
        // While shown, scans go to the spike's scan log (and write test) instead of a pairing flow.
        LaunchedEffect(Unit) {
            tagEvents.platformTags.collect { tag -> withContext(Dispatchers.IO) { TagScans.onReaderModeTag(tag) } }
        }
        val status = stringResource(
            when (readerStatus) {
                ReaderStatus.ACTIVE -> R.string.scan_status_reader_mode
                ReaderStatus.NFC_OFF -> R.string.scan_status_nfc_off
                ReaderStatus.NO_NFC -> R.string.scan_status_no_nfc
            },
        )
        ScanDebugScreen(
            status = status,
            scans = scans,
            writeControls = WriteControls(writeArmed, TagScans::armWrite, TagScans::cancelWrite)
                .takeIf { readerStatus == ReaderStatus.ACTIVE },
            extraItems = {
                blockingSpikeItems(
                    state = BlockingSpikeState(accessibilityServiceEnabled, blockingOn, latencies, events),
                    onBlockingChange = { on -> lifecycleScope.launch { BlockingSpike.setEnabled(this@MainActivity, on) } },
                    onOpenAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                )
            },
        )
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
        accessibilityServiceEnabled = isBlockerServiceEnabled()
    }

    private fun isBlockerServiceEnabled(): Boolean {
        val ours = ComponentName(this, BlockerAccessibilityService::class.java)
        return getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == ours }
    }

    override fun onPause() {
        NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)
        // An armed write shouldn't wait around to overwrite some tag after the user comes back.
        TagScans.cancelWrite()
        super.onPause()
    }

    private class Tab(val icon: ImageVector, @StringRes val label: Int)

    private companion object {
        val TABS: Map<NavKey, Tab> = linkedMapOf(
            ProfilesRoute to Tab(Icons.AutoMirrored.Filled.List, R.string.tab_profiles),
            TagListRoute to Tab(Icons.Filled.Lock, R.string.tab_tags),
        )

        const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
    }
}
