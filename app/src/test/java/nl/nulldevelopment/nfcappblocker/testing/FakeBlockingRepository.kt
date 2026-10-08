package nl.nulldevelopment.nfcappblocker.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import nl.nulldevelopment.nfcappblocker.data.blocking.ActivateResult
import nl.nulldevelopment.nfcappblocker.data.blocking.ActiveBlock
import nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository
import nl.nulldevelopment.nfcappblocker.data.history.BlockScreenView
import nl.nulldevelopment.nfcappblocker.data.history.BlockSession
import nl.nulldevelopment.nfcappblocker.data.history.SessionEnd
import nl.nulldevelopment.nfcappblocker.data.history.UsageHistory
import java.time.Instant

/**
 * In-memory, with the same rules as the database: one active profile, none without apps. Keeps
 * the usage history the way the database does (D-044).
 */
class FakeBlockingRepository(private val profiles: FakeProfileRepository) : BlockingRepository, UsageHistory {
    /** Active profile ids with their start time. */
    val active = MutableStateFlow<List<Pair<Long, Instant>>>(emptyList())
    val sessions = MutableStateFlow<List<BlockSession>>(emptyList())
    val blockScreenViews = MutableStateFlow<List<BlockScreenView>>(emptyList())
    var now: Instant = Instant.EPOCH

    override fun observeActive(): Flow<List<ActiveBlock>> = combine(active, profiles.profiles) { rows, all ->
        rows.mapNotNull { (id, startedAt) -> all.firstOrNull { it.id == id }?.let { ActiveBlock(it, startedAt) } }
    }

    override suspend fun activate(profileId: Long): ActivateResult {
        val profile = profiles.profiles.first().firstOrNull { it.id == profileId } ?: return ActivateResult.ProfileNotFound
        if (profile.packageNames.isEmpty()) return ActivateResult.ProfileHasNoApps
        endOpen(SessionEnd.Switch) { true }
        active.value = listOf(profileId to now)
        sessions.update { it + BlockSession(profileId, now, null, null) }
        return ActivateResult.Activated
    }

    override suspend fun deactivate(profileId: Long, reason: SessionEnd) {
        active.update { rows -> rows.filterNot { it.first == profileId } }
        endOpen(reason) { it.profileId == profileId }
    }

    private fun endOpen(reason: SessionEnd, which: (BlockSession) -> Boolean) = sessions.update { list ->
        list.map { if (it.endedAt == null && which(it)) it.copy(endedAt = now, endedBy = reason) else it }
    }

    override fun observeSessions(): Flow<List<BlockSession>> = sessions

    override fun observeBlockScreenViews(): Flow<List<BlockScreenView>> = blockScreenViews

    override suspend fun recordBlockScreen(packageName: String, profileId: Long) =
        blockScreenViews.update { it + BlockScreenView(packageName, profileId, now) }
}
