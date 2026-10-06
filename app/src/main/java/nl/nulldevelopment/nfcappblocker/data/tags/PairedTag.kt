package nl.nulldevelopment.nfcappblocker.data.tags

import java.time.Instant
import java.util.UUID

/**
 * A tag this app has written or adopted (D-008). Identified by the UUID on the tag; the
 * hardware UID is kept for a possible strict mode later.
 */
data class PairedTag(
    val uuid: UUID,
    val name: String,
    val hardwareUid: String?,
    val createdAt: Instant,
    /** The profile this tag controls (D-003); null while unlinked. */
    val profileId: Long? = null,
)
