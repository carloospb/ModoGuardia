package es.carlospb.modoguardia.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.carlospb.modoguardia.domain.TextNormalizer
import es.carlospb.modoguardia.domain.WatchSummary
import es.carlospb.modoguardia.ui.theme.GuardColors

/** Elegir una app instalada para vigilarla. Empieza con «todos los avisos»; luego se puede afinar. */
@Composable
fun AddAppScreen(rulesVm: RulesViewModel, onBack: () -> Unit, onAdded: (String) -> Unit) {
    LaunchedEffect(Unit) { rulesVm.loadApps() }
    val apps by rulesVm.apps.collectAsStateWithLifecycle()
    val rules by rulesVm.rules.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val watched = WatchSummary.appsOf(rules).map { it.key }.toSet()
    val haptics = rememberHaptics()

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = "Añadir aplicación", subtitle = "Sus notificaciones podrán activar la alarma", onBack = onBack)
        Column(Modifier.then(ScreenPadding)) {
            GuardTextField(
                value = query,
                onValueChange = { query = it },
                label = "Buscar app",
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = GuardColors.TextDim) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.height(8.dp))
        }
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GuardColors.Active)
            }
        } else {
            AppList(list, query, watched) { app, key, already ->
                if (!already) {
                    rulesVm.addApp(app)
                    haptics.confirm()
                }
                onAdded(key)
            }
        }
    }
}

@Composable
private fun AppList(list: List<InstalledApp>, query: String, watched: Set<String>, onPick: (InstalledApp, String, Boolean) -> Unit) {
    val folded = TextNormalizer.fold(query)
    val filtered = list.filter { folded.isEmpty() || folded in TextNormalizer.fold(it.label) || folded in it.packageName.lowercase() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            if (filtered.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.SearchOff, "Ninguna app coincide", "Solo aparecen las apps con icono en el menú de aplicaciones.")
                }
            }
            items(filtered, key = { it.packageName }) { app ->
                val key = app.packageName
                val already = key in watched
                ListRow(
                    title = app.label,
                    subtitle = if (already) "Ya vigilada · tocar para ver" else app.packageName,
                    subtitleColor = if (already) GuardColors.Active else GuardColors.TextFaint,
                    leading = { AppIcon(app.packageName, size = 40.dp) },
                    onClick = { onPick(app, key, already) },
                )
            }
        }
}
