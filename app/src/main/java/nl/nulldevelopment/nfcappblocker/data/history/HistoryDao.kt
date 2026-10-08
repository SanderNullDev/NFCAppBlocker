package nl.nulldevelopment.nfcappblocker.data.history

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM block_sessions ORDER BY started_at")
    fun observeSessions(): Flow<List<BlockSessionEntity>>

    @Query("SELECT * FROM block_screen_views ORDER BY shown_at")
    fun observeBlockScreenViews(): Flow<List<BlockScreenViewEntity>>

    @Insert
    suspend fun insertBlockScreenView(view: BlockScreenViewEntity)
}
