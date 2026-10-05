package es.carlospb.modoguardia.domain

/**
 * Reglas del actualizador, sin Android: qué cuenta como versión nueva y de dónde se acepta
 * descargar. La red y la instalación viven en `update/`.
 */
object UpdatePolicy {

    /** Manifiesto publicado junto a la descarga en carlospb.es. */
    const val MANIFEST_URL = "https://www.carlospb.es/modoguardia-version.json"

    /** Descarga de respaldo si el manifiesto no trae una dirección válida. */
    const val FALLBACK_APK_URL = "https://www.carlospb.es/ModoGuardia.apk"

    private val TRUSTED_HOSTS = setOf("www.carlospb.es", "carlospb.es")

    fun isNewer(installedVersionCode: Int, publishedVersionCode: Int): Boolean =
        publishedVersionCode > installedVersionCode

    /**
     * Solo se descarga por HTTPS desde carlospb.es. Si el manifiesto apuntara a otro sitio (error
     * al publicar o manipulación), no se sigue: se usa la descarga de respaldo.
     */
    fun isTrustedApkUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val m = Regex("""^https://([^/:?#]+)(/[^?#]*)?(\?[^#]*)?$""").matchEntire(url.trim()) ?: return false
        val host = m.groupValues[1].lowercase()
        val path = m.groupValues[2]
        return host in TRUSTED_HOSTS && path.endsWith(".apk", ignoreCase = true)
    }

    fun apkUrlOrFallback(url: String?): String = if (isTrustedApkUrl(url)) url!!.trim() else FALLBACK_APK_URL
}
