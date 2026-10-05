package es.carlospb.modoguardia.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.carlospb.modoguardia.BuildConfig
import es.carlospb.modoguardia.alarm.ActiveAlarm
import es.carlospb.modoguardia.alarm.AlarmState
import es.carlospb.modoguardia.container
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.data.HistoryEntity
import es.carlospb.modoguardia.data.SoundSource
import es.carlospb.modoguardia.data.VolumeMode
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.SeenChat
import es.carlospb.modoguardia.domain.TextNormalizer
import es.carlospb.modoguardia.domain.WhatsApp
import es.carlospb.modoguardia.service.DiagnosticEntry
import es.carlospb.modoguardia.service.GuardNotificationListener
import es.carlospb.modoguardia.service.GuardService
import es.carlospb.modoguardia.system.SystemStatus
import es.carlospb.modoguardia.update.PublishedVersion
import es.carlospb.modoguardia.update.UpdateCheck
import es.carlospb.modoguardia.update.Updater
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
import java.io.File
import java.util.UUID

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

    /** Chats de WhatsApp vistos en notificaciones, el más reciente primero. */
    val seenChats: StateFlow<List<SeenChat>> = c.seenChats.chats
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun clearSeenChats() = viewModelScope.launch { c.seenChats.clear() }

    /** Una regla por chat elegido; los que ya tienen regla del mismo tipo y nombre no se duplican. */
    fun addChatRules(chats: List<SeenChat>) = viewModelScope.launch {
        c.settings.update { s ->
            val fresh = chats.filterNot { chat -> s.rules.any { it.watches(chat) } }
                .map { Rule(id = UUID.randomUUID().toString(), type = it.ruleType, groupName = it.name) }
            s.copy(rules = s.rules + fresh)
        }
    }

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

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val versionName: String) : UpdateUi
    data class Failed(val reason: String, val version: PublishedVersion? = null) : UpdateUi
    data class Available(val version: PublishedVersion) : UpdateUi
    data class Downloading(val version: PublishedVersion, val percent: Int) : UpdateUi
    /** Descargada; falta que el usuario permita instalar apps desconocidas o confirme en el instalador. */
    data class Downloaded(val version: PublishedVersion, val apk: File) : UpdateUi
}

/**
 * Actualizador automático: comprueba carlospb.es cada vez que se abre la app y, si hay versión
 * nueva, la ofrece. Descarga e instalación solo si el usuario acepta (ver [Updater]).
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val state: StateFlow<UpdateUi> = _state.asStateFlow()

    /** Diálogo visible. «Ahora no» lo cierra hasta la próxima vez que se abra la app. */
    private val _prompt = MutableStateFlow(false)
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    /** Se mandó al usuario al ajuste de instalar apps: al volver se instala sin pedir otro toque. */
    private var awaitingPermission = false

    init {
        check(manual = false)
    }

    fun check(manual: Boolean) {
        val s = _state.value
        if (s is UpdateUi.Checking || s is UpdateUi.Downloading) return
        _state.value = UpdateUi.Checking
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { Updater.check(BuildConfig.VERSION_CODE) }
            _state.value = when (result) {
                is UpdateCheck.Available -> UpdateUi.Available(result.version)
                is UpdateCheck.UpToDate -> UpdateUi.UpToDate(result.versionName)
                // Sin red al abrir la app no se avisa de nada; a mano, el fallo sale en Ajustes.
                is UpdateCheck.Failed -> if (manual) UpdateUi.Failed("No se pudo comprobar: ${result.reason}") else UpdateUi.Idle
            }
            if (result is UpdateCheck.Available) _prompt.value = true
        }
    }

    fun dismiss() {
        _prompt.value = false
    }

    fun downloadAndInstall() {
        val version = when (val s = _state.value) {
            is UpdateUi.Available -> s.version
            is UpdateUi.Failed -> s.version ?: return
            is UpdateUi.Downloaded -> return install(s)
            else -> return
        }
        _prompt.value = true
        _state.value = UpdateUi.Downloading(version, 0)
        viewModelScope.launch {
            val apk = withContext(Dispatchers.IO) {
                Updater.download(getApplication(), version) { p -> _state.value = UpdateUi.Downloading(version, p) }
            }
            if (apk == null) {
                _state.value = UpdateUi.Failed("No se pudo descargar la actualización. Revisa la conexión y reintenta.", version)
            } else {
                install(UpdateUi.Downloaded(version, apk).also { _state.value = it })
            }
        }
    }

    private fun install(d: UpdateUi.Downloaded) {
        val app = getApplication<Application>()
        if (Updater.canInstall(app)) {
            awaitingPermission = false
            if (!Updater.install(app, d.apk)) _state.value = UpdateUi.Failed("Android no dejó abrir el instalador.", d.version)
        } else {
            awaitingPermission = Updater.openInstallPermission(app)
            if (!awaitingPermission) _state.value = UpdateUi.Failed("No se pudo abrir el ajuste «Instalar apps desconocidas».", d.version)
        }
    }

    /** Al volver de Ajustes con el permiso concedido, se sigue solo. */
    fun onResume() {
        val s = _state.value
        if (awaitingPermission && s is UpdateUi.Downloaded && Updater.canInstall(getApplication())) install(s)
    }
}

/** Esta regla ya vigila ese chat (mismo tipo y mismo nombre normalizado). */
fun Rule.watches(chat: SeenChat): Boolean =
    type == chat.ruleType && TextNormalizer.groupKey(groupName.orEmpty()) == TextNormalizer.groupKey(chat.name)
