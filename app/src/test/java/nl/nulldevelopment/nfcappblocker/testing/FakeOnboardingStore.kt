package nl.nulldevelopment.nfcappblocker.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingProgress
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStep
import nl.nulldevelopment.nfcappblocker.data.onboarding.OnboardingStore
import java.util.UUID

class FakeOnboardingStore(initial: OnboardingProgress = OnboardingProgress.NotStarted) : OnboardingStore {
    val progress = MutableStateFlow(initial)

    override fun observe(): Flow<OnboardingProgress> = progress

    override suspend fun setStep(step: OnboardingStep) = progress.update {
        (it as? OnboardingProgress.InProgress)?.copy(step = step) ?: OnboardingProgress.InProgress(step)
    }

    override suspend fun setTag(uuid: UUID) = progress.update { (it as? OnboardingProgress.InProgress)?.copy(tagUuid = uuid) ?: it }

    override suspend fun setProfile(id: Long) = progress.update { (it as? OnboardingProgress.InProgress)?.copy(profileId = id) ?: it }

    override suspend fun finish() {
        progress.value = OnboardingProgress.Done
    }

    override suspend fun reset() {
        progress.value = OnboardingProgress.InProgress(OnboardingStep.Welcome)
    }
}
