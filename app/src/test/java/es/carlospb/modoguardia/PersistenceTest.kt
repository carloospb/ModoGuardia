package es.carlospb.modoguardia

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import es.carlospb.modoguardia.data.RuleCodec
import es.carlospb.modoguardia.data.SeenChatsRepository
import es.carlospb.modoguardia.data.SettingsRepository
import es.carlospb.modoguardia.data.VolumeMode
import es.carlospb.modoguardia.domain.CallMode
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.domain.SeenChat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Lo guardado se lee igual, también lo que guardaron versiones anteriores. */
class PersistenceTest {
    @get:JUnitRule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "$name.preferences_pb") })

    @Test
    fun `rules keep every field through the codec`() {
        val rules = listOf(
            Rule(id = "1", type = RuleType.PERSON, packageName = "org.telegram.messenger", appLabel = "Telegram", name = "Laura", conversationId = "tg-1"),
            Rule(id = "2", type = RuleType.GROUP, name = "Soporte"),
            Rule(id = "3", type = RuleType.CALL, name = "Carlos", aliases = listOf("Pérez, Carlos"), numbers = listOf("+34 600 11 22 33")),
            Rule(id = "4", type = RuleType.KEYWORDS, keywords = listOf("URGENTE", "CAÍDA"), matchAll = true, ignoreCase = false, enabled = false),
            Rule(id = "5", type = RuleType.WHATSAPP_ANY),
            Rule(id = "6", type = RuleType.APP, packageName = "com.pagerduty.android", appLabel = "PagerDuty"),
        )
        assertEquals(rules, RuleCodec.decode(RuleCodec.encode(rules)))
    }

    @Test
    fun `rules saved by version 1_1 are read as group and person rules for whatsapp`() {
        val legacy = """[
            {"id":"a","type":"WHATSAPP_GROUP","enabled":true,"packageName":null,"appLabel":null,"groupName":"GUARDIAS SOPORTE","keywords":[],"matchAll":false,"ignoreCase":true},
            {"id":"b","type":"WHATSAPP_CHAT","enabled":false,"packageName":null,"appLabel":null,"groupName":"Ana","keywords":[],"matchAll":false,"ignoreCase":true},
            {"id":"c","type":"KEYWORDS","enabled":true,"packageName":null,"appLabel":null,"groupName":null,"keywords":["URGENTE"],"matchAll":false,"ignoreCase":true},
            {"id":"d","type":"FUTURE_TYPE","enabled":true}
        ]"""
        val rules = RuleCodec.decode(legacy)
        assertEquals(3, rules.size)
        assertEquals(Rule(id = "a", type = RuleType.GROUP, name = "GUARDIAS SOPORTE"), rules[0])
        assertEquals(Rule(id = "b", type = RuleType.PERSON, enabled = false, name = "Ana"), rules[1])
        assertTrue(rules[0].appliesTo("com.whatsapp") && rules[0].appliesTo("com.whatsapp.w4b"))
        assertEquals(listOf("URGENTE"), rules[2].keywords)
    }

    @Test
    fun `corrupt rules are ignored instead of crashing`() {
        assertTrue(RuleCodec.decode("{no es json").isEmpty())
        assertTrue(RuleCodec.decode(null).isEmpty())
    }

    @Test
    fun `settings survive a round trip`() = runBlocking {
        val repo = SettingsRepository(store("settings"))
        repo.update {
            it.copy(
                rules = listOf(personRule("Carlos"), numberRule("912345678", label = "Empresa"), callRule("Ana", "611", alt = "López, Ana")),
                wakeOnPhoneCalls = true,
                callMode = CallMode.SELECTED,
                wakeOnUnidentifiedCalls = false,
                learnConversations = false,
                volumeMode = VolumeMode.CUSTOM,
                customVolumePercent = 70,
            )
        }
        val s = repo.current()
        assertEquals(CallMode.SELECTED, s.callMode)
        assertFalse(s.wakeOnUnidentifiedCalls)
        assertFalse(s.learnConversations)
        assertEquals(70, s.customVolumePercent)
        assertEquals(listOf(personRule("Carlos"), numberRule("912345678", label = "Empresa"), callRule("Ana", "611", alt = "López, Ana")), s.rules)
    }

    @Test
    fun `new settings default to the previous behaviour`() = runBlocking {
        val s = SettingsRepository(store("defaults")).current()
        assertEquals(CallMode.ALL, s.callMode)
        assertTrue(s.wakeOnUnidentifiedCalls)
        assertTrue(s.learnConversations)
    }

    @Test
    fun `detected conversations are stored, migrated from 1_1 and cleared`() = runBlocking {
        val ds = store("seen")
        // Formato de la 1.1: solo WhatsApp, sin app ni id.
        ds.edit { it[stringPreferencesKey("seen_whatsapp_chats")] = "100\tG\tGUARDIAS\n200\tC\tAna" }
        val repo = SeenChatsRepository(ds)
        assertEquals(listOf(SeenChat("GUARDIAS", true, 100), SeenChat("Ana", false, 200)), repo.chats.first())

        repo.record(SeenChat("Laura", false, 5_000_000, packageName = TG, conversationId = "tg-9"))
        val chats = repo.chats.first()
        assertEquals(SeenChat("Laura", false, 5_000_000, TG, "tg-9"), chats.first())
        assertEquals(3, chats.size)

        repo.clear()
        assertTrue(repo.chats.first().isEmpty())
    }

    @Test
    fun `chats with the same name in different apps are all recorded`() = runBlocking {
        val repo = SeenChatsRepository(store("seen-apps"))
        repo.record(SeenChat("Familia", true, 1_000, packageName = WA))
        repo.record(SeenChat("Familia", true, 2_000, packageName = TG))
        repo.record(SeenChat("Familia", false, 3_000, packageName = TG))
        val chats = repo.chats.first()
        assertEquals(3, chats.size)
        assertEquals(setOf(WA to true, TG to true, TG to false), chats.map { it.packageName to it.isGroup }.toSet())
    }
}
