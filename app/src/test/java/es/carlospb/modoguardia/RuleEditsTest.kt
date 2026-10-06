package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.RuleEdits
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.domain.SeenChat
import es.carlospb.modoguardia.domain.WatchSummary
import es.carlospb.modoguardia.domain.WhatsApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que la interfaz hace con las reglas: sin duplicados y sin perder nada. */
class RuleEditsTest {
    private var n = 0
    private val id = { "id${n++}" }

    @Test
    fun `all notifications on, paused and back`() {
        var rules = RuleEdits.setAllNotifications(emptyList(), TG, on = true, label = "Telegram", newId = id)
        assertEquals(RuleType.APP, rules.single().type)
        assertEquals("Telegram", rules.single().appLabel)
        // «Solo los chats que elijas»: se pausa, no se borra, y la app sigue en la lista.
        rules = RuleEdits.setAllNotifications(rules, TG, on = false, label = null, newId = id)
        assertFalse(rules.single().enabled)
        assertEquals(1, WatchSummary.appsOf(rules).size)
        rules = RuleEdits.setAllNotifications(rules, TG, on = true, label = null, newId = id)
        assertEquals(1, rules.size)
        assertTrue(rules.single().enabled)
    }

    @Test
    fun `whatsapp all notifications uses the whatsapp rule for both apps`() {
        val rules = RuleEdits.setAllNotifications(emptyList(), WhatsApp.KEY, on = true, label = null, newId = id)
        assertEquals(RuleType.WHATSAPP_ANY, rules.single().type)
        assertEquals(WhatsApp.KEY, RuleEdits.appKeyOf("com.whatsapp.w4b"))
    }

    @Test
    fun `adding conversations never duplicates and keeps the android id`() {
        val carlos = SeenChat("Carlos", false, 1, packageName = WA, conversationId = "34600@s")
        val soporte = SeenChat("Soporte", true, 2, packageName = TG, conversationId = "tg-1")
        var rules = RuleEdits.addConversations(emptyList(), listOf(carlos, soporte), { "Telegram" }, id)
        assertEquals(2, rules.size)
        val wa = rules.first { it.name == "Carlos" }
        assertNull("WhatsApp: sin app concreta, vale también para Business", wa.packageName)
        assertEquals("34600@s", wa.conversationId)
        assertEquals(TG, rules.first { it.name == "Soporte" }.packageName)
        // Volver a elegirlos (o el mismo con otra forma de escribirse) no duplica.
        rules = RuleEdits.addConversations(rules, listOf(carlos.copy(name = "CARLOS"), soporte), { null }, id)
        assertEquals(2, rules.size)
    }

    @Test
    fun `a paused conversation chosen again is reactivated`() {
        val rules = listOf(personRule("Carlos").copy(enabled = false))
        val updated = RuleEdits.addConversations(rules, listOf(SeenChat("Carlos", false, 1)), { null }, id)
        assertTrue(updated.single().enabled)
    }

    @Test
    fun `typed conversations are cleaned and empty names are ignored`() {
        var rules = RuleEdits.addManualConversation(emptyList(), WhatsApp.KEY, "  Guardias   Soporte ", isGroup = true, label = null, newId = id)
        assertEquals("Guardias Soporte", rules.single().name)
        assertEquals(RuleType.GROUP, rules.single().type)
        rules = RuleEdits.addManualConversation(rules, WhatsApp.KEY, "   ", isGroup = false, label = null, newId = id)
        assertEquals(1, rules.size)
    }

    @Test
    fun `remove app removes its rules only`() {
        val rules = listOf(personRule("Carlos"), groupRule("Equipo").copy(packageName = TG), keywordRule("URGENTE", pkg = TG), callRule("Carlos", "600"))
        val left = RuleEdits.removeApp(rules, TG)
        assertEquals(listOf("Carlos", "Carlos"), left.map { it.name })
        assertTrue(left.any { it.isCall })
    }

    @Test
    fun `picking the same contact again adds the new number to the same rule`() {
        var rules = RuleEdits.addCallContact(emptyList(), "Carlos Pérez", "Pérez, Carlos", "+34 600 11 22 33", id)
        rules = RuleEdits.addCallContact(rules, "carlos perez", null, "+34 911 22 33 44", id)
        rules = RuleEdits.addCallContact(rules, "Carlos Pérez", null, "600112233", id)
        val carlos = rules.single()
        assertEquals(listOf("+34 600 11 22 33", "+34 911 22 33 44"), carlos.numbers)
        assertEquals(listOf("Carlos Pérez", "Pérez, Carlos"), carlos.aliases)
        assertTrue(carlos.isContact)
    }

    @Test
    fun `typed numbers are labels, not contact names, and are not duplicated`() {
        var rules = RuleEdits.addCallNumber(emptyList(), "+34 912 34 56 78", "Empresa", id)
        rules = RuleEdits.addCallNumber(rules, "912345678", "Otra", id)
        rules = RuleEdits.addCallNumber(rules, "12", null, id)
        val empresa = rules.single()
        assertEquals("Empresa", empresa.name)
        assertTrue(empresa.aliases.isEmpty())
        assertFalse(empresa.isContact)
    }

    @Test
    fun `contact without a name keeps only its number`() {
        val rules = RuleEdits.addCallContact(emptyList(), "  ", null, "611 22 33 44", id)
        assertFalse(rules.single().isContact)
        assertEquals(listOf("611 22 33 44"), rules.single().numbers)
    }
}
