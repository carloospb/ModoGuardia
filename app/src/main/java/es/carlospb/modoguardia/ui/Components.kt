package es.carlospb.modoguardia.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import es.carlospb.modoguardia.service.GuardNotificationListener
import es.carlospb.modoguardia.system.CheckItem
import es.carlospb.modoguardia.system.CheckLevel
import es.carlospb.modoguardia.system.FixAction
import es.carlospb.modoguardia.system.SystemSettings
import es.carlospb.modoguardia.ui.theme.GuardColors
import es.carlospb.modoguardia.ui.theme.SectionLabel

fun levelColor(level: CheckLevel): Color = when (level) {
    CheckLevel.OK -> GuardColors.Active
    CheckLevel.WARNING -> GuardColors.Warning
    CheckLevel.ERROR -> GuardColors.Error
    CheckLevel.INFO -> GuardColors.TextDim
}

fun levelEmoji(level: CheckLevel): String = when (level) {
    CheckLevel.OK -> "🟢"
    CheckLevel.WARNING -> "🟠"
    CheckLevel.ERROR -> "🔴"
    CheckLevel.INFO -> "⚪"
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = SectionLabel, color = GuardColors.TextDim, modifier = modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
fun Dot(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}

@Composable
fun GuardCard(modifier: Modifier = Modifier, borderColor: Color? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GuardColors.Surface),
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun CheckRow(item: CheckItem, onFix: (FixAction) -> Unit, showDetail: Boolean = true) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(levelColor(item.level))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold)
                Text(item.summary, color = levelColor(item.level), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (showDetail && item.detail != null) {
            Text(
                item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = GuardColors.TextDim,
                modifier = Modifier.padding(start = 22.dp, top = 4.dp),
            )
        }
        if (item.action != null && item.actionLabel != null) {
            OutlinedButton(onClick = { onFix(item.action) }, modifier = Modifier.padding(start = 22.dp, top = 6.dp)) {
                Text(item.actionLabel)
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Resuelve cada FixAction: permisos de ejecución con su diálogo del sistema (y, si el usuario los
 * denegó para siempre, la pantalla de la app en Ajustes) o la pantalla de Ajustes que corresponda.
 */
@Composable
fun rememberFixHandler(vm: MainViewModel): (FixAction) -> Unit {
    val context = LocalContext.current
    val activity = context as? Activity

    fun afterResult(granted: Boolean, permission: String, fallback: FixAction) {
        if (!granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            // Denegado "para siempre": Android ya no muestra el diálogo, hay que ir a Ajustes.
            SystemSettings.open(context, fallback)
        }
        vm.refreshStatus()
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            afterResult(granted, Manifest.permission.POST_NOTIFICATIONS, FixAction.APP_NOTIFICATION_SETTINGS)
        }
    }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        afterResult(granted, Manifest.permission.READ_PHONE_STATE, FixAction.APP_DETAILS)
    }

    return remember(context, vm) {
        { action ->
            when (action) {
                FixAction.POST_NOTIFICATIONS -> {
                    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        SystemSettings.open(context, FixAction.APP_NOTIFICATION_SETTINGS)
                    }
                }
                FixAction.PHONE_PERMISSION -> phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                FixAction.RESTART_GUARD_SERVICE -> vm.restartGuardService()
                FixAction.REBIND_LISTENER -> {
                    GuardNotificationListener.requestRebind(context)
                    vm.refreshStatus()
                }
                else -> SystemSettings.open(context, action)
            }
        }
    }
}

@Composable
fun WarningBanner(problems: List<CheckItem>, onReview: () -> Unit) {
    GuardCard(borderColor = GuardColors.Warning) {
        Text("⚠️ MODO GUARDIA PUEDE NO FUNCIONAR CORRECTAMENTE", color = GuardColors.Warning, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        problems.forEach {
            Text("${levelEmoji(it.level)} ${it.title}: ${it.summary}", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.size(8.dp))
        OutlinedButton(onClick = onReview) { Text("Revisar y corregir") }
    }
}

@Composable
fun VerticalSpace(dp: Int) = Spacer(Modifier.size(dp.dp))

val ScreenPadding = Modifier.padding(horizontal = 20.dp)
