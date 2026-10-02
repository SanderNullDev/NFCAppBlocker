package nl.nulldevelopment.nfcappblocker.hceemulator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Teal fallback palette so the emulator is easy to tell apart from the main app on Android 11.
private val LightColors = lightColorScheme(
    primary = Color(0xFF006A6A),
    secondary = Color(0xFF4A6363),
    tertiary = Color(0xFF4B607C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CDADA),
    secondary = Color(0xFFB0CCCB),
    tertiary = Color(0xFFB3C8E8),
)

@Composable
fun HceEmulatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
