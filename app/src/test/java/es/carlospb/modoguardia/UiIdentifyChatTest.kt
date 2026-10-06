package es.carlospb.modoguardia

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.carlospb.modoguardia.domain.RuleType
import es.carlospb.modoguardia.service.ParsedNotification
import es.carlospb.modoguardia.ui.MainActivity
import es.carlospb.modoguardia.ui.ShareChatActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Identificar el chat exacto con la app real: «Identificar con un mensaje» (llega un aviso y se
 * vigila su chat con un toque) y compartir un chat desde WhatsApp con «Exportar chat».
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiIdentifyChatTest : UiTestBase() {
    override val shotPrefix = "id-"

    private val container get() = ApplicationProvider.getApplicationContext<ModoGuardiaApp>().container

    /** Las reglas se guardan en DataStore en segundo plano: se espera (hasta 5 s) a que estén. */
    private fun rulesEventually(check: (List<es.carlospb.modoguardia.domain.Rule>) -> Boolean): Boolean {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (check(runBlocking { container.settings.current().rules })) return true
            Thread.sleep(50)
        }
        return false
    }

    @Test
    fun aMessageIdentifiesTheChatAndOneTapWatchesIt() {
        compose.mainClock.autoAdvance = false
        seed(guardActive = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            current = scenario
            exists("Modo Guardia desactivado")
            click("Reglas")
            click("WhatsApp")
            click("Identificar con un mensaje")
            exists("Esperando un aviso")
            shot("01-esperando")

            // El más reciente queda arriba: el grupo, que es el que se vigila después.
            container.chatCapture.record(ParsedNotification(appNotification(WA, "WhatsApp", "Equipo: Luis", "hola", postTime = now), null))
            container.chatCapture.record(ParsedNotification(waGroupMessage("Incidencias Nocturnas", "Ana", "Hola", ts = now), null))
            exists("Incidencias Nocturnas")
            exists("Android no dice si es una persona o un grupo")
            shot("02-avisos")

            click("Vigilar")
            exists("Vigilado")
            assertTrue(rulesEventually { rules -> rules.any { it.type == RuleType.GROUP && it.packageName == WA && it.name == "Incidencias Nocturnas" } })
            back()
        }
        // Al salir de la pantalla se deja de escuchar.
        assertTrue(container.chatCapture.session.value == null)
    }

    @Test
    fun aChatSharedFromWhatsAppBecomesARule() {
        compose.mainClock.autoAdvance = false
        seed(guardActive = false)
        val intent = Intent(ApplicationProvider.getApplicationContext(), ShareChatActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Chat de WhatsApp con Guardia Redes")
        ActivityScenario.launch<ShareChatActivity>(intent).use { scenario ->
            current = scenario
            exists("Guardia Redes")
            exists("No estoy seguro")
            shot("03-compartido")
            click("Vigilar este chat")
            exists("te despertará")
            shot("04-compartido-listo")
        }
        // Sin saber si es persona o grupo, se vigila de las dos formas.
        assertTrue(rulesEventually { rules -> rules.filter { it.name == "Guardia Redes" }.map { it.type }.toSet() == setOf(RuleType.PERSON, RuleType.GROUP) })
    }

    @Test
    fun theShareScreenOpensIdentifyForThatApp() {
        compose.mainClock.autoAdvance = false
        seed(guardActive = false)
        val intent = MainActivity.open(ApplicationProvider.getApplicationContext(), MainActivity.OpenRequest.Identify(TG))
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            current = scenario
            exists("Identificar un chat")
            exists("Esperando un aviso")
        }
        assertTrue(container.chatCapture.session.value == null)
    }

    @Test
    fun aShareThatDoesNotNameTheChatIsExplainedHonestly() {
        compose.mainClock.autoAdvance = false
        seed(guardActive = false)
        val intent = Intent(ApplicationProvider.getApplicationContext(), ShareChatActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "https://t.me/c/1234/56")
        ActivityScenario.launch<ShareChatActivity>(intent).use { scenario ->
            current = scenario
            exists("Lo compartido no dice de qué chat es")
            exists("Identificar con un mensaje")
            shot("05-compartido-sin-chat")
        }
    }
}
