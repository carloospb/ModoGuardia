package es.carlospb.modoguardia.domain

/** Esta regla ya vigila esa conversación (misma app y tipo; mismo id de Android o mismo nombre). */
fun Rule.watches(chat: SeenChat): Boolean =
    type == chat.ruleType && appKey == chat.appKey &&
        ((conversationId != null && conversationId == chat.conversationId) ||
            TextNormalizer.groupKey(name.orEmpty()) == TextNormalizer.groupKey(chat.name))

/**
 * Cambios en la lista de reglas que hace la interfaz, como funciones puras (se prueban en la JVM).
 * Nunca duplican: añadir algo que ya existe lo deja como está (o lo reactiva).
 */
object RuleEdits {

    /** Regla «todas las notificaciones» de una app: WHATSAPP_ANY para WhatsApp, APP para el resto. */
    private fun isAllRule(rule: Rule, appKey: String) = when (rule.type) {
        RuleType.WHATSAPP_ANY -> appKey == WhatsApp.KEY
        RuleType.APP -> rule.appKey == appKey
        else -> false
    }

    /**
     * Encender o apagar «todos los avisos» de una app. Apagar PAUSA la regla en vez de borrarla: así la
     * app sigue en la lista (en pausa) mientras el usuario elige sus chats.
     */
    fun setAllNotifications(rules: List<Rule>, appKey: String, on: Boolean, label: String?, newId: () -> String): List<Rule> {
        if (!on) return rules.map { if (isAllRule(it, appKey)) it.copy(enabled = false) else it }
        if (rules.any { isAllRule(it, appKey) }) return rules.map { if (isAllRule(it, appKey)) it.copy(enabled = true) else it }
        val rule = if (appKey == WhatsApp.KEY) {
            Rule(id = newId(), type = RuleType.WHATSAPP_ANY)
        } else {
            Rule(id = newId(), type = RuleType.APP, packageName = appKey, appLabel = label)
        }
        return rules + rule
    }

    /** Clave de app con la que se agrupa un paquete: WhatsApp y Business van juntos. */
    fun appKeyOf(packageName: String): String = if (WhatsApp.isWhatsApp(packageName)) WhatsApp.KEY else packageName

    /** Todas las reglas de una app (todas, conversaciones y palabras clave de esa app). */
    fun removeApp(rules: List<Rule>, appKey: String): List<Rule> = rules.filterNot { !it.isCall && it.appKey == appKey }

    fun addConversations(rules: List<Rule>, chats: List<SeenChat>, label: (String) -> String?, newId: () -> String): List<Rule> {
        var out = rules
        chats.forEach { chat ->
            val existing = out.firstOrNull { it.watches(chat) }
            out = if (existing != null) {
                out.map { if (it.id == existing.id) it.copy(enabled = true, conversationId = it.conversationId ?: chat.conversationId) else it }
            } else {
                out + Rule(
                    id = newId(),
                    type = chat.ruleType,
                    packageName = chat.rulePackage,
                    appLabel = chat.rulePackage?.let(label),
                    name = chat.name,
                    conversationId = chat.conversationId,
                )
            }
        }
        return out
    }

    /** Conversación escrita a mano (aún no ha mandado ningún aviso). */
    fun addManualConversation(rules: List<Rule>, appKey: String, name: String, isGroup: Boolean, label: String?, newId: () -> String): List<Rule> {
        val clean = TextNormalizer.clean(name)
        if (TextNormalizer.groupKey(clean).isEmpty()) return rules
        val chat = SeenChat(clean, isGroup, 0L, packageName = appKey)
        return addConversations(rules, listOf(chat), { label }, newId)
    }

    /**
     * Contacto elegido en el selector de Android. Si ya hay una regla de ese contacto (mismo nombre),
     * se le añade el número; si no, se crea. Se guardan las dos formas del nombre («Carlos Pérez» y
     * «Pérez, Carlos») porque el marcador usa la que el usuario tenga configurada.
     */
    fun addCallContact(rules: List<Rule>, name: String, alternativeName: String?, number: String?, newId: () -> String): List<Rule> {
        val cleanName = TextNormalizer.clean(name)
        val key = TextNormalizer.groupKey(cleanName)
        val usableNumber = number?.trim()?.takeIf(PhoneNumbers::isUsable)
        if (key.isEmpty()) {
            // Contacto sin nombre: solo queda el número.
            return if (usableNumber == null) rules else addCallNumber(rules, usableNumber, null, newId)
        }
        val names = listOfNotNull(cleanName, alternativeName?.let(TextNormalizer::clean))
            .filter { TextNormalizer.groupKey(it).isNotEmpty() }
            .distinctBy(TextNormalizer::groupKey)
        val existing = rules.firstOrNull { r -> r.isCall && r.aliases.any { TextNormalizer.groupKey(it) == key } }
        if (existing != null) {
            return rules.map { r ->
                if (r.id != existing.id) return@map r
                r.copy(
                    enabled = true,
                    aliases = (r.aliases + names).distinctBy(TextNormalizer::groupKey),
                    numbers = if (usableNumber == null || r.numbers.any { PhoneNumbers.same(it, usableNumber) }) r.numbers else r.numbers + usableNumber,
                )
            }
        }
        return rules + Rule(id = newId(), type = RuleType.CALL, name = cleanName, aliases = names, numbers = listOfNotNull(usableNumber))
    }

    /**
     * Número escrito a mano, con etiqueta opcional («Empresa»). La etiqueta es solo para mostrar: no
     * se compara con el nombre que enseña el marcador. No duplica un número ya elegido.
     */
    fun addCallNumber(rules: List<Rule>, number: String, label: String?, newId: () -> String): List<Rule> {
        val usable = number.trim().takeIf(PhoneNumbers::isUsable) ?: return rules
        val existing = rules.firstOrNull { r -> r.isCall && r.numbers.any { PhoneNumbers.same(it, usable) } }
        if (existing != null) return rules.map { if (it.id == existing.id) it.copy(enabled = true) else it }
        return rules + Rule(
            id = newId(),
            type = RuleType.CALL,
            name = label?.let(TextNormalizer::clean)?.takeIf { it.isNotBlank() },
            numbers = listOf(usable),
        )
    }
}
