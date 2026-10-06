package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.MessageLine
import es.carlospb.modoguardia.domain.NotificationEvent
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleType

const val WA = "com.whatsapp"
const val TG = "org.telegram.messenger"
const val DIALER = "com.google.android.dialer"

fun waGroupMessage(
    group: String,
    sender: String,
    text: String,
    ts: Long,
    postTime: Long = ts,
    titleStyle: String = "$group: $sender",
    conversationTitle: String? = group,
) = NotificationEvent(
    key = "0|$WA|1|$group|10101",
    packageName = WA,
    appName = "WhatsApp",
    title = titleStyle,
    text = text,
    conversationTitle = conversationTitle,
    isGroupConversation = true,
    messages = listOf(MessageLine(sender, text, ts)),
    postTime = postTime,
)

/** Mensaje de un chat individual (MessagingStyle, Android marca que no es grupo). */
fun personMessage(name: String, text: String, ts: Long, pkg: String = WA, appName: String = "WhatsApp") = NotificationEvent(
    key = "0|$pkg|1|$name|10101",
    packageName = pkg,
    appName = appName,
    title = name,
    text = text,
    conversationTitle = null,
    isGroupConversation = false,
    messages = listOf(MessageLine(name, text, ts)),
    postTime = ts,
)

fun appNotification(pkg: String, appName: String, title: String?, text: String?, postTime: Long, key: String = "0|$pkg|7") =
    NotificationEvent(key = key, packageName = pkg, appName = appName, title = title, text = text, postTime = postTime)

/** Aviso de llamada entrante del marcador: el título es el contacto o el número. */
fun dialerCall(title: String?, ts: Long, text: String? = "Llamada entrante") =
    appNotification(DIALER, "Teléfono", title, text, postTime = ts, key = "0|$DIALER|1|call")
        .copy(isIncomingCall = true, isPhoneCall = true, category = "call")

fun groupRule(name: String) = Rule(id = "g-$name", type = RuleType.GROUP, name = name)
fun personRule(name: String, pkg: String? = null) = Rule(id = "p-$name-$pkg", type = RuleType.PERSON, packageName = pkg, name = name)
fun appRule(pkg: String, label: String) = Rule(id = "a-$pkg", type = RuleType.APP, packageName = pkg, appLabel = label)
fun keywordRule(vararg words: String, pkg: String? = null, all: Boolean = false, ignoreCase: Boolean = true) =
    Rule(id = "k-${words.joinToString()}", type = RuleType.KEYWORDS, packageName = pkg, keywords = words.toList(), matchAll = all, ignoreCase = ignoreCase)
/** Contacto de la agenda: se reconoce por su nombre (y [alt], «Apellido, Nombre») o por sus números. */
fun callRule(name: String?, vararg numbers: String, alt: String? = null) =
    Rule(id = "call-$name-${numbers.joinToString()}", type = RuleType.CALL, name = name, numbers = numbers.toList(), aliases = listOfNotNull(name, alt))

/** Número añadido a mano: la etiqueta es solo para mostrar. */
fun numberRule(number: String, label: String? = null) =
    Rule(id = "num-$number", type = RuleType.CALL, name = label, numbers = listOf(number))
