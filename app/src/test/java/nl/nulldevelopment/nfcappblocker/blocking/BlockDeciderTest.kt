package nl.nulldevelopment.nfcappblocker.blocking

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlockDeciderTest {

    private val own = "nl.nulldevelopment.nfcappblocker"
    private val launcher = "ginlemon.flowerfree"
    private val instagram = "com.instagram.android"
    private val chrome = "com.android.chrome"
    private val blocked = setOf(instagram, chrome)
    private val decider = BlockDecider(ownPackage = own)

    private fun check(pkg: String?, at: Long, list: Set<String> = blocked) = decider.shouldBlock(pkg, at, list)

    @Test
    fun `blocks an app on the list`() {
        assertTrue(check(instagram, at = 0))
    }

    @Test
    fun `allows an app that isn't on the list, and everything when the list is empty`() {
        assertFalse(check("com.whatsapp", at = 0))
        assertFalse(check(instagram, at = 0, list = emptySet()))
    }

    @Test
    fun `never blocks our own app, even when listed`() {
        assertFalse(check(own, at = 0, list = blocked + own))
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
    fun `a different blocked app is blocked within the window`() {
        assertTrue(check(instagram, at = 0))
        assertTrue(check(chrome, at = 100))
    }

    @Test
    fun `stopping and starting blocking resets the duplicate check`() {
        assertTrue(check(instagram, at = 0))
        assertFalse(check(instagram, at = 100, list = emptySet()))
        assertTrue(check(instagram, at = 200))
    }
}
