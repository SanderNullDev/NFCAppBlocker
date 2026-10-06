package nl.nulldevelopment.nfcappblocker.spike.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Spike A (D-001): watches which app comes to the foreground and shows [BlockActivity] when it's
 * on the test list. Only window state changes are requested, and screen content is never read.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val scope = MainScope()
    private var enabled = false
    private lateinit var decider: BlockDecider

    override fun onServiceConnected() {
        super.onServiceConnected()
        decider = BlockDecider(ownPackage = packageName, neverBlock = launcherPackages())
        // Collected on the main thread, the same thread that delivers accessibility events.
        scope.launch { BlockingSpike.enabled(this@BlockerAccessibilityService).collect { enabled = it } }
        BlockingSpike.logEvent("Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !::decider.isInitialized) return
        val foreground = event.packageName?.toString() ?: return
        if (!decider.shouldBlock(foreground, SystemClock.uptimeMillis(), enabled, BlockingSpike.TEST_APPS.keys)) return
        // A bound accessibility service may start activities from the background.
        startActivity(BlockActivity.intent(this, foreground, eventUptimeMillis = event.eventTime))
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        BlockingSpike.logEvent("Service unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        BlockingSpike.logEvent("Service destroyed")
        scope.cancel()
        super.onDestroy()
    }

    private fun launcherPackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
    }
}
