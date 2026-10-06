package nl.nulldevelopment.nfcappblocker.feature.pairing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import nl.nulldevelopment.nfcappblocker.nfc.ScannedTag
import nl.nulldevelopment.nfcappblocker.nfc.TagContent
import nl.nulldevelopment.nfcappblocker.nfc.TagWriter
import nl.nulldevelopment.nfcappblocker.nfc.WriteResult
import java.util.UUID

sealed interface PairingState {
    data object WaitingForTag : PairingState
    data object Writing : PairingState

    /** The tag holds content that isn't ours; overwriting needs the user's OK. */
    data class ConfirmOverwrite(val content: TagContent) : PairingState

    /** Overwrite confirmed: waiting for the same tag (same hardware UID) to be held again. */
    data class WaitingForSameTag(val otherTagTapped: Boolean = false) : PairingState

    data class AlreadyPaired(val tag: PairedTag) : PairingState

    /** Written by this app but not in the database, e.g. after clearing app data. */
    data class OursNotPaired(val uuid: UUID, val canOverwrite: Boolean) : PairingState

    data class Paired(val tag: PairedTag) : PairingState
    data class Failed(val reason: PairingFailure) : PairingState
}

sealed interface PairingFailure {
    data object ReadOnly : PairingFailure
    data class TooSmall(val capacity: Int, val needed: Int) : PairingFailure
    data object NotNdefCapable : PairingFailure

    /** The tag moved away mid-write and may hold partial content. */
    data object TagLost : PairingFailure

    /** The write call returned, but reading back didn't show our content. */
    data object NotVerified : PairingFailure
    data class Other(val message: String) : PairingFailure
}

/**
 * The "add tag" flow (Phase 1) as a state machine, free of Android classes. Scans and user
 * actions may arrive from different threads; a mutex handles them one at a time.
 *
 * @param defaultName name for a new tag, given a number; the first unused number is chosen.
 * @param ioDispatcher where the blocking tag I/O runs.
 */
class PairingFlow(
    private val repository: TagRepository,
    private val defaultName: (Int) -> String,
    private val ioDispatcher: CoroutineDispatcher,
    private val newUuid: () -> UUID = UUID::randomUUID,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<PairingState>(PairingState.WaitingForTag)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    /** Hardware UID of the tag the user agreed to overwrite. */
    private var confirmedHardwareUid: String? = null
    private var lastScanned: ScannedTag? = null

    /**
     * Handles a tag held against the phone. Dropped while another scan or action is being
     * handled (e.g. during a write), so one tap that registers twice can't act twice.
     */
    suspend fun onTagScanned(tag: ScannedTag) {
        if (!mutex.tryLock()) return
        try {
            handleScan(tag)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun handleScan(tag: ScannedTag) {
        when (val current = _state.value) {
            PairingState.WaitingForTag -> inspect(tag)
            is PairingState.WaitingForSameTag ->
                if (tag.hardwareUid == confirmedHardwareUid) {
                    write(tag)
                } else {
                    _state.value = current.copy(otherTagTapped = true)
                }
            // While asking or showing a result, a new tap starts over with that tag.
            is PairingState.ConfirmOverwrite, is PairingState.AlreadyPaired, is PairingState.OursNotPaired,
            is PairingState.Paired, is PairingState.Failed -> inspect(tag)
            // Unreachable: writing happens while the mutex is held.
            PairingState.Writing -> Unit
        }
    }

    /** From [PairingState.ConfirmOverwrite] or [PairingState.OursNotPaired]. */
    suspend fun confirmOverwrite() = mutex.withLock {
        val state = _state.value
        val allowed = state is PairingState.ConfirmOverwrite ||
            (state is PairingState.OursNotPaired && state.canOverwrite)
        if (!allowed) return@withLock
        confirmedHardwareUid = lastScanned?.hardwareUid
        _state.value = PairingState.WaitingForSameTag()
    }

    /** From [PairingState.OursNotPaired]: store the tag's UUID without writing to it. */
    suspend fun adopt() = mutex.withLock {
        val state = _state.value as? PairingState.OursNotPaired ?: return@withLock
        val tag = repository.add(state.uuid, nextDefaultName(), lastScanned?.hardwareUid)
        _state.value = PairingState.Paired(tag)
    }

    /** Back to waiting for a tag, e.g. after a failure or to pair another one. */
    suspend fun restart() = mutex.withLock {
        confirmedHardwareUid = null
        lastScanned = null
        _state.value = PairingState.WaitingForTag
    }

    private suspend fun inspect(tag: ScannedTag) {
        confirmedHardwareUid = null
        lastScanned = tag
        val content = tag.content
        _state.value = when {
            // Recognising our own tags comes first: even a read-only one can be adopted.
            content is TagContent.Ours -> {
                val known = repository.find(content.uuid)
                if (known != null) {
                    PairingState.AlreadyPaired(known)
                } else {
                    PairingState.OursNotPaired(content.uuid, canOverwrite = tag.isWritable && tag.target != null)
                }
            }
            tag.target == null -> PairingState.Failed(PairingFailure.NotNdefCapable)
            !tag.isWritable -> PairingState.Failed(PairingFailure.ReadOnly)
            content == TagContent.Empty -> return write(tag)
            else -> PairingState.ConfirmOverwrite(content)
        }
    }

    private suspend fun write(tag: ScannedTag) {
        _state.value = PairingState.Writing
        val uuid = newUuid()
        val result = withContext(ioDispatcher) { TagWriter.write(tag.target, uuid) }
        confirmedHardwareUid = null
        _state.value = when (result) {
            is WriteResult.Written -> PairingState.Paired(repository.add(uuid, nextDefaultName(), tag.hardwareUid))
            is WriteResult.WrittenUnverified, is WriteResult.VerificationFailed ->
                PairingState.Failed(PairingFailure.NotVerified)
            WriteResult.ReadOnly -> PairingState.Failed(PairingFailure.ReadOnly)
            is WriteResult.TooSmall -> PairingState.Failed(PairingFailure.TooSmall(result.capacity, result.needed))
            WriteResult.NotNdefCapable -> PairingState.Failed(PairingFailure.NotNdefCapable)
            WriteResult.TagLost -> PairingState.Failed(PairingFailure.TagLost)
            is WriteResult.Failed -> PairingState.Failed(PairingFailure.Other(result.reason))
        }
    }

    private suspend fun nextDefaultName(): String {
        val used = repository.observeAll().first().map { it.name }.toSet()
        return generateSequence(1) { it + 1 }.map(defaultName).first { it !in used }
    }
}
