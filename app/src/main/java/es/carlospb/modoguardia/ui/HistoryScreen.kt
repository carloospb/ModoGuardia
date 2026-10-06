package es.carlospb.modoguardia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlert
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.data.HistoryEntity
import es.carlospb.modoguardia.domain.HistoryType
import es.carlospb.modoguardia.ui.theme.GuardColors
import es.carlospb.modoguardia.ui.theme.SectionLabel
import es.carlospb.modoguardia.ui.theme.TabularNumbers
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ALARM_TYPES = setOf(HistoryType.ALARM, HistoryType.MATCH_WHILE_RINGING, HistoryType.ALARM_TEST)

@Composable
fun HistoryScreen(vm: HistoryViewModel) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val shown = if (tab == 0) entries.filter { it.historyType in ALARM_TYPES } else entries
    val byDay = remember(shown) { shown.groupBy { dayKey(it.timestamp) } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.then(ScreenPadding)) {
            ScreenTitle(
                "Historial",
                "Solo en este teléfono: hora, origen y conversación. Nunca el texto de los mensajes.",
                action = if (entries.isNotEmpty()) {
                    {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Limpiar historial", tint = GuardColors.TextDim)
                        }
                    }
                } else {
                    null
                },
            )
            PillTabs(
                listOf("Alarmas · ${entries.count { it.historyType in ALARM_TYPES }}", "Todo · ${entries.size}"),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        title = if (tab == 0) "Sin alarmas todavía" else "Sin registros",
                        body = if (tab == 0) {
                            "Aquí verás cada vez que Modo Guardia te despierte: la hora, la app y la conversación o quién llamaba."
                        } else {
                            "Aquí aparecen las guardias, las alarmas y los avisos importantes del sistema."
                        },
                    )
                }
            }
            byDay.forEach { (day, list) ->
                item(key = "day-$day") {
                    Text(
                        dayLabel(list.first().timestamp).uppercase(),
                        style = SectionLabel,
                        color = GuardColors.TextFaint,
                        modifier = Modifier
                            .padding(top = 18.dp, bottom = 8.dp)
                            .semantics { heading() },
                    )
                }
                items(list, key = { it.id }) { HistoryRow(it, Modifier.animateItem()) }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "¿Limpiar el historial?",
            text = "Se borran todos los registros de este teléfono. No afecta a tus reglas.",
            confirm = "Limpiar",
            destructive = true,
            onConfirm = vm::clear,
            onDismiss = { confirmClear = false },
        )
    }
}

private fun visual(type: HistoryType): Pair<ImageVector, Tone> = when (type) {
    HistoryType.ALARM -> Icons.Rounded.NotificationsActive to Tone.ERROR
    HistoryType.ALARM_TEST -> Icons.Rounded.Alarm to Tone.INFO
    HistoryType.ALARM_STOPPED -> Icons.Rounded.NotificationsOff to Tone.NEUTRAL
    HistoryType.GUARD_STARTED, HistoryType.GUARD_RESTORED -> Icons.Rounded.ShieldMoon to Tone.ACTIVE
    HistoryType.GUARD_ENDED -> Icons.Rounded.PowerSettingsNew to Tone.NEUTRAL
    HistoryType.IGNORED -> Icons.Rounded.VisibilityOff to Tone.NEUTRAL
    HistoryType.GROUP_UNRESOLVED, HistoryType.WARNING -> Icons.Rounded.WarningAmber to Tone.WARNING
    HistoryType.MATCH_WHILE_RINGING -> Icons.Rounded.AddAlert to Tone.WARNING
}

@Composable
private fun HistoryRow(e: HistoryEntity, modifier: Modifier) {
    val (icon, tone) = visual(e.historyType)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(e.timestamp)),
            style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
            color = GuardColors.TextDim,
            modifier = Modifier
                .width(52.dp)
                .padding(top = 8.dp),
        )
        IconBadge(icon, tone, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleSmall)
            // Versiones anteriores guardaban el inicio del mensaje en una segunda línea: no se muestra.
            e.detail?.substringBefore('\n')?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
            }
        }
    }
}

private fun dayKey(t: Long): String = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(t))

private fun dayLabel(t: Long): String {
    val today = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = t }
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        same(today, then) -> "Hoy"
        same(yesterday, then) -> "Ayer"
        else -> SimpleDateFormat("EEEE d 'de' MMMM", Locale.getDefault()).format(Date(t))
    }
}
