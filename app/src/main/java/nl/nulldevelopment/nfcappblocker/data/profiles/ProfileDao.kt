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
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM profile_apps WHERE profile_id = :profileId")
    protected abstract suspend fun deleteApps(profileId: Long)

    @Insert
    protected abstract suspend fun insertApps(apps: List<ProfileAppEntity>)

    /** Replaces the profile's apps in one transaction. */
    @Transaction
    open suspend fun replaceApps(profileId: Long, packageNames: Set<String>) {
        deleteApps(profileId)
        insertApps(packageNames.map { ProfileAppEntity(profileId, it) })
    }
}
