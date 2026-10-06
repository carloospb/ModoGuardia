package es.carlospb.modoguardia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.domain.CallMode
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.domain.WatchSummary
import es.carlospb.modoguardia.domain.WatchedApp
import es.carlospb.modoguardia.ui.theme.GuardColors
import java.util.UUID

/** Pestaña «Reglas»: qué despierta, por aplicación, más llamadas y palabras clave. */
@Composable
fun RulesScreen(
    settings: GuardSettings,
    rulesVm: RulesViewModel,
    onOpenApp: (String) -> Unit,
    onAddApp: () -> Unit,
    onOpenCalls: () -> Unit,
    onEditKeywords: (String?) -> Unit,
) {
    val rules by rulesVm.rules.collectAsStateWithLifecycle()
    val apps = remember(rules) { WatchSummary.appsOf(rules) }
    val anyAppKeywords = rules.filter { it.type == RuleType.KEYWORDS && it.appKey == null }
    var toDelete by remember { mutableStateOf<Rule?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(ScreenPadding),
    ) {
        ScreenTitle("Qué te despierta", "Solo esto hará sonar la alarma. Todo lo demás seguirá en silencio.")

        SectionTitle("Aplicaciones vigiladas")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            apps.forEach { app -> AppCard(app) { onOpenApp(app.key) } }
            AddCard("Añadir aplicación", "WhatsApp, Telegram, correo, monitorización…", onAddApp)
        }

        SectionTitle("Llamadas")
        CallsCard(settings, onOpenCalls)

        SectionTitle("Palabras clave en cualquier app")
        GuardCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
            if (anyAppKeywords.isEmpty()) {
                Text(
                    "Para avisos que contienen palabras como «URGENTE» o «CAÍDA», vengan de la app que vengan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GuardColors.TextDim,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            anyAppKeywords.forEachIndexed { i, rule ->
                if (i > 0) RowDivider()
                KeywordRuleRow(rule, onToggle = { rulesVm.setEnabled(rule.id, it) }, onEdit = { onEditKeywords(rule.id) }, onDelete = { toDelete = rule })
            }
            RowDivider()
            ListRow(
                title = "Añadir palabras clave",
                leading = { IconBadge(Icons.Rounded.Add, Tone.NEUTRAL, size = 36.dp) },
                onClick = { onEditKeywords(null) },
                titleColor = GuardColors.Active,
                showChevron = false,
            )
        }

        SectionTitle("Qué puede ver Modo Guardia")
        GuardCard {
            Row {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = GuardColors.Info, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Solo lo que las apps ponen en sus notificaciones. No verá nada si el chat está silenciado, si " +
                        "las notificaciones de la app están desactivadas, si tienes ese chat abierto en ese momento o si " +
                        "la app no tiene conexión. Ninguna app de chat deja leer su lista de conversaciones: por eso los " +
                        "chats aparecen para elegirlos cuando mandan su primer aviso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GuardColors.TextDim,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    toDelete?.let { rule ->
        ConfirmDialog(
            title = "¿Eliminar esta regla?",
            text = rule.label(),
            confirm = "Eliminar",
            destructive = true,
            onConfirm = { rulesVm.delete(rule.id) },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun AppCard(app: WatchedApp, onClick: () -> Unit) {
    val label = rememberAppLabel(app.key, app.storedLabel)
    val paused = !app.isActive
    GuardCard(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.key, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                // El estado va en el propio resumen: verde = vigilada; «En pausa» en ámbar si no despertará.
                Text(
                    if (paused) "En pausa: no te despertará" else appSummary(app),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (paused) GuardColors.Warning else GuardColors.Active,
                )
            }
            val faces = (app.people + app.groups).take(3)
            if (!app.allNotifications && faces.isNotEmpty() && !isCompact()) {
                Spacer(Modifier.width(8.dp))
                AvatarStack(faces)
            }
            if (paused) {
                Spacer(Modifier.width(8.dp))
                StatusChip("Pausa", Tone.WARNING)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = GuardColors.TextFaint)
        }
    }
}

/** Hasta tres avatares superpuestos: de un vistazo, quién puede despertarte. */
@Composable
private fun AvatarStack(rules: List<Rule>) {
    Box(Modifier.width((24 + (rules.size - 1) * 14).dp).height(24.dp)) {
        rules.forEachIndexed { i, r ->
            Box(
                Modifier
                    .offset(x = (i * 14).dp)
                    .clip(CircleShape)
                    .background(GuardColors.Surface)
                    .border(2.dp, GuardColors.Surface, CircleShape),
            ) {
                ConversationAvatar(r.name.orEmpty(), r.type == RuleType.GROUP, size = 22.dp)
            }
        }
    }
}

@Composable
private fun AddCard(title: String, subtitle: String, onClick: () -> Unit) {
    GuardCard(onClick = onClick, containerColor = GuardColors.Background, borderColor = GuardColors.Outline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Add, Tone.ACTIVE, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = GuardColors.Active)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
            }
        }
    }
}

@Composable
private fun CallsCard(settings: GuardSettings, onClick: () -> Unit) {
    val watch = settings.watch
    val (summary, tone) = when {
        !settings.wakeOnPhoneCalls -> "Desactivadas" to Tone.NEUTRAL
        settings.callMode == CallMode.ALL -> "Todas las llamadas" to Tone.ACTIVE
        watch.callRules.isEmpty() -> "Ningún contacto elegido: no te despertarán" to Tone.WARNING
        else -> (if (watch.callRules.size == 1) "1 contacto o número" else "${watch.callRules.size} contactos y números") to Tone.ACTIVE
    }
    GuardCard(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Call, if (tone == Tone.NEUTRAL) Tone.NEUTRAL else tone, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Llamadas que activan la alarma", style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (tone) {
                        Tone.WARNING -> GuardColors.Warning
                        Tone.ACTIVE -> GuardColors.Active
                        else -> GuardColors.TextDim
                    },
                )
                if (settings.wakeOnAppCalls) {
                    Text("Más las llamadas de apps (WhatsApp, Teams…)", style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = GuardColors.TextFaint)
        }
    }
}

@Composable
fun KeywordRuleRow(rule: Rule, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    ListRow(
        title = rule.cleanKeywords().joinToString(if (rule.matchAll) " + " else " / "),
        subtitle = (if (rule.matchAll) "Todas las palabras" else "Cualquiera de las palabras") +
            (if (rule.ignoreCase) " · sin distinguir mayúsculas" else " · distingue mayúsculas"),
        leading = { IconBadge(Icons.Rounded.TextFields, if (rule.enabled) Tone.INFO else Tone.NEUTRAL, size = 36.dp) },
        onClick = onEdit,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GuardSwitch(rule.enabled, onToggle, contentDescription = "Activar regla")
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar", tint = GuardColors.TextDim) }
            }
        },
    )
}

@Composable
fun GuardSwitch(checked: Boolean, onChange: (Boolean) -> Unit, contentDescription: String? = null) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = GuardColors.OnActive,
            checkedTrackColor = GuardColors.Active,
            uncheckedThumbColor = GuardColors.TextDim,
            uncheckedTrackColor = GuardColors.SurfaceHighest,
            uncheckedBorderColor = GuardColors.Outline,
        ),
    )
}

/** Campo de texto con el estilo de la app. */
@Composable
fun GuardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = GuardColors.TextFaint) } },
        singleLine = singleLine,
        leadingIcon = leadingIcon,
        keyboardOptions = keyboardOptions,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GuardColors.Active,
            unfocusedBorderColor = GuardColors.Outline,
            focusedLabelColor = GuardColors.Active,
            cursorColor = GuardColors.Active,
            focusedContainerColor = GuardColors.Surface,
            unfocusedContainerColor = GuardColors.Surface,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Editor de una regla de palabras clave: en una app concreta ([presetApp]) o en cualquiera. Las
 * demás reglas (apps, chats, llamadas) se gestionan en sus pantallas.
 */
@Composable
fun KeywordEditorScreen(ruleId: String?, presetApp: String?, rulesVm: RulesViewModel, onDone: () -> Unit) {
    val rules by rulesVm.rules.collectAsStateWithLifecycle()
    val existing = remember(ruleId, rules) { rules.firstOrNull { it.id == ruleId } }
    val pkg = existing?.packageName ?: presetApp
    var keywordsText by rememberSaveable(existing) { mutableStateOf(existing?.keywords?.joinToString(", ").orEmpty()) }
    var matchAll by rememberSaveable(existing) { mutableStateOf(existing?.matchAll ?: false) }
    var ignoreCase by rememberSaveable(existing) { mutableStateOf(existing?.ignoreCase ?: true) }
    val appLabel = pkg?.let { rememberAppLabel(it) }

    val keywords = keywordsText.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
    val draft = Rule(
        id = existing?.id ?: UUID.randomUUID().toString(),
        type = RuleType.KEYWORDS,
        enabled = existing?.enabled ?: true,
        packageName = pkg,
        appLabel = appLabel ?: existing?.appLabel,
        keywords = keywords,
        matchAll = matchAll,
        ignoreCase = ignoreCase,
    )

    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            title = if (existing == null) "Palabras clave" else "Editar palabras clave",
            subtitle = appLabel?.let { "Solo en $it" } ?: "En cualquier app",
            onBack = onDone,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .then(ScreenPadding),
        ) {
            Spacer(Modifier.height(8.dp))
            GuardTextField(
                value = keywordsText,
                onValueChange = { keywordsText = it },
                label = "Palabras (separadas por comas)",
                placeholder = "URGENTE, INCIDENCIA, CRÍTICO, CAÍDA",
                singleLine = false,
            )
            Hint("Se buscan en el título, el texto y los mensajes visibles del aviso. Una palabra también se encuentra dentro de otra («ERROR» en «errores»).")

            SectionTitle("Cuándo coincide")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceCard("Cualquiera de las palabras", "Basta con que aparezca una.", selected = !matchAll) { matchAll = false }
                ChoiceCard("Todas las palabras", "Tienen que aparecer todas en el mismo aviso.", selected = matchAll) { matchAll = true }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                "Ignorar mayúsculas y tildes",
                "«urgente» encuentra «URGENTE»; «critico» encuentra «CRÍTICO».",
                ignoreCase,
            ) { ignoreCase = it }
            Spacer(Modifier.height(24.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            PrimaryButton(
                "Guardar",
                onClick = {
                    rulesVm.save(draft)
                    onDone()
                },
                enabled = draft.isValid(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
