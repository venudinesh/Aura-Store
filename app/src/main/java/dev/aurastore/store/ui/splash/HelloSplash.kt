package dev.aurastore.store.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The first frame the app draws: the A of a signature, in the hand of the 1984 hello.
 *
 * Four strokes: the left leg up to the crown, the right leg down with a slight outward bow, the
 * short diagonal back up from the right foot to the left end of the bar, then the bar itself laid
 * across. The diagonal is what makes the A read a little like a star without ever closing into one.
 *
 * The paths are revealed along their own length, so the mark is written rather than faded in and the
 * round cap is the pen. The starting window behind it is the same flat background, which is why the
 * system splash is given a transparent mark: nothing should flash before the nib lands.
 *
 * Light and dark follow the app's own scheme, not the phone's: cream paper and iron ink, or the reverse.
 */
@Composable
fun HelloSplash(onDone: () -> Unit) {
    val paper = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    val warm = MaterialTheme.colorScheme.primary
    val body = remember { Path().apply { aBodyInto() } }
    val bar = remember { Path().apply { aBarInto() } }
    val bodyMeasure = remember { PathMeasure().apply { setPath(body, false) } }
    val barMeasure = remember { PathMeasure().apply { setPath(bar, false) } }
    val bodyProgress = remember { Animatable(0f) }
    val barProgress = remember { Animatable(0f) }
    var signed by remember { mutableStateOf(false) }
    val caption by animateFloatAsState(
        targetValue = if (signed) 1f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "splash-caption",
    )

    LaunchedEffect(Unit) {
        bodyProgress.animateTo(1f, tween(1040, easing = FastOutSlowInEasing))
        barProgress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        signed = true
        // Long enough for the caption to land and be read before the sheet fades: the mark is the
        // handshake, and a word that only exists during the exit is a word nobody saw.
        delay(850)
        onDone()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(paper)
            // The tour is already composed underneath while this fades; the splash owns every touch
            // until it is gone, or a stray tap would land on a page the user cannot see yet.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        // Long, not square: the letter is drawn in a 136 × 212 space (the ink itself 112 wide by
        // 180 tall) and the slot keeps that shape. The legs open wider than the first slim pass and
        // the bar sits just above the middle, so the A reads as a drawn letter, not a needle.
        val artAspect = 212f / 136f
        val markHeight = minOf(maxHeight * 0.56f, maxWidth * 0.70f * artAspect)
        val markWidth = markHeight / artAspect
        Canvas(Modifier.fillMaxSize()) {
            // A warm breath behind the mark, barely there. It keeps the flat sheet from reading as
            // an empty activity while the nib is still at the start of the line.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(warm.copy(alpha = 0.085f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.62f,
                ),
                radius = size.minDimension * 0.62f,
                center = center,
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(markWidth, markHeight)) {
                // The art already carries its own air, so the letter is drawn one-to-one in its
                // 136 × 212 space; the round cap at either foot stays well inside the slot.
                val unit = size.width / 136f
                scale(unit, unit, Offset.Zero) {
                    val gloss = Brush.linearGradient(
                        colors = listOf(ink, warm.copy(alpha = 0.86f)),
                        start = Offset(14f, 196f),
                        end = Offset(122f, 12f),
                    )
                    val leg = bodyMeasure.segment(bodyProgress.value)
                    val line = barMeasure.segment(barProgress.value)
                    // A breath under the ink rather than an outline: two strokes, the wider
                    // fainter, stand in for a blur the canvas does not have. The ink then sits
                    // on top as a fine nib, not a marker.
                    scribble(leg, brush = SolidColor(warm.copy(alpha = 0.030f)), width = 13.2f)
                    scribble(line, brush = SolidColor(warm.copy(alpha = 0.030f)), width = 13.2f)
                    scribble(leg, brush = SolidColor(warm.copy(alpha = 0.055f)), width = 9.0f)
                    scribble(line, brush = SolidColor(warm.copy(alpha = 0.055f)), width = 9.0f)
                    scribble(leg, brush = gloss, width = 4.4f)
                    scribble(line, brush = gloss, width = 4.4f)
                    tip(bodyMeasure, bodyProgress.value, warm)
                    tip(barMeasure, barProgress.value, warm)
                }
            }
            Spacer(Modifier.height(markHeight * 0.032f))
            Text(
                "AURA STORE",
                color = ink.copy(alpha = 0.62f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 5.5.sp,
                modifier = Modifier.alpha(caption),
            )
        }
    }
}

/**
 * The letter, in a 136 × 212 box: the left leg to the crown, the right leg down to the baseline,
 * then the short line back up to the left corner of the bar. The legs open to 112 units at the
 * bar, so the A is wide enough to read as a letter; the bar sits just above the middle and runs
 * past both legs, which is what makes it read as a pen stroke.
 */
private fun Path.aBodyInto() {
    moveTo(32f, 192f)
    quadraticTo(46f, 98f, 68f, 16f)
    quadraticTo(90f, 100f, 104f, 196f)
    lineTo(12f, 110f)
}

/** The crossbar, laid in after the letter is standing and running well past both feet. */
private fun Path.aBarInto() {
    moveTo(12f, 110f)
    lineTo(124f, 110f)
}

/** The visible part of [path] at [progress], or a hair of it, so the round cap has something to sit on. */
private fun PathMeasure.segment(progress: Float): Path {
    val out = Path()
    if (progress <= 0f) return out
    getSegment(0f, length * progress.coerceAtMost(1f), out, true)
    return out
}

private fun DrawScope.scribble(path: Path, brush: Brush, width: Float) {
    if (path.isEmpty) return
    drawPath(path, brush, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A glowing bead at the distance the nib has reached; gone once the line is complete. */
private fun DrawScope.tip(measure: PathMeasure, progress: Float, warm: Color) {
    if (progress <= 0.02f || progress >= 0.995f) return
    val at = measure.getPosition(measure.length * progress)
    drawCircle(warm.copy(alpha = 0.26f), radius = 7.5f, center = at)
    drawCircle(warm.copy(alpha = 0.85f), radius = 2.6f, center = at)
}
