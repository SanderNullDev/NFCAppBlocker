package nl.nulldevelopment.nfcappblocker.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Phase 0 debug screen: scans, the write test and the blocking spike. Replaced in Phase 1. */
@Serializable
data object ScanTestRoute : NavKey
