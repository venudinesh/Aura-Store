package dev.aurastore.store.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aurastore.store.R
import dev.aurastore.store.data.GlassStyle
import dev.aurastore.store.theme.EmphasizedEasing
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardAccelerateEasing
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.NAV_TABS
import dev.aurastore.store.ui.playClickable
import kotlin.math.roundToInt

/**
 * The controls Settings is made of, in one file.
 *
 * The hub and its six category pages are one screen's worth of behaviour split across routes; the
 * row, the switch, the chooser and the glass slider are the same objects on every one of them. They
 * lived inside the single screen while it was one form; the category pages draw them now, so they
 * live here rather than being copied six times.
 */

/**
 * A settings row: title, a line of state, and a value on the right when the state has a name.
 *
 * Each row is its own soft tile. The page is a column of them with air between, not one long card
 * chopped up by hairlines: a setting is a thing, so it looks like a thing. A row whose title and
 * value both want the whole width stacks past 1.4x text: measured on the Redmi at 2.0x, *Auto-update
 * apps* beside *Don't auto-update apps* left the title a 176 px column.
 */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String?,
    value: String?,
    onClick: (() -> Unit)?,
) {
    val stacked = LocalDensity.current.fontScale > 1.4f

    SettingsTile(onClick = onClick) {
        if (stacked) {
            Column(Modifier.fillMaxWidth()) {
                SettingsTitle(title, subtitle)
                if (value != null) {
                    Spacer(Modifier.height(8.dp))
                    AnimatedContent(
                        targetState = value,
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = { settingValueTransition() },
                        label = "setting-value",
                    ) { text ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) { SettingValue(text) }
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    SettingsTitle(title, subtitle)
                }
                if (value != null) {
                    Spacer(Modifier.width(10.dp))
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = { settingValueTransition() },
                        label = "setting-value",
                    ) { text -> SettingValue(text) }
                }
                if (onClick != null) {
                    Spacer(Modifier.width(4.dp))
                    GlyphIcon(
                        Glyph.ChevronRight,
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** The value as a small capsule, so it reads as state rather than as another line of the title. */
@Composable
private fun SettingValue(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** The title and its note, which both layouts draw the same way. */
@Composable
private fun SettingsTitle(title: String, subtitle: String?) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    if (!subtitle.isNullOrBlank()) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The cross-fade a value uses when it changes, shared by both row layouts. */
private fun settingValueTransition(): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedEasing)),
    initialContentExit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)),
)

/**
 * One soft tile on the page: the shape every setting stands in.
 *
 * The page owns the gaps; a tile only owns its own surface. That is what keeps a category from
 * reading as a card full of little cards.
 */
@Composable
internal fun SettingsTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val base = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = if (onClick != null) base.clip(shape).playClickable(onClick = onClick) else base,
    ) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

/** A preference with one switch, so it can be changed without leaving the page. */
@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    tile: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val toggle = { onCheckedChange(!checked) }
    if (tile) {
        SettingsTile(onClick = toggle, modifier = modifier) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SwitchContent(title, subtitle, checked, onCheckedChange)
            }
        }
    } else {
        Row(
            modifier
                .fillMaxWidth()
                .playClickable(onClick = toggle)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwitchContent(title, subtitle, checked, onCheckedChange)
        }
    }
}

@Composable
private fun RowScope.SwitchContent(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.width(12.dp))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
}

/**
 * A radio list for one persisted enum.
 *
 * Three settings rows need exactly this, and the auto-update chooser had grown its own copy of the
 * whole dialog before it was folded in here.
 */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    noteOf: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // Three options with a note each are taller than the dialog at a 2.0x text size; the
            // scroll keeps the last option selectable instead of clipped by the dialog's edge.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(option) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(labelOf(option), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                noteOf(option),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
    )
}

/**
 * A panel of related controls: a heading on the page, one surface under it.
 *
 * The heading sits outside the surface on purpose. A card that starts with its own title inside
 * itself is a box around a box; this one belongs to the page.
 */
@Composable
internal fun SettingsCard(
    title: String? = null,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp)) {
        if (title != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .width(3.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.0.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (note != null) {
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** A sub-heading inside a panel: GLASS EFFECTS, SHAPE & LAYOUT. */
@Composable
internal fun SettingsCardLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.0.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** A row that ends a panel: a title and the one action it offers, on the panel's own padding. */
@Composable
internal fun SettingsCardRow(
    title: String,
    value: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .playClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SettingsTitle(title, subtitle)
        }
        Spacer(Modifier.width(10.dp))
        SettingValue(value)
        Spacer(Modifier.width(4.dp))
        GlyphIcon(
            Glyph.ChevronRight,
            size = 16.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/**
 * A door on the Settings hub: a glyph, the category, and the state of everything behind it.
 *
 * The line under the title is the reason the hub is worth visiting before the category: it answers
 * "what is it set to" without opening anything.
 */
@Composable
internal fun CategoryRow(
    title: String,
    note: String,
    glyph: Glyph,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .playClickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        GlyphIcon(
            Glyph.ChevronRight,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One slider and the field of [GlassStyle] it writes.
 *
 * [display] exists because not every fraction means its own percentage: Bar width maps 0..1 onto
 * 60..150 % of the content width, and printing the raw fraction there would say the bar is 0 % wide.
 */
internal class GlassKnob(
    @StringRes val label: Int,
    val value: Float,
    val display: (Float) -> Int = { (it * 100).roundToInt() },
    val set: (GlassStyle, Float) -> GlassStyle,
)

/**
 * One glass parameter, dragged with the bar on screen reacting as it moves.
 *
 * Every parameter is a 0..1 fraction of its own range, so the four rows are one shape and the same
 * numbers mean the same look at any size.
 */
@Composable
internal fun GlassSliderRow(
    title: String,
    value: Float,
    display: String,
    onDrag: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                display,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        SlimSlider(value = value, description = title, onDrag = onDrag, onDone = onDone)
    }
}

/**
 * The slider the glass rows use.
 *
 * Material's slider draws a 40 dp handle, which beside four lines of 14 sp labels dominated the card
 * and made the four controls read as one tall column of bars. This is a 6 dp track with an 18 dp
 * handle: still a full-height drag target, but the card's rhythm belongs to the labels again.
 */
@Composable
private fun SlimSlider(
    value: Float,
    description: String,
    onDrag: (Float) -> Unit,
    onDone: () -> Unit,
) {
    var width by remember { mutableIntStateOf(0) }
    val fraction = value.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val active = MaterialTheme.colorScheme.primary
    val halo = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { width = it.width }
            .pointerInput(width) {
                if (width <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onDrag((offset.x / width).coerceIn(0f, 1f)) },
                    onDragEnd = onDone,
                    onDragCancel = onDone,
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onDrag((change.position.x / width).coerceIn(0f, 1f))
                    },
                )
            }
            .pointerInput(width) {
                if (width <= 0) return@pointerInput
                detectTapGestures { offset ->
                    onDrag((offset.x / width).coerceIn(0f, 1f))
                    onDone()
                }
            }
            // The visible title is a sibling, not a label, so a screen reader reaches the track with
            // no idea which parameter it is holding.
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackHeight = 6.dp.toPx()
            val centerY = size.height / 2f
            val radius = CornerRadius(trackHeight / 2f)
            drawRoundRect(
                color = track,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = radius,
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = active,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width * fraction, trackHeight),
                    cornerRadius = radius,
                )
            }
            val handle = size.width * fraction
            drawCircle(halo, radius = 11.dp.toPx(), center = Offset(handle, centerY))
            drawCircle(active, radius = 8.dp.toPx(), center = Offset(handle, centerY))
        }
    }
}

/**
 * The five tabs, in their current order, dragged sideways to move one.
 *
 * Deliberately not a live-shifting list: the chip follows the finger, and on release the strip is
 * permuted. A drag that ends between two slots resolves to the nearest one, clamped to the row, so
 * a finger that leaves the five at either end cannot ask for a sixth tab.
 */
@Composable
internal fun NavOrderRow(order: List<Int>, onMove: (from: Int, to: Int) -> Unit) {
    var width by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val cell = if (order.isEmpty() || width == 0) 0f else width / order.size.toFloat()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .onSizeChanged { width = it.width },
    ) {
        order.forEachIndexed { position, screen ->
            val lifted = dragging == position
            Box(
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = if (lifted) dragX else 0f
                        val scale = if (lifted) 1.1f else 1f
                        scaleX = scale
                        scaleY = scale
                    }
                    // `cell` is in the key: it is 0 until the row is measured, and a lambda
                    // captured before that would never reorder anything.
                    .pointerInput(order, cell) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragging = position
                                dragX = 0f
                            },
                            onDragEnd = {
                                if (cell > 0f) {
                                    val target = (position + (dragX / cell).roundToInt())
                                        .coerceIn(0, order.lastIndex)
                                    if (target != position) onMove(position, target)
                                }
                                dragging = null
                                dragX = 0f
                            },
                            onDragCancel = {
                                dragging = null
                                dragX = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragX += amount.x
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                if (lifted) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(
                            NAV_TABS[screen].glyph,
                            size = 22.dp,
                            tint = if (lifted) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(NAV_TABS[screen].label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** A one-value line inside the glass card, for the running engine and its label. */
@Composable
internal fun SettingsChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** The engine rows: one radio each, with the chosen engine wearing the panel's own light. */
@Composable
internal fun EngineOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else Color.Transparent,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
