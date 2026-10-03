package es.carlospb.modoguardia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.system.CheckItem
import es.carlospb.modoguardia.system.FixAction
import es.carlospb.modoguardia.system.SystemStatus
import es.carlospb.modoguardia.ui.theme.GuardColors
import java.util.UUID

private const val STEPS = 7

@Composable
fun OnboardingScreen(
    settings: GuardSettings,
    status: SystemStatus,
    vm: MainViewModel,
    rulesVm: RulesViewModel,
    onFix: (FixAction) -> Unit,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    fun item(id: String): CheckItem? = status.items.firstOrNull { it.id == id }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp),
    ) {
        if (step <= STEPS) {
            Text("PASO $step DE $STEPS", color = GuardColors.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { step / STEPS.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                1 -> {
                    Title("Bienvenido a Modo Guardia")
                    Body(
                        "Para dormir tranquilo durante una guardia. Mientras Modo Guardia está activo, un aviso " +
                            "que cumpla tus reglas o una llamada hace sonar una alarma potente que no para hasta " +
                            "que pulses DETENER.",
                    )
                    Body("Modo Guardia analiza las notificaciones localmente en tu dispositivo únicamente para comprobar si cumplen las reglas configuradas. La app no tiene acceso a Internet.")
                    Body("En los siguientes pasos se configuran los permisos. Cada estado que verás es real: se comprueba en el teléfono.")
                }
                2 -> {
                    Title("Acceso a notificaciones")
                    Body("Es imprescindible: es la forma oficial de Android de leer los avisos de WhatsApp y otras apps.")
                    item("notification_access")?.let { CheckRow(it, onFix) }
                }
                3 -> {
                    Title("Notificaciones de Modo Guardia")
                    Body("Hacen falta para mostrar la alerta sobre la pantalla de bloqueo.")
                    item("app_notifications")?.let { CheckRow(it, onFix) }
                    item("full_screen")?.let { CheckRow(it, onFix) }
                }
                4 -> {
                    Title("Segundo plano y batería")
                    Body("De madrugada Android duerme las apps para ahorrar batería. Modo Guardia debe quedar fuera de esa optimización.")
                    item("battery")?.let { CheckRow(it, onFix) }
                    item("unused_app")?.let { CheckRow(it, onFix) }
                    item("manufacturer")?.let { CheckRow(it, onFix) }
                    item("dnd")?.let { CheckRow(it, onFix) }
                }
                5 -> {
                    Title("Configurar alarma")
                    AlarmSettings(settings, vm)
                }
                6 -> {
                    Title("Crear primera regla")
                    FirstRule(settings, rulesVm) { step++ }
                }
                7 -> {
                    Title("Realizar prueba")
                    Body("Comprueba que oyes la alarma, notas la vibración y ves la pantalla de alerta. Después pulsa DETENER ALARMA.")
                    val countdown by vm.countdown.collectAsStateWithLifecycle()
                    if (countdown != null) {
                        Text("Sonará en $countdown s…", color = GuardColors.Warning, fontSize = 22.sp)
                    } else {
                        Button(onClick = { vm.testAlarm(3) }, modifier = Modifier.fillMaxWidth()) { Text("PROBAR ALARMA") }
                    }
                }
                else -> Finish(status, onFix)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 1) TextButton(onClick = { step-- }) { Text("Atrás") } else Spacer(Modifier)
            if (step <= STEPS) {
                Button(onClick = { step++ }) { Text(if (step == STEPS) "Terminar" else "Siguiente") }
            } else {
                Button(onClick = onFinish) { Text("Ir a Modo Guardia") }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun FirstRule(settings: GuardSettings, rulesVm: RulesViewModel, onCreated: () -> Unit) {
    var group by rememberSaveable { mutableStateOf("") }
    if (settings.rules.isNotEmpty()) {
        Body("Ya tienes ${settings.rules.size} regla(s). Puedes añadir más en la pestaña Reglas.")
    }
    Body("La más habitual: un grupo de WhatsApp de guardias.")
    OutlinedTextField(
        value = group,
        onValueChange = { group = it },
        label = { Text("Nombre del grupo de WhatsApp") },
        placeholder = { Text("GUARDIAS SOPORTE") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            rulesVm.save(Rule(id = UUID.randomUUID().toString(), type = RuleType.WHATSAPP_GROUP, groupName = group.trim()))
            onCreated()
        },
        enabled = group.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Crear regla de grupo") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            rulesVm.save(Rule(id = UUID.randomUUID().toString(), type = RuleType.WHATSAPP_ANY))
            onCreated()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Cualquier WhatsApp") }
    Text(
        "Reglas por app, palabras clave y llamadas: pestaña Reglas.",
        style = MaterialTheme.typography.bodySmall,
        color = GuardColors.TextDim,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Finish(status: SystemStatus, onFix: (FixAction) -> Unit) {
    if (status.ready) {
        Spacer(Modifier.height(48.dp))
        Text("TODO PREPARADO ✓", style = MaterialTheme.typography.headlineMedium, color = GuardColors.Active)
        Body("Pulsa INICIAR GUARDIA cuando empiece tu guardia.")
    } else {
        // No se dice "todo preparado" si no lo está.
        Title("Casi listo")
        Body("Queda por resolver:")
        status.problems.forEach { CheckRow(it, onFix) }
        Body("Puedes continuar y corregirlo después en la pestaña Estado.")
    }
}
