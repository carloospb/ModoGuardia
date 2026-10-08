package es.carlospb.modoguardia

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.carlospb.modoguardia.service.GuardNotificationListener
import es.carlospb.modoguardia.service.ListenerRecovery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Reconexión del lector de notificaciones. Lo que más importa: el componente del lector nunca puede
 * quedarse desactivado, porque entonces Android no lo enlazaría nunca más.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ListenerRecoveryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pm = context.packageManager
    private val component = GuardNotificationListener.component(context)

    @Test
    fun aDisabledListenerComponentIsReEnabledAtStartup() {
        pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)

        ListenerRecovery.ensureComponentEnabled(context)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(component))
    }

    @Test
    fun startupLeavesAHealthyComponentAlone() {
        ListenerRecovery.ensureComponentEnabled(context)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(component))
    }

    @Test
    fun withoutAccessItSaysSoAndTouchesNothing() = runBlocking {
        // En la prueba el usuario no ha concedido el acceso a notificaciones.
        assertEquals(ListenerRecovery.Result.NO_ACCESS, ListenerRecovery.reconnect(context))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(component))
    }
}
