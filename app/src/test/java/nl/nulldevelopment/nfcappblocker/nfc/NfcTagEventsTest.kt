package nl.nulldevelopment.nfcappblocker.nfc

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NfcTagEventsTest {

    private val events = NfcTagEvents()
    private fun tag(uid: String) = ScannedTag(uid, TagContent.Empty, isWritable = true, target = null)

    @Test
    fun `without a claim, scans go to the toggle`() = runTest {
        events.unclaimedScans.test {
            events.onSimulatedScan(tag("04:01"))
            assertEquals("04:01", awaitItem().hardwareUid)
        }
    }

    @Test
    fun `with a claim, scans go to the claiming screen instead`() = runTest {
        events.claim()
        events.scans.test {
            events.onSimulatedScan(tag("04:02"))
            assertEquals("04:02", awaitItem().hardwareUid)
        }
        events.unclaimedScans.test {
            events.onSimulatedScan(tag("04:03"))
            expectNoEvents()
        }
    }

    @Test
    fun `releasing the last claim sends scans to the toggle again`() = runTest {
        events.claim()
        events.claim()
        events.release()
        events.release()
        events.release() // an extra release never goes below zero

        events.unclaimedScans.test {
            events.onSimulatedScan(tag("04:04"))
            assertEquals("04:04", awaitItem().hardwareUid)
        }
        events.claim()
        events.scans.test {
            events.onSimulatedScan(tag("04:05"))
            assertEquals("04:05", awaitItem().hardwareUid)
        }
    }

    @Test
    fun `a ScanClaim only holds while resumed and wanted`() = runTest {
        val claim = ScanClaim(events)

        claim.setWanted(true)
        assertFalse(claim.active)
        claim.setResumed(true)
        assertTrue(claim.active)
        claim.setResumed(false)
        assertFalse(claim.active)
        claim.setResumed(true)
        claim.setWanted(false)
        assertFalse(claim.active)

        // Nothing left claimed: scans go to the toggle.
        events.unclaimedScans.test {
            events.onSimulatedScan(tag("04:06"))
            assertEquals("04:06", awaitItem().hardwareUid)
        }
    }

    @Test
    fun `closing a ScanClaim releases it`() = runTest {
        val claim = ScanClaim(events).apply { setWanted(true); setResumed(true) }

        claim.close()

        events.unclaimedScans.test {
            events.onSimulatedScan(tag("04:07"))
            assertEquals("04:07", awaitItem().hardwareUid)
        }
    }
}
