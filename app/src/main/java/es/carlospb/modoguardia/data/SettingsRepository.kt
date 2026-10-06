package es.carlospb.modoguardia.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.carlospb.modoguardia.domain.CallMode
import es.carlospb.modoguardia.domain.GuardSnapshot
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.WatchSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class VolumeMode { CURRENT, CUSTOM, MAX }

enum class SoundSource { SIREN, SYSTEM_ALARM }

data class GuardSettings(
    val guardActive: Boolean = false,
    val guardStartedAt: Long = 0L,
    val onboardingDone: Boolean = false,
    val rules: List<Rule> = emptyList(),
    val wakeOnPhoneCalls: Boolean = false,
    val wakeOnAppCalls: Boolean = false,
    val volumeMode: VolumeMode = VolumeMode.MAX,
    /** 0..100, solo con VolumeMode.CUSTOM. */
    val customVolumePercent: Int = 80,
    val vibrate: Boolean = true,
    val soundSource: SoundSource = SoundSource.SIREN,
    val diagnosticsEnabled: Boolean = false,
    /** Qué llamadas telefónicas despiertan: todas o solo los contactos y números elegidos. */
    val callMode: CallMode = CallMode.ALL,
    /** Con [CallMode.SELECTED], si no se puede saber quién llama: despertar (más seguro) o no. */
    val wakeOnUnidentifiedCalls: Boolean = true,
    /** Recordar las conversaciones que mandan avisos para poder elegirlas (solo el nombre). */
    val learnConversations: Boolean = true,
) {
    val enabledRules: List<Rule> get() = rules.filter { it.enabled && it.isValid() }

    /** Resumen de lo vigilado: apps, conversaciones y llamadas. */
    val watch: WatchSummary get() = WatchSummary.of(rules, wakeOnPhoneCalls, callMode, wakeOnAppCalls)

    /** Reglas + llamadas: lo que puede despertar al usuario. 0 = nada le despertará. */
    val activeTriggerCount: Int get() = watch.triggerCount

    fun toSnapshot() = GuardSnapshot(
        active = guardActive,
        startedAt = guardStartedAt,
        rules = rules,
        wakeOnPhoneCalls = wakeOnPhoneCalls,
        wakeOnAppCalls = wakeOnAppCalls,
        callMode = callMode,
        wakeOnUnidentifiedCalls = wakeOnUnidentifiedCalls,
    )
}

val Context.guardDataStore: DataStore<Preferences> by preferencesDataStore(name = "modo_guardia")

class SettingsRepository(private val store: DataStore<Preferences>) {

    private object Keys {
        val ACTIVE = booleanPreferencesKey("guard_active")
        val STARTED_AT = longPreferencesKey("guard_started_at")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val RULES = stringPreferencesKey("rules_json")
        val PHONE_CALLS = booleanPreferencesKey("wake_phone_calls")
        val APP_CALLS = booleanPreferencesKey("wake_app_calls")
        val VOLUME_MODE = stringPreferencesKey("volume_mode")
        val VOLUME_PERCENT = intPreferencesKey("volume_percent")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val SOUND = stringPreferencesKey("sound_source")
        val DIAGNOSTICS = booleanPreferencesKey("diagnostics")
        val CALL_MODE = stringPreferencesKey("call_mode")
        val UNIDENTIFIED_CALLS = booleanPreferencesKey("wake_unidentified_calls")
        val LEARN = booleanPreferencesKey("learn_conversations")
    }

    val settings: Flow<GuardSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)

    suspend fun current(): GuardSettings = settings.first()

    /** Lectura-modificación-escritura atómica de todos los ajustes. */
    suspend fun update(transform: (GuardSettings) -> GuardSettings) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences) = GuardSettings(
        guardActive = p[Keys.ACTIVE] ?: false,
        guardStartedAt = p[Keys.STARTED_AT] ?: 0L,
        onboardingDone = p[Keys.ONBOARDING] ?: false,
        rules = RuleCodec.decode(p[Keys.RULES]),
        wakeOnPhoneCalls = p[Keys.PHONE_CALLS] ?: false,
        wakeOnAppCalls = p[Keys.APP_CALLS] ?: false,
        volumeMode = enumOr(p[Keys.VOLUME_MODE], VolumeMode.MAX),
        customVolumePercent = (p[Keys.VOLUME_PERCENT] ?: 80).coerceIn(0, 100),
        vibrate = p[Keys.VIBRATE] ?: true,
        soundSource = enumOr(p[Keys.SOUND], SoundSource.SIREN),
        diagnosticsEnabled = p[Keys.DIAGNOSTICS] ?: false,
        callMode = enumOr(p[Keys.CALL_MODE], CallMode.ALL),
        wakeOnUnidentifiedCalls = p[Keys.UNIDENTIFIED_CALLS] ?: true,
        learnConversations = p[Keys.LEARN] ?: true,
    )

    private fun write(p: MutablePreferences, s: GuardSettings) {
        p[Keys.ACTIVE] = s.guardActive
        p[Keys.STARTED_AT] = s.guardStartedAt
        p[Keys.ONBOARDING] = s.onboardingDone
        p[Keys.RULES] = RuleCodec.encode(s.rules)
        p[Keys.PHONE_CALLS] = s.wakeOnPhoneCalls
        p[Keys.APP_CALLS] = s.wakeOnAppCalls
        p[Keys.VOLUME_MODE] = s.volumeMode.name
        p[Keys.VOLUME_PERCENT] = s.customVolumePercent.coerceIn(0, 100)
        p[Keys.VIBRATE] = s.vibrate
        p[Keys.SOUND] = s.soundSource.name
        p[Keys.DIAGNOSTICS] = s.diagnosticsEnabled
        p[Keys.CALL_MODE] = s.callMode.name
        p[Keys.UNIDENTIFIED_CALLS] = s.wakeOnUnidentifiedCalls
        p[Keys.LEARN] = s.learnConversations
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
