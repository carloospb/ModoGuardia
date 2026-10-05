package es.carlospb.modoguardia.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.system.FixAction
import es.carlospb.modoguardia.ui.theme.GuardColors
import java.util.UUID

@Composable
fun RulesScreen(
    settings: GuardSettings,
    rulesVm: RulesViewModel,
    mainVm: MainViewModel,
    onFix: (FixAction) -> Unit,
    onEdit: (String?) -> Unit,
    onPickChats: () -> Unit,
) {
    val rules by rulesVm.rules.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Rule?>(null) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(ScreenPadding),
    ) {
        SectionTitle("¿Qué debe despertarme?")
        if (rules.isEmpty()) {
            Text("Todavía no hay reglas. Crea la primera: por app, por WhatsApp, por grupo o chat, o por palabras.", color = GuardColors.TextDim)
        }
        rules.forEach { rule ->
            RuleCard(
                rule = rule,
                onToggle = { rulesVm.setEnabled(rule.id, it) },
                onEdit = { onEdit(rule.id) },
                onDelete = { toDelete = rule },
            )
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = onPickChats, modifier = Modifier.fillMaxWidth()) { Text("Elegir chats de WhatsApp") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth()) { Text("＋ Otra regla (app, palabras…)") }

        SectionTitle("Despertar con llamadas")
        GuardCard {
            SwitchRow(
                title = "Cualquier llamada entrante",
                subtitle = "Llamadas telefónicas. Suena la alarma además del timbre; la llamada funciona con normalidad. Se detiene al contestar.",
                checked = settings.wakeOnPhoneCalls,
            ) { on ->
                mainVm.setWakeOnPhoneCalls(on)
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                if (on && !granted) onFix(FixAction.PHONE_PERMISSION)
            }
            SwitchRow(
                title = "Llamadas de apps",
                subtitle = "WhatsApp, Teams, Telegram… Se detectan por su notificación de llamada entrante.",
                checked = settings.wakeOnAppCalls,
            ) { mainVm.setWakeOnAppCalls(it) }
            Text(
                "Modo Guardia no lee el número ni contesta, rechaza o silencia llamadas.",
                style = MaterialTheme.typography.bodySmall,
                color = GuardColors.TextDim,
            )
        }

        SectionTitle("Límites de WhatsApp")
        GuardCard {
            Text(
                "Modo Guardia solo ve lo que WhatsApp pone en su notificación. No verá nada si:\n" +
                    "• el grupo o chat está silenciado en WhatsApp;\n" +
                    "• las notificaciones de WhatsApp están desactivadas en Android;\n" +
                    "• tienes el chat abierto en ese momento;\n" +
                    "• WhatsApp no puede conectarse (sin datos, ahorro extremo).\n" +
                    "Usa Estado → Diagnóstico para ver cómo llegan los avisos en tu teléfono.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    toDelete?.let { rule ->
        ConfirmDialog(
            title = "¿Eliminar regla?",
            text = rule.label(),
            confirm = "Eliminar",
            onConfirm = { rulesVm.delete(rule.id) },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun RuleCard(rule: Rule, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    GuardCard(modifier = Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ruleIcon(rule.type), contentDescription = null, tint = if (rule.enabled) GuardColors.Active else GuardColors.TextDim)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rule.label(), fontWeight = FontWeight.SemiBold)
                Text(ruleSubtitle(rule), style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
                if (!rule.isValid()) Text("Regla incompleta: no se aplicará", color = GuardColors.Error, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
        }
    }
}

private fun ruleIcon(type: RuleType) = when (type) {
    RuleType.APP -> Icons.Filled.Apps
    RuleType.WHATSAPP_ANY -> Icons.AutoMirrored.Filled.Chat
    RuleType.WHATSAPP_GROUP -> Icons.Filled.Groups
    RuleType.WHATSAPP_CHAT -> Icons.Filled.Person
    RuleType.KEYWORDS -> Icons.Filled.TextFields
}

private fun ruleSubtitle(rule: Rule): String = when (rule.type) {
    RuleType.APP -> "App · ${rule.packageName}"
    RuleType.WHATSAPP_ANY -> "Cualquier notificación nueva de WhatsApp"
    RuleType.WHATSAPP_GROUP -> "Grupo de WhatsApp"
    RuleType.WHATSAPP_CHAT -> "Chat de WhatsApp"
    RuleType.KEYWORDS -> (rule.appLabel ?: "Cualquier app") +
        (if (rule.matchAll) " · todas las palabras" else " · cualquiera de las palabras") +
        (if (rule.ignoreCase) " · sin distinguir mayúsculas" else " · distingue mayúsculas")
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Crear / editar regla. [ruleId] null = nueva. */
@Composable
fun RuleEditorScreen(ruleId: String?, rulesVm: RulesViewModel, onPickChats: () -> Unit, onDone: () -> Unit) {
    val rules by rulesVm.rules.collectAsStateWithLifecycle()
    val existing = remember(ruleId, rules) { rules.firstOrNull { it.id == ruleId } }

    var type by rememberSaveable(existing) { mutableStateOf(existing?.type ?: RuleType.WHATSAPP_GROUP) }
    var pkg by rememberSaveable(existing) { mutableStateOf(existing?.packageName) }
    var appLabel by rememberSaveable(existing) { mutableStateOf(existing?.appLabel) }
    var group by rememberSaveable(existing) { mutableStateOf(existing?.groupName.orEmpty()) }
    var keywordsText by rememberSaveable(existing) { mutableStateOf(existing?.keywords?.joinToString(", ").orEmpty()) }
    var matchAll by rememberSaveable(existing) { mutableStateOf(existing?.matchAll ?: false) }
    var ignoreCase by rememberSaveable(existing) { mutableStateOf(existing?.ignoreCase ?: true) }
    var picking by remember { mutableStateOf(false) }

    val keywords = keywordsText.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
    val draft = Rule(
        id = existing?.id ?: "",
        type = type,
        enabled = existing?.enabled ?: true,
        packageName = if (type == RuleType.APP || type == RuleType.KEYWORDS) pkg else null,
        appLabel = if (type == RuleType.APP || type == RuleType.KEYWORDS) appLabel else null,
        groupName = if (type == RuleType.WHATSAPP_GROUP || type == RuleType.WHATSAPP_CHAT) group.trim() else null,
        keywords = if (type == RuleType.KEYWORDS) keywords else emptyList(),
        matchAll = matchAll,
        ignoreCase = ignoreCase,
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(ScreenPadding),
    ) {
        SectionTitle(if (existing == null) "Nueva regla" else "Editar regla")
        Text("Tipo", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            FilterChip(selected = type == RuleType.WHATSAPP_GROUP, onClick = { type = RuleType.WHATSAPP_GROUP }, label = { Text("Grupo WhatsApp") })
            FilterChip(selected = type == RuleType.WHATSAPP_CHAT, onClick = { type = RuleType.WHATSAPP_CHAT }, label = { Text("Chat WhatsApp") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            FilterChip(selected = type == RuleType.WHATSAPP_ANY, onClick = { type = RuleType.WHATSAPP_ANY }, label = { Text("Cualquier WhatsApp") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == RuleType.APP, onClick = { type = RuleType.APP }, label = { Text("Aplicación") })
            FilterChip(selected = type == RuleType.KEYWORDS, onClick = { type = RuleType.KEYWORDS }, label = { Text("Palabras clave") })
        }
        Spacer(Modifier.height(16.dp))

        when (type) {
            RuleType.WHATSAPP_GROUP, RuleType.WHATSAPP_CHAT -> {
                if (existing == null) {
                    Button(onClick = onPickChats, modifier = Modifier.fillMaxWidth()) { Text("Elegir de mis chats de WhatsApp") }
                    Hint("Recomendado: el nombre sale de la propia notificación y coincide siempre. O escríbelo a mano:")
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text(if (type == RuleType.WHATSAPP_GROUP) "Nombre del grupo" else "Nombre del contacto") },
                    placeholder = { Text(if (type == RuleType.WHATSAPP_GROUP) "GUARDIAS SOPORTE" else "Ana López") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint(
                    "Escríbelo como aparece en WhatsApp (no importan mayúsculas ni tildes). Se compara con " +
                        "el nombre de conversación que WhatsApp pone en la notificación; si una notificación " +
                        "no lo trae, no se adivina: queda en el historial como «grupo no identificado». " +
                        "El chat NO debe estar silenciado en WhatsApp." +
                        (if (type == RuleType.WHATSAPP_CHAT) " En un chat individual se usa el nombre con el que tienes guardado el contacto." else ""),
                )
                WhatsAppInstalled(rulesVm)
            }
            RuleType.WHATSAPP_ANY -> {
                Hint("Cualquier notificación NUEVA de WhatsApp o WhatsApp Business hará sonar la alarma. Los resúmenes y las re-publicaciones de mensajes ya vistos no.")
                WhatsAppInstalled(rulesVm)
            }
            RuleType.APP -> {
                AppSelector(appLabel, pkg, allowAny = false) { picking = true }
                Hint("Cualquier notificación nueva de esta app hará sonar la alarma. Se identifica por su nombre de paquete real.")
            }
            RuleType.KEYWORDS -> {
                AppSelector(appLabel, pkg, allowAny = true) { picking = true }
                if (pkg != null) {
                    TextButton(onClick = { pkg = null; appLabel = null }) { Text("Usar cualquier aplicación") }
                }
                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { keywordsText = it },
                    label = { Text("Palabras (separadas por comas)") },
                    placeholder = { Text("URGENTE, INCIDENCIA, CRÍTICO, CAÍDA") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !matchAll, onClick = { matchAll = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text("Cualquiera")
                    }
                    SegmentedButton(selected = matchAll, onClick = { matchAll = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text("Todas")
                    }
                }
                SwitchRow("Ignorar mayúsculas y tildes", "«urgente» encuentra «URGENTE»; «critico» encuentra «CRÍTICO».", ignoreCase) { ignoreCase = it }
                Hint("Se busca en título, texto y mensajes visibles de la notificación. Una palabra también se encuentra dentro de otra («ERROR» en «errores»).")
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                rulesVm.save(draft.copy(id = existing?.id ?: UUID.randomUUID().toString()))
                onDone()
            },
            enabled = draft.isValid(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar regla") }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        Spacer(Modifier.height(24.dp))
    }

    if (picking) {
        AppPickerDialog(
            rulesVm = rulesVm,
            onPick = { app ->
                pkg = app.packageName
                appLabel = app.label
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun WhatsAppInstalled(rulesVm: RulesViewModel) {
    val installed = remember { rulesVm.installedWhatsApp() }
    val text = if (installed.isEmpty()) {
        "⚠️ No se encuentra WhatsApp instalado en este teléfono."
    } else {
        "Instalado: " + installed.joinToString { it.label }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = if (installed.isEmpty()) GuardColors.Warning else GuardColors.Active, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AppSelector(label: String?, pkg: String?, allowAny: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(label ?: if (allowAny) "Cualquier aplicación (tocar para elegir una)" else "Elegir aplicación…")
            pkg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim) }
        }
    }
}

@Composable
private fun AppPickerDialog(rulesVm: RulesViewModel, onPick: (InstalledApp) -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { rulesVm.loadApps() }
    val apps by rulesVm.apps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir aplicación") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val list = apps
                if (list == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else {
                    val filtered = list.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(app) }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
