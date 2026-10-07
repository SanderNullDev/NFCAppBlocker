package nl.nulldevelopment.nfcappblocker.feature.onboarding

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.nulldevelopment.nfcappblocker.apps.InstalledApp
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.testing.FakeDefaultNameFormats
import nl.nulldevelopment.nfcappblocker.testing.FakeInstalledAppsSource
import nl.nulldevelopment.nfcappblocker.testing.FakeProfileRepository
import nl.nulldevelopment.nfcappblocker.testing.FakeTagRepository
import nl.nulldevelopment.nfcappblocker.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.UUID

class OnboardingAppsViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val desk = UUID.fromString("00000000-0000-0000-0000-00000000000d")
    private val bed = UUID.fromString("00000000-0000-0000-0000-00000000000b")
    private val tags = FakeTagRepository()
    private val profiles = FakeProfileRepository(tags)
    private val apps = FakeInstalledAppsSource(
        listOf(InstalledApp("com.instagram.android", "Instagram"), InstalledApp("com.android.chrome", "Chrome")),
    )
    private val vm by lazy { OnboardingAppsViewModel(apps, profiles, tags, FakeDefaultNameFormats()) }

    private fun pair(vararg uuids: UUID) {
        tags.tags.value = uuids.map { PairedTag(it, it.toString(), null, Instant.EPOCH) }
    }

    @Test
    fun `saving creates a block list with a default name, linked only to the onboarding tag`() = runTest {
        pair(desk, bed)
        vm.setSelected("com.instagram.android", true)
        var saved: Long? = null

        vm.save(desk, profileId = null) { saved = it }

        val profile = profiles.profiles.value.single()
        assertEquals(profile.id, saved)
        assertEquals("Profile 1", profile.name)
        assertEquals(setOf("com.instagram.android"), profile.packageNames)
        assertEquals(profile.id, tags.tags.value.single { it.uuid == desk }.profileId)
        assertNull(tags.tags.value.single { it.uuid == bed }.profileId)
    }

    @Test
    fun `nothing is saved without apps`() = runTest {
        pair(desk)
        var saved: Long? = null

        vm.save(desk, profileId = null) { saved = it }

        assertNull(saved)
        assertEquals(emptyList<Any>(), profiles.profiles.value)
    }

    @Test
    fun `after going back, it starts from the saved apps and updates the same block list`() = runTest {
        pair(desk)
        val focus = profiles.save(null, "Focus", setOf("com.android.chrome"), setOf(desk))
        // The state only updates while collected, as on screen.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.load(focus)
        assertEquals(1, vm.state.value?.selectedCount)

        vm.setSelected("com.instagram.android", true)
        vm.save(desk, focus) {}

        assertEquals(setOf("com.android.chrome", "com.instagram.android"), profiles.profiles.value.single().packageNames)
        assertEquals("Focus", profiles.profiles.value.single().name)
    }
}
