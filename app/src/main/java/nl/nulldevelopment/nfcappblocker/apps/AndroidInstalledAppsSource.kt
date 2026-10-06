package nl.nulldevelopment.nfcappblocker.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import nl.nulldevelopment.nfcappblocker.di.IoDispatcher
import javax.inject.Inject

/** Needs the `<queries>` entries for launcher and home intents in the manifest (D-010). */
class AndroidInstalledAppsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : InstalledAppsSource {

    private val packageManager: PackageManager get() = context.packageManager

    override suspend fun launcherApps(): List<InstalledApp> = withContext(ioDispatcher) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(launcher, 0)
            .distinctBy { it.activityInfo.packageName }
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
    }

    override suspend fun neverBlockPolicy(): NeverBlockPolicy = withContext(ioDispatcher) {
        NeverBlockPolicy(
            ownPackage = context.packageName,
            phonePackages = setOfNotNull(context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage),
            settingsPackages = packagesFor(Intent(Settings.ACTION_SETTINGS)) + "com.android.settings",
            launcherPackages = packagesFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)),
        )
    }

    private fun packagesFor(intent: Intent): Set<String> =
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapTo(mutableSetOf()) { it.activityInfo.packageName }
}
