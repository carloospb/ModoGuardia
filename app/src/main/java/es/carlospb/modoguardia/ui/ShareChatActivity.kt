package es.carlospb.modoguardia.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import es.carlospb.modoguardia.domain.SharedChat
import es.carlospb.modoguardia.domain.SharedChats
import es.carlospb.modoguardia.domain.TextNormalizer
import es.carlospb.modoguardia.domain.WatchedApp
import es.carlospb.modoguardia.domain.WhatsApp
import es.carlospb.modoguardia.ui.theme.GuardColors
import es.carlospb.modoguardia.ui.theme.ModoGuardiaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que llegó al compartir con Modo Guardia: el chat (si se pudo saber) y la app que lo compartió. */
data class ShareResult(val chat: SharedChat?, val sourcePackage: String?)

/**
 * Destino del menú «Compartir» de Android. Desde WhatsApp o WhatsApp Business, «Exportar chat» →
 * Modo Guardia da el nombre exacto del chat. Solo se leen el asunto y el NOMBRE de los archivos
 * compartidos: nunca se abre su contenido ni se lee el texto compartido.
 *
 * Otras apps (Telegram, Slack...) no dicen de qué chat viene lo que comparten: se explica y se
 * ofrece «Identificar con un mensaje».
 */
class ShareChatActivity : ComponentActivity() {
    private val result = MutableStateFlow<ShareResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        read(intent)
        setContent {
            ModoGuardiaTheme {
                val r by result.collectAsStateWithLifecycle()
                ShareChatScreen(
                    result = r,
                    onClose = ::finish,
                    onOpenMain = { request ->
                        startActivity(MainActivity.open(this, request))
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        result.value = null
        read(intent)
    }

    private fun read(intent: Intent) {
        // La app que compartió (Android la indica como «referrer»), solo si es una app instalada.
        val referrerHost = referrer?.takeIf { it.scheme == "android-app" }?.host
        lifecycleScope.launch {
            result.value = withContext(Dispatchers.IO) {
                val source = referrerHost?.takeIf { it != packageName && it != "android" && AppInfo.isInstalled(this@ShareChatActivity, it) }
                val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
                val title = intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString()
                val names = streams(intent).take(MAX_FILES).mapNotNull(::displayName)
                ShareResult(SharedChats.fromShare(subject, title, names, source), source)
            }
        }
    }

    private fun streams(intent: Intent): List<Uri> {
        val out = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(out::add)
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(out::addAll)
        }
        intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNullTo(out) { clip.getItemAt(it).uri } }
        return out.distinct()
    }

    /** Solo el nombre del archivo («Chat de WhatsApp con Soporte.txt»), sin abrirlo. */
    private fun displayName(uri: Uri): String? {
        val fromProvider = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment
    }

    private companion object {
        const val MAX_FILES = 5
    }
}

@Composable
fun ShareChatScreen(result: ShareResult?, onClose: () -> Unit, onOpenMain: (MainActivity.OpenRequest) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(GuardColors.Background)
            .safeDrawingPadding(),
    ) {
        when {
            result == null -> CircularProgressIndicator(color = GuardColors.Active, modifier = Modifier.align(Alignment.Center))
            result.chat == null -> NotAChat(result.sourcePackage, onClose, onOpenMain)
            else -> SharedChatForm(result.chat, onClose, onOpenMain)
        }
    }
}

@Composable
private fun NotAChat(source: String?, onClose: () -> Unit, onOpenMain: (MainActivity.OpenRequest) -> Unit) {
    val app = source?.let { rememberAppLabel(it) }
    val fromWhatsApp = source != null && WhatsApp.isWhatsApp(source)
    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = "Vigilar un chat", subtitle = app?.let { "Compartido desde $it" }, onBack = onClose, leading = source?.let { { AppIcon(it, size = 36.dp) } })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .then(ScreenPadding),
        ) {
            EmptyState(
                icon = Icons.Rounded.LinkOff,
                title = "Lo compartido no dice de qué chat es",
                body = if (fromWhatsApp) {
                    "Para elegir un chat de $app, compártelo así: abre el chat → ⋮ → Más → Exportar chat → " +
                        "Sin archivos → Modo Guardia. Así llega su nombre exacto."
                } else {
                    "${app ?: "Esta app"} no incluye el nombre del chat cuando comparte un mensaje o un enlace con otra app. " +
                        "Usa «Identificar con un mensaje»: alguien escribe en ese chat y Modo Guardia te dice cuál es."
                },
            )
            Text(
                "Modo Guardia no ha leído lo que compartiste.",
                style = MaterialTheme.typography.bodySmall,
                color = GuardColors.TextDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                "Identificar con un mensaje",
                onClick = { onOpenMain(MainActivity.OpenRequest.Identify(source)) },
                icon = Icons.Rounded.MarkChatUnread,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton("Cerrar", onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SharedChatForm(chat: SharedChat, onClose: () -> Unit, onOpenMain: (MainActivity.OpenRequest) -> Unit) {
    val rulesVm: RulesViewModel = viewModel()
    val rules by rulesVm.rules.collectAsStateWithLifecycle()
    val seen by rulesVm.seenChats.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val installed = remember { WhatsApp.PACKAGES.filter { AppInfo.isInstalled(context, it) } }
    // App: la que lo compartió; si Android no lo dice, la única WhatsApp instalada o la que elija el usuario.
    val choices = installed.ifEmpty { WhatsApp.PACKAGES.toList() }
    var appIndex by rememberSaveable { mutableIntStateOf(0) }
    val appKey = chat.packageName ?: choices.getOrElse(appIndex) { WhatsApp.PERSONAL }
    val label = rememberAppLabel(appKey)

    // Persona o grupo: WhatsApp no lo dice al exportar. Si ya avisó alguna vez, se sabe; si no, los dos.
    val known = remember(seen, appKey) {
        seen.filter { it.appKey == appKey && TextNormalizer.groupKey(it.name) == TextNormalizer.groupKey(chat.name) }
            .map { it.isGroup }.distinct().singleOrNull()
    }
    var kind by rememberSaveable(known) { mutableIntStateOf(if (known == null) BOTH else if (known) GROUP else PERSON) }
    var done by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val allNotifications = WatchedApp(appKey, rules.filter { !it.isCall && it.appKey == appKey }).allNotifications

    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            title = "Vigilar un chat",
            subtitle = if (chat.packageName != null) "Compartido desde $label" else "Chat exportado de WhatsApp",
            onBack = onClose,
            leading = { AppIcon(appKey, size = 36.dp) },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .then(ScreenPadding),
        ) {
            Spacer(Modifier.height(8.dp))
            GuardCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConversationAvatar(chat.name, isGroup = kind == GROUP, size = 52.dp, appKey = appKey)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Chat de $label", style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
                    }
                }
            }
            if (done) {
                Spacer(Modifier.height(24.dp))
                EmptyState(
                    icon = Icons.Rounded.CheckCircle,
                    title = "Listo: «${chat.name}» te despertará",
                    body = if (allNotifications) {
                        "Ahora mismo $label te despierta con todos sus avisos. Este chat cuenta cuando elijas «Solo los chats que elijas»."
                    } else {
                        "Mientras Modo Guardia esté activo, un mensaje de este chat hará sonar la alarma."
                    },
                )
            } else {
                ShareChoices(chat, choices, appIndex, { appIndex = it }, kind, { kind = it }, known, label)
            }
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (done) {
                PrimaryButton("Ver $label", onClick = { onOpenMain(MainActivity.OpenRequest.App(appKey)) }, modifier = Modifier.fillMaxWidth())
                SecondaryButton("Cerrar", onClick = onClose, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton(
                    "Vigilar este chat",
                    onClick = {
                        rulesVm.addSharedChat(appKey, chat.name, asPerson = kind != GROUP, asGroup = kind != PERSON)
                        haptics.confirm()
                        done = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton("Cancelar", onClick = onClose, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ShareChoices(
    chat: SharedChat,
    choices: List<String>,
    appIndex: Int,
    onApp: (Int) -> Unit,
    kind: Int,
    onKind: (Int) -> Unit,
    known: Boolean?,
    label: String,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column {
        if (chat.packageName == null && choices.size > 1) {
            SectionTitle("¿De qué app?")
            PillTabs(choices.map { AppInfo.label(context, it) }, selected = appIndex, onSelect = onApp)
        }
        SectionTitle("¿Qué es «${chat.name}»?")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceCard("Una persona", "Un chat individual.", selected = kind == PERSON, icon = Icons.Rounded.Person) { onKind(PERSON) }
            ChoiceCard("Un grupo", null, selected = kind == GROUP, icon = Icons.Rounded.Group) { onKind(GROUP) }
            ChoiceCard("No estoy seguro", "Se vigila de las dos formas. Así no falla.", selected = kind == BOTH, icon = Icons.AutoMirrored.Rounded.HelpOutline) { onKind(BOTH) }
        }
        Hint(
            (if (known != null) "Ya ha avisado antes como ${if (known) "grupo" else "persona"}. " else "Al exportar, $label no dice si es una persona o un grupo. ") +
                "Modo Guardia solo ha leído el nombre del chat: no ha abierto la conversación exportada.",
        )
    }
}

private const val PERSON = 0
private const val GROUP = 1
private const val BOTH = 2
