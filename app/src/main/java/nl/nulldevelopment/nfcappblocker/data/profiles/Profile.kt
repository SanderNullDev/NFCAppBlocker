package nl.nulldevelopment.nfcappblocker.data.profiles

import java.time.Instant

/** A named set of apps to block (Phase 2). Tags link to a profile; see PairedTag.profileId. */
data class Profile(
    val id: Long,
    val name: String,
    val packageNames: Set<String>,
    val createdAt: Instant,
)
