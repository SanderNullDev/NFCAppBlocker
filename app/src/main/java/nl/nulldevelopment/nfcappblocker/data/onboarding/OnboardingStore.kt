package nl.nulldevelopment.nfcappblocker.data.onboarding

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** The first-run onboarding steps, in order (D-037). */
enum class OnboardingStep { Welcome, Service, Tag, Notifications, Apps, Stopping, TryIt }

sealed interface OnboardingProgress {
    /** Never started: a fresh install, or an install from before onboarding existed. */
    data object NotStarted : OnboardingProgress

    data class InProgress(
        val step: OnboardingStep,
        /** The tag added during onboarding, once there is one. */
        val tagUuid: UUID? = null,
        /** The block list created during onboarding, once there is one. */
        val profileId: Long? = null,
    ) : OnboardingProgress

    data object Done : OnboardingProgress
}

/** Saved, so leaving onboarding halfway resumes at the same step. */
interface OnboardingStore {
    fun observe(): Flow<OnboardingProgress>

    suspend fun setStep(step: OnboardingStep)

    suspend fun setTag(uuid: UUID)

    suspend fun setProfile(id: Long)

    suspend fun finish()

    /** Starts over at the first step, also when the app is already set up. Tags and block lists stay. */
    suspend fun reset()
}
