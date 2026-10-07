package nl.nulldevelopment.nfcappblocker.feature.status

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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.UUID

class StatusViewModelTest {

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

    private fun viewModel() = StatusViewModel(blocking, profiles, tags, apps, service, notifications)

    private suspend fun StatusViewModel.loaded(): StatusState.Loaded {
        var result: StatusState.Loaded? = null
        state.test {
            while (result == null) result = awaitItem() as? StatusState.Loaded
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
}
