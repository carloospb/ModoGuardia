package es.carlospb.modoguardia.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Paleta «guardia nocturna»: azul noche casi negro, verde menta de «vigilando» (el del icono), ámbar
 * para lo que hay que revisar y rojo solo para la alarma y los fallos. Pensada para leerse a las 3:00
 * recién despertado: fondos oscuros sin negro puro, texto claro sin blanco puro, contraste AA o mejor.
 */
object GuardColors {
    val Background = Color(0xFF080C11)
    val Surface = Color(0xFF0F151D)
    val SurfaceHigh = Color(0xFF151D27)
    val SurfaceHighest = Color(0xFF1C2632)
    val Outline = Color(0xFF263241)
    val OutlineSoft = Color(0xFF19222D)

    val Text = Color(0xFFE8EEF5)
    val TextDim = Color(0xFF9AA8BA)
    // Mínimo AA (4,5:1) también sobre las tarjetas: 5,3:1 sobre Surface y 5,7:1 sobre el fondo.
    val TextFaint = Color(0xFF7D8BA1)

    /** Vigilando / correcto. Mismo verde que el escudo del icono. */
    val Active = Color(0xFF3DE2A3)
    val OnActive = Color(0xFF00281A)
    val ActiveContainer = Color(0xFF0C2A20)
    val ActiveSoft = Color(0xFF8FF3CB)

    val Warning = Color(0xFFFFB648)
    val WarningContainer = Color(0xFF2E2210)

    val Error = Color(0xFFFF5C63)
    val ErrorContainer = Color(0xFF341417)

    val Info = Color(0xFF79AEFF)
    val InfoContainer = Color(0xFF14233A)

    /** Escudo apagado: guardia desactivada. Neutro, sin color de estado. */
    val Idle = Color(0xFF7D8BA0)
    val IdleContainer = Color(0xFF131A23)

    /** Pantalla de alarma. */
    val Alarm = Color(0xFFE5202A)
    val AlarmBright = Color(0xFFFF4B53)
    val AlarmDark = Color(0xFF1C0305)
}

private val scheme = darkColorScheme(
    primary = GuardColors.Active,
    onPrimary = GuardColors.OnActive,
    primaryContainer = GuardColors.ActiveContainer,
    onPrimaryContainer = GuardColors.ActiveSoft,
    secondary = GuardColors.Info,
    onSecondary = Color(0xFF00213F),
    secondaryContainer = GuardColors.InfoContainer,
    onSecondaryContainer = Color(0xFFCFE1FF),
    tertiary = GuardColors.Warning,
    onTertiary = Color(0xFF2B1A00),
    error = GuardColors.Error,
    onError = Color(0xFF3B0006),
    errorContainer = GuardColors.ErrorContainer,
    onErrorContainer = Color(0xFFFFDAD8),
    background = GuardColors.Background,
    onBackground = GuardColors.Text,
    surface = GuardColors.Background,
    onSurface = GuardColors.Text,
    surfaceVariant = GuardColors.SurfaceHigh,
    onSurfaceVariant = GuardColors.TextDim,
    surfaceDim = GuardColors.Background,
    surfaceBright = GuardColors.SurfaceHighest,
    surfaceContainerLowest = GuardColors.Background,
    surfaceContainerLow = GuardColors.Surface,
    surfaceContainer = GuardColors.Surface,
    surfaceContainerHigh = GuardColors.SurfaceHigh,
    surfaceContainerHighest = GuardColors.SurfaceHighest,
    inverseSurface = GuardColors.Text,
    inverseOnSurface = GuardColors.Background,
    outline = GuardColors.Outline,
    outlineVariant = GuardColors.OutlineSoft,
    scrim = Color(0xCC000000),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Solo tema oscuro, a propósito: la app se usa sobre todo de noche y con el usuario medio dormido;
 * un fondo claro a las 3:00 deslumbra justo cuando hay que leer rápido.
 */
@Composable
fun ModoGuardiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = GuardTypography, shapes = shapes) {
        // Fuera de un Surface o Scaffold Compose pinta el texto en negro por defecto: el color base
        // sale del tema para todo.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
