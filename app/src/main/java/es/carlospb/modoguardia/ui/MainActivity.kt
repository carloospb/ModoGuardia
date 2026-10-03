package es.carlospb.modoguardia.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import es.carlospb.modoguardia.alarm.GuardAlarmActivity
import es.carlospb.modoguardia.container
import es.carlospb.modoguardia.ui.theme.GuardColors
import es.carlospb.modoguardia.ui.theme.ModoGuardiaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ModoGuardiaTheme { Root() }
        }
    }

    override fun onResume() {
        super.onResume()
        // En primer plano Android siempre deja iniciar servicios: buen momento para reanudar la guardia.
        lifecycleScope.launch { container.guardController.ensureServiceRunning() }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("guardia", "Guardia", Icons.Filled.Shield),
    RULES("reglas", "Reglas", Icons.AutoMirrored.Filled.Rule),
    STATUS("estado", "Estado", Icons.Filled.MonitorHeart),
    HISTORY("historial", "Historial", Icons.Filled.History),
    SETTINGS("ajustes", "Ajustes", Icons.Filled.Settings),
}

@Composable
private fun Root() {
    val vm: MainViewModel = viewModel()
    val rulesVm: RulesViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val alarm by vm.alarm.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val onFix = rememberFixHandler(vm)
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshStatus() }

    // Con la app abierta, la alerta se abre directamente (sin depender del full-screen intent).
    LaunchedEffect(alarm != null) {
        if (alarm != null) {
            context.startActivity(Intent(context, GuardAlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val s = settings ?: run {
        Box(Modifier.fillMaxSize())
        return
    }

    if (!s.onboardingDone) {
        OnboardingScreen(s, status, vm, rulesVm, onFix) { vm.setOnboardingDone(true) }
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        containerColor = GuardColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = GuardColors.Surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    settings = s,
                    status = status,
                    onStart = vm::startGuard,
                    onStop = vm::stopGuard,
                    onFix = onFix,
                    onOpenStatus = { nav.navigate(Tab.STATUS.route) { launchSingleTop = true } },
                    onOpenRules = { nav.navigate(Tab.RULES.route) { launchSingleTop = true } },
                )
            }
            composable(Tab.RULES.route) {
                RulesScreen(s, rulesVm, vm, onFix) { id -> nav.navigate("regla/${id ?: "nueva"}") }
            }
            composable("regla/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.takeIf { it != "nueva" }
                RuleEditorScreen(id, rulesVm) { nav.popBackStack() }
            }
            composable(Tab.STATUS.route) { StatusScreen(s, status, vm, onFix) }
            composable(Tab.HISTORY.route) { HistoryScreen(viewModel()) }
            composable(Tab.SETTINGS.route) { SettingsScreen(s, vm) { vm.setOnboardingDone(false) } }
        }
    }
}
