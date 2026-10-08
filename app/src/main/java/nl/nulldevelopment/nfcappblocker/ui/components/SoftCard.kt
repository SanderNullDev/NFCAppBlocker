package nl.nulldevelopment.nfcappblocker.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(20.dp)

/** Warm, faint shadow colour: lifts the card without a hard edge (D-045). */
private val ShadowColor = Color(0xFF2B2A27)

/**
 * The app's card (D-045): a Material card lifted off the broken-white background by a wide,
 * faint shadow instead of a border. Shadows barely show in dark mode, so there the card is one
 * step lighter than the background instead.
 */
@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    Card(
        modifier = if (dark) {
            modifier
        } else {
            modifier.shadow(
                elevation = 12.dp,
                shape = CardShape,
                ambientColor = ShadowColor.copy(alpha = 0.04f),
                spotColor = ShadowColor.copy(alpha = 0.08f),
            )
        },
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}
