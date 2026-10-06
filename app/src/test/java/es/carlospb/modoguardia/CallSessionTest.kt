package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.AlarmRequest
import es.carlospb.modoguardia.domain.AlarmTrigger
import es.carlospb.modoguardia.domain.CallMode
import es.carlospb.modoguardia.domain.Deduplicator
import es.carlospb.modoguardia.domain.GuardEngine
import es.carlospb.modoguardia.domain.GuardSnapshot
import es.carlospb.modoguardia.domain.HistoryLog
import es.carlospb.modoguardia.domain.HistoryType
import es.carlospb.modoguardia.domain.Outcome
import es.carlospb.modoguardia.domain.Rule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Llamadas con «solo contactos y números elegidos»: las pruebas de quién llama llegan por separado
 * (señal RINGING, con número solo con «Registro de llamadas», y aviso del marcador) y en cualquier
 * orden. Nunca se descarta una llamada sin poder descartar a todos los elegidos.
 */
class CallSessionTest {

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
        val entries = mutableListOf<HistoryType>()
        override suspend fun log(type: HistoryType, title: String, detail: String?, time: Long) {
            entries += type
        }
    }

    private val guardStart = 1_000_000L
    private var now = guardStart + 60_000
    private var snapshot = GuardSnapshot(
        active = true, startedAt = guardStart, rules = emptyList(),
        wakeOnPhoneCalls = true, wakeOnAppCalls = false, callMode = CallMode.SELECTED,
    )
    private val alarm = FakeAlarm()
    private val history = FakeHistory()
    private val engine = GuardEngine({ snapshot }, Deduplicator(), alarm, history, clock = { now })

    private fun rules(vararg r: Rule, wakeUnidentified: Boolean = true) {
        snapshot = snapshot.copy(rules = r.toList(), wakeOnUnidentifiedCalls = wakeUnidentified)
    }

    @Test
    fun `typed number while the dialer shows a business name - not silently ignored`() = runTest {
        rules(numberRule("912345678", label = "Guardia hospital"))
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging())
        now += 600
        // El marcador identifica la empresa y enseña su nombre, no el número: no se puede descartar.
        assertEquals(Outcome.AWAITING_CALLER, engine.onNotification(dialerCall("Hospital Universitario", now)))
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.ALARM, engine.onCallerTimeout())
        assertEquals("Llamada sin identificar", alarm.triggered.single().ruleLabel)
    }

    @Test
    fun `exact number from the call signal rings at once`() = runTest {
        rules(numberRule("912345678", label = "Guardia hospital"))
        // Con «Registro de llamadas» Android manda la señal dos veces: sin número y con número.
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging(null))
        assertEquals(Outcome.ALARM, engine.onPhoneCallRinging("+34912345678"))
        now += 500
        assertEquals(Outcome.DUPLICATE, engine.onNotification(dialerCall("Hospital Universitario", now)))
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.NO_MATCH, engine.onCallerTimeout())
        assertEquals(1, alarm.triggered.size)
        // Se muestra la etiqueta que puso el usuario, no las cifras.
        assertEquals("Guardia hospital", alarm.triggered.single().conversation)
    }

    @Test
    fun `exact number that is not chosen rules out typed numbers even with a business name`() = runTest {
        rules(numberRule("912345678", label = "Guardia hospital"))
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging("611223344"))
        now += 500
        assertEquals(Outcome.NO_MATCH, engine.onNotification(dialerCall("Tienda online", now)))
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.NO_MATCH, engine.onCallerTimeout())
        assertTrue(alarm.triggered.isEmpty())
        assertTrue(HistoryType.IGNORED in history.entries)
    }

    @Test
    fun `contact calling from a number that was not picked is still recognised by name`() = runTest {
        rules(callRule("Carlos", "600112233"))
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging("699000000"))
        now += 400
        assertEquals(Outcome.ALARM, engine.onNotification(dialerCall("Carlos", now)))
    }

    @Test
    fun `only the exact number is known and it is not chosen - decided at the timeout`() = runTest {
        rules(callRule("Carlos", "600112233"))
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging("699000000"))
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.NO_MATCH, engine.onCallerTimeout())
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `answering before the timeout means no fallback alarm`() = runTest {
        rules(callRule("Carlos", "600112233"))
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging())
        now += 2_000
        engine.onPhoneCallAnswered()
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.NO_MATCH, engine.onCallerTimeout())
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `without phone permission the dialer notification decides alone`() = runTest {
        rules(numberRule("912345678"))
        // Sin READ_PHONE_STATE no hay señal RINGING: el aviso es la única prueba y se decide ya.
        assertEquals(Outcome.ALARM, engine.onNotification(dialerCall("Hospital Universitario", now)))
        assertEquals("Llamada sin identificar", alarm.triggered.single().ruleLabel)
    }

    @Test
    fun `a second call after hanging up is evaluated on its own`() = runTest {
        rules(callRule("Carlos", "600112233"))
        assertEquals(Outcome.NO_MATCH, engine.onNotification(dialerCall("Pedro", now)))
        engine.onPhoneIdle()
        now += 20_000
        assertEquals(Outcome.ALARM, engine.onNotification(dialerCall("Carlos", now)))
    }
}
