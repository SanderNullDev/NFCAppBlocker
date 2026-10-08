package nl.nulldevelopment.nfcappblocker.feature.home

import nl.nulldevelopment.nfcappblocker.ui.components.NfcStatus

/** What blocking with a tag needs, in the order the user best does it. */
enum class SetupStep { Nfc, Service, Notifications, Profile, Tag }

data class SetupItem(val step: SetupStep, val done: Boolean)

/**
 * The setup checklist on the Status tab (Phase 6), or null once every step is done. It comes back
 * when a step is undone later, e.g. the service switched off by a force stop.
 */
fun setupChecklist(
    nfc: NfcStatus,
    serviceEnabled: Boolean,
    notificationsAllowed: Boolean,
    hasProfileWithApps: Boolean,
    hasLinkedTag: Boolean,
): List<SetupItem>? {
    val items = listOf(
        SetupItem(SetupStep.Nfc, nfc == NfcStatus.ON),
        SetupItem(SetupStep.Service, serviceEnabled),
        SetupItem(SetupStep.Notifications, notificationsAllowed),
        SetupItem(SetupStep.Profile, hasProfileWithApps),
        SetupItem(SetupStep.Tag, hasLinkedTag),
    )
    return items.takeIf { list -> list.any { !it.done } }
}
