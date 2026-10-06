package es.carlospb.modoguardia.service

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import es.carlospb.modoguardia.container
import es.carlospb.modoguardia.domain.HistoryType
import es.carlospb.modoguardia.domain.GuardEngine
import es.carlospb.modoguardia.domain.NotificationEvent
import es.carlospb.modoguardia.domain.Outcome
import es.carlospb.modoguardia.domain.SeenChats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Lector de notificaciones. Lo enlaza el sistema cuando el usuario concede el acceso, y el sistema
 * lo vuelve a enlazar tras reiniciar o actualizar la app. Todo el análisis es local.
 */
class GuardNotificationListener : NotificationListenerService() {
    private lateinit var parser: NotificationParser

    override fun onCreate() {
        super.onCreate()
        parser = NotificationParser(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = WeakReference(this)
        _connected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _connected.value = false
        if (instance?.get() === this) instance = null
        val c = container
        c.appScope.launch {
            if (c.settings.current().guardActive) {
                requestRebind(applicationContext)
                c.history.log(
                    HistoryType.WARNING,
                    "Acceso a notificaciones desconectado",
                    "Se ha pedido a Android que vuelva a conectarlo. Comprueba Estado del sistema.",
                )
            }
        }
    }

    override fun onDestroy() {
        if (instance?.get() === this) {
            instance = null
            _connected.value = false
        }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val parsed = try {
            parser.parse(sbn)
        } catch (e: Exception) {
            // Un aviso con extras raros no puede tumbar el detector.
            Log.e(TAG, "No se pudo analizar ${sbn.packageName}", e)
            return
        }
        val c = container
        c.diagnostics.record(parsed)
        if (parsed.skipReason != null) return
        // «Conversaciones detectadas», para elegirlas al crear reglas. Solo el nombre, nunca el mensaje,
        // y solo si el usuario no lo ha desactivado en Ajustes → Privacidad.
        SeenChats.chatOf(parsed.event, System.currentTimeMillis())?.let { chat ->
            c.appScope.launch { if (c.settings.current().learnConversations) c.seenChats.record(chat) }
        }
        c.appScope.launch {
            // Aviso de llamada sin identificar con el número exacto en camino: se espera a la señal.
            if (c.engine.onNotification(parsed.event) == Outcome.AWAITING_CALLER) {
                delay(GuardEngine.CALLER_TIMEOUT_MS)
                c.engine.onCallerTimeout()
            }
        }
    }

    private fun snapshotActive(): List<NotificationEvent> = try {
        activeNotifications.orEmpty()
            .filter { it.packageName != packageName }
            .mapNotNull { runCatching { parser.parse(it).event }.getOrNull() }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudieron leer las notificaciones activas", e)
        emptyList()
    }

    companion object {
        private const val TAG = "GuardListener"
        private val _connected = MutableStateFlow(false)

        /** true mientras el sistema mantiene enlazado el lector. */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        @Volatile
        private var instance: WeakReference<GuardNotificationListener>? = null

        fun component(context: Context) = ComponentName(context, GuardNotificationListener::class.java)

        /** El usuario ha concedido el acceso a notificaciones en Ajustes. */
        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /** API oficial para pedir al sistema que vuelva a enlazar el lector. */
        fun requestRebind(context: Context) {
            if (!isEnabled(context)) return
            try {
                NotificationListenerService.requestRebind(component(context))
            } catch (e: Exception) {
                Log.w(TAG, "requestRebind falló", e)
            }
        }

        /** Avisos ya presentes al iniciar la guardia, para que una re-publicación no haga sonar. */
        fun activeEvents(): List<NotificationEvent> = instance?.get()?.snapshotActive().orEmpty()
    }
}
