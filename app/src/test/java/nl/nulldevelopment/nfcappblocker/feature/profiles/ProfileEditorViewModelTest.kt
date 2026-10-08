package nl.nulldevelopment.nfcappblocker.feature.profiles

import app.cash.turbine.test
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.apps.InstalledApp
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.feature.pairing.PairingState
import nl.nulldevelopment.nfcappblocker.nfc.NfcTagEvents
import nl.nulldevelopment.nfcappblocker.nfc.ScannedTag
import nl.nulldevelopment.nfcappblocker.nfc.TagContent
import nl.nulldevelopment.nfcappblocker.testing.FakeNdefTarget
import nl.nulldevelopment.nfcappblocker.testing.FakeBlockingRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeDefaultNameFormats
import nl.nulldevelopment.nfcappblocker.testing.FakeInstalledAppsSource
import nl.nulldevelopment.nfcappblocker.testing.FakeProfileRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeTagRepository
import nl.nulldevelopment.nfcappblocker.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
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
        listOf(InstalledApp(instagram, "Instagram"), InstalledApp(chrome, "Chrome"), InstalledApp(settings, "Settings")),
    )

    private val desk = UUID.fromString("00000000-0000-0000-0000-00000000000d")
    private val bed = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    private val tagEvents = NfcTagEvents()
    private val blocking = FakeBlockingRepository(profiles)

    private fun editor(profileId: Long?) = ProfileEditorViewModel(
        profileId, profiles, tags, apps, FakeDefaultNameFormats(), tagEvents, blocking, UnconfinedTestDispatcher(),
    )

    private fun tap(content: TagContent, uid: String = "04:AA") =
        tagEvents.onSimulatedScan(ScannedTag(uid, content, isWritable = true, target = FakeNdefTarget()))

    private suspend fun ProfileEditorViewModel.loaded(): ProfileEditorState.Loaded {
        var result: ProfileEditorState.Loaded? = null
        state.test {
            while (result == null) result = awaitItem() as? ProfileEditorState.Loaded
            cancelAndIgnoreRemainingEvents()
        }
        return result!!
    }

    @Nested
    inner class NewProfile {
        @Test
        fun `nothing is stored until save`() = runTest {
            val vm = editor(null)
            val state = vm.loaded()

            assertTrue(state.isNew)
            assertEquals("Profile 1", state.name)
            vm.setAppSelected(instagram, true)

            assertTrue(profiles.profiles.value.isEmpty())
        }

        @Test
        fun `an untouched new profile has no changes, so leaving doesn't ask`() = runTest {
            val vm = editor(null)

            assertFalse(vm.loaded().hasChanges)
            vm.setAppSelected(instagram, true)
            assertTrue(vm.loaded().hasChanges)
        }

        @Test
        fun `the default name skips names in use`() = runTest {
            profiles.create("Profile 1", emptySet())

            assertEquals("Profile 2", editor(null).loaded().name)
        }

        @Test
        fun `save creates it with its apps and tags, then closes`() = runTest {
            tags.tags.value = listOf(PairedTag(desk, "Desk", null, Instant.EPOCH))
            val vm = editor(null)
            vm.loaded()

            vm.setName(" Focus ")
            vm.setAppSelected(instagram, true)
            vm.linkTag(desk)
            vm.save()

            val saved = profiles.profiles.value.single()
            assertEquals("Focus", saved.name)
            assertEquals(setOf(instagram), saved.packageNames)
            assertEquals(saved.id, tags.find(desk)?.profileId)
            assertTrue(vm.closed.value)
        }

        @Test
        fun `a new profile can be saved straight away, but not with a blank name`() = runTest {
            val vm = editor(null)
            assertTrue(vm.loaded().canSave)

            vm.setName("   ")
            assertFalse(vm.loaded().canSave)
            vm.save()
            assertTrue(profiles.profiles.value.isEmpty())
        }
    }

    @Nested
    inner class ExistingProfile {
        @Test
        fun `loads name, apps and linked tags without changes`() = runTest {
            val id = profiles.create("Focus", setOf(instagram))
            tags.tags.value = listOf(PairedTag(desk, "Desk", null, Instant.EPOCH, profileId = id))

            val state = editor(id).loaded()

            assertEquals("Focus", state.name)
            // The saved app first, then the rest by name.
            assertEquals(listOf("Instagram", "Chrome", "Settings"), state.apps.map { it.label })
            assertEquals(1, state.selectedCount)
            assertEquals(listOf("Desk"), state.linkedTags.map { it.name })
            assertFalse(state.hasChanges)
            assertFalse(state.canSave)
        }

        @Test
        fun `edits stay in the draft until save`() = runTest {
            val id = profiles.create("Focus", setOf(instagram))
            val vm = editor(id)
            vm.loaded()

            vm.setAppSelected(chrome, true)
            vm.setName("Deep work")
            val state = vm.loaded()

            assertTrue(state.hasChanges)
            assertEquals(2, state.selectedCount)
            assertEquals("Focus", profiles.profiles.value.single().name)
            assertEquals(setOf(instagram), profiles.profiles.value.single().packageNames)

            vm.save()
            assertEquals("Deep work", profiles.profiles.value.single().name)
            assertEquals(setOf(instagram, chrome), profiles.profiles.value.single().packageNames)
        }

        @Test
        fun `undoing an edit means there are no changes again`() = runTest {
            val id = profiles.create("Focus", setOf(instagram))
            val vm = editor(id)
            vm.loaded()

            vm.setAppSelected(chrome, true)
            vm.setAppSelected(chrome, false)
            vm.setName("Focus ")

            assertFalse(vm.loaded().hasChanges)
        }

        @Test
        fun `saving moves a tag from another profile and unlinks a removed one`() = runTest {
            val focus = profiles.create("Focus", emptySet())
            val evening = profiles.create("Evening", emptySet())
            tags.tags.value = listOf(
                PairedTag(desk, "Desk", null, Instant.EPOCH, profileId = focus),
                PairedTag(bed, "Bed", null, Instant.EPOCH, profileId = evening),
            )
            val vm = editor(focus)
            val before = vm.loaded()
            assertEquals(listOf(TagChoice(tags.tags.value[1], "Evening")), before.otherTags)

            vm.linkTag(bed)
            vm.unlinkTag(desk)
            vm.save()

            assertEquals(focus, tags.find(bed)?.profileId)
            assertNull(tags.find(desk)?.profileId)
        }

        @Test
        fun `a protected app can't be added but can be removed`() = runTest {
            val id = profiles.create("Focus", setOf(settings))
            val vm = editor(id)
            vm.loaded()

            vm.setAppSelected("com.samsung.android.dialer", true)
            assertEquals(1, vm.loaded().selectedCount)

            vm.setAppSelected(settings, false)
            assertEquals(0, vm.loaded().selectedCount)
        }

        @Test
        fun `says when the profile is blocking right now`() = runTest {
            val id = profiles.create("Focus", setOf(instagram))
            val vm = editor(id)
            assertFalse(vm.loaded().isActive)

            blocking.activate(id)

            assertTrue(vm.loaded().isActive)
        }

        @Test
        fun `search filters the app list`() = runTest {
            val vm = editor(profiles.create("Focus", emptySet()))

            vm.setQuery("chr")

            assertEquals(listOf("Chrome"), vm.loaded().apps.map { it.label })
        }

        @Test
        fun `delete removes it at once, unlinks its tags and closes`() = runTest {
            val id = profiles.create("Focus", emptySet())
            tags.tags.value = listOf(PairedTag(desk, "Desk", null, Instant.EPOCH, profileId = id))
            val vm = editor(id)
            vm.loaded()

            vm.delete()

            assertTrue(profiles.profiles.value.isEmpty())
            assertNull(tags.find(desk)?.profileId)
            assertTrue(vm.closed.value)
        }

        @Test
        fun `an unknown profile id ends in Missing`() = runTest {
            editor(42).state.test {
                var s = awaitItem()
                while (s == ProfileEditorState.Loading) s = awaitItem()
                assertEquals(ProfileEditorState.Missing, s)
            }
        }
    }

    @Nested
    inner class ScanATag {
        @Test
        fun `a paired tag without a profile is linked in the draft and scanning stops`() = runTest {
            tags.tags.value = listOf(PairedTag(desk, "Desk", null, Instant.EPOCH))
            val vm = editor(profiles.create("Focus", emptySet()))
            vm.loaded()

            vm.setResumed(true)
            vm.startScan()
            tap(TagContent.Ours(desk))

            assertNull(vm.scan.value)
            assertEquals(listOf("Desk"), vm.loaded().linkedTags.map { it.name })
            assertNull(tags.find(desk)?.profileId, "only linked when the profile is saved")
        }

        @Test
        fun `a tag already in this profile is reported and scanning continues`() = runTest {
            val id = profiles.create("Focus", emptySet())
            tags.tags.value = listOf(PairedTag(desk, "Desk", null, Instant.EPOCH, profileId = id))
            val vm = editor(id)
            vm.loaded()

            vm.setResumed(true)
            vm.startScan()
            tap(TagContent.Ours(desk))

            assertEquals(EditorScan.AlreadyHere("Desk"), vm.scan.value)
        }

        @Test
        fun `a tag of another profile is only moved after confirming`() = runTest {
            val focus = profiles.create("Focus", emptySet())
            val evening = profiles.create("Evening", emptySet())
            tags.tags.value = listOf(PairedTag(bed, "Bed", null, Instant.EPOCH, profileId = evening))
            val vm = editor(focus)
            vm.loaded()

            vm.setResumed(true)
            vm.startScan()
            tap(TagContent.Ours(bed))
            assertEquals(EditorScan.ConfirmMove(tags.tags.value.single(), "Evening"), vm.scan.value)
            assertTrue(vm.loaded().linkedTags.isEmpty())

            vm.confirmMove()
            assertEquals(listOf("Bed"), vm.loaded().linkedTags.map { it.name })
            vm.save()
            assertEquals(focus, tags.find(bed)?.profileId)
        }

        @Test
        fun `a blank tag is paired, named and linked`() = runTest {
            val vm = editor(null)
            vm.loaded()

            vm.setResumed(true)
            vm.startScan()
            tap(TagContent.Empty)
            val naming = vm.scan.value as EditorScan.NameNewTag
            assertEquals("Tag 1", naming.tag.name)

            vm.finishNewTag("Desk")

            assertNull(vm.scan.value)
            assertEquals("Desk", tags.find(naming.tag.uuid)?.name)
            assertEquals(listOf(naming.tag.uuid), vm.loaded().linkedTags.map { it.uuid })
        }

        @Test
        fun `a tag with other content goes through the usual confirmation`() = runTest {
            val vm = editor(null)
            vm.loaded()

            vm.setResumed(true)
            vm.startScan()
            tap(TagContent.Foreign)

            assertEquals(EditorScan.Pairing(PairingState.ConfirmOverwrite(TagContent.Foreign)), vm.scan.value)
        }

        @Test
        fun `after stopping, taps are ignored`() = runTest {
            tags.tags.value = listOf(PairedTag(desk, "Desk", null, Instant.EPOCH))
            val vm = editor(profiles.create("Focus", emptySet()))
            vm.loaded()

            vm.setResumed(true)
            vm.startScan()
            vm.stopScan()
            tap(TagContent.Ours(desk))

            assertNull(vm.scan.value)
            assertTrue(vm.loaded().linkedTags.isEmpty())
        }

        @Test
        fun `while the screen isn't resumed, scans go elsewhere`() = runTest {
            tags.tags.value = listOf(PairedTag(desk, "Desk", null, Instant.EPOCH))
            val vm = editor(profiles.create("Focus", emptySet()))
            vm.loaded()

            vm.startScan()
            tap(TagContent.Ours(desk))

            assertTrue(vm.loaded().linkedTags.isEmpty())
        }
    }
}
