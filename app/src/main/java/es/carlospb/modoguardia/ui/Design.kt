package es.carlospb.modoguardia.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.carlospb.modoguardia.ui.theme.GuardColors
import es.carlospb.modoguardia.ui.theme.SectionLabel

/** Tono de un estado: el mismo código de color en toda la app. */
enum class Tone { ACTIVE, OK, WARNING, ERROR, INFO, NEUTRAL }

val Tone.color: Color
    get() = when (this) {
        Tone.ACTIVE, Tone.OK -> GuardColors.Active
        Tone.WARNING -> GuardColors.Warning
        Tone.ERROR -> GuardColors.Error
        Tone.INFO -> GuardColors.Info
        Tone.NEUTRAL -> GuardColors.TextDim
    }

val Tone.container: Color
    get() = when (this) {
        Tone.ACTIVE, Tone.OK -> GuardColors.ActiveContainer
        Tone.WARNING -> GuardColors.WarningContainer
        Tone.ERROR -> GuardColors.ErrorContainer
        Tone.INFO -> GuardColors.InfoContainer
        Tone.NEUTRAL -> GuardColors.SurfaceHigh
    }

/** Margen lateral de todas las pantallas. */
val ScreenPadding = Modifier.padding(horizontal = 20.dp)

/**
 * Poco ancho para el texto: pantalla estrecha o letra grande (ancho en dp dividido por la escala de
 * la fuente). Entonces los iconos decorativos de filas y tarjetas se ocultan para dejar sitio al texto.
 */
@Composable
fun isCompact(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp / config.fontScale.coerceAtLeast(1f) < 340f
}

/** Tarjeta base: superficie elevada por color (en oscuro las sombras no se ven), borde sutil. */
@Composable
fun GuardCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    containerColor: Color = GuardColors.Surface,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, borderColor ?: GuardColors.OutlineSoft)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = containerColor, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = containerColor, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 26.dp, bottom = 10.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = SectionLabel, color = GuardColors.TextFaint, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Título grande de las pestañas principales. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            subtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = GuardColors.TextDim)
            }
        }
        action?.invoke()
    }
}

/** Barra superior de las pantallas que se abren desde otra: volver + título. */
@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
        }
        leading?.let {
            Spacer(Modifier.width(4.dp))
            it()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        actions()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = GuardColors.Active,
    contentColor: Color = GuardColors.OnActive,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = GuardColors.SurfaceHigh,
            disabledContentColor = GuardColors.TextFaint,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = GuardColors.Text,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, GuardColors.Outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor, disabledContentColor = GuardColors.TextFaint),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Icono en un cuadrado redondeado con el color de su estado. */
@Composable
fun IconBadge(icon: ImageVector, tone: Tone, size: Dp = 40.dp, contentDescription: String? = null) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(tone.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tone.color, modifier = Modifier.size(size * 0.52f))
    }
}

/** Pastilla de estado: punto + texto. */
@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(tone.container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(tone.color),
        )
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tone.color, maxLines = 1)
    }
}

/** Fila de lista: icono o avatar, título, subtítulo y acción. Toda la fila se puede pulsar. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = GuardColors.Text,
    subtitleColor: Color = GuardColors.TextDim,
    showChevron: Boolean = onClick != null && trailing == null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = subtitleColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.let {
            Spacer(Modifier.width(10.dp))
            it()
        }
        if (showChevron) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = GuardColors.TextFaint)
        }
    }
}

/** Interruptor con texto: toda la fila es el control (área táctil grande, un solo foco en TalkBack). */
@Composable
fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null && !isCompact()) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) GuardColors.Text else GuardColors.TextFaint)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GuardColors.OnActive,
                checkedTrackColor = GuardColors.Active,
                uncheckedThumbColor = GuardColors.TextDim,
                uncheckedTrackColor = GuardColors.SurfaceHighest,
                uncheckedBorderColor = GuardColors.Outline,
            ),
        )
    }
}

/** Opción de una elección única, como tarjeta: se marca en verde cuando está elegida. */
@Composable
fun ChoiceCard(
    title: String,
    subtitle: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val border by animateColorAsState(if (selected) GuardColors.Active else GuardColors.OutlineSoft, tween(220), label = "choiceBorder")
    val bg by animateColorAsState(if (selected) GuardColors.ActiveContainer else GuardColors.Surface, tween(220), label = "choiceBg")
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(bg)
            .border(1.dp, border, MaterialTheme.shapes.large)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && !isCompact()) {
            Icon(icon, contentDescription = null, tint = if (selected) GuardColors.Active else GuardColors.TextDim, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim)
            }
        }
        Spacer(Modifier.width(10.dp))
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = GuardColors.Active, unselectedColor = GuardColors.TextFaint),
        )
    }
}

/** Pestañas en píldora (Todos / Personas / Grupos). */
@Composable
fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(GuardColors.Surface)
            .border(1.dp, GuardColors.OutlineSoft, CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            val bg by animateColorAsState(if (isSelected) GuardColors.SurfaceHighest else Color.Transparent, tween(200), label = "tabBg")
            val fg by animateColorAsState(if (isSelected) GuardColors.Text else GuardColors.TextDim, tween(200), label = "tabFg")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .selectable(selected = isSelected, role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Cifra del panel: número grande y qué cuenta. */
@Composable
fun StatTile(value: String, label: String, icon: ImageVector, tone: Tone, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    GuardCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
        Icon(icon, contentDescription = null, tint = tone.color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall.merge(es.carlospb.modoguardia.ui.theme.TabularNumbers), maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = GuardColors.TextDim, maxLines = 2)
    }
}

/** Pantalla o sección sin contenido: qué pasa y qué se puede hacer. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, Tone.NEUTRAL, size = 56.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = GuardColors.TextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            SecondaryButton(actionLabel, onAction)
        }
    }
}

/** Casilla redonda con animación de marcado (selección múltiple). */
@Composable
fun SelectionMark(selected: Boolean, enabled: Boolean = true) {
    val bg by animateColorAsState(
        when {
            selected && enabled -> GuardColors.Active
            selected -> GuardColors.ActiveContainer
            else -> Color.Transparent
        },
        tween(180),
        label = "markBg",
    )
    val scale by animateFloatAsState(if (selected) 1f else 0.4f, tween(180), label = "markScale")
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, if (selected) Color.Transparent else GuardColors.Outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = if (enabled) GuardColors.OnActive else GuardColors.Active,
                modifier = Modifier
                    .size(16.dp)
                    .scale(scale),
            )
        }
    }
}

/** Texto de ayuda bajo un control. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim, modifier = modifier.padding(top = 8.dp))
}

/** Separador fino entre filas de una tarjeta. */
@Composable
fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GuardColors.OutlineSoft),
    )
}

/** Texto en negrita seguido de su valor, para resúmenes. */
@Composable
fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = GuardColors.TextDim, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
