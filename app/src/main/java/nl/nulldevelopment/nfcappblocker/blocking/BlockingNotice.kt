package nl.nulldevelopment.nfcappblocker.blocking

import nl.nulldevelopment.nfcappblocker.data.blocking.ActiveBlock
import java.time.Instant

/** What the ongoing notification says; null means no notification. */
sealed interface BlockingNotice {
    /** The number of apps rather than their names, which wouldn't fit for a long list. */
    data class Blocking(val profileName: String, val since: Instant, val appCount: Int) : BlockingNotice

    /** Blocking is on, but the accessibility service is off, so nothing gets blocked (Phase 5). */
    data class NotWorking(val profileName: String) : BlockingNotice
}

fun blockingNotice(active: ActiveBlock?, serviceEnabled: Boolean): BlockingNotice? = when {
    active == null -> null
    !serviceEnabled -> BlockingNotice.NotWorking(active.profile.name)
    else -> BlockingNotice.Blocking(
        profileName = active.profile.name,
        since = active.startedAt,
        appCount = active.profile.packageNames.size,
    )
}
