package nl.nulldevelopment.nfcappblocker.data.tags

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY created_at, uuid")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE uuid = :uuid")
    fun observe(uuid: String): Flow<TagEntity?>

    @Query("SELECT * FROM tags WHERE uuid = :uuid")
    suspend fun find(uuid: String): TagEntity?

    /** Fails on a duplicate UUID; the default conflict strategy is ABORT. */
    @Insert
    suspend fun insert(tag: TagEntity)

    @Query("UPDATE tags SET name = :name WHERE uuid = :uuid")
    suspend fun rename(uuid: String, name: String)

    @Query("DELETE FROM tags WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}
