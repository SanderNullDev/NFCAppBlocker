package nl.nulldevelopment.nfcappblocker.apps

data class InstalledApp(val packageName: String, val label: String)

interface InstalledAppsSource {
    /** Apps with a launcher icon: what users think of as their apps. */
    suspend fun launcherApps(): List<InstalledApp>

    /** Reads the current phone app, Settings and launchers, which can change at any time. */
    suspend fun neverBlockPolicy(): NeverBlockPolicy
}
