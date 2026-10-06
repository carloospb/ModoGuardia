package es.carlospb.modoguardia.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import es.carlospb.modoguardia.service.GuardNotificationListener
import es.carlospb.modoguardia.system.CheckItem
import es.carlospb.modoguardia.system.CheckLevel
import es.carlospb.modoguardia.system.FixAction
import es.carlospb.modoguardia.system.SystemSettings
import es.carlospb.modoguardia.ui.theme.GuardColors

val CheckLevel.tone: Tone
    get() = when (this) {
        CheckLevel.OK -> Tone.OK
        CheckLevel.WARNING -> Tone.WARNING
        CheckLevel.ERROR -> Tone.ERROR
        CheckLevel.INFO -> Tone.INFO
    }

val CheckLevel.icon: ImageVector
    get() = when (this) {
        CheckLevel.OK -> Icons.Rounded.CheckCircle
        CheckLevel.WARNING -> Icons.Rounded.WarningAmber
        CheckLevel.ERROR -> Icons.Rounded.ErrorOutline
        CheckLevel.INFO -> Icons.Rounded.Info
    }

fun levelColor(level: CheckLevel): Color = level.tone.color

/**
 * Una comprobación del sistema. Si tiene arreglo, toda la fila lo abre: «permitir solucionarlo
 * pulsando directamente sobre esa opción».
 */
@Composable
fun CheckRow(item: CheckItem, onFix: (FixAction) -> Unit, showDetail: Boolean = true) {
    val action = item.action
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (action != null) Modifier.clickable(role = Role.Button, onClickLabel = item.actionLabel) { onFix(action) } else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(item.level.icon, item.level.tone, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Text(item.summary, style = MaterialTheme.typography.bodySmall, color = item.level.tone.color)
            if (showDetail && item.detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
            }
            if (action != null && item.actionLabel != null) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton(item.actionLabel, onClick = { onFix(action) }, contentColor = item.level.tone.color)
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
    dismiss: String = "Cancelar",
    destructive: Boolean = false,
    /** Acción del botón secundario (además de cerrar). Tocar fuera solo cierra. */
    onDismissButton: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GuardColors.SurfaceHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = GuardColors.TextDim) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, style = MaterialTheme.typography.labelLarge, color = if (destructive) GuardColors.Error else GuardColors.Active)
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismissButton?.invoke(); onDismiss() }) {
                Text(dismiss, style = MaterialTheme.typography.labelLarge, color = GuardColors.TextDim)
            }
        },
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
    val callLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        afterResult(granted, Manifest.permission.READ_CALL_LOG, FixAction.APP_DETAILS)
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
                FixAction.CALL_LOG_PERMISSION -> callLogLauncher.launch(Manifest.permission.READ_CALL_LOG)
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
