package es.carlospb.modoguardia.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.container
import es.carlospb.modoguardia.ui.theme.GuardColors
import es.carlospb.modoguardia.ui.theme.ModoGuardiaTheme
import java.text.DateFormat
import java.util.Date

/**
 * 🚨 ALERTA DE GUARDIA. Se abre por full-screen intent (pantalla bloqueada o apagada) o desde la
 * app. Se muestra sobre el bloqueo y enciende la pantalla (manifiesto + setShowWhenLocked /
 * setTurnScreenOn). No desbloquea el teléfono: no hace falta para detener la alarma.
 */
class GuardAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
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
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha",
    )
    val r = alarm.request
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.timestamp))

    Box(
        Modifier
            .fillMaxSize()
            .background(GuardColors.AlarmDark)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Text("🚨", fontSize = 72.sp, modifier = Modifier.alpha(pulse))
                Text(
                    "ALERTA DE GUARDIA",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(time, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(24.dp))
                Field("Origen", r.sourceApp)
                Field("Regla", r.ruleLabel)
                if (r.message.isNotBlank()) Field("Mensaje", r.message)
                if (alarm.extraMatches > 0) {
                    Field("Además", "${alarm.extraMatches} aviso(s) más mientras sonaba la alarma")
                }
                alarm.note?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("⚠️ $it", color = GuardColors.Warning, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = GuardColors.Alarm),
            ) {
                Text("DETENER ALARMA", fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, letterSpacing = 1.5.sp)
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
