package nl.nulldevelopment.nfcappblocker.tagformat

import java.nio.ByteBuffer
import java.util.UUID

/**
 * Payload of the app's MIME record: one version byte followed by the 16 bytes of the UUID,
 * most significant bits first (D-013).
 */
object TagPayload {
    private const val UUID_SIZE = 16
    const val SIZE = 1 + UUID_SIZE

    fun encode(uuid: UUID): ByteArray =
        ByteBuffer.allocate(SIZE)
            .put(TagFormat.PAYLOAD_VERSION)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()

    fun parse(payload: ByteArray): Result {
        if (payload.isEmpty()) return Result.Malformed("Payload is empty")
        val version = payload[0]
        if (version != TagFormat.PAYLOAD_VERSION) return Result.UnsupportedVersion(version.toInt() and 0xFF)
        if (payload.size != SIZE) return Result.Malformed("Expected $SIZE bytes, got ${payload.size}")
        val buffer = ByteBuffer.wrap(payload, 1, UUID_SIZE)
        return Result.Valid(UUID(buffer.long, buffer.long))
    }

    sealed interface Result {
        data class Valid(val uuid: UUID) : Result
        data class UnsupportedVersion(val version: Int) : Result
        data class Malformed(val reason: String) : Result
    }
}
