package es.carlospb.modoguardia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.carlospb.modoguardia.BuildConfig
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.data.SoundSource
import es.carlospb.modoguardia.data.VolumeMode
import es.carlospb.modoguardia.ui.theme.GuardColors

@Composable
fun SettingsScreen(settings: GuardSettings, vm: MainViewModel, onRerunOnboarding: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(ScreenPadding),
    ) {
        AlarmSettings(settings, vm)

        SectionTitle("No molestar, silencio y batería")
        GuardCard {
            Text(
                "• La alarma usa el canal de ALARMA de Android: suena aunque el teléfono esté en silencio o vibración.\n" +
                    "• «No molestar» deja pasar las alarmas por defecto; en «silencio total», no. Modo Guardia lo comprueba en Estado.\n" +
                    "• El volumen elegido solo se aplica mientras suena la alarma; al detenerla vuelve a tu volumen de alarma.\n" +
                    "• Con optimización de batería, Android puede retrasar la app de madrugada. Recomendado: «Sin restricciones».",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionTitle("Privacidad")
        GuardCard {
            Text(
                "Modo Guardia analiza las notificaciones localmente en tu dispositivo únicamente para comprobar si cumplen las reglas configuradas.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "La app no tiene permiso de Internet: nada sale del teléfono. No guarda mensajes; el historial guarda solo " +
                    "lo imprescindible y puedes borrarlo. El diagnóstico vive solo en memoria. No hay analítica.",
                style = MaterialTheme.typography.bodySmall,
                color = GuardColors.TextDim,
            )
        }

        SectionTitle("Asistente")
        OutlinedButton(onClick = onRerunOnboarding, modifier = Modifier.fillMaxWidth()) { Text("Repetir configuración guiada") }

        SectionTitle("Acerca de")
        Text("Modo Guardia ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        Text("© ${BuildConfig.AUTHOR}", style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
        Spacer(Modifier.height(24.dp))
    }
}

/** Volumen, vibración y sonido. Reutilizado en el asistente inicial. */
@Composable
fun AlarmSettings(settings: GuardSettings, vm: MainViewModel) {
    SectionTitle("Volumen de alarma")
    GuardCard {
        RadioRow("Volumen máximo", "Recomendado para dormir.", settings.volumeMode == VolumeMode.MAX) { vm.setVolumeMode(VolumeMode.MAX) }
        RadioRow("Volumen configurable", null, settings.volumeMode == VolumeMode.CUSTOM) { vm.setVolumeMode(VolumeMode.CUSTOM) }
        if (settings.volumeMode == VolumeMode.CUSTOM) {
            var value by remember(settings.customVolumePercent) { mutableFloatStateOf(settings.customVolumePercent.toFloat()) }
            Text("${value.toInt()} %", color = GuardColors.Active)
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { vm.setCustomVolume(value.toInt()) },
                valueRange = 10f..100f,
            )
        }
        RadioRow(
            "Usar volumen actual de alarma",
            "El que tengas puesto en el teléfono. Si está a 0, se sube al 60 % mientras suena.",
            settings.volumeMode == VolumeMode.CURRENT,
        ) { vm.setVolumeMode(VolumeMode.CURRENT) }
    }

    SectionTitle("Vibración")
    GuardCard {
        SwitchRow("☑ Vibración", "Vibrar → pausa → vibrar → pausa, mientras suene la alarma.", settings.vibrate) { vm.setVibrate(it) }
    }

    SectionTitle("Sonido")
    GuardCard {
        RadioRow("Sirena de Modo Guardia", "Incluida en la app: siempre disponible.", settings.soundSource == SoundSource.SIREN) { vm.setSound(SoundSource.SIREN) }
        RadioRow("Tono de alarma del sistema", "Si es «Ninguno» o falla, suena la sirena.", settings.soundSource == SoundSource.SYSTEM_ALARM) { vm.setSound(SoundSource.SYSTEM_ALARM) }
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim) }
        }
    }
}
