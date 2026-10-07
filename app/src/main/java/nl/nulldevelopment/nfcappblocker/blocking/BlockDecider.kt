package nl.nulldevelopment.nfcappblocker.blocking

/**
 * Decides whether a window change should bring up the block screen. Pure Kotlin, so it can be
 * unit tested. Not thread safe; the accessibility service calls it on the main thread.
 *
 * @param ownPackage this app. Our block screen appearing doesn't count as leaving the blocked app.
 * @param duplicateWindowMillis one app launch can produce several window changes (splash screen,
 *   then main activity). Repeats for the same app within this window are ignored.
 */
class BlockDecider(
    private val ownPackage: String,
    private val duplicateWindowMillis: Long = DEFAULT_DUPLICATE_WINDOW_MILLIS,
) {
    private var lastBlockedPackage: String? = null
    private var lastBlockedAtMillis = 0L

    /** @param blockedPackages already without protected apps; see [blockList]. */
    fun shouldBlock(packageName: String?, nowMillis: Long, blockedPackages: Set<String>): Boolean {
        if (packageName == null || packageName == ownPackage) return false
        if (packageName !in blockedPackages) {
            // Any other app in front (the launcher too) means the next open is a new one.
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
