package es.carlospb.modoguardia.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.domain.CaptureArrival
import es.carlospb.modoguardia.domain.CaptureOption
import es.carlospb.modoguardia.domain.ChatKind
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.SeenChat
import es.carlospb.modoguardia.domain.WatchedApp
import es.carlospb.modoguardia.domain.watches
import es.carlospb.modoguardia.service.GuardNotificationListener
import es.carlospb.modoguardia.ui.theme.GuardColors
import kotlinx.coroutines.delay

/**
 * «Identificar con un mensaje»: mientras esta pantalla está abierta, cada aviso de chat que llega
 * dice de qué conversación viene, para vigilarla con un toque. Vale para cualquier app de mensajería
 * ([appKey], o todas si es null). No guarda nada: al salir se olvida.
 */
@Composable
fun ChatCaptureScreen(appKey: String?, rulesVm: RulesViewModel, onBack: () -> Unit, onOpenStatus: () -> Unit, onAdded: (String) -> Unit) {
    val session by rulesVm.capture.collectAsStateWithLifecycle()
    val rules by rulesVm.rules.collectAsStateWithLifecycle()
    val connected by GuardNotificationListener.connected.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val activity = LocalContext.current as? Activity
    val appLabel = appKey?.let { rememberAppLabel(it) }

    LaunchedEffect(appKey) {
        val s = rulesVm.capture.value
        if (s == null || s.appKey != appKey || System.currentTimeMillis() > s.until) rulesVm.startCapture(appKey)
    }
    // Al salir de la pantalla se deja de escuchar (girar el teléfono no cuenta como salir).
    DisposableEffect(Unit) {
        onDispose { if (activity?.isChangingConfigurations != true) rulesVm.stopCapture() }
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val s = session
    val listening = s != null && now <= s.until
    val arrivals = s?.arrivals.orEmpty()

    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            title = "Identificar un chat",
            subtitle = appLabel?.let { "Con un mensaje de $it" } ?: "Con un mensaje, en cualquier app",
            onBack = onBack,
            leading = appKey?.let { { AppIcon(it, size = 36.dp) } },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .then(ScreenPadding),
        ) {
            if (!connected) {
                Spacer(Modifier.height(8.dp))
                GuardCard(borderColor = GuardColors.Warning.copy(alpha = 0.5f), containerColor = GuardColors.WarningContainer, onClick = onOpenStatus) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = GuardColors.Warning)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Modo Guardia no está recibiendo notificaciones. Sin ese acceso no llega ningún aviso: " +
                                "revísalo en «Estado del sistema».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GuardColors.Warning,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            GuardCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(
                        if (listening) "Escuchando · quedan ${minutesLeft(s!!.until, now)} min" else "Ya no se escucha",
                        if (listening) Tone.ACTIVE else Tone.NEUTRAL,
                    )
                    Spacer(Modifier.weight(1f))
                    if (!listening) {
                        TextButton(onClick = { rulesVm.startCapture(appKey) }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = GuardColors.Active)
                            Spacer(Modifier.width(6.dp))
                            Text("Escuchar otra vez", color = GuardColors.Active, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Step(1, "Pide a alguien de ese chat que escriba algo (o escribe tú desde otro teléfono).")
                Step(2, "En cuanto llegue el aviso, el chat aparece aquí abajo.")
                Step(3, "Pulsa «Vigilar». Persona o grupo, el que tú elijas.")
                Hint(
                    "Si el chat está abierto en ${appLabel ?: "la app"}, o tienes WhatsApp Web o Escritorio abierto, " +
                        "o el chat está silenciado, la app no publica aviso y no aparecerá. " +
                        "Modo Guardia no guarda el mensaje: solo mira de qué chat viene.",
                )
            }

            if (appKey != null && WatchedApp(appKey, rules.filter { !it.isCall && it.appKey == appKey }).allNotifications) {
                Hint("Ahora mismo $appLabel te despierta con todos sus avisos. Los chats que añadas cuentan cuando elijas «Solo los chats que elijas».")
            }

            SectionTitle("Chats que han avisado")
            if (arrivals.isEmpty()) {
                GuardCard {
                    EmptyState(
                        icon = if (listening) Icons.Rounded.HourglassTop else Icons.Rounded.MarkChatUnread,
                        title = if (listening) "Esperando un aviso…" else "No llegó ningún aviso",
                        body = if (listening) {
                            "Deja esta pantalla abierta. No hace falta que hagas nada más."
                        } else {
                            "Pulsa «Escuchar otra vez» y pide que escriban en el chat."
                        },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                arrivals.forEach { arrival ->
                    ArrivalCard(arrival, rules, now, showApp = appKey == null) { option ->
                        rulesVm.addCaptured(arrival, option)
                        haptics.confirm()
                        onAdded(option.name)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun minutesLeft(until: Long, now: Long): Long = ((until - now).coerceAtLeast(0) + 59_999) / 60_000

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("$n.", style = MaterialTheme.typography.bodyMedium, color = GuardColors.Active, modifier = Modifier.width(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ArrivalCard(arrival: CaptureArrival, rules: List<Rule>, now: Long, showApp: Boolean, onWatch: (CaptureOption) -> Unit) {
    GuardCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showApp) {
                AppIcon(arrival.packageName, size = 20.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                (if (showApp) arrival.appName + " · " else "") + relativeTime(arrival.receivedAt, now),
                style = MaterialTheme.typography.labelMedium,
                color = GuardColors.TextDim,
            )
        }
        arrival.problem?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = GuardColors.Warning)
            Hint("Prueba con otro mensaje, o escribe el nombre del chat a mano.")
        }
        if (arrival.kind == ChatKind.UNKNOWN && arrival.options.size > 1) {
            Hint("Android no dice si es una persona o un grupo. Elige la opción que corresponde al chat.")
        }
        arrival.options.forEachIndexed { i, option ->
            if (i > 0) RowDivider()
            val chat = SeenChat(option.name, option.isGroup, arrival.receivedAt, arrival.packageName, arrival.conversationId)
            val watched = rules.any { it.enabled && it.watches(chat) }
            ListRow(
                title = option.name,
                subtitle = if (option.isGroup) "Grupo" else "Persona",
                leading = { ConversationAvatar(option.name, option.isGroup, size = 40.dp, appKey = arrival.packageName) },
                trailing = {
                    if (watched) {
                        StatusChip("Vigilado", Tone.ACTIVE)
                    } else {
                        TextButton(onClick = { onWatch(option) }) {
                            Text("Vigilar", color = GuardColors.Active, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
            )
        }
    }
}
