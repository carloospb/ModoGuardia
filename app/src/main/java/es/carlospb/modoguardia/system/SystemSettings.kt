package es.carlospb.modoguardia.system

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import es.carlospb.modoguardia.service.GuardNotificationListener

/**
 * Lleva al usuario a la pantalla de Ajustes donde se concede cada cosa. Solo intents públicos;
 * si una pantalla no existe en un fabricante, se cae a la siguiente más cercana.
 */
object SystemSettings {

    fun open(context: Context, action: FixAction) {
        val pkg = context.packageName
        val packageUri = Uri.parse("package:$pkg")
        val candidates: List<Intent> = when (action) {
            FixAction.NOTIFICATION_ACCESS, FixAction.REBIND_LISTENER -> buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                            .putExtra(
                                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                GuardNotificationListener.component(context).flattenToString(),
                            ),
                    )
                }
                add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            FixAction.POST_NOTIFICATIONS, FixAction.APP_NOTIFICATION_SETTINGS -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
            )
            FixAction.FULL_SCREEN_INTENT -> buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    add(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri))
                }
                add(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
            }
            FixAction.BATTERY_OPTIMIZATION -> listOf(
                batteryRequest(packageUri),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
            FixAction.DND_SETTINGS -> listOf(
                Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
                Intent(Settings.ACTION_SOUND_SETTINGS),
            )
            FixAction.SOUND_SETTINGS -> listOf(Intent(Settings.ACTION_SOUND_SETTINGS))
            FixAction.UNUSED_APP_RESTRICTIONS -> buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, packageUri))
            }
            FixAction.APP_DETAILS, FixAction.PHONE_PERMISSION, FixAction.RESTART_GUARD_SERVICE -> emptyList()
        } + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)

        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // Siguiente candidato.
            } catch (_: SecurityException) {
                // Algunos fabricantes protegen pantallas concretas.
            }
        }
    }

    /**
     * Diálogo del sistema "¿Permitir que la app se ejecute siempre en segundo plano?".
     * Uso aceptado por la política de Play para apps cuya función principal se rompe con Doze
     * (una alarma de guardia lo es); ver docs/PERMISOS.md.
     */
    @SuppressLint("BatteryLife")
    private fun batteryRequest(packageUri: Uri) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
}
