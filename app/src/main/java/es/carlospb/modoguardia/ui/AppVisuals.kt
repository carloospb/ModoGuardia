package es.carlospb.modoguardia.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import es.carlospb.modoguardia.domain.TextNormalizer
import es.carlospb.modoguardia.domain.WhatsApp
import es.carlospb.modoguardia.ui.theme.GuardColors
import es.carlospb.modoguardia.ui.theme.Manrope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Nombre e icono reales de las apps vigiladas (PackageManager, visibles por el <queries> del
 * lanzador). Con caché: la lista de reglas los pide en cada recomposición.
 */
object AppInfo {
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = LruCache<String, ImageBitmap>(48)

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Paquete que representa a una clave de app (hoy, el mismo: cada app es la suya). */
    fun resolve(@Suppress("UNUSED_PARAMETER") context: Context, key: String): String = key

    fun label(context: Context, key: String, fallback: String? = null): String = labels.getOrPut(key) {
        val pkg = resolve(context, key)
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            return fallback ?: when (key) {
                WhatsApp.PERSONAL -> "WhatsApp"
                WhatsApp.BUSINESS -> "WhatsApp Business"
                else -> key
            }
        }
    }

    /** Bloquea (decodifica un Drawable): llamar fuera del hilo principal. */
    fun icon(context: Context, key: String, sizePx: Int): ImageBitmap? {
        val pkg = resolve(context, key)
        val cacheKey = "$pkg@$sizePx"
        icons.get(cacheKey)?.let { return it }
        return try {
            context.packageManager.getApplicationIcon(pkg).toBitmap(sizePx, sizePx).asImageBitmap().also { icons.put(cacheKey, it) }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun rememberAppLabel(key: String, fallback: String? = null): String {
    val context = LocalContext.current
    return remember(key, fallback) { AppInfo.label(context, key, fallback) }
}

/** Icono real de la app; mientras carga o si no está instalada, un símbolo neutro. */
@Composable
fun AppIcon(key: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key, px) {
        value = withContext(Dispatchers.IO) { AppInfo.icon(context, key, px) }
    }
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(GuardColors.SurfaceHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Apps, contentDescription = null, tint = GuardColors.TextDim, modifier = Modifier.size(size * 0.55f))
        }
    }
}

private val avatarColors = listOf(
    Color(0xFF3DE2A3), Color(0xFF79AEFF), Color(0xFFFFB648), Color(0xFFE48BD6),
    Color(0xFFA08CFF), Color(0xFFFF8A65), Color(0xFF4DD0E1), Color(0xFFC5E86C),
)

/** Color estable por nombre: el mismo contacto siempre del mismo color. */
fun avatarColor(name: String): Color = avatarColors[Math.floorMod(TextNormalizer.groupKey(name).hashCode(), avatarColors.size)]

/**
 * Avatar de conversación: inicial (persona) o símbolo de grupo, y, si se indica, el icono de su app
 * en la esquina. No se usan fotos de perfil: habría que guardarlas y no aportan para decidir.
 */
@Composable
fun ConversationAvatar(name: String, isGroup: Boolean, size: Dp = 44.dp, appKey: String? = null) {
    val color = avatarColor(name)
    // El hueco extra solo hace falta para el icono de la app: sin él, mismo tamaño que los demás iconos.
    Box(Modifier.size(if (appKey != null) size + 4.dp else size)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            if (isGroup) {
                Icon(Icons.Rounded.Groups, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.52f))
            } else {
                val initial = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
                Text(initial, color = color, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.4f).sp)
            }
        }
        if (appKey != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(size * 0.42f)
                    .clip(CircleShape)
                    .background(GuardColors.Background)
                    .border(2.dp, GuardColors.Background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(appKey, size = size * 0.36f)
            }
        }
    }
}

/**
 * Respuesta háptica. Usa los efectos del sistema (respetan el ajuste de vibración táctil del
 * usuario): confirmar / rechazar en Android 11+, encender / apagar en Android 14+.
 */
class Haptics(private val view: View) {
    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT >= 34 -> if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            Build.VERSION.SDK_INT >= 30 -> if (on) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
            else -> HapticFeedbackConstants.LONG_PRESS
        },
    )

    fun confirm() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)

    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
