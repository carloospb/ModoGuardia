package es.carlospb.modoguardia.domain

/** Un mensaje de una notificación MessagingStyle (WhatsApp, Telegram, Teams...). */
data class MessageLine(
    val sender: String?,
    val text: String,
    /** Milisegundos epoch. 0 si la app no lo indica. */
    val timestamp: Long,
)

/**
 * Notificación normalizada. Contiene SOLO lo que Android entrega al NotificationListenerService;
 * nunca se completa con datos supuestos. Un campo nulo significa "Android/la app no lo dio".
 */
data class NotificationEvent(
    /** StatusBarNotification.key: identifica la notificación aunque se actualice. */
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val conversationTitle: String? = null,
    /** EXTRA_IS_GROUP_CONVERSATION. null = la app no lo indica. */
    val isGroupConversation: Boolean? = null,
    val messages: List<MessageLine> = emptyList(),
    val textLines: List<String> = emptyList(),
    val category: String? = null,
    /** Notificación de llamada entrante (CATEGORY_CALL + tipo entrante o full-screen intent). */
    val isIncomingCall: Boolean = false,
    /** Llamada entrante publicada por el marcador del teléfono (no por una app de VoIP). */
    val isPhoneCall: Boolean = false,
    /** StatusBarNotification.postTime. */
    val postTime: Long,
    /** Notification.when. */
    val whenTime: Long = postTime,
    /** Notification.getShortcutId(): la conversación en las apps de chat de Android 11+. Puede faltar. */
    val shortcutId: String? = null,
    /** Canal de la notificación (pista de grupo/individual en WhatsApp). */
    val channelId: String? = null,
    /** Personas del aviso (EXTRA_PEOPLE_LIST / EXTRA_CALL_PERSON): solo el nombre. */
    val personNames: List<String> = emptyList(),
    /** Números «tel:» de esas personas, si el aviso los trae (llamadas). */
    val personNumbers: List<String> = emptyList(),
) {
    val lastMessage: MessageLine?
        get() = messages.maxByOrNull { it.timestamp }

    /** El texto más representativo del aviso: el último mensaje o, si no hay, el texto. */
    fun visibleText(): String {
        lastMessage?.let { return it.text }
        return bigText?.takeIf { it.isNotBlank() }
            ?: text?.takeIf { it.isNotBlank() }
            ?: textLines.lastOrNull()
            ?: ""
    }

    /** Texto para mostrar en la alarma: "Remitente: mensaje" cuando el remitente se conoce. */
    fun displayMessage(): String {
        val last = lastMessage
        if (last != null) {
            val sender = last.sender?.takeIf { it.isNotBlank() }
            return if (sender != null) "$sender: ${last.text}" else last.text
        }
        return visibleText()
    }

    /** Todo el texto visible del aviso, para las reglas de palabras clave. */
    fun searchableText(): String = buildList {
        title?.let(::add)
        text?.let(::add)
        bigText?.let(::add)
        subText?.let(::add)
        conversationTitle?.let(::add)
        addAll(textLines)
        messages.forEach { m ->
            m.sender?.let(::add)
            add(m.text)
        }
    }.joinToString("\n")

    /**
     * Copia con solo los mensajes posteriores a [since]. WhatsApp repite en cada notificación
     * los mensajes anteriores del chat; sin esto, una palabra clave de un mensaje viejo haría
     * sonar la alarma cuando llega uno nuevo e inocuo. Los mensajes sin hora (0) se conservan.
     */
    fun onlyMessagesSince(since: Long): NotificationEvent {
        if (messages.isEmpty()) return this
        val recent = messages.filter { it.timestamp == 0L || it.timestamp >= since }
        return if (recent.isEmpty()) this else copy(messages = recent)
    }
}
