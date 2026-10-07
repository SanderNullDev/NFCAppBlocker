package nl.nulldevelopment.nfcappblocker.data.settings

import kotlinx.coroutines.flow.Flow

/** Length of the escape countdown that stops blocking without a tag (D-006). */
object CountdownLength {
    const val DEFAULT_MINUTES = 3
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 20

    fun clamp(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
}

interface SettingsRepository {
    /** Always within [CountdownLength.MIN_MINUTES]..[CountdownLength.MAX_MINUTES]. */
    fun observeCountdownMinutes(): Flow<Int>

    /** Values outside the allowed range are clamped. */
    suspend fun setCountdownMinutes(minutes: Int)
}
