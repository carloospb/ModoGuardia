package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.Deduplicator
import es.carlospb.modoguardia.domain.MessageLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeduplicatorTest {
    private val min = 60_000L

    @Test
    fun `whatsapp re-posting the same message is a duplicate`() {
        val d = Deduplicator()
        val e = waGroupMessage("GUARDIAS SOPORTE", "Luis", "URGENTE", ts = 1000)
        assertFalse(d.isDuplicate(e, now = 1000))
        // WhatsApp vuelve a publicar la misma notificación 30 min después (otro postTime).
        assertTrue(d.isDuplicate(e.copy(postTime = 1000 + 30 * min), now = 1000 + 30 * min))
    }

    @Test
    fun `a new message in the same chat is not a duplicate`() {
        val d = Deduplicator()
        val first = waGroupMessage("GUARDIAS SOPORTE", "Luis", "URGENTE: servidor caído", ts = 1000)
        val second = first.copy(
            messages = first.messages + MessageLine("Ana", "Sigue caído", 1000 + 5 * min),
            postTime = 1000 + 5 * min,
        )
        assertFalse(d.isDuplicate(first, now = 1000))
        assertFalse(d.isDuplicate(second, now = 1000 + 5 * min))
    }

    @Test
    fun `same content re-posted with a new time within two minutes is a duplicate`() {
        val d = Deduplicator()
        val e = appNotification("com.alerts", "Alertas", "Servidor", "CAÍDO", postTime = 0).copy(whenTime = 0)
        assertFalse(d.isDuplicate(e, now = 0))
        assertTrue(d.isDuplicate(e.copy(whenTime = 30_000, postTime = 30_000), now = 30_000))
    }

    @Test
    fun `identical alert repeated later with a new time is not a duplicate`() {
        val d = Deduplicator()
        val e = appNotification("com.alerts", "Alertas", "Servidor", "CAÍDO", postTime = 0).copy(whenTime = 0)
        assertFalse(d.isDuplicate(e, now = 0))
        val later = 20 * min
        assertFalse(d.isDuplicate(e.copy(whenTime = later, postTime = later), now = later))
    }

    @Test
    fun `seeded notifications are treated as already seen`() {
        val d = Deduplicator()
        val old = waGroupMessage("GUARDIAS SOPORTE", "Luis", "mensaje de ayer", ts = 1000)
        d.seed(listOf(old), now = 5000)
        assertTrue(d.isDuplicate(old.copy(postTime = 9000), now = 9000))
    }

    @Test
    fun `call key window`() {
        val d = Deduplicator()
        assertFalse(d.isDuplicateKey("phone-call", 90_000, now = 0))
        assertTrue(d.isDuplicateKey("phone-call", 90_000, now = 10_000))
        assertFalse(d.isDuplicateKey("phone-call", 90_000, now = 200_000))
    }
}
