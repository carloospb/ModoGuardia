package es.carlospb.modoguardia.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.domain.WatchedApp
import es.carlospb.modoguardia.domain.WhatsApp
import es.carlospb.modoguardia.ui.theme.GuardColors

/**
 * Una app vigilada: si despierta con cualquier aviso o solo con chats concretos (personas y grupos),
 * y sus palabras clave. Para WhatsApp vale también para WhatsApp Business.
 */
@Composable
fun AppDetailScreen(
    appKey: String,
    rulesVm: RulesViewModel,
    onBack: () -> Unit,
    onPickChats: (String) -> Unit,
    onIdentify: (String) -> Unit,
    onEditKeywords: (ruleId: String?, appKey: String) -> Unit,
) {
    val rules by rulesVm.rules.collectAsStateWithLifecycle()
    val app = remember(rules, appKey) { WatchedApp(appKey, rules.filter { !it.isCall && it.appKey == appKey }) }
    val label = rememberAppLabel(appKey, app.storedLabel)
    val context = LocalContext.current
    val installed = remember(appKey) { AppInfo.isInstalled(context, AppInfo.resolve(context, appKey)) }
    val conversations = app.rules.filter { it.isConversation }
    val keywordRules = app.rules.filter { it.type == RuleType.KEYWORDS }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var manual by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val openApp: () -> Unit = {
        context.packageManager.getLaunchIntentForPackage(appKey)?.let { intent -> runCatching { context.startActivity(intent) } }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            title = label,
            subtitle = if (installed) "Qué avisos de $label te despiertan" else "No está instalada en este teléfono",
            onBack = onBack,
            leading = { AppIcon(appKey, size = 40.dp) },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .then(ScreenPadding),
        ) {
            SectionTitle("Te despierta")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceCard(
                    "Todos los avisos",
                    "Cualquier mensaje o notificación nueva de $label.",
                    selected = app.allNotifications,
                    icon = Icons.Rounded.NotificationsActive,
                ) {
                    if (!app.allNotifications) haptics.confirm()
                    rulesVm.setAllNotifications(appKey, true)
                }
                ChoiceCard(
                    "Solo los chats que elijas",
                    "Personas y grupos concretos. El resto de $label, en silencio.",
                    selected = !app.allNotifications,
                    icon = Icons.Rounded.Forum,
                ) {
                    if (app.allNotifications) haptics.confirm()
                    rulesVm.setAllNotifications(appKey, false)
                }
            }
            AnimatedVisibility(!app.allNotifications && conversations.none { it.enabled } && keywordRules.none { it.enabled }) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    GuardCard(borderColor = GuardColors.Warning.copy(alpha = 0.5f), containerColor = GuardColors.WarningContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = GuardColors.Warning)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Elige al menos un chat. Si no, $label no te despertará.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GuardColors.Warning,
                            )
                        }
                    }
                }
            }

            SectionTitle("Chats elegidos")
            if (app.allNotifications && conversations.isNotEmpty()) {
                Text(
                    "Con «Todos los avisos» cualquier chat ya te despierta. Estos se guardan por si cambias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GuardColors.TextDim,
                )
                Spacer(Modifier.height(10.dp))
            }
            val people = conversations.filter { it.type == RuleType.PERSON }
            val groups = conversations.filter { it.type == RuleType.GROUP }
            PillTabs(
                listOf("Todos · ${conversations.size}", "Personas · ${people.size}", "Grupos · ${groups.size}"),
                selected = tab,
                onSelect = { tab = it },
            )
            Spacer(Modifier.height(10.dp))
            val shown = when (tab) {
                1 -> people
                2 -> groups
                else -> conversations
            }
            GuardCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                if (shown.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.PersonSearch,
                        title = when (tab) {
                            1 -> "Ninguna persona elegida"
                            2 -> "Ningún grupo elegido"
                            else -> "Ningún chat elegido"
                        },
                        body = "Elige de la lista de conversaciones detectadas o escribe el nombre tal como aparece en $label.",
                    )
                }
                shown.forEachIndexed { i, rule ->
                    if (i > 0) RowDivider()
                    ConversationRuleRow(
                        rule,
                        dim = app.allNotifications,
                        onToggle = { rulesVm.setEnabled(rule.id, it) },
                        onDelete = { rulesVm.delete(rule.id) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Elegir chats", onClick = { onPickChats(appKey) }, icon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
                SecondaryButton("Escribir", onClick = { manual = true }, icon = Icons.Rounded.EditNote, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            GuardCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                ListRow(
                    title = "Identificar con un mensaje",
                    subtitle = "¿No aparece en la lista? Alguien escribe en ese chat y Modo Guardia te dice cuál es.",
                    leading = { IconBadge(Icons.Rounded.MarkChatUnread, Tone.ACTIVE, size = 36.dp) },
                    onClick = { onIdentify(appKey) },
                )
                if (WhatsApp.isWhatsApp(appKey)) {
                    RowDivider()
                    ListRow(
                        title = "Compartir el chat desde $label",
                        subtitle = "En el chat: ⋮ → Más → Exportar chat → Sin archivos → «Vigilar en Modo Guardia». " +
                            "Llega su nombre exacto; la conversación no se abre.",
                        leading = { IconBadge(Icons.Rounded.IosShare, Tone.INFO, size = 36.dp) },
                        onClick = if (installed) openApp else null,
                    )
                }
            }
            if (WhatsApp.isWhatsApp(appKey)) {
                Hint(
                    if (appKey == WhatsApp.BUSINESS) {
                        "Solo WhatsApp Business. WhatsApp tiene su propia tarjeta en «Reglas»."
                    } else {
                        "Solo WhatsApp. WhatsApp Business tiene su propia tarjeta en «Reglas»."
                    },
                )
            }

            SectionTitle("Palabras clave en $label")
            GuardCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                keywordRules.forEachIndexed { i, rule ->
                    if (i > 0) RowDivider()
                    KeywordRuleRow(rule, onToggle = { rulesVm.setEnabled(rule.id, it) }, onEdit = { onEditKeywords(rule.id, appKey) }, onDelete = { rulesVm.delete(rule.id) })
                }
                if (keywordRules.isNotEmpty()) RowDivider()
                ListRow(
                    title = "Añadir palabras clave",
                    subtitle = if (keywordRules.isEmpty()) "Despierta solo si el aviso contiene ciertas palabras, venga del chat que venga." else null,
                    leading = { IconBadge(Icons.Rounded.Add, Tone.NEUTRAL, size = 36.dp) },
                    onClick = { onEditKeywords(null, appKey) },
                    titleColor = GuardColors.Active,
                    showChevron = false,
                )
            }

            Spacer(Modifier.height(18.dp))
            TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = GuardColors.Error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dejar de vigilar $label", style = MaterialTheme.typography.labelLarge, color = GuardColors.Error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (manual) {
        ManualConversationDialog(
            appLabel = label,
            onDismiss = { manual = false },
            onAdd = { name, isGroup ->
                rulesVm.addManualConversation(appKey, name, isGroup)
                haptics.confirm()
            },
        )
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = "¿Dejar de vigilar $label?",
            text = "Se quitan sus reglas: todos los avisos, los chats elegidos y sus palabras clave.",
            confirm = "Dejar de vigilar",
            destructive = true,
            onConfirm = {
                rulesVm.removeApp(appKey)
                onBack()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun ConversationRuleRow(rule: Rule, dim: Boolean, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val isGroup = rule.type == RuleType.GROUP
    ListRow(
        title = rule.name.orEmpty(),
        subtitle = (if (isGroup) "Grupo" else "Persona") + if (!rule.enabled) " · en pausa" else "",
        leading = { ConversationAvatar(rule.name.orEmpty(), isGroup, size = 40.dp) },
        titleColor = if (dim || !rule.enabled) GuardColors.TextDim else GuardColors.Text,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GuardSwitch(rule.enabled, onToggle, contentDescription = "Vigilar ${rule.name}")
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Quitar ${rule.name}", tint = GuardColors.TextDim)
                }
            }
        },
    )
}

/** Añadir un chat que aún no ha mandado ningún aviso, escribiendo su nombre. */
@Composable
fun ManualConversationDialog(appLabel: String, onDismiss: () -> Unit, onAdd: (name: String, isGroup: Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GuardColors.SurfaceHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Escribir un chat", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                PillTabs(listOf("Persona", "Grupo"), selected = kind, onSelect = { kind = it })
                Spacer(Modifier.height(14.dp))
                GuardTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = if (kind == 0) "Nombre del contacto" else "Nombre del grupo",
                    placeholder = if (kind == 0) "Carlos" else "Soporte técnico",
                )
                Hint(
                    "Escríbelo como aparece en las notificaciones de $appLabel. Mayúsculas, tildes y emojis no importan. " +
                        "Si el chat ya te ha escrito, es más seguro elegirlo de la lista.",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(name, kind == 1)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text("Añadir", style = MaterialTheme.typography.labelLarge, color = if (name.isNotBlank()) GuardColors.Active else GuardColors.TextFaint) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", style = MaterialTheme.typography.labelLarge, color = GuardColors.TextDim) }
        },
    )
}
