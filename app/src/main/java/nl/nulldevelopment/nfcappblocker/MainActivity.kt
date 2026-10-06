package nl.nulldevelopment.nfcappblocker

import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nulldevelopment.nfcappblocker.nfc.TagScans
import nl.nulldevelopment.nfcappblocker.ui.scan.ScanDebugScreen
import nl.nulldevelopment.nfcappblocker.ui.scan.WriteControls
import nl.nulldevelopment.nfcappblocker.ui.theme.NFCAppBlockerTheme

class MainActivity : ComponentActivity() {

    private enum class ReaderStatus { ACTIVE, NFC_OFF, NO_NFC }

    private var readerStatus by mutableStateOf(ReaderStatus.ACTIVE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NFCAppBlockerTheme {
                val scans by TagScans.recent.collectAsStateWithLifecycle()
                val writeArmed by TagScans.writeArmed.collectAsStateWithLifecycle()
                val status = stringResource(
                    when (readerStatus) {
                        ReaderStatus.ACTIVE -> R.string.scan_status_reader_mode
                        ReaderStatus.NFC_OFF -> R.string.scan_status_nfc_off
                        ReaderStatus.NO_NFC -> R.string.scan_status_no_nfc
                    },
                )
                ScanDebugScreen(
                    status = status,
                    scans = scans,
                    writeControls = WriteControls(writeArmed, TagScans::armWrite, TagScans::cancelWrite)
                        .takeIf { readerStatus == ReaderStatus.ACTIVE },
                )
            }
        }
    }

    /**
     * While the app is in the foreground, read tags with reader mode instead of tag dispatch,
     * so a scan doesn't relaunch an activity on top of the open app (spike B).
     */
    override fun onResume() {
        super.onResume()
        val adapter = NfcAdapter.getDefaultAdapter(this)
        readerStatus = when {
            adapter == null -> ReaderStatus.NO_NFC
            !adapter.isEnabled -> ReaderStatus.NFC_OFF
            else -> ReaderStatus.ACTIVE
        }
        adapter?.enableReaderMode(this, TagScans::onReaderModeTag, READER_FLAGS, null)
    }

    override fun onPause() {
        NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)
        // An armed write shouldn't wait around to overwrite some tag after the user comes back.
        TagScans.cancelWrite()
        super.onPause()
    }

    private companion object {
        const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
    }
}
