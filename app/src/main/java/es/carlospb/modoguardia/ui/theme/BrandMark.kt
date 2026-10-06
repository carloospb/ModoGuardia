package es.carlospb.modoguardia.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

/** Escudo con media luna (guardia + descanso): el mismo dibujo que el icono de la app. */
private const val SHIELD = "M26,2 L48,10 L48,28 C48,42 38,53 26,58 C14,53 4,42 4,28 L4,10 Z"
private const val MOON = "M30,16 C22,17 17,24 18,32 C19,39 25,44 32,43 C27,40 24,35 24,29 C24,23 26,19 30,16 Z"
private const val VIEW_W = 52f
private const val VIEW_H = 60f

/**
 * La marca de Modo Guardia. [filled] = guardia activa (escudo lleno); si no, solo el contorno.
 * Proporción 52×60: el ancho manda.
 */
@Composable
fun BrandShield(
    modifier: Modifier = Modifier,
    color: Color = GuardColors.Active,
    moonColor: Color = GuardColors.Background,
    filled: Boolean = true,
    strokeWidth: Float = 3.2f,
) {
    val shield = remember { PathParser().parsePathString(SHIELD).toPath() }
    val moon = remember { PathParser().parsePathString(MOON).toPath() }
    Canvas(modifier) {
        val scale = minOf(size.width / VIEW_W, size.height / VIEW_H)
        val dx = (size.width - VIEW_W * scale) / 2f
        val dy = (size.height - VIEW_H * scale) / 2f
        withTransform({
            translate(dx, dy)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            if (filled) {
                drawPath(shield, color)
                drawPath(moon, moonColor)
            } else {
                drawPath(shield, color, style = Stroke(width = strokeWidth, join = StrokeJoin.Round))
                drawPath(moon, color)
            }
        }
    }
}
