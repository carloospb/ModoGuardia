package es.carlospb.modoguardia

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import es.carlospb.modoguardia.alarm.AlarmController
import es.carlospb.modoguardia.alarm.AlarmState
import es.carlospb.modoguardia.alarm.GuardNotifications
import es.carlospb.modoguardia.alarm.VolumeRestore
import es.carlospb.modoguardia.data.HistoryDatabase
import es.carlospb.modoguardia.data.HistoryRepository
import es.carlospb.modoguardia.data.SeenChatsRepository
import es.carlospb.modoguardia.data.SettingsRepository
import es.carlospb.modoguardia.data.guardDataStore
import es.carlospb.modoguardia.domain.Deduplicator
import es.carlospb.modoguardia.domain.GuardEngine
import es.carlospb.modoguardia.domain.RuleEdits
import es.carlospb.modoguardia.domain.WhatsApp
import es.carlospb.modoguardia.service.ChatCaptureStore
import es.carlospb.modoguardia.service.DiagnosticsStore
import es.carlospb.modoguardia.service.GuardController
import es.carlospb.modoguardia.system.SystemStatusChecker
import android.util.Log
import es.carlospb.modoguardia.service.ListenerRecovery
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/** Dependencias del proceso, creadas una vez. Sin framework de DI: el grafo es pequeño. */
class AppContainer(app: Application) {
    /**
     * Un fallo inesperado en una tarea de fondo se registra, pero NO tumba el proceso: en él viven el
     * lector de notificaciones y la alarma, y si el proceso cae varias veces seguidas Android deja de
     * volver a enlazar el lector.
     */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            Log.e("ModoGuardia", "Fallo en tarea de fondo", e)
        },
    )
    val settings = SettingsRepository(app.guardDataStore)
    val seenChats = SeenChatsRepository(app.guardDataStore)
    private val database = HistoryDatabase.create(app)
    val history = HistoryRepository(database.historyDao())
    val diagnostics = DiagnosticsStore()
    val chatCapture = ChatCaptureStore()
    val alarmController = AlarmController(app, settings, history, appScope)
    val engine = GuardEngine(
        snapshot = {
            settings.current().toSnapshot().copy(
                exactNumberAvailable = granted(app, Manifest.permission.READ_PHONE_STATE) && granted(app, Manifest.permission.READ_CALL_LOG),
            )
        },
        dedup = Deduplicator(),
        alarm = alarmController,
        history = history,
    )
    val guardController = GuardController(app, settings, history, engine)
    val statusChecker = SystemStatusChecker(app)
}

class ModoGuardiaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Si el proceso murió en mitad de una reconexión, el lector no puede quedarse desactivado.
        ListenerRecovery.ensureComponentEnabled(this)
        GuardNotifications.ensureChannels(this)
        // Si el proceso murió con la alarma sonando, el volumen de alarma vuelve a su valor.
        if (AlarmState.active.value == null) VolumeRestore.restoreIfPending(this)

        val c = container
        c.appScope.launch {
            c.settings.settings.map { it.diagnosticsEnabled }.distinctUntilChanged().collect(c.diagnostics::setEnabled)
        }
        // Si el proceso arranca (p. ej. lo enlaza el lector de notificaciones) con la guardia activa
        // y sin su servicio, se reanuda.
        c.appScope.launch { c.guardController.ensureServiceRunning() }
        // Reglas de 1.0/1.1 que valían para WhatsApp y Business a la vez: una por cada app instalada.
        c.appScope.launch {
            val installed = WhatsApp.PACKAGES.filter { isInstalled(it) }.toSet()
            c.settings.update { s ->
                RuleEdits.splitLegacyWhatsApp(s.rules, installed) { UUID.randomUUID().toString() }?.let { s.copy(rules = it) } ?: s
            }
        }
        // Privacidad: el historial de versiones anteriores guardaba el inicio de los mensajes.
        c.appScope.launch { runCatching { c.history.stripLegacyMessageText() } }
    }
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.isInstalled(pkg: String): Boolean = try {
    packageManager.getApplicationInfo(pkg, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

val Context.container: AppContainer
    get() = (applicationContext as ModoGuardiaApp).container
