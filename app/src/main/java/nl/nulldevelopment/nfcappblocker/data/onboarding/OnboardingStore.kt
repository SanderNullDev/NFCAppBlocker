package nl.nulldevelopment.nfcappblocker.data.onboarding

import kotlinx.coroutines.flow.Flow

/** The first-run onboarding steps, in order (D-037). */
enum class OnboardingStep { Welcome, Service, Tag, Notifications, Apps, TryIt }

sealed interface OnboardingProgress {
    /** Never started: a fresh install, or an install from before onboarding existed. */
    data object NotStarted : OnboardingProgress

    data class InProgress(val step: OnboardingStep) : OnboardingProgress

    data object Done : OnboardingProgress
}

/** Saved, so leaving onboarding halfway resumes at the same step. */
interface OnboardingStore {
    fun observe(): Flow<OnboardingProgress>

    suspend fun setStep(step: OnboardingStep)

    suspend fun finish()

    /** Starts over at the first step, also when the app is already set up. */
    suspend fun reset()
}
