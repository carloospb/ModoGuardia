package es.carlospb.modoguardia.domain

/**
 * WhatsApp y WhatsApp Business. Son dos apps distintas (a menudo una personal y otra del trabajo) y
 * se vigilan por separado: cada regla lleva el paquete de su app.
 *
 * Excepción heredada: las reglas creadas por las versiones 1.0 y 1.1 no llevaban app y valían para
 * las dos. Siguen valiendo para las dos hasta que [RuleEdits.splitLegacyWhatsApp] las divide en una
 * regla por cada WhatsApp instalado, al arrancar la app.
 *
 * La identificación de grupos y chats no es exclusiva de WhatsApp: vive en [Conversations].
 */
object WhatsApp {
    const val PERSONAL = "com.whatsapp"
    const val BUSINESS = "com.whatsapp.w4b"
    val PACKAGES: Set<String> = linkedSetOf(PERSONAL, BUSINESS)

    /** Tarjeta en la que la interfaz muestra las reglas heredadas sin app (las dos WhatsApp). */
    const val KEY = PERSONAL

    fun isWhatsApp(packageName: String): Boolean = packageName in PACKAGES
}
