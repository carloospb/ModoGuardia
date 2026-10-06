package es.carlospb.modoguardia.domain

/**
 * Un chat compartido con Modo Guardia desde la propia app de chat. [packageName] es la app que lo
 * compartió si se sabe (null si no: el usuario la elige).
 */
data class SharedChat(val name: String, val packageName: String?)

/**
 * Lo que llega al compartir un chat con Modo Guardia desde WhatsApp o WhatsApp Business con
 * «Exportar chat»: Android entrega el asunto («Chat de WhatsApp con Soporte») y el nombre del
 * archivo («Chat de WhatsApp con Soporte.txt»). De ahí sale el nombre EXACTO del chat (persona o
 * grupo), el mismo que WhatsApp pone en sus avisos.
 *
 * Nunca se abre el archivo exportado: solo se leen el asunto y el nombre del archivo.
 *
 * Otras apps (Telegram, Slack, Signal…) no incluyen el nombre del chat cuando comparten algo con
 * otra app: para ellas, «Identificar con un mensaje» ([ChatCapture]).
 */
object SharedChats {
    /** Las frases de WhatsApp en los idiomas habituales, sin mayúsculas ni tildes. */
    private val patterns = listOf(
        Regex("""^chat de whatsapp(?: business)? con (.+)$"""),
        Regex("""^whatsapp(?: business)? chat with (.+)$"""),
        Regex("""^conversa do whatsapp(?: business)? com (.+)$"""),
        Regex("""^discussion whatsapp(?: business)? avec (.+)$"""),
        Regex("""^whatsapp(?: business)?[ -]chat mit (.+)$"""),
        Regex("""^chat (?:di )?whatsapp(?: business)? con (.+)$"""),
        Regex("""^xat (?:de |del )?whatsapp(?: business)? amb (.+)$"""),
        Regex("""^chat do whatsapp(?: business)? co?n (.+)$"""),
        Regex("""^whatsapp(?: business)?[ -]?(?:chat|gesprek|czat) (?:met|z) (.+)$"""),
        // Exportación en .zip: «WhatsApp Chat - Soporte», «Chat de WhatsApp - Soporte».
        Regex("""^whatsapp(?: business)? chat - (.+)$"""),
        Regex("""^chat de whatsapp(?: business)? - (.+)$"""),
    )

    private val extensions = Regex("""\.(txt|zip)$""", RegexOption.IGNORE_CASE)

    /**
     * El chat de lo compartido, o null si no es un chat exportado de WhatsApp. [sourcePackage] es la
     * app que lo compartió (si Android la dice); si no se sabe y el texto dice «WhatsApp Business», es
     * Business.
     */
    fun fromShare(subject: String?, title: String?, fileNames: List<String>, sourcePackage: String?): SharedChat? {
        val candidates = listOfNotNull(subject, title) + fileNames.map { it.replace(extensions, "") }
        for (raw in candidates) {
            val clean = TextNormalizer.clean(raw)
            val name = nameFrom(clean) ?: continue
            if (TextNormalizer.groupKey(name).isEmpty()) continue
            val pkg = when {
                sourcePackage != null && WhatsApp.isWhatsApp(sourcePackage) -> sourcePackage
                "whatsapp business" in TextNormalizer.fold(clean) -> WhatsApp.BUSINESS
                else -> null
            }
            return SharedChat(name, pkg)
        }
        return null
    }

    /** El nombre tal cual (con sus mayúsculas y emojis) de «Chat de WhatsApp con <nombre>». */
    private fun nameFrom(text: String): String? {
        val folded = TextNormalizer.fold(text)
        for (pattern in patterns) {
            val match = pattern.find(folded) ?: continue
            // Se busca sobre el texto sin tildes ni mayúsculas, pero el nombre se toma del original: la
            // frase de WhatsApp que va delante es ASCII y mide lo mismo en los dos.
            val start = match.groups[1]?.range?.first ?: continue
            if (start > text.length) continue
            val name = text.substring(start).trim().trim('"', '«', '»', '“', '”').trim()
            return name.takeIf { it.isNotBlank() }
        }
        return null
    }
}
