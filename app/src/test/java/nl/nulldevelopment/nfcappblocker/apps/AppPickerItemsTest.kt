package nl.nulldevelopment.nfcappblocker.apps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class AppPickerItemsTest {

    private val policy = NeverBlockPolicy(
        ownPackage = "nl.nulldevelopment.nfcappblocker",
        phonePackages = setOf("com.samsung.android.dialer"),
        settingsPackages = setOf("com.android.settings"),
        launcherPackages = setOf("ginlemon.flowerfree"),
    )
    private val installed = listOf(
        InstalledApp("com.instagram.android", "Instagram"),
        InstalledApp("com.android.chrome", "Chrome"),
        InstalledApp("com.android.settings", "Settings"),
        InstalledApp("fr.example.ecole", "École"),
        InstalledApp("com.zhiliaoapp.musically", "TikTok"),
        InstalledApp("com.google.android.youtube", "youtube"),
    )

    private fun items(selected: Set<String> = emptySet(), query: String = "", pinned: Set<String> = emptySet()) =
        appPickerItems(installed, selected, policy, query, Locale.ENGLISH, pinnedFirst = pinned)

    @Test
    fun `sorts by name ignoring case and accents`() {
        assertEquals(
            listOf("Chrome", "École", "Instagram", "Settings", "TikTok", "youtube"),
            items().map { it.label },
        )
    }

    @Test
    fun `marks selected apps`() {
        val rows = items(selected = setOf("com.instagram.android"))

        assertTrue(rows.single { it.label == "Instagram" }.selected)
        assertFalse(rows.single { it.label == "Chrome" }.selected)
    }

    @Test
    fun `protected apps are listed but not selectable, with their reason`() {
        val settings = items().single { it.packageName == "com.android.settings" }

        assertEquals(NeverBlockReason.SETTINGS, settings.neverBlockReason)
        assertFalse(settings.selectable)
    }

    @Test
    fun `selected apps that are no longer installed stay listed so they can be removed`() {
        val rows = items(selected = setOf("com.example.gone", "com.instagram.android"))

        val gone = rows.single { it.packageName == "com.example.gone" }
        assertEquals("com.example.gone", gone.label)
        assertTrue(gone.selected)
        assertFalse(gone.installed)
        assertEquals(installed.size + 1, rows.size)
    }

    @Test
    fun `search matches the name without case or accents, or the package name`() {
        assertEquals(listOf("École"), items(query = "ecole").map { it.label })
        assertEquals(listOf("Instagram"), items(query = "  INSTA ").map { it.label })
        assertEquals(listOf("TikTok"), items(query = "musically").map { it.label })
    }

    @Test
    fun `an empty search shows everything`() {
        assertEquals(installed.size, items(query = "   ").size)
    }

    @Test
    fun `pinned apps come first, by name, and stay put when the selection changes`() {
        val pinned = setOf("com.zhiliaoapp.musically", "com.android.chrome")

        val rows = items(selected = setOf("com.instagram.android"), pinned = pinned)

        assertEquals(listOf("Chrome", "TikTok", "École", "Instagram", "Settings", "youtube"), rows.map { it.label })
    }
}
