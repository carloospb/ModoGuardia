package es.carlospb.modoguardia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Paleta de monitorización nocturna: fondo casi negro, verde de "vigilando", rojo de alerta. */
object GuardColors {
    val Background = Color(0xFF0A0E13)
    val Surface = Color(0xFF121821)
    val SurfaceHigh = Color(0xFF1A2230)
    val Outline = Color(0xFF2A3445)
    val Text = Color(0xFFE6EDF5)
    val TextDim = Color(0xFF8C99AB)
    val Active = Color(0xFF2EE59D)
    val Warning = Color(0xFFFFB020)
    val Error = Color(0xFFFF4D4F)
    val Alarm = Color(0xFFE5202A)
    val AlarmDark = Color(0xFF3A0508)
    val Info = Color(0xFF5AA9FF)
}

private val scheme = darkColorScheme(
    primary = GuardColors.Active,
    onPrimary = Color(0xFF00311F),
    primaryContainer = Color(0xFF0E3B2A),
    onPrimaryContainer = GuardColors.Active,
    secondary = GuardColors.Info,
    onSecondary = Color(0xFF00213F),
    error = GuardColors.Error,
    onError = Color.White,
    errorContainer = Color(0xFF3B1214),
    onErrorContainer = Color(0xFFFFDAD6),
    background = GuardColors.Background,
    onBackground = GuardColors.Text,
    surface = GuardColors.Background,
    onSurface = GuardColors.Text,
    surfaceVariant = GuardColors.SurfaceHigh,
    onSurfaceVariant = GuardColors.TextDim,
    surfaceContainer = GuardColors.Surface,
    surfaceContainerLow = GuardColors.Surface,
    surfaceContainerHigh = GuardColors.SurfaceHigh,
    surfaceContainerHighest = GuardColors.SurfaceHigh,
    outline = GuardColors.Outline,
    outlineVariant = GuardColors.Outline,
)

private val base = Typography()
private val typography = base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
)

val SectionLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)

@Composable
fun ModoGuardiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
