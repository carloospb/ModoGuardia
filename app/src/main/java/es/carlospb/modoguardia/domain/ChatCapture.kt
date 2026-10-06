package es.carlospb.modoguardia.domain

/** Una forma de vigilar el chat de un aviso capturado: nombre y si es grupo. */
data class CaptureOption(val name: String, val isGroup: Boolean)

/**
 * Un aviso que llegó mientras el usuario esperaba para identificar un chat. [options] son las
 * formas de vigilarlo; una sola si Android dice si es persona o grupo, dos si no se sabe (el usuario
 * elige), ninguna si el aviso no dice de qué chat es ([problem] explica por qué).
 */
data class CaptureArrival(
    val packageName: String,
    val appName: String,
    val kind: ChatKind,
    val options: List<CaptureOption>,
    val conversationId: String?,
    val receivedAt: Long,
    val problem: String? = null,
) {
    /** Misma conversación (para no repetirla en la lista cuando escribe varias veces). */
    val key: String
        get() = "$packageName|${conversationId ?: options.joinToString("|") { "${it.isGroup}:${TextNormalizer.groupKey(it.name)}" }.ifEmpty { "?" + problem }}"
}

/**
 * «Identificar con un mensaje»: el usuario pulsa el botón, alguien escribe en el chat que quiere
 * vigilar y aquí se dice qué chat era, con lo que trae el aviso (como en [Conversations]). Vale para
 * cualquier app que publique avisos de sus chats: WhatsApp, Telegram, Slack, Signal, Teams...
 *
 * No se guarda nada: el resultado solo vive en memoria mientras la pantalla espera, y nunca incluye
 * el texto del mensaje.
 */
object ChatCapture {
    const val DURATION_MS = 10 * 60 * 1000L
    const val MAX = 12

    /**
     * Qué chat es el aviso, o null si no interesa mostrarlo (resúmenes, avisos persistentes, llamadas,
     * respuestas propias o, sin app elegida, avisos que no son de mensajería).
     */
    fun analyze(event: NotificationEvent, skipReason: String?, onlyApp: String?, now: Long): CaptureArrival? {
        if (onlyApp != null && event.packageName != onlyApp) return null
        if (skipReason != null || event.isIncomingCall || event.category == "call") return null
        val messaging = WhatsApp.isWhatsApp(event.packageName) || event.messages.isNotEmpty() ||
            event.conversationTitle != null || event.category == "msg"
        // Sin app elegida, solo chats: el resto (descargas, correo, sistema) sería ruido.
        if (onlyApp == null && !messaging) return null

        val kind = Conversations.kind(event)
        val title = event.title?.let(TextNormalizer::displayGroupName).orEmpty()
        val options = when (kind) {
            ChatKind.GROUP -> listOfNotNull(
                (event.conversationTitle ?: Conversations.groupCandidates(event).firstOrNull())?.let { CaptureOption(it, true) },
            )
            ChatKind.INDIVIDUAL -> listOfNotNull((event.title ?: event.conversationTitle)?.let { CaptureOption(it, false) })
            ChatKind.UNKNOWN -> unknownOptions(event, title)
        }.map { it.copy(name = TextNormalizer.displayGroupName(it.name)) }
            .filter { TextNormalizer.groupKey(it.name).isNotEmpty() && TextNormalizer.groupKey(it.name) != TextNormalizer.groupKey(event.appName) }
            .distinctBy { "${it.isGroup}:${TextNormalizer.groupKey(it.name)}" }

        val problem = when {
            options.isNotEmpty() -> null
            title.isEmpty() -> "El aviso no trae título: no dice de qué chat es."
            else -> "El aviso no dice de qué chat es (su título es «$title»)."
        }
        return CaptureArrival(
            packageName = event.packageName,
            appName = event.appName,
            kind = kind,
            options = options,
            conversationId = event.shortcutId?.takeIf { it.isNotBlank() && options.isNotEmpty() },
            receivedAt = now,
            problem = problem,
        )
    }

    /**
     * Android no dice si es persona o grupo: se ofrecen las dos lecturas del título y elige el usuario,
     * que sí sabe qué chat era. «Equipo: Ana» → el grupo «Equipo» o la persona «Equipo: Ana».
     */
    private fun unknownOptions(event: NotificationEvent, title: String): List<CaptureOption> {
        if (title.isEmpty()) return emptyList()
        val groups = Conversations.groupCandidates(event)
        return if (groups.isNotEmpty()) {
            groups.map { CaptureOption(it, true) } + CaptureOption(title, false)
        } else {
            listOf(CaptureOption(title, false), CaptureOption(title, true))
        }
    }

    /** Lista tras llegar [arrival]: la más reciente arriba, sin repetir chat, como mucho [MAX]. */
    fun add(current: List<CaptureArrival>, arrival: CaptureArrival): List<CaptureArrival> =
        (listOf(arrival) + current.filterNot { it.key == arrival.key }).take(MAX)
}
