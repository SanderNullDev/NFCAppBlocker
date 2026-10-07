package nl.nulldevelopment.nfcappblocker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import nl.nulldevelopment.nfcappblocker.blocking.BlockingNotifier
import nl.nulldevelopment.nfcappblocker.blocking.ScanToggleRunner
import javax.inject.Inject

@HiltAndroidApp
class NfcAppBlockerApplication : Application() {

    @Inject lateinit var scanToggleRunner: ScanToggleRunner
    @Inject lateinit var blockingNotifier: BlockingNotifier

    override fun onCreate() {
        super.onCreate()
        scanToggleRunner.start()
        blockingNotifier.start()
    }
}
