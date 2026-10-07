package nl.nulldevelopment.nfcappblocker.blocking

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.nulldevelopment.nfcappblocker.data.blocking.ActivateResult
import nl.nulldevelopment.nfcappblocker.data.blocking.BlockingRepository
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import nl.nulldevelopment.nfcappblocker.nfc.TagContent
import java.util.UUID

/** What scanning a tag did, so the caller can give feedback or open the right screen. */
sealed interface ScanOutcome {
    data class Started(val profile: Profile) : ScanOutcome
    data class Stopped(val profile: Profile) : ScanOutcome

    /** Another profile was active; scanning switches to this tag's profile (Q-1). */
    data class Switched(val from: Profile, val to: Profile) : ScanOutcome

    /** A paired tag without a profile: nothing to toggle yet. */
    data class TagNotLinked(val tag: PairedTag) : ScanOutcome

    /** Its profile has no apps, so there's nothing to block (D-021). Nothing changed. */
    data class ProfileHasNoApps(val profile: Profile) : ScanOutcome

    /** Written by this app but not in the database, e.g. after clearing app data. */
    data class UnknownTagOfOurs(val uuid: UUID) : ScanOutcome

    /** Blank, foreign or unreadable: not a tag of ours. */
    data class NotOurTag(val content: TagContent) : ScanOutcome

    /** The same tag again within the debounce window; probably one tap registering twice. */
    data object Ignored : ScanOutcome
}

/**
 * Scan to toggle (D-005, Phase 4): a linked tag starts its profile, stops it when it's the active
 * one, or switches to it when another profile is active. Scans are handled one at a time.
 *
 * @param elapsedMillis a monotonic clock, e.g. `SystemClock.elapsedRealtime`.
 * @param debounceMillis repeats of the same tag within this window are ignored.
 */
class ScanToggle(
    private val tags: TagRepository,
    private val profiles: ProfileRepository,
    private val blocking: BlockingRepository,
    private val elapsedMillis: () -> Long,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private val mutex = Mutex()
    private var lastKey: String? = null
    private var lastAtMillis = 0L

    suspend fun onScan(content: TagContent, hardwareUid: String): ScanOutcome = mutex.withLock {
        // Our UUID identifies the tag even when the hardware UID is random (HCE, D-011).
        val key = (content as? TagContent.Ours)?.uuid?.toString() ?: "uid:$hardwareUid"
        val now = elapsedMillis()
        if (key == lastKey && now - lastAtMillis < debounceMillis) return@withLock ScanOutcome.Ignored
        lastKey = key
        lastAtMillis = now
        handle(content)
    }

    private suspend fun handle(content: TagContent): ScanOutcome {
        if (content !is TagContent.Ours) return ScanOutcome.NotOurTag(content)
        val tag = tags.find(content.uuid) ?: return ScanOutcome.UnknownTagOfOurs(content.uuid)
        val profileId = tag.profileId ?: return ScanOutcome.TagNotLinked(tag)
        val profile = profiles.observe(profileId).first() ?: return ScanOutcome.TagNotLinked(tag)

        val active = blocking.observeActive().first()
        if (active.any { it.profile.id == profileId }) {
            blocking.deactivate(profileId)
            return ScanOutcome.Stopped(profile)
        }
        return when (blocking.activate(profileId)) {
            ActivateResult.Activated -> active.firstOrNull()
                ?.let { ScanOutcome.Switched(from = it.profile, to = profile) }
                ?: ScanOutcome.Started(profile)
            ActivateResult.ProfileHasNoApps -> ScanOutcome.ProfileHasNoApps(profile)
            ActivateResult.ProfileNotFound -> ScanOutcome.TagNotLinked(tag)
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 2_000L
    }
}
