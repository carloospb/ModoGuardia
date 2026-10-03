package es.carlospb.modoguardia.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import es.carlospb.modoguardia.container
import kotlinx.coroutines.launch

/**
 * Tras reiniciar (BOOT_COMPLETED, ya desbloqueado: los ajustes viven en almacenamiento cifrado por
 * credenciales) o tras actualizar la app (MY_PACKAGE_REPLACED). Ambas difusiones permiten iniciar
 * un servicio en primer plano de tipo specialUse, también en Android 15.
 *
 * El lector de notificaciones no hay que restaurarlo: el sistema lo vuelve a enlazar solo.
 * Limitación: si el usuario hizo "Forzar detención", Android no entrega ninguna difusión hasta que
 * se abre la app manualmente.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "tras reiniciar el teléfono"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "tras actualizar la app"
            else -> return
        }
        val c = context.container
        val pending = goAsync()
        c.appScope.launch {
            try {
                c.guardController.restore(reason)
            } finally {
                pending.finish()
            }
        }
    }
}
