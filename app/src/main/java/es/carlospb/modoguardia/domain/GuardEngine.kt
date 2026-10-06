package es.carlospb.modoguardia.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AlarmKind { NOTIFICATION, PHONE_CALL, APP_CALL, TEST }

/** Lo que la alarma muestra: origen, regla, conversación o quién llama, mensaje y hora. */
data class AlarmRequest(
    val kind: AlarmKind,
    val sourceApp: String,
    val packageName: String?,
    val ruleLabel: String,
    /** Solo para la pantalla de alarma: nunca se guarda en el historial. */
    val message: String,
    val timestamp: Long,
    /** Grupo, chat o quién llama, si se conoce. */
    val conversation: String? = null,
)

/** Estado de guardia que necesita el motor, leído en cada evento. */
data class GuardSnapshot(
    val active: Boolean,
    val startedAt: Long,
    val rules: List<Rule>,
    val wakeOnPhoneCalls: Boolean,
    val wakeOnAppCalls: Boolean,
    val callMode: CallMode = CallMode.ALL,
    /** Con [CallMode.SELECTED]: si no se puede saber quién llama, despertar igualmente. */
    val wakeOnUnidentifiedCalls: Boolean = true,
    /**
     * La señal de llamada trae el número exacto (permisos «Teléfono» y «Registro de llamadas»): si el
     * aviso del marcador llega antes, merece la pena esperarla.
     */
    val exactNumberAvailable: Boolean = false,
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

enum class Outcome {
    GUARD_INACTIVE, PREEXISTING, NO_MATCH, DUPLICATE, ALREADY_RINGING, ALARM,

    /** Suena el teléfono y aún no se sabe quién llama: hay que llamar a [GuardEngine.onCallerTimeout]. */
    AWAITING_CALLER,
}

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

    /** Llamada en curso con [CallMode.SELECTED]: lo que se va sabiendo de quién llama. */
    private var call: CallSession? = null

    private enum class CallOutcome { PENDING, ALARMED, IGNORED }

    /**
     * Una llamada entrante. Las pruebas de quién llama llegan por separado y en cualquier orden: la
     * señal RINGING (con el número solo si hay «Registro de llamadas») y el aviso del marcador (nombre
     * o número). Se acumulan y se decide una sola vez.
     */
    private class CallSession(val startedAt: Long) {
        val names = LinkedHashSet<String>()
        val numbers = LinkedHashSet<String>()

        /** Llegó la señal RINGING de Android: al terminar llegará IDLE u OFFHOOK. */
        var ringing = false

        /** Hay un temporizador de identificación en marcha ([onCallerTimeout]). */
        var awaiting = false

        /** La señal de llamada dijo que el número está oculto (solo con «Registro de llamadas»). */
        var withheld = false

        /** Contestada: el usuario está despierto; esta llamada ya no hace sonar la alarma. */
        var answered = false

        /** Notification.when del aviso del marcador: distingue una llamada de la siguiente. */
        var callWhen: Long? = null
        var outcome = CallOutcome.PENDING

        fun add(caller: CallerId) {
            names += caller.names
            numbers += caller.numbers
        }

        fun caller() = CallerId(names.toList(), numbers.toList())
    }

    private fun session(now: Long): CallSession {
        val current = call
        if (current != null && now - current.startedAt < CALL_SESSION_MS) return current
        return CallSession(now).also { call = it }
    }

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
            if (event.isPhoneCall && s.wakeOnPhoneCalls && s.callMode == CallMode.SELECTED) {
                return@withLock onSelectedPhoneCallLocked(event, s, now)
            }
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
                            conversation = if (result.rule.isConversation) result.rule.label() else Conversations.displayName(event),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Llamada telefónica detectada por el estado del teléfono (READ_PHONE_STATE). Esa señal solo trae
     * el número si el usuario ha concedido «Registro de llamadas» ([number]: el número, "" si está
     * oculto, null si no lo trae); si no, no dice quién llama. Con [CallMode.SELECTED], si aún no se
     * sabe, devuelve [Outcome.AWAITING_CALLER] y hay que llamar a [onCallerTimeout] pasados
     * [CALLER_TIMEOUT_MS]: mientras, puede llegar el aviso del marcador con el nombre o el número.
     */
    suspend fun onPhoneCallRinging(number: String? = null): Outcome = mutex.withLock {
        val s = snapshot()
        if (!s.active) return@withLock Outcome.GUARD_INACTIVE
        if (!s.wakeOnPhoneCalls) return@withLock Outcome.NO_MATCH
        val now = clock()
        if (s.callMode == CallMode.ALL) {
            if (dedup.isDuplicateKey(PHONE_CALL_KEY, CALL_WINDOW_MS, now)) return@withLock Outcome.DUPLICATE
            return@withLock fire(phoneCallRequest(number?.takeIf(PhoneNumbers::isUsable), null, now))
        }
        val session = session(now)
        session.ringing = true
        when {
            number == null -> Unit
            number.isBlank() -> session.withheld = true
            PhoneNumbers.isUsable(number) -> session.numbers += PhoneNumbers.digits(number)
        }
        if (session.outcome == CallOutcome.ALARMED) return@withLock Outcome.DUPLICATE
        val result = evaluate(session, s)
        if (result is CallEvaluation.Match) return@withLock fireSelectedCall(session, result.rule, now)
        // Ya decidido por el aviso del marcador (ignorada): un número que no coincide no cambia nada.
        if (session.outcome != CallOutcome.PENDING) return@withLock Outcome.NO_MATCH
        session.awaiting = true
        Outcome.AWAITING_CALLER
    }

    /**
     * Han pasado [CALLER_TIMEOUT_MS] desde que empezó a esperarse quién llama. Si con lo que se sabe
     * se puede decidir, se decide; si no, se aplica la preferencia del usuario para llamadas sin
     * identificar.
     */
    suspend fun onCallerTimeout(): Outcome = mutex.withLock {
        val session = call ?: return@withLock Outcome.NO_MATCH
        if (!session.awaiting || session.outcome != CallOutcome.PENDING || session.answered) return@withLock Outcome.NO_MATCH
        val s = snapshot()
        if (!s.active) return@withLock Outcome.GUARD_INACTIVE
        if (!s.wakeOnPhoneCalls || s.callMode != CallMode.SELECTED) return@withLock Outcome.NO_MATCH
        decideSelectedCallLocked(session, s, clock())
    }

    /**
     * El teléfono deja de sonar (colgaron o se perdió). Si aún no se había decidido, se decide ahora
     * con lo que se sepa: una llamada sin identificar que cuelga a los pocos segundos también
     * despierta, si el usuario lo eligió así. Después, la llamada ya no cuenta.
     */
    suspend fun onPhoneIdle(): Outcome = mutex.withLock {
        val session = call
        call = null
        if (session == null || !session.awaiting || session.outcome != CallOutcome.PENDING || session.answered) {
            return@withLock Outcome.NO_MATCH
        }
        val s = snapshot()
        if (!s.active) return@withLock Outcome.GUARD_INACTIVE
        if (!s.wakeOnPhoneCalls || s.callMode != CallMode.SELECTED) return@withLock Outcome.NO_MATCH
        decideSelectedCallLocked(session, s, clock())
    }

    suspend fun onPhoneCallAnswered() {
        // Contestada: el usuario está despierto; no hace falta decidir si despertarle.
        mutex.withLock {
            call?.let {
                it.answered = true
                if (it.outcome == CallOutcome.PENDING) it.outcome = CallOutcome.IGNORED
            }
        }
        alarm.onPhoneCallAnswered()
    }

    fun seed(existing: List<NotificationEvent>) = dedup.seed(existing, clock())

    /**
     * Evaluación con lo acumulado. Con el número oculto, lo que enseñe la pantalla (un «número privado»
     * en un idioma no previsto) no basta para descartar a nadie: queda «sin identificar».
     */
    private fun evaluate(session: CallSession, s: GuardSnapshot): CallEvaluation {
        val result = RuleEngine.evaluateCall(session.caller(), s.rules)
        val hasCallRules = s.rules.any { it.enabled && it.isCall && it.isValid() }
        return if (result == CallEvaluation.NoMatch && session.withheld && session.numbers.isEmpty() && hasCallRules) {
            CallEvaluation.Unidentified
        } else {
            result
        }
    }

    /** Aviso de llamada del marcador con [CallMode.SELECTED]: solo suenan los contactos y números elegidos. */
    private suspend fun onSelectedPhoneCallLocked(event: NotificationEvent, s: GuardSnapshot, now: Long): Outcome {
        var session = session(now)
        // Sin la señal de llamada (sin permiso «Teléfono») no llega IDLE entre llamadas: una llamada
        // nueva se reconoce porque su aviso trae otra hora de inicio.
        if (!session.ringing && session.callWhen != null && session.callWhen != event.whenTime) {
            session = CallSession(now).also { call = it }
        }
        session.callWhen = event.whenTime
        session.add(CallerId.from(event))
        if (session.outcome == CallOutcome.ALARMED) return Outcome.DUPLICATE
        if (session.answered) return Outcome.NO_MATCH
        val result = evaluate(session, s)
        if (result is CallEvaluation.Match) return fireSelectedCall(session, result.rule, now)
        if (session.outcome != CallOutcome.PENDING) return Outcome.NO_MATCH
        // Sin identificar, pero aún puede llegar más (el temporizador ya corre, o la señal con el
        // número exacto está en camino): se espera en vez de decidir ya.
        if (result is CallEvaluation.Unidentified && (session.awaiting || s.exactNumberAvailable)) {
            session.awaiting = true
            return Outcome.AWAITING_CALLER
        }
        return decideSelectedCallLocked(session, s, now)
    }

    private suspend fun decideSelectedCallLocked(session: CallSession, s: GuardSnapshot, now: Long): Outcome =
        when (val result = evaluate(session, s)) {
            is CallEvaluation.Match -> fireSelectedCall(session, result.rule, now)
            CallEvaluation.NoMatch -> {
                session.outcome = CallOutcome.IGNORED
                logThrottled(HistoryType.IGNORED, "call", "Llamada ignorada", "No es de ningún contacto o número elegido.", now)
                Outcome.NO_MATCH
            }
            CallEvaluation.Unidentified -> {
                val hasCallRules = s.rules.any { it.enabled && it.isCall && it.isValid() }
                if (s.wakeOnUnidentifiedCalls && hasCallRules) {
                    session.outcome = CallOutcome.ALARMED
                    if (dedup.isDuplicateKey(PHONE_CALL_KEY, CALL_WINDOW_MS, now)) {
                        Outcome.DUPLICATE
                    } else {
                        fire(phoneCallRequest(session.caller().display(), "Llamada sin identificar", now))
                    }
                } else {
                    session.outcome = CallOutcome.IGNORED
                    logThrottled(HistoryType.IGNORED, "call-unknown", "Llamada sin identificar ignorada", "No se pudo saber quién llamaba.", now)
                    Outcome.NO_MATCH
                }
            }
        }

    private suspend fun fireSelectedCall(session: CallSession, rule: Rule, now: Long): Outcome {
        // Ya contestada: el usuario habla por teléfono; una alarma encima no tiene sentido.
        if (session.answered) return Outcome.NO_MATCH
        session.outcome = CallOutcome.ALARMED
        if (dedup.isDuplicateKey(PHONE_CALL_KEY, CALL_WINDOW_MS, now)) return Outcome.DUPLICATE
        // Quién llama: el nombre que enseña el marcador, o el de la regla («Empresa»), o el número.
        val who = session.names.firstOrNull() ?: rule.label().takeIf { it.isNotBlank() } ?: session.numbers.firstOrNull()
        return fire(phoneCallRequest(who, rule.label(), now))
    }

    private suspend fun onCallLocked(event: NotificationEvent, now: Long): Outcome {
        val key = if (event.isPhoneCall) PHONE_CALL_KEY else "app-call|${event.packageName}|${event.title}"
        if (dedup.isDuplicateKey(key, CALL_WINDOW_MS, now)) return Outcome.DUPLICATE
        val request = if (event.isPhoneCall) {
            phoneCallRequest(CallerId.from(event).display(), null, now)
        } else {
            AlarmRequest(
                kind = AlarmKind.APP_CALL,
                sourceApp = event.appName,
                packageName = event.packageName,
                ruleLabel = "Llamada entrante de app",
                message = listOfNotNull(event.title, event.text).joinToString(" · ").ifBlank { "Llamada entrante" },
                timestamp = now,
                conversation = event.title?.let(TextNormalizer::clean)?.takeIf { it.isNotBlank() },
            )
        }
        return fire(request)
    }

    private fun phoneCallRequest(caller: String?, ruleLabel: String?, now: Long) = AlarmRequest(
        kind = AlarmKind.PHONE_CALL,
        sourceApp = "Teléfono",
        packageName = null,
        ruleLabel = ruleLabel ?: "Cualquier llamada entrante",
        message = caller?.takeIf { it.isNotBlank() }?.let { "Llamada entrante: $it" } ?: "Llamada entrante",
        timestamp = now,
        conversation = caller?.takeIf { it.isNotBlank() },
    )

    private suspend fun fire(request: AlarmRequest): Outcome {
        if (alarm.isRinging()) {
            alarm.noteAdditionalMatch(request)
            history.log(HistoryType.MATCH_WHILE_RINGING, "Nuevo aviso con la alarma sonando", historyDetail(request), request.timestamp)
            return Outcome.ALREADY_RINGING
        }
        alarm.trigger(request)
        // Sin el texto del mensaje: el historial guarda solo origen y conversación (privacidad).
        history.log(HistoryType.ALARM, "Alarma activada", historyDetail(request), request.timestamp)
        return Outcome.ALARM
    }

    /** «WhatsApp · Carlos», «Teléfono · Empresa», «Gmail · Palabras: URGENTE». */
    private fun historyDetail(r: AlarmRequest): String {
        val who = r.conversation?.takeIf { it.isNotBlank() }
        return when {
            who == null -> "${r.sourceApp} · ${r.ruleLabel}"
            TextNormalizer.groupKey(who) == TextNormalizer.groupKey(r.ruleLabel) -> "${r.sourceApp} · $who"
            else -> "${r.sourceApp} · $who · ${r.ruleLabel}"
        }
    }

    private suspend fun logThrottled(type: HistoryType, key: String, title: String, detail: String, now: Long) {
        val throttleKey = "$type|$key"
        val last = lastIgnoredLog[throttleKey]
        if (last != null && now - last < ignoredLogIntervalMs) return
        lastIgnoredLog[throttleKey] = now
        history.log(type, title, detail, now)
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

        /** Espera máxima al aviso del marcador para saber quién llama. */
        const val CALLER_TIMEOUT_MS = 6_000L

        /** Las pruebas de quién llama se acumulan durante este tiempo (una llamada suena menos). */
        const val CALL_SESSION_MS = 90_000L
    }
}
