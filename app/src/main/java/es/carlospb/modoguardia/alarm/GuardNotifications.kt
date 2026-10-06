package es.carlospb.modoguardia.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import es.carlospb.modoguardia.R
import es.carlospb.modoguardia.domain.AlarmKind
import es.carlospb.modoguardia.domain.AlarmRequest
import es.carlospb.modoguardia.ui.MainActivity
import java.text.DateFormat
import java.util.Date

/** Canales y notificaciones de Modo Guardia. */
object GuardNotifications {
    /** Notificación persistente "Modo Guardia activo". Discreta. */
    const val CHANNEL_STATUS = "guard_status"

    /** Notificación del servicio de alarma. Sin sonido propio: el servicio reproduce la alarma. */
    const val CHANNEL_ALARM = "guard_alarm"

    /** Plan B si Android no deja arrancar el servicio: suena el propio canal, en bucle (FLAG_INSISTENT). */
    const val CHANNEL_ALARM_FALLBACK = "guard_alarm_fallback"

    /** "Modo Guardia puede no funcionar": avisos de configuración rota. */
    const val CHANNEL_WARNINGS = "guard_warnings"

    const val ID_STATUS = 1001
    const val ID_ALARM = 1002
    const val ID_FALLBACK = 1003
    const val ID_WARNING = 1004
    const val ID_COUNTDOWN = 1005

    val VIBRATION_PATTERN = longArrayOf(0, 900, 500, 900, 1100)

    fun sirenUri(context: Context): Uri =
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.guard_siren}")

    fun alarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Guardia activa", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Indica que Modo Guardia está vigilando. Android exige mostrarla mientras el servicio de guardia funciona."
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "Alerta de guardia", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pantalla de alerta cuando salta la alarma. El sonido lo reproduce la app por el canal de alarma."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM_FALLBACK, "Alerta de guardia (reserva)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Solo se usa si Android impide arrancar el servicio de alarma. Suena en bucle hasta que la detienes."
                setSound(sirenUri(context), alarmAudioAttributes())
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WARNINGS, "Avisos de funcionamiento", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Te avisa si algo impide que Modo Guardia te despierte (permiso retirado, servicio parado...)."
            },
        )
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 10,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun alarmScreenIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 11,
        Intent(context, GuardAlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
        context, 12,
        AlarmService.stopIntent(context, "Detenida desde la notificación"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun status(context: Context, text: String, warning: Boolean): Notification =
        NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_guard)
            .setContentTitle(if (warning) "⚠️ Modo Guardia puede no funcionar" else "🟢 Modo Guardia activo")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(context))
            .build()

    /** «LLAMADA DE GUARDIA · Carlos», «ALERTA DE GUARDIA · WhatsApp», «PRUEBA DE ALARMA». */
    fun alarmTitle(r: AlarmRequest): String = when (r.kind) {
        AlarmKind.PHONE_CALL, AlarmKind.APP_CALL -> "LLAMADA DE GUARDIA" + (r.conversation?.let { " · $it" } ?: "")
        AlarmKind.TEST -> "PRUEBA DE ALARMA"
        AlarmKind.NOTIFICATION -> "ALERTA DE GUARDIA · ${r.sourceApp}"
    }

    /** La notificación del servicio de alarma: full-screen intent + botón DETENER. */
    fun alarm(context: Context, r: AlarmRequest): Notification {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.timestamp))
        val who = r.conversation ?: r.ruleLabel
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(alarmTitle(r))
            .setContentText("$who · $time")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$who · $time\n${r.message}"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setFullScreenIntent(alarmScreenIntent(context), true)
            .setContentIntent(alarmScreenIntent(context))
            .addAction(0, "DETENER ALARMA", stopIntent(context))
            .build()
    }

    fun countdown(context: Context, seconds: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Prueba de alarma en $seconds s")
            .setContentText("Puedes bloquear el teléfono para probar la pantalla de bloqueo.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "CANCELAR", stopIntent(context))
            .build()

    /** Plan B: el sonido sale del canal y FLAG_INSISTENT lo repite hasta cancelar la notificación. */
    fun fallback(context: Context, r: AlarmRequest): Notification {
        val n = NotificationCompat.Builder(context, CHANNEL_ALARM_FALLBACK)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(alarmTitle(r))
            .setContentText(r.conversation ?: r.ruleLabel)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${r.conversation ?: r.ruleLabel}\n${r.message}"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(alarmScreenIntent(context), true)
            .setContentIntent(alarmScreenIntent(context))
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        return n
    }

    fun warning(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("⚠️ MODO GUARDIA PUEDE NO FUNCIONAR CORRECTAMENTE")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
}
