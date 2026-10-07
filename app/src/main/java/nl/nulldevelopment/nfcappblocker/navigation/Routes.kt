package nl.nulldevelopment.nfcappblocker.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Tab: whether blocking is on, and what's needed to make it work. The start tab. */
@Serializable
data object StatusRoute : NavKey

/** Tab: block profiles. */
@Serializable
data object ProfilesRoute : NavKey

/** [profileId] is null for a new profile, which is only created when saved (D-025). */
@Serializable
data class ProfileEditorRoute(val profileId: Long?) : NavKey

/** Tab: paired tags. */
@Serializable
data object TagListRoute : NavKey

@Serializable
data object AddTagRoute : NavKey

/** [uuid] is the canonical UUID string; NavKeys must be serializable. */
@Serializable
data class TagDetailsRoute(val uuid: String) : NavKey
