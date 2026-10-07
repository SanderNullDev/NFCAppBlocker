package nl.nulldevelopment.nfcappblocker.data.blocking

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ActiveProfileDao {
    @Query("SELECT * FROM active_profiles ORDER BY started_at, profile_id")
    abstract fun observe(): Flow<List<ActiveProfileEntity>>

    @Query("SELECT COUNT(*) FROM profiles WHERE id = :profileId")
    protected abstract suspend fun profileCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM profile_apps WHERE profile_id = :profileId")
    protected abstract suspend fun appCount(profileId: Long): Int

    @Query("DELETE FROM active_profiles")
    abstract suspend fun deleteAll()

    @Query("DELETE FROM active_profiles WHERE profile_id = :profileId")
    abstract suspend fun delete(profileId: Long)

    @Insert
    protected abstract suspend fun insert(active: ActiveProfileEntity)

    /** Makes [profileId] the only active profile (D-004), unless it's missing or has no apps. */
    @Transaction
    open suspend fun activateExclusively(profileId: Long, startedAtEpochMillis: Long): ActivateResult {
        if (profileCount(profileId) == 0) return ActivateResult.ProfileNotFound
        if (appCount(profileId) == 0) return ActivateResult.ProfileHasNoApps
        deleteAll()
        insert(ActiveProfileEntity(profileId, startedAtEpochMillis))
        return ActivateResult.Activated
    }
}
