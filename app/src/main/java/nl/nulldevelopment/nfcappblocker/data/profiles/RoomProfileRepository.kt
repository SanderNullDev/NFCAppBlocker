package nl.nulldevelopment.nfcappblocker.data.profiles

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RoomProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val clock: Clock,
) : ProfileRepository {

    override fun observeAll(): Flow<List<Profile>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observe(id: Long): Flow<Profile?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun create(name: String, packageNames: Set<String>): Long {
        val id = dao.insert(ProfileEntity(name = name, createdAtEpochMillis = clock.millis()))
        if (packageNames.isNotEmpty()) dao.replaceApps(id, packageNames)
        return id
    }

    override suspend fun save(id: Long?, name: String, packageNames: Set<String>, tagUuids: Set<UUID>): Long =
        dao.save(id, name, clock.millis(), packageNames, tagUuids.mapTo(mutableSetOf()) { it.toString() })

    override suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    override suspend fun setApps(id: Long, packageNames: Set<String>) = dao.replaceApps(id, packageNames)

    override suspend fun delete(id: Long) = dao.delete(id)

    private fun ProfileWithApps.toDomain() = Profile(
        id = profile.id,
        name = profile.name,
        packageNames = apps.mapTo(mutableSetOf()) { it.packageName },
        createdAt = Instant.ofEpochMilli(profile.createdAtEpochMillis),
    )
}
