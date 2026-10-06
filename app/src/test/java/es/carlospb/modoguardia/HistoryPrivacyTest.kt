package es.carlospb.modoguardia

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.carlospb.modoguardia.data.HistoryDatabase
import es.carlospb.modoguardia.data.HistoryEntity
import es.carlospb.modoguardia.data.HistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** El historial no conserva texto de mensajes, tampoco el que guardaron versiones anteriores. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HistoryPrivacyTest {

    @Test
    fun legacyMessageTextIsRemovedAndNothingElseChanges() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HistoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.historyDao()
        // Así guardaban las alarmas la 1.0 y la 1.1: origen y regla, y en otra línea el mensaje.
        dao.insert(HistoryEntity(timestamp = 1, type = "ALARM", title = "Alarma activada", detail = "WhatsApp · GUARDIAS\nURGENTE: la clave es 1234"))
        dao.insert(HistoryEntity(timestamp = 2, type = "MATCH_WHILE_RINGING", title = "Nuevo aviso", detail = "WhatsApp · GUARDIAS\notro mensaje"))
        dao.insert(HistoryEntity(timestamp = 3, type = "WARNING", title = "Aviso", detail = "Primera línea\nSegunda línea"))
        dao.insert(HistoryEntity(timestamp = 4, type = "ALARM", title = "Alarma activada", detail = "Teléfono · Empresa"))

        val repo = HistoryRepository(dao)
        repo.stripLegacyMessageText()
        repo.stripLegacyMessageText() // Idempotente: se ejecuta en cada arranque.

        val byTime = dao.observe(10).first().associateBy { it.timestamp }
        assertEquals("WhatsApp · GUARDIAS", byTime.getValue(1).detail)
        assertFalse(byTime.getValue(1).detail.orEmpty().contains("1234"))
        assertEquals("WhatsApp · GUARDIAS", byTime.getValue(2).detail)
        assertEquals("Primera línea\nSegunda línea", byTime.getValue(3).detail)
        assertEquals("Teléfono · Empresa", byTime.getValue(4).detail)
        db.close()
    }
}
