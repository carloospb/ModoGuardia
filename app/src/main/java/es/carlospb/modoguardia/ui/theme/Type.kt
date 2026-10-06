package es.carlospb.modoguardia.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import es.carlospb.modoguardia.R

/**
 * Manrope (SIL Open Font License 1.1, licencia en assets/licenses): geométrica, de aspecto técnico y
 * muy legible en tamaños pequeños. Un solo archivo variable para todos los pesos.
 */
@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) = Font(
    R.font.manrope,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Manrope = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.em,
)

val GuardTypography = Typography(
    displayLarge = style(52, 58, FontWeight.ExtraBold, -0.02),
    displayMedium = style(40, 46, FontWeight.ExtraBold, -0.02),
    displaySmall = style(34, 40, FontWeight.Bold, -0.01),
    headlineLarge = style(30, 36, FontWeight.ExtraBold, -0.015),
    headlineMedium = style(26, 32, FontWeight.ExtraBold, -0.01),
    headlineSmall = style(22, 28, FontWeight.Bold, -0.005),
    titleLarge = style(20, 26, FontWeight.Bold),
    titleMedium = style(16, 22, FontWeight.Bold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Medium),
    bodyMedium = style(14, 21, FontWeight.Medium),
    bodySmall = style(13, 19, FontWeight.Medium),
    labelLarge = style(15, 20, FontWeight.Bold, 0.005),
    labelMedium = style(13, 18, FontWeight.SemiBold, 0.01),
    labelSmall = style(11, 16, FontWeight.Bold, 0.06),
)

/** Etiqueta de sección: mayúsculas pequeñas y espaciadas. */
val SectionLabel = style(12, 16, FontWeight.ExtraBold, 0.09)

/** Cifras que cambian (cronómetro, contadores): todas del mismo ancho para que no bailen. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")
