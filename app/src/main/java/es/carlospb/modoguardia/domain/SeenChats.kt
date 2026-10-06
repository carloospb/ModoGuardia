package es.carlospb.modoguardia.domain

/** Una conversación (grupo o chat individual) vista en un aviso, con la hora del último. */
data class SeenChat(
    val name: String,
    val isGroup: Boolean,
    val lastSeen: Long,
    val packageName: String = WhatsApp.KEY,
    /** shortcutId de la conversación, si la app lo da. */
    val conversationId: String? = null,
) {
    val ruleType: RuleType get() = if (isGroup) RuleType.GROUP else RuleType.PERSON

    /** App de la conversación (WhatsApp y WhatsApp Business son apps distintas). */
    val appKey: String get() = packageName
}

/**
 * «Conversaciones detectadas»: las que han mandado algún aviso, para elegirlas al crear reglas en
 * lugar de escribir el nombre. Ninguna app de chat deja a otras leer su lista de conversaciones:
 * esta es la única fuente pública y fiable.
 *
 * Se guarda lo mínimo para reconocer la conversación: app, nombre (el que trae el propio aviso,
 * limpio y sin «(3 mensajes)»), si es grupo, su identificador de Android si existe y la hora del
 * último aviso. Nunca mensajes ni remitentes. Las que llevan [EXPIRE_MS] sin avisar se olvidan.
 */
object SeenChats {
    const val MAX = 200

    /** Nueva visita a una conversación ya guardada: solo se reescribe si pasó al menos esto. */
    const val REFRESH_MS = 60 * 60 * 1000L

    const val EXPIRE_MS = 90L * 24 * 60 * 60 * 1000L

    /** La conversación de este aviso, o null si no es de una conversación o no se sabe cuál es. */
    fun chatOf(event: NotificationEvent, now: Long): SeenChat? {
        val c = Conversations.identify(event) ?: return null
        return SeenChat(c.name, c.isGroup, now, c.packageName, c.conversationId)
    }

    fun sameChat(a: SeenChat, b: SeenChat): Boolean {
        if (a.appKey != b.appKey || a.isGroup != b.isGroup) return false
        val ida = a.conversationId
        val idb = b.conversationId
        if (ida != null && idb != null) return ida == idb
        return TextNormalizer.groupKey(a.name) == TextNormalizer.groupKey(b.name)
    }

    /**
     * Lista tras ver [chat], o null si no cambia nada que merezca escribir en disco (misma
     * conversación vista hace menos de [REFRESH_MS]). Más recientes primero, como mucho [MAX].
     */
    fun record(current: List<SeenChat>, chat: SeenChat): List<SeenChat>? {
        val existing = current.firstOrNull { sameChat(it, chat) }
        if (existing != null && existing.name == chat.name && existing.conversationId == (chat.conversationId ?: existing.conversationId) &&
            chat.lastSeen - existing.lastSeen in 0 until REFRESH_MS
        ) {
            return null
        }
        val merged = chat.copy(conversationId = chat.conversationId ?: existing?.conversationId)
        return (listOf(merged) + current.filterNot { sameChat(it, chat) || chat.lastSeen - it.lastSeen > EXPIRE_MS }).take(MAX)
    }

    /**
     * Una línea por conversación: "hora<TAB>G|C<TAB>app<TAB>id<TAB>nombre". Lee también el formato
     * de la 1.1, de solo WhatsApp: "hora<TAB>G|C<TAB>nombre".
     */
    fun encode(chats: List<SeenChat>): String =
        chats.joinToString("\n") {
            listOf(
                it.lastSeen.toString(),
                if (it.isGroup) "G" else "C",
                it.packageName.clean(),
                it.conversationId?.clean().orEmpty(),
                it.name.clean(),
            ).joinToString("\t")
        }

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ')

    fun decode(raw: String?): List<SeenChat> =
        raw.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            val ts = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val isGroup = when (parts.getOrNull(1)) {
                "G" -> true
                "C" -> false
                else -> return@mapNotNull null
            }
            when (parts.size) {
                3 -> parts[2].takeIf { it.isNotBlank() }?.let { SeenChat(it, isGroup, ts) }
                5 -> {
                    val pkg = parts[2].takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    parts[4].takeIf { it.isNotBlank() }?.let { SeenChat(it, isGroup, ts, pkg, parts[3].takeIf(String::isNotBlank)) }
                }
                else -> null
            }
        }.toList()
}
