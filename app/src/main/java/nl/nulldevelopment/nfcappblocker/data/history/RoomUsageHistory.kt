package nl.nulldevelopment.nfcappblocker.data.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class RoomUsageHistory @Inject constructor(
    private val dao: HistoryDao,
    private val clock: Clock,
) : UsageHistory {

    override fun observeSessions(): Flow<List<BlockSession>> = dao.observeSessions().map { rows ->
        rows.map { row ->
            BlockSession(
                profileId = row.profileId,
                startedAt = Instant.ofEpochMilli(row.startedAtEpochMillis),
                endedAt = row.endedAtEpochMillis?.let(Instant::ofEpochMilli),
                endedBy = row.endedBy?.let { name -> SessionEnd.entries.firstOrNull { it.name == name } },
            )
        }
    }

    override fun observeBlockScreenViews(): Flow<List<BlockScreenView>> = dao.observeBlockScreenViews().map { rows ->
        rows.map { BlockScreenView(it.packageName, it.profileId, Instant.ofEpochMilli(it.shownAtEpochMillis)) }
    }

    override suspend fun recordBlockScreen(packageName: String, profileId: Long) =
        dao.insertBlockScreenView(BlockScreenViewEntity(packageName = packageName, profileId = profileId, shownAtEpochMillis = clock.millis()))
}
