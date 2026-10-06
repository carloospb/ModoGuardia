package es.carlospb.modoguardia.domain

sealed interface Evaluation {
    data class Match(val rule: Rule) : Evaluation

    /** [groupUnresolved]: había reglas de grupo para esa app, pero el aviso no traía el nombre del grupo. */
    data class NoMatch(val groupUnresolved: Boolean) : Evaluation
}

sealed interface CallEvaluation {
    data class Match(val rule: Rule) : CallEvaluation

    /** Se sabe lo suficiente de quien llama para descartar a todos los elegidos. */
    data object NoMatch : CallEvaluation

    /** No se sabe quién llama, o no lo bastante para descartar a alguno de los elegidos. */
    data object Unidentified : CallEvaluation
}

/**
 * Decide si un aviso o una llamada cumple alguna regla. Es el ÚNICO sitio con esa lógica. Puro: sin
 * Android, sin estado. Para una app nueva no hace falta tocarlo: las reglas llevan su package name.
 */
object RuleEngine {

    /** Más específica primero: así la alarma muestra la regla más informativa. */
    private fun specificity(type: RuleType): Int = when (type) {
        RuleType.GROUP, RuleType.PERSON -> 0
        RuleType.KEYWORDS -> 1
        RuleType.APP -> 2
        RuleType.WHATSAPP_ANY -> 3
        RuleType.CALL -> 4
    }

    fun evaluate(event: NotificationEvent, rules: List<Rule>): Evaluation {
        val active = rules
            .filter { it.enabled && !it.isCall && it.isValid() && it.appliesTo(event.packageName) }
            .sortedBy { specificity(it.type) }
        val kind by lazy { Conversations.kind(event) }
        val groupKeys by lazy { Conversations.groupCandidates(event).map(TextNormalizer::groupKey).toSet() }
        val personKeys by lazy { Conversations.personCandidates(event).map(TextNormalizer::groupKey).toSet() }
        var groupUnresolved = false

        for (rule in active) {
            val matches = when (rule.type) {
                RuleType.APP, RuleType.WHATSAPP_ANY -> true
                RuleType.GROUP -> when {
                    sameConversation(rule, event) && kind != ChatKind.INDIVIDUAL -> true
                    groupKeys.isEmpty() -> {
                        // Un chat individual no es un grupo «sin identificar»: simplemente no es un grupo.
                        if (kind != ChatKind.INDIVIDUAL) groupUnresolved = true
                        false
                    }
                    else -> TextNormalizer.groupKey(rule.name.orEmpty()) in groupKeys
                }
                RuleType.PERSON ->
                    (sameConversation(rule, event) && kind != ChatKind.GROUP) ||
                        TextNormalizer.groupKey(rule.name.orEmpty()) in personKeys
                RuleType.KEYWORDS -> keywordsMatch(rule, event.searchableText())
                RuleType.CALL -> false
            }
            if (matches) return Evaluation.Match(rule)
        }
        return Evaluation.NoMatch(groupUnresolved)
    }

    /**
     * Mismo identificador de conversación de Android (shortcutId). Sirve para seguir reconociendo un
     * grupo aunque le cambien el nombre; si falta en la regla o en el aviso, decide el nombre.
     */
    private fun sameConversation(rule: Rule, event: NotificationEvent): Boolean {
        val mine = rule.conversationId?.takeIf { it.isNotBlank() } ?: return false
        return mine == event.shortcutId
    }

    /**
     * Llamada telefónica con [CallMode.SELECTED]: ¿es de alguno de los contactos o números elegidos?
     * Solo es [CallEvaluation.NoMatch] si lo que se sabe permite descartarlos a TODOS; si queda la
     * duda, [CallEvaluation.Unidentified] y decide la preferencia del usuario (por defecto, despertar).
     */
    fun evaluateCall(caller: CallerId, rules: List<Rule>): CallEvaluation {
        val active = rules.filter { it.enabled && it.isCall && it.isValid() }
        active.firstOrNull { it.matchesCaller(caller) }?.let { return CallEvaluation.Match(it) }
        if (active.isEmpty()) return CallEvaluation.NoMatch
        if (caller.isEmpty) return CallEvaluation.Unidentified
        return if (active.all { it.excludesCaller(caller) }) CallEvaluation.NoMatch else CallEvaluation.Unidentified
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
