package nl.nulldevelopment.nfcappblocker.spike.blocking

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlockDeciderTest {

    private val own = "nl.nulldevelopment.nfcappblocker"
    private val launcher = "com.sec.android.app.launcher"
    private val instagram = "com.instagram.android"
    private val chrome = "com.android.chrome"
    private val blocked = setOf(instagram, chrome)
    private val decider = BlockDecider(ownPackage = own, neverBlock = setOf(launcher))

    private fun check(pkg: String?, at: Long, enabled: Boolean = true, list: Set<String> = blocked) =
        decider.shouldBlock(pkg, at, enabled, list)

    @Test
    fun `blocks an app on the list`() {
        assertTrue(check(instagram, at = 0))
    }

    @Test
    fun `allows an app that isn't on the list`() {
        assertFalse(check("com.whatsapp", at = 0))
    }

    @Test
    fun `allows everything when blocking is off`() {
        assertFalse(check(instagram, at = 0, enabled = false))
    }

    @Test
    fun `never blocks our own app or the launcher, even when listed`() {
        assertFalse(check(own, at = 0, list = blocked + own))
        assertFalse(check(launcher, at = 0, list = blocked + launcher))
    }

    @Test
    fun `ignores events without a package`() {
        assertFalse(check(null, at = 0))
    }

    @Test
    fun `ignores repeats for the same app within the duplicate window`() {
        assertTrue(check(instagram, at = 0))
        assertFalse(check(instagram, at = 500))
        // Our block screen appearing in between doesn't reset the window.
        assertFalse(check(own, at = 600))
        assertFalse(check(instagram, at = 999))
    }

    @Test
    fun `blocks the same app again after the duplicate window`() {
        assertTrue(check(instagram, at = 0))
        assertTrue(check(instagram, at = 1_000))
    }

    @Test
    fun `blocks again right away after going home`() {
        assertTrue(check(instagram, at = 0))
        assertFalse(check(launcher, at = 100))
        assertTrue(check(instagram, at = 200))
    }

    @Test
    fun `blocks again right away after another app was in front`() {
        assertTrue(check(instagram, at = 0))
        assertFalse(check("com.whatsapp", at = 100))
        assertTrue(check(instagram, at = 200))
    }

    @Test
    fun `a different blocked app is blocked within the window`() {
        assertTrue(check(instagram, at = 0))
        assertTrue(check(chrome, at = 100))
    }

    @Test
    fun `turning blocking off and on resets the duplicate check`() {
        assertTrue(check(instagram, at = 0))
        assertFalse(check(instagram, at = 100, enabled = false))
        assertTrue(check(instagram, at = 200))
    }
}
