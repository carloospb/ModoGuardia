package es.carlospb.modoguardia.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import es.carlospb.modoguardia.alarm.GuardNotifications
import es.carlospb.modoguardia.container
import es.carlospb.modoguardia.domain.HistoryType
import es.carlospb.modoguardia.system.CheckLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Servicio de guardia en primer plano, activo mientras Modo Guardia lo está.
 *
 * Por qué existe (no es estética):
 *  * Un proceso con servicio en primer plano no se congela ni se mata como una app en caché, y
 *    sus wakelocks siguen valiendo en Doze: la alarma puede arrancar y sonar a las 3:00.
 *  * Desde un proceso así Android permite arrancar el servicio de alarma en segundo plano.
 *  * Cada minuto revisa la configuración y avisa si algo dejaría de despertar al usuario.
 */
class GuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitor: Job? = null
    private var lastCriticalKey: String = ""

    /** Último texto publicado en la notificación de estado: no se vuelve a publicar si no cambia. */
    private var lastStatusText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(
                this, GuardNotifications.ID_STATUS,
                GuardNotifications.status(this, "Vigilando…", warning = false), type,
            )
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo pasar a primer plano", e)
            _running.value = false
            val text = "Android no permitió iniciar el servicio de guardia. Abre Modo Guardia para reactivarlo."
            getSystemService(NotificationManager::class.java)
                .notify(GuardNotifications.ID_WARNING, GuardNotifications.warning(this, text))
            container.appScope.launch { container.history.log(HistoryType.WARNING, "Servicio de guardia no iniciado", text) }
            stopSelf()
            return START_NOT_STICKY
        }
        _running.value = true
        if (monitor?.isActive != true) {
            monitor = scope.launch {
                while (isActive) {
                    if (!checkHealth()) break
                    delay(HEALTH_INTERVAL_MS)
                }
            }
        }
        // START_STICKY: si Android mata el proceso, lo recrea; checkHealth decide si seguir.
        return START_STICKY
    }

    /** Devuelve false si la guardia ya no está activa (y el servicio se detiene). */
    private suspend fun checkHealth(): Boolean {
        val c = container
        val settings = c.settings.current()
        if (!settings.guardActive) {
            stopSelf()
            return false
        }
        if (GuardNotificationListener.isEnabled(this) && !GuardNotificationListener.connected.value) {
            GuardNotificationListener.requestRebind(this)
        }
        val status = c.statusChecker.check(settings, guardServiceRunning = true)
        val critical = status.items.filter { it.level == CheckLevel.ERROR }
        val warnings = status.items.filter { it.level == CheckLevel.WARNING }
        val since = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(settings.guardStartedAt))
        val watch = settings.watch
        val base = "Protegiendo tu descanso desde las $since · " + when {
            watch.conversationCount > 0 -> "${watch.appCount} apps · ${watch.conversationCount} chats"
            else -> "${settings.activeTriggerCount} reglas activas"
        }
        val text = when {
            critical.isNotEmpty() -> "$base\nCorrige: " + critical.joinToString(", ") { it.title }
            warnings.isNotEmpty() -> "$base\nRevisa: " + warnings.joinToString(", ") { it.title }
            else -> "$base\nSistema preparado"
        }
        val nm = getSystemService(NotificationManager::class.java)
        // Solo si cambia o si el usuario la ha quitado (Android 14+ lo permite): republicar la misma
        // notificación cada minuto es trabajo en balde.
        val showing = runCatching { nm.activeNotifications.any { it.id == GuardNotifications.ID_STATUS } }.getOrDefault(false)
        if (text != lastStatusText || !showing) {
            nm.notify(GuardNotifications.ID_STATUS, GuardNotifications.status(this, text, warning = critical.isNotEmpty()))
            lastStatusText = text
        }

        val key = critical.joinToString("|") { it.id }
        if (key != lastCriticalKey) {
            if (critical.isNotEmpty()) {
                val detail = critical.joinToString("\n") { "• ${it.title}: ${it.summary}" }
                nm.notify(GuardNotifications.ID_WARNING, GuardNotifications.warning(this, detail))
                c.history.log(HistoryType.WARNING, "Modo Guardia puede no funcionar", detail)
            } else {
                nm.cancel(GuardNotifications.ID_WARNING)
            }
            lastCriticalKey = key
        }
        return true
    }

    override fun onDestroy() {
        _running.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GuardService"
        private const val HEALTH_INTERVAL_MS = 60_000L

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, GuardService::class.java))
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar GuardService", e)
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardService::class.java))
        }
    }
}
