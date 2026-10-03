package es.carlospb.modoguardia.service

import es.carlospb.modoguardia.domain.NotificationEvent
import es.carlospb.modoguardia.domain.WhatsApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiagnosticEntry(
    val event: NotificationEvent,
    val skipReason: String?,
    val groupCandidates: List<String>,
    val receivedAt: Long,
)

/**
 * Diagnóstico de notificaciones. SOLO en memoria (nunca en disco) y solo si el usuario lo activa;
 * al desactivarlo se borra. Sirve para ver cómo estructura WhatsApp los avisos en cada teléfono.
 *
 * [lastSeen] guarda únicamente package → hora del último aviso, sin contenido: permite comprobar
 * que las apps de las reglas están llegando de verdad al detector.
 */
class DiagnosticsStore {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _recent = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val recent: StateFlow<List<DiagnosticEntry>> = _recent.asStateFlow()

    private val _lastSeen = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastSeen: StateFlow<Map<String, Long>> = _lastSeen.asStateFlow()

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        if (!value) _recent.value = emptyList()
    }

    fun record(parsed: ParsedNotification, now: Long = System.currentTimeMillis()) {
        _lastSeen.update { it + (parsed.event.packageName to now) }
        if (!_enabled.value) return
        val candidates = if (WhatsApp.isWhatsApp(parsed.event.packageName)) WhatsApp.groupCandidates(parsed.event) else emptyList()
        val entry = DiagnosticEntry(parsed.event, parsed.skipReason, candidates, now)
        _recent.update { (listOf(entry) + it).take(MAX) }
    }

    fun clear() {
        _recent.value = emptyList()
    }

    private companion object {
        const val MAX = 15
    }
}
