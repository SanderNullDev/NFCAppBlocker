package nl.nulldevelopment.nfcappblocker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStore
import nl.nulldevelopment.nfcappblocker.di.ApplicationScope
import javax.inject.Inject

/**
 * Starts onboarding over (D-037) without clearing the app's data, so it can be tried on a device
 * that's already set up. Debug builds only; only adb or the system can send it (DUMP permission).
 *
 * ```
 * adb shell am broadcast -a nl.nulldevelopment.nfcappblocker.debug.RESET_ONBOARDING -p nl.nulldevelopment.nfcappblocker
 * ```
 */
@AndroidEntryPoint
class DebugOnboardingReceiver : BroadcastReceiver() {

    @Inject lateinit var store: OnboardingStore
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                store.reset()
            } finally {
                pending.finish()
            }
        }
    }
}
