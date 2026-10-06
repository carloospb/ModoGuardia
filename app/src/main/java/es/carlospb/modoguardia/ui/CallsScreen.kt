package es.carlospb.modoguardia.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.domain.CallMode
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.system.FixAction
import es.carlospb.modoguardia.ui.theme.GuardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Permiso concedido, comprobado de nuevo cada vez que se vuelve a la pantalla (p. ej. desde Ajustes). */
@Composable
fun rememberPermissionGranted(permission: String): Boolean {
    val context = LocalContext.current
    fun check() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(check()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = check() }
    return granted
}

/** «Llamadas que activan la alarma»: todas, o solo los contactos y números elegidos. */
@Composable
fun CallsScreen(
    settings: GuardSettings,
    rulesVm: RulesViewModel,
    mainVm: MainViewModel,
    onFix: (FixAction) -> Unit,
    onBack: () -> Unit,
) {
    val rules by rulesVm.rules.collectAsStateWithLifecycle()
    val callRules = rules.filter { it.isCall }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val phoneGranted = rememberPermissionGranted(Manifest.permission.READ_PHONE_STATE)
    val callLogGranted = rememberPermissionGranted(Manifest.permission.READ_CALL_LOG)
    var addNumber by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(PickPhoneContact()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // El permiso temporal sobre el contacto dura poco: se lee al momento.
        scope.launch {
            val contact = withContext(Dispatchers.IO) { ContactReader.read(context, uri) }
            if (contact == null || (contact.name.isNullOrBlank() && contact.number.isNullOrBlank())) {
                mainVm.showMessage("No se pudo leer ese contacto. Prueba a añadir su número.")
            } else {
                rulesVm.addCallContact(contact.name.orEmpty(), contact.alternativeName, contact.number)
                haptics.confirm()
                mainVm.showMessage("${contact.name ?: contact.number} añadido")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = "Llamadas", subtitle = "Qué llamadas activan la alarma", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .then(ScreenPadding),
        ) {
            GuardCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
                ToggleRow(
                    title = "Despertar con llamadas telefónicas",
                    subtitle = "La llamada funciona con normalidad; la alarma suena además del timbre y se para al contestar.",
                    checked = settings.wakeOnPhoneCalls,
                    leading = { IconBadge(Icons.Rounded.Call, if (settings.wakeOnPhoneCalls) Tone.ACTIVE else Tone.NEUTRAL, size = 40.dp) },
                ) { on ->
                    mainVm.setWakeOnPhoneCalls(on)
                    haptics.toggle(on)
                    if (on && !phoneGranted) onFix(FixAction.PHONE_PERMISSION)
                }
                if (settings.wakeOnPhoneCalls && !phoneGranted) {
                    RowDivider()
                    PermissionRow(
                        "Falta el permiso «Teléfono»",
                        "Sin él, las llamadas solo se detectan por el aviso del marcador, que algunos teléfonos no publican con la pantalla bloqueada.",
                        "Conceder",
                    ) { onFix(FixAction.PHONE_PERMISSION) }
                }
            }

            AnimatedVisibility(settings.wakeOnPhoneCalls) {
                Column {
                    SectionTitle("¿Qué llamadas?")
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChoiceCard(
                            "Todas las llamadas",
                            "Cualquier llamada entrante hace sonar la alarma.",
                            selected = settings.callMode == CallMode.ALL,
                            icon = Icons.Rounded.PhoneInTalk,
                        ) { mainVm.setCallMode(CallMode.ALL) }
                        ChoiceCard(
                            "Solo contactos y números elegidos",
                            "El resto de llamadas suena como siempre, sin alarma.",
                            selected = settings.callMode == CallMode.SELECTED,
                            icon = Icons.Rounded.PeopleAlt,
                        ) { mainVm.setCallMode(CallMode.SELECTED) }
                    }

                    AnimatedVisibility(settings.callMode == CallMode.SELECTED) {
                        Column {
                            SectionTitle("Contactos y números")
                            GuardCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                                if (callRules.isEmpty()) {
                                    EmptyState(
                                        icon = Icons.Rounded.ContactPhone,
                                        title = "Ningún contacto elegido",
                                        body = "Añade los contactos o números que deben despertarte. Mientras no haya ninguno, ninguna llamada hará sonar la alarma.",
                                    )
                                }
                                callRules.forEachIndexed { i, rule ->
                                    if (i > 0) RowDivider()
                                    CallRuleRow(rule, onToggle = { rulesVm.setEnabled(rule.id, it) }, onDelete = { rulesVm.delete(rule.id) })
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrimaryButton(
                                    "Añadir contacto",
                                    onClick = {
                                        try {
                                            picker.launch(Unit)
                                        } catch (_: ActivityNotFoundException) {
                                            mainVm.showMessage("No hay ninguna app de contactos. Añade el número a mano.")
                                        }
                                    },
                                    icon = Icons.Rounded.ContactPhone,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                SecondaryButton("Añadir número", onClick = { addNumber = true }, icon = Icons.Rounded.Dialpad, modifier = Modifier.fillMaxWidth())
                            }
                            Hint("Los contactos se eligen con el selector de Android: Modo Guardia no lee tu agenda, solo el contacto que tocas.")

                            val manualNumbers = callRules.any { !it.isContact && it.numbers.isNotEmpty() }
                            if (manualNumbers && !callLogGranted) {
                                Spacer(Modifier.height(12.dp))
                                GuardCard(borderColor = GuardColors.Info.copy(alpha = 0.4f)) {
                                    PermissionRow(
                                        "Identificar números exactos",
                                        "Un número añadido a mano se reconoce si el marcador muestra el número. Si enseña un nombre " +
                                            "(por ejemplo, el de una empresa), solo con el permiso «Registro de llamadas» Android da el " +
                                            "número exacto. Modo Guardia no lee tu historial de llamadas.",
                                        "Permitir",
                                        tone = Tone.INFO,
                                    ) { onFix(FixAction.CALL_LOG_PERMISSION) }
                                }
                            }

                            SectionTitle("Si no se puede saber quién llama")
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ChoiceCard(
                                    "Despertarme igualmente",
                                    "Recomendado: mejor una alarma de más que perder una llamada importante.",
                                    selected = settings.wakeOnUnidentifiedCalls,
                                    icon = Icons.Rounded.NotificationsActive,
                                ) { mainVm.setWakeOnUnidentifiedCalls(true) }
                                ChoiceCard(
                                    "No despertarme",
                                    "Solo suenan las llamadas que se reconocen con seguridad.",
                                    selected = !settings.wakeOnUnidentifiedCalls,
                                    icon = Icons.Rounded.NotificationsOff,
                                ) { mainVm.setWakeOnUnidentifiedCalls(false) }
                            }

                            SectionTitle("Cómo se sabe quién llama")
                            GuardCard {
                                InfoLine(
                                    "Por el aviso de llamada del teléfono: muestra el nombre del contacto o, si no es un contacto, " +
                                        "el número. Modo Guardia lo compara con lo que has elegido.",
                                )
                                Spacer(Modifier.height(10.dp))
                                InfoLine(
                                    "No hay opción «números desconocidos»: sin leer tu agenda, Android no permite saber con " +
                                        "seguridad si quien llama está en tus contactos, y algunos teléfonos muestran el nombre de " +
                                        "empresas que no lo están.",
                                )
                            }
                        }
                    }
                }
            }

            SectionTitle("Llamadas de apps")
            GuardCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
                ToggleRow(
                    title = "WhatsApp, Teams, Telegram…",
                    subtitle = "Cualquier llamada entrante de una app de llamadas. Se detecta por su aviso de llamada.",
                    checked = settings.wakeOnAppCalls,
                    leading = { IconBadge(Icons.Rounded.PhoneInTalk, if (settings.wakeOnAppCalls) Tone.ACTIVE else Tone.NEUTRAL, size = 40.dp) },
                ) { on ->
                    mainVm.setWakeOnAppCalls(on)
                    haptics.toggle(on)
                }
            }
            Hint("Aunque esto esté desactivado, una llamada de WhatsApp de una persona que vigilas normalmente también despierta: su aviso trae el nombre del contacto.")
            Spacer(Modifier.height(28.dp))
        }
    }

    if (addNumber) {
        AddNumberDialog(onDismiss = { addNumber = false }) { number, label ->
            rulesVm.addCallNumber(number, label)
            haptics.confirm()
        }
    }
}

@Composable
private fun CallRuleRow(rule: Rule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    ListRow(
        title = rule.label(),
        subtitle = rule.callSubtitle() + if (!rule.enabled) " · en pausa" else "",
        leading = {
            if (rule.isContact) {
                ConversationAvatar(rule.label(), isGroup = false, size = 40.dp)
            } else {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GuardColors.SurfaceHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Dialpad, contentDescription = null, tint = GuardColors.TextDim, modifier = Modifier.size(20.dp))
                }
            }
        },
        titleColor = if (rule.enabled) GuardColors.Text else GuardColors.TextDim,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GuardSwitch(rule.enabled, onToggle, contentDescription = "Vigilar ${rule.label()}")
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Quitar ${rule.label()}", tint = GuardColors.TextDim)
                }
            }
        },
    )
}

@Composable
private fun PermissionRow(title: String, body: String, action: String, tone: Tone = Tone.WARNING, onClick: () -> Unit) {
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = tone.color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tone.color)
            Text(body, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(action, onClick = onClick, contentColor = tone.color)
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Row {
        Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, tint = GuardColors.Info, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
    }
}

@Composable
private fun AddNumberDialog(onDismiss: () -> Unit, onAdd: (number: String, label: String?) -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    val valid = es.carlospb.modoguardia.domain.PhoneNumbers.isUsable(number)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GuardColors.SurfaceHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Añadir número", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                GuardTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = "Número",
                    placeholder = "+34 600 00 00 00",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(10.dp))
                GuardTextField(value = label, onValueChange = { label = it }, label = "Nombre (opcional)", placeholder = "Empresa")
                Hint("Con o sin prefijo: +34 600 12 34 56, 600123456 y 0034600123456 son el mismo número. Si está en tus contactos, mejor elígelo con «Añadir contacto».")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(number, label.takeIf { it.isNotBlank() })
                    onDismiss()
                },
                enabled = valid,
            ) { Text("Añadir", style = MaterialTheme.typography.labelLarge, color = if (valid) GuardColors.Active else GuardColors.TextFaint) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", style = MaterialTheme.typography.labelLarge, color = GuardColors.TextDim) }
        },
    )
}
