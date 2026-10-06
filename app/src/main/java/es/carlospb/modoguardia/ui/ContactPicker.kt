package es.carlospb.modoguardia.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract

/** Contacto elegido: nombre (y su forma «Apellido, Nombre») y el número que el usuario tocó. */
data class PickedContact(val name: String?, val alternativeName: String?, val number: String?)

/**
 * Selector de contactos oficial de Android, en su vista de teléfonos. NO hace falta el permiso de
 * contactos: Android concede a la app permiso temporal de lectura solo sobre la fila elegida.
 * Así Modo Guardia nunca ve la agenda entera.
 */
class PickPhoneContact : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

object ContactReader {
    private const val TAG = "ContactReader"

    // Columnas públicas de ContactsContract.Data (las constantes viven en interfaces protegidas).
    private const val DISPLAY_NAME = "display_name"
    private const val DISPLAY_NAME_ALT = "display_name_alt"

    /** Lee la fila elegida. Bloquea (consulta a un proveedor): llamar fuera del hilo principal. */
    fun read(context: Context, uri: Uri): PickedContact? = try {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, DISPLAY_NAME, DISPLAY_NAME_ALT)
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            fun col(i: Int): String? = if (c.isNull(i)) null else c.getString(i)?.takeIf { it.isNotBlank() }
            PickedContact(name = col(1), alternativeName = col(2), number = col(0))
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Sin permiso temporal sobre el contacto elegido", e)
        null
    } catch (e: IllegalArgumentException) {
        // Algún selector de fabricante no expone display_name_alt: se reintenta sin él.
        readWithoutAlternative(context, uri)
    }

    private fun readWithoutAlternative(context: Context, uri: Uri): PickedContact? = try {
        context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, DISPLAY_NAME), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            PickedContact(name = c.getString(1), alternativeName = null, number = c.getString(0))
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer el contacto elegido", e)
        null
    }
}
