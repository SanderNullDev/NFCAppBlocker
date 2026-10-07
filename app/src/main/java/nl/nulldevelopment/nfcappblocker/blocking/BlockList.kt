package nl.nulldevelopment.nfcappblocker.blocking

import nl.nulldevelopment.nfcappblocker.apps.NeverBlockPolicy
import nl.nulldevelopment.nfcappblocker.data.blocking.ActiveBlock
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile

/**
 * The apps to block right now, each with the profile that blocks it: the active profiles' apps
 * minus everything [policy] protects (D-023), even if a profile lists it.
 */
fun blockList(active: List<ActiveBlock>, policy: NeverBlockPolicy): Map<String, Profile> =
    buildMap {
        // The oldest active profile wins when two list the same app.
        for (block in active) {
            for (packageName in block.profile.packageNames) {
                if (policy.canBlock(packageName) && packageName !in this) put(packageName, block.profile)
            }
        }
    }
