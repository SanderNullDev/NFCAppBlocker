package nl.nulldevelopment.nfcappblocker.testing

import nl.nulldevelopment.nfcappblocker.apps.InstalledApp
import nl.nulldevelopment.nfcappblocker.apps.InstalledAppsSource
import nl.nulldevelopment.nfcappblocker.apps.NeverBlockPolicy

class FakeInstalledAppsSource(
    private val apps: List<InstalledApp>,
    private val policy: NeverBlockPolicy = NeverBlockPolicy(
        ownPackage = "nl.nulldevelopment.nfcappblocker",
        phonePackages = setOf("com.samsung.android.dialer"),
        settingsPackages = setOf("com.android.settings"),
        launcherPackages = setOf("ginlemon.flowerfree"),
    ),
) : InstalledAppsSource {
    override suspend fun launcherApps() = apps
    override suspend fun neverBlockPolicy() = policy
}
