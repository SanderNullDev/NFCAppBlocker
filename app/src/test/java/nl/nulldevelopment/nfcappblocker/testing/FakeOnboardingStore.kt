package nl.nulldevelopment.nfcappblocker.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingProgress
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStep
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStore

class FakeOnboardingStore(initial: OnboardingProgress = OnboardingProgress.NotStarted) : OnboardingStore {
    val progress = MutableStateFlow(initial)

    override fun observe(): Flow<OnboardingProgress> = progress

    override suspend fun setStep(step: OnboardingStep) {
        progress.value = OnboardingProgress.InProgress(step)
    }

    override suspend fun finish() {
        progress.value = OnboardingProgress.Done
    }

    override suspend fun reset() = setStep(OnboardingStep.Welcome)
}
