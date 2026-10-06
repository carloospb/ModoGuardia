package es.carlospb.modoguardia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.domain.SeenChat
import es.carlospb.modoguardia.domain.TextNormalizer
import es.carlospb.modoguardia.domain.watches
import es.carlospb.modoguardia.ui.theme.GuardColors

/**
 * Elegir conversaciones (personas y grupos) de entre las detectadas en los avisos, de una app
 * ([appKey]) o de todas. Selección múltiple, búsqueda y categorías.
 */
@Composable
fun ConversationPickerScreen(appKey: String?, rulesVm: RulesViewModel, onBack: () -> Unit, onAdded: (Int) -> Unit) {
    val chats by rulesVm.seenChats.collectAsStateWithLifecycle()
    val rules by rulesVm.rules.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var appFilter by rememberSaveable { mutableStateOf(appKey) }
    var manual by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<SeenChat>() }
    val haptics = rememberHaptics()
    val appLabel = appKey?.let { rememberAppLabel(it) }

    val forApp = chats.filter { appFilter == null || it.appKey == appFilter }
    val folded = TextNormalizer.fold(query)
    val visible = forApp.filter { chat ->
        (tab == 0 || (tab == 2) == chat.isGroup) && (folded.isEmpty() || folded in TextNormalizer.fold(chat.name))
    }.distinctBy(::itemKey) // Una clave repetida haría fallar la lista: nunca debe pasar, pero no se arriesga.

    val appsInList = remember(chats) { chats.map { it.appKey }.distinct() }
    val now = remember(chats) { System.currentTimeMillis() }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            title = "Elegir chats",
            subtitle = appLabel?.let { "Conversaciones de $it" } ?: "Conversaciones detectadas",
            onBack = onBack,
            leading = appKey?.let { { AppIcon(it, size = 36.dp) } },
        )
        Column(Modifier.then(ScreenPadding)) {
            GuardTextField(
                value = query,
                onValueChange = { query = it },
                label = "Buscar conversación",
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = GuardColors.TextDim) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.height(10.dp))
            val people = forApp.count { !it.isGroup }
            PillTabs(
                listOf("Todos · ${forApp.size}", "Personas · $people", "Grupos · ${forApp.size - people}"),
                selected = tab,
                onSelect = { tab = it },
            )
        }
        if (appKey == null && appsInList.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = appFilter == null,
                        onClick = { appFilter = null },
                        label = { Text("Todas las apps") },
                        colors = chipColors(),
                    )
                }
                items(appsInList) { key ->
                    FilterChip(
                        selected = appFilter == key,
                        onClick = { appFilter = if (appFilter == key) null else key },
                        label = { Text(rememberAppLabel(key)) },
                        leadingIcon = { AppIcon(key, size = 18.dp) },
                        colors = chipColors(),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        ) {
            if (forApp.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Forum,
                        title = "Todavía no hemos detectado conversaciones" + (appLabel?.let { " de $it" } ?: ""),
                        body = "Cuando recibas una notificación de una conversación, aparecerá aquí para que puedas añadirla " +
                            "a Modo Guardia. Mientras tanto, puedes escribir su nombre.",
                        actionLabel = "Escribir el nombre",
                        onAction = { manual = true },
                    )
                }
            } else if (visible.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "Ninguna conversación coincide",
                        body = "Prueba con otra parte del nombre, o escríbelo si aún no ha mandado ningún aviso.",
                        actionLabel = "Escribir el nombre",
                        onAction = { manual = true },
                    )
                }
            }
            items(visible, key = ::itemKey) { chat ->
                val watched = rules.any { it.watches(chat) }
                val isSelected = chat in selected
                PickerRow(
                    chat = chat,
                    subtitle = buildString {
                        append(if (chat.isGroup) "Grupo" else "Persona")
                        if (appKey == null) append(" · ").append(rememberAppLabel(chat.appKey))
                        append(" · ").append(relativeTime(chat.lastSeen, now))
                    },
                    watched = watched,
                    selected = isSelected || watched,
                    modifier = Modifier.animateItem(),
                ) {
                    haptics.tick()
                    if (isSelected) selected.remove(chat) else selected.add(chat)
                }
            }
            if (forApp.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { manual = true }) {
                            Icon(Icons.Rounded.EditNote, contentDescription = null, tint = GuardColors.Active)
                            Spacer(Modifier.width(8.dp))
                            Text("¿No aparece? Escribe su nombre", color = GuardColors.Active, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            PrimaryButton(
                text = when (selected.size) {
                    0 -> "Marca los chats que deben despertarte"
                    1 -> "Vigilar 1 chat"
                    else -> "Vigilar ${selected.size} chats"
                },
                onClick = {
                    rulesVm.addConversations(selected.toList())
                    haptics.confirm()
                    onAdded(selected.size)
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (manual) {
        val target = appFilter ?: appKey ?: es.carlospb.modoguardia.domain.WhatsApp.PERSONAL
        ManualConversationDialog(
            appLabel = rememberAppLabel(target),
            onDismiss = { manual = false },
            onAdd = { name, isGroup ->
                rulesVm.addManualConversation(target, name, isGroup)
                haptics.confirm()
                onAdded(1)
            },
        )
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = GuardColors.Surface,
    labelColor = GuardColors.TextDim,
    selectedContainerColor = GuardColors.ActiveContainer,
    selectedLabelColor = GuardColors.ActiveSoft,
)

@Composable
private fun PickerRow(chat: SeenChat, subtitle: String, watched: Boolean, selected: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = !watched, role = Role.Checkbox, onClick = onToggle)
            .semantics { stateDescription = if (watched) "Ya vigilado" else if (selected) "Elegido" else "Sin elegir" }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversationAvatar(chat.name, chat.isGroup, size = 44.dp, appKey = chat.appKey)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(chat.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (watched) "Ya vigilado" else subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (watched) GuardColors.Active else GuardColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        SelectionMark(selected, enabled = !watched)
    }
}

/** «ahora», «hace 5 min», «hace 3 h», «hace 2 d». */
fun relativeTime(then: Long, now: Long): String {
    if (then <= 0L) return "escrito a mano"
    val min = (now - then).coerceAtLeast(0) / 60_000
    return when {
        min < 1 -> "ahora"
        min < 60 -> "hace $min min"
        min < 24 * 60 -> "hace ${min / 60} h"
        else -> "hace ${min / (24 * 60)} d"
    }
}

private fun itemKey(chat: SeenChat): String = "${chat.appKey}|${chat.isGroup}|${chat.conversationId ?: TextNormalizer.groupKey(chat.name)}"
