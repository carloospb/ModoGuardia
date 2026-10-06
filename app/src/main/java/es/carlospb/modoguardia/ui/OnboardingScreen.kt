package es.carlospb.modoguardia.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.domain.CallMode
import es.carlospb.modoguardia.domain.WatchSummary
import es.carlospb.modoguardia.domain.WhatsApp
import es.carlospb.modoguardia.system.FixAction
import es.carlospb.modoguardia.system.SystemStatus
import es.carlospb.modoguardia.ui.theme.BrandShield
import es.carlospb.modoguardia.ui.theme.GuardColors

private const val STEPS = 6
private val PERMISSION_CHECKS = listOf("notification_access", "app_notifications", "full_screen", "battery", "unused_app", "dnd")

/**
 * Configuración guiada: 1 bienvenida · 2 acceso necesario · 3 qué te despierta · 4 llamadas ·
 * 5 alarma y prueba · 6 activar. Cada paso es una pantalla corta; lo que necesita más espacio
 * (elegir chats, contactos) abre su pantalla y vuelve aquí.
 */
@Composable
fun OnboardingScreen(
    settings: GuardSettings,
    status: SystemStatus,
    vm: MainViewModel,
    rulesVm: RulesViewModel,
    onFix: (FixAction) -> Unit,
    onPickChats: (String) -> Unit,
    onAddApp: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenCalls: () -> Unit,
    onFinish: (startGuard: Boolean) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    val haptics = rememberHaptics()

    Column(Modifier.fillMaxSize()) {
        StepHeader(step)
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { if (forward) it / 8 else -it / 8 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { if (forward) -it / 8 else it / 8 })
            },
            modifier = Modifier.weight(1f),
            label = "onboardingStep",
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .then(ScreenPadding),
            ) {
                when (current) {
                    1 -> Welcome()
                    2 -> Permissions(status, onFix)
                    3 -> WhatWakesYou(settings, rulesVm, onPickChats, onAddApp, onOpenApp)
                    4 -> Calls(settings, vm, onFix, onOpenCalls)
                    5 -> {
                        StepTitle("Tu alarma", "Elige cómo suena y pruébala ahora: es el mismo camino que una alarma real.")
                        AlarmSettings(settings, vm)
                        SectionTitle("Probar")
                        TestAlarmCard(vm)
                    }
                    else -> Ready(settings, status)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 1) {
                TextButton(onClick = { step-- }) { Text("Atrás", style = MaterialTheme.typography.labelLarge, color = GuardColors.TextDim) }
            }
            Spacer(Modifier.weight(1f))
            if (step < STEPS) {
                PrimaryButton(if (step == 1) "Empezar" else "Siguiente", onClick = { step++ })
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    PrimaryButton(
                        "Activar Modo Guardia",
                        onClick = {
                            haptics.toggle(true)
                            onFinish(true)
                        },
                        icon = Icons.Rounded.NotificationsActive,
                    )
                }
            }
        }
        if (step == STEPS) {
            TextButton(onClick = { onFinish(false) }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) {
                Text("Terminar sin activar", style = MaterialTheme.typography.labelLarge, color = GuardColors.TextDim)
            }
        }
    }
}

@Composable
private fun StepHeader(step: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .semantics { contentDescription = "Paso $step de $STEPS" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(STEPS) { i ->
            val done = i < step
            val width by animateDpAsState(if (i == step - 1) 28.dp else 10.dp, tween(260), label = "dot")
            val color by animateColorAsState(if (done) GuardColors.Active else GuardColors.SurfaceHighest, tween(260), label = "dotColor")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Spacer(Modifier.weight(1f))
        Text("$step / $STEPS", style = MaterialTheme.typography.labelMedium, color = GuardColors.TextFaint)
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = GuardColors.TextDim)
}

@Composable
private fun Welcome() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        BrandShield(Modifier.size(width = 88.dp, height = 102.dp))
        Spacer(Modifier.height(28.dp))
        Text("Duerme tranquilo.", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Modo Guardia te despierta solo cuando algo de tu guardia lo necesita.",
            style = MaterialTheme.typography.bodyLarge,
            color = GuardColors.TextDim,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(32.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Benefit(Icons.Rounded.Forum, "Solo lo importante", "Los chats, grupos, apps y llamadas que tú elijas. Lo demás, en silencio.")
        Benefit(Icons.AutoMirrored.Rounded.VolumeUp, "Suena de verdad", "Alarma potente aunque el teléfono esté en silencio o bloqueado, hasta que la detengas.")
        Benefit(Icons.Rounded.Lock, "Privado", "Todo se analiza en tu teléfono. No se guardan mensajes y nada sale de él.")
    }
}

@Composable
private fun Benefit(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    GuardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, Tone.ACTIVE, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
            }
        }
    }
}

@Composable
private fun Permissions(status: SystemStatus, onFix: (FixAction) -> Unit) {
    val items = PERMISSION_CHECKS.mapNotNull { id -> status.items.firstOrNull { it.id == id } }
    val ready = items.count { it.level == es.carlospb.modoguardia.system.CheckLevel.OK || it.level == es.carlospb.modoguardia.system.CheckLevel.INFO }
    StepTitle(
        "Lo imprescindible",
        "Para ver los avisos y sonar con el teléfono bloqueado, Android necesita tu permiso. Cada estado es real: se comprueba en el teléfono.",
    )
    Spacer(Modifier.height(16.dp))
    if (items.isNotEmpty()) {
        StatusChip("$ready de ${items.size} listos", if (ready == items.size) Tone.OK else Tone.WARNING)
        Spacer(Modifier.height(10.dp))
    }
    GuardCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
        if (items.isEmpty()) {
            Text("Comprobando…", style = MaterialTheme.typography.bodyMedium, color = GuardColors.TextDim, modifier = Modifier.padding(vertical = 12.dp))
        }
        items.forEachIndexed { i, item ->
            if (i > 0) RowDivider()
            CheckRow(item, onFix)
        }
    }
    Hint("Si Android muestra «Ajuste restringido» al dar el acceso a notificaciones: Ajustes → Apps → Modo Guardia → ⋮ → «Permitir ajustes restringidos».")
}

@Composable
private fun WhatWakesYou(
    settings: GuardSettings,
    rulesVm: RulesViewModel,
    onPickChats: (String) -> Unit,
    onAddApp: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val rules by rulesVm.rules.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val installedWhatsApps = remember { WhatsApp.PACKAGES.filter { AppInfo.isInstalled(context, it) } }
    val apps = remember(rules) { WatchSummary.appsOf(rules) }
    // WhatsApp y WhatsApp Business por separado (suele ser una personal y otra del trabajo): cada una
    // si está instalada o si ya tiene reglas.
    val whatsapps = WhatsApp.PACKAGES.filter { pkg -> pkg in installedWhatsApps || apps.any { it.key == pkg } }
    StepTitle("¿Qué debe despertarte?", "Elige apps y, si quieres, solo algunos de sus chats. Podrás cambiarlo cuando quieras en «Reglas».")
    whatsapps.forEach { pkg ->
        val label = rememberAppLabel(pkg)
        val wa = apps.firstOrNull { it.key == pkg }
        SectionTitle(label)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceCard(
                "Cualquier mensaje de $label",
                "Todo lo que llegue a $label te despierta.",
                selected = wa?.allNotifications == true,
                icon = Icons.Rounded.NotificationsActive,
            ) { rulesVm.setAllNotifications(pkg, true) }
            ChoiceCard(
                "Solo los chats que elija",
                wa?.takeIf { it.conversationCount > 0 }?.let { appSummary(it) } ?: "Personas y grupos concretos, por ejemplo tu grupo de guardias.",
                selected = wa != null && !wa.allNotifications && wa.conversationCount > 0,
                icon = Icons.Rounded.PeopleAlt,
            ) {
                rulesVm.setAllNotifications(pkg, false)
                onPickChats(pkg)
            }
        }
    }
    SectionTitle("Otras apps")
    GuardCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        val others = apps.filter { it.key !in whatsapps }
        others.forEachIndexed { i, app ->
            if (i > 0) RowDivider()
            ListRow(
                title = rememberAppLabel(app.key, app.storedLabel),
                subtitle = appSummary(app),
                leading = { AppIcon(app.key, size = 36.dp) },
                onClick = { onOpenApp(app.key) },
            )
        }
        if (others.isNotEmpty()) RowDivider()
        ListRow(
            title = "Añadir una app",
            subtitle = "Telegram, correo, monitorización, mensajería del trabajo…",
            leading = { IconBadge(Icons.Rounded.Add, Tone.ACTIVE, size = 36.dp) },
            onClick = onAddApp,
            titleColor = GuardColors.Active,
        )
    }
    if (settings.watch.apps.isEmpty()) {
        Hint("Puedes seguir sin elegir nada y hacerlo luego, pero mientras tanto ningún aviso te despertará.")
    }
}

@Composable
private fun Calls(settings: GuardSettings, vm: MainViewModel, onFix: (FixAction) -> Unit, onOpenCalls: () -> Unit) {
    StepTitle("Llamadas", "Una llamada puede hacer sonar la alarma además del timbre. La llamada funciona con normalidad.")
    Spacer(Modifier.height(16.dp))
    GuardCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        ToggleRow(
            "Despertar con llamadas telefónicas",
            null,
            settings.wakeOnPhoneCalls,
            leading = { IconBadge(Icons.Rounded.PhoneInTalk, if (settings.wakeOnPhoneCalls) Tone.ACTIVE else Tone.NEUTRAL, size = 40.dp) },
        ) { on ->
            vm.setWakeOnPhoneCalls(on)
            if (on) onFix(FixAction.PHONE_PERMISSION)
        }
    }
    if (settings.wakeOnPhoneCalls) {
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceCard("Todas las llamadas", null, settings.callMode == CallMode.ALL) { vm.setCallMode(CallMode.ALL) }
            ChoiceCard(
                "Solo contactos y números elegidos",
                if (settings.callMode == CallMode.SELECTED) "${settings.watch.callRules.size} elegidos" else null,
                settings.callMode == CallMode.SELECTED,
            ) {
                vm.setCallMode(CallMode.SELECTED)
                onOpenCalls()
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    GuardCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        ToggleRow(
            "Llamadas de apps",
            "WhatsApp, Teams, Telegram…",
            settings.wakeOnAppCalls,
            leading = { IconBadge(Icons.Rounded.Forum, if (settings.wakeOnAppCalls) Tone.ACTIVE else Tone.NEUTRAL, size = 40.dp) },
        ) { vm.setWakeOnAppCalls(it) }
    }
}

@Composable
private fun Ready(settings: GuardSettings, status: SystemStatus) {
    val watch = settings.watch
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        BrandShield(Modifier.size(width = 72.dp, height = 84.dp), color = if (status.operational) GuardColors.Active else GuardColors.Warning)
        Spacer(Modifier.height(20.dp))
        Text(
            if (status.operational) "Todo listo" else "Casi listo",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (status.operational) "Activa Modo Guardia cuando empiece tu guardia." else "Puedes activarlo ya y resolver lo pendiente desde Inicio.",
            style = MaterialTheme.typography.bodyLarge,
            color = GuardColors.TextDim,
            textAlign = TextAlign.Center,
        )
    }
    SectionTitle("Te despertará")
    GuardCard {
        LabeledValue("Apps vigiladas", watch.appCount.toString())
        LabeledValue("Chats elegidos", watch.conversationCount.toString())
        LabeledValue(
            "Llamadas",
            when {
                !watch.phoneCalls -> "No"
                watch.callMode == CallMode.ALL -> "Todas"
                else -> "${watch.callRules.size} contactos y números"
            },
        )
        LabeledValue("Llamadas de apps", if (watch.appCalls) "Sí" else "No")
    }
    if (status.problems.isNotEmpty()) {
        SectionTitle("Pendiente")
        GuardCard {
            status.problems.forEach { item ->
                Text("• ${item.title}: ${item.summary}", style = MaterialTheme.typography.bodySmall, color = item.level.tone.color)
            }
        }
    }
}
