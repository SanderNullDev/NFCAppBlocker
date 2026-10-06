package nl.nulldevelopment.nfcappblocker.testing

import nl.nulldevelopment.nfcappblocker.nfc.NdefIoException
import nl.nulldevelopment.nfcappblocker.nfc.NdefTarget

/** In-memory tag for tests. Configure errors to simulate a tag moving away or failing. */
class FakeNdefTarget(
    override val isWritable: Boolean = true,
    override val maxSize: Int? = 492,
    var stored: ByteArray? = null,
    val connectError: NdefIoException? = null,
    val writeError: NdefIoException? = null,
    val readError: NdefIoException? = null,
    /** Replaces what was written when reading back, to simulate a tag that didn't store it. */
    val readOverride: ByteArray? = null,
) : NdefTarget {
    var connected = false
    var closed = false
    var writeCount = 0

    override fun connect() {
        connectError?.let { throw it }
        connected = true
    }

    override fun write(message: ByteArray) {
        check(connected) { "write before connect" }
        writeError?.let { throw it }
        writeCount++
        stored = message
    }

    override fun read(): ByteArray? {
        readError?.let { throw it }
        return readOverride ?: stored
    }

    override fun close() {
        closed = true
    }
}
