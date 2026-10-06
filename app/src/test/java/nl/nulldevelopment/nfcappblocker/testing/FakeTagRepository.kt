package nl.nulldevelopment.nfcappblocker.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.data.tags.TagAlreadyPairedException
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import java.time.Instant
import java.util.UUID

class FakeTagRepository(initial: List<PairedTag> = emptyList()) : TagRepository {
    val tags = MutableStateFlow(initial)
    private var nextSecond = 0L

    override fun observeAll(): Flow<List<PairedTag>> = tags
    override fun observe(uuid: UUID): Flow<PairedTag?> = tags.map { list -> list.firstOrNull { it.uuid == uuid } }
    override suspend fun find(uuid: UUID): PairedTag? = tags.value.firstOrNull { it.uuid == uuid }

    override suspend fun add(uuid: UUID, name: String, hardwareUid: String?): PairedTag {
        if (find(uuid) != null) throw TagAlreadyPairedException(uuid)
        val tag = PairedTag(uuid, name, hardwareUid, Instant.ofEpochSecond(nextSecond++))
        tags.update { it + tag }
        return tag
    }

    override suspend fun rename(uuid: UUID, name: String) =
        tags.update { list -> list.map { if (it.uuid == uuid) it.copy(name = name) else it } }

    override suspend fun remove(uuid: UUID) = tags.update { list -> list.filterNot { it.uuid == uuid } }
}
