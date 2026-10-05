package es.carlospb.modoguardia.domain

import java.text.Normalizer
import java.util.Locale

/** Normalización de texto para comparar nombres de grupo y palabras clave. */
object TextNormalizer {
    /** Caracteres de formato invisibles (marcas de dirección que WhatsApp mete en los nombres). */
    private val invisible = Regex("\\p{Cf}+")
    private val spaces = Regex("[\\s\\u00A0]+")
    private val diacritics = Regex("\\p{Mn}+")

    /** Sufijo de recuento: "GUARDIAS (3 mensajes)", "Team (12 messages)". Exige número + palabra. */
    private val countSuffix = Regex("\\s*\\(\\s*\\d+\\s+[^()]*\\)\\s*$")

    /** Quita invisibles y colapsa espacios. No cambia mayúsculas. */
    fun clean(s: String): String = s.replace(invisible, "").replace(spaces, " ").trim()

    /** Sin mayúsculas ni tildes: "CRÍTICO" == "critico". */
    fun fold(s: String): String =
        Normalizer.normalize(clean(s), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase(Locale.ROOT)

    /** Nombre de grupo para mostrar: limpio y sin sufijo de recuento. Misma [groupKey] que el original. */
    fun displayGroupName(s: String): String = clean(s).replace(countSuffix, "").trim()

    /** Clave de comparación de un nombre de grupo/conversación. */
    fun groupKey(s: String): String = fold(clean(s).replace(countSuffix, ""))
}
