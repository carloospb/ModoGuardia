package es.carlospb.modoguardia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.carlospb.modoguardia.BuildConfig
import es.carlospb.modoguardia.ui.theme.GuardColors
import es.carlospb.modoguardia.update.Updater

private const val RESUME_NOTE = "Si Modo Guardia está activo, se reanuda solo al terminar de actualizar."

/** Diálogo que ofrece la versión nueva y acompaña la descarga y la instalación. */
@Composable
fun UpdateDialog(state: UpdateUi, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        is UpdateUi.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = GuardColors.SurfaceHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Nueva versión ${state.version.versionName}") },
            text = {
                Column {
                    if (state.version.notes.isNotBlank()) Text(state.version.notes, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tienes la ${BuildConfig.VERSION_NAME}. $RESUME_NOTE",
                        style = MaterialTheme.typography.bodySmall,
                        color = GuardColors.TextDim,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onUpdate) { Text("Actualizar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } },
        )

        is UpdateUi.Downloading -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = GuardColors.SurfaceHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Descargando ${state.version.versionName}") },
            text = {
                Column {
                    if (state.percent >= 0) {
                        LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${state.percent} %")
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Ocultar") } },
        )

        is UpdateUi.Downloaded -> {
            val allowed = Updater.canInstall(context)
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = GuardColors.SurfaceHigh,
                shape = MaterialTheme.shapes.extraLarge,
                title = { Text("Lista para instalar") },
                text = {
                    Text(
                        if (allowed) {
                            "Android te pedirá confirmar la instalación. $RESUME_NOTE"
                        } else {
                            "Android necesita que permitas a Modo Guardia «Instalar apps desconocidas». " +
                                "Actívalo en el ajuste que se abre y vuelve: la instalación sigue sola."
                        },
                    )
                },
                confirmButton = { TextButton(onClick = onUpdate) { Text(if (allowed) "Instalar" else "Abrir ajuste") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } },
            )
        }

        is UpdateUi.Failed -> if (state.version != null) {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = GuardColors.SurfaceHigh,
                shape = MaterialTheme.shapes.extraLarge,
                title = { Text("No se pudo actualizar") },
                text = { Text(state.reason) },
                confirmButton = { TextButton(onClick = onUpdate) { Text("Reintentar") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
            )
        }

        else -> Unit
    }
}

/** Sección de Ajustes: versión instalada, último resultado y comprobación manual. */
@Composable
fun UpdateSection(state: UpdateUi, onCheck: () -> Unit, onUpdate: () -> Unit) {
    SectionTitle("Actualizaciones")
    GuardCard {
        Text("Modo Guardia ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
        val line = when (state) {
            UpdateUi.Idle -> "Se comprueba sola cada vez que abres la app."
            UpdateUi.Checking -> "Comprobando…"
            is UpdateUi.UpToDate -> "Estás al día."
            is UpdateUi.Available -> "Hay una versión nueva: ${state.version.versionName}."
            is UpdateUi.Downloading -> "Descargando ${state.version.versionName}…"
            is UpdateUi.Downloaded -> "${state.version.versionName} descargada, pendiente de instalar."
            is UpdateUi.Failed -> state.reason
        }
        Text(line, style = MaterialTheme.typography.bodySmall, color = if (state is UpdateUi.Available) GuardColors.Active else GuardColors.TextDim)
        Spacer(Modifier.height(12.dp))
        val pending = state is UpdateUi.Available || state is UpdateUi.Downloaded || (state is UpdateUi.Failed && state.version != null)
        val busy = state is UpdateUi.Checking || state is UpdateUi.Downloading
        if (pending) {
            PrimaryButton("Actualizar ahora", onClick = onUpdate, enabled = !busy, modifier = Modifier.fillMaxWidth())
        } else {
            SecondaryButton("Buscar actualizaciones", onClick = onCheck, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
        Hint("Solo consulta carlospb.es y descarga la APK si aceptas. Android siempre pide confirmar la instalación.")
    }
}
