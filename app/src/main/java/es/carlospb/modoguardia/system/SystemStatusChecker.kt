package es.carlospb.modoguardia.system

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import es.carlospb.modoguardia.alarm.GuardNotifications
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.data.SoundSource
import es.carlospb.modoguardia.data.VolumeMode
import es.carlospb.modoguardia.domain.CallMode
import es.carlospb.modoguardia.service.GuardNotificationListener

enum class CheckLevel { OK, WARNING, ERROR, INFO }

enum class FixAction {
    NOTIFICATION_ACCESS, REBIND_LISTENER, POST_NOTIFICATIONS, APP_NOTIFICATION_SETTINGS,
    FULL_SCREEN_INTENT, BATTERY_OPTIMIZATION, APP_DETAILS, DND_SETTINGS, PHONE_PERMISSION,
    SOUND_SETTINGS, RESTART_GUARD_SERVICE, UNUSED_APP_RESTRICTIONS, CALL_LOG_PERMISSION,
}

data class CheckItem(
    val id: String,
    val title: String,
    val level: CheckLevel,
    val summary: String,
    val detail: String? = null,
    val action: FixAction? = null,
    val actionLabel: String? = null,
)

data class SystemStatus(val items: List<CheckItem>) {
    val problems: List<CheckItem> get() = items.filter { it.level == CheckLevel.ERROR || it.level == CheckLevel.WARNING }
    val hasErrors: Boolean get() = items.any { it.level == CheckLevel.ERROR }
    val ready: Boolean get() = problems.isEmpty()

    /** Ya se ha comprobado el sistema al menos una vez (antes no se puede decir que esté listo). */
    val checked: Boolean get() = items.isNotEmpty()

    /** Todo comprobado y sin problemas. Nunca es true mientras falte algo o no se haya comprobado. */
    val operational: Boolean get() = checked && problems.isEmpty()

    companion object {
        val EMPTY = SystemStatus(emptyList())
    }
}

/**
 * Estado REAL del sistema. Cada línea se calcula consultando a Android en el momento; ningún
 * estado se supone. Lo que Android no deja comprobar se marca como INFO y se explica.
 */
class SystemStatusChecker(private val context: Context) {

    fun check(settings: GuardSettings, guardServiceRunning: Boolean): SystemStatus = SystemStatus(
        listOfNotNull(
            notificationAccess(),
            appNotifications(),
            fullScreen(),
            alarm(settings),
            battery(),
            unusedAppPause(),
            doNotDisturb(),
            calls(settings),
            rules(settings),
            guardService(settings, guardServiceRunning),
            manufacturer(),
        ),
    )

    private fun notificationAccess(): CheckItem {
        val enabled = GuardNotificationListener.isEnabled(context)
        val connected = GuardNotificationListener.connected.value
        return when {
            !enabled -> CheckItem(
                "notification_access", "Acceso a notificaciones", CheckLevel.ERROR, "Falta permiso",
                "Sin este permiso Modo Guardia no ve ningún aviso. Si Android muestra «Ajuste restringido» " +
                    "(apps instaladas fuera de Play, Android 13+): Ajustes → Apps → Modo Guardia → ⋮ → " +
                    "«Permitir ajustes restringidos», y vuelve a intentarlo.",
                FixAction.NOTIFICATION_ACCESS, "Conceder acceso",
            )
            !connected -> CheckItem(
                "notification_access", "Acceso a notificaciones", CheckLevel.WARNING, "Concedido, pero desconectado",
                "Android ha desconectado el detector. Se puede pedir que lo reconecte; si no vuelve, " +
                    "desactiva y activa el acceso en Ajustes.",
                FixAction.REBIND_LISTENER, "Reconectar",
            )
            else -> CheckItem("notification_access", "Acceso a notificaciones", CheckLevel.OK, "Correcto")
        }
    }

    private fun appNotifications(): CheckItem {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return CheckItem(
                "app_notifications", "Notificaciones de Modo Guardia", CheckLevel.ERROR, "Bloqueadas",
                "Sin ellas no se puede mostrar la alerta a pantalla completa ni la alarma de reserva.",
                FixAction.POST_NOTIFICATIONS, "Permitir",
            )
        }
        val blocked = listOf(GuardNotifications.CHANNEL_ALARM, GuardNotifications.CHANNEL_ALARM_FALLBACK, GuardNotifications.CHANNEL_STATUS)
            .mapNotNull { nm.getNotificationChannel(it) }
            .filter { it.importance == NotificationManager.IMPORTANCE_NONE }
        if (blocked.isNotEmpty()) {
            return CheckItem(
                "app_notifications", "Notificaciones de Modo Guardia", CheckLevel.ERROR, "Canal bloqueado",
                "Están desactivadas: " + blocked.joinToString { "«${it.name}»" } + ".",
                FixAction.APP_NOTIFICATION_SETTINGS, "Abrir ajustes",
            )
        }
        return CheckItem("app_notifications", "Notificaciones de Modo Guardia", CheckLevel.OK, "Correcto")
    }

    private fun fullScreen(): CheckItem? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return CheckItem("full_screen", "Alerta a pantalla completa", CheckLevel.OK, "Permitida")
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        return if (nm.canUseFullScreenIntent()) {
            CheckItem("full_screen", "Alerta a pantalla completa", CheckLevel.OK, "Permitida")
        } else {
            CheckItem(
                "full_screen", "Alerta a pantalla completa", CheckLevel.WARNING, "No permitida",
                "La alarma sonará, pero con el teléfono bloqueado no se abrirá la pantalla de alerta: " +
                    "verás una notificación con el botón DETENER.",
                FixAction.FULL_SCREEN_INTENT, "Permitir",
            )
        }
    }

    private fun alarm(s: GuardSettings): CheckItem {
        val audio = context.getSystemService(AudioManager::class.java)
        val volume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val notes = mutableListOf<String>()
        var level = CheckLevel.OK
        val volumeText = when (s.volumeMode) {
            VolumeMode.MAX -> "volumen máximo"
            VolumeMode.CUSTOM -> "volumen ${s.customVolumePercent} %"
            VolumeMode.CURRENT -> "volumen actual $volume/$max"
        }
        if (s.volumeMode == VolumeMode.CURRENT && volume == 0) {
            level = CheckLevel.WARNING
            notes += "El volumen de alarma está a 0. Al sonar se subirá al 60 %, pero conviene subirlo o elegir «Volumen máximo»."
        }
        if (s.soundSource == SoundSource.SYSTEM_ALARM &&
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) == null
        ) {
            notes += "El tono de alarma del sistema es «Ninguno»: sonará la sirena de Modo Guardia."
        }
        if (s.vibrate && !hasVibrator()) notes += "Este dispositivo no tiene vibrador."
        return CheckItem(
            "alarm", "Alarma", level,
            if (level == CheckLevel.OK) "Preparada · $volumeText" else "Revisar volumen",
            notes.joinToString("\n").ifEmpty { null },
            if (level == CheckLevel.OK) null else FixAction.SOUND_SETTINGS,
            if (level == CheckLevel.OK) null else "Ajustes de sonido",
        )
    }

    private fun hasVibrator(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.hasVibrator() == true
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)?.hasVibrator() == true
    }

    private fun battery(): CheckItem {
        val am = context.getSystemService(ActivityManager::class.java)
        if (am.isBackgroundRestricted) {
            return CheckItem(
                "battery", "Batería", CheckLevel.ERROR, "Actividad en segundo plano restringida",
                "Android impide que la app funcione en segundo plano. Quita la restricción en " +
                    "Información de la app → Batería → «Sin restricciones».",
                FixAction.APP_DETAILS, "Abrir ajustes de la app",
            )
        }
        val pm = context.getSystemService(PowerManager::class.java)
        return if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
            CheckItem("battery", "Batería", CheckLevel.OK, "Sin restricciones")
        } else {
            CheckItem(
                "battery", "Batería", CheckLevel.WARNING, "Puede limitar el funcionamiento",
                "Con la optimización de batería activa, Android puede retrasar o cortar la app de " +
                    "madrugada (Doze). Recomendado: excluir Modo Guardia de la optimización.",
                FixAction.BATTERY_OPTIMIZATION, "Quitar optimización",
            )
        }
    }

    /**
     * Android 11+ retira permisos y (12+) hiberna las apps que no se abren en meses. Una app de
     * guardia puede pasar meses sin abrirse y aun así tener que despertar a alguien.
     */
    private fun unusedAppPause(): CheckItem? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return if (context.packageManager.isAutoRevokeWhitelisted) {
            CheckItem("unused_app", "Pausa por falta de uso", CheckLevel.OK, "Desactivada")
        } else {
            CheckItem(
                "unused_app", "Pausa por falta de uso", CheckLevel.WARNING, "Android puede pausar la app",
                "Si no abres Modo Guardia en unos meses, Android puede retirarle permisos y dejarla en " +
                    "pausa, y entonces no te despertaría. Desactiva «Pausar actividad si no se usa».",
                FixAction.UNUSED_APP_RESTRICTIONS, "Desactivar pausa",
            )
        }
    }

    private fun doNotDisturb(): CheckItem {
        val nm = context.getSystemService(NotificationManager::class.java)
        val schedHint = "Si tienes un horario nocturno de «No molestar», revisa que permita las alarmas."
        return when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL ->
                CheckItem("dnd", "No molestar", CheckLevel.OK, "Desactivado", schedHint)
            NotificationManager.INTERRUPTION_FILTER_ALARMS ->
                CheckItem("dnd", "No molestar", CheckLevel.OK, "Activo «solo alarmas»: la alarma sonará", schedHint)
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> {
                val alarmsAllowed = runCatching {
                    nm.notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS != 0
                }.getOrNull()
                when (alarmsAllowed) {
                    true -> CheckItem("dnd", "No molestar", CheckLevel.OK, "Activo, alarmas permitidas", schedHint)
                    false -> CheckItem(
                        "dnd", "No molestar", CheckLevel.ERROR, "Activo y bloquea las alarmas",
                        "Este modo «No molestar» silencia las alarmas. Permite «Alarmas» en sus excepciones.",
                        FixAction.DND_SETTINGS, "Abrir No molestar",
                    )
                    null -> CheckItem(
                        "dnd", "No molestar", CheckLevel.WARNING, "Revisar",
                        "«No molestar» está activo y Android no deja comprobar si permite alarmas. Revisa que «Alarmas» esté permitido.",
                        FixAction.DND_SETTINGS, "Abrir No molestar",
                    )
                }
            }
            NotificationManager.INTERRUPTION_FILTER_NONE -> CheckItem(
                "dnd", "No molestar", CheckLevel.ERROR, "Silencio total",
                "En «silencio total» Android silencia también las alarmas: Modo Guardia NO sonará. " +
                    "Usa un modo que permita alarmas.",
                FixAction.DND_SETTINGS, "Abrir No molestar",
            )
            else -> CheckItem("dnd", "No molestar", CheckLevel.WARNING, "Revisar", schedHint, FixAction.DND_SETTINGS, "Abrir No molestar")
        }
    }

    private fun calls(s: GuardSettings): CheckItem {
        if (!s.wakeOnPhoneCalls && !s.wakeOnAppCalls) {
            return CheckItem("calls", "Llamadas", CheckLevel.INFO, "Desactivado", "Actívalo en Reglas → Despertar con llamadas.")
        }
        val appCallsOk = GuardNotificationListener.isEnabled(context)
        if (s.wakeOnPhoneCalls) {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                return CheckItem("calls", "Llamadas", CheckLevel.WARNING, "Sin telefonía", "Este dispositivo no puede recibir llamadas telefónicas.")
            }
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return CheckItem(
                    "calls", "Llamadas", CheckLevel.WARNING, "Permiso necesario",
                    "Sin el permiso «Teléfono» solo se detectan las llamadas por la notificación del marcador, " +
                        "que algunos teléfonos no publican con la pantalla bloqueada.",
                    FixAction.PHONE_PERMISSION, "Conceder permiso",
                )
            }
        }
        val manualNumbers = s.callMode == CallMode.SELECTED && s.rules.any { it.enabled && it.isCall && !it.isContact && it.numbers.isNotEmpty() }
        if (s.wakeOnPhoneCalls && manualNumbers &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
        ) {
            // Sin el número exacto, una llamada de esos números puede quedar «sin identificar»: si el
            // usuario ha elegido no despertar en ese caso, podría perderse una llamada.
            return CheckItem(
                "calls", "Llamadas", if (s.wakeOnUnidentifiedCalls) CheckLevel.INFO else CheckLevel.WARNING,
                "Números a mano sin identificación exacta",
                "Los números añadidos a mano se reconocen si el marcador muestra el número. Si muestra un nombre " +
                    "(por ejemplo, el de una empresa), la llamada queda «sin identificar». Con el permiso «Registro de " +
                    "llamadas» Android da el número exacto.",
                FixAction.CALL_LOG_PERMISSION, "Permitir identificar números",
            )
        }
        if (s.wakeOnAppCalls && !appCallsOk) {
            return CheckItem(
                "calls", "Llamadas", CheckLevel.WARNING, "Falta acceso a notificaciones",
                "Las llamadas de apps (WhatsApp, Teams…) se detectan por su notificación.",
                FixAction.NOTIFICATION_ACCESS, "Conceder acceso",
            )
        }
        return CheckItem("calls", "Llamadas", CheckLevel.OK, "Disponible")
    }

    private fun rules(s: GuardSettings): CheckItem = if (s.activeTriggerCount == 0) {
        CheckItem("rules", "Reglas", CheckLevel.WARNING, "Ninguna regla activa", "Nada te despertará hasta que crees una regla o actives las llamadas.")
    } else {
        CheckItem("rules", "Reglas", CheckLevel.OK, "${s.activeTriggerCount} activas")
    }

    private fun guardService(s: GuardSettings, running: Boolean): CheckItem = when {
        !s.guardActive -> CheckItem("guard_service", "Servicio de guardia", CheckLevel.INFO, "Se inicia al activar la guardia")
        running -> CheckItem("guard_service", "Servicio de guardia", CheckLevel.OK, "En ejecución")
        else -> CheckItem(
            "guard_service", "Servicio de guardia", CheckLevel.ERROR, "Detenido",
            "La guardia está activa pero su servicio no se está ejecutando: Android puede cerrar la app.",
            FixAction.RESTART_GUARD_SERVICE, "Reiniciar servicio",
        )
    }

    /** No se puede comprobar por API: se informa, sin dar un estado falso. */
    private fun manufacturer(): CheckItem? {
        val brand = Build.MANUFACTURER.lowercase()
        val aggressive = listOf("xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "vivo", "oneplus", "samsung", "meizu", "asus", "tecno", "infinix")
        if (aggressive.none { brand.contains(it) }) return null
        return CheckItem(
            "manufacturer", "Gestión del fabricante (${Build.MANUFACTURER})", CheckLevel.INFO, "No comprobable automáticamente",
            "Este fabricante añade su propio ahorro de energía. Revisa a mano en Información de la app: " +
                "«Inicio automático» permitido, batería «Sin restricciones» y, en Samsung, que Modo Guardia " +
                "no esté en «Apps en suspensión». Guía por modelo: dontkillmyapp.com",
            FixAction.APP_DETAILS, "Información de la app",
        )
    }
}
