package es.carlospb.modoguardia.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.carlospb.modoguardia.alarm.ActiveAlarm
import es.carlospb.modoguardia.alarm.AlarmState
import es.carlospb.modoguardia.container
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.data.HistoryEntity
import es.carlospb.modoguardia.data.SoundSource
import es.carlospb.modoguardia.data.VolumeMode
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.WhatsApp
import es.carlospb.modoguardia.service.DiagnosticEntry
import es.carlospb.modoguardia.service.GuardNotificationListener
import es.carlospb.modoguardia.service.GuardService
import es.carlospb.modoguardia.system.SystemStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Guardia, estado del sistema, ajustes de alarma, prueba y diagnóstico. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    /** null mientras DataStore carga: evita enseñar el asistente un instante a quien ya lo hizo. */
    val settings: StateFlow<GuardSettings?> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _status = MutableStateFlow(SystemStatus.EMPTY)
    val status: StateFlow<SystemStatus> = _status.asStateFlow()

    val alarm: StateFlow<ActiveAlarm?> = AlarmState.active
    val countdown: StateFlow<Int?> = AlarmState.countdown
    val diagnostics: StateFlow<List<DiagnosticEntry>> = c.diagnostics.recent
    val lastSeen: StateFlow<Map<String, Long>> = c.diagnostics.lastSeen

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        // El estado cambia fuera de la app (Ajustes, No molestar, volumen): se vuelve a leer a menudo.
        viewModelScope.launch {
            while (isActive) {
                refreshStatus()
                delay(3_000)
            }
        }
        viewModelScope.launch {
            combine(c.settings.settings, GuardService.running, GuardNotificationListener.connected) { _, _, _ -> }
                .collect { refreshStatus() }
        }
    }

    /** Consultas al sistema (varias llamadas binder): fuera del hilo principal. */
    fun refreshStatus() {
        val s = settings.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _status.value = c.statusChecker.check(s, GuardService.running.value)
        }
    }

    fun startGuard() = viewModelScope.launch { c.guardController.start() }

    fun stopGuard() = viewModelScope.launch { c.guardController.stop() }

    fun restartGuardService() = viewModelScope.launch {
        if (!c.guardController.ensureServiceRunning()) _message.value = "Android no permitió iniciar el servicio de guardia."
    }

    fun testAlarm(delaySeconds: Int) {
        if (!c.alarmController.startTest(delaySeconds)) _message.value = "No se pudo iniciar la prueba de alarma."
    }

    fun cancelTest() = c.alarmController.stop("Prueba cancelada")

    fun stopAlarm() = c.alarmController.stop("Detenida por el usuario")

    fun consumeMessage() {
        _message.value = null
    }

    fun setVolumeMode(mode: VolumeMode) = edit { it.copy(volumeMode = mode) }
    fun setCustomVolume(percent: Int) = edit { it.copy(customVolumePercent = percent.coerceIn(1, 100)) }
    fun setVibrate(on: Boolean) = edit { it.copy(vibrate = on) }
    fun setSound(source: SoundSource) = edit { it.copy(soundSource = source) }
    fun setWakeOnPhoneCalls(on: Boolean) = edit { it.copy(wakeOnPhoneCalls = on) }
    fun setWakeOnAppCalls(on: Boolean) = edit { it.copy(wakeOnAppCalls = on) }
    fun setDiagnostics(on: Boolean) = edit { it.copy(diagnosticsEnabled = on) }
    fun setOnboardingDone(done: Boolean) = edit { it.copy(onboardingDone = done) }

    fun clearDiagnostics() = c.diagnostics.clear()

    private fun edit(block: (GuardSettings) -> GuardSettings) {
        viewModelScope.launch { c.settings.update(block) }
    }
}

data class InstalledApp(val packageName: String, val label: String)

class RulesViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    val rules: StateFlow<List<Rule>> = c.settings.settings
        .map { it.rules }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)
    val apps: StateFlow<List<InstalledApp>?> = _apps.asStateFlow()

    fun loadApps() {
        if (_apps.value != null) return
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { queryLaunchableApps() }
        }
    }

    /** WhatsApp / WhatsApp Business instalados (visibles por <queries> del lanzador). */
    fun installedWhatsApp(): List<InstalledApp> =
        (_apps.value ?: queryLaunchableApps()).filter { WhatsApp.isWhatsApp(it.packageName) }

    private fun queryLaunchableApps(): List<InstalledApp> {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val own = getApplication<Application>().packageName
        @Suppress("DEPRECATION") // La variante con ResolveInfoFlags es solo Android 13+.
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != own }
            .distinctBy { it.first }
            .map { InstalledApp(it.first, it.second) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun save(rule: Rule) = viewModelScope.launch {
        c.settings.update { s ->
            val exists = s.rules.any { it.id == rule.id }
            s.copy(rules = if (exists) s.rules.map { if (it.id == rule.id) rule else it } else s.rules + rule)
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        c.settings.update { s -> s.copy(rules = s.rules.filterNot { it.id == id }) }
    }

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        c.settings.update { s -> s.copy(rules = s.rules.map { if (it.id == id) it.copy(enabled = enabled) else it }) }
    }
}

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    val entries: StateFlow<List<HistoryEntity>> = c.history.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() = viewModelScope.launch { c.history.clear() }
}
