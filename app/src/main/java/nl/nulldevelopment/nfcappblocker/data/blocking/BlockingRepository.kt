package nl.nulldevelopment.nfcappblocker.data.blocking

import kotlinx.coroutines.flow.Flow
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile
import java.time.Instant

/** A profile that is blocking, and since when. */
data class ActiveBlock(val profile: Profile, val startedAt: Instant)

enum class ActivateResult { Activated, ProfileNotFound, ProfileHasNoApps }

interface BlockingRepository {
    /** What's blocking now, oldest first; empty when nothing is. Follows edits to the profiles. */
    fun observeActive(): Flow<List<ActiveBlock>>

    /** Starts blocking with [profileId], replacing any other active profile (D-004). */
    suspend fun activate(profileId: Long): ActivateResult

    suspend fun deactivate(profileId: Long)

    suspend fun deactivateAll()
}
