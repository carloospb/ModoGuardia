package es.carlospb.modoguardia.alarm

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.container
import es.carlospb.modoguardia.domain.AlarmKind
import es.carlospb.modoguardia.domain.TextNormalizer
import es.carlospb.modoguardia.ui.AppIcon
import es.carlospb.modoguardia.ui.rememberHaptics
import es.carlospb.modoguardia.ui.theme.GuardColors
import es.carlospb.modoguardia.ui.theme.ModoGuardiaTheme
import es.carlospb.modoguardia.ui.theme.SectionLabel
import es.carlospb.modoguardia.ui.theme.TabularNumbers
import java.text.DateFormat
import java.util.Date

/**
 * ALERTA DE GUARDIA. Se abre por full-screen intent (pantalla bloqueada o apagada) o desde la
 * app. Se muestra sobre el bloqueo y enciende la pantalla (manifiesto + setShowWhenLocked /
 * setTurnScreenOn). No desbloquea el teléfono: no hace falta para detener la alarma.
 *
 * Sin «posponer» a propósito: la alarma suena hasta que el usuario la detiene, que es lo que
 * garantiza que se ha despertado. Un posponer permitiría volver a dormirse con la incidencia abierta.
 */
class GuardAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Atrás no debe ocultar la alerta por accidente: solo DETENER la para.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        promoteFallbackToService()

        setContent {
            ModoGuardiaTheme {
                val alarm by AlarmState.active.collectAsStateWithLifecycle()
                LaunchedEffect(alarm) {
                    if (alarm == null) finish()
                }
                alarm?.let { AlarmScreen(it) { container.alarmController.stop("Detenida por el usuario") } }
            }
        }
    }

    /**
     * Si la alarma está en plan B (Android no dejó arrancar el servicio en segundo plano), ahora la
     * app está en primer plano y sí puede: se pasa al servicio, con volumen y vibración configurados.
     */
    private fun promoteFallbackToService() {
        val alarm = AlarmState.active.value ?: return
        if (!alarm.viaFallback) return
        try {
            ContextCompat.startForegroundService(this, AlarmService.startIntent(this, alarm.request))
        } catch (_: Exception) {
            // Sigue sonando la notificación de reserva: no se pierde la alarma.
        }
    }
}

@Composable
private fun AlarmScreen(alarm: ActiveAlarm, onStop: () -> Unit) {
    val r = alarm.request
    val haptics = rememberHaptics()
    val isCall = r.kind == AlarmKind.PHONE_CALL || r.kind == AlarmKind.APP_CALL
    val isTest = r.kind == AlarmKind.TEST
    val title = when {
        isTest -> "PRUEBA DE ALARMA"
        isCall -> "LLAMADA DE GUARDIA"
        else -> "ALERTA DE GUARDIA"
    }
    val who = r.conversation?.takeIf { it.isNotBlank() } ?: r.ruleLabel
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.timestamp))
    // El mensaje solo si aporta algo distinto de quién es (en llamadas suele repetirlo).
    val message = r.message.takeIf { m ->
        m.isNotBlank() && !isCall && TextNormalizer.groupKey(m) != TextNormalizer.groupKey(who)
    }

    val waves = rememberInfiniteTransition(label = "waves")
    val wave by waves.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "wave")
    val glow by waves.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A0609), GuardColors.AlarmDark, Color(0xFF0B0203))))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(GuardColors.Alarm.copy(alpha = glow * 0.55f), Color.Transparent),
                        center = center.copy(y = size.height * 0.28f),
                        radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f,
                    center = center.copy(y = size.height * 0.28f),
                )
            }
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MODO GUARDIA", style = SectionLabel, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                Text(time, style = MaterialTheme.typography.titleLarge.merge(TabularNumbers), color = Color.White.copy(alpha = 0.85f))
            }
            if (isTest) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Esto es una prueba: comprueba que oyes la alarma, notas la vibración y ves esta pantalla.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GuardColors.Warning,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GuardColors.Warning.copy(alpha = 0.12f))
                        .padding(12.dp),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .size(132.dp)
                        .drawBehind {
                            // Ondas que salen del centro: una alarma, no una notificación.
                            for (i in 0..2) {
                                val p = (wave + i / 3f) % 1f
                                drawCircle(
                                    Color.White.copy(alpha = (1f - p) * 0.35f),
                                    radius = size.minDimension / 2f * (0.55f + p * 0.45f),
                                    style = Stroke(width = 3.dp.toPx()),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            when {
                                isTest -> Icons.Rounded.Alarm
                                isCall -> Icons.Rounded.PhoneInTalk
                                else -> Icons.Rounded.NotificationsActive
                            },
                            contentDescription = null,
                            tint = GuardColors.Alarm,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = 28.sp,
                    style = MaterialTheme.typography.headlineMedium,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    r.packageName?.let {
                        AppIcon(it, size = 24.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(r.sourceApp, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    who,
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                message?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "«$it»",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
                if (TextNormalizer.groupKey(r.ruleLabel) != TextNormalizer.groupKey(who) && !isTest) {
                    Spacer(Modifier.height(12.dp))
                    Text("Regla: ${r.ruleLabel}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f), textAlign = TextAlign.Center)
                }
                if (alarm.extraMatches > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (alarm.extraMatches == 1) "+1 aviso más mientras sonaba" else "+${alarm.extraMatches} avisos más mientras sonaba",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                alarm.note?.let {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = GuardColors.Warning, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = GuardColors.Warning)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Button(
                onClick = {
                    haptics.confirm()
                    onStop()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = GuardColors.Alarm),
            ) {
                Text("DETENER ALARMA", fontSize = 26.sp, style = MaterialTheme.typography.headlineSmall, letterSpacing = 1.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
