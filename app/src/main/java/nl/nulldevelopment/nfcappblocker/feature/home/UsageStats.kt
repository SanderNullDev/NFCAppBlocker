package nl.nulldevelopment.nfcappblocker.feature.home

import nl.nulldevelopment.nfcappblocker.data.history.BlockScreenView
import nl.nulldevelopment.nfcappblocker.data.history.BlockSession
import nl.nulldevelopment.nfcappblocker.data.history.SessionEnd
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The period the home screen's numbers cover (D-044). */
enum class StatsPeriod { Today, Week, AllTime }

data class UsageStats(
    val timeBlocked: Duration,
    /** Sessions started in the period. */
    val timesBlocked: Int,
    /** Block screens shown in the period. */
    val appsStopped: Int,
    /** Countdowns that finished in the period. */
    val stoppedWithoutTag: Int,
)

/**
 * The four numbers for [period] (D-044). Today starts at midnight in [zone]; Week is today and the
 * six days before. Time blocked counts only the part of each session inside the period, and a
 * running session up to [now].
 */
fun usageStats(
    sessions: List<BlockSession>,
    views: List<BlockScreenView>,
    period: StatsPeriod,
    now: Instant,
    zone: ZoneId,
): UsageStats {
    val today = LocalDate.ofInstant(now, zone)
    val from = when (period) {
        StatsPeriod.Today -> today.atStartOfDay(zone).toInstant()
        StatsPeriod.Week -> today.minusDays(6).atStartOfDay(zone).toInstant()
        StatsPeriod.AllTime -> Instant.EPOCH
    }
    val blocked = sessions.fold(Duration.ZERO) { total, session ->
        val start = maxOf(session.startedAt, from)
        val end = minOf(session.endedAt ?: now, now)
        if (end > start) total + Duration.between(start, end) else total
    }
    return UsageStats(
        timeBlocked = blocked,
        timesBlocked = sessions.count { it.startedAt >= from },
        appsStopped = views.count { it.shownAt >= from },
        stoppedWithoutTag = sessions.count { it.endedBy == SessionEnd.Unlock && it.endedAt != null && it.endedAt >= from },
    )
}
