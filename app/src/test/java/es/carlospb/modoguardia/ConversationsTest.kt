package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.ChatKind
import es.carlospb.modoguardia.domain.Conversations
import es.carlospb.modoguardia.domain.DetectedConversation
import es.carlospb.modoguardia.domain.Evaluation
import es.carlospb.modoguardia.domain.MessageLine
import es.carlospb.modoguardia.domain.RuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationsTest {

    @Test
    fun `android group flag decides the kind`() {
        assertEquals(ChatKind.GROUP, Conversations.kind(waGroupMessage("Soporte", "Ana", "x", ts = 1)))
        assertEquals(ChatKind.INDIVIDUAL, Conversations.kind(personMessage("Carlos", "x", 1)))
    }

    @Test
    fun `without the flag a conversation title different from the title is a group`() {
        val e = waGroupMessage("Equipo técnico", "Ana", "x", ts = 1, titleStyle = "Ana").copy(isGroupConversation = null)
        assertEquals(ChatKind.GROUP, Conversations.kind(e))
    }

    @Test
    fun `whatsapp channel is used only as a hint when nothing else says it`() {
        val old = appNotification(WA, "WhatsApp", "GUARDIAS SOPORTE", "hola", postTime = 1).copy(channelId = "group_chat_defaults_4")
        assertEquals(ChatKind.GROUP, Conversations.kind(old))
        val person = appNotification(WA, "WhatsApp", "Carlos", "hola", postTime = 1).copy(channelId = "individual_chat_defaults_3")
        assertEquals(ChatKind.INDIVIDUAL, Conversations.kind(person))
        // En otra app el mismo nombre de canal no significa nada.
        val other = appNotification(TG, "Telegram", "Carlos: hola", "hola", postTime = 1).copy(channelId = "group_chat_x")
        assertEquals(ChatKind.UNKNOWN, Conversations.kind(other))
    }

    @Test
    fun `unclear notifications are unknown, never guessed`() {
        assertEquals(ChatKind.UNKNOWN, Conversations.kind(appNotification(WA, "WhatsApp", "GUARDIAS: Luis", "hola", postTime = 1)))
        assertEquals(ChatKind.UNKNOWN, Conversations.kind(appNotification(WA, "WhatsApp", null, "hola", postTime = 1)))
    }

    @Test
    fun `identifies group and person conversations of any chat app`() {
        assertEquals(
            DetectedConversation(WA, "Soporte", isGroup = true, conversationId = null),
            Conversations.identify(waGroupMessage("Soporte", "Ana", "x", ts = 1, conversationTitle = "Soporte (3 mensajes)")),
        )
        assertEquals(
            DetectedConversation(TG, "Laura", isGroup = false, conversationId = "tg-55"),
            Conversations.identify(personMessage("Laura", "x", 1, pkg = TG, appName = "Telegram").copy(shortcutId = "tg-55")),
        )
    }

    @Test
    fun `calls, generic app notifications and non chat apps are not conversations`() {
        assertNull(Conversations.identify(dialerCall("Carlos", ts = 1)))
        assertNull(Conversations.identify(personMessage("WhatsApp", "2 mensajes nuevos", 1)))
        // Un correo con título «Juan» no es un chat: no trae MessagingStyle ni categoría de mensaje.
        assertNull(Conversations.identify(appNotification("com.google.android.gm", "Gmail", "Juan", "Factura", postTime = 1)))
    }

    @Test
    fun `sms style conversations are recognised`() {
        val sms = personMessage("Mamá", "¿Llegaste?", 1, pkg = "com.google.android.apps.messaging", appName = "Mensajes")
        assertEquals(DetectedConversation("com.google.android.apps.messaging", "Mamá", false, null), Conversations.identify(sms))
    }

    @Test
    fun `senders only count as the person when android says it is not a group`() {
        val e = personMessage("Carlos", "x", 1).copy(title = "2 mensajes nuevos", messages = listOf(MessageLine("Carlos", "x", 1)))
        assertTrue("Carlos" in Conversations.personCandidates(e))
        val group = waGroupMessage("Soporte", "Carlos", "x", ts = 1)
        assertTrue(Conversations.personCandidates(group).isEmpty())
    }

    @Test
    fun `group rule without candidates flags unresolved only when it could be a group`() {
        val unknown = appNotification(WA, "WhatsApp", null, "hola", postTime = 1)
        assertEquals(Evaluation.NoMatch(groupUnresolved = true), RuleEngine.evaluate(unknown, listOf(groupRule("Soporte"))))
        assertEquals(Evaluation.NoMatch(groupUnresolved = false), RuleEngine.evaluate(personMessage("Carlos", "x", 1), listOf(groupRule("Soporte"))))
    }

    @Test
    fun `conversation id never makes a person rule match a group`() {
        val rule = personRule("Carlos").copy(conversationId = "same-id")
        val group = waGroupMessage("Amigos", "Carlos", "x", ts = 1).copy(shortcutId = "same-id")
        assertEquals(Evaluation.NoMatch(groupUnresolved = false), RuleEngine.evaluate(group, listOf(rule)))
    }
}
