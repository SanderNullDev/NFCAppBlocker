package nl.nulldevelopment.nfcappblocker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = SagePrimary,
    onPrimary = SageOnPrimary,
    primaryContainer = SagePrimaryContainer,
    onPrimaryContainer = SageOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = SageSecondary,
    onSecondary = SageOnSecondary,
    secondaryContainer = SageSecondaryContainer,
    onSecondaryContainer = SageOnSecondaryContainer,
    tertiary = SandTertiary,
    onTertiary = SandOnTertiary,
    tertiaryContainer = SandTertiaryContainer,
    onTertiaryContainer = SandOnTertiaryContainer,
    background = BrokenWhite,
    onBackground = WarmInk,
    surface = BrokenWhite,
    onSurface = WarmInk,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmOnSurfaceVariant,
    surfaceTint = SagePrimary,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    surfaceBright = BrokenWhite,
    surfaceDim = WarmSurfaceDim,
    surfaceContainerLowest = WarmContainerLowest,
    surfaceContainerLow = WarmContainerLow,
    surfaceContainer = WarmContainer,
    surfaceContainerHigh = WarmContainerHigh,
    surfaceContainerHighest = WarmContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = SagePrimaryDark,
    onPrimary = SageOnPrimaryDark,
    primaryContainer = SagePrimaryContainerDark,
    onPrimaryContainer = SageOnPrimaryContainerDark,
    inversePrimary = SagePrimary,
    secondary = SageSecondaryDark,
    onSecondary = SageOnSecondaryDark,
    secondaryContainer = SageSecondaryContainerDark,
    onSecondaryContainer = SageOnSecondaryContainerDark,
    tertiary = SandTertiaryDark,
    onTertiary = SandOnTertiaryDark,
    tertiaryContainer = SandTertiaryContainerDark,
    onTertiaryContainer = SandOnTertiaryContainerDark,
    background = Charcoal,
    onBackground = WarmInkDark,
    surface = Charcoal,
    onSurface = WarmInkDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onSurfaceVariant = WarmOnSurfaceVariantDark,
    surfaceTint = SagePrimaryDark,
    inverseSurface = WarmInkDark,
    inverseOnSurface = LightInverseSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = WarmOutlineDark,
    outlineVariant = WarmOutlineVariantDark,
    surfaceBright = WarmSurfaceBrightDark,
    surfaceDim = Charcoal,
    surfaceContainerLowest = WarmContainerLowestDark,
    surfaceContainerLow = WarmContainerLowDark,
    surfaceContainer = WarmContainerDark,
    surfaceContainerHigh = WarmContainerHighDark,
    surfaceContainerHighest = WarmContainerHighestDark,
)

/** Fixed colours, not the wallpaper's (dynamic colour is off, D-038); follows dark mode. */
@Composable
fun NFCAppBlockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
