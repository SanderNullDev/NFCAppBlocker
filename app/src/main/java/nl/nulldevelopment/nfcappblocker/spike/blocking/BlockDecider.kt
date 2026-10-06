package nl.nulldevelopment.nfcappblocker.spike.blocking

/**
 * Decides whether a window change should bring up the block screen. Pure Kotlin, so it can be
 * unit tested. Not thread safe; the accessibility service calls it on the main thread.
 *
 * @param ownPackage this app. Never blocked, and our block screen appearing doesn't count as
 *   leaving the blocked app.
 * @param neverBlock other packages that are never blocked, whatever the list says, such as the
 *   launcher, so Home always works.
 * @param duplicateWindowMillis one app launch can produce several window changes (splash screen,
 *   then main activity). Repeats for the same app within this window are ignored.
 */
class BlockDecider(
    private val ownPackage: String,
    private val neverBlock: Set<String>,
    private val duplicateWindowMillis: Long = DEFAULT_DUPLICATE_WINDOW_MILLIS,
) {
    private var lastBlockedPackage: String? = null
    private var lastBlockedAtMillis = 0L

    fun shouldBlock(packageName: String?, nowMillis: Long, enabled: Boolean, blockedPackages: Set<String>): Boolean {
        if (packageName == null || packageName == ownPackage) return false
        if (!enabled || packageName in neverBlock || packageName !in blockedPackages) {
            lastBlockedPackage = null
            return false
        }
        if (packageName == lastBlockedPackage && nowMillis - lastBlockedAtMillis < duplicateWindowMillis) return false
        lastBlockedPackage = packageName
        lastBlockedAtMillis = nowMillis
        return true
    }

    companion object {
        const val DEFAULT_DUPLICATE_WINDOW_MILLIS = 1_000L
    }
}
