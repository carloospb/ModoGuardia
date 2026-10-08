package es.carlospb.modoguardia.service

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reconexión REAL del lector de notificaciones.
 *
 * `NotificationListenerService.requestRebind()` solo vuelve a enlazar un lector que la propia app
 * desconectó con `requestUnbind()`. Si fue Android quien perdió el enlace (el proceso murió, el
 * fabricante cerró la app, o el sistema dejó de reintentarlo tras varios fallos), requestRebind no
 * hace nada: el acceso sigue concedido, pero el lector no vuelve hasta que se reinicia el teléfono o
 * se quita y se vuelve a dar el acceso.
 *
 * Lo que sí obliga a Android a soltar el enlace muerto y crear uno nuevo, con API pública, es
 * desactivar y volver a activar el componente del lector (`PackageManager.setComponentEnabledSetting`):
 * el gestor de notificaciones recibe PACKAGE_CHANGED, deja de ver el lector, lo vuelve a ver y lo
 * enlaza de nuevo. El permiso concedido por el usuario no se toca.
 */
object ListenerRecovery {
    enum class Result { ALREADY_CONNECTED, RECONNECTED, NO_ACCESS, FAILED }

    private const val TAG = "ListenerRecovery"

    /** Margen para que Android termine un enlace que ya está en camino (p. ej. al arrancar el proceso). */
    private const val REBIND_WAIT_MS = 3_000L

    /**
     * Entre desactivar y activar. Android agrupa durante ~1 s los cambios de componentes en una sola
     * difusión: si se activa antes, el sistema solo ve «activado» y no suelta el enlace muerto.
     */
    private const val TOGGLE_GAP_MS = 3_000L

    /** Lo que se espera a que Android vuelva a enlazar tras activar el componente. */
    private const val CONNECT_WAIT_MS = 15_000L

    private val mutex = Mutex()

    /**
     * Intenta reconectar el lector y espera a saber si lo ha conseguido (hasta ~20 s). Nunca deja el
     * componente desactivado, aunque se cancele a medias.
     */
    suspend fun reconnect(context: Context): Result = mutex.withLock {
        val app = context.applicationContext
        if (!GuardNotificationListener.isEnabled(app)) return@withLock Result.NO_ACCESS
        if (GuardNotificationListener.connected.value) return@withLock Result.ALREADY_CONNECTED

        // 1) Lo barato: por si Android ya lo estaba enlazando o la app lo desconectó con requestUnbind.
        GuardNotificationListener.requestRebind(app)
        if (awaitConnected(REBIND_WAIT_MS)) return@withLock Result.RECONNECTED

        // 2) Forzar un enlace nuevo: desactivar y volver a activar el componente.
        val pm = app.packageManager
        val component = GuardNotificationListener.component(app)
        try {
            pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            delay(TOGGLE_GAP_MS)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo desactivar el lector", e)
        } finally {
            withContext(NonCancellable) { ensureComponentEnabled(app) }
        }
        GuardNotificationListener.requestRebind(app)
        if (awaitConnected(CONNECT_WAIT_MS)) Result.RECONNECTED else Result.FAILED
    }

    /**
     * El lector debe estar siempre activo como componente. Si el proceso murió justo entre desactivar
     * y activar, se corrige al volver a arrancar (lo llama la Application).
     */
    fun ensureComponentEnabled(context: Context) {
        val pm = context.packageManager
        val component = GuardNotificationListener.component(context)
        try {
            if (pm.getComponentEnabledSetting(component) != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo reactivar el componente del lector", e)
        }
    }

    private suspend fun awaitConnected(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { GuardNotificationListener.connected.first { it } } != null
}
