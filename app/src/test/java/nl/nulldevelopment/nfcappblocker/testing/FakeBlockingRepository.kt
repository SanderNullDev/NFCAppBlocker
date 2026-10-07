package nl.nulldevelopment.nfcappblocker.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import nl.nulldevelopment.nfcappblocker.data.blocking.ActivateResult
import nl.nulldevelopment.nfcappblocker.data.blocking.ActiveBlock
import nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository
import java.time.Instant

/** In-memory, with the same rules as the database: one active profile, none without apps. */
class FakeBlockingRepository(private val profiles: FakeProfileRepository) : BlockingRepository {
    /** Active profile ids with their start time. */
    val active = MutableStateFlow<List<Pair<Long, Instant>>>(emptyList())
    var now: Instant = Instant.EPOCH

    override fun observeActive(): Flow<List<ActiveBlock>> = combine(active, profiles.profiles) { rows, all ->
        rows.mapNotNull { (id, startedAt) -> all.firstOrNull { it.id == id }?.let { ActiveBlock(it, startedAt) } }
    }

    override suspend fun activate(profileId: Long): ActivateResult {
        val profile = profiles.profiles.first().firstOrNull { it.id == profileId } ?: return ActivateResult.ProfileNotFound
        if (profile.packageNames.isEmpty()) return ActivateResult.ProfileHasNoApps
        active.value = listOf(profileId to now)
        return ActivateResult.Activated
    }

    override suspend fun deactivate(profileId: Long) = active.update { rows -> rows.filterNot { it.first == profileId } }

    override suspend fun deactivateAll() {
        active.value = emptyList()
    }
}
