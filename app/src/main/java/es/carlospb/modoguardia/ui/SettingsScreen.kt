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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.BuildConfig
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.data.SoundSource
import es.carlospb.modoguardia.data.VolumeMode
import es.carlospb.modoguardia.system.SystemStatus
import es.carlospb.modoguardia.ui.theme.GuardColors
import es.carlospb.modoguardia.ui.theme.TabularNumbers

@Composable
fun SettingsScreen(
    settings: GuardSettings,
    status: SystemStatus,
    vm: MainViewModel,
    rulesVm: RulesViewModel,
    updateVm: UpdateViewModel,
    onOpenStatus: () -> Unit,
    onRerunOnboarding: () -> Unit,
) {
    val update by updateVm.state.collectAsStateWithLifecycle()
    var confirmChats by remember { mutableStateOf(false) }
    var confirmHistory by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(ScreenPadding),
    ) {
        ScreenTitle("Ajustes")

        AlarmSettings(settings, vm)

        SectionTitle("Probar alarma")
        TestAlarmCard(vm)

        SectionTitle("Sistema")
        GuardCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
            val (chipText, chipTone) = when {
                !status.checked -> "Comprobando" to Tone.NEUTRAL
                status.hasErrors -> "Acción necesaria" to Tone.ERROR
                status.problems.isNotEmpty() -> "Revisar" to Tone.WARNING
                else -> "Preparado" to Tone.OK
            }
            ListRow(
                title = "Estado del sistema",
                subtitle = "Permisos, batería, No molestar y servicio de guardia",
                leading = { IconBadge(Icons.Rounded.MonitorHeart, chipTone, size = 40.dp) },
                trailing = { StatusChip(chipText, chipTone) },
                onClick = onOpenStatus,
            )
            RowDivider()
            ListRow(
                title = "Repetir la configuración guiada",
                subtitle = "Permisos, qué te despierta, llamadas y alarma, paso a paso",
                leading = { IconBadge(Icons.Rounded.Replay, Tone.NEUTRAL, size = 40.dp) },
                onClick = onRerunOnboarding,
            )
        }

        SectionTitle("Privacidad")
        GuardCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
            ToggleRow(
                title = "Recordar conversaciones detectadas",
                subtitle = "Guarda solo el nombre de cada chat que manda un aviso, para que puedas elegirlo. Nunca los mensajes.",
                checked = settings.learnConversations,
                leading = { IconBadge(Icons.Rounded.Tune, Tone.NEUTRAL, size = 40.dp) },
            ) { vm.setLearnConversations(it) }
            RowDivider()
            ListRow(
                title = "Borrar conversaciones detectadas",
                subtitle = "Tus reglas no cambian",
                leading = { IconBadge(Icons.Rounded.AutoDelete, Tone.NEUTRAL, size = 40.dp) },
                onClick = { confirmChats = true },
                showChevron = false,
            )
            RowDivider()
            ListRow(
                title = "Borrar historial",
                leading = { IconBadge(Icons.Rounded.DeleteSweep, Tone.NEUTRAL, size = 40.dp) },
                onClick = { confirmHistory = true },
                showChevron = false,
            )
        }
        Hint(
            "Todo se analiza en este teléfono. Ningún dato sale de él: Internet solo se usa para buscar " +
                "actualizaciones en carlospb.es. Lo guardado (reglas, nombres de chats, historial) vive en el " +
                "almacenamiento privado de la app, que Android cifra con el bloqueo de tu pantalla. No hay analítica.",
        )

        UpdateSection(update, onCheck = { updateVm.check(manual = true) }, onUpdate = updateVm::downloadAndInstall)

        SectionTitle("Acerca de")
        GuardCard {
            Text("Modo Guardia ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
            Text("© ${BuildConfig.AUTHOR}", style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
            Spacer(Modifier.height(8.dp))
            Text("Tipografía Manrope · SIL Open Font License 1.1", style = MaterialTheme.typography.bodySmall, color = GuardColors.TextFaint)
        }
        Spacer(Modifier.height(28.dp))
    }

    if (confirmChats) {
        ConfirmDialog(
            title = "¿Borrar las conversaciones detectadas?",
            text = "Se borra la lista de chats para elegir. Tus reglas siguen igual y la lista se irá llenando con los próximos avisos.",
            confirm = "Borrar",
            destructive = true,
            onConfirm = {
                rulesVm.clearSeenChats()
                vm.showMessage("Conversaciones detectadas borradas")
            },
            onDismiss = { confirmChats = false },
        )
    }
    if (confirmHistory) {
        ConfirmDialog(
            title = "¿Borrar el historial?",
            text = "Se borran todos los registros de este teléfono.",
            confirm = "Borrar",
            destructive = true,
            onConfirm = { vm.clearHistory() },
            onDismiss = { confirmHistory = false },
        )
    }
}

/** Volumen, vibración y sonido. Reutilizado en la configuración guiada. */
@Composable
fun AlarmSettings(settings: GuardSettings, vm: MainViewModel) {
    SectionTitle("Volumen de la alarma")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceCard("Volumen máximo", "Recomendado para dormir.", settings.volumeMode == VolumeMode.MAX, icon = Icons.AutoMirrored.Rounded.VolumeUp) {
            vm.setVolumeMode(VolumeMode.MAX)
        }
        ChoiceCard("Volumen a medida", "Elige el nivel.", settings.volumeMode == VolumeMode.CUSTOM, icon = Icons.Rounded.GraphicEq) {
            vm.setVolumeMode(VolumeMode.CUSTOM)
        }
        AnimatedVisibility(settings.volumeMode == VolumeMode.CUSTOM) {
            GuardCard {
                var value by remember(settings.customVolumePercent) { mutableFloatStateOf(settings.customVolumePercent.toFloat()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Volumen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${value.toInt()} %", style = MaterialTheme.typography.titleMedium.merge(TabularNumbers), color = GuardColors.Active)
                }
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    onValueChangeFinished = { vm.setCustomVolume(value.toInt()) },
                    valueRange = 10f..100f,
                    colors = SliderDefaults.colors(thumbColor = GuardColors.Active, activeTrackColor = GuardColors.Active, inactiveTrackColor = GuardColors.SurfaceHighest),
                    modifier = Modifier.semantics { stateDescription = "${value.toInt()} por ciento" },
                )
            }
        }
        ChoiceCard(
            "Volumen actual del teléfono",
            "El de alarma que tengas puesto. Si está a 0, se sube al 60 % mientras suena.",
            settings.volumeMode == VolumeMode.CURRENT,
            icon = Icons.Rounded.Tune,
        ) { vm.setVolumeMode(VolumeMode.CURRENT) }
    }

    SectionTitle("Vibración y sonido")
    GuardCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        ToggleRow(
            "Vibración",
            "Vibra y pausa, en bucle, mientras suena la alarma.",
            settings.vibrate,
            leading = { IconBadge(Icons.Rounded.Vibration, if (settings.vibrate) Tone.ACTIVE else Tone.NEUTRAL, size = 40.dp) },
        ) { vm.setVibrate(it) }
    }
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceCard("Sirena de Modo Guardia", "Incluida en la app: siempre disponible.", settings.soundSource == SoundSource.SIREN, icon = Icons.Rounded.NotificationsActive) {
            vm.setSound(SoundSource.SIREN)
        }
        ChoiceCard("Tono de alarma del teléfono", "Si es «Ninguno» o falla, suena la sirena.", settings.soundSource == SoundSource.SYSTEM_ALARM, icon = Icons.Rounded.Alarm) {
            vm.setSound(SoundSource.SYSTEM_ALARM)
        }
    }
    Hint("La alarma usa el canal de ALARMA de Android: suena aunque el teléfono esté en silencio o en vibración. Al terminar, el volumen vuelve a como estaba.")
}

/**
 * Prueba de alarma: el mismo camino que un aviso real (servicio, volumen, vibración y pantalla de
 * alerta), marcado claramente como prueba.
 */
@Composable
fun TestAlarmCard(vm: MainViewModel) {
    val countdown by vm.countdown.collectAsStateWithLifecycle()
    val alarm by vm.alarm.collectAsStateWithLifecycle()
    GuardCard {
        when {
            countdown != null -> {
                Text("La prueba sonará en $countdown s", style = MaterialTheme.typography.titleMedium.merge(TabularNumbers), color = GuardColors.Warning)
                Text("Bloquea el teléfono ahora para comprobar que la alerta se abre sobre el bloqueo.", style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
                TextButton(onClick = vm::cancelTest) { Text("Cancelar prueba", color = GuardColors.TextDim) }
            }
            alarm != null -> {
                Text("La alarma está sonando", style = MaterialTheme.typography.titleMedium, color = GuardColors.Error)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("DETENER ALARMA", onClick = vm::stopAlarm, containerColor = GuardColors.Error, contentColor = GuardColors.Background, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                Text(
                    "Comprueba volumen, sonido, vibración y pantalla de alerta sin esperar un aviso real. Se identifica como prueba.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GuardColors.TextDim,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Probar ahora", onClick = { vm.testAlarm(3) }, icon = Icons.Rounded.PlayCircle, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Probar con el teléfono bloqueado (15 s)", onClick = { vm.testAlarm(15) }, icon = Icons.Rounded.LockClock, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
