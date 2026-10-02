package nl.nulldevelopment.nfcappblocker.hceemulator

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.nulldevelopment.nfcappblocker.hceemulator.data.EmulatorRepository

class EmulatorApplication : Application() {

    lateinit var repository: EmulatorRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Created here rather than lazily so presets start loading before the first APDU arrives.
        repository = EmulatorRepository(this, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
