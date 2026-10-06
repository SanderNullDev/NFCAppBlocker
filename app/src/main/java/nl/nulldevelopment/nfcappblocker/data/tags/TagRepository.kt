package nl.nulldevelopment.nfcappblocker.data.tags

import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface TagRepository {
    /** All paired tags, oldest first. */
    fun observeAll(): Flow<List<PairedTag>>

    fun observe(uuid: UUID): Flow<PairedTag?>

    suspend fun find(uuid: UUID): PairedTag?

    /** @throws TagAlreadyPairedException when a tag with this UUID is already stored. */
    suspend fun add(uuid: UUID, name: String, hardwareUid: String?): PairedTag

    suspend fun rename(uuid: UUID, name: String)

    /** Links the tag to a profile, moving it from any other profile, or unlinks it with null. */
    suspend fun setProfile(uuid: UUID, profileId: Long?)

    suspend fun remove(uuid: UUID)
}

class TagAlreadyPairedException(val uuid: UUID) : IllegalStateException("Tag $uuid is already paired")
