package nl.nulldevelopment.nfcappblocker.data.blocking

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class RoomBlockingRepository @Inject constructor(
    private val dao: ActiveProfileDao,
    private val profiles: ProfileRepository,
    private val clock: Clock,
) : BlockingRepository {

    override fun observeActive(): Flow<List<ActiveBlock>> =
        combine(dao.observe(), profiles.observeAll()) { active, allProfiles ->
            val byId = allProfiles.associateBy { it.id }
            active.mapNotNull { row ->
                byId[row.profileId]?.let { ActiveBlock(it, Instant.ofEpochMilli(row.startedAtEpochMillis)) }
            }
        }

    override suspend fun activate(profileId: Long): ActivateResult = dao.activateExclusively(profileId, clock.millis())

    override suspend fun deactivate(profileId: Long) = dao.delete(profileId)

    override suspend fun deactivateAll() = dao.deleteAll()
}
