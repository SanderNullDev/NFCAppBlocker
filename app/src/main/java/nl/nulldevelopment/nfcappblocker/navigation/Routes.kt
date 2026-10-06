package nl.nulldevelopment.nfcappblocker.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object TagListRoute : NavKey

@Serializable
data object AddTagRoute : NavKey

/** [uuid] is the canonical UUID string; NavKeys must be serializable. */
@Serializable
data class TagDetailsRoute(val uuid: String) : NavKey

/** Phase 0 debug screen: scans, the write test and the blocking spike. */
@Serializable
data object TestToolsRoute : NavKey
