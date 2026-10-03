package es.carlospb.modoguardia.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AlarmKind { NOTIFICATION, PHONE_CALL, APP_CALL, TEST }

/** Lo que la alarma muestra: origen, regla, mensaje y hora. */
data class AlarmRequest(
    val kind: AlarmKind,
    val sourceApp: String,
    val packageName: String?,
    val ruleLabel: String,
    val message: String,
    val timestamp: Long,
)

/** Estado de guardia que necesita el motor, leído en cada evento. */
data class GuardSnapshot(
    val active: Boolean,
    val startedAt: Long,
    val rules: List<Rule>,
    val wakeOnPhoneCalls: Boolean,
    val wakeOnAppCalls: Boolean,
)

interface AlarmTrigger {
    fun isRinging(): Boolean
    fun trigger(request: AlarmRequest)
    /** Otro aviso válido ha llegado mientras la alarma ya suena: no se reinicia, se anota. */
    fun noteAdditionalMatch(request: AlarmRequest)
    /** El usuario ha contestado una llamada: si la alarma la provocó una llamada, ya está despierto. */
    fun onPhoneCallAnswered()
}

enum class HistoryType {
    GUARD_STARTED, GUARD_ENDED, GUARD_RESTORED, ALARM, ALARM_STOPPED, ALARM_TEST,
    IGNORED, GROUP_UNRESOLVED, MATCH_WHILE_RINGING, WARNING,
}

interface HistoryLog {
    suspend fun log(type: HistoryType, title: String, detail: String? = null, time: Long = System.currentTimeMillis())
}

enum class Outcome { GUARD_INACTIVE, PREEXISTING, NO_MATCH, DUPLICATE, ALREADY_RINGING, ALARM }

/**
 * Orquesta: evento → filtros → RuleEngine → deduplicación → alarma, y deja rastro en el historial.
 * Serializado con un Mutex: las notificaciones llegan en ráfagas y la deduplicación no debe
 * tener carreras (dos re-publicaciones simultáneas no pueden disparar dos alarmas).
 */
class GuardEngine(
    private val snapshot: suspend () -> GuardSnapshot,
    private val dedup: Deduplicator,
    private val alarm: AlarmTrigger,
    private val history: HistoryLog,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ignoredLogIntervalMs: Long = 10 * 60 * 1000L,
) {
    private val mutex = Mutex()
    private val lastIgnoredLog = HashMap<String, Long>()

    suspend fun onNotification(raw: NotificationEvent): Outcome = mutex.withLock {
        val s = snapshot()
        if (!s.active) return@withLock Outcome.GUARD_INACTIVE
        val now = clock()

        // Publicada antes de iniciar la guardia: no es un aviso nuevo.
        if (raw.postTime < s.startedAt) return@withLock Outcome.PREEXISTING
        // Re-publicación de un chat cuyos mensajes son todos anteriores a la guardia.
        if (raw.messages.isNotEmpty() && raw.messages.all { it.timestamp in 1 until s.startedAt }) {
            return@withLock Outcome.PREEXISTING
        }
        val event = raw.onlyMessagesSince(s.startedAt)

        if (event.isIncomingCall) {
            val wanted = if (event.isPhoneCall) s.wakeOnPhoneCalls else s.wakeOnAppCalls
            if (wanted) return@withLock onCallLocked(event, now)
        }

        when (val result = RuleEngine.evaluate(event, s.rules)) {
            is Evaluation.NoMatch -> {
                logIgnored(event, result.groupUnresolved, now)
                Outcome.NO_MATCH
            }
            is Evaluation.Match -> {
                if (dedup.isDuplicate(event, now)) {
                    Outcome.DUPLICATE
                } else {
                    fire(
                        AlarmRequest(
                            kind = AlarmKind.NOTIFICATION,
                            sourceApp = event.appName,
                            packageName = event.packageName,
                            ruleLabel = result.rule.label(),
                            message = event.displayMessage(),
                            timestamp = now,
                        ),
                    )
                }
            }
        }
    }

    /** Llamada telefónica detectada por el estado del teléfono (READ_PHONE_STATE). */
    suspend fun onPhoneCallRinging(): Outcome = mutex.withLock {
        val s = snapshot()
        if (!s.active) return@withLock Outcome.GUARD_INACTIVE
        if (!s.wakeOnPhoneCalls) return@withLock Outcome.NO_MATCH
        val now = clock()
        if (dedup.isDuplicateKey(PHONE_CALL_KEY, CALL_WINDOW_MS, now)) return@withLock Outcome.DUPLICATE
        fire(phoneCallRequest(null, now))
    }

    fun onPhoneCallAnswered() = alarm.onPhoneCallAnswered()

    fun seed(existing: List<NotificationEvent>) = dedup.seed(existing, clock())

    private suspend fun onCallLocked(event: NotificationEvent, now: Long): Outcome {
        val key = if (event.isPhoneCall) PHONE_CALL_KEY else "app-call|${event.packageName}|${event.title}"
        if (dedup.isDuplicateKey(key, CALL_WINDOW_MS, now)) return Outcome.DUPLICATE
        val request = if (event.isPhoneCall) {
            phoneCallRequest(event.title, now)
        } else {
            AlarmRequest(
                kind = AlarmKind.APP_CALL,
                sourceApp = event.appName,
                packageName = event.packageName,
                ruleLabel = "Llamada entrante de app",
                message = listOfNotNull(event.title, event.text).joinToString(" · ").ifBlank { "Llamada entrante" },
                timestamp = now,
            )
        }
        return fire(request)
    }

    private fun phoneCallRequest(caller: String?, now: Long) = AlarmRequest(
        kind = AlarmKind.PHONE_CALL,
        sourceApp = "Teléfono",
        packageName = null,
        ruleLabel = "Cualquier llamada entrante",
        message = caller?.takeIf { it.isNotBlank() }?.let { "Llamada entrante: $it" } ?: "Llamada entrante",
        timestamp = now,
    )

    private suspend fun fire(request: AlarmRequest): Outcome {
        if (alarm.isRinging()) {
            alarm.noteAdditionalMatch(request)
            history.log(HistoryType.MATCH_WHILE_RINGING, "Nuevo aviso con la alarma sonando", "${request.sourceApp} · ${request.ruleLabel}", request.timestamp)
            return Outcome.ALREADY_RINGING
        }
        alarm.trigger(request)
        history.log(HistoryType.ALARM, "Alarma activada", "${request.sourceApp} · ${request.ruleLabel}\n${request.message.take(140)}", request.timestamp)
        return Outcome.ALARM
    }

    /** Solo el nombre de la app (nunca el contenido), y como mucho una vez cada 10 min por app. */
    private suspend fun logIgnored(event: NotificationEvent, groupUnresolved: Boolean, now: Long) {
        val type = if (groupUnresolved) HistoryType.GROUP_UNRESOLVED else HistoryType.IGNORED
        val throttleKey = "$type|${event.packageName}"
        val last = lastIgnoredLog[throttleKey]
        if (last != null && now - last < ignoredLogIntervalMs) return
        lastIgnoredLog[throttleKey] = now
        if (groupUnresolved) {
            history.log(type, "WhatsApp: grupo no identificado", "La notificación no traía el nombre del grupo; no se ha podido comprobar la regla de grupo.", now)
        } else {
            history.log(type, "Notificación ignorada", event.appName, now)
        }
    }

    companion object {
        const val PHONE_CALL_KEY = "phone-call"
        const val CALL_WINDOW_MS = 90_000L
    }
}
