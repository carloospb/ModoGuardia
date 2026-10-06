package es.carlospb.modoguardia.domain

enum class ChatKind { GROUP, INDIVIDUAL, UNKNOWN }

/** Conversación identificada en un aviso: solo lo necesario para reconocerla después. */
data class DetectedConversation(
    val packageName: String,
    val name: String,
    val isGroup: Boolean,
    /** `Notification.getShortcutId()`: identificador de la conversación en esa app. Opaco; puede faltar. */
    val conversationId: String?,
)

/**
 * Identificación de conversaciones (grupo o chat individual) con lo que trae la notificación, para
 * cualquier app de mensajería que use el formato estándar de Android (WhatsApp, Telegram, Signal,
 * Mensajes...). Ninguna de ellas deja leer su lista de chats: el aviso es la única fuente pública.
 *
 * Campos usados, por orden de fiabilidad:
 *  * `EXTRA_IS_GROUP_CONVERSATION` (MessagingStyle): si la app lo indica, es la verdad.
 *  * `conversationTitle` distinto del título: es un grupo (el título suele ser el remitente).
 *  * En WhatsApp, el canal de la notificación (`group_chat_*` / `individual_chat_*`), solo como pista.
 *  * El título con formas «Grupo: Remitente» o «Remitente @ Grupo» (versiones antiguas de WhatsApp).
 *
 * Si no se puede saber, es [ChatKind.UNKNOWN]: no se inventa. Una regla de persona nunca coincide con
 * un aviso de grupo, para que un mensaje de Ana en un grupo no dispare la regla del chat con Ana.
 */
object Conversations {

    fun kind(event: NotificationEvent): ChatKind {
        event.isGroupConversation?.let { return if (it) ChatKind.GROUP else ChatKind.INDIVIDUAL }
        val title = event.title?.let(TextNormalizer::displayGroupName).orEmpty()
        val conversation = event.conversationTitle?.let(TextNormalizer::displayGroupName).orEmpty()
        if (conversation.isNotEmpty() && conversation != title) return ChatKind.GROUP
        whatsAppChannelHint(event)?.let { return it }
        return when {
            title.isEmpty() || ": " in title || " @ " in title -> ChatKind.UNKNOWN
            else -> ChatKind.INDIVIDUAL
        }
    }

    private fun whatsAppChannelHint(event: NotificationEvent): ChatKind? {
        if (!WhatsApp.isWhatsApp(event.packageName)) return null
        val channel = event.channelId?.lowercase() ?: return null
        return when {
            channel.startsWith("group_chat") -> ChatKind.GROUP
            channel.startsWith("individual_chat") -> ChatKind.INDIVIDUAL
            else -> null
        }
    }

    /**
     * Nombres con los que puede identificarse el GRUPO de este aviso. Una regla de grupo coincide si
     * su nombre es IGUAL (normalizado) a alguno. Vacío si el aviso es de un chat individual.
     */
    fun groupCandidates(event: NotificationEvent): List<String> {
        if (kind(event) == ChatKind.INDIVIDUAL) return emptyList()
        val out = LinkedHashSet<String>()
        event.conversationTitle?.let { out += TextNormalizer.clean(it) }
        event.title?.let { raw ->
            val title = TextNormalizer.clean(raw)
            val first = title.indexOf(": ")
            if (first > 0) out += title.substring(0, first)
            val last = title.lastIndexOf(": ")
            if (last > 0 && last != first) out += title.substring(0, last)
            val at = title.indexOf(" @ ")
            if (at > 0) out += title.substring(at + 3)
            if (event.isGroupConversation == true) out += title
        }
        return out.filter { it.isNotBlank() }
    }

    /**
     * Nombres con los que puede identificarse un chat INDIVIDUAL: el título (nombre del contacto),
     * conversationTitle y, si Android confirma que no es un grupo, los remitentes. Vacío si es un
     * grupo o no se sabe.
     */
    fun personCandidates(event: NotificationEvent): List<String> {
        if (kind(event) != ChatKind.INDIVIDUAL) return emptyList()
        val out = LinkedHashSet<String>()
        event.title?.let { out += TextNormalizer.clean(it) }
        event.conversationTitle?.let { out += TextNormalizer.clean(it) }
        if (event.isGroupConversation == false) event.messages.mapNotNullTo(out) { it.sender?.let(TextNormalizer::clean) }
        return out.filter { it.isNotBlank() }
    }

    /**
     * La conversación de este aviso, para la lista de «Conversaciones detectadas», o null si el aviso
     * no es de una conversación o no se sabe con seguridad cuál es.
     */
    fun identify(event: NotificationEvent): DetectedConversation? {
        if (event.isIncomingCall || event.category == CATEGORY_CALL) return null
        val messaging = WhatsApp.isWhatsApp(event.packageName) || event.messages.isNotEmpty() ||
            event.conversationTitle != null || event.category == CATEGORY_MESSAGE
        if (!messaging) return null
        val (raw, isGroup) = when (kind(event)) {
            ChatKind.GROUP -> (event.conversationTitle ?: groupCandidates(event).firstOrNull()) to true
            ChatKind.INDIVIDUAL -> (event.title ?: event.conversationTitle) to false
            ChatKind.UNKNOWN -> return null
        }
        val name = raw?.let(TextNormalizer::displayGroupName)?.takeIf { it.isNotBlank() } ?: return null
        // «WhatsApp», «Telegram»: avisos genéricos de la app, no una conversación.
        if (TextNormalizer.groupKey(name) == TextNormalizer.groupKey(event.appName)) return null
        return DetectedConversation(event.packageName, name, isGroup, event.shortcutId?.takeIf { it.isNotBlank() })
    }

    /** Nombre de la conversación para mostrar en la alarma, si se conoce. */
    fun displayName(event: NotificationEvent): String? = identify(event)?.name

    private const val CATEGORY_CALL = "call"
    private const val CATEGORY_MESSAGE = "msg"
}
