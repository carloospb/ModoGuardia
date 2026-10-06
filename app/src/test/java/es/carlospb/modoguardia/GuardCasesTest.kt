package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.AlarmKind
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos del documento de requisitos (punto 29) y los de llamadas por contacto o número. */
class GuardCasesTest {

    private class FakeAlarm : AlarmTrigger {
        val triggered = mutableListOf<AlarmRequest>()
        var ringing = false
        var answered = 0
        override fun isRinging() = ringing
        override fun trigger(request: AlarmRequest) {
            triggered += request
            ringing = true
        }
        override fun noteAdditionalMatch(request: AlarmRequest) = Unit
        override fun onPhoneCallAnswered() {
            answered++
        }
    }

    private class FakeHistory : HistoryLog {
        val entries = mutableListOf<Triple<HistoryType, String, String?>>()
        override suspend fun log(type: HistoryType, title: String, detail: String?, time: Long) {
            entries += Triple(type, title, detail)
        }
    }

    private val guardStart = 1_000_000L
    private var now = guardStart + 60_000
    private var snapshot = GuardSnapshot(active = true, startedAt = guardStart, rules = emptyList(), wakeOnPhoneCalls = false, wakeOnAppCalls = false)
    private val alarm = FakeAlarm()
    private val history = FakeHistory()
    private val engine = GuardEngine({ snapshot }, Deduplicator(), alarm, history, clock = { now })

    private fun rules(vararg r: Rule) {
        snapshot = snapshot.copy(rules = r.toList())
    }

    private fun selectedCalls(vararg r: Rule, wakeUnidentified: Boolean = true) {
        snapshot = snapshot.copy(rules = r.toList(), wakeOnPhoneCalls = true, callMode = CallMode.SELECTED, wakeOnUnidentifiedCalls = wakeUnidentified)
    }

    // Caso 1
    @Test
    fun `chat Carlos selected - message from Carlos rings`() = runTest {
        rules(personRule("Carlos"))
        assertEquals(Outcome.ALARM, engine.onNotification(personMessage("Carlos", "¿Estás?", now)))
        assertEquals("Carlos", alarm.triggered.single().conversation)
    }

    // Caso 2
    @Test
    fun `chat Carlos selected - message from Pedro does not ring`() = runTest {
        rules(personRule("Carlos"))
        assertEquals(Outcome.NO_MATCH, engine.onNotification(personMessage("Pedro", "hola", now)))
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `chat Carlos selected - Carlos writing in a group does not ring`() = runTest {
        rules(personRule("Carlos"))
        assertEquals(Outcome.NO_MATCH, engine.onNotification(waGroupMessage("Amigos", "Carlos", "hola", ts = now)))
        assertTrue(alarm.triggered.isEmpty())
    }

    // Caso 3
    @Test
    fun `group Soporte selected - message in Soporte rings`() = runTest {
        rules(groupRule("Soporte"))
        assertEquals(Outcome.ALARM, engine.onNotification(waGroupMessage("Soporte", "Ana", "Caída en producción", ts = now)))
        assertEquals("Soporte", alarm.triggered.single().conversation)
    }

    // Caso 4
    @Test
    fun `group Soporte selected - message in Amigos does not ring`() = runTest {
        rules(groupRule("Soporte"))
        assertEquals(Outcome.NO_MATCH, engine.onNotification(waGroupMessage("Amigos", "Ana", "Cena el viernes", ts = now)))
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `group rule does not match a person chat with the same name`() = runTest {
        rules(groupRule("Soporte"))
        assertEquals(Outcome.NO_MATCH, engine.onNotification(personMessage("Soporte", "hola", now)))
        // Un chat individual no es un «grupo no identificado».
        assertFalse(history.entries.any { it.first == HistoryType.GROUP_UNRESOLVED })
    }

    // Caso 5
    @Test
    fun `contact Carlos selected - Carlos calls - rings`() = runTest {
        selectedCalls(callRule("Carlos", "+34 600 11 22 33"))
        assertEquals(Outcome.ALARM, engine.onNotification(dialerCall("Carlos", now)))
        val r = alarm.triggered.single()
        assertEquals(AlarmKind.PHONE_CALL, r.kind)
        assertEquals("Carlos", r.conversation)
    }

    @Test
    fun `selected number calls while not a contact - dialer shows the number - rings`() = runTest {
        selectedCalls(numberRule("+34 912 34 56 78", label = "Empresa"))
        // Primero la señal RINGING (no dice quién es), luego el aviso del marcador con el número.
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging())
        now += 700
        assertEquals(Outcome.ALARM, engine.onNotification(dialerCall("912 34 56 78", now)))
        // El temporizador de identificación ya no hace nada: la llamada está decidida.
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.NO_MATCH, engine.onCallerTimeout())
        assertEquals(1, alarm.triggered.size)
    }

    // Caso 6
    @Test
    fun `only Carlos selected - another number calls - no alarm`() = runTest {
        selectedCalls(callRule("Carlos", "+34 600 11 22 33"))
        assertEquals(Outcome.NO_MATCH, engine.onNotification(dialerCall("+34 699 99 99 99", now)))
        now += 400
        // La señal RINGING llega después del aviso: ya está decidido, no se espera nada.
        assertEquals(Outcome.NO_MATCH, engine.onPhoneCallRinging())
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.NO_MATCH, engine.onCallerTimeout())
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `only Carlos selected - another contact calls - no alarm`() = runTest {
        selectedCalls(callRule("Carlos", "+34 600 11 22 33"))
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging())
        now += 500
        assertEquals(Outcome.NO_MATCH, engine.onNotification(dialerCall("Pedro", now)))
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.NO_MATCH, engine.onCallerTimeout())
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `contact shown with the alternative name order still matches`() = runTest {
        selectedCalls(callRule("Carlos Pérez", alt = "Pérez, Carlos"))
        assertEquals(Outcome.ALARM, engine.onNotification(dialerCall("Pérez, Carlos", now)))
    }

    @Test
    fun `caller cannot be identified - wakes by default, not when the user chose so`() = runTest {
        selectedCalls(callRule("Carlos", "600112233"))
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging())
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.ALARM, engine.onCallerTimeout())
        assertEquals("Llamada sin identificar", alarm.triggered.single().ruleLabel)

        alarm.ringing = false
        alarm.triggered.clear()
        now += 10 * 60_000
        engine.onPhoneIdle()
        selectedCalls(callRule("Carlos", "600112233"), wakeUnidentified = false)
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging())
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.NO_MATCH, engine.onCallerTimeout())
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `dialer notification without name or number counts as unidentified`() = runTest {
        selectedCalls(callRule("Carlos", "600112233"))
        assertEquals(Outcome.ALARM, engine.onNotification(dialerCall("Número desconocido", now, text = null)))
    }

    @Test
    fun `hanging up before identifying the caller decides at once, never silently`() = runTest {
        selectedCalls(callRule("Carlos", "600112233"))
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging())
        now += 2_000
        // Sonó 2 s y colgaron sin que el marcador dijera quién era: «sin identificar» → despierta.
        assertEquals(Outcome.ALARM, engine.onPhoneIdle())
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.NO_MATCH, engine.onCallerTimeout())
        assertEquals(1, alarm.triggered.size)

        alarm.ringing = false
        alarm.triggered.clear()
        now += 10 * 60_000
        selectedCalls(callRule("Carlos", "600112233"), wakeUnidentified = false)
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging())
        assertEquals(Outcome.NO_MATCH, engine.onPhoneIdle())
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `a known caller that hangs up early is still decided by who it was`() = runTest {
        selectedCalls(callRule("Carlos", "600112233"))
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging())
        assertEquals(Outcome.NO_MATCH, engine.onNotification(dialerCall("Pedro", now)))
        assertEquals(Outcome.NO_MATCH, engine.onPhoneIdle())
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `selected mode without any contact does not wake for calls`() = runTest {
        selectedCalls()
        assertEquals(Outcome.AWAITING_CALLER, engine.onPhoneCallRinging())
        now += GuardEngine.CALLER_TIMEOUT_MS
        assertEquals(Outcome.NO_MATCH, engine.onCallerTimeout())
        assertEquals(Outcome.NO_MATCH, engine.onNotification(dialerCall("Carlos", now)))
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `call rules never match chat notifications`() = runTest {
        selectedCalls(callRule("Carlos", "600112233"))
        assertEquals(Outcome.NO_MATCH, engine.onNotification(personMessage("Carlos", "hola", now)))
    }

    // Caso 7
    @Test
    fun `guard inactive - nothing rings`() = runTest {
        snapshot = snapshot.copy(
            active = false,
            rules = listOf(personRule("Carlos"), groupRule("Soporte"), callRule("Carlos", "600112233")),
            wakeOnPhoneCalls = true,
            wakeOnAppCalls = true,
        )
        assertEquals(Outcome.GUARD_INACTIVE, engine.onNotification(personMessage("Carlos", "hola", now)))
        assertEquals(Outcome.GUARD_INACTIVE, engine.onNotification(waGroupMessage("Soporte", "Ana", "caída", ts = now)))
        assertEquals(Outcome.GUARD_INACTIVE, engine.onNotification(dialerCall("Carlos", now)))
        assertEquals(Outcome.GUARD_INACTIVE, engine.onPhoneCallRinging())
        snapshot = snapshot.copy(callMode = CallMode.SELECTED)
        assertEquals(Outcome.GUARD_INACTIVE, engine.onPhoneCallRinging())
        assertEquals(Outcome.NO_MATCH, engine.onCallerTimeout())
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `rules are scoped to their app`() = runTest {
        rules(personRule("Carlos", pkg = TG))
        assertEquals(Outcome.NO_MATCH, engine.onNotification(personMessage("Carlos", "hola", now)))
        assertEquals(Outcome.ALARM, engine.onNotification(personMessage("Carlos", "hola", now + 1, pkg = TG, appName = "Telegram")))
    }

    @Test
    fun `whatsapp rules without app also cover whatsapp business`() = runTest {
        rules(personRule("Carlos"))
        assertEquals(Outcome.ALARM, engine.onNotification(personMessage("Carlos", "hola", now, pkg = "com.whatsapp.w4b", appName = "WA Business")))
    }

    @Test
    fun `renamed group is still recognised by its android conversation id`() = runTest {
        rules(groupRule("Soporte").copy(conversationId = "120363@g.us"))
        val renamed = waGroupMessage("Soporte 24h", "Ana", "caída", ts = now).copy(shortcutId = "120363@g.us")
        assertEquals(Outcome.ALARM, engine.onNotification(renamed))
    }

    @Test
    fun `history keeps origin and conversation but not the message text`() = runTest {
        rules(groupRule("Soporte"))
        engine.onNotification(waGroupMessage("Soporte", "Ana", "Contraseña del servidor: 1234", ts = now))
        val entry = history.entries.single { it.first == HistoryType.ALARM }
        assertEquals("WhatsApp · Soporte", entry.third)
        assertFalse(entry.third.orEmpty().contains("1234"))
    }

    @Test
    fun `call mode all keeps the original behaviour`() = runTest {
        snapshot = snapshot.copy(wakeOnPhoneCalls = true, callMode = CallMode.ALL, rules = listOf(callRule("Carlos", "600112233")))
        assertEquals(Outcome.ALARM, engine.onPhoneCallRinging())
        engine.onPhoneCallAnswered()
        assertEquals(1, alarm.answered)
    }
}
