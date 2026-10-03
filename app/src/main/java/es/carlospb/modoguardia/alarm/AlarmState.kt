package es.carlospb.modoguardia.alarm

import android.content.Intent
import es.carlospb.modoguardia.domain.AlarmKind
import es.carlospb.modoguardia.domain.AlarmRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ActiveAlarm(
    val request: AlarmRequest,
    val startedAt: Long,
    /** Coincidencias recibidas mientras ya sonaba. */
    val extraMatches: Int = 0,
    /** Android no dejó arrancar el servicio: suena la notificación insistente de reserva. */
    val viaFallback: Boolean = false,
    /** Aviso para el usuario (p. ej. "el volumen estaba a 0 y se ha subido"). */
    val note: String? = null,
)

/** Estado de la alarma en el proceso. Fuente única para el servicio, la pantalla de alerta y la UI. */
object AlarmState {
    private val _active = MutableStateFlow<ActiveAlarm?>(null)
    val active: StateFlow<ActiveAlarm?> = _active.asStateFlow()

    /** Segundos restantes de una prueba de alarma; null si no hay cuenta atrás. */
    private val _countdown = MutableStateFlow<Int?>(null)
    val countdown: StateFlow<Int?> = _countdown.asStateFlow()

    fun set(alarm: ActiveAlarm?) {
        _active.value = alarm
    }

    fun update(block: (ActiveAlarm) -> ActiveAlarm) = _active.update { it?.let(block) }

    fun setCountdown(seconds: Int?) {
        _countdown.value = seconds
    }
}

internal object AlarmExtras {
    private const val KIND = "kind"
    private const val SOURCE = "source"
    private const val PACKAGE = "package"
    private const val RULE = "rule"
    private const val MESSAGE = "message"
    private const val TIME = "time"

    fun put(intent: Intent, r: AlarmRequest): Intent = intent
        .putExtra(KIND, r.kind.name)
        .putExtra(SOURCE, r.sourceApp)
        .putExtra(PACKAGE, r.packageName)
        .putExtra(RULE, r.ruleLabel)
        .putExtra(MESSAGE, r.message)
        .putExtra(TIME, r.timestamp)

    fun read(intent: Intent?): AlarmRequest? {
        if (intent == null || !intent.hasExtra(SOURCE)) return null
        return AlarmRequest(
            kind = AlarmKind.entries.firstOrNull { it.name == intent.getStringExtra(KIND) } ?: AlarmKind.NOTIFICATION,
            sourceApp = intent.getStringExtra(SOURCE).orEmpty(),
            packageName = intent.getStringExtra(PACKAGE),
            ruleLabel = intent.getStringExtra(RULE).orEmpty(),
            message = intent.getStringExtra(MESSAGE).orEmpty(),
            timestamp = intent.getLongExtra(TIME, System.currentTimeMillis()),
        )
    }
}
