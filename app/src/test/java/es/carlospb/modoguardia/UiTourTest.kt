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
 * Recorrido por toda la interfaz con la app real: cada pantalla se abre y muestra lo que debe, sin
 * cierres inesperados (inicio, reglas, app, elegir chats, llamadas, historial, ajustes, estado,
 * guardia activa, configuración guiada completa y pantallas de alarma).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiTourTest : UiTestBase() {

    @Test
    fun everyScreenOpensAndShowsItsContent() {
        compose.mainClock.autoAdvance = false
        seed(guardActive = false)
        seedLists()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            current = scenario
            shot("01-inicio")
            exists("Modo Guardia desactivado")
            exists("Te despertará")

            click("Reglas")
            exists("Qué te despierta")
            exists("Aplicaciones vigiladas")
            shot("02-reglas")

            click("Añadir aplicación")
            exists("Buscar app")
            shot("02b-anadir-app")
            back()

            click("Añadir palabras clave")
            exists("Palabras (separadas por comas)")
            shot("02c-palabras-clave")
            back()

            click("WhatsApp")
            exists("Te despierta")
            exists("Guardias Soporte")
            shot("03-app-whatsapp")

            click("Elegir chats")
            exists("Buscar conversación")
            click("Pedro")
            shot("04-elegir-chats")
            back()
            back()

            click("Llamadas que activan la alarma")
            exists("Contactos y números")
            exists("Juan Martín")
            shot("05-llamadas")
            back()

            click("Historial")
            exists("Guardias Soporte")
            shot("06-historial")

            click("Ajustes")
            exists("Volumen de la alarma")
            shot("07-ajustes")
            click("Estado del sistema")
            exists("Comprobaciones")
            shot("08-estado")
            back()

            seed(guardActive = true)
            click("Inicio")
            exists("Modo Guardia activo")
            shot("09-inicio-activo")

            click("Ajustes")
            click("Repetir la configuración guiada")
            exists("Duerme tranquilo")
            shot("10-guia-bienvenida")
            click("Empezar")
            exists("Lo imprescindible")
            shot("11-guia-permisos")
            click("Siguiente")
            exists("¿Qué debe despertarte?")
            shot("12-guia-que-despierta")
            click("Siguiente")
            exists("Llamadas")
            shot("13-guia-llamadas")
            click("Siguiente")
            exists("Tu alarma")
            shot("14-guia-alarma")
            click("Siguiente")
            exists("Te despertará")
            shot("15-guia-listo")
        }

        alarmShot("16-alarma-mensaje", messageAlarm())
        alarmShot("17-alarma-llamada", callAlarm())
        AlarmState.set(null)
    }
}
