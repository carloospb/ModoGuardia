package es.carlospb.modoguardia.domain

sealed interface Evaluation {
    data class Match(val rule: Rule) : Evaluation

    /** [groupUnresolved]: hay reglas de grupo y era WhatsApp, pero el aviso no traía el nombre del grupo. */
    data class NoMatch(val groupUnresolved: Boolean) : Evaluation
}

/** Decide si una notificación cumple alguna regla. Puro: sin Android, sin estado. */
object RuleEngine {

    /** Más específica primero: así la alarma muestra la regla más informativa. */
    private fun specificity(type: RuleType): Int = when (type) {
        RuleType.WHATSAPP_GROUP -> 0
        RuleType.KEYWORDS -> 1
        RuleType.APP -> 2
        RuleType.WHATSAPP_ANY -> 3
    }

    fun evaluate(event: NotificationEvent, rules: List<Rule>): Evaluation {
        val active = rules.filter { it.enabled && it.isValid() }.sortedBy { specificity(it.type) }
        val isWhatsApp = WhatsApp.isWhatsApp(event.packageName)
        val groupKeys by lazy { WhatsApp.groupCandidates(event).map(TextNormalizer::groupKey).toSet() }
        var groupUnresolved = false

        for (rule in active) {
            val matches = when (rule.type) {
                RuleType.APP -> event.packageName == rule.packageName
                RuleType.WHATSAPP_ANY -> isWhatsApp
                RuleType.WHATSAPP_GROUP -> when {
                    !isWhatsApp -> false
                    groupKeys.isEmpty() -> {
                        groupUnresolved = true
                        false
                    }
                    else -> TextNormalizer.groupKey(rule.groupName.orEmpty()) in groupKeys
                }
                RuleType.KEYWORDS ->
                    (rule.packageName.isNullOrBlank() || rule.packageName == event.packageName) &&
                        keywordsMatch(rule, event.searchableText())
            }
            if (matches) return Evaluation.Match(rule)
        }
        return Evaluation.NoMatch(groupUnresolved)
    }

    /** Búsqueda por subcadena: "ERROR" encuentra "errores". Más vale una alarma de más que una de menos. */
    fun keywordsMatch(rule: Rule, text: String): Boolean {
        val words = rule.cleanKeywords()
        if (words.isEmpty()) return false
        val haystack = if (rule.ignoreCase) TextNormalizer.fold(text) else TextNormalizer.clean(text)
        val needles = if (rule.ignoreCase) words.map(TextNormalizer::fold) else words
        return if (rule.matchAll) needles.all { it in haystack } else needles.any { it in haystack }
    }
}
