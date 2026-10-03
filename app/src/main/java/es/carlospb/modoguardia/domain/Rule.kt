package es.carlospb.modoguardia.domain

enum class RuleType {
    /** Cualquier notificación de una app concreta (por package name). */
    APP,

    /** Cualquier notificación de WhatsApp o WhatsApp Business. */
    WHATSAPP_ANY,

    /** Mensaje de un grupo de WhatsApp concreto, identificado por los campos de la notificación. */
    WHATSAPP_GROUP,

    /** Notificación (de una app concreta o de cualquiera) que contiene palabras clave. */
    KEYWORDS,
}

data class Rule(
    val id: String,
    val type: RuleType,
    val enabled: Boolean = true,
    /** APP: obligatorio. KEYWORDS: null = cualquier app. Resto: ignorado. */
    val packageName: String? = null,
    val appLabel: String? = null,
    /** WHATSAPP_GROUP: nombre del grupo tal y como aparece en WhatsApp. */
    val groupName: String? = null,
    val keywords: List<String> = emptyList(),
    /** KEYWORDS: true = todas las palabras, false = cualquiera. */
    val matchAll: Boolean = false,
    /** KEYWORDS: ignorar mayúsculas/minúsculas y tildes. */
    val ignoreCase: Boolean = true,
) {
    fun cleanKeywords(): List<String> = keywords.map { TextNormalizer.clean(it) }.filter { it.isNotEmpty() }

    fun isValid(): Boolean = when (type) {
        RuleType.APP -> !packageName.isNullOrBlank()
        RuleType.WHATSAPP_ANY -> true
        RuleType.WHATSAPP_GROUP -> !groupName.isNullOrBlank() && TextNormalizer.groupKey(groupName).isNotEmpty()
        RuleType.KEYWORDS -> cleanKeywords().isNotEmpty()
    }

    /** Texto corto para la alarma, el historial y la lista de reglas. */
    fun label(): String = when (type) {
        RuleType.APP -> "Cualquier aviso de ${appLabel ?: packageName}"
        RuleType.WHATSAPP_ANY -> "Cualquier WhatsApp"
        RuleType.WHATSAPP_GROUP -> groupName.orEmpty()
        RuleType.KEYWORDS -> {
            val joiner = if (matchAll) " + " else " / "
            "Palabras: " + cleanKeywords().joinToString(joiner)
        }
    }
}
