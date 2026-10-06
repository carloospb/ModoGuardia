package es.carlospb.modoguardia.service

import android.app.Notification
import android.app.Person
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.os.BundleCompat
import es.carlospb.modoguardia.domain.MessageLine
import es.carlospb.modoguardia.domain.NotificationEvent
import java.util.concurrent.ConcurrentHashMap

/** Resultado del análisis: el evento normalizado y, si no debe evaluarse, por qué. */
data class ParsedNotification(val event: NotificationEvent, val skipReason: String?)

/**
 * StatusBarNotification → NotificationEvent. Lee únicamente los extras públicos que Android
 * entrega al lector de notificaciones; no accede a nada de la app que publicó el aviso.
 */
class NotificationParser(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()

    private fun dialerPackages(): Set<String> {
        val set = mutableSetOf(
            "com.android.dialer", "com.google.android.dialer", "com.android.incallui",
            "com.samsung.android.incallui", "com.samsung.android.dialer", "com.android.server.telecom",
        )
        // getDefaultDialerPackage no exige permisos. Se consulta cada vez: el usuario puede cambiarlo.
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
            .getOrNull()?.let(set::add)
        return set
    }

    fun appName(packageName: String): String = labels.getOrPut(packageName) {
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun parse(sbn: StatusBarNotification): ParsedNotification {
        val n = sbn.notification
        val extras: Bundle = n.extras ?: Bundle.EMPTY
        fun cs(key: String): String? = extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }

        val title = cs(Notification.EXTRA_TITLE) ?: cs(Notification.EXTRA_TITLE_BIG)
        val text = cs(Notification.EXTRA_TEXT)
        val bigText = cs(Notification.EXTRA_BIG_TEXT)
        val subText = cs(Notification.EXTRA_SUB_TEXT)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            .orEmpty()

        // MessagingStyle: androidx sabe leerlo de cualquier versión de Android/app.
        val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
        val rawMessages = style?.messages.orEmpty().mapNotNull { m ->
            val body = m.text?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            RawMessage(MessageLine(m.person?.name?.toString(), body, m.timestamp), fromSelf = m.person == null)
        }
        // En MessagingStyle, un mensaje sin remitente es del propio usuario (respuesta desde la
        // notificación). Si TODOS carecen de remitente no se puede distinguir y se conservan.
        val someoneElse = rawMessages.any { !it.fromSelf }
        val messages = if (someoneElse) rawMessages.filterNot { it.fromSelf }.map { it.line } else rawMessages.map { it.line }
        val latestIsOwnReply = someoneElse && rawMessages.maxByOrNull { it.line.timestamp }?.fromSelf == true

        val conversation = style?.conversationTitle?.toString()?.takeIf { it.isNotBlank() }
            ?: cs(Notification.EXTRA_CONVERSATION_TITLE)
        val isGroup = if (extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)) {
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
        } else {
            null
        }

        // Personas del aviso: en las llamadas, quién llama (CallStyle, Android 12+); en los chats, los
        // participantes. Solo nombre y, si es un número «tel:», el número.
        val people = buildList {
            BundleCompat.getParcelableArrayList(extras, Notification.EXTRA_PEOPLE_LIST, Person::class.java)?.let(::addAll)
            BundleCompat.getParcelable(extras, EXTRA_CALL_PERSON, Person::class.java)?.let(::add)
        }
        @Suppress("DEPRECATION") // EXTRA_PEOPLE: forma antigua, aún usada por algunos marcadores.
        val legacyPeople = extras.getStringArray(Notification.EXTRA_PEOPLE)?.toList().orEmpty()

        val isCall = n.category == Notification.CATEGORY_CALL
        // Notification.EXTRA_CALL_TYPE ("android.callType") existe desde Android 12; antes vale 0.
        val callType = extras.getInt(EXTRA_CALL_TYPE, 0)
        val incomingCall = isCall && (callType == CALL_TYPE_INCOMING || (callType == 0 && n.fullScreenIntent != null))
        val phoneCall = incomingCall && sbn.packageName in dialerPackages()

        val event = NotificationEvent(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName(sbn.packageName),
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            conversationTitle = conversation,
            isGroupConversation = isGroup,
            messages = messages,
            textLines = lines,
            category = n.category,
            isIncomingCall = incomingCall,
            isPhoneCall = phoneCall,
            postTime = sbn.postTime,
            whenTime = n.`when`,
            shortcutId = n.shortcutId?.takeIf { it.isNotBlank() },
            channelId = n.channelId,
            personNames = people.mapNotNull { it.name?.toString()?.takeIf(String::isNotBlank) }.distinct(),
            personNumbers = (people.mapNotNull { it.uri } + legacyPeople)
                .filter { it.startsWith(TEL) }
                .map { Uri.decode(it.removePrefix(TEL)) }
                .distinct(),
        )

        val flags = n.flags
        val skip = when {
            flags and Notification.FLAG_GROUP_SUMMARY != 0 ->
                "Resumen de grupo: se evalúan los avisos individuales"
            !incomingCall && flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0 ->
                "Aviso persistente o en curso: no es un aviso nuevo"
            latestIsOwnReply ->
                "Actualización por una respuesta tuya"
            !incomingCall && title == null && text == null && bigText == null && messages.isEmpty() && lines.isEmpty() ->
                "Sin contenido"
            else -> null
        }
        return ParsedNotification(event, skip)
    }

    private data class RawMessage(val line: MessageLine, val fromSelf: Boolean)

    private companion object {
        const val EXTRA_CALL_TYPE = "android.callType"
        const val CALL_TYPE_INCOMING = 1

        /** Notification.EXTRA_CALL_PERSON (Android 12). */
        const val EXTRA_CALL_PERSON = "android.callPerson"
        const val TEL = "tel:"
    }
}
