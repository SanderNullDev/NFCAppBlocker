package nl.nulldevelopment.nfcappblocker.data.profiles

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProfileDao {
    @Transaction
    @Query("SELECT * FROM profiles ORDER BY name COLLATE NOCASE, id")
    abstract fun observeAll(): Flow<List<ProfileWithApps>>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :id")
    abstract fun observe(id: Long): Flow<ProfileWithApps?>

    @Insert
    abstract suspend fun insert(profile: ProfileEntity): Long

    @Query("UPDATE profiles SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM profiles WHERE id = :id")
    protected abstract suspend fun deleteProfile(id: Long)

    @Query("UPDATE block_sessions SET ended_at = :endedAt, ended_by = 'Deleted' WHERE profile_id = :profileId AND ended_at IS NULL")
    protected abstract suspend fun endOpenSessionsOf(profileId: Long, endedAt: Long)

    /** Deletes the profile; a running session ends as deleted (D-044). Blocking stops by cascade. */
    @Transaction
    open suspend fun delete(id: Long, nowEpochMillis: Long) {
        endOpenSessionsOf(id, nowEpochMillis)
        deleteProfile(id)
    }

    @Query("DELETE FROM profile_apps WHERE profile_id = :profileId")
    protected abstract suspend fun deleteApps(profileId: Long)

    @Insert
    protected abstract suspend fun insertApps(apps: List<ProfileAppEntity>)

    @Query("UPDATE tags SET profile_id = NULL WHERE profile_id = :profileId AND uuid NOT IN (:keep)")
    protected abstract suspend fun unlinkTagsExcept(profileId: Long, keep: List<String>)

    @Query("UPDATE tags SET profile_id = :profileId WHERE uuid IN (:uuids)")
    protected abstract suspend fun linkTags(profileId: Long, uuids: List<String>)

    /**
     * Saves a profile as edited in one transaction: name, apps, and exactly [tagUuids] as its
     * tags (moving them from other profiles). Creates the profile when [id] is null.
     * @return the profile's id.
     */
    @Transaction
    open suspend fun save(id: Long?, name: String, createdAtEpochMillis: Long, packageNames: Set<String>, tagUuids: Set<String>): Long {
        val profileId = if (id == null) {
            insert(ProfileEntity(name = name, createdAtEpochMillis = createdAtEpochMillis))
        } else {
            rename(id, name)
            id
        }
        replaceApps(profileId, packageNames)
        unlinkTagsExcept(profileId, tagUuids.toList())
        if (tagUuids.isNotEmpty()) linkTags(profileId, tagUuids.toList())
        return profileId
    }

    /** Replaces the profile's apps in one transaction. */
    @Transaction
    open suspend fun replaceApps(profileId: Long, packageNames: Set<String>) {
        deleteApps(profileId)
        insertApps(packageNames.map { ProfileAppEntity(profileId, it) })
    }
}
