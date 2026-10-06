package nl.nulldevelopment.nfcappblocker.data.tags

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Runs against real SQLite on a device; instrumented tests stay on JUnit 4 (D-014). */
@RunWith(AndroidJUnit4::class)
class RoomTagRepositoryTest {

    private val start = Instant.parse("2026-10-06T10:00:00Z")
    private val clock = MutableClock(start)
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomTagRepository

    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .build()
        repository = RoomTagRepository(database.tagDao(), clock)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun addStoresAllFieldsWithTheCurrentTime() = runTest {
        val added = repository.add(first, "Desk", "04:1D:42:74:D4:2A:81")

        assertEquals(PairedTag(first, "Desk", "04:1D:42:74:D4:2A:81", start), added)
        assertEquals(added, repository.find(first))
    }

    @Test
    fun hardwareUidIsOptional() = runTest {
        repository.add(first, "Adopted", hardwareUid = null)

        assertNull(repository.find(first)?.hardwareUid)
    }

    @Test
    fun addingTheSameUuidTwiceFailsAndKeepsTheOriginal() = runTest {
        repository.add(first, "Desk", null)

        val error = runCatching { repository.add(first, "Other", null) }.exceptionOrNull()

        assertEquals(TagAlreadyPairedException::class, error!!::class)
        assertEquals("Desk", repository.find(first)?.name)
    }

    @Test
    fun observeAllListsTagsOldestFirstAndFollowsChanges() = runTest {
        repository.observeAll().test {
            assertEquals(emptyList<PairedTag>(), awaitItem())

            repository.add(first, "Desk", null)
            assertEquals(listOf("Desk"), awaitItem().map { it.name })

            clock.now = start.plusSeconds(60)
            repository.add(second, "Bed", null)
            assertEquals(listOf("Desk", "Bed"), awaitItem().map { it.name })

            repository.rename(first, "Office")
            assertEquals(listOf("Office", "Bed"), awaitItem().map { it.name })

            repository.remove(second)
            assertEquals(listOf("Office"), awaitItem().map { it.name })
        }
    }

    @Test
    fun observeEmitsNullOnceATagIsRemoved() = runTest {
        repository.add(first, "Desk", null)

        repository.observe(first).test {
            assertEquals("Desk", awaitItem()?.name)
            repository.remove(first)
            assertNull(awaitItem())
        }
    }

    @Test
    fun findReturnsNullForAnUnknownUuid() = runTest {
        assertNull(repository.find(UUID.randomUUID()))
    }

    @Test
    fun renamingAnUnknownTagDoesNothing() = runTest {
        repository.rename(first, "Ghost")

        assertNull(repository.find(first))
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
}
