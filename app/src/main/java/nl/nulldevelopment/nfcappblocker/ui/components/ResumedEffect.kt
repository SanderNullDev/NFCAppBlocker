package nl.nulldevelopment.nfcappblocker.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Reports whether this screen is resumed and on screen. A screen in a tab that isn't shown leaves
 * composition, which counts as not resumed.
 */
@Composable
fun ResumedEffect(onChange: (Boolean) -> Unit) {
    LifecycleResumeEffect(Unit) {
        onChange(true)
        onPauseOrDispose { onChange(false) }
    }
}
