package nl.nulldevelopment.nfcappblocker.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.apps.InstalledAppsSource
import nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository
import nl.nulldevelopment.nfcappblocker.data.history.UsageHistory
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingProgress
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStore
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile
import javax.inject.Inject

/**
 * Enforces blocking (D-001): when an app of the active profile comes to the foreground, the block
 * screen covers it. Only window state changes are requested, and screen content is never read.
 */
@AndroidEntryPoint
class BlockerAccessibilityService : AccessibilityService() {

    @Inject lateinit var blocking: BlockingRepository
    @Inject lateinit var apps: InstalledAppsSource
    @Inject lateinit var notifier: BlockingNotifier
    @Inject lateinit var history: UsageHistory
    @Inject lateinit var onboarding: OnboardingStore

    private val scope = MainScope()
    private lateinit var decider: BlockDecider

    /** Package name to the profile blocking it. Updated on the main thread, read on the main thread. */
    private var blocked: Map<String, Profile> = emptyMap()

    /** Onboarding's "Try it" is running: the block screen leads back to the app (D-037). */
    private var duringOnboarding = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        decider = BlockDecider(ownPackage = packageName)
        // Switched (back) on: clears a "not working" warning.
        notifier.refresh()
        scope.launch {
            // The never-block rule is re-read on every change, so a new launcher or phone app is
            // picked up the next time blocking starts.
            blocking.observeActive()
                .map { active -> blockList(active, apps.neverBlockPolicy()) }
                .collect { blocked = it }
        }
        scope.launch {
            onboarding.observe().collect { duringOnboarding = it is OnboardingProgress.InProgress }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !::decider.isInitialized) return
        val foreground = event.packageName?.toString() ?: return
        if (!decider.shouldBlock(foreground, SystemClock.uptimeMillis(), blocked.keys)) return
        val profile = blocked[foreground] ?: return
        // A bound accessibility service may start activities from the background.
        startActivity(BlockActivity.intent(this, foreground, profile.name, duringOnboarding))
        scope.launch { history.recordBlockScreen(foreground, profile.id) }
    }

    override fun onInterrupt() = Unit

    /** The user switched the service off (or Android did); the notification warns while blocking. */
    override fun onUnbind(intent: Intent?): Boolean {
        notifier.onServiceStopped()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
