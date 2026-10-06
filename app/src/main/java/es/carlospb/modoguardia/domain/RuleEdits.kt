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

    /** Regla «todas las notificaciones» de una app (WHATSAPP_ANY solo en reglas heredadas). */
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
        return rules + Rule(id = newId(), type = RuleType.APP, packageName = appKey, appLabel = label)
    }

    /**
     * WhatsApp y WhatsApp Business por separado, sin perder nada de lo que ya sonaba:
     *  * Reglas que valían para las dos a la vez (sin app: las de 1.0/1.1 y las de WhatsApp de la 1.2.0)
     *    → una regla por cada WhatsApp instalado ([installed]): suenan exactamente igual que antes.
     *  * Reglas del WhatsApp normal cuando solo está instalado Business (la 1.2.0 las mostraba en una
     *    tarjeta «WhatsApp» que era en realidad Business) → pasan a Business.
     * null si no hay nada que cambiar. Sin ningún WhatsApp instalado, todo se queda como está.
     */
    fun splitLegacyWhatsApp(rules: List<Rule>, installed: Set<String>, newId: () -> String): List<Rule>? {
        val targets = WhatsApp.PACKAGES.filter { it in installed }
        val onlyBusiness = targets == listOf(WhatsApp.BUSINESS)
        val orphanPersonal = onlyBusiness && rules.any { !it.isLegacyWhatsApp && it.packageName == WhatsApp.PERSONAL }
        if (targets.isEmpty() || (rules.none { it.isLegacyWhatsApp } && !orphanPersonal)) return null
        val out = mutableListOf<Rule>()
        fun alreadyThere(candidate: Rule) = (out + rules).any { r ->
            !r.isLegacyWhatsApp && r.type == candidate.type && r.packageName == candidate.packageName &&
                TextNormalizer.groupKey(r.name.orEmpty()) == TextNormalizer.groupKey(candidate.name.orEmpty())
        }
        rules.forEach { rule ->
            if (orphanPersonal && !rule.isLegacyWhatsApp && rule.packageName == WhatsApp.PERSONAL) {
                val moved = rule.copy(packageName = WhatsApp.BUSINESS)
                if (!alreadyThere(moved) || moved.type == RuleType.KEYWORDS) out += moved
                return@forEach
            }
            if (!rule.isLegacyWhatsApp) {
                out += rule
                return@forEach
            }
            targets.forEachIndexed { i, pkg ->
                val split = when (rule.type) {
                    RuleType.WHATSAPP_ANY -> Rule(id = if (i == 0) rule.id else newId(), type = RuleType.APP, enabled = rule.enabled, packageName = pkg)
                    else -> rule.copy(id = if (i == 0) rule.id else newId(), packageName = pkg)
                }
                if (!alreadyThere(split)) out += split
            }
        }
        return out
    }

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
                    packageName = chat.packageName,
                    appLabel = label(chat.packageName),
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
