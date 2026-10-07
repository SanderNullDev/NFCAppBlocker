package nl.nulldevelopment.nfcappblocker.blocking

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether the user has the blocker's accessibility service switched on (D-001). */
interface BlockerServiceStatus {
    fun isEnabled(): Boolean
}

class AndroidBlockerServiceStatus @Inject constructor(@ApplicationContext private val context: Context) : BlockerServiceStatus {
    override fun isEnabled(): Boolean {
        val ours = ComponentName(context, BlockerAccessibilityService::class.java)
        return context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == ours }
    }
}
