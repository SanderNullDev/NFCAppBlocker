package nl.nulldevelopment.nfcappblocker.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.nulldevelopment.nfcappblocker.data.settings.CountdownLength
import nl.nulldevelopment.nfcappblocker.data.settings.SettingsRepository

class FakeSettingsRepository : SettingsRepository {
    val countdownMinutes = MutableStateFlow(CountdownLength.DEFAULT_MINUTES)

    override fun observeCountdownMinutes(): Flow<Int> = countdownMinutes

    override suspend fun setCountdownMinutes(minutes: Int) {
        countdownMinutes.value = CountdownLength.clamp(minutes)
    }
}
