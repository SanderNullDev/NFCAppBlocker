package nl.nulldevelopment.nfcappblocker.util

/** A monotonic clock in milliseconds that keeps counting in deep sleep (`SystemClock.elapsedRealtime`). */
fun interface ElapsedRealtime {
    fun millis(): Long
}
