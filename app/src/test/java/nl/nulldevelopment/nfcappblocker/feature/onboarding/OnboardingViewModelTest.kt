package nl.nulldevelopment.nfcappblocker.feature.onboarding

import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.blocking.BlockerServiceStatus
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingProgress
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStep
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.testing.FakeOnboardingStore
import nl.nulldevelopment.nfcappblocker.testing.FakeProfileRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeTagRepository
import nl.nulldevelopment.nfcappblocker.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.UUID

class OnboardingViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val tags = FakeTagRepository()
    private val profiles = FakeProfileRepository(tags)
    private var serviceOn = false
    private val service = object : BlockerServiceStatus {
        override fun isEnabled() = serviceOn
    }

    private fun viewModel(store: FakeOnboardingStore = FakeOnboardingStore()) = OnboardingViewModel(store, service, profiles, tags)

    private suspend fun linkTagToProfileWithApps() {
        val focus = profiles.create("Focus", setOf("com.instagram.android"))
        tags.tags.value = listOf(PairedTag(UUID.randomUUID(), "Desk", null, Instant.EPOCH, profileId = focus))
    }

    private fun showing(step: OnboardingStep, serviceEnabled: Boolean = false) = OnboardingState.Showing(
        step = step,
        index = OnboardingViewModel.STEPS.indexOf(step),
        total = OnboardingViewModel.STEPS.size,
        serviceEnabled = serviceEnabled,
    )

    @Test
    fun `a fresh install starts at the welcome step`() = runTest {
        assertEquals(showing(OnboardingStep.Welcome), viewModel().state.value)
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

        assertEquals(showing(OnboardingStep.Welcome), viewModel().state.value)
    }

    @Test
    fun `resumes at the saved step`() = runTest {
        val vm = viewModel(FakeOnboardingStore(OnboardingProgress.InProgress(OnboardingStep.Service)))

        assertEquals(showing(OnboardingStep.Service), vm.state.value)
    }

    @Test
    fun `a reset shows onboarding again even when set up`() = runTest {
        serviceOn = true
        linkTagToProfileWithApps()
        val store = FakeOnboardingStore(OnboardingProgress.Done)
        val vm = viewModel(store)

        store.reset()

        assertEquals(showing(OnboardingStep.Welcome, serviceEnabled = true), vm.state.value)
    }

    @Test
    fun `next and back move between steps, and next on the last step finishes`() = runTest {
        val store = FakeOnboardingStore()
        val vm = viewModel(store)

        vm.next()
        assertEquals(OnboardingStep.Service, (vm.state.value as OnboardingState.Showing).step)
        vm.back()
        assertEquals(OnboardingStep.Welcome, (vm.state.value as OnboardingState.Showing).step)

        repeat(OnboardingViewModel.STEPS.size) { vm.next() }
        assertEquals(OnboardingProgress.Done, store.progress.value)
        assertEquals(OnboardingState.Hidden, vm.state.value)
    }

    @Test
    fun `the service state is re-read on refresh`() = runTest {
        val vm = viewModel(FakeOnboardingStore(OnboardingProgress.InProgress(OnboardingStep.Service)))

        serviceOn = true
        vm.refresh()

        assertEquals(showing(OnboardingStep.Service, serviceEnabled = true), vm.state.value)
    }
}
