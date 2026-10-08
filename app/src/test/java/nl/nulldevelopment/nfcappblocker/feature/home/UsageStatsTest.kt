package nl.nulldevelopment.nfcappblocker.feature.home

import nl.nulldevelopment.nfcappblocker.data.history.BlockScreenView
import nl.nulldevelopment.nfcappblocker.data.history.BlockSession
import nl.nulldevelopment.nfcappblocker.data.history.SessionEnd
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class UsageStatsTest {

    private val zone = ZoneId.of("Europe/Amsterdam")

    // Wednesday 8 October 2026, 12:00 in Amsterdam (UTC+2).
    private val now = Instant.parse("2026-10-08T10:00:00Z")
    private fun at(text: String) = Instant.parse(text)

    private fun stats(sessions: List<BlockSession>, views: List<BlockScreenView> = emptyList(), period: StatsPeriod) =
        usageStats(sessions, views, period, now, zone)

    @Test
    fun `nothing recorded gives zeros`() {
        assertEquals(UsageStats(Duration.ZERO, 0, 0, 0), stats(emptyList(), period = StatsPeriod.AllTime))
    }

    @Test
    fun `today starts at local midnight and only counts the part of a session after it`() {
        // 23:00 to 01:00 local time, across midnight.
        val overnight = BlockSession(1, at("2026-10-07T21:00:00Z"), at("2026-10-07T23:00:00Z"), SessionEnd.Tag)

        val today = stats(listOf(overnight), period = StatsPeriod.Today)

        assertEquals(Duration.ofHours(1), today.timeBlocked)
        assertEquals(0, today.timesBlocked)
        assertEquals(Duration.ofHours(2), stats(listOf(overnight), period = StatsPeriod.AllTime).timeBlocked)
    }

    @Test
    fun `a running session counts up to now`() {
        val running = BlockSession(1, at("2026-10-08T09:30:00Z"), null, null)

        assertEquals(Duration.ofMinutes(30), stats(listOf(running), period = StatsPeriod.Today).timeBlocked)
    }

    @Test
    fun `week is today and the six days before`() {
        val inside = BlockSession(1, at("2026-10-02T08:00:00Z"), at("2026-10-02T09:00:00Z"), SessionEnd.Unlock)
        val outside = BlockSession(1, at("2026-10-01T08:00:00Z"), at("2026-10-01T09:00:00Z"), SessionEnd.Unlock)

        val week = stats(listOf(outside, inside), period = StatsPeriod.Week)

        assertEquals(Duration.ofHours(1), week.timeBlocked)
        assertEquals(1, week.timesBlocked)
        assertEquals(1, week.stoppedWithoutTag)
    }

    @Test
    fun `only countdowns count as stopped without a tag`() {
        val sessions = listOf(
            BlockSession(1, at("2026-10-08T06:00:00Z"), at("2026-10-08T07:00:00Z"), SessionEnd.Tag),
            BlockSession(1, at("2026-10-08T07:00:00Z"), at("2026-10-08T08:00:00Z"), SessionEnd.Unlock),
            BlockSession(2, at("2026-10-08T08:00:00Z"), at("2026-10-08T09:00:00Z"), SessionEnd.Switch),
            BlockSession(2, at("2026-10-08T09:00:00Z"), at("2026-10-08T09:10:00Z"), SessionEnd.Deleted),
        )

        val today = stats(sessions, period = StatsPeriod.Today)

        assertEquals(4, today.timesBlocked)
        assertEquals(1, today.stoppedWithoutTag)
        assertEquals(Duration.ofMinutes(190), today.timeBlocked)
    }

    @Test
    fun `apps stopped counts block screens in the period`() {
        val views = listOf(
            BlockScreenView("com.zhiliaoapp.musically", 1, at("2026-10-08T07:00:00Z")),
            BlockScreenView("com.zhiliaoapp.musically", 1, at("2026-10-05T07:00:00Z")),
            BlockScreenView("com.instagram.android", 1, at("2026-09-01T07:00:00Z")),
        )

        assertEquals(1, stats(emptyList(), views, StatsPeriod.Today).appsStopped)
        assertEquals(2, stats(emptyList(), views, StatsPeriod.Week).appsStopped)
        assertEquals(3, stats(emptyList(), views, StatsPeriod.AllTime).appsStopped)
    }
}
