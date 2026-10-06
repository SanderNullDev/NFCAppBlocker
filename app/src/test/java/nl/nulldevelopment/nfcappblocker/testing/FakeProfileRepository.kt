package nl.nulldevelopment.nfcappblocker.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import java.time.Instant

/** In-memory profiles. Deleting unlinks tags in [tags], like the database's SET NULL rule. */
class FakeProfileRepository(private val tags: FakeTagRepository? = null) : ProfileRepository {
    val profiles = MutableStateFlow<List<Profile>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Profile>> = profiles.map { list -> list.sortedBy { it.name.lowercase() } }
    override fun observe(id: Long): Flow<Profile?> = profiles.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun create(name: String, packageNames: Set<String>): Long {
        val id = nextId++
        profiles.update { it + Profile(id, name, packageNames, Instant.EPOCH) }
        return id
    }

    override suspend fun rename(id: Long, name: String) = edit(id) { it.copy(name = name) }
    override suspend fun setApps(id: Long, packageNames: Set<String>) = edit(id) { it.copy(packageNames = packageNames) }

    override suspend fun delete(id: Long) {
        profiles.update { list -> list.filterNot { it.id == id } }
        tags?.tags?.update { list -> list.map { if (it.profileId == id) it.copy(profileId = null) else it } }
    }

    private fun edit(id: Long, change: (Profile) -> Profile) =
        profiles.update { list -> list.map { if (it.id == id) change(it) else it } }
}
