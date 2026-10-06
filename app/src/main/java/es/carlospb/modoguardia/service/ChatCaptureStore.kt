package es.carlospb.modoguardia.service

import es.carlospb.modoguardia.domain.CaptureArrival
import es.carlospb.modoguardia.domain.ChatCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Espera en curso de «Identificar con un mensaje»: de qué app (null = cualquiera) y hasta cuándo. */
data class CaptureSession(
    val appKey: String?,
    val startedAt: Long,
    val until: Long,
    val arrivals: List<CaptureArrival> = emptyList(),
)

/**
 * «Identificar con un mensaje». SOLO en memoria y solo mientras el usuario espera en esa pantalla
 * (como mucho [ChatCapture.DURATION_MS]): el lector de notificaciones le pasa cada aviso y aquí se
 * queda el chat del que viene, nunca el mensaje.
 */
class ChatCaptureStore {
    private val _session = MutableStateFlow<CaptureSession?>(null)
    val session: StateFlow<CaptureSession?> = _session.asStateFlow()

    fun start(appKey: String?, now: Long = System.currentTimeMillis()) {
        _session.value = CaptureSession(appKey, now, now + ChatCapture.DURATION_MS)
    }

    fun stop() {
        _session.value = null
    }

    fun record(parsed: ParsedNotification, now: Long = System.currentTimeMillis()) {
        val current = _session.value ?: return
        if (now > current.until) {
            _session.value = null
            return
        }
        val arrival = ChatCapture.analyze(parsed.event, parsed.skipReason, current.appKey, now) ?: return
        _session.update { s -> s?.copy(arrivals = ChatCapture.add(s.arrivals, arrival)) }
    }
}
