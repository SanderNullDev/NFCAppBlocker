package nl.nulldevelopment.nfcappblocker.blocking

import nl.nulldevelopment.nfcappblocker.data.blocking.ActiveBlock
import java.time.Instant

/** What the ongoing notification says; null means no notification. */
sealed interface BlockingNotice {
    data class Blocking(val profileName: String, val since: Instant, val appNames: List<String>) : BlockingNotice

    /** Blocking is on, but the accessibility service is off, so nothing gets blocked (Phase 5). */
    data class NotWorking(val profileName: String) : BlockingNotice
}

fun blockingNotice(active: ActiveBlock?, serviceEnabled: Boolean, labels: Map<String, String>): BlockingNotice? = when {
    active == null -> null
    !serviceEnabled -> BlockingNotice.NotWorking(active.profile.name)
    else -> BlockingNotice.Blocking(
        profileName = active.profile.name,
        since = active.startedAt,
        appNames = active.profile.packageNames.map { labels[it] ?: it }.sortedBy { it.lowercase() },
    )
}
