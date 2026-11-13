package dev.aurastore.store.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.aurastore.store.R
import dev.aurastore.store.theme.Hsv
import dev.aurastore.store.theme.SoftPalettes
import dev.aurastore.store.theme.hsvToArgb
import dev.aurastore.store.theme.toHsv
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The seed chooser behind Settings → Colours → Custom colour.
 *
 * Dragging writes through to the running composition, so the storefront behind this dialog repaints
 * while the handle moves — the preview is the app, not a swatch of it. Nothing reaches disk until the
 * dialog closes: a drag is sixty samples a second and a preference file is not a canvas.
 *
 * The wheel is the plain HSV one — angle for hue, radius for saturation, the bar below for value —
 * because it is the coordinate system people already know how to drag. What it selects is a *seed*;
 * the tones the storefront is drawn in come out of [dev.aurastore.store.theme.monetScheme] afterwards,
 * which is the difference between picking a colour and picking a theme.
 */
@Composable
fun ColourPickerDialog(
    seed: Int,
    onPreview: (Int) -> Unit,
    onCommit: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var hsv by remember { mutableStateOf(seed.toHsv()) }

    fun publish(next: Hsv) {
        hsv = next
        onPreview(hsvToArgb(next))
    }

    fun finish() {
        onCommit(hsvToArgb(hsv))
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::finish,
        title = { Text(stringResource(R.string.colour_picker_title)) },
        text = {
            // The wheel and the value bar are fixed, but the title and the presets grow with the
            // text size; the scroll is what keeps the bottom of the dialog reachable at 2.0×.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PresetRow(current = hsvToArgb(hsv)) { publish(it.toHsv()) }
                ColourWheel(hsv = hsv) { hue, saturation -> publish(hsv.copy(hue = hue, saturation = saturation)) }
                ValueBar(hsv = hsv) { value -> publish(hsv.copy(value = value)) }
            }
        },
        confirmButton = {
            TextButton(onClick = ::finish) { Text(stringResource(R.string.settings_done)) }
        },
    )
}

/** The eight soft seeds, in a row that scrolls because eight swatches outrun a dialog. */
@Composable
private fun PresetRow(current: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.colour_prebuilt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SoftPalettes.all.forEach { preset ->
            val label = stringResource(preset.label)
            val selected = preset.seed == current
            Box(
                Modifier
                    .padding(start = 10.dp)
                    .size(32.dp)
                    .semantics { contentDescription = label }
                    .background(Color(preset.seed), CircleShape)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    )
                    .clickable(onClickLabel = label) { onPick(preset.seed) },
            )
        }
    }
}

/** Hue around the edge, saturation toward the middle, value as a veil over the whole disc. */
@Composable
private fun ColourWheel(hsv: Hsv, onDrag: (hue: Double, saturation: Double) -> Unit) {
    val hues = remember { (0..6).map { Color.hsv((it * 60f) % 360f, 1f, 1f) } }
    val description = stringResource(R.string.colour_wheel)
    val radius = WheelSize / 2
    val angle = hsv.hue * (PI / 180.0)
    Box(
        Modifier
            .size(WheelSize)
            .semantics { contentDescription = description }
            .pointerInput(Unit) { dragPolar(onDrag) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = min(size.width, size.height) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(brush = Brush.sweepGradient(hues, center = center), radius = r, center = center)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
            // Scaling HSV by a value is a uniform darkening, and a veil over the disc is the same thing.
            if (hsv.value < 1f) drawCircle(Color.Black.copy(alpha = 1f - hsv.value.toFloat()), r, center)
        }
        Box(
            Modifier
                .offset(
                    x = radius * (cos(angle) * hsv.saturation).toFloat(),
                    y = radius * (sin(angle) * hsv.saturation).toFloat(),
                )
                .size(20.dp)
                .background(Color.White, CircleShape)
                .border(2.dp, Color.Black.copy(alpha = 0.55f), CircleShape),
        )
    }
}

@Composable
private fun ValueBar(hsv: Hsv, onDrag: (value: Double) -> Unit) {
    val full = Color.hsv(hsv.hue.toFloat(), hsv.saturation.toFloat(), 1f)
    Column {
        Text(
            stringResource(R.string.colour_value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(
                    Brush.horizontalGradient(listOf(Color.Black, full)),
                    RoundedCornerShape(percent = 50),
                )
                .pointerInput(hsv.hue, hsv.saturation) { dragAcross(onDrag) },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * hsv.value.toFloat() - 10.dp)
                    .size(20.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, Color.Black.copy(alpha = 0.55f), CircleShape),
            )
        }
    }
}

private val WheelSize = 228.dp

/** Down-and-drag over the disc, reported as hue (degrees) and saturation (0..1). */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dragPolar(
    onDrag: (hue: Double, saturation: Double) -> Unit,
) {
    awaitEachGesture {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        fun emit(at: Offset) {
            val dx = at.x - center.x
            val dy = at.y - center.y
            onDrag(
                (atan2(dy.toDouble(), dx.toDouble()) * (180.0 / PI) + 360.0) % 360.0,
                (hypot(dx, dy) / radius).coerceIn(0f, 1f).toDouble(),
            )
        }
        awaitFirstDown().also { emit(it.position) }
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull() ?: break
            if (!change.pressed) break
            emit(change.position)
            change.consume()
        }
    }
}

/** The same gesture across a bar, reported as a fraction of its width. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dragAcross(onDrag: (value: Double) -> Unit) {
    awaitEachGesture {
        fun emit(x: Float) = onDrag((x / size.width).coerceIn(0f, 1f).toDouble())
        awaitFirstDown().also { emit(it.position.x) }
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull() ?: break
            if (!change.pressed) break
            emit(change.position.x)
            change.consume()
        }
    }
}
