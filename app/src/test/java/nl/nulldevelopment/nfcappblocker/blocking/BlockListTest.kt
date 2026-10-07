package nl.nulldevelopment.nfcappblocker.blocking

import nl.nulldevelopment.nfcappblocker.apps.NeverBlockPolicy
import nl.nulldevelopment.nfcappblocker.data.blocking.ActiveBlock
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class BlockListTest {

    private val policy = NeverBlockPolicy(
        ownPackage = "nl.nulldevelopment.nfcappblocker",
        phonePackages = setOf("com.samsung.android.dialer"),
        settingsPackages = setOf("com.android.settings"),
        launcherPackages = setOf("ginlemon.flowerfree"),
    )

    private fun active(id: Long, name: String, vararg packages: String, startedAt: Long = id) =
        ActiveBlock(Profile(id, name, packages.toSet(), Instant.EPOCH), Instant.ofEpochSecond(startedAt))

    @Test
    fun `nothing is blocked when nothing is active`() {
        assertEquals(emptyMap<String, Profile>(), blockList(emptyList(), policy))
    }

    @Test
    fun `maps each app of the active profile to that profile`() {
        val focus = active(1, "Focus", "com.instagram.android", "com.android.chrome")

        assertEquals(
            mapOf("com.instagram.android" to focus.profile, "com.android.chrome" to focus.profile),
            blockList(listOf(focus), policy),
        )
    }

    @Test
    fun `protected apps are left out even when the profile lists them`() {
        // E.g. an app that became the launcher after it was added to the profile.
        val focus = active(1, "Focus", "com.instagram.android", "ginlemon.flowerfree", "com.google.android.gm")

        assertEquals(setOf("com.instagram.android"), blockList(listOf(focus), policy).keys)
    }

    @Test
    fun `with several active profiles, the oldest one names a shared app`() {
        val older = active(1, "Focus", "com.instagram.android", startedAt = 10)
        val newer = active(2, "Evening", "com.instagram.android", "com.android.chrome", startedAt = 20)

        val list = blockList(listOf(older, newer), policy)

        assertEquals("Focus", list.getValue("com.instagram.android").name)
        assertEquals("Evening", list.getValue("com.android.chrome").name)
    }
}
