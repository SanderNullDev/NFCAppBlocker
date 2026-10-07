package nl.nulldevelopment.nfcappblocker.feature.settings

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.testing.FakeBlockingRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeProfileRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeSettingsRepository
import nl.nulldevelopment.nfcappblocker.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SettingsViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val settings = FakeSettingsRepository()
    private val profiles = FakeProfileRepository()
    private val blocking = FakeBlockingRepository(profiles)
    // Created on first use, after the extension has set the test main dispatcher.
    private val vm by lazy { SettingsViewModel(settings, blocking) }

    private suspend fun loaded(): SettingsState.Loaded {
        var result: SettingsState.Loaded? = null
        vm.state.test {
            while (result == null) result = awaitItem() as? SettingsState.Loaded
            cancelAndIgnoreRemainingEvents()
        }
        return result!!
    }

    @Test
    fun `starts at three minutes and unlocked`() = runTest {
        assertEquals(SettingsState.Loaded(countdownMinutes = 3, countdownLocked = false), loaded())
    }

    @Test
    fun `changing it is saved and kept within 1 to 20 minutes`() = runTest {
        vm.setCountdownMinutes(10)
        assertEquals(10, loaded().countdownMinutes)

        vm.setCountdownMinutes(0)
        assertEquals(1, settings.countdownMinutes.value)
        vm.setCountdownMinutes(99)
        assertEquals(20, settings.countdownMinutes.value)
    }

    @Test
    fun `while a profile is blocking it's locked and changes are ignored`() = runTest {
        blocking.activate(profiles.create("Focus", setOf("com.instagram.android")))

        assertEquals(true, loaded().countdownLocked)
        vm.setCountdownMinutes(1)
        assertEquals(3, settings.countdownMinutes.value)
    }
}
