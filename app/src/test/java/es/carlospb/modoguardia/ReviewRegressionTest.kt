package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.AlarmRequest
import es.carlospb.modoguardia.domain.AlarmTrigger
import es.carlospb.modoguardia.domain.CallMode
import es.carlospb.modoguardia.domain.CallerId
import es.carlospb.modoguardia.domain.Deduplicator
import es.carlospb.modoguardia.domain.Evaluation
import es.carlospb.modoguardia.domain.GuardEngine
import es.carlospb.modoguardia.domain.GuardSnapshot
import es.carlospb.modoguardia.domain.HistoryLog
import es.carlospb.modoguardia.domain.HistoryType
import es.carlospb.modoguardia.domain.Outcome
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleEdits
import es.carlospb.modoguardia.domain.RuleEngine
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.domain.WhatsApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fallos encontrados en la revisión del rediseño: cada uno con su prueba para que no vuelva. */
class ReviewRegressionTest {

    private class FakeAlarm : AlarmTrigger {
        val triggered = mutableListOf<AlarmRequest>()
        var ringing = false
        override fun isRinging() = ringing
        override fun trigger(request: AlarmRequest) {
            triggered += request
            ringing = true
        }
        override fun noteAdditionalMatch(request: AlarmRequest) = Unit
        override fun onPhoneCallAnswered() = Unit
    }

    private class FakeHistory : HistoryLog {
        override suspend fun log(type: HistoryType, title: String, detail: String?, time: Long) = Unit
    }

    private val guardStart = 1_000_000L
    private var now = guardStart + 60_000
    private var snapshot = GuardSnapshot(
        active = true, startedAt = guardStart, rules = emptyList(),
        wakeOnPhoneCalls = true, wakeOnAppCalls = false, callMode = CallMode.SELECTED,
    )
    private val alarm = FakeAlarm()
    private val engine = GuardEngine({ snapshot }, Deduplicator(), alarm, FakeHistory(), clock = { now })

    // 1. WhatsApp y WhatsApp Business se vigilan por separado: una regla de una no salta con la otra.
    @Test
    fun `whatsapp and whatsapp business rules are kept apart`() {
        val business = personMessage("Carlos", "URGENTE: caída", 1, pkg = WhatsApp.BUSINESS, appName = "WhatsApp Business")
        val personal = personMessage("Carlos", "URGENTE: caída", 1, pkg = WhatsApp.PERSONAL, appName = "WhatsApp")
        val personalKeywords = keywordRule("URGENTE", pkg = WhatsApp.PERSONAL)
        val businessKeywords = keywordRule("URGENTE", pkg = WhatsApp.BUSINESS)
        assertTrue(RuleEngine.evaluate(business, listOf(personalKeywords)) is Evaluation.NoMatch)
        assertTrue(RuleEngine.evaluate(business, listOf(businessKeywords)) is Evaluation.Match)
        assertTrue(RuleEngine.evaluate(personal, listOf(businessKeywords)) is Evaluation.NoMatch)
        assertTrue(RuleEngine.evaluate(business, listOf(appRule(WhatsApp.PERSONAL, "WhatsApp"))) is Evaluation.NoMatch)
        assertTrue(RuleEngine.evaluate(business, listOf(personRule("Carlos", pkg = WhatsApp.PERSONAL))) is Evaluation.NoMatch)
        assertTrue(RuleEngine.evaluate(business, listOf(personRule("Carlos", pkg = WhatsApp.BUSINESS))) is Evaluation.Match)
        assertEquals(WhatsApp.BUSINESS, businessKeywords.appKey)
    }

    // 2. Un «número privado» en otro idioma no es un nombre.
    @Test
    fun `withheld number texts in other languages are not names`() {
        listOf("Número privat", "Desconegut", "Número descoñecido", "Zenbaki pribatua", "Numéro masqué", "Unbekannt", "Numero privato", "Chamada privada")
            .forEach { assertTrue(it, CallerId.from(dialerCall(it, ts = 1, text = null)).isEmpty) }
        assertEquals(listOf("Carlos"), CallerId.from(dialerCall("Carlos", ts = 1)).names)
    }

    @Test
    fun `withheld number never lets an unknown text rule out the chosen contacts`() = runTest {
        snapshot = snapshot.copy(rules = listOf(callRule("Carlos", "600112233")))
        // Con «Registro de llamadas»: "" = número oculto.
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging(""))
        now += 500
        assertEquals(Outcome.AWAITING_CALLER, engine.onNotification(dialerCall("Texto raro del marcador", now)))
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.ALARM, engine.onCallerTimeout())
    }

    // 4. Sin permiso «Teléfono» (no hay IDLE), una llamada no hereda lo que se supo de la anterior.
    @Test
    fun `without phone permission each call is evaluated on its own`() = runTest {
        snapshot = snapshot.copy(rules = listOf(callRule("Carlos", "600112233")))
        assertEquals(Outcome.NO_MATCH, engine.onNotification(dialerCall("Mamá", now).copy(whenTime = now)))
        now += 40_000
        val withheld = dialerCall("Número privado", now, text = null).copy(whenTime = now)
        assertEquals(Outcome.ALARM, engine.onNotification(withheld))
        assertEquals("Llamada sin identificar", alarm.triggered.single().ruleLabel)
    }

    // 5. Aviso del marcador antes que la señal: si el número exacto va a llegar, se espera.
    @Test
    fun `dialer notification first waits for the exact number when it can come`() = runTest {
        snapshot = snapshot.copy(rules = listOf(numberRule("912345678", label = "Hospital")), exactNumberAvailable = true)
        assertEquals(Outcome.AWAITING_CALLER, engine.onNotification(dialerCall("Tienda online", now)))
        now += 300
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging("611223344"))
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.NO_MATCH, engine.onCallerTimeout())
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `dialer notification first and then the chosen number rings`() = runTest {
        snapshot = snapshot.copy(rules = listOf(numberRule("912345678", label = "Hospital")), exactNumberAvailable = true)
        assertEquals(Outcome.AWAITING_CALLER, engine.onNotification(dialerCall("Hospital Universitario", now)))
        now += 300
        assertEquals(Outcome.ALARM, engine.onPhoneCallRinging("+34912345678"))
    }

    @Test
    fun `information arriving after answering never rings during the call`() = runTest {
        snapshot = snapshot.copy(rules = listOf(numberRule("912345678")), exactNumberAvailable = true)
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging(null))
        engine.onPhoneCallAnswered()
        now += 200
        assertEquals(Outcome.NO_MATCH, engine.onNotification(dialerCall("912 34 56 78", now)))
        assertTrue(alarm.triggered.isEmpty())
    }

    // 6. Volver a añadir un número pausado lo reactiva.
    @Test
    fun `adding a paused number again reactivates it`() {
        val paused = numberRule("912345678").copy(enabled = false)
        val rules = RuleEdits.addCallNumber(listOf(paused), "+34 912 34 56 78", null) { "nuevo" }
        assertEquals(1, rules.size)
        assertTrue(rules.single().enabled)
        assertEquals(RuleType.CALL, rules.single().type)
    }

    @Test
    fun `whatsapp business has its own card`() {
        val rules = listOf(
            Rule(id = "a", type = RuleType.APP, packageName = WhatsApp.BUSINESS, enabled = false),
            personRule("Carlos", pkg = WhatsApp.PERSONAL),
        )
        assertEquals(WhatsApp.BUSINESS, rules[0].appKey)
        assertEquals(listOf(WhatsApp.PERSONAL, WhatsApp.BUSINESS), es.carlospb.modoguardia.domain.WatchSummary.appsOf(rules).map { it.key })
    }
}
