package nl.nulldevelopment.nfcappblocker.feature.tags

import app.cash.turbine.test
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.nfc.NfcTagEvents
import nl.nulldevelopment.nfcappblocker.testing.FakeProfileRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeTagRepository
import nl.nulldevelopment.nfcappblocker.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.UUID

class TagDetailsViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val uuid = UUID.fromString("00000000-0000-0000-0000-00000000000d")
    private val tags = FakeTagRepository(listOf(PairedTag(uuid, "Desk", null, Instant.EPOCH)))
    private val profiles = FakeProfileRepository(tags)

    private fun viewModel() = TagDetailsViewModel(uuid, tags, profiles, NfcTagEvents(), UnconfinedTestDispatcher())

    private suspend fun TagDetailsViewModel.awaitLoaded(check: (TagDetailsState.Loaded) -> Boolean = { true }) {
        state.test {
            while (true) {
                val s = awaitItem()
                if (s is TagDetailsState.Loaded && check(s)) break
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows the linked profile and all profiles to choose from`() = runTest {
        val focus = profiles.create("Focus", emptySet())
        profiles.create("Evening", emptySet())
        tags.setProfile(uuid, focus)

        viewModel().state.test {
            var s = awaitItem()
            while (s !is TagDetailsState.Loaded) s = awaitItem()
            assertEquals("Focus", s.profile?.name)
            assertEquals(listOf("Evening", "Focus"), s.profiles.map { it.name })
        }
    }

    @Test
    fun `linking, changing and unlinking the profile`() = runTest {
        val focus = profiles.create("Focus", emptySet())
        val evening = profiles.create("Evening", emptySet())
        val vm = viewModel()

        vm.setProfile(focus)
        assertEquals(focus, tags.find(uuid)?.profileId)
        vm.setProfile(evening)
        assertEquals(evening, tags.find(uuid)?.profileId)
        vm.setProfile(null)
        assertNull(tags.find(uuid)?.profileId)
    }

    @Test
    fun `deleting the linked profile leaves the tag unlinked`() = runTest {
        val focus = profiles.create("Focus", emptySet())
        tags.setProfile(uuid, focus)
        val vm = viewModel()

        profiles.delete(focus)

        vm.awaitLoaded { it.profile == null }
        assertNull(tags.find(uuid)?.profileId)
    }
}
