package nl.nulldevelopment.nfcappblocker.apps

/** Why an app can never be blocked. Shown in the picker, so it's clear why it's unavailable. */
enum class NeverBlockReason { THIS_APP, PHONE, SETTINGS, LAUNCHER, ALWAYS_ALLOWED }

/**
 * Apps that are never blocked, whatever a profile says (Phase 2): this app, the phone app (for
 * emergencies), Settings and launchers (so the user is never trapped), plus apps the user always
 * wants available. Shared by the app picker and, from Phase 3, the blocker.
 */
class NeverBlockPolicy(
    private val ownPackage: String,
    private val phonePackages: Set<String>,
    private val settingsPackages: Set<String>,
    private val launcherPackages: Set<String>,
    private val alwaysAllowed: Set<String> = ALWAYS_ALLOWED,
) {
    /** Null when the app can be blocked. The most specific reason wins. */
    fun reasonFor(packageName: String): NeverBlockReason? = when (packageName) {
        ownPackage -> NeverBlockReason.THIS_APP
        in phonePackages -> NeverBlockReason.PHONE
        in settingsPackages -> NeverBlockReason.SETTINGS
        in launcherPackages -> NeverBlockReason.LAUNCHER
        in alwaysAllowed -> NeverBlockReason.ALWAYS_ALLOWED
        else -> null
    }

    fun canBlock(packageName: String): Boolean = reasonFor(packageName) == null

    companion object {
        /** Chosen by the user: maps and email must always stay available. */
        val ALWAYS_ALLOWED = setOf(
            "com.google.android.apps.maps",
            "com.google.android.gm",
            "com.samsung.android.email.provider",
        )
    }
}
