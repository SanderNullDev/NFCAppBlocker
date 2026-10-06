package nl.nulldevelopment.nfcappblocker.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the package visibility setup (`<queries>`) on a real device. */
@RunWith(AndroidJUnit4::class)
class AndroidInstalledAppsSourceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = AndroidInstalledAppsSource(context, Dispatchers.IO)

    @Test
    fun listsLauncherAppsIncludingThisOneWithLabels() = runTest {
        val apps = source.launcherApps()

        assertTrue("expected more than a handful of apps, got ${apps.size}", apps.size > 5)
        assertTrue(apps.any { it.packageName == context.packageName })
        assertEquals(apps.size, apps.distinctBy { it.packageName }.size)
        assertTrue(apps.all { it.label.isNotBlank() })
    }

    @Test
    fun protectsThisAppTheCurrentLauncherAndThePhoneApp() = runTest {
        val policy = source.neverBlockPolicy()
        val home = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage

        assertEquals(NeverBlockReason.THIS_APP, policy.reasonFor(context.packageName))
        assertEquals(NeverBlockReason.SETTINGS, policy.reasonFor("com.android.settings"))
        if (home != null && home != "android") assertEquals(NeverBlockReason.LAUNCHER, policy.reasonFor(home))
        if (dialer != null) assertEquals(NeverBlockReason.PHONE, policy.reasonFor(dialer))
    }
}
