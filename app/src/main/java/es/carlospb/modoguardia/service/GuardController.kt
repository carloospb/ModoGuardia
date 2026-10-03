package es.carlospb.modoguardia.service

import android.app.NotificationManager
import android.content.Context
import es.carlospb.modoguardia.alarm.GuardNotifications
import es.carlospb.modoguardia.data.HistoryRepository
import es.carlospb.modoguardia.data.SettingsRepository
import es.carlospb.modoguardia.domain.GuardEngine
import es.carlospb.modoguardia.domain.HistoryType

/** Activar / finalizar / restaurar Modo Guardia. */
class GuardController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    private val engine: GuardEngine,
) {
    suspend fun start() {
        val now = System.currentTimeMillis()
        settings.update { it.copy(guardActive = true, guardStartedAt = now) }
        // Lo que ya estaba en la bandeja no es nuevo: si una app lo re-publica, no debe sonar.
        engine.seed(GuardNotificationListener.activeEvents())
        val started = GuardService.start(context)
        val s = settings.current()
        history.log(HistoryType.GUARD_STARTED, "Guardia iniciada", "${s.activeTriggerCount} reglas activas", now)
        if (!started) {
            history.log(HistoryType.WARNING, "Servicio de guardia no iniciado", "La detección funciona, pero Android puede cerrar la app. Revisa Estado del sistema.")
        }
    }

    suspend fun stop() {
        val s = settings.current()
        settings.update { it.copy(guardActive = false) }
        GuardService.stop(context)
        context.getSystemService(NotificationManager::class.java).cancel(GuardNotifications.ID_WARNING)
        val minutes = ((System.currentTimeMillis() - s.guardStartedAt) / 60_000).coerceAtLeast(0)
        history.log(HistoryType.GUARD_ENDED, "Guardia finalizada", "Duración: ${minutes / 60} h ${minutes % 60} min")
    }

    /** Si la guardia está activa y su servicio no, lo (re)inicia. Devuelve si quedó en marcha. */
    suspend fun ensureServiceRunning(): Boolean {
        if (!settings.current().guardActive) return true
        if (GuardService.running.value) return true
        return GuardService.start(context)
    }

    /** Tras reiniciar el teléfono o actualizar la app. */
    suspend fun restore(reason: String) {
        if (!settings.current().guardActive) return
        val ok = GuardService.start(context)
        if (ok) {
            history.log(HistoryType.GUARD_RESTORED, "Guardia restaurada", reason)
        } else {
            val text = "Modo Guardia estaba activo, pero Android no dejó reanudar el servicio ($reason). Abre la app."
            history.log(HistoryType.WARNING, "No se pudo restaurar la guardia", text)
            context.getSystemService(NotificationManager::class.java)
                .notify(GuardNotifications.ID_WARNING, GuardNotifications.warning(context, text))
        }
    }
}
