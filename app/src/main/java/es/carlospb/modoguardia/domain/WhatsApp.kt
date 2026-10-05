package es.carlospb.modoguardia.domain

/**
 * Lectura de la estructura de las notificaciones de WhatsApp.
 *
 * WhatsApp no documenta su formato y cambia entre versiones, así que no se asume una sola forma.
 * Se reúnen como candidatos a "nombre del grupo" todos los campos donde las versiones conocidas
 * lo ponen, y una regla de grupo solo coincide si su nombre es IGUAL (normalizado) a alguno:
 *
 *  * `conversationTitle` (MessagingStyle, Android 7+): el nombre del grupo.
 *  * `title` con la forma "Grupo: Remitente" o "Grupo (3 mensajes): Remitente".
 *  * `title` con la forma antigua "Remitente @ Grupo".
 *  * `title` completo cuando la app marca la conversación como grupal.
 *
 * Si no hay ningún candidato, el grupo NO se inventa: el motor lo informa como "no identificado".
 */
enum class ChatKind { GROUP, INDIVIDUAL, UNKNOWN }

object WhatsApp {
    val PACKAGES: Set<String> = setOf("com.whatsapp", "com.whatsapp.w4b")

    fun isWhatsApp(packageName: String): Boolean = packageName in PACKAGES

    /**
     * Grupo o chat individual. Con la marca de Android (EXTRA_IS_GROUP_CONVERSATION) es seguro; sin
     * ella se deduce solo en los casos claros y si no, UNKNOWN: no se inventa.
     */
    fun chatKind(event: NotificationEvent): ChatKind {
        event.isGroupConversation?.let { return if (it) ChatKind.GROUP else ChatKind.INDIVIDUAL }
        val title = event.title?.let(TextNormalizer::displayGroupName).orEmpty()
        val conversation = event.conversationTitle?.let(TextNormalizer::displayGroupName).orEmpty()
        return when {
            conversation.isNotEmpty() && conversation != title -> ChatKind.GROUP
            title.isEmpty() || ": " in title || " @ " in title -> ChatKind.UNKNOWN
            else -> ChatKind.INDIVIDUAL
        }
    }

    /**
     * Nombres con los que puede identificarse un chat INDIVIDUAL: el título (nombre del contacto),
     * conversationTitle y, si Android confirma que no es un grupo, los remitentes. Vacío si es un
     * grupo o no se sabe: así un mensaje de Ana en un grupo no dispara la regla del chat con Ana.
     */
    fun chatCandidates(event: NotificationEvent): List<String> {
        if (chatKind(event) != ChatKind.INDIVIDUAL) return emptyList()
        val out = LinkedHashSet<String>()
        event.title?.let { out += TextNormalizer.clean(it) }
        event.conversationTitle?.let { out += TextNormalizer.clean(it) }
        if (event.isGroupConversation == false) event.messages.mapNotNullTo(out) { it.sender?.let(TextNormalizer::clean) }
        return out.filter { it.isNotBlank() }
    }

    fun groupCandidates(event: NotificationEvent): List<String> {
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
}
