package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.ChatKind
import es.carlospb.modoguardia.domain.Evaluation
import es.carlospb.modoguardia.domain.MessageLine
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleEngine
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.domain.SeenChat
import es.carlospb.modoguardia.domain.SeenChats
import es.carlospb.modoguardia.domain.WhatsApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenChatsTest {
    private val hour = 60 * 60 * 1000L

    private fun individual(name: String, text: String = "hola", flag: Boolean? = false, title: String = name) =
        waGroupMessage(name, name, text, ts = 1, titleStyle = title, conversationTitle = null).copy(isGroupConversation = flag)

    private fun chatRule(name: String) = Rule(id = "c-$name", type = RuleType.WHATSAPP_CHAT, groupName = name)

    private fun matches(e: es.carlospb.modoguardia.domain.NotificationEvent, rule: Rule) =
        RuleEngine.evaluate(e, listOf(rule)) is Evaluation.Match

    @Test
    fun `group chat comes from conversationTitle without count suffix`() {
        val e = waGroupMessage("GUARDIAS SOPORTE", "Luis", "hola", ts = 1, conversationTitle = "GUARDIAS SOPORTE (3 mensajes)")
        assertEquals(SeenChat("GUARDIAS SOPORTE", isGroup = true, lastSeen = 7), SeenChats.chatOf(e, now = 7))
    }

    @Test
    fun `individual chat comes from the title`() {
        assertEquals(SeenChat("Ana López", isGroup = false, lastSeen = 7), SeenChats.chatOf(individual("Ana López", title = "Ana López (2 mensajes)"), now = 7))
    }

    @Test
    fun `unclear chats and other apps are not listed`() {
        // Sin marca de grupo, sin conversationTitle y con forma "Grupo: Remitente": no se adivina.
        assertNull(SeenChats.chatOf(individual("Luis", flag = null, title = "GUARDIAS: Luis"), now = 1))
        assertNull(SeenChats.chatOf(appNotification("com.slack", "Slack", "Canal", "hola", postTime = 1), now = 1))
    }

    @Test
    fun `a rule created from a seen chat matches that chat`() {
        val group = waGroupMessage("Guardias ‎Soporte 🚨", "Luis", "caído", ts = 1, conversationTitle = "Guardias ‎Soporte 🚨 (2 mensajes)")
        val g = SeenChats.chatOf(group, now = 1)!!
        assertTrue(matches(group, Rule(id = "g", type = g.ruleType, groupName = g.name)))

        val ana = individual("Ana López", title = "Ana López (2 mensajes)")
        val c = SeenChats.chatOf(ana, now = 1)!!
        assertEquals(RuleType.WHATSAPP_CHAT, c.ruleType)
        assertTrue(matches(ana, Rule(id = "c", type = c.ruleType, groupName = c.name)))
    }

    @Test
    fun `individual chat rule ignores the same person writing in a group`() {
        val inGroup = waGroupMessage("GUARDIAS", "Ana López", "hola", ts = 1)
        assertEquals(ChatKind.GROUP, WhatsApp.chatKind(inGroup))
        assertFalse(matches(inGroup, chatRule("Ana López")))
        assertFalse(matches(individual("Pedro"), chatRule("Ana López")))
    }

    @Test
    fun `individual chat matches by sender when Android confirms it is not a group`() {
        val e = individual("Ana", title = "Ana").copy(title = "2 mensajes nuevos", messages = listOf(MessageLine("Ana", "hola", 1)))
        assertTrue(matches(e, chatRule("Ana")))
    }

    @Test
    fun `recently seen chat is not rewritten, newest first otherwise`() {
        val list = listOf(SeenChat("A", true, 0), SeenChat("B", false, 0))
        assertNull(SeenChats.record(list, SeenChat("A", true, hour - 1)))
        assertEquals(listOf("B", "A"), SeenChats.record(list, SeenChat("B", false, hour))!!.map { it.name })
        assertEquals(listOf("C", "A", "B"), SeenChats.record(list, SeenChat("C", false, 5))!!.map { it.name })
    }

    @Test
    fun `group and individual chat with the same name are different entries`() {
        val list = SeenChats.record(listOf(SeenChat("Ana", true, 0)), SeenChat("Ana", false, 5))!!
        assertEquals(2, list.size)
    }

    @Test
    fun `same chat with different case or accents is one entry with the newest spelling`() {
        assertEquals(listOf(SeenChat("GUARDIAS", true, 5)), SeenChats.record(listOf(SeenChat("Guardias", true, 0)), SeenChat("GUARDIAS", true, 5)))
    }

    @Test
    fun `list is capped`() {
        val full = (1..SeenChats.MAX).map { SeenChat("G$it", true, 0) }
        val updated = SeenChats.record(full, SeenChat("Nuevo", false, 1))!!
        assertEquals(SeenChats.MAX, updated.size)
        assertEquals("Nuevo", updated.first().name)
    }

    @Test
    fun `encode and decode round trip, ignoring broken lines`() {
        val chats = listOf(SeenChat("GUARDIAS: SOPORTE 🚨", true, 123), SeenChat("Ana", false, 456))
        assertEquals(chats, SeenChats.decode(SeenChats.encode(chats)))
        assertEquals(listOf(SeenChat("ok", false, 1)), SeenChats.decode("roto\nx\tG\tmal\n3\tZ\tmal\n1\tC\tok\n2\tG\t"))
        assertEquals(emptyList<SeenChat>(), SeenChats.decode(null))
    }
}
