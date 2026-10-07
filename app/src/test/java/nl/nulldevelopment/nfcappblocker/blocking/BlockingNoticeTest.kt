package nl.nulldevelopment.nfcappblocker.blocking

import nl.nulldevelopment.nfcappblocker.data.blocking.ActiveBlock
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class BlockingNoticeTest {

    private val since = Instant.parse("2026-10-07T08:00:00Z")
    private val focus = ActiveBlock(
        Profile(id = 1, name = "Focus", packageNames = setOf("com.zhiliaoapp.musically", "com.instagram.android"), createdAt = Instant.EPOCH),
        startedAt = since,
    )
    private val labels = mapOf("com.instagram.android" to "Instagram", "com.zhiliaoapp.musically" to "TikTok")

    @Test
    fun `nothing while nothing is blocking, whether or not the service is on`() {
        assertNull(blockingNotice(null, serviceEnabled = true, labels))
        assertNull(blockingNotice(null, serviceEnabled = false, labels))
    }

    @Test
    fun `blocking with the service on lists the apps by name`() {
        assertEquals(
            BlockingNotice.Blocking("Focus", since, listOf("Instagram", "TikTok")),
            blockingNotice(focus, serviceEnabled = true, labels),
        )
    }

    @Test
    fun `an app without a label shows its package name`() {
        val notice = blockingNotice(focus, serviceEnabled = true, mapOf("com.instagram.android" to "Instagram"))

        assertEquals(listOf("com.zhiliaoapp.musically", "Instagram"), (notice as BlockingNotice.Blocking).appNames)
    }

    @Test
    fun `blocking with the service off warns that it isn't working`() {
        assertEquals(BlockingNotice.NotWorking("Focus"), blockingNotice(focus, serviceEnabled = false, labels))
    }
}
