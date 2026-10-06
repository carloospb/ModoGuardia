package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.Rule
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
    fun `whatsapp and whatsapp business each have their own all notifications rule`() {
        var rules = RuleEdits.setAllNotifications(emptyList(), WhatsApp.PERSONAL, on = true, label = "WhatsApp", newId = id)
        rules = RuleEdits.setAllNotifications(rules, WhatsApp.BUSINESS, on = true, label = "WhatsApp Business", newId = id)
        assertEquals(listOf(WhatsApp.PERSONAL, WhatsApp.BUSINESS), rules.map { it.packageName })
        assertTrue(rules.all { it.type == RuleType.APP })
        // Apagar «todos los avisos» de una no toca la otra.
        rules = RuleEdits.setAllNotifications(rules, WhatsApp.PERSONAL, on = false, label = null, newId = id)
        assertEquals(listOf(false, true), rules.map { it.enabled })
    }

    @Test
    fun `adding conversations never duplicates and keeps the android id`() {
        val carlos = SeenChat("Carlos", false, 1, packageName = WA, conversationId = "34600@s")
        val soporte = SeenChat("Soporte", true, 2, packageName = TG, conversationId = "tg-1")
        var rules = RuleEdits.addConversations(emptyList(), listOf(carlos, soporte), { "Telegram" }, id)
        assertEquals(2, rules.size)
        val wa = rules.first { it.name == "Carlos" }
        assertEquals("Cada regla es de su app: WhatsApp, no Business", WA, wa.packageName)
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

    @Test
    fun `old rules for both whatsapps are split when both are installed`() {
        val rules = listOf(
            groupRule("GUARDIAS").copy(id = "g"),
            personRule("Carlos").copy(id = "p", enabled = false),
            Rule(id = "any", type = RuleType.WHATSAPP_ANY),
            appRule("org.telegram.messenger", "Telegram"),
        )
        val split = RuleEdits.splitLegacyWhatsApp(rules, setOf(WhatsApp.PERSONAL, WhatsApp.BUSINESS), id)!!
        assertTrue(split.none { it.isLegacyWhatsApp })
        assertEquals(listOf(WhatsApp.PERSONAL, WhatsApp.BUSINESS), split.filter { it.name == "GUARDIAS" }.map { it.packageName })
        assertEquals("g", split.first { it.name == "GUARDIAS" }.id)
        // La pausa se conserva en las dos copias.
        assertEquals(listOf(false, false), split.filter { it.name == "Carlos" }.map { it.enabled })
        assertEquals(listOf(WhatsApp.PERSONAL, WhatsApp.BUSINESS), split.filter { it.type == RuleType.APP && it.packageName != "org.telegram.messenger" }.map { it.packageName })
        assertTrue(split.any { it.packageName == "org.telegram.messenger" })
        // Siguen sonando igual que antes: un aviso de cualquiera de las dos coincide.
        val business = waGroupMessage("GUARDIAS", "Ana", "caída", ts = 1).copy(packageName = WhatsApp.BUSINESS)
        assertTrue(es.carlospb.modoguardia.domain.RuleEngine.evaluate(business, split.filter { it.name == "GUARDIAS" }) is es.carlospb.modoguardia.domain.Evaluation.Match)
    }

    @Test
    fun `old rules go to the only whatsapp installed and stay as they are with none`() {
        val rules = listOf(groupRule("GUARDIAS").copy(id = "g"))
        val onlyBusiness = RuleEdits.splitLegacyWhatsApp(rules, setOf(WhatsApp.BUSINESS), id)!!
        assertEquals(listOf("g" to WhatsApp.BUSINESS), onlyBusiness.map { it.id to it.packageName })
        assertNull(RuleEdits.splitLegacyWhatsApp(rules, emptySet(), id))
        assertNull(RuleEdits.splitLegacyWhatsApp(onlyBusiness, setOf(WhatsApp.BUSINESS), id))
    }

    @Test
    fun `splitting never duplicates a rule that already exists for that app`() {
        val rules = listOf(groupRule("Soporte").copy(id = "old"), groupRule("Soporte").copy(id = "new", packageName = WhatsApp.PERSONAL))
        val split = RuleEdits.splitLegacyWhatsApp(rules, setOf(WhatsApp.PERSONAL, WhatsApp.BUSINESS), id)!!
        assertEquals(2, split.size)
        assertEquals(setOf(WhatsApp.PERSONAL, WhatsApp.BUSINESS), split.map { it.packageName }.toSet())
    }

    @Test
    fun `whatsapp rules of a business only phone move to whatsapp business`() {
        // La 1.2.0 enseñaba Business como «WhatsApp» y guardaba sus palabras clave en el paquete normal.
        val rules = listOf(keywordRule("URGENTE", pkg = WhatsApp.PERSONAL), appRule(WhatsApp.PERSONAL, "WhatsApp"), appRule("org.telegram.messenger", "Telegram"))
        val moved = RuleEdits.splitLegacyWhatsApp(rules, setOf(WhatsApp.BUSINESS), id)!!
        assertEquals(listOf(WhatsApp.BUSINESS, WhatsApp.BUSINESS, "org.telegram.messenger"), moved.map { it.packageName })
        // Con el WhatsApp normal instalado no se toca: son suyas.
        assertNull(RuleEdits.splitLegacyWhatsApp(rules, setOf(WhatsApp.PERSONAL, WhatsApp.BUSINESS), id))
        assertNull(RuleEdits.splitLegacyWhatsApp(moved, setOf(WhatsApp.BUSINESS), id))
    }
}
