package es.carlospb.modoguardia.service

import es.carlospb.modoguardia.domain.CallerId
import es.carlospb.modoguardia.domain.ChatKind
import es.carlospb.modoguardia.domain.Conversations
import es.carlospb.modoguardia.domain.DetectedConversation
import es.carlospb.modoguardia.domain.NotificationEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiagnosticEntry(
    val event: NotificationEvent,
    val skipReason: String?,
    val groupCandidates: List<String>,
    val receivedAt: Long,
    val kind: ChatKind = ChatKind.UNKNOWN,
    /** Conversación que Modo Guardia reconoce en el aviso (la que aparecería en la lista). */
    val conversation: DetectedConversation? = null,
    /** En avisos de llamada: quién llama según el aviso. */
    val caller: CallerId? = null,
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
        val e = parsed.event
        val entry = DiagnosticEntry(
            event = e,
            skipReason = parsed.skipReason,
            groupCandidates = Conversations.groupCandidates(e),
            receivedAt = now,
            kind = Conversations.kind(e),
            conversation = Conversations.identify(e),
            caller = if (e.isIncomingCall) CallerId.from(e) else null,
        )
        _recent.update { (listOf(entry) + it).take(MAX) }
    }

    fun clear() {
        _recent.value = emptyList()
    }

    private companion object {
        const val MAX = 15
    }
}
