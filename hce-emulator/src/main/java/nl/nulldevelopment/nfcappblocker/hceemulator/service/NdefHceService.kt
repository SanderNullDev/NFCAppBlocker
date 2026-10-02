package nl.nulldevelopment.nfcappblocker.hceemulator.service

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.hceemulator.EmulatorApplication
import nl.nulldevelopment.nfcappblocker.hceemulator.type4.Type4Tag

/**
 * Answers the reader phone's APDUs as an NFC Forum Type 4 tag. The protocol logic lives in
 * [Type4Tag]; this class only connects it to Android and logs the traffic.
 */
class NdefHceService : HostApduService() {

    private val repository by lazy { (application as EmulatorApplication).repository }
    private val scope = MainScope()
    private val tag by lazy {
        Type4Tag { checkNotNull(repository.currentNdefMessage()) { "Presets not loaded" } }
    }

    // Called on the main thread. Subsequent APDUs wait until this one has been answered.
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray? {
        repository.logReceived(commandApdu)
        if (repository.settings.value != null) return respond(commandApdu)

        // A tap can start the process cold, before the presets have loaded from disk.
        // Answer asynchronously once they're available instead of blocking the main thread.
        scope.launch {
            repository.awaitSettings()
            sendResponseApdu(respond(commandApdu))
        }
        return null
    }

    override fun onDeactivated(reason: Int) {
        tag.reset()
        val cause = when (reason) {
            DEACTIVATION_LINK_LOSS -> "link lost"
            DEACTIVATION_DESELECTED -> "another AID selected"
            else -> "reason $reason"
        }
        repository.logEvent("Deactivated: $cause")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun respond(command: ByteArray): ByteArray =
        tag.process(command).also(repository::logSent)
}
