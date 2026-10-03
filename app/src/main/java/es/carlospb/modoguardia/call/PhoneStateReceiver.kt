package es.carlospb.modoguardia.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import es.carlospb.modoguardia.container
import kotlinx.coroutines.launch

/**
 * Llamadas telefónicas. ACTION_PHONE_STATE_CHANGED es una de las difusiones implícitas que Android
 * sigue entregando a receptores del manifiesto (excepción documentada), así que funciona aunque la
 * app no esté abierta. Exige READ_PHONE_STATE; sin él el sistema simplemente no la entrega.
 *
 * No toca la llamada: no la rechaza, no la silencia, no lee el número. Solo hace sonar la alarma
 * además del timbre normal, y la para si el usuario contesta.
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
                    TelephonyManager.EXTRA_STATE_RINGING -> c.engine.onPhoneCallRinging()
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> c.engine.onPhoneCallAnswered()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
