package es.carlospb.modoguardia.domain

/** Un chat de WhatsApp (grupo o individual) visto en una notificación, con la hora del último aviso. */
data class SeenChat(val name: String, val isGroup: Boolean, val lastSeen: Long) {
    val ruleType: RuleType get() = if (isGroup) RuleType.WHATSAPP_GROUP else RuleType.WHATSAPP_CHAT
}

/**
 * Lista de chats de WhatsApp vistos en las notificaciones, para elegirlos al crear reglas en lugar
 * de escribir el nombre a mano. WhatsApp no deja a otras apps leer su lista de chats: esta es la
 * única fuente pública y fiable, y solo contiene chats que han mandado algún aviso.
 *
 * Se guarda el nombre que trae la propia notificación (solo limpio y sin «(3 mensajes)»), que es
 * uno de los candidatos con los que [RuleEngine] compara y tiene la misma clave: una regla creada
 * desde esta lista coincide siempre que WhatsApp siga mandando ese nombre.
 * Solo nombres, nunca mensajes.
 */
object SeenChats {
    const val MAX = 100

    /** Nueva visita a un chat ya guardado: solo se reescribe si pasó al menos esto. */
    const val REFRESH_MS = 60 * 60 * 1000L

    /** El chat de este aviso, o null si no se sabe con seguridad cuál es o si es grupo. */
    fun chatOf(event: NotificationEvent, now: Long): SeenChat? {
        if (!WhatsApp.isWhatsApp(event.packageName)) return null
        val (raw, isGroup) = when (WhatsApp.chatKind(event)) {
            ChatKind.GROUP -> event.conversationTitle to true
            ChatKind.INDIVIDUAL -> (event.title ?: event.conversationTitle) to false
            ChatKind.UNKNOWN -> return null
        }
        val name = raw?.let(TextNormalizer::displayGroupName)?.takeIf { it.isNotBlank() } ?: return null
        return SeenChat(name, isGroup, now)
    }

    private fun sameChat(a: SeenChat, b: SeenChat) =
        a.isGroup == b.isGroup && TextNormalizer.groupKey(a.name) == TextNormalizer.groupKey(b.name)

    /**
     * Lista tras ver [chat], o null si no cambia nada que merezca escribir en disco (mismo chat
     * visto hace menos de [REFRESH_MS]). Más recientes primero, como mucho [MAX].
     */
    fun record(current: List<SeenChat>, chat: SeenChat): List<SeenChat>? {
        val existing = current.firstOrNull { sameChat(it, chat) }
        if (existing != null && existing.name == chat.name && chat.lastSeen - existing.lastSeen in 0 until REFRESH_MS) return null
        return (listOf(chat) + current.filterNot { sameChat(it, chat) }).take(MAX)
    }

    /** Una línea por chat: "hora<TAB>G|C<TAB>nombre". */
    fun encode(chats: List<SeenChat>): String =
        chats.joinToString("\n") {
            "${it.lastSeen}\t${if (it.isGroup) "G" else "C"}\t${it.name.replace('\t', ' ').replace('\n', ' ')}"
        }

    fun decode(raw: String?): List<SeenChat> =
        raw.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size != 3) return@mapNotNull null
            val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
            val isGroup = when (parts[1]) {
                "G" -> true
                "C" -> false
                else -> return@mapNotNull null
            }
            parts[2].takeIf { it.isNotBlank() }?.let { SeenChat(it, isGroup, ts) }
        }.toList()
}
