package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.CallMode
import es.carlospb.modoguardia.domain.WatchSummary
import es.carlospb.modoguardia.domain.WhatsApp
import es.carlospb.modoguardia.system.CheckItem
import es.carlospb.modoguardia.system.CheckLevel
import es.carlospb.modoguardia.system.SystemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSummaryTest {
    private val rules = listOf(
        personRule("Carlos"),
        groupRule("Soporte"),
        personRule("Pedro").copy(enabled = false),
        groupRule("Equipo técnico").copy(packageName = TG),
        appRule("com.pagerduty.android", "PagerDuty"),
        keywordRule("URGENTE"),
        callRule("Juan", "600000001"),
        numberRule("912345678", label = "Empresa"),
    )

    @Test
    fun `dashboard counts apps, conversations and call contacts`() {
        val w = WatchSummary.of(rules, wakeOnPhoneCalls = true, callMode = CallMode.SELECTED, wakeOnAppCalls = false)
        // WhatsApp primero; el resto, por nombre guardado o, si no lo hay, por paquete.
        assertEquals(listOf(WhatsApp.KEY, TG, "com.pagerduty.android"), w.apps.map { it.key })
        assertEquals(3, w.appCount)
        assertEquals(3, w.conversationCount)
        assertEquals(2, w.callRules.size)
        assertEquals(1, w.anyAppKeywords.size)
        // 2 de WhatsApp + 1 de Telegram + PagerDuty + palabras + 2 contactos.
        assertEquals(7, w.triggerCount)
        val whatsapp = w.apps.first()
        assertEquals(listOf("Carlos"), whatsapp.people.map { it.name })
        assertEquals(listOf("Soporte"), whatsapp.groups.map { it.name })
    }

    @Test
    fun `call contacts only count with selected calls`() {
        val all = WatchSummary.of(rules, wakeOnPhoneCalls = true, callMode = CallMode.ALL, wakeOnAppCalls = false)
        assertEquals(6, all.triggerCount)
        val off = WatchSummary.of(rules, wakeOnPhoneCalls = false, callMode = CallMode.SELECTED, wakeOnAppCalls = false)
        assertFalse(off.phoneCallsActive)
        assertEquals(5, off.triggerCount)
    }

    @Test
    fun `selected calls without contacts wake nobody`() {
        val w = WatchSummary.of(emptyList(), wakeOnPhoneCalls = true, callMode = CallMode.SELECTED, wakeOnAppCalls = false)
        assertFalse(w.phoneCallsActive)
        assertEquals(0, w.triggerCount)
    }

    @Test
    fun `apps whose rules are all paused are listed but not watched`() {
        val paused = listOf(groupRule("Soporte").copy(packageName = TG, enabled = false))
        assertEquals(1, WatchSummary.appsOf(paused).size)
        assertFalse(WatchSummary.appsOf(paused).single().isActive)
        assertTrue(WatchSummary.of(paused, false, CallMode.ALL, false).apps.isEmpty())
    }

    @Test
    fun `system is never operational with a missing permission or before checking`() {
        assertFalse(SystemStatus.EMPTY.operational)
        val warning = SystemStatus(listOf(CheckItem("battery", "Batería", CheckLevel.WARNING, "Puede limitar")))
        assertFalse(warning.operational)
        val error = SystemStatus(listOf(CheckItem("notification_access", "Acceso", CheckLevel.ERROR, "Falta permiso")))
        assertFalse(error.operational)
        assertTrue(error.hasErrors)
        val ok = SystemStatus(listOf(CheckItem("a", "A", CheckLevel.OK, "ok"), CheckItem("m", "Fabricante", CheckLevel.INFO, "no comprobable")))
        assertTrue(ok.operational)
    }
}
