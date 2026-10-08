package nl.nulldevelopment.nfcappblocker.feature.home

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.apps.InstalledApp
import nl.nulldevelopment.nfcappblocker.blocking.BlockerServiceStatus
import nl.nulldevelopment.nfcappblocker.blocking.BlockingNotifications
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.testing.FakeBlockingRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeInstalledAppsSource
import nl.nulldevelopment.nfcappblocker.testing.FakeProfileRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeTagRepository
import nl.nulldevelopment.nfcappblocker.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import nl.nulldevelopment.nfcappblocker.data.history.SessionEnd
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class HomeViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val tags = FakeTagRepository()
    private val profiles = FakeProfileRepository(tags)
    private val blocking = FakeBlockingRepository(profiles)
    private val apps = FakeInstalledAppsSource(
        listOf(
            InstalledApp("com.instagram.android", "Instagram"),
            InstalledApp("com.android.chrome", "Chrome"),
            InstalledApp("com.google.android.gm", "Gmail"),
        ),
    )
    private var serviceOn = true
    private var refreshes = 0
    private val service = object : BlockerServiceStatus {
        override fun isEnabled() = serviceOn
    }
    private val notifications = object : BlockingNotifications {
        override fun refresh() {
            refreshes++
        }
    }

    private val now = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun viewModel() = HomeViewModel(blocking, profiles, tags, apps, service, notifications, history = blocking, clock = clock)

    private suspend fun HomeViewModel.loaded(): HomeState.Loaded {
        var result: HomeState.Loaded? = null
        state.test {
            while (result == null) result = awaitItem() as? HomeState.Loaded
            cancelAndIgnoreRemainingEvents()
        }
        return result!!
    }

    @Test
    fun `with nothing set up, there's no profile with apps and no linked tag`() = runTest {
        val state = viewModel().loaded()

        assertNull(state.activeProfileName)
        assertFalse(state.hasProfileWithApps)
        assertFalse(state.hasLinkedTag)
    }

    @Test
    fun `a profile without apps doesn't count`() = runTest {
        profiles.create("Empty", emptySet())

        assertFalse(viewModel().loaded().hasProfileWithApps)
    }

    @Test
    fun `with a profile but no linked tag, only the profile counts`() = runTest {
        profiles.create("Focus", setOf("com.instagram.android"))
        tags.tags.value = listOf(PairedTag(UUID.randomUUID(), "Desk", null, Instant.EPOCH))

        val state = viewModel().loaded()

        assertTrue(state.hasProfileWithApps)
        assertFalse(state.hasLinkedTag)
    }

    @Test
    fun `shows the active profile, since when, and its blockable apps by name`() = runTest {
        // Gmail is always allowed (D-023), so it isn't listed as blocked.
        val focus = profiles.create("Focus", setOf("com.instagram.android", "com.android.chrome", "com.google.android.gm"))
        tags.tags.value = listOf(PairedTag(UUID.randomUUID(), "Desk", null, Instant.EPOCH, profileId = focus))
        blocking.now = Instant.parse("2026-10-07T09:30:00Z")
        blocking.activate(focus)

        val state = viewModel().loaded()

        assertEquals("Focus", state.activeProfileName)
        assertEquals(Instant.parse("2026-10-07T09:30:00Z"), state.since)
        assertEquals(listOf("Chrome", "Instagram"), state.blockedApps.map { it.label })
        assertTrue(state.hasProfileWithApps && state.hasLinkedTag)
    }

    @Test
    fun `the service state is re-read on refresh, which also refreshes the notification`() = runTest {
        val vm = viewModel()
        assertTrue(vm.loaded().serviceEnabled)

        serviceOn = false
        vm.refresh()

        assertFalse(vm.loaded().serviceEnabled)
        assertEquals(1, refreshes)
    }

    @Test
    fun `lists every block list with its app count, tags and whether it's blocking`() = runTest {
        val focus = profiles.create("Focus", setOf("com.instagram.android", "com.android.chrome"))
        val empty = profiles.create("Empty", emptySet())
        tags.tags.value = listOf(
            PairedTag(UUID.randomUUID(), "Desk", null, Instant.EPOCH, profileId = focus),
            PairedTag(UUID.randomUUID(), "bed", null, Instant.EPOCH, profileId = focus),
        )
        blocking.activate(focus)

        assertEquals(
            listOf(
                BlockListSummary(focus, "Focus", 2, listOf("bed", "Desk"), active = true, lastUsed = Instant.EPOCH),
                BlockListSummary(empty, "Empty", 0, emptyList(), active = false, lastUsed = null),
            ),
            viewModel().loaded().blockLists,
        )
    }

    @Test
    fun `numbers come from the history, for the chosen period`() = runTest {
        val focus = profiles.create("Focus", setOf("com.instagram.android"))
        // One session 30 days ago, and two hours this morning that ended by unlocking.
        blocking.now = now.minus(Duration.ofDays(30))
        blocking.activate(focus)
        blocking.deactivate(focus, SessionEnd.Tag)
        blocking.now = now.minus(Duration.ofHours(3))
        blocking.activate(focus)
        blocking.recordBlockScreen("com.instagram.android", focus)
        blocking.now = now.minus(Duration.ofHours(1))
        blocking.deactivate(focus, SessionEnd.Unlock)
        val vm = viewModel()

        assertEquals(StatsPeriod.Week, vm.loaded().period)
        assertEquals(UsageStats(Duration.ofHours(2), 1, 1, 1), vm.loaded().stats)

        vm.setPeriod(StatsPeriod.AllTime)
        assertEquals(2, vm.loaded().stats.timesBlocked)
    }

    @Test
    fun `block lists come active first, then most recently used, then by name`() = runTest {
        val apps = setOf("com.instagram.android")
        val work = profiles.create("Work", apps)
        val evening = profiles.create("Evening", apps)
        val never = profiles.create("Another", apps)
        val focus = profiles.create("Focus", apps)
        blocking.now = now.minus(Duration.ofDays(2))
        blocking.activate(work)
        blocking.now = now.minus(Duration.ofDays(1))
        blocking.activate(evening)
        blocking.now = now.minus(Duration.ofHours(1))
        blocking.activate(focus)

        assertEquals(listOf("Focus", "Evening", "Work", "Another"), viewModel().loaded().blockLists.map { it.name })
        assertEquals(null, viewModel().loaded().blockLists.single { it.id == never }.lastUsed)
    }

    @Test
    fun `the greeting gets the names of tags that start a block list with apps`() = runTest {
        val focus = profiles.create("Focus", setOf("com.instagram.android"))
        val empty = profiles.create("Empty", emptySet())
        tags.tags.value = listOf(
            PairedTag(UUID.randomUUID(), "Desk", null, Instant.EPOCH, profileId = focus),
            PairedTag(UUID.randomUUID(), "Drawer", null, Instant.EPOCH, profileId = empty),
            PairedTag(UUID.randomUUID(), "Loose", null, Instant.EPOCH),
        )

        assertEquals(listOf("Desk"), viewModel().loaded().tagNames)
    }
}
