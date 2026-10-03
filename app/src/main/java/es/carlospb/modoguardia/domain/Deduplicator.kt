package es.carlospb.modoguardia.domain

/**
 * Evita alarmas repetidas cuando una app vuelve a publicar la misma notificación.
 *
 * WhatsApp re-publica la notificación de un chat cada vez que cambia algo (otro mensaje, lectura
 * en otro dispositivo, resumen...), repitiendo los mensajes anteriores. Se usan dos huellas:
 *
 *  * Exacta (12 h): último mensaje (remitente + texto + hora) o, sin MessagingStyle,
 *    clave + título + texto + `when`. Una re-publicación del mismo mensaje no vuelve a sonar.
 *    Una alerta idéntica posterior de una app de monitorización trae otro `when` y SÍ suena.
 *  * De contenido (2 min): app + conversación + texto visible. Cubre re-publicaciones que
 *    cambian la hora pero no el contenido.
 */
class Deduplicator(
    private val exactWindowMs: Long = 12 * 60 * 60 * 1000L,
    private val contentWindowMs: Long = 2 * 60 * 1000L,
    private val maxEntries: Int = 2000,
) {
    private val seen = object : LinkedHashMap<String, Long>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun isDuplicate(event: NotificationEvent, now: Long): Boolean {
        prune(now)
        val exact = fingerprint(event)
        val content = contentKey(event)
        val exactSeen = seen[exact]?.let { now - it < exactWindowMs } == true
        val contentSeen = seen[content]?.let { now - it < contentWindowMs } == true
        if (!exactSeen) seen[exact] = now
        seen.remove(content)
        seen[content] = now
        return exactSeen || contentSeen
    }

    /** Para eventos sin notificación (llamadas): duplicado si [key] se vio hace menos de [windowMs]. */
    @Synchronized
    fun isDuplicateKey(key: String, windowMs: Long, now: Long): Boolean {
        val previous = seen[key]
        val dup = previous != null && now - previous < windowMs
        if (!dup) {
            seen.remove(key)
            seen[key] = now
        }
        return dup
    }

    /** Marca como ya vistas las notificaciones que existían al iniciar la guardia. */
    @Synchronized
    fun seed(events: List<NotificationEvent>, now: Long) {
        events.forEach {
            seen[fingerprint(it)] = now
            seen[contentKey(it)] = now
        }
    }

    @Synchronized
    fun clear() = seen.clear()

    private fun prune(now: Long) {
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value > exactWindowMs) it.remove()
        }
    }

    companion object {
        fun fingerprint(e: NotificationEvent): String {
            val conversation = e.conversationTitle ?: e.title
            val last = e.lastMessage
            return if (last != null) {
                "m|${e.packageName}|$conversation|${last.sender}|${last.text}|${last.timestamp}"
            } else {
                "n|${e.packageName}|${e.key}|${e.title}|${e.visibleText()}|${e.whenTime}"
            }
        }

        fun contentKey(e: NotificationEvent): String =
            "c|${e.packageName}|${e.conversationTitle ?: e.title}|${e.visibleText()}"
    }
}
