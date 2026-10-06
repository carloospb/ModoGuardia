package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.Evaluation
import es.carlospb.modoguardia.domain.NotificationEvent
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleEngine
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.domain.Conversations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private fun matches(event: NotificationEvent, vararg rules: Rule) = RuleEngine.evaluate(event, rules.toList()) is Evaluation.Match

    // --- Aplicación ---

    @Test
    fun `app rule matches its package`() {
        val e = appNotification("com.microsoft.teams", "Teams", "Ana", "¿Puedes mirar esto?", 1000)
        assertTrue(matches(e, appRule("com.microsoft.teams", "Teams")))
    }

    @Test
    fun `app rule does not match other packages`() {
        val e = appNotification("com.instagram.android", "Instagram", "x", "le gusta tu foto", 1000)
        assertFalse(matches(e, appRule("com.microsoft.teams", "Teams")))
    }

    @Test
    fun `any whatsapp matches whatsapp and business but not telegram`() {
        val rule = Rule("w", RuleType.WHATSAPP_ANY)
        assertTrue(matches(appNotification("com.whatsapp", "WhatsApp", "Juan", "hola", 1), rule))
        assertTrue(matches(appNotification("com.whatsapp.w4b", "WA Business", "Juan", "hola", 1), rule))
        assertFalse(matches(appNotification("org.telegram.messenger", "Telegram", "Juan", "hola", 1), rule))
    }

    // --- Grupo ---

    @Test
    fun `group rule matches by conversation title`() {
        val e = waGroupMessage("GUARDIAS SOPORTE", "Luis", "URGENTE: servidor principal caído.", 1000)
        assertTrue(matches(e, groupRule("GUARDIAS SOPORTE")))
    }

    @Test
    fun `group rule ignores case, accents, extra spaces and bidi marks`() {
        val e = waGroupMessage("‪Guardias  Soporte‬", "Luis", "hola", 1000)
        assertTrue(matches(e, groupRule("guardias soporte")))
        val accented = waGroupMessage("Guardias Técnicas", "Luis", "hola", 1000)
        assertTrue(matches(accented, groupRule("GUARDIAS TECNICAS")))
    }

    @Test
    fun `group rule matches title prefix when there is no conversation title`() {
        val e = waGroupMessage("GUARDIAS SOPORTE", "Luis", "hola", 1000, conversationTitle = null)
        assertTrue(matches(e, groupRule("GUARDIAS SOPORTE")))
    }

    @Test
    fun `group rule matches title with message count suffix`() {
        val e = waGroupMessage(
            "GUARDIAS SOPORTE", "Luis", "hola", 1000,
            titleStyle = "GUARDIAS SOPORTE (3 mensajes): Luis", conversationTitle = null,
        )
        assertTrue(matches(e, groupRule("GUARDIAS SOPORTE")))
    }

    @Test
    fun `group rule matches legacy sender at group title`() {
        val e = waGroupMessage("GUARDIAS SOPORTE", "Luis", "hola", 1000, titleStyle = "Luis @ GUARDIAS SOPORTE", conversationTitle = null)
        assertTrue(matches(e, groupRule("GUARDIAS SOPORTE")))
    }

    @Test
    fun `message from another group does not match`() {
        val e = waGroupMessage("Familia", "Mamá", "URGENTE: comprar pan", 1000)
        assertFalse(matches(e, groupRule("GUARDIAS SOPORTE")))
    }

    @Test
    fun `group name must be equal, not just contained`() {
        val e = waGroupMessage("GUARDIAS SOPORTE ANTIGUO", "Luis", "hola", 1000)
        assertFalse(matches(e, groupRule("GUARDIAS SOPORTE")))
    }

    @Test
    fun `group rule never matches non whatsapp apps`() {
        val e = appNotification("org.telegram.messenger", "Telegram", "GUARDIAS SOPORTE: Luis", "hola", 1000)
        assertFalse(matches(e, groupRule("GUARDIAS SOPORTE")))
    }

    @Test
    fun `private chat named like the sender is not a group`() {
        val e = NotificationEvent(key = "k", packageName = WA, appName = "WhatsApp", title = "GUARDIAS SOPORTE", text = "hola", postTime = 1)
        // Sin conversationTitle, sin "Grupo: remitente" y sin marca de grupo: no se puede afirmar que sea el grupo.
        val result = RuleEngine.evaluate(e, listOf(groupRule("GUARDIAS SOPORTE")))
        assertTrue(result is Evaluation.NoMatch)
    }

    @Test
    fun `whatsapp notification without any group data is reported as unresolved`() {
        val e = NotificationEvent(key = "k", packageName = WA, appName = "WhatsApp", title = null, text = "2 mensajes nuevos", postTime = 1)
        assertTrue(Conversations.groupCandidates(e).isEmpty())
        val result = RuleEngine.evaluate(e, listOf(groupRule("GUARDIAS SOPORTE")))
        assertEquals(Evaluation.NoMatch(groupUnresolved = true), result)
    }

    // --- Palabras clave ---

    @Test
    fun `any keyword matches`() {
        val e = appNotification("com.slack", "Slack", "#alertas", "Incidencia en producción", 1)
        assertTrue(matches(e, keywordRule("URGENTE", "INCIDENCIA")))
    }

    @Test
    fun `all keywords requires every word`() {
        val e = appNotification("com.slack", "Slack", "#alertas", "ERROR en el servidor", 1)
        assertTrue(matches(e, keywordRule("error", "servidor", all = true)))
        assertFalse(matches(e, keywordRule("error", "base de datos", all = true)))
    }

    @Test
    fun `ignore case also ignores accents`() {
        val e = appNotification("com.slack", "Slack", null, "Fallo crítico, CAÍDA total", 1)
        assertTrue(matches(e, keywordRule("CRITICO")))
        assertTrue(matches(e, keywordRule("caida")))
    }

    @Test
    fun `case sensitive rule does not match different case`() {
        val e = appNotification("com.slack", "Slack", null, "urgente: revisar", 1)
        assertFalse(matches(e, keywordRule("URGENTE", ignoreCase = false)))
        assertTrue(matches(e, keywordRule("urgente", ignoreCase = false)))
    }

    @Test
    fun `keyword rule restricted to an app ignores other apps`() {
        val tg = appNotification("org.telegram.messenger", "Telegram", "Ana", "URGENTE", 1)
        val wa = appNotification(WA, "WhatsApp", "Ana", "URGENTE", 1)
        val rule = keywordRule("URGENTE", pkg = WA)
        assertFalse(matches(tg, rule))
        assertTrue(matches(wa, rule))
    }

    @Test
    fun `keywords are searched in messaging style messages`() {
        val e = waGroupMessage("Otro grupo", "Luis", "Esto es URGENTE", 1000)
        assertTrue(matches(e, keywordRule("urgente", pkg = WA)))
    }

    @Test
    fun `no keyword present does not match`() {
        val e = appNotification("com.slack", "Slack", "#general", "¿Comemos?", 1)
        assertFalse(matches(e, keywordRule("URGENTE", "INCIDENCIA", "CRÍTICO", "CAÍDA", "SERVIDOR", "ERROR")))
    }

    // --- Generales ---

    @Test
    fun `disabled and invalid rules never match`() {
        val e = appNotification("com.slack", "Slack", null, "URGENTE", 1)
        assertFalse(matches(e, keywordRule("URGENTE").copy(enabled = false)))
        assertFalse(matches(e, Rule("bad", RuleType.KEYWORDS, keywords = listOf(" ", ""))))
        assertFalse(matches(e, Rule("bad2", RuleType.APP, packageName = null)))
    }

    @Test
    fun `most specific rule is reported`() {
        val e = waGroupMessage("GUARDIAS SOPORTE", "Luis", "URGENTE", 1000)
        val result = RuleEngine.evaluate(e, listOf(Rule("any", RuleType.WHATSAPP_ANY), groupRule("GUARDIAS SOPORTE")))
        assertEquals("GUARDIAS SOPORTE", (result as Evaluation.Match).rule.label())
    }
}
