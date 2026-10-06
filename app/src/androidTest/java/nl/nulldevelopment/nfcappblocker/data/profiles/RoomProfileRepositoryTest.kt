package nl.nulldevelopment.nfcappblocker.data.profiles

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.data.db.AppDatabase
import nl.nulldevelopment.nfcappblocker.data.tags.RoomTagRepository
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

@RunWith(AndroidJUnit4::class)
class RoomProfileRepositoryTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: AppDatabase
    private lateinit var profiles: RoomProfileRepository
    private lateinit var tags: RoomTagRepository

    private val instagram = "com.instagram.android"
    private val chrome = "com.android.chrome"
    private val youtube = "com.google.android.youtube"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
        profiles = RoomProfileRepository(database.profileDao(), clock)
        tags = RoomTagRepository(database.tagDao(), clock)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun createStoresNameAndApps() = runTest {
        val id = profiles.create("Focus", setOf(instagram, chrome))

        assertEquals(
            Profile(id, "Focus", setOf(instagram, chrome), clock.instant()),
            profiles.observe(id).first(),
        )
    }

    @Test
    fun aProfileWithoutAppsIsAllowed() = runTest {
        val id = profiles.create("Draft", emptySet())

        assertEquals(emptySet<String>(), profiles.observe(id).first()?.packageNames)
    }

    @Test
    fun setAppsReplacesTheWholeList() = runTest {
        val id = profiles.create("Focus", setOf(instagram, chrome))

        profiles.setApps(id, setOf(chrome, youtube))

        assertEquals(setOf(chrome, youtube), profiles.observe(id).first()?.packageNames)
    }

    @Test
    fun observeAllSortsByNameIgnoringCaseAndFollowsChanges() = runTest {
        profiles.observeAll().test {
            assertEquals(emptyList<Profile>(), awaitItem())
            profiles.create("work", emptySet())
            assertEquals(listOf("work"), awaitItem().map { it.name })
            val evening = profiles.create("Evening", emptySet())
            assertEquals(listOf("Evening", "work"), awaitItem().map { it.name })
            profiles.rename(evening, "zzz")
            assertEquals(listOf("work", "zzz"), awaitItem().map { it.name })
        }
    }

    @Test
    fun deletingAProfileKeepsItsTagsButUnlinksThem() = runTest {
        val id = profiles.create("Focus", setOf(instagram))
        val uuid = UUID.randomUUID()
        tags.add(uuid, "Desk", null)
        tags.setProfile(uuid, id)
        assertEquals(id, tags.find(uuid)?.profileId)

        profiles.delete(id)

        assertNull(profiles.observe(id).first())
        assertEquals("Desk", tags.find(uuid)?.name)
        assertNull(tags.find(uuid)?.profileId)
    }

    @Test
    fun aTagMovesBetweenProfilesAndCanBeUnlinked() = runTest {
        val focus = profiles.create("Focus", emptySet())
        val evening = profiles.create("Evening", emptySet())
        val uuid = UUID.randomUUID()
        tags.add(uuid, "Desk", null)

        tags.setProfile(uuid, focus)
        tags.setProfile(uuid, evening)
        assertEquals(evening, tags.find(uuid)?.profileId)

        tags.setProfile(uuid, null)
        assertNull(tags.find(uuid)?.profileId)
    }
}
