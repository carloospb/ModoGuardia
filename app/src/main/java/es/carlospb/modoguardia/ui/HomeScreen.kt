package es.carlospb.modoguardia.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.domain.CallMode
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.WatchSummary
import es.carlospb.modoguardia.domain.WatchedApp
import es.carlospb.modoguardia.system.SystemStatus
import es.carlospb.modoguardia.ui.theme.BrandShield
import es.carlospb.modoguardia.ui.theme.GuardColors
import es.carlospb.modoguardia.ui.theme.SectionLabel
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Centro de control: el estado de la guardia manda, y todo lo demás lo explica. */
@Composable
fun HomeScreen(
    settings: GuardSettings,
    status: SystemStatus,
    countdown: Int?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenCalls: () -> Unit,
    onOpenApp: (String) -> Unit,
    onTestAlarm: () -> Unit,
) {
    val haptics = rememberHaptics()
    var confirmStop by remember { mutableStateOf(false) }
    var confirmProblems by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val active = settings.guardActive
    val watch = settings.watch

    fun start() {
        haptics.toggle(true)
        onStart()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))
        Wordmark()
        Spacer(Modifier.height(18.dp))

        GuardHero(
            active = active,
            attention = active && status.hasErrors,
            onToggle = {
                when {
                    active -> confirmStop = true
                    watch.triggerCount == 0 -> confirmEmpty = true
                    status.checked && status.problems.isNotEmpty() -> confirmProblems = true
                    else -> start()
                }
            },
        )

        Spacer(Modifier.height(20.dp))
        AnimatedContent(
            targetState = active,
            transitionSpec = {
                (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 4 }) togetherWith
                    (fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 4 })
            },
            label = "guardText",
        ) { isActive ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Mismo color que el anillo: verde si todo está bien, ámbar si algo puede impedir la alarma.
                val attention = isActive && status.hasErrors
                Text(
                    if (isActive) "Modo Guardia activo" else "Modo Guardia desactivado",
                    style = MaterialTheme.typography.headlineMedium,
                    color = when {
                        attention -> GuardColors.Warning
                        isActive -> GuardColors.Active
                        else -> GuardColors.Text
                    },
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                if (isActive) {
                    ActiveSince(settings.guardStartedAt)
                } else {
                    Text(
                        "Toca el escudo para empezar tu guardia",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GuardColors.TextDim,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SystemBanner(status, active, onOpenStatus)

        Spacer(Modifier.height(12.dp))
        StatsRow(watch, onOpenRules, onOpenCalls)

        SectionTitle(if (active) "Vigilando ahora" else "Te despertará")
        WatchListCard(watch, onOpenApp = onOpenApp, onOpenCalls = onOpenCalls, onOpenRules = onOpenRules)

        Spacer(Modifier.height(16.dp))
        if (countdown != null) {
            GuardCard {
                Text("La prueba sonará en $countdown s", style = MaterialTheme.typography.titleMedium, color = GuardColors.Warning)
                Text("Puedes bloquear el teléfono para comprobar la pantalla de alerta.", style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
            }
        } else {
            SecondaryButton(
                "Probar alarma",
                onClick = onTestAlarm,
                icon = Icons.Rounded.NotificationsActive,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(28.dp))
    }

    if (confirmStop) {
        ConfirmDialog(
            title = "¿Desactivar Modo Guardia?",
            text = "Dejarán de sonar las alarmas por mensajes y llamadas.",
            confirm = "Desactivar",
            destructive = true,
            onConfirm = {
                haptics.toggle(false)
                onStop()
            },
            onDismiss = { confirmStop = false },
        )
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = "Aún no has elegido qué te despierta",
            text = "Sin apps, chats ni llamadas elegidas, Modo Guardia no hará sonar la alarma por nada.",
            confirm = "Elegir ahora",
            dismiss = "Activar igualmente",
            onConfirm = onOpenRules,
            onDismissButton = ::start,
            onDismiss = { confirmEmpty = false },
        )
    }
    if (confirmProblems) {
        ConfirmDialog(
            title = "Hay ajustes por resolver",
            text = status.problems.joinToString("\n") { "• ${it.title}: ${it.summary}" } +
                "\n\nPuedes activar la guardia igualmente, pero la alarma podría no sonar.",
            confirm = "Activar igualmente",
            dismiss = "Revisar",
            onConfirm = ::start,
            onDismissButton = onOpenStatus,
            onDismiss = { confirmProblems = false },
        )
    }
}

@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandShield(Modifier.size(width = 14.dp, height = 16.dp), color = GuardColors.TextFaint, moonColor = GuardColors.Background)
        Spacer(Modifier.width(8.dp))
        Text("MODO GUARDIA", style = SectionLabel, color = GuardColors.TextFaint)
    }
}

/**
 * El control principal. Un anillo que se llena al activar (barrido de 0,9 s), el escudo de la
 * marca que pasa de contorno a relleno y, activo, un halo que «respira» despacio. Es un interruptor
 * para TalkBack («Modo Guardia, activado»).
 */
@Composable
private fun GuardHero(active: Boolean, attention: Boolean, onToggle: () -> Unit) {
    val accent = if (attention) GuardColors.Warning else GuardColors.Active
    val progress by animateFloatAsState(if (active) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "ring")
    val ringColor by animateColorAsState(if (active) accent else GuardColors.Idle, tween(500), label = "ringColor")
    val discTop by animateColorAsState(
        when {
            !active -> GuardColors.SurfaceHigh
            attention -> GuardColors.WarningContainer
            else -> GuardColors.ActiveContainer
        },
        tween(500),
        label = "disc",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(140), label = "press")
    val glow = if (active) {
        rememberInfiniteTransition(label = "breath").animateFloat(
            initialValue = 0.18f,
            targetValue = 0.42f,
            animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glow",
        )
    } else {
        null
    }

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val heroSize = (maxWidth * 0.64f).coerceIn(188.dp, 264.dp)
        Box(
            Modifier
                .size(heroSize)
                .scale(pressScale)
                .drawBehind {
                    val stroke = heroSize.toPx() * 0.045f
                    val radius = this.size.minDimension / 2f
                    // Halo: solo dibujo, sin recomponer en cada fotograma.
                    val g = (glow?.value ?: 0f) * progress
                    if (g > 0f) {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = g), Color.Transparent),
                                center = center,
                                radius = radius * 1.0f,
                            ),
                            radius = radius,
                        )
                    }
                    val inset = stroke / 2f + radius * 0.06f
                    val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                    drawArc(GuardColors.OutlineSoft, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    // Sin progreso no se dibuja: un arco de 0° con extremo redondo pinta un punto suelto.
                    if (progress > 0.005f) {
                        drawArc(ringColor, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    if (!active && progress < 0.01f) {
                        drawArc(GuardColors.Idle.copy(alpha = 0.55f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke * 0.35f))
                    }
                    val discRadius = radius * 0.74f
                    drawCircle(
                        Brush.verticalGradient(listOf(discTop, GuardColors.Surface), startY = center.y - discRadius, endY = center.y + discRadius),
                        radius = discRadius,
                    )
                }
                .clip(CircleShape)
                .toggleable(
                    value = active,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Switch,
                    onValueChange = { onToggle() },
                )
                .semantics {
                    contentDescription = "Modo Guardia"
                    stateDescription = if (active) "Activado" else "Desactivado"
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandShield(
                    Modifier.size(width = heroSize * 0.26f, height = heroSize * 0.30f),
                    color = ringColor,
                    moonColor = discTop,
                    filled = active,
                )
                Spacer(Modifier.height(heroSize * 0.05f))
                Text(
                    if (active) "ACTIVO" else "ACTIVAR",
                    style = SectionLabel,
                    color = if (active) accent else GuardColors.TextDim,
                )
            }
        }
    }
}

/** «Protegiendo tu descanso desde las 23:04 · 2 h 10 min». Se actualiza cada 30 s, sin prisa. */
@Composable
private fun ActiveSince(startedAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val minutes = ((now - startedAt) / 60_000).coerceAtLeast(0)
    val elapsed = when {
        minutes < 1 -> "recién activado"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
    val since = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(startedAt))
    Text(
        "Protegiendo tu descanso desde las $since · $elapsed",
        style = MaterialTheme.typography.bodyMedium,
        color = GuardColors.TextDim,
        textAlign = TextAlign.Center,
    )
}

/** Una línea con el estado real del sistema. Nunca dice «preparado» si falta algo. */
@Composable
private fun SystemBanner(status: SystemStatus, active: Boolean, onOpen: () -> Unit) {
    val errors = status.items.count { it.level == es.carlospb.modoguardia.system.CheckLevel.ERROR }
    val warnings = status.items.count { it.level == es.carlospb.modoguardia.system.CheckLevel.WARNING }
    val (tone, icon, title, body) = when {
        !status.checked -> Banner(Tone.NEUTRAL, Icons.Rounded.HourglassTop, "Comprobando el sistema…", "Permisos, batería y alarma.")
        errors > 0 -> Banner(
            Tone.ERROR, Icons.Rounded.ErrorOutline, "Acción necesaria",
            status.items.first { it.level == es.carlospb.modoguardia.system.CheckLevel.ERROR }.let { "${it.title}: ${it.summary}" } +
                if (errors + warnings > 1) " · y ${errors + warnings - 1} más" else "",
        )
        warnings > 0 -> Banner(
            Tone.WARNING, Icons.Rounded.WarningAmber, if (warnings == 1) "Revisa 1 ajuste" else "Revisa $warnings ajustes",
            status.problems.first().let { "${it.title}: ${it.summary}" },
        )
        else -> Banner(
            Tone.OK, Icons.Rounded.CheckCircle, "Sistema preparado",
            if (active) "Todo comprobado: la alarma puede sonar." else "Todo comprobado. Listo para tu guardia.",
        )
    }
    GuardCard(onClick = onOpen, borderColor = if (tone == Tone.OK || tone == Tone.NEUTRAL) null else tone.color.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tone, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (tone == Tone.OK || tone == Tone.NEUTRAL) GuardColors.Text else tone.color)
                Text(body, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = GuardColors.TextFaint)
        }
    }
}

private data class Banner(val tone: Tone, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val body: String)

@Composable
private fun StatsRow(watch: WatchSummary, onOpenRules: () -> Unit, onOpenCalls: () -> Unit) {
    val calls = when {
        !watch.phoneCalls -> "No"
        watch.callMode == CallMode.ALL -> "Todas"
        else -> watch.callRules.size.toString()
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            watch.appCount.toString(), "Apps vigiladas", Icons.Rounded.Apps,
            if (watch.appCount > 0) Tone.ACTIVE else Tone.NEUTRAL, Modifier.weight(1f), onOpenRules,
        )
        StatTile(
            watch.conversationCount.toString(), "Chats elegidos", Icons.Rounded.Forum,
            if (watch.conversationCount > 0) Tone.ACTIVE else Tone.NEUTRAL, Modifier.weight(1f), onOpenRules,
        )
        StatTile(
            calls, if (watch.callMode == CallMode.SELECTED && watch.phoneCalls) "Contactos de llamada" else "Llamadas", Icons.Rounded.Call,
            if (watch.phoneCallsActive) Tone.ACTIVE else Tone.NEUTRAL, Modifier.weight(1f), onOpenCalls,
        )
    }
}

/** Qué está vigilado, agrupado por app, con los chats y contactos concretos. */
@Composable
private fun WatchListCard(watch: WatchSummary, onOpenApp: (String) -> Unit, onOpenCalls: () -> Unit, onOpenRules: () -> Unit) {
    val empty = watch.apps.isEmpty() && watch.anyAppKeywords.isEmpty() && !watch.phoneCallsActive && !watch.appCalls
    GuardCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
        if (empty) {
            EmptyState(
                icon = Icons.Rounded.ShieldMoon,
                title = "Aún no hay nada vigilado",
                body = "Elige las apps, chats o llamadas que deben despertarte. Todo lo demás seguirá en silencio.",
                actionLabel = "Elegir qué vigilar",
                onAction = onOpenRules,
            )
            return@GuardCard
        }
        var first = true
        watch.apps.forEach { app ->
            if (!first) RowDivider()
            first = false
            AppWatchRow(app) { onOpenApp(app.key) }
        }
        if (watch.anyAppKeywords.isNotEmpty()) {
            if (!first) RowDivider()
            first = false
            WatchGroupRow(
                leading = { IconBadge(Icons.Rounded.TextFields, Tone.INFO, size = 36.dp) },
                title = "Palabras clave",
                summary = "En cualquier app",
                items = watch.anyAppKeywords.flatMap { it.cleanKeywords() }.distinct().map { it to null },
                onClick = onOpenRules,
            )
        }
        if (watch.phoneCalls) {
            if (!first) RowDivider()
            first = false
            WatchGroupRow(
                leading = { IconBadge(Icons.Rounded.Call, if (watch.phoneCallsActive) Tone.ACTIVE else Tone.WARNING, size = 36.dp) },
                title = "Llamadas",
                summary = when {
                    watch.callMode == CallMode.ALL -> "Todas las llamadas"
                    watch.callRules.isEmpty() -> "Ningún contacto elegido"
                    else -> "Solo contactos elegidos"
                },
                items = if (watch.callMode == CallMode.SELECTED) watch.callRules.map { it.label() to null } else emptyList(),
                onClick = onOpenCalls,
            )
        }
        if (watch.appCalls) {
            if (!first) RowDivider()
            WatchGroupRow(
                leading = { IconBadge(Icons.Rounded.PhoneInTalk, Tone.ACTIVE, size = 36.dp) },
                title = "Llamadas de apps",
                summary = "WhatsApp, Teams, Telegram…",
                items = emptyList(),
                onClick = onOpenCalls,
            )
        }
    }
}

@Composable
private fun AppWatchRow(app: WatchedApp, onClick: () -> Unit) {
    val label = rememberAppLabel(app.key, app.storedLabel)
    val summary = appSummary(app)
    val items: List<Pair<String, Boolean?>> = (app.groups + app.people).map { it.name.orEmpty() to (it.type == es.carlospb.modoguardia.domain.RuleType.GROUP) } +
        app.keywordRules.flatMap { r -> r.cleanKeywords().map { "«$it»" to null } }
    WatchGroupRow(leading = { AppIcon(app.key, size = 36.dp) }, title = label, summary = summary, items = items, onClick = onClick)
}

/** «Todas las notificaciones», «2 personas · 1 grupo», «Palabras clave». */
fun appSummary(app: WatchedApp): String {
    if (app.allNotifications) return "Todas las notificaciones"
    val parts = buildList {
        if (app.people.isNotEmpty()) add(if (app.people.size == 1) "1 persona" else "${app.people.size} personas")
        if (app.groups.isNotEmpty()) add(if (app.groups.size == 1) "1 grupo" else "${app.groups.size} grupos")
        if (app.keywordRules.isNotEmpty()) add("palabras clave")
    }
    return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }.ifEmpty { "En pausa" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchGroupRow(
    leading: @Composable () -> Unit,
    title: String,
    summary: String,
    items: List<Pair<String, Boolean?>>,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        ListRow(title = title, subtitle = summary, leading = leading, onClick = onClick)
        if (items.isNotEmpty()) {
            val shown = items.take(MAX_PILLS)
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 50.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                shown.forEach { (name, isGroup) -> NamePill(name, isGroup) }
                if (items.size > MAX_PILLS) NamePill("+${items.size - MAX_PILLS} más", null, faint = true)
            }
        }
    }
}

private const val MAX_PILLS = 6

@Composable
private fun NamePill(name: String, isGroup: Boolean?, faint: Boolean = false) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(GuardColors.SurfaceHigh)
            .padding(start = if (isGroup != null) 4.dp else 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isGroup != null) {
            ConversationAvatar(name, isGroup, size = 20.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = if (faint) GuardColors.TextDim else GuardColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Subtítulo de una regla de llamada: sus números, o cómo se reconoce. */
fun Rule.callSubtitle(): String = when {
    name.isNullOrBlank() -> "Número añadido a mano"
    numbers.isEmpty() -> "Se reconoce por el nombre del contacto"
    else -> numbers.joinToString(" · ")
}
