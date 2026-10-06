package es.carlospb.modoguardia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.domain.ChatKind
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.domain.WhatsApp
import es.carlospb.modoguardia.service.DiagnosticEntry
import es.carlospb.modoguardia.system.CheckLevel
import es.carlospb.modoguardia.system.FixAction
import es.carlospb.modoguardia.system.SystemStatus
import es.carlospb.modoguardia.ui.theme.GuardColors
import java.text.DateFormat
import java.util.Date

@Composable
fun StatusScreen(settings: GuardSettings, status: SystemStatus, vm: MainViewModel, onFix: (FixAction) -> Unit, onBack: () -> Unit) {
    val diagnostics by vm.diagnostics.collectAsStateWithLifecycle()
    val lastSeen by vm.lastSeen.collectAsStateWithLifecycle()
    val errors = status.items.count { it.level == CheckLevel.ERROR }
    val warnings = status.items.count { it.level == CheckLevel.WARNING }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = "Estado del sistema", subtitle = "Comprobado en el teléfono ahora mismo", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .then(ScreenPadding),
        ) {
            val (icon, tone, title, body) = when {
                !status.checked -> Summary(Icons.Rounded.HourglassTop, Tone.NEUTRAL, "Comprobando…", "Un momento.")
                errors > 0 -> Summary(
                    Icons.Rounded.ErrorOutline, Tone.ERROR, "Acción necesaria",
                    "Hay ${errors + warnings} ${if (errors + warnings == 1) "punto" else "puntos"} por resolver. Con un fallo en rojo la alarma puede no sonar.",
                )
                warnings > 0 -> Summary(
                    Icons.Rounded.WarningAmber, Tone.WARNING, if (warnings == 1) "Revisa 1 ajuste" else "Revisa $warnings ajustes",
                    "La alarma funciona, pero estos ajustes pueden limitarla.",
                )
                else -> Summary(Icons.Rounded.CheckCircle, Tone.OK, "Todo preparado", "Permisos, batería y alarma comprobados: Modo Guardia puede despertarte.")
            }
            GuardCard(borderColor = if (tone == Tone.OK || tone == Tone.NEUTRAL) null else tone.color.copy(alpha = 0.45f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon, tone, size = 52.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = if (tone == Tone.NEUTRAL) GuardColors.Text else tone.color)
                        Text(body, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
                    }
                }
            }

            SectionTitle("Comprobaciones")
            GuardCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                // Primero lo que falla; dentro de cada nivel, el orden de siempre.
                val sorted = status.items.sortedBy {
                    when (it.level) {
                        CheckLevel.ERROR -> 0
                        CheckLevel.WARNING -> 1
                        CheckLevel.OK -> 2
                        CheckLevel.INFO -> 3
                    }
                }
                sorted.forEachIndexed { i, item ->
                    if (i > 0) RowDivider()
                    CheckRow(item, onFix)
                }
            }

            SectionTitle("Probar alarma")
            TestAlarmCard(vm)

            SectionTitle("¿Llegan los avisos?")
            GuardCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                val packages = buildSet {
                    settings.rules.filter { it.enabled }.forEach { r ->
                        when (r.type) {
                            RuleType.APP, RuleType.KEYWORDS -> r.packageName?.let(::add)
                            RuleType.WHATSAPP_ANY -> addAll(WhatsApp.PACKAGES)
                            RuleType.GROUP, RuleType.PERSON -> r.packageName?.let(::add) ?: addAll(WhatsApp.PACKAGES)
                            RuleType.CALL -> Unit
                        }
                    }
                }
                if (packages.isEmpty()) {
                    Text("Aún no vigilas ninguna app.", style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim, modifier = Modifier.padding(vertical = 12.dp))
                }
                packages.sorted().forEachIndexed { i, pkg ->
                    if (i > 0) RowDivider()
                    val seen = lastSeen[pkg]
                    ListRow(
                        title = rememberAppLabel(pkg),
                        subtitle = if (seen == null) "Sin avisos desde que arrancó el detector" else "Último aviso: " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(seen)),
                        subtitleColor = if (seen == null) GuardColors.TextDim else GuardColors.Active,
                        leading = { AppIcon(pkg, size = 36.dp) },
                    )
                }
            }
            Hint(
                "Si una app que recibe mensajes nunca aparece aquí, sus notificaciones no llegan a Android " +
                    "(chat silenciado, notificaciones de la app desactivadas o sin conexión).",
            )

            SectionTitle("Diagnóstico de notificaciones")
            GuardCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
                ToggleRow(
                    "Ver cómo llegan los avisos",
                    "Muestra lo que trae cada aviso y qué conversación o llamada reconoce Modo Guardia. Solo en memoria; se borra al desactivarlo.",
                    settings.diagnosticsEnabled,
                ) { vm.setDiagnostics(it) }
                if (settings.diagnosticsEnabled) {
                    if (diagnostics.isEmpty()) {
                        Text("Esperando el próximo aviso…", style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim, modifier = Modifier.padding(vertical = 10.dp))
                    } else {
                        TextButton(onClick = vm::clearDiagnostics) { Text("Limpiar", color = GuardColors.Active) }
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
                        "• Solo se ven los avisos que las apps publican: un chat silenciado no publica nada.\n" +
                        "• Ninguna app de chat deja leer su lista de conversaciones: se aprenden de sus avisos.\n" +
                        "• Algunos fabricantes cierran apps en segundo plano por su cuenta: revisa la línea del fabricante arriba.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GuardColors.TextDim,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

private data class Summary(val icon: androidx.compose.ui.graphics.vector.ImageVector, val tone: Tone, val title: String, val body: String)

@Composable
private fun DiagnosticCard(d: DiagnosticEntry) {
    val e = d.event
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        RowDivider()
        Spacer(Modifier.height(8.dp))
        fun line(label: String, value: String?) = "$label: ${value ?: "—"}"
        val kind = when (d.kind) {
            ChatKind.GROUP -> "grupo"
            ChatKind.INDIVIDUAL -> "chat individual"
            ChatKind.UNKNOWN -> "no se puede saber"
        }
        val text = listOf(
            line("Aplicación", "${e.appName} (${e.packageName})"),
            line("Título", e.title),
            line("Texto", e.text),
            line("Subtexto", e.subText),
            line("Conversation title", e.conversationTitle),
            line("Es grupo (Android)", e.isGroupConversation?.let { if (it) "sí" else "no" } ?: "no indicado"),
            line("Tipo de conversación", kind),
            line("Id de conversación", if (e.shortcutId != null) "sí" else "no"),
            line("Canal", e.channelId),
            line("Mensajes (MessagingStyle)", e.messages.size.toString()),
            line("Último remitente", e.lastMessage?.sender),
            line("Categoría", e.category),
            line("Hora", DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(d.receivedAt))),
        ) + listOfNotNull(
            d.conversation?.let { line("Conversación reconocida", "${it.name} (${if (it.isGroup) "grupo" else "persona"})") },
            if (d.groupCandidates.isNotEmpty()) line("Nombres de grupo posibles", d.groupCandidates.joinToString(" | ")) else null,
            d.caller?.let { c -> line("Quién llama", (c.names + c.numbers).joinToString(" | ").ifEmpty { "no identificable en este aviso" }) },
            d.skipReason?.let { line("No evaluado", it) },
            if (e.isIncomingCall) "Llamada entrante: sí (${if (e.isPhoneCall) "teléfono" else "app"})" else null,
        )
        Text(text.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = GuardColors.TextDim, lineHeight = 17.sp)
    }
}
