package nl.nulldevelopment.nfcappblocker.feature.home

import nl.nulldevelopment.nfcappblocker.ui.components.NfcStatus

/** The one problem the home screen shows (D-046), most urgent first. */
enum class HomeProblem {
    /** The accessibility service is off: nothing gets blocked. */
    ServiceOff,

    /** NFC is switched off, so tags can't be read. */
    NfcOff,

    /** No block list has apps yet. */
    NoBlockList,

    /** No tag is linked to a block list yet. */
    NoTag,
}

/**
 * The most urgent problem, or null when blocking can work. Not shown: a phone without NFC
 * hardware (the app can't fix it) and notifications (the app works without them); both are in
 * Settings.
 */
fun homeProblem(serviceEnabled: Boolean, nfc: NfcStatus, hasProfileWithApps: Boolean, hasLinkedTag: Boolean): HomeProblem? = when {
    !serviceEnabled -> HomeProblem.ServiceOff
    nfc == NfcStatus.OFF -> HomeProblem.NfcOff
    !hasProfileWithApps -> HomeProblem.NoBlockList
    !hasLinkedTag -> HomeProblem.NoTag
    else -> null
}
