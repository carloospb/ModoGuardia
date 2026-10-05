package es.carlospb.modoguardia.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import es.carlospb.modoguardia.domain.UpdatePolicy
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Versión publicada en carlospb.es. */
data class PublishedVersion(val versionCode: Int, val versionName: String, val notes: String, val apkUrl: String)

sealed interface UpdateCheck {
    data class Available(val version: PublishedVersion) : UpdateCheck
    data class UpToDate(val versionName: String) : UpdateCheck
    /** No se pudo comprobar. Es la red o el servidor, nunca la guardia. */
    data class Failed(val reason: String) : UpdateCheck
}

/**
 * Comprobación, descarga e instalación de actualizaciones (como BT-LINKER y NowOrNever).
 *
 * Es lo único de la app que usa Internet: lee el manifiesto de carlospb.es al abrir la app y
 * descarga la APK solo si el usuario lo acepta. Nunca desde un servicio ni en segundo plano, y
 * ninguna parte de la cadena de alarma depende de ello: si falla, se dice y la guardia sigue igual.
 *
 * Instalar sin preguntar no es posible con APIs públicas: el instalador de Android siempre pide
 * confirmación, y la primera vez el usuario debe permitir «Instalar apps desconocidas» a mano.
 */
object Updater {
    private const val TAG = "ModoGuardia/Update"

    /** Bloquea: llamar desde un hilo secundario. */
    fun check(installedVersionCode: Int): UpdateCheck {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(UpdatePolicy.MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                // Sin esto una caché intermedia puede devolver el manifiesto viejo.
                setRequestProperty("Cache-Control", "no-cache")
                useCaches = false
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return UpdateCheck.Failed("El servidor respondió ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val code = json.getInt("versionCode")
            val name = json.optString("versionName", code.toString())
            if (UpdatePolicy.isNewer(installedVersionCode, code)) {
                UpdateCheck.Available(
                    PublishedVersion(code, name, json.optString("notas", ""), UpdatePolicy.apkUrlOrFallback(json.optString("url"))),
                )
            } else {
                UpdateCheck.UpToDate(name)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo comprobar: ${e.message}")
            UpdateCheck.Failed(e.message ?: "sin conexión")
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun folder(context: Context) = File(context.cacheDir, "actualizaciones").apply { mkdirs() }

    /**
     * Descarga la APK a la caché propia. Bloquea: llamar desde un hilo secundario.
     * @param onProgress 0..100, o -1 si el servidor no da el tamaño.
     * @return el archivo, o null si falló.
     */
    fun download(context: Context, version: PublishedVersion, onProgress: (Int) -> Unit): File? {
        val target = File(folder(context), "ModoGuardia-${version.versionName}.apk")
        // A un temporal y se renombra al final: nunca queda una APK a medias con nombre de buena.
        val temp = File(folder(context), "descarga.tmp")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(version.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                useCaches = false
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Descarga: el servidor respondió ${conn.responseCode}")
                return null
            }
            val total = conn.contentLengthLong
            var read = 0L
            temp.outputStream().use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(if (total > 0) ((read * 100) / total).toInt() else -1)
                    }
                }
            }
            if (total > 0 && read != total) {
                Log.w(TAG, "Descarga incompleta: $read de $total bytes")
                temp.delete()
                return null
            }
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                return null
            }
            target
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo descargar: ${e.message}")
            runCatching { temp.delete() }
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    fun canInstall(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /** Abre el ajuste «Instalar apps desconocidas» de esta app. */
    fun openInstallPermission(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    /** Entrega la APK al instalador del sistema; la confirmación final es del usuario. */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                // El instalador es otro proceso: necesita permiso de lectura sobre el content://.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    }.getOrElse {
        Log.w(TAG, "No se pudo lanzar el instalador: ${it.message}")
        false
    }
}
