package nl.nulldevelopment.nfcappblocker.feature.profiles

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.apps.InstalledApp
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.testing.FakeInstalledAppsSource
import nl.nulldevelopment.nfcappblocker.testing.FakeProfileRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeTagRepository
import nl.nulldevelopment.nfcappblocker.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.UUID

class ProfileEditorViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val instagram = "com.instagram.android"
    private val chrome = "com.android.chrome"
    private val settings = "com.android.settings"

    private val tags = FakeTagRepository()
    private val profiles = FakeProfileRepository(tags)
    private val apps = FakeInstalledAppsSource(
        listOf(
            InstalledApp(instagram, "Instagram"),
            InstalledApp(chrome, "Chrome"),
            InstalledApp(settings, "Settings"),
        ),
    )

    private val desk = UUID.fromString("00000000-0000-0000-0000-00000000000d")
    private val bed = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    private suspend fun setUp(packages: Set<String> = emptySet()): Pair<Long, ProfileEditorViewModel> {
        val id = profiles.create("Focus", packages)
        return id to ProfileEditorViewModel(id, profiles, tags, apps)
    }

    private suspend fun ProfileEditorViewModel.loaded(): ProfileEditorState.Loaded {
        var result: ProfileEditorState.Loaded? = null
        state.test {
            while (result == null) result = awaitItem() as? ProfileEditorState.Loaded
            cancelAndIgnoreRemainingEvents()
        }
        return result!!
    }

    @Test
    fun `shows the profile with its apps sorted and selected`() = runTest {
        val (_, vm) = setUp(setOf(instagram))

        val state = vm.loaded()

        assertEquals("Focus", state.name)
        assertEquals(listOf("Chrome", "Instagram", "Settings"), state.apps.map { it.label })
        assertEquals(listOf(instagram), state.apps.filter { it.selected }.map { it.packageName })
        assertEquals(1, state.selectedCount)
    }

    @Test
    fun `selecting and deselecting apps saves straight away`() = runTest {
        val (id, vm) = setUp()

        vm.setAppSelected(instagram, true)
        vm.setAppSelected(chrome, true)
        vm.setAppSelected(instagram, false)

        assertEquals(setOf(chrome), profiles.profiles.value.single { it.id == id }.packageNames)
    }

    @Test
    fun `a protected app can't be added but can be removed`() = runTest {
        val (id, vm) = setUp(setOf(settings))
        vm.loaded()

        vm.setAppSelected("com.samsung.android.dialer", true)
        assertEquals(setOf(settings), profiles.profiles.value.single { it.id == id }.packageNames)

        vm.setAppSelected(settings, false)
        assertEquals(emptySet<String>(), profiles.profiles.value.single { it.id == id }.packageNames)
    }

    @Test
    fun `search filters the app list`() = runTest {
        val (_, vm) = setUp()

        vm.setQuery("chr")

        assertEquals(listOf("Chrome"), vm.loaded().apps.map { it.label })
    }

    @Test
    fun `lists linked tags and the others with the profile they're in`() = runTest {
        val (id, vm) = setUp()
        val evening = profiles.create("Evening", emptySet())
        tags.tags.value = listOf(
            PairedTag(desk, "Desk", null, Instant.EPOCH, profileId = id),
            PairedTag(bed, "Bed", null, Instant.EPOCH, profileId = evening),
        )

        val state = vm.loaded()

        assertEquals(listOf("Desk"), state.linkedTags.map { it.name })
        assertEquals(listOf(TagChoice(tags.tags.value[1], "Evening")), state.otherTags)
    }

    @Test
    fun `linking moves a tag here and unlinking clears it`() = runTest {
        val (id, vm) = setUp()
        val evening = profiles.create("Evening", emptySet())
        tags.tags.value = listOf(PairedTag(bed, "Bed", null, Instant.EPOCH, profileId = evening))

        vm.linkTag(bed)
        assertEquals(id, tags.find(bed)?.profileId)

        vm.unlinkTag(bed)
        assertEquals(null, tags.find(bed)?.profileId)
    }

    @Test
    fun `a blank name isn't saved`() = runTest {
        val (id, vm) = setUp()

        vm.rename("   ")
        assertEquals("Focus", profiles.profiles.value.single { it.id == id }.name)

        vm.rename("  Deep work ")
        assertEquals("Deep work", profiles.profiles.value.single { it.id == id }.name)
    }

    @Test
    fun `deleting the profile ends in Missing and unlinks its tags`() = runTest {
        val (id, vm) = setUp()
        tags.tags.value = listOf(PairedTag(desk, "Desk", null, Instant.EPOCH, profileId = id))

        vm.state.test {
            while (awaitItem() !is ProfileEditorState.Loaded) Unit
            vm.delete()
            assertEquals(ProfileEditorState.Missing, awaitItem())
        }
        assertTrue(tags.tags.value.all { it.profileId == null })
    }
}
