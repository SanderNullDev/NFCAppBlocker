package nl.nulldevelopment.nfcappblocker.data.profiles

import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface ProfileRepository {
    /** All profiles, by name. */
    fun observeAll(): Flow<List<Profile>>

    fun observe(id: Long): Flow<Profile?>

    /** @return the new profile's id. */
    suspend fun create(name: String, packageNames: Set<String>): Long

    /**
     * Saves an edited profile in one go: name, apps, and exactly [tagUuids] as its tags, moving
     * them from other profiles (D-003). Creates the profile when [id] is null.
     * @return the profile's id.
     */
    suspend fun save(id: Long?, name: String, packageNames: Set<String>, tagUuids: Set<UUID>): Long

    suspend fun rename(id: Long, name: String)

    suspend fun setApps(id: Long, packageNames: Set<String>)

    /** Deletes the profile and its app list. Its tags stay paired, without a profile. */
    suspend fun delete(id: Long)
}
