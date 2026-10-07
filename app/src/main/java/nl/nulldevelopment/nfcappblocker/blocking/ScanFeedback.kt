package nl.nulldevelopment.nfcappblocker.blocking

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.nulldevelopment.nfcappblocker.R
import javax.inject.Inject

/** Tells the user what a scan (or the escape countdown) did. */
interface ScanFeedback {
    fun show(outcome: ScanOutcome)
}

/**
 * Works also when the app isn't open: a short message (which TalkBack reads out) and a vibration
 * that differs between starting and stopping.
 */
class AndroidScanFeedback @Inject constructor(@ApplicationContext private val context: Context) : ScanFeedback {

    override fun show(outcome: ScanOutcome) {
        val message = message(outcome) ?: return
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        vibrate(
            when (outcome) {
                is ScanOutcome.Started, is ScanOutcome.Switched -> VibrationEffect.EFFECT_HEAVY_CLICK
                is ScanOutcome.Stopped -> VibrationEffect.EFFECT_DOUBLE_CLICK
                else -> VibrationEffect.EFFECT_TICK
            },
        )
    }

    private fun message(outcome: ScanOutcome): String? = when (outcome) {
        is ScanOutcome.Started -> context.getString(R.string.scan_started, outcome.profile.name)
        is ScanOutcome.Stopped -> context.getString(R.string.scan_stopped, outcome.profile.name)
        is ScanOutcome.Switched -> context.getString(R.string.scan_switched, outcome.to.name, outcome.from.name)
        is ScanOutcome.ProfileHasNoApps -> context.getString(R.string.scan_no_apps, outcome.profile.name)
        is ScanOutcome.TagNotLinked -> context.getString(R.string.scan_not_linked, outcome.tag.name)
        is ScanOutcome.UnknownTagOfOurs -> context.getString(R.string.scan_unknown_ours)
        is ScanOutcome.NotOurTag -> context.getString(R.string.scan_not_ours)
        ScanOutcome.Ignored -> null
    }

    private fun vibrate(effect: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createPredefined(effect))
    }
}
