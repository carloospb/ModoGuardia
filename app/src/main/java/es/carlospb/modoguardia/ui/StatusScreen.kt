package es.carlospb.modoguardia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.domain.WhatsApp
import es.carlospb.modoguardia.service.DiagnosticEntry
import es.carlospb.modoguardia.system.FixAction
import es.carlospb.modoguardia.system.SystemStatus
import es.carlospb.modoguardia.ui.theme.GuardColors
import java.text.DateFormat
import java.util.Date

@Composable
fun StatusScreen(settings: GuardSettings, status: SystemStatus, vm: MainViewModel, onFix: (FixAction) -> Unit) {
    val countdown by vm.countdown.collectAsStateWithLifecycle()
    val alarm by vm.alarm.collectAsStateWithLifecycle()
    val diagnostics by vm.diagnostics.collectAsStateWithLifecycle()
    val lastSeen by vm.lastSeen.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(ScreenPadding),
    ) {
        SectionTitle("Estado del sistema")
        GuardCard {
            status.items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(color = GuardColors.Outline)
                CheckRow(item, onFix)
            }
        }

        SectionTitle("Probar alarma")
        GuardCard {
            Text(
                "Ejecuta exactamente el mismo sistema que una incidencia real: servicio de alarma, " +
                    "volumen, vibración y pantalla de alerta.",
                style = MaterialTheme.typography.bodySmall,
                color = GuardColors.TextDim,
            )
            Spacer(Modifier.height(12.dp))
            when {
                countdown != null -> {
                    Text("La alarma sonará en ${countdown} s", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GuardColors.Warning)
                    TextButton(onClick = vm::cancelTest) { Text("Cancelar prueba") }
                }
                alarm != null -> {
                    Text("🚨 La alarma está sonando", color = GuardColors.Alarm, fontWeight = FontWeight.Bold)
                    Button(onClick = vm::stopAlarm) { Text("DETENER ALARMA") }
                }
                else -> {
                    Button(onClick = { vm.testAlarm(3) }, modifier = Modifier.fillMaxWidth()) { Text("PROBAR ALARMA (3 s)") }
                    OutlinedButton(onClick = { vm.testAlarm(15) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Probar con el teléfono bloqueado (15 s)")
                    }
                    Text(
                        "Pulsa la segunda opción y bloquea el teléfono: comprobarás que la pantalla se enciende y la alerta aparece sobre el bloqueo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GuardColors.TextDim,
                    )
                }
            }
        }

        SectionTitle("¿Llegan los avisos?")
        GuardCard {
            val packages = buildSet {
                settings.rules.filter { it.enabled }.forEach { r ->
                    when (r.type) {
                        RuleType.APP, RuleType.KEYWORDS -> r.packageName?.let(::add)
                        RuleType.WHATSAPP_ANY, RuleType.WHATSAPP_GROUP -> addAll(WhatsApp.PACKAGES)
                    }
                }
            }
            if (packages.isEmpty()) {
                Text("Sin reglas por app todavía.", color = GuardColors.TextDim)
            }
            packages.sorted().forEach { pkg ->
                val seen = lastSeen[pkg]
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(pkg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        if (seen == null) "sin avisos aún" else "último: " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(seen)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (seen == null) GuardColors.TextDim else GuardColors.Active,
                    )
                }
            }
            Text(
                "Desde que se inició el detector. Si una app de tus reglas nunca aparece aquí aunque reciba mensajes, " +
                    "sus notificaciones no llegan a Android (silenciada, bloqueada o sin conexión).",
                style = MaterialTheme.typography.bodySmall,
                color = GuardColors.TextDim,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionTitle("Diagnóstico de notificaciones")
        GuardCard {
            SwitchRow(
                "Mostrar última notificación detectada",
                "Solo en memoria, se borra al desactivarlo. Sirve para ver cómo estructura WhatsApp los avisos en este teléfono.",
                settings.diagnosticsEnabled,
            ) { vm.setDiagnostics(it) }
            if (settings.diagnosticsEnabled) {
                if (diagnostics.isEmpty()) {
                    Text("Esperando la próxima notificación…", color = GuardColors.TextDim)
                } else {
                    TextButton(onClick = vm::clearDiagnostics) { Text("Limpiar") }
                }
                diagnostics.forEach { DiagnosticCard(it) }
            }
        }

        SectionTitle("Lo que Android no permite")
        GuardCard {
            Text(
                "• «No molestar» en silencio total silencia también las alarmas: Modo Guardia lo detecta y avisa, pero no puede saltárselo.\n" +
                    "• Si fuerzas la detención de la app, Android no la deja funcionar hasta que la abras de nuevo.\n" +
                    "• Tras reiniciar, la guardia se reanuda sola cuando desbloqueas el teléfono por primera vez.\n" +
                    "• Solo se ven los avisos que las apps publican; un chat silenciado no publica nada.\n" +
                    "• Algunos fabricantes cierran apps en segundo plano por su cuenta: revisa la línea del fabricante arriba.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DiagnosticCard(d: DiagnosticEntry) {
    val e = d.event
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        HorizontalDivider(color = GuardColors.Outline)
        Spacer(Modifier.height(8.dp))
        fun line(label: String, value: String?) = "$label: ${value ?: "—"}"
        val text = listOf(
            line("Aplicación", e.appName),
            line("Package name", e.packageName),
            line("Título", e.title),
            line("Texto", e.text),
            line("Subtexto", e.subText),
            line("Conversation title", e.conversationTitle),
            line("Es grupo", e.isGroupConversation?.let { if (it) "sí" else "no" } ?: "no indicado"),
            line("Mensajes (MessagingStyle)", e.messages.size.toString()),
            line("Último remitente", e.lastMessage?.sender),
            line("Categoría", e.category),
            line("Hora", DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(d.receivedAt))),
        ) + listOfNotNull(
            if (WhatsApp.isWhatsApp(e.packageName)) {
                line("Grupo detectado", d.groupCandidates.joinToString(" | ").ifEmpty { "NO IDENTIFICABLE en este aviso" })
            } else {
                null
            },
            d.skipReason?.let { line("No evaluado", it) },
            if (e.isIncomingCall) "Llamada entrante: sí (${if (e.isPhoneCall) "teléfono" else "app"})" else null,
        )
        Text(text.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
