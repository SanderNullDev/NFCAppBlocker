package nl.nulldevelopment.nfcappblocker.data.history

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** How a blocking session ended (D-044). */
enum class SessionEnd {
    /** A tag of the block list was tapped. */
    Tag,

    /** The countdown finished: unlocked without a tag. */
    Unlock,

    /** A tag of another block list was tapped, which started that one. */
    Switch,

    /** The block list was deleted while blocking. */
    Deleted,
}

data class BlockSession(
    val profileId: Long,
    val startedAt: Instant,
    /** Null while it's running. */
    val endedAt: Instant?,
    val endedBy: SessionEnd?,
)

data class BlockScreenView(val packageName: String, val profileId: Long, val shownAt: Instant)

/**
 * What happened over time, for the numbers on the home screen (D-044). Sessions are recorded
 * by [nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository] as blocking starts and
 * stops; block screens by the accessibility service.
 */
interface UsageHistory {
    fun observeSessions(): Flow<List<BlockSession>>

    fun observeBlockScreenViews(): Flow<List<BlockScreenView>>

    suspend fun recordBlockScreen(packageName: String, profileId: Long)
}
