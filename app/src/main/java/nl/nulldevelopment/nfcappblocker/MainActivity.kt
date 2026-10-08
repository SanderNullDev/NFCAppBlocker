package nl.nulldevelopment.nfcappblocker

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import nl.nulldevelopment.nfcappblocker.ui.components.PredictiveBackFrame
import androidx.compose.animation.scaleOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ContentTransform
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.nulldevelopment.nfcappblocker.blocking.ScanNavigation
import nl.nulldevelopment.nfcappblocker.blocking.ScanToggleRunner
import nl.nulldevelopment.nfcappblocker.feature.onboarding.OnboardingScreen
import nl.nulldevelopment.nfcappblocker.feature.onboarding.OnboardingState
import nl.nulldevelopment.nfcappblocker.feature.onboarding.OnboardingViewModel
import nl.nulldevelopment.nfcappblocker.feature.pairing.AddTagScreen
import nl.nulldevelopment.nfcappblocker.feature.profiles.ProfileEditorScreen
import nl.nulldevelopment.nfcappblocker.feature.settings.SettingsScreen
import nl.nulldevelopment.nfcappblocker.feature.home.HomeScreen
import nl.nulldevelopment.nfcappblocker.feature.tags.TagDetailsScreen
import nl.nulldevelopment.nfcappblocker.feature.tags.TagListScreen
import nl.nulldevelopment.nfcappblocker.navigation.AddTagRoute
import nl.nulldevelopment.nfcappblocker.navigation.Navigator
import nl.nulldevelopment.nfcappblocker.navigation.ProfileEditorRoute
import nl.nulldevelopment.nfcappblocker.navigation.SettingsRoute
import nl.nulldevelopment.nfcappblocker.navigation.HomeRoute
import nl.nulldevelopment.nfcappblocker.navigation.TagDetailsRoute
import nl.nulldevelopment.nfcappblocker.navigation.TagListRoute
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
                // First-run onboarding takes the whole screen until it's finished (D-037).
                val onboarding: OnboardingViewModel = hiltViewModel()
                val onboardingState by onboarding.state.collectAsStateWithLifecycle()
                when (val shown = onboardingState) {
                    OnboardingState.Loading -> Surface(Modifier.fillMaxSize()) {}
                    is OnboardingState.Showing -> OnboardingScreen(shown, onboarding)
                    OnboardingState.Hidden -> MainContent()
                }
            }
        }
    }

    @Composable
    private fun MainContent() {
        val backStack = rememberNavBackStack(HomeRoute)
        val navigator = remember(backStack) { Navigator(backStack) }
        // Scans that couldn't toggle anything ask for a screen: from the runner while the app
        // is open, or through the intent when ScanActivity opened the app.
        LaunchedEffect(navigator) { scanToggleRunner.navigation.collect { pendingNavigation = it } }
        LaunchedEffect(pendingNavigation) {
            when (val request = pendingNavigation) {
                is ScanNavigation.OpenTag -> navigator.openFromHome(TagDetailsRoute(request.uuid.toString()))
                ScanNavigation.AddTag -> navigator.openFromHome(AddTagRoute)
                null -> Unit
            }
            pendingNavigation = null
        }
        val entryProvider = entryProvider {
            entry<HomeRoute> {
                PredictiveBackFrame {
                    HomeScreen(
                        onOpenSettings = { navigator.navigate(SettingsRoute) },
                        onOpenBlockList = { id -> navigator.navigate(ProfileEditorRoute(id)) },
                        onNewBlockList = { navigator.navigate(ProfileEditorRoute(null)) },
                        onAddTag = { navigator.navigate(AddTagRoute) },
                    )
                }
            }
            entry<SettingsRoute> {
                PredictiveBackFrame {
                    SettingsScreen(onBack = navigator::goBack, onOpenTags = { navigator.navigate(TagListRoute) })
                }
            }
            entry<ProfileEditorRoute> { route ->
                PredictiveBackFrame {
                    ProfileEditorScreen(
                        profileId = route.profileId,
                        onBack = navigator::goBack,
                        onOpenTag = { uuid -> navigator.navigate(TagDetailsRoute(uuid.toString())) },
                    )
                }
            }
            entry<TagListRoute> {
                PredictiveBackFrame {
                    TagListScreen(
                        onBack = navigator::goBack,
                        onAddTag = { navigator.navigate(AddTagRoute) },
                        onOpenTag = { uuid -> navigator.navigate(TagDetailsRoute(uuid.toString())) },
                    )
                }
            }
            entry<AddTagRoute> {
                PredictiveBackFrame {
                    AddTagScreen(
                        onBack = navigator::goBack,
                        // Replace "add tag" so Back from the details returns to where it was opened.
                        onOpenTag = { uuid -> navigator.replace(TagDetailsRoute(uuid.toString())) },
                    )
                }
            }
            entry<TagDetailsRoute> { route ->
                PredictiveBackFrame {
                    TagDetailsScreen(uuid = UUID.fromString(route.uuid), onBack = navigator::goBack)
                }
            }
        }
        NavDisplay(
            entries = rememberDecoratedNavEntries(
                backStack = backStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider,
            ),
            onBack = navigator::goBack,
            // The leaving screen shrinks a little and gets rounded corners (PredictiveBackFrame)
            // over the steady screen behind it, instead of the default 70% shrink over a fade-in.
            predictivePopTransitionSpec = {
                ContentTransform(
                    targetContentEnter = EnterTransition.None,
                    initialContentExit = scaleOut(targetScale = 0.9f),
                    targetContentZIndex = -1f,
                )
            },
        )
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


        private const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
    }
}
