package es.carlospb.modoguardia.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShieldMoon
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

/** Pestañas: Inicio (estado), Reglas (qué despierta), Historial y Ajustes. */
private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("inicio", "Inicio", Icons.Outlined.ShieldMoon, Icons.Rounded.ShieldMoon),
    RULES("reglas", "Reglas", Icons.Outlined.Tune, Icons.Rounded.Tune),
    HISTORY("historial", "Historial", Icons.Outlined.History, Icons.Rounded.History),
    SETTINGS("ajustes", "Ajustes", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

private object Routes {
    const val ONBOARDING = "bienvenida"
    const val STATUS = "estado"
    const val CALLS = "llamadas"
    const val ADD_APP = "anadir-app"
    const val APP = "app/{key}"
    const val CHATS = "chats?app={app}"
    const val KEYWORDS = "palabras?id={id}&app={app}"

    fun app(key: String) = "app/${Uri.encode(key)}"
    fun chats(appKey: String?) = if (appKey == null) "chats" else "chats?app=${Uri.encode(appKey)}"
    fun keywords(id: String?, appKey: String?) = buildString {
        append("palabras")
        val params = listOfNotNull(id?.let { "id=${Uri.encode(it)}" }, appKey?.let { "app=${Uri.encode(it)}" })
        if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
    }
}

private val tabRoutes = Tab.entries.map { it.route }.toSet()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

@Composable
private fun Root() {
    val vm: MainViewModel = viewModel()
    val rulesVm: RulesViewModel = viewModel()
    val updateVm: UpdateViewModel = viewModel()
    val update by updateVm.state.collectAsStateWithLifecycle()
    val updatePrompt by updateVm.prompt.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val alarm by vm.alarm.collectAsStateWithLifecycle()
    val countdown by vm.countdown.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onFix = rememberFixHandler(vm)
    val snackbar = remember { SnackbarHostState() }

    // Estado del sistema: se vuelve a leer cada pocos segundos solo con la app en pantalla.
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollStatus() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { updateVm.onResume() }

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

    // Nunca encima de una alarma: la alerta tiene prioridad sobre cualquier actualización.
    if (updatePrompt && alarm == null) {
        UpdateDialog(update, onUpdate = updateVm::downloadAndInstall, onDismiss = updateVm::dismiss)
    }

    val s = settings ?: run {
        Box(Modifier.fillMaxSize().background(GuardColors.Background))
        return
    }

    val nav = rememberNavController()
    val startRoute = remember { if (s.onboardingDone) Tab.HOME.route else Routes.ONBOARDING }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = current == null && startRoute != Routes.ONBOARDING || current in tabRoutes

    Scaffold(
        containerColor = GuardColors.Background,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = GuardColors.SurfaceHighest,
                    contentColor = GuardColors.Text,
                    shape = MaterialTheme.shapes.medium,
                )
            }
        },
        bottomBar = {
            if (showBar) BottomBar(nav, current)
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = startRoute,
            modifier = Modifier.padding(padding),
            enterTransition = { if (isTabSwitch()) fadeIn(tween(220)) else fadeIn(tween(260)) + slideInHorizontally(tween(300)) { it / 6 } },
            exitTransition = { if (isTabSwitch()) fadeOut(tween(160)) else fadeOut(tween(200)) },
            popEnterTransition = { if (isTabSwitch()) fadeIn(tween(220)) else fadeIn(tween(260)) },
            popExitTransition = { if (isTabSwitch()) fadeOut(tween(160)) else fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 6 } },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    settings = s,
                    status = status,
                    vm = vm,
                    rulesVm = rulesVm,
                    onFix = onFix,
                    onPickChats = { key -> nav.navigate(Routes.chats(key)) },
                    onAddApp = { nav.navigate(Routes.ADD_APP) },
                    onOpenApp = { key -> nav.navigate(Routes.app(key)) },
                    onOpenCalls = { nav.navigate(Routes.CALLS) },
                    onFinish = { startGuard ->
                        vm.setOnboardingDone(true)
                        if (startGuard) vm.startGuard()
                        // Inicio queda como única pantalla de la pila (primera vez o repetición).
                        nav.navigate(Tab.HOME.route) { popUpTo(nav.graph.id) { inclusive = true } }
                    },
                )
            }
            composable(Tab.HOME.route) {
                HomeScreen(
                    settings = s,
                    status = status,
                    countdown = countdown,
                    onStart = vm::startGuard,
                    onStop = vm::stopGuard,
                    onOpenStatus = { nav.navigate(Routes.STATUS) { launchSingleTop = true } },
                    onOpenRules = { nav.switchTab(Tab.RULES) },
                    onOpenCalls = { nav.navigate(Routes.CALLS) { launchSingleTop = true } },
                    onOpenApp = { key -> nav.navigate(Routes.app(key)) },
                    onTestAlarm = { vm.testAlarm(3) },
                )
            }
            composable(Tab.RULES.route) {
                RulesScreen(
                    settings = s,
                    rulesVm = rulesVm,
                    onOpenApp = { key -> nav.navigate(Routes.app(key)) },
                    onAddApp = { nav.navigate(Routes.ADD_APP) },
                    onOpenCalls = { nav.navigate(Routes.CALLS) { launchSingleTop = true } },
                    onEditKeywords = { id -> nav.navigate(Routes.keywords(id, null)) },
                )
            }
            composable(Tab.HISTORY.route) { HistoryScreen(viewModel()) }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    settings = s,
                    status = status,
                    vm = vm,
                    rulesVm = rulesVm,
                    updateVm = updateVm,
                    onOpenStatus = { nav.navigate(Routes.STATUS) { launchSingleTop = true } },
                    onRerunOnboarding = { nav.navigate(Routes.ONBOARDING) { launchSingleTop = true } },
                )
            }
            composable(Routes.STATUS) { StatusScreen(s, status, vm, onFix) { nav.popBackStack() } }
            composable(Routes.CALLS) { CallsScreen(s, rulesVm, vm, onFix) { nav.popBackStack() } }
            composable(Routes.ADD_APP) {
                AddAppScreen(rulesVm, onBack = { nav.popBackStack() }) { key ->
                    nav.navigate(Routes.app(key)) { popUpTo(Routes.ADD_APP) { inclusive = true } }
                }
            }
            composable(Routes.APP, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                val key = entry.arguments?.getString("key").orEmpty()
                AppDetailScreen(
                    appKey = key,
                    rulesVm = rulesVm,
                    onBack = { nav.popBackStack() },
                    onPickChats = { appKey -> nav.navigate(Routes.chats(appKey)) },
                    onEditKeywords = { id, appKey -> nav.navigate(Routes.keywords(id, appKey)) },
                )
            }
            composable(
                Routes.CHATS,
                arguments = listOf(navArgument("app") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                ConversationPickerScreen(
                    appKey = entry.arguments?.getString("app"),
                    rulesVm = rulesVm,
                    onBack = { nav.popBackStack() },
                    onAdded = { count ->
                        vm.showMessage(if (count == 1) "1 chat vigilado" else "$count chats vigilados")
                        nav.popBackStack()
                    },
                )
            }
            composable(
                Routes.KEYWORDS,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("app") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                KeywordEditorScreen(
                    ruleId = entry.arguments?.getString("id"),
                    presetApp = entry.arguments?.getString("app"),
                    rulesVm = rulesVm,
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}

/** Cambiar de pestaña: Inicio siempre queda debajo, así «atrás» vuelve a Inicio y luego sale. */
private fun NavHostController.switchTab(tab: Tab) = navigate(tab.route) {
    popUpTo(Tab.HOME.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(GuardColors.OutlineSoft),
        )
        NavigationBar(containerColor = GuardColors.Background, tonalElevation = 0.dp) {
            Tab.entries.forEach { tab ->
                val selected = current == tab.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { nav.switchTab(tab) },
                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GuardColors.Active,
                        selectedTextColor = GuardColors.Text,
                        indicatorColor = GuardColors.ActiveContainer,
                        unselectedIconColor = GuardColors.TextDim,
                        unselectedTextColor = GuardColors.TextDim,
                    ),
                )
            }
        }
    }
}
