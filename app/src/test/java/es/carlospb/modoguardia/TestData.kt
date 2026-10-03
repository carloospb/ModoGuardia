package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.MessageLine
import es.carlospb.modoguardia.domain.NotificationEvent
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleType

const val WA = "com.whatsapp"

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

fun appNotification(pkg: String, appName: String, title: String?, text: String?, postTime: Long, key: String = "0|$pkg|7") =
    NotificationEvent(key = key, packageName = pkg, appName = appName, title = title, text = text, postTime = postTime)

fun groupRule(name: String) = Rule(id = "g-$name", type = RuleType.WHATSAPP_GROUP, groupName = name)
fun appRule(pkg: String, label: String) = Rule(id = "a-$pkg", type = RuleType.APP, packageName = pkg, appLabel = label)
fun keywordRule(vararg words: String, pkg: String? = null, all: Boolean = false, ignoreCase: Boolean = true) =
    Rule(id = "k-${words.joinToString()}", type = RuleType.KEYWORDS, packageName = pkg, keywords = words.toList(), matchAll = all, ignoreCase = ignoreCase)
