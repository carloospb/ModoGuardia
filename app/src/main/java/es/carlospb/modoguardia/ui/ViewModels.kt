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
import es.carlospb.modoguardia.domain.CallMode
import es.carlospb.modoguardia.domain.CaptureArrival
import es.carlospb.modoguardia.domain.CaptureOption
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleEdits
import es.carlospb.modoguardia.domain.SeenChat
import es.carlospb.modoguardia.service.CaptureSession
import es.carlospb.modoguardia.service.DiagnosticEntry
import es.carlospb.modoguardia.service.GuardNotificationListener
import es.carlospb.modoguardia.service.GuardService
import es.carlospb.modoguardia.system.SystemStatus
import es.carlospb.modoguardia.update.PublishedVersion
import es.carlospb.modoguardia.update.UpdateCheck
import es.carlospb.modoguardia.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
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

private fun newId() = UUID.randomUUID().toString()

/** Guardia, estado del sistema, ajustes de alarma y llamadas, prueba y diagnóstico. */
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
        // Cambios que avisan solos (ajustes, servicio, detector): se recalcula al momento.
        viewModelScope.launch {
            combine(c.settings.settings, GuardService.running, GuardNotificationListener.connected) { _, _, _ -> }
                .collect { refreshStatus() }
        }
    }

    /**
     * Lo que cambia fuera de la app sin avisar (permisos en Ajustes, No molestar, volumen) se vuelve a
     * leer cada 3 s, pero SOLO mientras la app está en pantalla: la interfaz llama a esto con
     * repeatOnLifecycle(STARTED), así en segundo plano no gasta batería.
     */
    suspend fun pollStatus() {
        while (currentCoroutineContext().isActive) {
            refreshStatusNow()
            delay(3_000)
        }
    }

    /** Consultas al sistema (varias llamadas binder): fuera del hilo principal. */
    fun refreshStatus() {
        viewModelScope.launch { refreshStatusNow() }
    }

    private suspend fun refreshStatusNow() {
        val s = settings.value ?: return
        _status.value = withContext(Dispatchers.Default) { c.statusChecker.check(s, GuardService.running.value) }
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

    fun showMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun setVolumeMode(mode: VolumeMode) = edit { it.copy(volumeMode = mode) }
    fun setCustomVolume(percent: Int) = edit { it.copy(customVolumePercent = percent.coerceIn(1, 100)) }
    fun setVibrate(on: Boolean) = edit { it.copy(vibrate = on) }
    fun setSound(source: SoundSource) = edit { it.copy(soundSource = source) }
    fun setWakeOnPhoneCalls(on: Boolean) = edit { it.copy(wakeOnPhoneCalls = on) }
    fun setWakeOnAppCalls(on: Boolean) = edit { it.copy(wakeOnAppCalls = on) }
    fun setCallMode(mode: CallMode) = edit { it.copy(callMode = mode) }
    fun setWakeOnUnidentifiedCalls(on: Boolean) = edit { it.copy(wakeOnUnidentifiedCalls = on) }
    fun setLearnConversations(on: Boolean) = edit { it.copy(learnConversations = on) }
    fun setDiagnostics(on: Boolean) = edit { it.copy(diagnosticsEnabled = on) }
    fun setOnboardingDone(done: Boolean) = edit { it.copy(onboardingDone = done) }

    fun clearDiagnostics() = c.diagnostics.clear()

    fun clearHistory() = viewModelScope.launch {
        c.history.clear()
        _message.value = "Historial borrado"
    }

    private fun edit(block: (GuardSettings) -> GuardSettings) {
        viewModelScope.launch { c.settings.update(block) }
    }
}

data class InstalledApp(val packageName: String, val label: String)

/** Lo que despierta: apps, conversaciones, palabras clave y llamadas. */
class RulesViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    val rules: StateFlow<List<Rule>> = c.settings.settings
        .map { it.rules }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** «Conversaciones detectadas», la más reciente primero. */
    val seenChats: StateFlow<List<SeenChat>> = c.seenChats.chats
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)
    val apps: StateFlow<List<InstalledApp>?> = _apps.asStateFlow()

    private fun label(pkg: String): String? = AppInfo.label(getApplication(), pkg, fallback = null).takeIf { it != pkg }

    private fun update(block: (List<Rule>) -> List<Rule>) = viewModelScope.launch {
        c.settings.update { s -> s.copy(rules = block(s.rules)) }
    }

    fun clearSeenChats() = viewModelScope.launch { c.seenChats.clear() }

    fun loadApps() {
        if (_apps.value != null) return
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { queryLaunchableApps() }
        }
    }

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

    /** Añadir una app: empieza vigilando todos sus avisos (seguro); luego se puede limitar a chats. */
    fun addApp(app: InstalledApp): String {
        val key = app.packageName
        update { RuleEdits.setAllNotifications(it, key, on = true, label = app.label, newId = ::newId) }
        return key
    }

    fun setAllNotifications(appKey: String, on: Boolean) =
        update { RuleEdits.setAllNotifications(it, appKey, on, label(appKey), ::newId) }

    fun removeApp(appKey: String) = update { RuleEdits.removeApp(it, appKey) }

    fun addConversations(chats: List<SeenChat>) =
        update { RuleEdits.addConversations(it, chats, ::label, ::newId) }

    fun addManualConversation(appKey: String, name: String, isGroup: Boolean) =
        update { RuleEdits.addManualConversation(it, appKey, name, isGroup, label(appKey), ::newId) }

    /** «Identificar con un mensaje»: la espera en curso (solo en memoria). */
    val capture: StateFlow<CaptureSession?> = c.chatCapture.session

    fun startCapture(appKey: String?) = c.chatCapture.start(appKey)

    fun stopCapture() = c.chatCapture.stop()

    /** El chat de un aviso capturado, con su identificador de Android si lo trae. */
    fun addCaptured(arrival: CaptureArrival, option: CaptureOption) = addConversations(
        listOf(SeenChat(option.name, option.isGroup, arrival.receivedAt, arrival.packageName, arrival.conversationId)),
    )

    /** Chat compartido desde la app (WhatsApp «Exportar chat»): como persona, como grupo o como los dos. */
    fun addSharedChat(appKey: String, name: String, asPerson: Boolean, asGroup: Boolean) = update { rules ->
        var out = rules
        if (asPerson) out = RuleEdits.addManualConversation(out, appKey, name, isGroup = false, label(appKey), ::newId)
        if (asGroup) out = RuleEdits.addManualConversation(out, appKey, name, isGroup = true, label(appKey), ::newId)
        out
    }

    fun addCallContact(name: String, alternativeName: String?, number: String?) =
        update { RuleEdits.addCallContact(it, name, alternativeName, number, ::newId) }

    fun addCallNumber(number: String, label: String?) =
        update { RuleEdits.addCallNumber(it, number, label, ::newId) }

    fun save(rule: Rule) = update { rules ->
        val exists = rules.any { it.id == rule.id }
        if (exists) rules.map { if (it.id == rule.id) rule else it } else rules + rule
    }

    fun delete(id: String) = update { rules -> rules.filterNot { it.id == id } }

    fun setEnabled(id: String, enabled: Boolean) = update { rules -> rules.map { if (it.id == id) it.copy(enabled = enabled) else it } }
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
