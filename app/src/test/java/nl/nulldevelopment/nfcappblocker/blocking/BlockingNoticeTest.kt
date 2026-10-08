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

    @Test
    fun `nothing while nothing is blocking, whether or not the service is on`() {
        assertNull(blockingNotice(null, serviceEnabled = true))
        assertNull(blockingNotice(null, serviceEnabled = false))
    }

    @Test
    fun `blocking with the service on counts the apps rather than naming them`() {
        assertEquals(BlockingNotice.Blocking("Focus", since, appCount = 2), blockingNotice(focus, serviceEnabled = true))
    }

    @Test
    fun `blocking with the service off warns that it isn't working`() {
        assertEquals(BlockingNotice.NotWorking("Focus"), blockingNotice(focus, serviceEnabled = false))
    }
}
