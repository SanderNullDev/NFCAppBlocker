package nl.nulldevelopment.nfcappblocker.nfc

import android.nfc.Tag
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tags read in reader mode while the main screen is in the foreground. Screens that need a tag
 * (adding, wiping) collect this while they're shown; when nobody collects, scans are dropped.
 */
@Singleton
class NfcTagEvents @Inject constructor() {
    private val _tags = MutableSharedFlow<Tag>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val tags: SharedFlow<Tag> = _tags.asSharedFlow()

    /** Called from the reader-mode callback, on a binder thread. */
    fun onTagDiscovered(tag: Tag) {
        _tags.tryEmit(tag)
    }
}
