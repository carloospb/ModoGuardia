package es.carlospb.modoguardia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.domain.SeenChat
import es.carlospb.modoguardia.domain.TextNormalizer
import es.carlospb.modoguardia.ui.theme.GuardColors

private enum class ChatFilter(val label: String) { ALL("Todos"), GROUPS("Grupos"), CHATS("Chats") }

/** Verde de WhatsApp: solo aquí, para que la lista se reconozca a primera vista. */
private val WaGreen = Color(0xFF25D366)

private val avatarColors = listOf(
    Color(0xFF00A884), Color(0xFF53BDEB), Color(0xFFFFB020), Color(0xFFE26AB6),
    Color(0xFF7F66FF), Color(0xFFFF7A59), Color(0xFF02A698), Color(0xFF5AA9FF),
)

/**
 * Elegir chats de WhatsApp (grupos e individuales) para vigilarlos. La lista sale de los avisos
 * que ya han llegado al teléfono: WhatsApp no deja a otras apps leer su lista de chats.
 */
@Composable
fun WhatsAppChatsScreen(rulesVm: RulesViewModel, onDone: () -> Unit) {
    val chats by rulesVm.seenChats.collectAsStateWithLifecycle()
    val rules by rulesVm.rules.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ChatFilter.ALL) }
    val selected = remember { mutableStateListOf<SeenChat>() }
    var confirmClear by remember { mutableStateOf(false) }

    val folded = TextNormalizer.fold(query)
    val visible = chats.filter { chat ->
        (filter == ChatFilter.ALL || (filter == ChatFilter.GROUPS) == chat.isGroup) &&
            (folded.isEmpty() || folded in TextNormalizer.fold(chat.name))
    }
    val now = System.currentTimeMillis()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.then(ScreenPadding)) {
            SectionTitle("Chats de WhatsApp")
            Text(
                "Marca los chats que deben despertarte. Aparecen los grupos y chats de los que te ha llegado algún aviso.",
                style = MaterialTheme.typography.bodySmall,
                color = GuardColors.TextDim,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                ChatFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
        }
        HorizontalDivider(color = GuardColors.Outline)

        if (chats.isEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Todavía no ha llegado ningún aviso de WhatsApp", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Cada grupo o chat aparece aquí en cuanto WhatsApp muestra una notificación suya con el acceso " +
                        "a notificaciones concedido. Pide que escriban en el grupo, o escribe tú desde otro teléfono.\n\n" +
                        "WhatsApp no permite a otras apps leer su lista de chats, por eso no salen todos de golpe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GuardColors.TextDim,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(visible, key = { "${it.isGroup}|${it.name}" }) { chat ->
                    val watched = rules.any { it.watches(chat) }
                    val checked = watched || chat in selected
                    ChatRow(
                        chat = chat,
                        subtitle = (if (chat.isGroup) "Grupo" else "Chat") + " · último aviso " + relativeTime(chat.lastSeen, now),
                        checked = checked,
                        watched = watched,
                    ) {
                        if (chat in selected) selected.remove(chat) else selected.add(chat)
                    }
                }
                if (visible.isEmpty()) {
                    item { Text("Ningún chat coincide.", color = GuardColors.TextDim, modifier = Modifier.padding(20.dp)) }
                }
                item {
                    TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("Borrar esta lista", color = GuardColors.TextDim)
                    }
                }
            }
        }

        HorizontalDivider(color = GuardColors.Outline)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDone) { Text("Cancelar") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    rulesVm.addChatRules(selected.toList())
                    onDone()
                },
                enabled = selected.isNotEmpty(),
            ) {
                Text(
                    when (selected.size) {
                        0 -> "Elige chats"
                        1 -> "Vigilar 1 chat"
                        else -> "Vigilar ${selected.size} chats"
                    },
                )
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "¿Borrar la lista de chats?",
            text = "Solo se borran los nombres guardados para elegir. Tus reglas no cambian. La lista se irá llenando de nuevo con los próximos avisos.",
            confirm = "Borrar",
            onConfirm = {
                selected.clear()
                rulesVm.clearSeenChats()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun ChatRow(chat: SeenChat, subtitle: String, checked: Boolean, watched: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !watched, onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(chat)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(chat.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (watched) "Ya vigilado" else subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (watched) WaGreen else GuardColors.TextDim,
                maxLines = 1,
            )
        }
        Checkbox(checked = checked, onCheckedChange = if (watched) null else { _ -> onToggle() }, enabled = !watched)
    }
}

@Composable
private fun Avatar(chat: SeenChat) {
    val color = avatarColors[Math.floorMod(TextNormalizer.groupKey(chat.name).hashCode(), avatarColors.size)]
    Box(
        Modifier
            .size(46.dp)
            .background(color.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (chat.isGroup) {
            Icon(Icons.Filled.Groups, contentDescription = "Grupo", tint = color)
        } else {
            val initial = chat.name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
            Text(initial, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

private fun relativeTime(then: Long, now: Long): String {
    val min = (now - then).coerceAtLeast(0) / 60_000
    return when {
        min < 1 -> "ahora"
        min < 60 -> "hace $min min"
        min < 24 * 60 -> "hace ${min / 60} h"
        else -> "hace ${min / (24 * 60)} d"
    }
}
