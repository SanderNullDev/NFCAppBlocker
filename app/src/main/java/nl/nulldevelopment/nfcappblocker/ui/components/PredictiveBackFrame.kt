package nl.nulldevelopment.nfcappblocker.ui.components

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/** The corner radius a screen reaches as it leaves, e.g. at the end of the back gesture. */
private val LeavingCorner = 28.dp
private val LeavingElevation = 12.dp

/**
 * Gives a screen its own background and rounds its corners as it leaves, so during the back
 * gesture it reads as a card being put away rather than a cut-out shrinking on top of the screen
 * behind it. A shadow that grows with it keeps it apart from that screen, which has the same
 * background.
 */
@Composable
fun PredictiveBackFrame(content: @Composable () -> Unit) {
    val transition = LocalNavAnimatedContentScope.current.transition
    val corner by transition.animateDp(label = "corner") { state -> if (state == EnterExitState.PostExit) LeavingCorner else 0.dp }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                shape = RoundedCornerShape(corner)
                clip = corner > 0.dp
                // Lifts the leaving screen off the one behind it, which has the same background.
                shadowElevation = (corner / LeavingCorner) * LeavingElevation.toPx()
            }
            .background(MaterialTheme.colorScheme.background),
    ) { content() }
}
