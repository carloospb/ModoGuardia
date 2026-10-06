package es.carlospb.modoguardia.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import es.carlospb.modoguardia.domain.SeenChat
import es.carlospb.modoguardia.domain.SeenChats
import es.carlospb.modoguardia.domain.TextNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Chats de WhatsApp vistos en notificaciones (ver [SeenChats]). Solo en el teléfono. */
class SeenChatsRepository(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("seen_whatsapp_chats")

    /** Última escritura por chat en este proceso: evita tocar el disco con cada mensaje. */
    private val recentlyWritten = ConcurrentHashMap<String, Long>()

    val chats: Flow<List<SeenChat>> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { SeenChats.decode(it[key]) }

    suspend fun record(chat: SeenChat) {
        val id = "${chat.appKey}|${chat.isGroup}|${chat.conversationId ?: TextNormalizer.groupKey(chat.name)}"
        val last = recentlyWritten[id]
        if (last != null && chat.lastSeen - last in 0 until SeenChats.REFRESH_MS) return
        store.edit { prefs ->
            SeenChats.record(SeenChats.decode(prefs[key]), chat)?.let { prefs[key] = SeenChats.encode(it) }
        }
        recentlyWritten[id] = chat.lastSeen
    }

    suspend fun clear() {
        recentlyWritten.clear()
        store.edit { it.remove(key) }
    }
}
