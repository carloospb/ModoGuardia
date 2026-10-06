package es.carlospb.modoguardia.domain

enum class RuleType {
    /** Cualquier notificación de una app concreta (por package name). */
    APP,

    /** Cualquier notificación de WhatsApp o WhatsApp Business. */
    WHATSAPP_ANY,

    /** Mensaje de un grupo concreto de una app de chat (sin app = WhatsApp). */
    GROUP,

    /** Mensaje de un chat individual concreto de una app de chat (sin app = WhatsApp). */
    PERSON,

    /** Notificación (de una app concreta o de cualquiera) que contiene palabras clave. */
    KEYWORDS,

    /** Llamada telefónica de un contacto o número concreto (solo con [CallMode.SELECTED]). */
    CALL,
    ;

    companion object {
        /** Tipo guardado, también con los nombres de versiones anteriores (1.0 y 1.1). */
        fun fromStored(name: String?): RuleType? = when (name) {
            "WHATSAPP_GROUP" -> GROUP
            "WHATSAPP_CHAT" -> PERSON
            else -> entries.firstOrNull { it.name == name }
        }
    }
}

/**
 * Una regla: QUÉ despierta. Ejemplos: WhatsApp · grupo · «Soporte técnico»; WhatsApp · persona ·
 * «Carlos»; Telegram · todas las notificaciones; llamada · Carlos (+34 600…).
 */
data class Rule(
    val id: String,
    val type: RuleType,
    val enabled: Boolean = true,
    /** APP: obligatorio. GROUP/PERSON: app de la conversación (null = WhatsApp). KEYWORDS: null = cualquier app. */
    val packageName: String? = null,
    val appLabel: String? = null,
    /** GROUP/PERSON: nombre de la conversación tal como lo trae el aviso. CALL: nombre para mostrar. */
    val name: String? = null,
    /** GROUP/PERSON: identificador de conversación de Android (shortcutId), si la app lo da. */
    val conversationId: String? = null,
    /**
     * CALL: nombres con los que el marcador muestra a este CONTACTO («Carlos Pérez» y «Pérez,
     * Carlos», según el orden que tenga configurado el usuario). Vacío en un número añadido a mano:
     * su etiqueta («Empresa») no es un nombre de contacto y nunca se compara.
     */
    val aliases: List<String> = emptyList(),
    /** CALL: números de teléfono del contacto, o el número añadido a mano. */
    val numbers: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    /** KEYWORDS: true = todas las palabras, false = cualquiera. */
    val matchAll: Boolean = false,
    /** KEYWORDS: ignorar mayúsculas/minúsculas y tildes. */
    val ignoreCase: Boolean = true,
) {
    val isConversation: Boolean get() = type == RuleType.GROUP || type == RuleType.PERSON
    val isCall: Boolean get() = type == RuleType.CALL

    fun cleanKeywords(): List<String> = keywords.map { TextNormalizer.clean(it) }.filter { it.isNotEmpty() }

    /** La regla es de WhatsApp: vale para WhatsApp y WhatsApp Business, como en la interfaz. */
    private val isWhatsAppFamily: Boolean get() = packageName?.let(WhatsApp::isWhatsApp) == true

    /** El aviso de [pkg] es de la app de esta regla (WhatsApp cuenta con Business). */
    private fun samePackage(pkg: String): Boolean = if (isWhatsAppFamily) WhatsApp.isWhatsApp(pkg) else pkg == packageName

    /** Si esta regla puede evaluar avisos de [pkg]. Las de llamada no evalúan avisos. */
    fun appliesTo(pkg: String): Boolean = when (type) {
        RuleType.APP -> samePackage(pkg)
        RuleType.WHATSAPP_ANY -> WhatsApp.isWhatsApp(pkg)
        RuleType.GROUP, RuleType.PERSON -> if (packageName.isNullOrBlank()) WhatsApp.isWhatsApp(pkg) else samePackage(pkg)
        RuleType.KEYWORDS -> packageName.isNullOrBlank() || samePackage(pkg)
        RuleType.CALL -> false
    }

    /** App a la que pertenece la regla, para agruparla en la interfaz (null = cualquier app / llamadas). */
    val appKey: String?
        get() = when (type) {
            RuleType.APP, RuleType.KEYWORDS -> packageName?.takeIf { it.isNotBlank() }?.let { if (WhatsApp.isWhatsApp(it)) WhatsApp.KEY else it }
            RuleType.WHATSAPP_ANY -> WhatsApp.KEY
            RuleType.GROUP, RuleType.PERSON -> packageName?.takeIf { it.isNotBlank() }?.let { if (WhatsApp.isWhatsApp(it)) WhatsApp.KEY else it } ?: WhatsApp.KEY
            RuleType.CALL -> null
        }

    fun isValid(): Boolean = when (type) {
        RuleType.APP -> !packageName.isNullOrBlank()
        RuleType.WHATSAPP_ANY -> true
        RuleType.GROUP, RuleType.PERSON -> !name.isNullOrBlank() && TextNormalizer.groupKey(name).isNotEmpty()
        RuleType.KEYWORDS -> cleanKeywords().isNotEmpty()
        RuleType.CALL -> aliasKeys().isNotEmpty() || numbers.any(PhoneNumbers::isUsable)
    }

    /** Texto corto para la alarma, el historial y la lista de reglas. */
    fun label(): String = when (type) {
        RuleType.APP -> "Cualquier aviso de ${appLabel ?: packageName}"
        RuleType.WHATSAPP_ANY -> "Cualquier WhatsApp"
        RuleType.GROUP, RuleType.PERSON -> name.orEmpty()
        RuleType.KEYWORDS -> {
            val joiner = if (matchAll) " + " else " / "
            "Palabras: " + cleanKeywords().joinToString(joiner)
        }
        RuleType.CALL -> name?.takeIf { it.isNotBlank() } ?: aliases.firstOrNull() ?: numbers.firstOrNull().orEmpty()
    }

    /** CALL: es un contacto de la agenda (se reconoce por su nombre), no solo un número. */
    val isContact: Boolean get() = type == RuleType.CALL && aliasKeys().isNotEmpty()

    private fun aliasKeys(): Set<String> = aliases.map(TextNormalizer::groupKey).filter { it.isNotEmpty() }.toSet()

    /** CALL: ¿es quien llama? Por nombre de contacto (igual, normalizado) o por número. */
    fun matchesCaller(caller: CallerId): Boolean {
        if (type != RuleType.CALL) return false
        val keys = aliasKeys()
        if (caller.names.any { TextNormalizer.groupKey(it) in keys }) return true
        return numbers.any { mine -> caller.numbers.any { PhoneNumbers.same(mine, it) } }
    }

    /**
     * CALL: lo que se sabe de quien llama demuestra que NO es este contacto o número (ya se ha
     * comprobado que no coincide). Un contacto se descarta si el marcador muestra otro nombre (un
     * contacto siempre aparece con su nombre) o si se conoce el número y no es ninguno de los suyos.
     * Un número añadido a mano solo se descarta conociendo el número: un nombre en pantalla puede ser
     * el de una empresa que el propio marcador identifica, y no dice qué número llama.
     */
    fun excludesCaller(caller: CallerId): Boolean {
        if (type != RuleType.CALL) return true
        val byName = isContact && caller.names.isNotEmpty()
        val byNumber = numbers.isNotEmpty() && caller.numbers.isNotEmpty()
        return byName || byNumber
    }
}
