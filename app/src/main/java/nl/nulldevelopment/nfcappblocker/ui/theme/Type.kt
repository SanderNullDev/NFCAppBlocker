package nl.nulldevelopment.nfcappblocker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import nl.nulldevelopment.nfcappblocker.R

/** Figtree (SIL Open Font License, see third_party/figtree), one variable font file (D-045). */
@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: FontWeight) = Font(
    R.font.figtree,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Figtree = FontFamily(
    figtree(FontWeight.Normal),
    figtree(FontWeight.Medium),
    figtree(FontWeight.SemiBold),
)

private val base = Typography()

private fun TextStyle.figtree(weight: FontWeight? = null) = copy(fontFamily = Figtree, fontWeight = weight ?: fontWeight)

/** Material's type scale in Figtree; headings a step heavier, so they hold without being loud. */
val Typography = Typography(
    displayLarge = base.displayLarge.figtree(FontWeight.Medium),
    displayMedium = base.displayMedium.figtree(FontWeight.Medium),
    displaySmall = base.displaySmall.figtree(FontWeight.Medium),
    headlineLarge = base.headlineLarge.figtree(FontWeight.Medium),
    headlineMedium = base.headlineMedium.figtree(FontWeight.Medium),
    headlineSmall = base.headlineSmall.figtree(FontWeight.Medium),
    titleLarge = base.titleLarge.figtree(FontWeight.SemiBold),
    titleMedium = base.titleMedium.figtree(FontWeight.SemiBold),
    titleSmall = base.titleSmall.figtree(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.figtree(),
    bodyMedium = base.bodyMedium.figtree(),
    bodySmall = base.bodySmall.figtree(),
    labelLarge = base.labelLarge.figtree(FontWeight.SemiBold),
    labelMedium = base.labelMedium.figtree(FontWeight.SemiBold),
    labelSmall = base.labelSmall.figtree(FontWeight.Medium),
)
