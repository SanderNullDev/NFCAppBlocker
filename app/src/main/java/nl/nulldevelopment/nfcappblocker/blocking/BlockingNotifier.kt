package nl.nulldevelopment.nfcappblocker.blocking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.MainActivity
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.apps.InstalledAppsSource
import nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository
import nl.nulldevelopment.nfcappblocker.di.ApplicationScope
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-shows the blocking notification, e.g. right after the user allows notifications, and
 * re-checks whether the accessibility service is on.
 */
interface BlockingNotifications {
    fun refresh()
}

/**
 * Keeps an ongoing notification while blocking is active, so it's visible outside the app too
 * (plan: "the user can always see whether blocking is active"). Needs the notification
 * permission on Android 13+; without it, nothing is shown and the Status tab explains why.
 *
 * When the accessibility service is off, it warns that blocking isn't working instead. That's
 * checked at app start and on [refresh], and the service reports being switched off through
 * [onServiceStopped].
 */
@Singleton
class BlockingNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blocking: BlockingRepository,
    private val apps: InstalledAppsSource,
    private val serviceStatus: BlockerServiceStatus,
    @ApplicationScope private val scope: CoroutineScope,
) : BlockingNotifications {
    private val refreshes = MutableStateFlow(0)

    private val serviceEnabled = MutableStateFlow(serviceStatus.isEnabled())

    override fun refresh() {
        serviceEnabled.value = serviceStatus.isEnabled()
        refreshes.update { it + 1 }
    }

    /** From the service itself: Android may still list it as enabled while it's unbinding. */
    fun onServiceStopped() {
        serviceEnabled.value = false
    }

    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

    /** Call once, at app start. */
    fun start() {
        createChannel()
        scope.launch {
            val labels = flow { emit(apps.launcherApps().associate { it.packageName to it.label }) }
            combine(blocking.observeActive(), labels, serviceEnabled, refreshes) { active, names, enabled, _ ->
                blockingNotice(active.firstOrNull(), enabled, names)
            }.collect { notice -> if (notice == null) cancel() else show(notice) }
        }
    }

    private fun show(notice: BlockingNotice) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (title, text) = when (notice) {
            is BlockingNotice.Blocking -> context.getString(R.string.notification_blocking_title, notice.profileName) to
                context.getString(R.string.notification_blocking_text, timeFormat.format(notice.since), notice.appNames.joinToString(", "))
            is BlockingNotice.NotWorking -> context.getString(R.string.notification_not_working_title, notice.profileName) to
                context.getString(R.string.notification_not_working_text)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the call; the Status tab shows it.
        }
    }

    private fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "blocking_status"
        const val NOTIFICATION_ID = 1
    }
}
