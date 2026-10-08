package nl.nulldevelopment.nfcappblocker.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The one home screen (D-043); everything else opens on top of it. */
@Serializable
data object HomeRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

/** [profileId] is null for a new block list, which is only created when saved (D-025). */
@Serializable
data class ProfileEditorRoute(val profileId: Long?) : NavKey

/** All tags, also unlinked ones; reached from Settings (D-043). */
@Serializable
data object TagListRoute : NavKey

@Serializable
data object AddTagRoute : NavKey

/** [uuid] is the canonical UUID string; NavKeys must be serializable. */
@Serializable
data class TagDetailsRoute(val uuid: String) : NavKey
