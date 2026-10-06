package nl.nulldevelopment.nfcappblocker.data.profiles

import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    /** All profiles, by name. */
    fun observeAll(): Flow<List<Profile>>

    fun observe(id: Long): Flow<Profile?>

    /** @return the new profile's id. */
    suspend fun create(name: String, packageNames: Set<String>): Long

    suspend fun rename(id: Long, name: String)

    suspend fun setApps(id: Long, packageNames: Set<String>)

    /** Deletes the profile and its app list. Its tags stay paired, without a profile. */
    suspend fun delete(id: Long)
}
