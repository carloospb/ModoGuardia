package es.carlospb.modoguardia.domain

/** Las reglas de una app, agrupadas como las ve el usuario: todas, grupos, personas, palabras. */
data class WatchedApp(val key: String, val rules: List<Rule>) {
    private val active: List<Rule> = rules.filter { it.enabled && it.isValid() }

    /** Cualquier aviso de la app despierta (regla APP o, en WhatsApp, WHATSAPP_ANY). */
    val allNotifications: Boolean = active.any { it.type == RuleType.APP || it.type == RuleType.WHATSAPP_ANY }
    val groups: List<Rule> = active.filter { it.type == RuleType.GROUP }
    val people: List<Rule> = active.filter { it.type == RuleType.PERSON }
    val keywordRules: List<Rule> = active.filter { it.type == RuleType.KEYWORDS }

    /** Alguna regla activa: la app está vigilada. Si no, está «en pausa». */
    val isActive: Boolean = active.isNotEmpty()
    val conversationCount: Int get() = groups.size + people.size

    /** Nombre guardado en alguna regla (por si la app ya no está instalada). */
    val storedLabel: String? = rules.firstNotNullOfOrNull { it.appLabel?.takeIf(String::isNotBlank) }
}

/** Resumen de lo vigilado, para el panel de inicio y el estado. Puro: se prueba en la JVM. */
data class WatchSummary(
    /** Apps con alguna regla activa: WhatsApp, WhatsApp Business y el resto. */
    val apps: List<WatchedApp>,
    /** Reglas de palabras clave para cualquier app. */
    val anyAppKeywords: List<Rule>,
    val phoneCalls: Boolean,
    val callMode: CallMode,
    /** Contactos y números elegidos (activos). */
    val callRules: List<Rule>,
    val appCalls: Boolean,
) {
    val appCount: Int get() = apps.size
    val conversationCount: Int get() = apps.sumOf { it.conversationCount }

    /** Las llamadas telefónicas pueden despertar: todas, o hay al menos un contacto o número elegido. */
    val phoneCallsActive: Boolean get() = phoneCalls && (callMode == CallMode.ALL || callRules.isNotEmpty())

    /** Cuántas cosas pueden despertar. 0 = nada despertará al usuario. */
    val triggerCount: Int
        get() = apps.sumOf { (if (it.allNotifications) 1 else 0) + it.conversationCount + it.keywordRules.size } +
            anyAppKeywords.size +
            (if (!phoneCallsActive) 0 else if (callMode == CallMode.ALL) 1 else callRules.size) +
            (if (appCalls) 1 else 0)

    companion object {
        fun of(rules: List<Rule>, wakeOnPhoneCalls: Boolean, callMode: CallMode, wakeOnAppCalls: Boolean) = WatchSummary(
            apps = appsOf(rules).filter { it.isActive },
            anyAppKeywords = rules.filter { it.enabled && it.isValid() && it.type == RuleType.KEYWORDS && it.appKey == null },
            phoneCalls = wakeOnPhoneCalls,
            callMode = callMode,
            callRules = rules.filter { it.enabled && it.isValid() && it.isCall },
            appCalls = wakeOnAppCalls,
        )

        /** Todas las apps con reglas (también las que están en pausa): WhatsApp, WhatsApp Business y el resto. */
        fun appsOf(rules: List<Rule>): List<WatchedApp> = rules
            .filter { !it.isCall }
            .mapNotNull { r -> r.appKey?.let { it to r } }
            .groupBy({ it.first }, { it.second })
            .map { (key, list) -> WatchedApp(key, list) }
            .sortedWith(
                compareBy<WatchedApp> {
                    when (it.key) {
                        WhatsApp.PERSONAL -> 0
                        WhatsApp.BUSINESS -> 1
                        else -> 2
                    }
                }.thenBy { (it.storedLabel ?: it.key).lowercase() },
            )
    }
}
