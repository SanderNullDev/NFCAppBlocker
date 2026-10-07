package nl.nulldevelopment.nfcappblocker.blocking

import nl.nulldevelopment.nfcappblocker.data.settings.CountdownLength

/** Where an [EscapeCountdown] stands. */
sealed interface CountdownState {
    data class Running(val remainingMillis: Long) : CountdownState {
        /** Rounded up, so the display reaches 0:00 only when the countdown finishes. */
        val remainingSeconds: Long get() = (remainingMillis + 999) / 1_000
    }

    /** The full time passed: blocking may stop. */
    data object Finished : CountdownState

    /** Left early; blocking goes on. */
    data object Cancelled : CountdownState
}

/**
 * Stopping blocking without a tag: the user waits out a countdown of [minutes] (Phase 5). It starts
 * when created. Pure logic; the caller ticks it and decides what finishing or cancelling does.
 *
 * @param elapsedMillis a monotonic clock, e.g. `SystemClock.elapsedRealtime`.
 */
class EscapeCountdown(minutes: Int, private val elapsedMillis: () -> Long) {

    val durationMillis: Long = CountdownLength.clamp(minutes) * MILLIS_PER_MINUTE
    private val startedAtMillis = elapsedMillis()
    private var cancelled = false
    private var lastAnnouncedMinutes: Long? = null

    fun state(): CountdownState {
        if (cancelled) return CountdownState.Cancelled
        val remaining = durationMillis - (elapsedMillis() - startedAtMillis)
        return if (remaining <= 0) CountdownState.Finished else CountdownState.Running(remaining)
    }

    /** Has no effect once finished: the time was already waited out. */
    fun cancel() {
        if (state() !is CountdownState.Finished) cancelled = true
    }

    /**
     * The whole minutes left when a new one is reached (rounded up: the full length at the start,
     * then each minute mark), or null. Each value is returned once, so TalkBack can announce the
     * remaining time once a minute instead of every second.
     */
    fun minutesToAnnounce(): Long? {
        val running = state() as? CountdownState.Running ?: return null
        val minutes = (running.remainingMillis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE
        if (minutes == lastAnnouncedMinutes) return null
        lastAnnouncedMinutes = minutes
        return minutes
    }

    /** How long to wait before the shown seconds change; null once no longer running. */
    fun millisUntilNextTick(): Long? {
        val running = state() as? CountdownState.Running ?: return null
        return (running.remainingMillis - 1) % 1_000 + 1
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
