package es.carlospb.modoguardia.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import es.carlospb.modoguardia.container
import es.carlospb.modoguardia.domain.GuardEngine
import es.carlospb.modoguardia.domain.Outcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Llamadas telefónicas. ACTION_PHONE_STATE_CHANGED es una de las difusiones implícitas que Android
 * sigue entregando a receptores del manifiesto (excepción documentada), así que funciona aunque la
 * app no esté abierta. Exige READ_PHONE_STATE; sin él el sistema simplemente no la entrega.
 *
 * No toca la llamada: no la rechaza ni la silencia. Solo hace sonar la alarma además del timbre
 * normal, y la para si el usuario contesta. La señal trae el número solo si el usuario concedió el
 * permiso opcional «Registro de llamadas» (Android la entrega dos veces: con y sin número). Con
 * «solo contactos elegidos», quién llama se sabe además por el aviso del marcador
 * (ver GuardEngine.onPhoneCallRinging).
 */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val c = context.container
        val pending = goAsync()
        c.appScope.launch {
            try {
                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        // Solo trae el número si el usuario concedió «Registro de llamadas»; si no, null.
                        @Suppress("DEPRECATION")
                        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        if (c.engine.onPhoneCallRinging(number) == Outcome.AWAITING_CALLER) {
                            // Fuera del goAsync: el proceso lo mantiene vivo el servicio de guardia.
                            c.appScope.launch {
                                delay(GuardEngine.CALLER_TIMEOUT_MS)
                                c.engine.onCallerTimeout()
                            }
                        }
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> c.engine.onPhoneCallAnswered()
                    TelephonyManager.EXTRA_STATE_IDLE -> c.engine.onPhoneIdle()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
