package nl.nulldevelopment.nfcappblocker.feature.escape

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.blocking.ScanFeedback
import nl.nulldevelopment.nfcappblocker.blocking.ScanOutcome
import nl.nulldevelopment.nfcappblocker.testing.FakeBlockingRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeProfileRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeSettingsRepository
import nl.nulldevelopment.nfcappblocker.testing.MainDispatcherExtension
import nl.nulldevelopment.nfcappblocker.util.ElapsedRealtime
import nl.nulldevelopment.nfcappblocker.data.history.SessionEnd
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class EscapeViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val scheduler get() = main.dispatcher.scheduler
    private val settings = FakeSettingsRepository()
    private val profiles = FakeProfileRepository()
    private val blocking = FakeBlockingRepository(profiles)
    private val shown = mutableListOf<ScanOutcome>()
    private val feedback = object : ScanFeedback {
        override fun show(outcome: ScanOutcome) {
            shown += outcome
        }
    }

    // Virtual time: the countdown's clock is the test scheduler's.
    private val vm by lazy { EscapeViewModel(blocking, settings, feedback, ElapsedRealtime { scheduler.currentTime }) }

    private suspend fun startBlocking(name: String = "Focus"): Long =
        profiles.create(name, setOf("com.instagram.android")).also { blocking.activate(it) }

    private fun advanceBy(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    private fun running() = vm.state.value as EscapeState.Running

    @Test
    fun `counts down the length from the settings`() = runTest {
        settings.countdownMinutes.value = 2
        startBlocking()

        assertEquals(EscapeState.Running("Focus", remainingSeconds = 120, fractionLeft = 1f, minutesLeft = 2), vm.state.value)
        advanceBy(30_000)
        assertEquals(90, running().remainingSeconds)
        assertEquals(0.75f, running().fractionLeft)
    }

    @Test
    fun `minutes left change only at each minute mark`() = runTest {
        startBlocking()
        vm.state.value

        advanceBy(59_000)
        assertEquals(3, running().minutesLeft)
        advanceBy(1_000)
        assertEquals(2, running().minutesLeft)
    }

    @Test
    fun `when it ends, blocking stops with the same feedback as a tag`() = runTest {
        settings.countdownMinutes.value = 1
        startBlocking()
        vm.state.value

        advanceBy(60_000)

        assertEquals(EscapeState.Closed, vm.state.value)
        assertTrue(blocking.active.value.isEmpty())
        assertEquals("Focus", (shown.single() as ScanOutcome.Stopped).profile.name)
    }

    @Test
    fun `cancelling keeps blocking on`() = runTest {
        startBlocking()
        vm.state.value

        vm.cancel()
        advanceBy(10 * 60_000)

        assertEquals(EscapeState.Closed, vm.state.value)
        assertEquals(1, blocking.active.value.size)
        assertTrue(shown.isEmpty())
    }

    @Test
    fun `closes at once when nothing is blocking`() = runTest {
        assertEquals(EscapeState.Closed, vm.state.value)
    }

    @Test
    fun `closes without stopping anything when a tag stopped blocking meanwhile`() = runTest {
        val focus = startBlocking()
        vm.state.value

        blocking.deactivate(focus, SessionEnd.Tag)
        advanceBy(10 * 60_000)

        assertEquals(EscapeState.Closed, vm.state.value)
        assertTrue(shown.isEmpty())
    }

    @Test
    fun `a switch to another profile closes it, so the new profile keeps blocking`() = runTest {
        startBlocking("Focus")
        vm.state.value

        startBlocking("Evening")
        advanceBy(10 * 60_000)

        assertEquals(EscapeState.Closed, vm.state.value)
        assertEquals(1, blocking.active.value.size)
        assertTrue(shown.isEmpty())
    }
}
