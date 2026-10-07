package nl.nulldevelopment.nfcappblocker.data.blocking

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.data.db.AppDatabase
import nl.nulldevelopment.nfcappblocker.data.profiles.RoomProfileRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RoomBlockingRepositoryTest {

    private val start = Instant.parse("2026-10-07T09:00:00Z")
    private val clock = object : Clock() {
        var now: Instant = start
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }
    private lateinit var database: AppDatabase
    private lateinit var profiles: RoomProfileRepository
    private lateinit var blocking: RoomBlockingRepository

    private val instagram = "com.instagram.android"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
        profiles = RoomProfileRepository(database.profileDao(), clock)
        blocking = RoomBlockingRepository(database.activeProfileDao(), profiles, clock)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun nothingIsActiveAtFirst() = runTest {
        assertEquals(emptyList<ActiveBlock>(), blocking.observeActive().first())
    }

    @Test
    fun activatingRecordsTheProfileAndTheStartTime() = runTest {
        val focus = profiles.create("Focus", setOf(instagram))

        assertEquals(ActivateResult.Activated, blocking.activate(focus))

        val active = blocking.observeActive().first().single()
        assertEquals("Focus", active.profile.name)
        assertEquals(setOf(instagram), active.profile.packageNames)
        assertEquals(start, active.startedAt)
    }

    @Test
    fun activatingAnotherProfileReplacesTheActiveOne() = runTest {
        val focus = profiles.create("Focus", setOf(instagram))
        val evening = profiles.create("Evening", setOf("com.android.chrome"))
        blocking.activate(focus)

        clock.now = start.plusSeconds(60)
        blocking.activate(evening)

        val active = blocking.observeActive().first().single()
        assertEquals("Evening", active.profile.name)
        assertEquals(start.plusSeconds(60), active.startedAt)
    }

    @Test
    fun aProfileWithoutAppsOrAnUnknownOneIsRefusedAndChangesNothing() = runTest {
        val focus = profiles.create("Focus", setOf(instagram))
        val empty = profiles.create("Empty", emptySet())
        blocking.activate(focus)

        assertEquals(ActivateResult.ProfileHasNoApps, blocking.activate(empty))
        assertEquals(ActivateResult.ProfileNotFound, blocking.activate(999))
        assertEquals(listOf("Focus"), blocking.observeActive().first().map { it.profile.name })
    }

    @Test
    fun deactivatingStopsBlocking() = runTest {
        val focus = profiles.create("Focus", setOf(instagram))
        blocking.activate(focus)

        blocking.deactivate(focus)

        assertTrue(blocking.observeActive().first().isEmpty())
    }

    @Test
    fun deletingTheActiveProfileStopsBlocking() = runTest {
        val focus = profiles.create("Focus", setOf(instagram))
        blocking.activate(focus)

        profiles.delete(focus)

        assertTrue(blocking.observeActive().first().isEmpty())
    }

    @Test
    fun editsToTheActiveProfileShowUpStraightAway() = runTest {
        val focus = profiles.create("Focus", setOf(instagram))
        blocking.activate(focus)

        blocking.observeActive().test {
            assertEquals(setOf(instagram), awaitItem().single().profile.packageNames)
            profiles.setApps(focus, setOf(instagram, "com.android.chrome"))
            assertEquals(setOf(instagram, "com.android.chrome"), awaitItem().single().profile.packageNames)
        }
    }
}
