package nl.nulldevelopment.nfcappblocker.feature.status

import nl.nulldevelopment.nfcappblocker.ui.components.NfcStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SetupChecklistTest {

    private fun checklist(
        nfc: NfcStatus = NfcStatus.ON,
        service: Boolean = true,
        notifications: Boolean = true,
        profile: Boolean = true,
        tag: Boolean = true,
    ) = setupChecklist(nfc, service, notifications, profile, tag)

    @Test
    fun `gone once everything is done`() {
        assertNull(checklist())
    }

    @Test
    fun `a fresh install shows every step in order, with only what's already done ticked`() {
        val items = checklist(service = false, notifications = false, profile = false, tag = false)

        assertEquals(
            listOf(
                SetupItem(SetupStep.Nfc, true),
                SetupItem(SetupStep.Service, false),
                SetupItem(SetupStep.Notifications, false),
                SetupItem(SetupStep.Profile, false),
                SetupItem(SetupStep.Tag, false),
            ),
            items,
        )
    }

    @Test
    fun `comes back when one step is undone later`() {
        val items = checklist(service = false)!!

        assertEquals(listOf(SetupStep.Service), items.filterNot { it.done }.map { it.step })
    }

    @Test
    fun `NFC counts as done only when it's on`() {
        assertEquals(false, checklist(nfc = NfcStatus.OFF)!!.first().done)
        assertEquals(false, checklist(nfc = NfcStatus.MISSING)!!.first().done)
    }
}
