package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.CaptureOption
import es.carlospb.modoguardia.domain.ChatCapture
import es.carlospb.modoguardia.domain.ChatKind
import es.carlospb.modoguardia.domain.Evaluation
import es.carlospb.modoguardia.domain.RuleEdits
import es.carlospb.modoguardia.domain.RuleEngine
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.domain.SeenChat
import es.carlospb.modoguardia.domain.SharedChat
import es.carlospb.modoguardia.domain.SharedChats
import es.carlospb.modoguardia.domain.WhatsApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Identificar el chat exacto: compartido desde WhatsApp («Exportar chat») o con un mensaje. */
class ChatIdentificationTest {

    // ---- Compartir desde WhatsApp ----

    @Test
    fun `whatsapp export gives the exact chat name from the subject`() {
        assertEquals(
            SharedChat("Guardias Soporte 🚨", WhatsApp.PERSONAL),
            SharedChats.fromShare("Chat de WhatsApp con Guardias Soporte 🚨", null, emptyList(), WhatsApp.PERSONAL),
        )
        assertEquals(
            SharedChat("Ángel Pérez", null),
            SharedChats.fromShare("WhatsApp Chat with Ángel Pérez", null, emptyList(), null),
        )
    }

    @Test
    fun `the file name is enough when there is no subject`() {
        assertEquals(
            SharedChat("Soporte (Turno B)", null),
            SharedChats.fromShare(null, null, listOf("Chat de WhatsApp con Soporte (Turno B).txt"), null),
        )
        assertEquals(SharedChat("Soporte", null), SharedChats.fromShare(null, null, listOf("WhatsApp Chat - Soporte.zip"), null))
    }

    @Test
    fun `whatsapp business is recognised by the sender or by the text`() {
        assertEquals(WhatsApp.BUSINESS, SharedChats.fromShare("Chat de WhatsApp con Jefe", null, emptyList(), WhatsApp.BUSINESS)?.packageName)
        assertEquals(WhatsApp.BUSINESS, SharedChats.fromShare("Chat de WhatsApp Business con Jefe", null, emptyList(), null)?.packageName)
        assertEquals("Jefe", SharedChats.fromShare("Chat de WhatsApp Business con Jefe", null, emptyList(), null)?.name)
    }

    @Test
    fun `other shares are not chats and are never guessed`() {
        assertNull(SharedChats.fromShare(null, null, listOf("IMG-20261006-WA0001.jpg"), WhatsApp.PERSONAL))
        assertNull(SharedChats.fromShare("https://t.me/c/1234/56", null, emptyList(), TG))
        assertNull(SharedChats.fromShare("Chat de WhatsApp con ", null, emptyList(), null))
        assertNull(SharedChats.fromShare(null, null, emptyList(), "com.Slack"))
    }

    @Test
    fun `a shared chat becomes a rule that matches its notifications`() {
        val chat = SharedChats.fromShare("Chat de WhatsApp con GUARDIAS SOPORTE", null, emptyList(), WA)!!
        var n = 0
        val rules = RuleEdits.addManualConversation(emptyList(), WA, chat.name, isGroup = true, label = "WhatsApp") { "r${n++}" }
        val e = waGroupMessage("Guardias Soporte", "Ana", "Se ha caído producción", ts = 10)
        assertTrue(RuleEngine.evaluate(e, rules) is Evaluation.Match)
    }

    // ---- Identificar con un mensaje ----

    @Test
    fun `a group message says which group it is`() {
        val a = ChatCapture.analyze(waGroupMessage("Soporte", "Ana", "x", ts = 1, conversationTitle = "Soporte (3 mensajes)"), null, WA, 5)!!
        assertEquals(ChatKind.GROUP, a.kind)
        assertEquals(listOf(CaptureOption("Soporte", true)), a.options)
        assertNull(a.problem)
    }

    @Test
    fun `works for any chat app, not only whatsapp`() {
        val tg = ChatCapture.analyze(personMessage("Laura", "x", 1, pkg = TG, appName = "Telegram").copy(shortcutId = "tg-9"), null, null, 5)!!
        assertEquals(listOf(CaptureOption("Laura", false)), tg.options)
        assertEquals("tg-9", tg.conversationId)
        val slack = ChatCapture.analyze(
            personMessage("#incidencias", "x", 1, pkg = "com.Slack", appName = "Slack")
                .copy(isGroupConversation = true, conversationTitle = "#incidencias"),
            null, "com.Slack", 5,
        )!!
        assertEquals(listOf(CaptureOption("#incidencias", true)), slack.options)
    }

    @Test
    fun `when android does not say person or group, both are offered`() {
        val a = ChatCapture.analyze(appNotification(WA, "WhatsApp", "Equipo: Luis", "hola", postTime = 1), null, WA, 5)!!
        assertEquals(ChatKind.UNKNOWN, a.kind)
        assertEquals(listOf(CaptureOption("Equipo", true), CaptureOption("Equipo: Luis", false)), a.options)
    }

    @Test
    fun `a notification that does not name the chat is reported, not hidden`() {
        val a = ChatCapture.analyze(appNotification(WA, "WhatsApp", "WhatsApp", "3 mensajes nuevos", postTime = 1), null, WA, 5)!!
        assertTrue(a.options.isEmpty())
        assertNotNull(a.problem)
    }

    @Test
    fun `summaries, own replies, calls, other apps and non chats are ignored`() {
        val e = personMessage("Carlos", "x", 1)
        assertNull(ChatCapture.analyze(e, "Resumen de grupo: se evalúan los avisos individuales", WA, 5))
        assertNull(ChatCapture.analyze(e, null, TG, 5))
        assertNull(ChatCapture.analyze(dialerCall("Carlos", ts = 1), null, null, 5))
        // Sin app elegida, solo avisos de chat: una descarga no es una conversación.
        assertNull(ChatCapture.analyze(appNotification("com.android.providers.downloads", "Descargas", "informe.pdf", "Completada", postTime = 1), null, null, 5))
        // Con la app elegida se enseña todo lo que llega de ella, para ver qué trae su aviso.
        assertNotNull(ChatCapture.analyze(appNotification("com.Slack", "Slack", "Ana", "hola", postTime = 1), null, "com.Slack", 5))
    }

    @Test
    fun `the same chat writing again is not repeated`() {
        val first = ChatCapture.analyze(personMessage("Carlos", "uno", 1), null, WA, 5)!!
        val second = ChatCapture.analyze(personMessage("Carlos", "dos", 2), null, WA, 9)!!
        val other = ChatCapture.analyze(personMessage("Laura", "x", 3), null, WA, 12)!!
        val list = ChatCapture.add(ChatCapture.add(ChatCapture.add(emptyList(), first), other), second)
        assertEquals(listOf("Carlos", "Laura"), list.map { it.options.single().name })
        assertEquals(9L, list.first().receivedAt)
    }

    @Test
    fun `a captured chat keeps its android id so it matches even an unclear notification`() {
        val captured = ChatCapture.analyze(personMessage("Carlos", "x", 1).copy(shortcutId = "34600@s.whatsapp.net"), null, WA, 5)!!
        val option = captured.options.single()
        var n = 0
        val rules = RuleEdits.addConversations(
            emptyList(),
            listOf(SeenChat(option.name, option.isGroup, captured.receivedAt, captured.packageName, captured.conversationId)),
            { "WhatsApp" },
        ) { "r${n++}" }
        assertEquals(RuleType.PERSON, rules.single().type)
        // Un aviso posterior que no dice si es persona o grupo, con otro título, pero del mismo chat.
        val unclear = appNotification(WA, "WhatsApp", "Carlos (2): hola", "hola", postTime = 9).copy(shortcutId = "34600@s.whatsapp.net")
        assertTrue(RuleEngine.evaluate(unclear, rules) is Evaluation.Match)
    }
}
