package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.AlarmKind
import es.carlospb.modoguardia.domain.AlarmRequest
import es.carlospb.modoguardia.domain.AlarmTrigger
import es.carlospb.modoguardia.domain.Deduplicator
import es.carlospb.modoguardia.domain.GuardEngine
import es.carlospb.modoguardia.domain.GuardSnapshot
import es.carlospb.modoguardia.domain.HistoryLog
import es.carlospb.modoguardia.domain.HistoryType
import es.carlospb.modoguardia.domain.MessageLine
import es.carlospb.modoguardia.domain.Outcome
import es.carlospb.modoguardia.domain.Rule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardEngineTest {

    private class FakeAlarm : AlarmTrigger {
        val triggered = mutableListOf<AlarmRequest>()
        var ringing = false
        var extra = 0
        var answeredCalls = 0
        override fun isRinging() = ringing
        override fun trigger(request: AlarmRequest) {
            triggered += request
            ringing = true
        }
        override fun noteAdditionalMatch(request: AlarmRequest) {
            extra++
        }
        override fun onPhoneCallAnswered() {
            answeredCalls++
        }
    }

    private class FakeHistory : HistoryLog {
        val entries = mutableListOf<Pair<HistoryType, String>>()
        override suspend fun log(type: HistoryType, title: String, detail: String?, time: Long) {
            entries += type to title
        }
    }

    private val guardStart = 1_000_000L // 23:00
    private var now = guardStart
    private var snapshot = GuardSnapshot(
        active = true,
        startedAt = guardStart,
        rules = listOf(groupRule("GUARDIAS SOPORTE")),
        wakeOnPhoneCalls = false,
        wakeOnAppCalls = false,
    )
    private val alarm = FakeAlarm()
    private val history = FakeHistory()
    private val engine = GuardEngine({ snapshot }, Deduplicator(), alarm, history, clock = { now })

    /** Caso real principal: 03:00, guardia activa, mensaje en GUARDIAS SOPORTE. */
    @Test
    fun `main scenario - message in watched group triggers the alarm`() = runTest {
        now = guardStart + 4 * 3_600_000
        val e = waGroupMessage("GUARDIAS SOPORTE", "Luis", "URGENTE: servidor principal caído.", ts = now)
        assertEquals(Outcome.ALARM, engine.onNotification(e))
        val r = alarm.triggered.single()
        assertEquals("WhatsApp", r.sourceApp)
        assertEquals("GUARDIAS SOPORTE", r.ruleLabel)
        assertEquals("Luis: URGENTE: servidor principal caído.", r.message)
        assertTrue(history.entries.any { it.first == HistoryType.ALARM })
    }

    @Test
    fun `message from another group does not trigger`() = runTest {
        now = guardStart + 1000
        val e = waGroupMessage("Familia", "Mamá", "URGENTE", ts = now)
        assertEquals(Outcome.NO_MATCH, engine.onNotification(e))
        assertTrue(alarm.triggered.isEmpty())
        assertEquals(HistoryType.IGNORED, history.entries.single().first)
    }

    @Test
    fun `nothing triggers while guard is inactive`() = runTest {
        snapshot = snapshot.copy(active = false)
        now = guardStart + 1000
        val e = waGroupMessage("GUARDIAS SOPORTE", "Luis", "URGENTE", ts = now)
        assertEquals(Outcome.GUARD_INACTIVE, engine.onNotification(e))
        assertTrue(alarm.triggered.isEmpty())
        assertTrue(history.entries.isEmpty())
    }

    @Test
    fun `activate then deactivate`() = runTest {
        now = guardStart + 1000
        assertEquals(Outcome.ALARM, engine.onNotification(waGroupMessage("GUARDIAS SOPORTE", "Luis", "uno", ts = now)))
        alarm.ringing = false // el usuario la detiene
        snapshot = snapshot.copy(active = false) // finaliza la guardia
        now += 60_000
        assertEquals(Outcome.GUARD_INACTIVE, engine.onNotification(waGroupMessage("GUARDIAS SOPORTE", "Luis", "dos", ts = now)))
        assertEquals(1, alarm.triggered.size)
    }

    @Test
    fun `notification posted before guard start is ignored`() = runTest {
        val e = waGroupMessage("GUARDIAS SOPORTE", "Luis", "URGENTE", ts = guardStart - 5000)
        assertEquals(Outcome.PREEXISTING, engine.onNotification(e))
    }

    @Test
    fun `repost of a chat whose messages are all older than the guard is ignored`() = runTest {
        now = guardStart + 3_600_000
        val e = waGroupMessage("GUARDIAS SOPORTE", "Luis", "mensaje de antes", ts = guardStart - 60_000, postTime = now)
        assertEquals(Outcome.PREEXISTING, engine.onNotification(e))
    }

    @Test
    fun `old keyword in chat history does not trigger on a new harmless message`() = runTest {
        snapshot = snapshot.copy(rules = listOf(keywordRule("URGENTE", pkg = WA)))
        now = guardStart + 3_600_000
        val e = waGroupMessage("Otro", "Ana", "buenas noches", ts = now).let {
            it.copy(messages = listOf(MessageLine("Luis", "URGENTE (de ayer)", guardStart - 600_000)) + it.messages, text = "buenas noches")
        }
        assertEquals(Outcome.NO_MATCH, engine.onNotification(e))
    }

    @Test
    fun `whatsapp updating the same notification does not ring twice`() = runTest {
        now = guardStart + 1000
        val e = waGroupMessage("GUARDIAS SOPORTE", "Luis", "URGENTE", ts = now)
        assertEquals(Outcome.ALARM, engine.onNotification(e))
        alarm.ringing = false // detenida
        now += 10 * 60_000
        assertEquals(Outcome.DUPLICATE, engine.onNotification(e.copy(postTime = now)))
        assertEquals(1, alarm.triggered.size)
    }

    @Test
    fun `new match while ringing does not restart the alarm`() = runTest {
        now = guardStart + 1000
        engine.onNotification(waGroupMessage("GUARDIAS SOPORTE", "Luis", "uno", ts = now))
        now += 5000
        assertEquals(Outcome.ALREADY_RINGING, engine.onNotification(waGroupMessage("GUARDIAS SOPORTE", "Ana", "dos", ts = now)))
        assertEquals(1, alarm.triggered.size)
        assertEquals(1, alarm.extra)
    }

    @Test
    fun `unresolved group is logged, not invented`() = runTest {
        now = guardStart + 1000
        val e = appNotification(WA, "WhatsApp", null, "3 mensajes nuevos", postTime = now)
        assertEquals(Outcome.NO_MATCH, engine.onNotification(e))
        assertEquals(HistoryType.GROUP_UNRESOLVED, history.entries.single().first)
    }

    /** Caso real de llamada: 04:15, "Cualquier llamada entrante → alarma". */
    @Test
    fun `incoming phone call triggers when enabled, once`() = runTest {
        snapshot = snapshot.copy(wakeOnPhoneCalls = true)
        now = guardStart + 5 * 3_600_000
        assertEquals(Outcome.ALARM, engine.onPhoneCallRinging())
        assertEquals(AlarmKind.PHONE_CALL, alarm.triggered.single().kind)
        alarm.ringing = false
        // La misma llamada vista por la notificación del marcador: no repite.
        val dialer = appNotification("com.google.android.dialer", "Teléfono", "Jefe de guardia", "Llamada entrante", postTime = now + 500)
            .copy(isIncomingCall = true, isPhoneCall = true, category = "call")
        now += 500
        assertEquals(Outcome.DUPLICATE, engine.onNotification(dialer))
        engine.onPhoneCallAnswered()
        assertEquals(1, alarm.answeredCalls)
    }

    @Test
    fun `phone call does nothing when disabled`() = runTest {
        now = guardStart + 1000
        assertEquals(Outcome.NO_MATCH, engine.onPhoneCallRinging())
        assertTrue(alarm.triggered.isEmpty())
    }

    @Test
    fun `incoming app call triggers only with app calls enabled`() = runTest {
        now = guardStart + 1000
        val call = appNotification(WA, "WhatsApp", "Luis", "Llamada de voz entrante", postTime = now)
            .copy(isIncomingCall = true, category = "call")
        snapshot = snapshot.copy(rules = emptyList<Rule>())
        assertEquals(Outcome.NO_MATCH, engine.onNotification(call))
        snapshot = snapshot.copy(wakeOnAppCalls = true)
        assertEquals(Outcome.ALARM, engine.onNotification(call))
        assertEquals(AlarmKind.APP_CALL, alarm.triggered.single().kind)
    }

    @Test
    fun `ignored notifications are logged at most once per app per interval`() = runTest {
        now = guardStart + 1000
        repeat(5) {
            now += 1000
            engine.onNotification(appNotification("com.instagram.android", "Instagram", "x", "like $it", postTime = now))
        }
        assertEquals(1, history.entries.count { it.first == HistoryType.IGNORED })
    }
}
