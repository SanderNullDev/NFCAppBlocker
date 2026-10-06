package nl.nulldevelopment.nfcappblocker.data.tags

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RoomTagRepository @Inject constructor(
    private val dao: TagDao,
    private val clock: Clock,
) : TagRepository {

    override fun observeAll(): Flow<List<PairedTag>> = dao.observeAll().map { tags -> tags.map { it.toDomain() } }

    override fun observe(uuid: UUID): Flow<PairedTag?> = dao.observe(uuid.toString()).map { it?.toDomain() }

    override suspend fun find(uuid: UUID): PairedTag? = dao.find(uuid.toString())?.toDomain()

    override suspend fun add(uuid: UUID, name: String, hardwareUid: String?): PairedTag {
        if (dao.find(uuid.toString()) != null) throw TagAlreadyPairedException(uuid)
        val entity = TagEntity(uuid.toString(), name, hardwareUid, clock.millis())
        dao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun rename(uuid: UUID, name: String) = dao.rename(uuid.toString(), name)

    override suspend fun setProfile(uuid: UUID, profileId: Long?) = dao.setProfile(uuid.toString(), profileId)

    override suspend fun remove(uuid: UUID) = dao.delete(uuid.toString())

    private fun TagEntity.toDomain() = PairedTag(
        uuid = UUID.fromString(uuid),
        name = name,
        hardwareUid = hardwareUid,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        profileId = profileId,
    )
}
