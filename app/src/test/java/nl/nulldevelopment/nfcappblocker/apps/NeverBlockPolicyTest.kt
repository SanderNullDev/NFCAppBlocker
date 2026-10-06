package nl.nulldevelopment.nfcappblocker.apps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NeverBlockPolicyTest {

    private val policy = NeverBlockPolicy(
        ownPackage = "nl.nulldevelopment.nfcappblocker",
        phonePackages = setOf("com.samsung.android.dialer"),
        settingsPackages = setOf("com.android.settings"),
        launcherPackages = setOf("ginlemon.flowerfree", "com.sec.android.app.launcher"),
    )

    @Test
    fun `gives each protected app its reason`() {
        assertEquals(NeverBlockReason.THIS_APP, policy.reasonFor("nl.nulldevelopment.nfcappblocker"))
        assertEquals(NeverBlockReason.PHONE, policy.reasonFor("com.samsung.android.dialer"))
        assertEquals(NeverBlockReason.SETTINGS, policy.reasonFor("com.android.settings"))
        assertEquals(NeverBlockReason.LAUNCHER, policy.reasonFor("ginlemon.flowerfree"))
        assertEquals(NeverBlockReason.LAUNCHER, policy.reasonFor("com.sec.android.app.launcher"))
    }

    @Test
    fun `maps and email are always allowed`() {
        assertEquals(NeverBlockReason.ALWAYS_ALLOWED, policy.reasonFor("com.google.android.apps.maps"))
        assertEquals(NeverBlockReason.ALWAYS_ALLOWED, policy.reasonFor("com.google.android.gm"))
        assertEquals(NeverBlockReason.ALWAYS_ALLOWED, policy.reasonFor("com.samsung.android.email.provider"))
    }

    @Test
    fun `other apps can be blocked`() {
        assertNull(policy.reasonFor("com.instagram.android"))
        assertTrue(policy.canBlock("com.android.chrome"))
        assertFalse(policy.canBlock("com.android.settings"))
    }

    @Test
    fun `the most specific reason wins when a package matches several`() {
        val odd = NeverBlockPolicy("a.b", phonePackages = setOf("x.y"), settingsPackages = emptySet(), launcherPackages = setOf("x.y"))

        assertEquals(NeverBlockReason.PHONE, odd.reasonFor("x.y"))
    }
}
