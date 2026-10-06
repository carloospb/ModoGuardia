package es.carlospb.modoguardia.domain

/**
 * WhatsApp y WhatsApp Business. Las reglas de conversación sin app concreta (las creadas antes de
 * que existieran otras apps de chat) se aplican a los dos, como siempre.
 *
 * La identificación de grupos y chats ya no es exclusiva de WhatsApp: vive en [Conversations].
 */
object WhatsApp {
    val PACKAGES: Set<String> = setOf("com.whatsapp", "com.whatsapp.w4b")

    /** Clave con la que la interfaz agrupa las reglas de WhatsApp. */
    const val KEY = "com.whatsapp"

    fun isWhatsApp(packageName: String): Boolean = packageName in PACKAGES
}
