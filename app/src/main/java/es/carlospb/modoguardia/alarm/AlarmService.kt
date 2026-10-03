package es.carlospb.modoguardia.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import es.carlospb.modoguardia.container
import es.carlospb.modoguardia.domain.AlarmKind
import es.carlospb.modoguardia.domain.AlarmRequest
import es.carlospb.modoguardia.domain.HistoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que hace sonar la alarma hasta que el usuario la detiene.
 *
 * Es el mismo camino para un aviso real y para la prueba: la prueba solo añade una cuenta atrás
 * delante. Nunca se detiene sola: solo con ACTION_STOP (botón DETENER, acción de la notificación
 * o llamada contestada).
 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: AlarmPlayer? = null
    private var countdownJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarm(intent.getStringExtra(EXTRA_REASON) ?: "Detenida")
                return START_NOT_STICKY
            }
            ACTION_TEST -> {
                startTest(intent.getIntExtra(EXTRA_DELAY, 3).coerceIn(0, 60))
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START, o la reentrega del sistema si el proceso murió con la alarma sonando.
                val request = AlarmExtras.read(intent) ?: AlarmState.active.value?.request
                if (request == null) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                startAlarm(request)
                return START_REDELIVER_INTENT
            }
        }
    }

    private fun goForeground(id: Int, notification: android.app.Notification): Boolean = try {
        ServiceCompat.startForeground(this, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        true
    } catch (e: Exception) {
        // Android 12+: ForegroundServiceStartNotAllowedException si se considera "segundo plano".
        Log.e(TAG, "startForeground rechazado", e)
        false
    }

    private fun startAlarm(request: AlarmRequest) {
        countdownJob?.cancel()
        countdownJob = null
        AlarmState.setCountdown(null)

        if (player != null) {
            // Ya suena: no se reinicia (lo cuenta AlarmController.noteAdditionalMatch).
            return
        }
        if (!goForeground(GuardNotifications.ID_ALARM, GuardNotifications.alarm(this, request))) {
            container.alarmController.fallback(request, "Android no permitió iniciar el servicio de alarma")
            stopSelf()
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(GuardNotifications.ID_COUNTDOWN)
        nm.cancel(GuardNotifications.ID_FALLBACK)

        val previous = AlarmState.active.value
        AlarmState.set(
            ActiveAlarm(
                request = request,
                startedAt = previous?.takeIf { it.request == request }?.startedAt ?: System.currentTimeMillis(),
                extraMatches = previous?.extraMatches ?: 0,
            ),
        )

        val p = AlarmPlayer(this)
        player = p
        scope.launch {
            val settings = container.settings.current()
            if (player !== p) return@launch // se detuvo mientras se leían los ajustes
            val note = p.start(settings)
            if (note != null) AlarmState.update { it.copy(note = note) }
        }
    }

    private fun startTest(seconds: Int) {
        if (AlarmState.active.value != null) return
        if (!goForeground(GuardNotifications.ID_COUNTDOWN, GuardNotifications.countdown(this, seconds))) {
            stopSelf()
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (s in seconds downTo 1) {
                AlarmState.setCountdown(s)
                nm.notify(GuardNotifications.ID_COUNTDOWN, GuardNotifications.countdown(this@AlarmService, s))
                delay(1000)
            }
            AlarmState.setCountdown(null)
            val now = System.currentTimeMillis()
            container.appScope.launch {
                container.history.log(HistoryType.ALARM_TEST, "Prueba de alarma", null, now)
            }
            startAlarm(
                AlarmRequest(
                    kind = AlarmKind.TEST,
                    sourceApp = "Prueba de alarma",
                    packageName = null,
                    ruleLabel = "Prueba de Modo Guardia",
                    message = "Esta es una prueba. Si la oyes, notas la vibración y ves esta pantalla, la alarma funciona.",
                    timestamp = now,
                ),
            )
        }
    }

    private fun stopAlarm(reason: String) {
        container.alarmController.stopInProcess()
        countdownJob?.cancel()
        countdownJob = null
        AlarmState.setCountdown(null)
        player?.stop()
        player = null
        // Sin servicio que restaure (plan B), el volumen se restaura igualmente.
        VolumeRestore.restoreIfPending(this)

        val ended = AlarmState.active.value
        AlarmState.set(null)
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(GuardNotifications.ID_FALLBACK)
        nm.cancel(GuardNotifications.ID_COUNTDOWN)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        nm.cancel(GuardNotifications.ID_ALARM)
        stopSelf()

        if (ended != null) {
            val seconds = (System.currentTimeMillis() - ended.startedAt) / 1000
            container.appScope.launch {
                container.history.log(
                    HistoryType.ALARM_STOPPED,
                    "Alarma detenida",
                    "$reason · sonó ${seconds / 60} min ${seconds % 60} s",
                )
            }
        }
    }

    override fun onDestroy() {
        player?.stop()
        player = null
        countdownJob?.cancel()
        AlarmState.setCountdown(null)
        if (AlarmState.active.value?.viaFallback == false) AlarmState.set(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val ACTION_START = "es.carlospb.modoguardia.alarm.START"
        private const val ACTION_STOP = "es.carlospb.modoguardia.alarm.STOP"
        private const val ACTION_TEST = "es.carlospb.modoguardia.alarm.TEST"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_DELAY = "delay"

        fun startIntent(context: Context, request: AlarmRequest): Intent =
            AlarmExtras.put(Intent(context, AlarmService::class.java).setAction(ACTION_START), request)

        fun stopIntent(context: Context, reason: String): Intent =
            Intent(context, AlarmService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_REASON, reason)

        fun testIntent(context: Context, delaySeconds: Int): Intent =
            Intent(context, AlarmService::class.java).setAction(ACTION_TEST).putExtra(EXTRA_DELAY, delaySeconds)
    }
}
