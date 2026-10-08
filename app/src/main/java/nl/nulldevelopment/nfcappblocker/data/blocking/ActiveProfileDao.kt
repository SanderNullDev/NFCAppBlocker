package nl.nulldevelopment.nfcappblocker.data.blocking

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import nl.nulldevelopment.nfcappblocker.data.history.BlockSessionEntity
import nl.nulldevelopment.nfcappblocker.data.history.SessionEnd

@Dao
abstract class ActiveProfileDao {
    @Query("SELECT * FROM active_profiles ORDER BY started_at, profile_id")
    abstract fun observe(): Flow<List<ActiveProfileEntity>>

    @Query("SELECT COUNT(*) FROM profiles WHERE id = :profileId")
    protected abstract suspend fun profileCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM profile_apps WHERE profile_id = :profileId")
    protected abstract suspend fun appCount(profileId: Long): Int

    @Query("DELETE FROM active_profiles")
    protected abstract suspend fun deleteAll()

    @Query("DELETE FROM active_profiles WHERE profile_id = :profileId")
    protected abstract suspend fun delete(profileId: Long)

    @Insert
    protected abstract suspend fun insert(active: ActiveProfileEntity)

    @Insert
    protected abstract suspend fun insertSession(session: BlockSessionEntity)

    @Query("UPDATE block_sessions SET ended_at = :endedAt, ended_by = :endedBy WHERE ended_at IS NULL")
    protected abstract suspend fun endOpenSessions(endedAt: Long, endedBy: String)

    @Query("UPDATE block_sessions SET ended_at = :endedAt, ended_by = :endedBy WHERE profile_id = :profileId AND ended_at IS NULL")
    protected abstract suspend fun endOpenSessionsOf(profileId: Long, endedAt: Long, endedBy: String)

    /**
     * Makes [profileId] the only active profile (D-004), unless it's missing or has no apps. A
     * session that was running ends as a switch, and a new one starts (D-044).
     */
    @Transaction
    open suspend fun activateExclusively(profileId: Long, startedAtEpochMillis: Long): ActivateResult {
        if (profileCount(profileId) == 0) return ActivateResult.ProfileNotFound
        if (appCount(profileId) == 0) return ActivateResult.ProfileHasNoApps
        deleteAll()
        endOpenSessions(startedAtEpochMillis, SessionEnd.Switch.name)
        insert(ActiveProfileEntity(profileId, startedAtEpochMillis))
        insertSession(BlockSessionEntity(profileId = profileId, startedAtEpochMillis = startedAtEpochMillis))
        return ActivateResult.Activated
    }

    /** Stops [profileId] and ends its session with [endedBy]. */
    @Transaction
    open suspend fun deactivate(profileId: Long, endedAtEpochMillis: Long, endedBy: SessionEnd) {
        delete(profileId)
        endOpenSessionsOf(profileId, endedAtEpochMillis, endedBy.name)
    }
}
