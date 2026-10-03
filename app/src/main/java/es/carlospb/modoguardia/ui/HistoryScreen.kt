package es.carlospb.modoguardia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.data.HistoryEntity
import es.carlospb.modoguardia.domain.HistoryType
import es.carlospb.modoguardia.ui.theme.GuardColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: HistoryViewModel) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().then(ScreenPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Historial de guardia", Modifier.weight(1f))
            if (entries.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Limpiar") }
        }
        Text(
            "Solo en este teléfono. Se guarda la hora, el tipo, la app y la regla; de las alarmas, además, el inicio del mensaje que las provocó.",
            style = MaterialTheme.typography.bodySmall,
            color = GuardColors.TextDim,
        )
        if (entries.isEmpty()) {
            Text("Sin registros.", color = GuardColors.TextDim, modifier = Modifier.padding(top = 24.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.id }) { HistoryRow(it) }
        }
    }

    if (confirmClear) {
        ConfirmDialog("¿Limpiar historial?", "Se borrarán todos los registros.", "Limpiar", vm::clear) { confirmClear = false }
    }
}

@Composable
private fun HistoryRow(e: HistoryEntity) {
    val (emoji, color) = when (e.historyType) {
        HistoryType.ALARM -> "🚨" to GuardColors.Alarm
        HistoryType.ALARM_TEST -> "🔔" to GuardColors.Info
        HistoryType.ALARM_STOPPED -> "✋" to GuardColors.Text
        HistoryType.GUARD_STARTED, HistoryType.GUARD_RESTORED -> "🟢" to GuardColors.Active
        HistoryType.GUARD_ENDED -> "⚫" to GuardColors.TextDim
        HistoryType.IGNORED -> "·" to GuardColors.TextDim
        HistoryType.GROUP_UNRESOLVED, HistoryType.WARNING -> "⚠️" to GuardColors.Warning
        HistoryType.MATCH_WHILE_RINGING -> "➕" to GuardColors.Warning
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(formatTime(e.timestamp), fontFamily = FontFamily.Monospace, color = GuardColors.TextDim, modifier = Modifier.width(92.dp))
            Text(emoji)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, color = color, fontWeight = FontWeight.SemiBold)
                e.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    HorizontalDivider(color = GuardColors.Outline)
}

private fun formatTime(t: Long): String {
    val today = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = t }
    val sameDay = today.get(Calendar.YEAR) == then.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "dd/MM HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(t))
}
