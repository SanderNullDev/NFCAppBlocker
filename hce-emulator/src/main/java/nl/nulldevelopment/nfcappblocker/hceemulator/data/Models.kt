package nl.nulldevelopment.nfcappblocker.hceemulator.data

import java.util.UUID

/** One emulated tag the user can switch to; several presets simulate several paired tags. */
data class TagPreset(val id: String, val label: String, val uuid: UUID)

data class EmulatorSettings(val presets: List<TagPreset>, val activePresetId: String) {
    val activePreset: TagPreset get() = presets.first { it.id == activePresetId }
}

data class LogEntry(
    val id: Long,
    val timeMillis: Long,
    val kind: Kind,
    val summary: String,
    val hex: String? = null,
) {
    enum class Kind { RECEIVED, SENT, EVENT }
}

private val strictUuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/**
 * Parses a UUID only in its canonical 8-4-4-4-12 form. `UUID.fromString` accepts shortened
 * groups such as "1-2-3-4-5", which would surprise someone typing a UUID by hand.
 */
fun parseUuidStrict(text: String): UUID? =
    text.trim().takeIf { strictUuid.matches(it) }?.let(UUID::fromString)
