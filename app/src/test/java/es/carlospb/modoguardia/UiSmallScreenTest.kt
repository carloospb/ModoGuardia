package es.carlospb.modoguardia

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.carlospb.modoguardia.alarm.AlarmState
import es.carlospb.modoguardia.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Móvil pequeño (320 dp de ancho) con la letra al 130 %: lo esencial sigue en pantalla y se puede
 * usar (control de la guardia, reglas, llamadas y el botón DETENER ALARMA).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.3f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSmallScreenTest : UiTestBase() {
    override val shotPrefix = "pequena-"

    @Test
    fun smallScreenWithLargeText() {
        compose.mainClock.autoAdvance = false
        seed(guardActive = true)
        seedLists()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            current = scenario
            exists("Modo Guardia activo")
            shot("01-inicio")
            click("Reglas")
            exists("Aplicaciones vigiladas")
            shot("02-reglas")
            click("WhatsApp")
            exists("Elegir chats")
            shot("03-app")
            back()
            click("Llamadas que activan la alarma")
            exists("Contactos y números")
            shot("04-llamadas")
        }
        alarmShot("05-alarma", messageAlarm())
        AlarmState.set(null)
    }
}
