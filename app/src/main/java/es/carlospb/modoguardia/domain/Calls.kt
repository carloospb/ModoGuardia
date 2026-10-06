package es.carlospb.modoguardia.domain

/** Qué llamadas telefónicas despiertan. */
enum class CallMode {
    /** Cualquier llamada entrante (comportamiento de siempre). */
    ALL,

    /** Solo las de los contactos y números elegidos (reglas [RuleType.CALL]). */
    SELECTED,
}

/** Comparación de números de teléfono escritos de formas distintas. */
object PhoneNumbers {
    /** Con al menos estas cifras se compara por el final (prefijo internacional opcional). */
    private const val LONG_NUMBER = 7
    private const val SIGNIFICANT = 9

    // Cifras, separadores habituales y las marcas de dirección que mete BidiFormatter.
    private val phoneChars = Regex("""^[\s+()./\- ‎‏‪-‮⁦-⁩\d]+$""")
    private val embedded = Regex("""(?:\+|00)?\d[\d\s()./\- ]{4,}\d""")

    /** Solo cifras, sin prefijo internacional «+» o «00». */
    fun digits(raw: String): String {
        val d = raw.filter(Char::isDigit)
        val visible = raw.filterNot { Character.getType(it) == Character.FORMAT.toInt() }.trimStart()
        return if (visible.startsWith("00") && d.startsWith("00")) d.drop(2) else d
    }

    fun isUsable(raw: String): Boolean = digits(raw).length >= 3

    /**
     * Mismo número: «+34 600 12 34 56», «0034600123456» y «600123456» lo son. Los números largos se
     * comparan por sus últimas 9 cifras; los cortos (centralitas, servicios) tienen que ser iguales.
     */
    fun same(a: String, b: String): Boolean {
        val da = digits(a)
        val db = digits(b)
        if (da.isEmpty() || db.isEmpty()) return false
        if (da.length < LONG_NUMBER || db.length < LONG_NUMBER) return da == db
        val n = minOf(SIGNIFICANT, da.length, db.length)
        return da.takeLast(n) == db.takeLast(n)
    }

    /** true si el texto es solo un número (lo que pone el marcador cuando no es un contacto). */
    fun isNumberOnly(text: String): Boolean = text.isNotBlank() && phoneChars.matches(text) && digits(text).length >= 3

    /** Números que aparecen en un texto: el texto entero si es un número, o secuencias de 6+ cifras. */
    fun extract(text: String): List<String> {
        if (isNumberOnly(text)) return listOf(digits(text))
        return embedded.findAll(text).map { digits(it.value) }.filter { it.length >= 6 }.toList()
    }
}

/**
 * Quién llama, según lo que Android deja ver sin leer la agenda:
 *  * el aviso de llamada entrante del marcador: el nombre del contacto (en el orden de nombre que
 *    tenga configurado el usuario) o, si no es un contacto, el número; a veces el nombre de una
 *    empresa que da el propio marcador;
 *  * opcionalmente, el número exacto de la señal de llamada (solo con «Registro de llamadas»).
 * Si no hay ni nombre ni número, la llamada es «sin identificar».
 */
data class CallerId(val names: List<String>, val numbers: List<String>) {
    val isEmpty: Boolean get() = names.isEmpty() && numbers.isEmpty()

    /** Texto para la alarma y el historial. */
    fun display(): String? = names.firstOrNull() ?: numbers.firstOrNull()

    companion object {
        /** Lo que algunos marcadores ponen en el título en lugar de un nombre. */
        private val GENERIC = setOf(
            "llamada entrante", "llamada", "llamada de voz entrante", "incoming call", "call",
            "numero desconocido", "desconocido", "unknown", "unknown caller", "numero privado",
            "privado", "private number", "private", "oculto", "numero oculto", "no disponible",
            "telefono", "restringido", "numero restringido", "telefono publico", "payphone", "cabina",
            "trucada entrant", "trucada", "chamada entrante", "chamada", "deia jasotzen", "deia",
            "appel entrant", "appel", "eingehender anruf", "anruf", "chiamata in arrivo", "chiamata",
        ).map(TextNormalizer::groupKey).toSet()

        /**
         * Trozos que delatan un texto genérico de número oculto o desconocido en los idiomas
         * habituales (castellano, catalán, gallego, euskera, portugués, inglés, francés, alemán,
         * italiano). Se comparan sin mayúsculas ni tildes.
         */
        private val GENERIC_PARTS = listOf(
            "desconoc", "desconeg", "descone", "descono", "desconh", "ezezagun", "unknown", "inconnu", "unbekannt", "sconosciut",
            "privad", "privat", "prive", "private", "pribatu", "oculto", "ocult", "ezkutu", "masque", "withheld", "unterdruck",
            "restring", "restrict", "anonim", "anonym", "payphone", "cabina", "publico", "public phone",
        )

        private fun isGeneric(text: String): Boolean {
            val key = TextNormalizer.groupKey(text)
            return key in GENERIC || GENERIC_PARTS.any { it in key }
        }

        fun from(event: NotificationEvent): CallerId {
            val nameFields = listOfNotNull(event.title, event.conversationTitle) + event.personNames
            val names = nameFields
                .map(TextNormalizer::clean)
                .filter { it.isNotBlank() && !PhoneNumbers.isNumberOnly(it) && !isGeneric(it) }
                .distinctBy(TextNormalizer::groupKey)
            val numbers = (listOfNotNull(event.title, event.text, event.subText, event.conversationTitle) + event.personNames)
                .flatMap(PhoneNumbers::extract)
                .plus(event.personNumbers.map(PhoneNumbers::digits).filter { it.length >= 3 })
                .distinct()
            return CallerId(names, numbers)
        }
    }
}
