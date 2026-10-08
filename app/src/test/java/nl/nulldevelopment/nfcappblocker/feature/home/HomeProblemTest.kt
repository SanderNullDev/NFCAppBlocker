package nl.nulldevelopment.nfcappblocker.feature.home

import nl.nulldevelopment.nfcappblocker.ui.components.NfcStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeProblemTest {

    private fun problem(
        service: Boolean = true,
        nfc: NfcStatus = NfcStatus.ON,
        profile: Boolean = true,
        tag: Boolean = true,
    ) = homeProblem(service, nfc, profile, tag)

    @Test
    fun `none when everything works`() {
        assertNull(problem())
    }

    @Test
    fun `the service being off goes first`() {
        assertEquals(HomeProblem.ServiceOff, problem(service = false, nfc = NfcStatus.OFF, profile = false, tag = false))
    }

    @Test
    fun `then NFC switched off, then a block list, then a tag`() {
        assertEquals(HomeProblem.NfcOff, problem(nfc = NfcStatus.OFF, profile = false))
        assertEquals(HomeProblem.NoBlockList, problem(profile = false, tag = false))
        assertEquals(HomeProblem.NoTag, problem(tag = false))
    }

    @Test
    fun `a phone without NFC isn't a problem on the home screen`() {
        assertNull(problem(nfc = NfcStatus.MISSING))
    }
}
