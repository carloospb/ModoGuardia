package es.carlospb.modoguardia

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import es.carlospb.modoguardia.alarm.ActiveAlarm
import es.carlospb.modoguardia.alarm.AlarmState
import es.carlospb.modoguardia.alarm.GuardAlarmActivity
import es.carlospb.modoguardia.domain.AlarmKind
import es.carlospb.modoguardia.domain.AlarmRequest
import es.carlospb.modoguardia.domain.CallMode
import es.carlospb.modoguardia.domain.HistoryType
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.domain.SeenChat
import kotlinx.coroutines.runBlocking
import org.junit.Rule as JUnitRule
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Base de las pruebas de interfaz con Robolectric: la app real (Application, ViewModels, DataStore,
 * Room, navegación) con datos de ejemplo. Con la variable de entorno MG_SCREENSHOTS_DIR guarda una
 * captura PNG de cada pantalla, para revisar el diseño sin teléfono.
 *
 * El reloj de animaciones va parado (hay animaciones infinitas: el halo de la guardia activa, las
 * ondas de la alarma) y se avanza a mano.
 */
abstract class UiTestBase {
    @get:JUnitRule
    val compose = createEmptyComposeRule()

    /** Prefijo de los nombres de las capturas. */
    protected open val shotPrefix = ""

    private val shotsDir: File? = System.getenv("MG_SCREENSHOTS_DIR")?.let { File(it).apply { mkdirs() } }

    protected val now = System.currentTimeMillis()
    protected val hour = 3_600_000L

    private val demoRules = listOf(
        Rule(id = "g1", type = RuleType.GROUP, name = "Guardias Soporte"),
        Rule(id = "p1", type = RuleType.PERSON, name = "Carlos"),
        Rule(id = "p2", type = RuleType.PERSON, name = "Laura"),
        Rule(id = "t1", type = RuleType.GROUP, packageName = TG, appLabel = "Telegram", name = "Equipo técnico"),
        Rule(id = "a1", type = RuleType.APP, packageName = "com.pagerduty.android", appLabel = "PagerDuty"),
        Rule(id = "k1", type = RuleType.KEYWORDS, keywords = listOf("URGENTE", "CAÍDA")),
        Rule(id = "c1", type = RuleType.CALL, name = "Juan Martín", aliases = listOf("Juan Martín", "Martín, Juan"), numbers = listOf("+34 600 11 22 33")),
        Rule(id = "c2", type = RuleType.CALL, name = "Empresa", numbers = listOf("+34 912 34 56 78")),
    )

    protected fun seed(guardActive: Boolean) = runBlocking {
        val c = ApplicationProvider.getApplicationContext<ModoGuardiaApp>().container
        c.settings.update {
            it.copy(
                onboardingDone = true,
                rules = demoRules,
                wakeOnPhoneCalls = true,
                callMode = CallMode.SELECTED,
                wakeOnAppCalls = true,
                guardActive = guardActive,
                guardStartedAt = now - 2 * hour - 14 * 60_000,
            )
        }
    }

    protected fun seedLists() = runBlocking {
        val c = ApplicationProvider.getApplicationContext<ModoGuardiaApp>().container
        listOf(
            SeenChat("Carlos", false, now - 5 * 60_000),
            SeenChat("Laura", false, now - 40 * 60_000),
            SeenChat("Pedro", false, now - 3 * hour),
            SeenChat("Guardias Soporte", true, now - 20 * 60_000),
            SeenChat("Amigos del barrio", true, now - 26 * hour),
            SeenChat("Familia", true, now - 2 * 24 * hour),
            SeenChat("Equipo técnico", true, now - hour, packageName = TG),
            SeenChat("Ana", false, now - 7 * hour, packageName = TG),
        ).forEach { c.seenChats.record(it) }
        c.history.log(HistoryType.GUARD_STARTED, "Guardia iniciada", "7 reglas activas", now - 5 * hour)
        c.history.log(HistoryType.ALARM, "Alarma activada", "Teléfono · Empresa", now - 3 * hour)
        c.history.log(HistoryType.ALARM_STOPPED, "Alarma detenida", "Detenida por el usuario", now - 3 * hour + 40_000)
        c.history.log(HistoryType.ALARM, "Alarma activada", "WhatsApp · Guardias Soporte", now - hour)
        c.history.log(HistoryType.IGNORED, "Notificación ignorada", "Instagram", now - 30 * 60_000)
    }

    /** Actividad visible ahora mismo, para dibujar su ventana. */
    protected var current: ActivityScenario<out Activity>? = null

    protected fun shot(name: String) {
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
        val dir = shotsDir ?: return
        current?.onActivity { activity ->
            // Se dibuja la ventana en un bitmap (gráficos nativos de Robolectric): no hace falta
            // esperar a un fotograma, que con el reloj de animaciones parado no llega.
            val view = activity.window.decorView
            val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(full))
            val small = Bitmap.createScaledBitmap(full, view.width / 2, view.height / 2, true)
            File(dir, "$shotPrefix$name.png").outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun node(text: String): SemanticsNodeInteraction =
        compose.onAllNodes(hasText(text, substring = true) and hasClickAction(), useUnmergedTree = false).onFirst()

    /**
     * Pulsa con la acción de accesibilidad (como TalkBack): no hace falta desplazar la pantalla, que
     * con el reloj de animaciones parado no terminaría nunca.
     */
    protected fun click(text: String) {
        node(text).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    protected fun back() {
        compose.onAllNodes(hasContentDescription("Volver") and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    /** Espera (hasta 5 s reales) a que aparezca: listas que cargan de Room o DataStore. */
    protected fun exists(text: String) {
        val matcher = hasText(text, substring = true, ignoreCase = true)
        try {
            compose.waitUntil(5_000) {
                // Los resultados de Room/DataStore llegan por la cola del hilo principal: se procesa.
                shadowOf(Looper.getMainLooper()).idle()
                // Con el reloj de Compose parado no hay fotogramas: se avanza uno para recomponer.
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (_: Throwable) {
            // Cae al fallo explicativo de abajo.
        }
        if (compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            shot("fallo")
            val tree = compose.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 40)
            throw AssertionError("No aparece en pantalla: «$text»\n$tree")
        }
    }

    protected fun alarmShot(name: String, request: AlarmRequest) {
        AlarmState.set(ActiveAlarm(request, startedAt = now))
        val intent = Intent(ApplicationProvider.getApplicationContext(), GuardAlarmActivity::class.java)
        ActivityScenario.launch<GuardAlarmActivity>(intent).use { scenario ->
            current = scenario
            exists(if (request.kind == AlarmKind.PHONE_CALL) "LLAMADA DE GUARDIA" else "ALERTA DE GUARDIA")
            exists("DETENER ALARMA")
            shot(name)
        }
    }

    protected fun messageAlarm() = AlarmRequest(
        AlarmKind.NOTIFICATION, "WhatsApp", WA, "Guardias Soporte",
        "Ana: Se ha caído producción, ¿puedes mirarlo?", now, "Guardias Soporte",
    )

    protected fun callAlarm() = AlarmRequest(
        AlarmKind.PHONE_CALL, "Teléfono", null, "Juan Martín", "Llamada entrante: Juan Martín", now, "Juan Martín",
    )
}
