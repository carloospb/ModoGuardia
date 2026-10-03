package es.carlospb.modoguardia.alarm

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import es.carlospb.modoguardia.data.HistoryRepository
import es.carlospb.modoguardia.data.SettingsRepository
import es.carlospb.modoguardia.domain.AlarmKind
import es.carlospb.modoguardia.domain.AlarmRequest
import es.carlospb.modoguardia.domain.AlarmTrigger
import es.carlospb.modoguardia.domain.HistoryType
import es.carlospb.modoguardia.service.GuardService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Punto único para disparar y detener la alarma.
 *
 * Tres escalones, del mejor al de último recurso. Ninguno deja la alarma en silencio:
 *
 *  1. AlarmService: servicio en primer plano propio de la alarma.
 *  2. Si Android 12+ rechaza arrancarlo desde segundo plano pero la guardia tiene su servicio en
 *     primer plano (GuardService), el proceso ya está protegido: la alarma suena dentro del proceso
 *     con el mismo reproductor (volumen, vibración) y la notificación de alerta con full-screen intent.
 *  3. Si tampoco: notificación insistente con sonido de alarma en su canal y full-screen intent.
 */
class AlarmController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
) : AlarmTrigger {
    /** Reproductor del escalón 2. Se asigna desde el motor; start/stop siempre en el hilo principal. */
    @Volatile
    private var inProcessPlayer: AlarmPlayer? = null

    override fun isRinging(): Boolean = AlarmState.active.value != null

    override fun trigger(request: AlarmRequest) {
        // El estado se marca ya: evita que dos eventos simultáneos lancen dos alarmas.
        AlarmState.set(ActiveAlarm(request = request, startedAt = System.currentTimeMillis()))
        try {
            ContextCompat.startForegroundService(context, AlarmService.startIntent(context, request))
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo arrancar AlarmService", e)
            if (GuardService.running.value && startInProcess(request)) return
            fallback(request, "Android no permitió iniciar el servicio de alarma (${e.javaClass.simpleName})")
        }
    }

    private fun startInProcess(request: AlarmRequest): Boolean {
        try {
            context.getSystemService(NotificationManager::class.java)
                .notify(GuardNotifications.ID_ALARM, GuardNotifications.alarm(context, request))
        } catch (e: SecurityException) {
            return false
        }
        val p = AlarmPlayer(context)
        inProcessPlayer = p
        scope.launch(Dispatchers.Main.immediate) {
            val s = settings.current()
            if (inProcessPlayer !== p) return@launch
            val note = p.start(s)
            if (note != null) AlarmState.update { it.copy(note = note) }
        }
        scope.launch { history.log(HistoryType.WARNING, "Alarma reproducida por el servicio de guardia", "Android no dejó iniciar el servicio de alarma; suena igualmente.") }
        return true
    }

    /** Detiene el escalón 2 si está en uso. Devuelve si había algo sonando así. */
    fun stopInProcess(): Boolean {
        val p = inProcessPlayer ?: return false
        inProcessPlayer = null
        scope.launch(Dispatchers.Main.immediate) { p.stop() }
        return true
    }

    override fun noteAdditionalMatch(request: AlarmRequest) {
        AlarmState.update { it.copy(extraMatches = it.extraMatches + 1) }
    }

    override fun onPhoneCallAnswered() {
        // Contestar la llamada demuestra que el usuario está despierto, y la alarma no debe sonar
        // encima de la conversación. Solo si la alarma la provocó una llamada.
        val kind = AlarmState.active.value?.request?.kind ?: return
        if (kind == AlarmKind.PHONE_CALL || kind == AlarmKind.APP_CALL) stop("Llamada contestada")
    }

    /** Prueba de alarma: se llama desde la interfaz (primer plano), siempre permitido. */
    fun startTest(delaySeconds: Int): Boolean = try {
        ContextCompat.startForegroundService(context, AlarmService.testIntent(context, delaySeconds))
        true
    } catch (e: Exception) {
        Log.e(TAG, "No se pudo iniciar la prueba", e)
        false
    }

    fun stop(reason: String) {
        try {
            context.startService(AlarmService.stopIntent(context, reason))
        } catch (e: Exception) {
            // El servicio no está y Android no deja arrancarlo: se limpia lo que depende de nosotros.
            Log.w(TAG, "No se pudo entregar STOP al servicio; limpieza directa", e)
            val ended = AlarmState.active.value
            stopInProcess()
            AlarmState.set(null)
            AlarmState.setCountdown(null)
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.cancel(GuardNotifications.ID_FALLBACK)
            nm.cancel(GuardNotifications.ID_ALARM)
            nm.cancel(GuardNotifications.ID_COUNTDOWN)
            VolumeRestore.restoreIfPending(context)
            if (ended != null) scope.launch { history.log(HistoryType.ALARM_STOPPED, "Alarma detenida", reason) }
        }
    }

    /** Plan B. El sonido sale del canal de reserva (uso ALARM) y se repite por FLAG_INSISTENT. */
    fun fallback(request: AlarmRequest, reason: String) {
        AlarmState.set(
            ActiveAlarm(
                request = request,
                startedAt = System.currentTimeMillis(),
                viaFallback = true,
                note = "$reason. Suena la notificación de alarma de reserva.",
            ),
        )
        try {
            context.getSystemService(NotificationManager::class.java)
                .notify(GuardNotifications.ID_FALLBACK, GuardNotifications.fallback(context, request))
        } catch (e: SecurityException) {
            Log.e(TAG, "Sin permiso de notificaciones: el plan B tampoco puede sonar", e)
        }
        scope.launch { history.log(HistoryType.WARNING, "Alarma en modo de reserva", reason) }
    }

    private companion object {
        const val TAG = "AlarmController"
    }
}
