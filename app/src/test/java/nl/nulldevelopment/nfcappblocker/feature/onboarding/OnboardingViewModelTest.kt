package nl.nulldevelopment.nfcappblocker.feature.onboarding

import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.apps.InstalledApp
import nl.nulldevelopment.nfcappblocker.blocking.BlockerServiceStatus
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingProgress
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStep
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.feature.status.BlockedApp
import nl.nulldevelopment.nfcappblocker.testing.FakeBlockingRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeInstalledAppsSource
import nl.nulldevelopment.nfcappblocker.testing.FakeOnboardingStore
import nl.nulldevelopment.nfcappblocker.testing.FakeProfileRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeSettingsRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeTagRepository
import nl.nulldevelopment.nfcappblocker.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.UUID

class OnboardingViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val desk = UUID.fromString("00000000-0000-0000-0000-00000000000d")
    private val tags = FakeTagRepository()
    private val profiles = FakeProfileRepository(tags)
    private val blocking = FakeBlockingRepository(profiles)
    private val settings = FakeSettingsRepository()
    private val apps = FakeInstalledAppsSource(
        listOf(InstalledApp("com.instagram.android", "Instagram"), InstalledApp("com.android.chrome", "Chrome")),
    )
    private var serviceOn = false
    private val service = object : BlockerServiceStatus {
        override fun isEnabled() = serviceOn
    }

    private fun viewModel(store: FakeOnboardingStore = FakeOnboardingStore()) =
        OnboardingViewModel(store, service, profiles, tags, blocking, settings, apps)

    private fun OnboardingViewModel.showing() = state.value as OnboardingState.Showing

    private fun pairDesk(profileId: Long? = null) {
        tags.tags.value = listOf(PairedTag(desk, "Desk", null, Instant.EPOCH, profileId = profileId))
    }

    private suspend fun linkTagToProfileWithApps(): Long =
        profiles.create("Focus", setOf("com.instagram.android", "com.android.chrome")).also { pairDesk(it) }

    @Test
    fun `a fresh install starts at the welcome step`() = runTest {
        val state = viewModel().showing()

        assertEquals(OnboardingStep.Welcome, state.step)
        assertEquals(0, state.index)
        assertEquals(7, state.total)
    }

    @Test
    fun `an install that already works skips onboarding`() = runTest {
        serviceOn = true
        linkTagToProfileWithApps()

        assertEquals(OnboardingState.Hidden, viewModel().state.value)
    }

    @Test
    fun `a linked tag without the service on still gets onboarding`() = runTest {
        linkTagToProfileWithApps()

        assertEquals(OnboardingStep.Welcome, viewModel().showing().step)
    }

    @Test
    fun `resumes at the saved step`() = runTest {
        val vm = viewModel(FakeOnboardingStore(OnboardingProgress.InProgress(OnboardingStep.Service)))

        assertEquals(OnboardingStep.Service, vm.showing().step)
    }

    @Test
    fun `a reset shows onboarding again even when set up`() = runTest {
        serviceOn = true
        linkTagToProfileWithApps()
        val store = FakeOnboardingStore(OnboardingProgress.Done)
        val vm = viewModel(store)

        store.reset()

        assertEquals(OnboardingStep.Welcome, vm.showing().step)
    }

    @Test
    fun `next and back move between steps`() = runTest {
        val vm = viewModel()

        vm.next()
        assertEquals(OnboardingStep.Service, vm.showing().step)
        vm.back()
        assertEquals(OnboardingStep.Welcome, vm.showing().step)
    }

    @Test
    fun `can't get past the tag step without a tag, or past the apps step without a block list`() = runTest {
        val store = FakeOnboardingStore(OnboardingProgress.InProgress(OnboardingStep.TryIt))
        val vm = viewModel(store)
        assertEquals(OnboardingStep.Tag, vm.showing().step)

        pairDesk()
        store.setTag(desk)
        assertEquals(OnboardingStep.Apps, vm.showing().step)
    }

    @Test
    fun `choosing a tag names it, saves it and moves on`() = runTest {
        pairDesk()
        val store = FakeOnboardingStore(OnboardingProgress.InProgress(OnboardingStep.Tag))
        val vm = viewModel(store)

        vm.tagChosen(desk, "  Hallway ")

        assertEquals("Hallway", tags.tags.value.single().name)
        assertEquals(OnboardingProgress.InProgress(OnboardingStep.Notifications, tagUuid = desk), store.progress.value)
    }

    @Test
    fun `try it lists the block list's apps by name and shows when it's blocking`() = runTest {
        val focus = linkTagToProfileWithApps()
        val store = FakeOnboardingStore(OnboardingProgress.InProgress(OnboardingStep.TryIt, tagUuid = desk, profileId = focus))
        val vm = viewModel(store)

        assertEquals(listOf(BlockedApp("com.android.chrome", "Chrome"), BlockedApp("com.instagram.android", "Instagram")), vm.showing().blockedApps)
        assertFalse(vm.showing().blocking)

        blocking.activate(focus)
        assertTrue(vm.showing().blocking)
    }

    @Test
    fun `finishing try it ends onboarding`() = runTest {
        val focus = linkTagToProfileWithApps()
        val store = FakeOnboardingStore(OnboardingProgress.InProgress(OnboardingStep.TryIt, tagUuid = desk, profileId = focus))
        val vm = viewModel(store)

        vm.next()

        assertEquals(OnboardingState.Hidden, vm.state.value)
    }

    @Test
    fun `the countdown length can be set, but not while blocking`() = runTest {
        val focus = linkTagToProfileWithApps()
        val vm = viewModel(FakeOnboardingStore(OnboardingProgress.InProgress(OnboardingStep.Stopping, tagUuid = desk, profileId = focus)))

        vm.setCountdownMinutes(5)
        assertEquals(5, vm.showing().countdownMinutes)

        blocking.activate(focus)
        vm.setCountdownMinutes(1)
        assertEquals(5, settings.countdownMinutes.value)
        assertTrue(vm.showing().countdownLocked)
    }

    @Test
    fun `the service state is re-read on refresh`() = runTest {
        val vm = viewModel(FakeOnboardingStore(OnboardingProgress.InProgress(OnboardingStep.Service)))

        serviceOn = true
        vm.refresh()

        assertTrue(vm.showing().serviceEnabled)
    }
}
