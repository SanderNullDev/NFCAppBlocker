package nl.nulldevelopment.nfcappblocker.blocking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EscapeCountdownTest {

    private var clock = 50_000L

    private fun countdown(minutes: Int = 3) = EscapeCountdown(minutes, elapsedMillis = { clock })

    @Nested
    inner class Running {
        @Test
        fun `starts with the full length`() {
            val countdown = countdown(3)

            assertEquals(CountdownState.Running(180_000), countdown.state())
        }

        @Test
        fun `counts down as the clock moves`() {
            val countdown = countdown(3)

            clock += 61_500

            assertEquals(CountdownState.Running(118_500), countdown.state())
        }

        @Test
        fun `shown seconds round up`() {
            val countdown = countdown(1)

            clock += 59_001

            assertEquals(1, (countdown.state() as CountdownState.Running).remainingSeconds)
        }

        @Test
        fun `the length is kept within the allowed range`() {
            assertEquals(60_000, countdown(0).durationMillis)
            assertEquals(20 * 60_000L, countdown(99).durationMillis)
        }
    }

    @Nested
    inner class Ending {
        @Test
        fun `finishes when the full time has passed`() {
            val countdown = countdown(3)

            clock += 180_000

            assertEquals(CountdownState.Finished, countdown.state())
        }

        @Test
        fun `cancelling stops it for good`() {
            val countdown = countdown(3)
            countdown.cancel()

            clock += 180_000

            assertEquals(CountdownState.Cancelled, countdown.state())
        }

        @Test
        fun `cancelling after finishing changes nothing`() {
            val countdown = countdown(1)
            clock += 60_000

            countdown.cancel()

            assertEquals(CountdownState.Finished, countdown.state())
        }
    }

    @Nested
    inner class Announcements {
        @Test
        fun `announces the full length at the start, then each minute mark once`() {
            val countdown = countdown(3)
            val announced = mutableListOf<Long>()

            // Ticks once a second, as the screen does.
            repeat(181) {
                countdown.minutesToAnnounce()?.let(announced::add)
                clock += 1_000
            }

            assertEquals(listOf(3L, 2L, 1L), announced)
        }

        @Test
        fun `nothing more once finished or cancelled`() {
            val finished = countdown(1)
            finished.minutesToAnnounce()
            clock += 60_000
            assertNull(finished.minutesToAnnounce())

            val cancelled = countdown(2)
            cancelled.cancel()
            assertNull(cancelled.minutesToAnnounce())
        }
    }

    @Nested
    inner class Ticks {
        @Test
        fun `next tick is when the shown seconds change`() {
            val countdown = countdown(1)

            assertEquals(1_000, countdown.millisUntilNextTick())
            clock += 250
            assertEquals(750, countdown.millisUntilNextTick())
            clock += 59_000
            assertEquals(750, countdown.millisUntilNextTick())
        }

        @Test
        fun `no tick once finished`() {
            val countdown = countdown(1)
            clock += 60_000

            assertNull(countdown.millisUntilNextTick())
        }
    }
}
