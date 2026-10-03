package es.carlospb.modoguardia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.system.CheckLevel
import es.carlospb.modoguardia.system.FixAction
import es.carlospb.modoguardia.system.SystemStatus
import es.carlospb.modoguardia.ui.theme.GuardColors
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    settings: GuardSettings,
    status: SystemStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onFix: (FixAction) -> Unit,
    onOpenStatus: () -> Unit,
    onOpenRules: () -> Unit,
) {
    var confirmStop by remember { mutableStateOf(false) }
    var confirmStartWithProblems by remember { mutableStateOf(false) }
    val active = settings.guardActive

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text("MODO GUARDIA", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        if (active) {
            Text("🟢 ACTIVO", color = GuardColors.Active, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("MODO GUARDIA ACTIVO · vigilando", color = GuardColors.TextDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            ElapsedClock(settings.guardStartedAt)
            Text(
                "Inicio: " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(settings.guardStartedAt)),
                color = GuardColors.TextDim,
            )
        } else {
            Text("⚫ MODO GUARDIA DESACTIVADO", color = GuardColors.TextDim, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Actualmente desactivado", color = GuardColors.TextDim)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "${settings.activeTriggerCount} reglas activas",
            color = if (settings.activeTriggerCount == 0) GuardColors.Warning else GuardColors.Text,
            modifier = Modifier.padding(4.dp),
        )
        val readyText = when {
            status.items.isEmpty() -> "Comprobando…"
            status.ready -> "Sistema preparado ✓"
            status.hasErrors -> "Hay problemas que impedirán la alarma"
            else -> "Hay ajustes que pueden limitar la alarma"
        }
        Text(
            readyText,
            color = when {
                status.ready -> GuardColors.Active
                status.hasErrors -> GuardColors.Error
                else -> GuardColors.Warning
            },
        )

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                when {
                    active -> confirmStop = true
                    status.problems.isNotEmpty() -> confirmStartWithProblems = true
                    else -> onStart()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(24.dp),
            colors = if (active) {
                ButtonDefaults.buttonColors(containerColor = GuardColors.SurfaceHigh, contentColor = GuardColors.Text)
            } else {
                ButtonDefaults.buttonColors(containerColor = GuardColors.Active, contentColor = MaterialTheme.colorScheme.onPrimary)
            },
        ) {
            Text(if (active) "FINALIZAR GUARDIA" else "INICIAR GUARDIA", fontSize = 22.sp, fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(20.dp))
        if (active && status.problems.isNotEmpty()) {
            WarningBanner(status.problems, onOpenStatus)
            Spacer(Modifier.height(12.dp))
        }
        if (settings.activeTriggerCount == 0) {
            GuardCard(borderColor = GuardColors.Warning) {
                Text("No hay ninguna regla activa: Modo Guardia no te despertará con nada.", color = GuardColors.Warning)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenRules) { Text("Crear una regla") }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Estado resumido: permisos, acceso a notificaciones, batería, alarma.
        GuardCard {
            status.items.filter { it.level != CheckLevel.INFO }.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Dot(levelColor(item.level))
                        Spacer(Modifier.width(10.dp))
                        Text(item.title, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(item.summary, color = levelColor(item.level), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
            if (status.problems.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                status.problems.firstOrNull { it.action != null }?.let { first ->
                    Button(onClick = { onFix(first.action!!) }) { Text(first.actionLabel ?: "Corregir") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmStop) {
        ConfirmDialog(
            title = "¿Finalizar la guardia?",
            text = "Dejarás de recibir alarmas por avisos y llamadas.",
            confirm = "Finalizar",
            onConfirm = onStop,
            onDismiss = { confirmStop = false },
        )
    }
    if (confirmStartWithProblems) {
        ConfirmDialog(
            title = "Hay problemas sin resolver",
            text = status.problems.joinToString("\n") { "${levelEmoji(it.level)} ${it.title}: ${it.summary}" } +
                "\n\nPuedes iniciar la guardia igualmente, pero la alarma podría no sonar.",
            confirm = "Iniciar igualmente",
            onConfirm = onStart,
            onDismiss = { confirmStartWithProblems = false },
        )
    }
}

@Composable
private fun ElapsedClock(startedAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val total = ((now - startedAt) / 1000).coerceAtLeast(0)
    Text(
        "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60),
        style = MaterialTheme.typography.displayLarge,
        color = GuardColors.Text,
    )
}
