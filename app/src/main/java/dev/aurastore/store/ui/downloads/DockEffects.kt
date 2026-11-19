package dev.aurastore.store.ui.downloads

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import dev.aurastore.store.data.DockAnimation
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.theme.PlayMotion
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The meters the floating download panel can wear.
 *
 * Every style floods the card from the leading edge to the byte front, the way the panel has always
 * filled: a landscape sweep, full height, with the name and the rate sitting above the paint. The
 * standard twelve stay in the theme's colours; the limited editions bring their own light — a ki
 * beam, a curling wave, fire, a boulder, a bullet's spiral, an arrow, a laser, lightning, a counter,
 * a speedster, a smoking tire and a car on nitrous. Each limited edition leads with an actor measured
 * from the card's height, unclipped at the byte front: a tire and a boulder that touch both edges, a
 * fixed half-circle blast, a car with its roof near the top and its wheels at the bottom.
 *
 * The motion is physical rather than decorative. The head is spring-driven by the caller and the
 * effects are built from things that carry momentum: ballistic embers, falling drops under gravity,
 * buoyant bubbles, a damped liquid meniscus, spinning wheels, a boulder that grinds forward.
 * [phase] is a 0..1 loop; the caller freezes it when the bytes stop or the phone has motion off, and
 * a paused or finished transfer keeps the fill without the movement.
 *
 * The drawings are deliberately quiet near the text: the name, the percentage and the sentence sit
 * on top of them, and a meter that wins the contrast fight makes those lines unreadable.
 */
@Composable
internal fun DockProgressLayer(
    animation: DockAnimation,
    fraction: Float,
    state: DownloadState,
    done: Boolean,
    phase: Float,
    modifier: Modifier = Modifier,
    indeterminate: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val tone = remember(state, done) { dockFillTone(state, done) }
    val moving = state == DownloadState.DOWNLOADING || state == DownloadState.VERIFYING
    val live = moving && PlayMotion.enabled
    // The limited editions get their own light. The shader paints the actor, the trail and the
    // weather; the wash behind it is tinted to the same family so the card reads as one scene
    // rather than a bronze card with a picture dropped on it.
    val styleTint = remember(animation) { dockTint(animation) }
    val limitedShader = rememberDockLimitedShader(animation)
    val shader = if (done) null else limitedShader
    val shaderPaint = remember {
        Paint().apply {
            isAntiAlias = true
            isDither = true
        }
    }
    // Both gradients are pictures, not per-frame state. The wash is two colours across the card and
    // the shine is one white band; the band moves by translating the canvas, not by rebuilding the
    // gradient with a new start and end sixty times a second.
    val wash = remember(styleTint, tone, scheme.primary, scheme.tertiary, scheme.onSurfaceVariant) {
        Brush.horizontalGradient(
            when {
                styleTint != null && !tone.muted && !tone.solid -> listOf(
                    styleTint.copy(alpha = 0.26f),
                    styleTint.copy(alpha = 0.12f),
                )
                tone.muted -> listOf(
                    scheme.onSurfaceVariant.copy(alpha = tone.start),
                    scheme.onSurfaceVariant.copy(alpha = tone.end),
                )
                tone.solid -> listOf(
                    scheme.primary.copy(alpha = tone.start),
                    scheme.primary.copy(alpha = tone.end),
                )
                else -> listOf(
                    scheme.primary.copy(alpha = tone.start),
                    scheme.tertiary.copy(alpha = tone.end),
                )
            },
        )
    }
    val sheen = remember {
        Brush.horizontalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
        )
    }
    val track = scheme.surfaceVariant.copy(alpha = tone.track)
    Canvas(modifier) {
        drawDockMeter(
            animation = animation,
            fraction = fraction,
            tone = tone,
            phase = if (live) phase else 0f,
            live = live,
            indeterminate = indeterminate,
            primary = scheme.primary,
            tertiary = scheme.tertiary,
            outline = scheme.outline,
            trackColor = track,
            wash = wash,
            sheen = sheen,
            shader = shader,
            shaderPaint = shaderPaint,
        )
    }
}

/**
 * One frame of the panel's meter.
 *
 * Kept as a plain [DrawScope] function rather than a composable so the frame can be reasoned about
 * without a phone: the track is always laid down, the fill is clipped to [dockFillWidth], and the
 * edge is drawn last so no effect can paint over the one line that says where the transfer stands.
 */
internal fun DrawScope.drawDockMeter(
    animation: DockAnimation,
    fraction: Float,
    tone: DockFillTone,
    phase: Float,
    live: Boolean,
    primary: Color,
    tertiary: Color,
    outline: Color,
    trackColor: Color,
    wash: Brush,
    sheen: Brush,
    shader: RuntimeShader? = null,
    shaderPaint: Paint? = null,
    indeterminate: Boolean = false,
) {
    val filled = dockFillWidth(size.width, fraction)
    // Always laid down, even at 0 %: a queued row still shows where the fill will arrive.
    drawRect(color = trackColor, size = size)
    // A transfer whose host published no size has no fraction to draw. Rather than a card that sits
    // at 0 % for the whole file, the track carries a band travelling with the bytes.
    if (indeterminate) {
        if (live) dockIndeterminateSweep(phase, tertiary)
        return
    }
    if (filled <= 0f) return
    // The liquid fronts are the edge themselves: their crests need a few dp past the byte position
    // or the clip shaves them back into the straight line every other style draws.
    val waveFront = live &&
        (animation == DockAnimation.TIDE || animation == DockAnimation.TSUNAMI)
    // Every other live style ends in a slow meniscus rather than a ruler-straight cut, so the paint
    // reads as a body of liquid crossing the whole card instead of a rectangle clipped at a side.
    val front = if (live && !waveFront) liquidFrontPath(filled, phase) else null
    // A limited edition brings its own weather. Where the runtime shader is available it owns that
    // weather end to end; the older Canvas trails stay for builds below API 33.
    val tint = dockTint(animation)
    val shaderActive = shader != null && shaderPaint != null && tint != null
    val paint: DrawScope.() -> Unit = {
        drawRect(brush = wash, size = size)
        // Only the fallback needs the extra tint: on a shader build the wash itself is tinted.
        if (tint != null && !shaderActive) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(tint.copy(alpha = 0.18f), tint.copy(alpha = 0.08f)),
                    startX = 0f,
                    endX = filled,
                ),
                size = size,
            )
        }
        // The body of the fill, present for every style: a lit bed, a band of light crossing the
        // full card and a soft column of colour behind the head. This is the part that floods the
        // panel rather than tracing its edge, and it drifts with [phase], so it never sits still.
        liquidBody(phase, filled, tint ?: tertiary, sheen)
        if (live) {
            when (animation) {
                DockAnimation.WASH -> washOverlay(phase, filled, sheen, tertiary)
                DockAnimation.TIDE -> tideOverlay(phase, filled, tertiary)
                DockAnimation.AURORA -> auroraOverlay(phase, primary, tertiary)
                DockAnimation.SCAN -> scanOverlay(phase, filled, tertiary)
                DockAnimation.COMET -> cometOverlay(phase, filled, primary, tertiary)
                DockAnimation.GLOW -> glowOverlay(phase, filled, primary, tertiary)
                DockAnimation.RAIN -> rainOverlay(phase, filled, tertiary)
                DockAnimation.BUBBLES -> bubbleOverlay(phase, filled, tertiary)
                // Neon draws on the card's rim, outside the fill: the edge of the region would cut
                // the one part of the effect that makes it neon. It is drawn after the edge below.
                DockAnimation.NEON -> Unit
                DockAnimation.PULSE -> pulseOverlay(phase, filled, tertiary)
                DockAnimation.MATRIX -> matrixOverlay(phase, filled, tertiary)
                DockAnimation.CHASE -> chaseOverlay(phase, filled, tertiary)
                // The limited editions split in two: the weather they leave behind is clipped to
                // the fill here, and the actor that leads the head is drawn full height after the
                // edge, below. A tire the size of a fingertip inside the wash is a texture; a tire
                // spanning the card and rolling with the bytes is the style. The shader builds
                // draw all of that in one pass, so their Canvas trails are skipped.
                DockAnimation.KAMEHAMEHA -> if (!shaderActive) kamehamehaTrail(phase, filled)
                DockAnimation.TSUNAMI -> if (!shaderActive) tsunamiTrail(phase, filled)
                DockAnimation.FIREBALL -> if (!shaderActive) fireballTrail(phase, filled)
                DockAnimation.BOULDER -> if (!shaderActive) boulderTrail(phase, filled)
                DockAnimation.BULLET -> if (!shaderActive) bulletTrail(phase, filled)
                DockAnimation.ARROW -> if (!shaderActive) arrowTrail(phase, filled)
                DockAnimation.LASER -> if (!shaderActive) laserTrail(phase, filled)
                DockAnimation.THUNDER -> if (!shaderActive) thunderTrail(phase, filled)
                DockAnimation.COUNTING -> if (!shaderActive) countingTrail(filled)
                DockAnimation.FLASH -> if (!shaderActive) flashTrail(phase, filled)
                DockAnimation.WHEEL -> if (!shaderActive) wheelTrail(phase, filled)
                DockAnimation.NOS -> if (!shaderActive) nosTrail(phase, filled)
            }
        }
        // A soft bed under the type. The head and its actors stay bright at the right, while the
        // words keep a darker ground no matter which style is painting behind them.
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.12f),
                    Color.Black.copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                startX = 0f,
                endX = size.width * 0.86f,
            ),
            size = size,
        )
    }
    // A shader-limited card is painted by the shader alone. The Compose wash underneath clipped
    // at the byte front, and that vertical cut was the line the actor was supposed to replace. The
    // shader lays its own feathered body and stops with the actor, so nothing draws a rule here.
    if (!shaderActive) {
        if (front != null) {
            clipPath(front, block = paint)
        } else {
            clipRect(
                right = if (waveFront) (filled + 6.dp.toPx()).coerceAtMost(size.width) else filled,
                block = paint,
            )
        }
        // The edge rule belongs to the standard meters. A limited edition is led by its actor, and a
        // full-height hairline in front of the tire, the car or the wave reads as a progress bar again.
        if (!waveFront && dockTint(animation) == null) {
            val edge = if (tone.solid) primary else if (tone.muted) outline else tertiary
            if (front != null) {
                val crest = liquidEdgePath(filled, phase)
                drawPath(crest, edge.copy(alpha = 0.18f), style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
                drawPath(crest, edge, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
            } else {
                drawRect(
                    color = edge,
                    topLeft = Offset(filled - 0.75.dp.toPx(), 0f),
                    size = Size(1.5.dp.toPx(), size.height),
                )
            }
        }
    }
    if (live && animation == DockAnimation.NEON) neonOverlay(phase, primary, tertiary)
    if (shaderActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        drawDockShader(shader, shaderPaint, animation, fraction, if (live) phase else 0f, live)
        drawDockTypeScrim()
    } else if (live) {
        // The leading form of a limited edition, at the byte front and unclipped. It is sized from
        // the card's height, so it owns the whole panel rather than sitting in a lane on it. Text
        // is drawn over this layer by the caller, and the dark bed above keeps the two lines
        // readable through it.
        limitedHead(animation, filled, phase, fraction)
        if (dockTint(animation) != null) drawDockTypeScrim()
    }
}

/**
 * Where the indeterminate sweep begins for [phase] on a card [width] px wide, with a band [band] px
 * wide. It enters from off the leading edge and leaves past the trailing one, so the loop has no
 * visible jump; the same arithmetic is pinned by the meter's tests.
 */
internal fun dockSweepStart(width: Float, band: Float, phase: Float): Float {
    if (width <= 0f || band <= 0f || phase.isNaN()) return 0f
    return (width + band) * phase.coerceIn(0f, 1f) - band
}

/**
 * The bar to draw when the total is unknown: one soft band crossing the track. It says the bytes are
 * moving without claiming a fraction of a size nobody published.
 */
private fun DrawScope.dockIndeterminateSweep(phase: Float, ink: Color) {
    val band = size.width * 0.42f
    val start = dockSweepStart(size.width, band, phase)
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, ink.copy(alpha = 0.30f), Color.Transparent),
            startX = start,
            endX = start + band,
        ),
        topLeft = Offset(start, 0f),
        size = Size(band, size.height),
    )
}

/**
 * The runtime shader pass for a limited edition.
 *
 * Uniforms are pushed per frame; [Paint] is reused rather than rebuilt, because the panel redraws
 * every frame while bytes move. The shader paints the whole card and returns nothing outside its
 * own fill mask, so no clip is needed around this call.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawDockShader(
    shader: RuntimeShader,
    paint: Paint,
    animation: DockAnimation,
    fraction: Float,
    phase: Float,
    live: Boolean,
) {
    val style = animation.shaderStyle ?: return
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("fill", fraction)
    shader.setFloatUniform("phase", phase)
    shader.setFloatUniform("style", style.toFloat())
    shader.setFloatUniform("percent", fraction * 100f)
    shader.setFloatUniform("live", if (live) 1f else 0f)
    paint.shader = shader
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
    paint.shader = null
}

/** The dark bed the two lines of type sit on, drawn after the actor rather than under it. */
private fun DrawScope.drawDockTypeScrim() {
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(
                Color.Black.copy(alpha = 0.32f),
                Color.Black.copy(alpha = 0.18f),
                Color.Transparent,
            ),
            startX = 0f,
            endX = size.width * 0.84f,
        ),
        size = size,
    )
}

/** The weather a limited edition paints over the theme wash, or null for the standard twelve. */
private fun dockTint(animation: DockAnimation): Color? = when (animation) {
    DockAnimation.KAMEHAMEHA -> KiCyan
    DockAnimation.TSUNAMI -> WaveBlue
    DockAnimation.FIREBALL -> FireOrange
    DockAnimation.BOULDER -> Dust
    DockAnimation.BULLET -> Brass
    DockAnimation.ARROW -> Brass
    DockAnimation.LASER -> LaserRed
    DockAnimation.THUNDER -> SpeedYellow
    DockAnimation.COUNTING -> CounterTeal
    DockAnimation.FLASH -> SpeedYellow
    DockAnimation.WHEEL -> SmokeGrey
    DockAnimation.NOS -> NosBlue
    else -> null
}

/**
 * The region the fill covers when the head is at [filled]: the full card height, with a slow wave
 * down the leading edge. A straight rectangle reads as a crop; a meniscus reads as liquid arriving.
 */
private fun DrawScope.liquidFrontPath(filled: Float, phase: Float): Path {
    val h = size.height
    val steps = 24
    val amp = 0.9.dp.toPx()
    val turns = 2f * PI.toFloat()
    val path = Path()
    path.moveTo(0f, 0f)
    path.lineTo(0f, h)
    for (i in steps downTo 0) {
        val y = h * i / steps
        val x = filled + sin((y / h) * turns * 1.2f + phase * turns) * amp
        path.lineTo(x.coerceIn(0f, size.width), y)
    }
    path.close()
    return path
}

/** The same wave as [liquidFrontPath], open, so the edge can be stroked rather than filled. */
private fun DrawScope.liquidEdgePath(filled: Float, phase: Float): Path {
    val h = size.height
    val steps = 24
    val amp = 0.9.dp.toPx()
    val turns = 2f * PI.toFloat()
    val path = Path()
    for (i in 0..steps) {
        val y = h * i / steps
        val x = filled + sin((y / h) * turns * 1.2f + phase * turns) * amp
        val px = x.coerceIn(0f, size.width)
        if (i == 0) path.moveTo(px, y) else path.lineTo(px, y)
    }
    return path
}

/**
 * The body of the fill: a lit bed, a wide band of light crossing the whole card and a column of the
 * style's own colour behind the head. Keep it under the actors; it is depth, not another drawing.
 */
private fun DrawScope.liquidBody(phase: Float, filled: Float, ink: Color, sheen: Brush) {
    val h = size.height
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Black.copy(alpha = 0.05f)),
        ),
        size = size,
    )
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, ink.copy(alpha = 0.10f)),
            startY = 0f,
            endY = h,
        ),
        size = size,
    )
    // One broad band of light travels the whole card, half the speed of the actors.
    val band = size.width * 0.55f
    val start = (size.width + band) * ((phase * 0.6f) % 1f) - band
    translate(left = start) {
        drawRect(brush = sheen, size = Size(band, h))
    }
    // The water behind the head is deeper than the water at the tap.
    val glow = 26.dp.toPx().coerceAtMost(filled)
    if (glow > 1f) {
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, ink.copy(alpha = 0.16f)),
                startX = filled - glow,
                endX = filled,
            ),
            topLeft = Offset(filled - glow, 0f),
            size = Size(glow, h),
        )
    }
}

// Limited-edition paints. These are fixed on purpose: a ki beam is not theme-coloured, and the
// point of the extra set is that a pick changes the light on the card.
private val KiCyan = Color(0xFF4DD8FF)
private val KiWhite = Color(0xFFEAFDFF)
private val WaveBlue = Color(0xFF2E9BFF)
private val WaveFoam = Color(0xFFE2F6FF)
private val FireOrange = Color(0xFFFF7A18)
private val FireGold = Color(0xFFFFD166)
private val FireRed = Color(0xFFE63946)
private val RockGrey = Color(0xFF9A948C)
private val RockDark = Color(0xFF4B4640)
private val Dust = Color(0xFFC7BEB0)
private val Brass = Color(0xFFFFC857)
private val LaserRed = Color(0xFFFF3355)
private val BoltWhite = Color(0xFFF2F6FF)
private val CounterTeal = Color(0xFF57F2D2)
private val SpeedYellow = Color(0xFFFFE066)
private val SpeedRed = Color(0xFFFF5722)
private val SmokeGrey = Color(0xFFC9C6C2)
private val NosBlue = Color(0xFF39C5FF)
private val NosWhite = Color(0xFFDFF6FF)

// --- The standard twelve -----------------------------------------------------------------------

/** The shipped look: a band of light crosses the fill while three embers climb the byte front. */
private fun DrawScope.washOverlay(phase: Float, filled: Float, sheen: Brush, tertiary: Color) {
    val band = size.width * 0.35f
    val start = (size.width + band) * phase - band
    translate(left = start) {
        drawRect(brush = sheen, size = Size(band, size.height))
    }
    val spark = 1.7.dp.toPx()
    for (i in 0 until 3) {
        val p = (phase + i / 3f) % 1f
        drawCircle(
            color = tertiary.copy(alpha = (1f - p) * 0.55f),
            radius = spark * (0.55f + p * 0.75f),
            center = Offset(
                x = filled - 5.dp.toPx() - p * 7.dp.toPx(),
                y = size.height * (1f - p),
            ),
        )
    }
}

/** A liquid front: the leading edge rolls in one slow wave, with ripples running back through it. */
private fun DrawScope.tideOverlay(phase: Float, filled: Float, tertiary: Color) {
    val h = size.height
    val amp = 4.dp.toPx()
    val back = 18.dp.toPx().coerceAtMost(filled)
    val turns = 2f * PI.toFloat()
    val crest = Path()
    val steps = 18
    crest.moveTo(filled, 0f)
    for (i in 0..steps) {
        val y = h * i / steps
        val x = filled + sin((y / h) * turns * 1.5f + phase * turns) * amp
        crest.lineTo(x, y)
    }
    val body = Path().apply {
        addPath(crest)
        lineTo(filled - back, h)
        lineTo(filled - back, 0f)
        close()
    }
    drawPath(body, tertiary.copy(alpha = 0.24f))
    // The crest is the edge for this style; without the line the fill reads as a soft tint rather
    // than as a place the bytes have reached.
    drawPath(crest, tertiary.copy(alpha = 0.5f), style = Stroke(1.5.dp.toPx()))
    for (i in 0 until 3) {
        val p = (phase + i / 3f) % 1f
        val y = h * p
        drawLine(
            color = tertiary.copy(alpha = 0.14f),
            start = Offset(0f, y),
            end = Offset((filled - back).coerceAtLeast(0f), y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/** Ribbons of colour drifting through the filled part, like light under ice. */
private fun DrawScope.auroraOverlay(phase: Float, primary: Color, tertiary: Color) {
    val turns = 2f * PI.toFloat()
    val colors = listOf(primary, tertiary, primary)
    for (i in 0 until 3) {
        val cx = size.width * (0.22f + 0.26f * i) + sin(phase * turns + i * 1.7f) * 24.dp.toPx()
        val cy = size.height * (0.5f + 0.34f * sin(phase * turns * 0.7f + i * 2.1f))
        val radius = size.width * (0.30f + 0.08f * i)
        val center = Offset(cx, cy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors[i].copy(alpha = 0.30f), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

/** A circuit grid under a bright band that sweeps the transfer left to right. */
private fun DrawScope.scanOverlay(phase: Float, filled: Float, tertiary: Color) {
    val column = 22.dp.toPx()
    var x = column
    while (x < filled) {
        drawLine(
            color = tertiary.copy(alpha = 0.10f),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        x += column
    }
    val row = size.height / 3f
    for (i in 1..2) {
        val y = row * i
        drawLine(
            color = tertiary.copy(alpha = 0.12f),
            start = Offset(0f, y),
            end = Offset(filled, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
    val band = 16.dp.toPx()
    val bx = (filled + band) * phase - band
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, tertiary.copy(alpha = 0.30f), Color.Transparent),
            startX = bx,
            endX = bx + band,
        ),
        topLeft = Offset(bx, 0f),
        size = Size(band, size.height),
    )
}

/** A small comet riding the byte front, its tail streaming back over what has landed. */
private fun DrawScope.cometOverlay(phase: Float, filled: Float, primary: Color, tertiary: Color) {
    // The comet crosses the body of the card, its tail long enough to read across the fill.
    val headY = size.height * (0.46f + 0.10f * sin(phase * 2f * PI.toFloat()))
    val headX = (filled - 6.dp.toPx()).coerceAtLeast(0f)
    val toss = 64.dp.toPx()
    val tailStart = (headX - toss).coerceAtLeast(0f)
    if (headX - tailStart > 1f) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, tertiary.copy(alpha = 0.40f)),
                startX = tailStart,
                endX = headX,
            ),
            topLeft = Offset(tailStart, headY - 2.5.dp.toPx()),
            size = Size(headX - tailStart, 5.dp.toPx()),
        )
    }
    val head = Offset(headX, headY)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.9f), primary.copy(alpha = 0f)),
            center = head,
            radius = 8.dp.toPx(),
        ),
        radius = 8.dp.toPx(),
        center = head,
    )
}

/** A warm orb of light bobbing at the edge, as if the bytes were still arriving under a lamp. */
private fun DrawScope.glowOverlay(phase: Float, filled: Float, primary: Color, tertiary: Color) {
    val center = Offset(
        x = (filled - 7.dp.toPx()).coerceAtLeast(0f),
        y = size.height * (0.50f + 0.12f * sin(phase * 2f * PI.toFloat())),
    )
    val radius = 30.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                tertiary.copy(alpha = 0.50f),
                primary.copy(alpha = 0.16f),
                Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    drawCircle(Color.White.copy(alpha = 0.75f), 2.4.dp.toPx(), center)
}

/** Rain on a window: fine streaks falling through the part already downloaded. */
private fun DrawScope.rainOverlay(phase: Float, filled: Float, tertiary: Color) {
    val drops = 9
    for (i in 0 until drops) {
        val seed = hashUnit(i * 37)
        val other = hashUnit(i * 37 + 11)
        val x = seed * filled
        val length = (8.dp.toPx() + other * 10.dp.toPx())
        val y = size.height * ((phase * 1.4f + other) % 1f) - length
        drawLine(
            color = tertiary.copy(alpha = 0.24f),
            start = Offset(x, y),
            end = Offset(x - 2.dp.toPx(), y + length),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/** Rings rising through the transfer, gathering near the top as they lose their speed. */
private fun DrawScope.bubbleOverlay(phase: Float, filled: Float, tertiary: Color) {
    val bubbles = 7
    for (i in 0 until bubbles) {
        val seed = hashUnit(i * 13)
        val p = (phase + seed) % 1f
        val x = filled * (0.08f + 0.84f * hashUnit(i * 29))
        val y = size.height * (1.05f - p * 1.1f)
        val radius = (2f + 2.5f * hashUnit(i * 7 + 3)) * 1.dp.toPx()
        drawCircle(
            color = tertiary.copy(alpha = (0.14f + p * 0.14f).coerceAtMost(0.32f)),
            radius = radius,
            center = Offset(x, y),
            style = Stroke(1.dp.toPx()),
        )
    }
}

/** A glowing line racing around the card's rim, one lap per cycle of the meter. */
private fun DrawScope.neonOverlay(phase: Float, primary: Color, tertiary: Color) {
    val inset = 1.dp.toPx()
    val rim = Path().apply {
        addRoundRect(
            RoundRect(
                left = inset,
                top = inset,
                right = size.width - inset,
                bottom = size.height - inset,
                cornerRadius = CornerRadius(22.dp.toPx()),
            ),
        )
    }
    drawPath(rim, primary.copy(alpha = 0.14f), style = Stroke(1.dp.toPx()))
    val measure = PathMeasure()
    measure.setPath(rim, false)
    val length = measure.length
    if (length <= 0f) return
    val headDistance = length * phase
    val trail = length * 0.18f
    for (i in 1..10) {
        val distance = headDistance - trail * (i / 10f)
        val wrapped = ((distance % length) + length) % length
        drawCircle(
            color = tertiary.copy(alpha = 0.38f * (1f - i / 11f)),
            radius = 1.8.dp.toPx(),
            center = measure.getPosition(wrapped),
        )
    }
    val head = measure.getPosition(headDistance)
    val radius = 9.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(tertiary.copy(alpha = 0.55f), Color.Transparent),
            center = head,
            radius = radius,
        ),
        radius = radius,
        center = head,
    )
    drawCircle(Color.White.copy(alpha = 0.55f), 1.4.dp.toPx(), head)
}

/** Sonar rings leaving the byte front, each fading as it spreads. */
private fun DrawScope.pulseOverlay(phase: Float, filled: Float, tertiary: Color) {
    val center = Offset(x = (filled - 3.dp.toPx()).coerceAtLeast(0f), y = size.height * 0.50f)
    for (i in 0 until 3) {
        val p = (phase + i / 3f) % 1f
        drawCircle(
            color = tertiary.copy(alpha = (1f - p) * 0.38f),
            radius = (10.dp.toPx() + 26.dp.toPx() * p),
            center = center,
            style = Stroke(1.dp.toPx()),
        )
    }
}

/** Columns of falling dashes, like a terminal that is actually downloading something. */
private fun DrawScope.matrixOverlay(phase: Float, filled: Float, tertiary: Color) {
    val columns = 10
    val dash = 4.dp.toPx()
    val gap = 6.dp.toPx()
    for (i in 0 until columns) {
        val seed = hashUnit(i * 41 + 7)
        val x = ((i + 0.5f) / columns) * filled
        val head = size.height * ((phase * 1.6f + seed) % 1.3f) - size.height * 0.15f
        for (j in 0 until 4) {
            val y = head - j * gap
            if (y < -gap || y > size.height) continue
            val alpha = (0.30f - j * 0.06f).coerceAtLeast(0f)
            drawRect(
                color = tertiary.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(1.6.dp.toPx(), dash),
            )
        }
    }
}

/** Chevrons marching from left to right along the transfer. */
private fun DrawScope.chaseOverlay(phase: Float, filled: Float, tertiary: Color) {
    val step = 20.dp.toPx()
    val y = size.height / 2f
    val h = 4.dp.toPx()
    var x = -step + phase * step
    while (x < filled) {
        if (x > 0f) {
            val chevron = Path().apply {
                moveTo(x, y - h)
                lineTo(x + h, y)
                lineTo(x, y + h)
            }
            drawPath(chevron, tertiary.copy(alpha = 0.30f), style = Stroke(1.4.dp.toPx()))
        }
        x += step
    }
}

// --- The limited editions ----------------------------------------------------------------------

/**
 * The leading form of a limited edition, drawn at the byte front after the fill is closed.
 *
 * Each actor is measured from the card's height rather than from a fixed dp size: the wheel and the
 * boulder are circles whose rims touch the top and bottom of the panel, the ki blast and the wave are
 * fixed half circles with their diameters on the byte front, and the rest grow to the same scale.
 * Their rotation follows [filled] rather than [phase], so a tire turns exactly as far as the transfer
 * has travelled and holds still when the bytes stop.
 */
private fun DrawScope.limitedHead(
    animation: DockAnimation,
    filled: Float,
    phase: Float,
    fraction: Float,
) {
    if (size.height <= 0f) return
    when (animation) {
        DockAnimation.KAMEHAMEHA -> kamehamehaHead(filled, phase)
        DockAnimation.TSUNAMI -> tsunamiHead(filled, phase)
        DockAnimation.FIREBALL -> fireballHead(filled, phase)
        DockAnimation.BOULDER -> boulderHead(filled, phase)
        DockAnimation.BULLET -> bulletHead(filled, phase)
        DockAnimation.ARROW -> arrowHead(filled)
        DockAnimation.LASER -> laserHead(filled, phase)
        DockAnimation.THUNDER -> thunderHead(filled, phase)
        DockAnimation.COUNTING -> countingHead(filled, fraction)
        DockAnimation.FLASH -> flashHead(filled, phase)
        DockAnimation.WHEEL -> wheelHead(filled)
        DockAnimation.NOS -> nosHead(filled, phase)
        else -> Unit
    }
}

/** The beam and drifting ki behind a blast whose head is a fixed half circle. */
private fun DrawScope.kamehamehaTrail(phase: Float, filled: Float) {
    val h = size.height
    val headX = (filled - 9.dp.toPx()).coerceAtLeast(0f)
    // Ki drifts down the whole panel behind the blast.
    ambientStreaks(phase, filled, KiCyan, 6)
    // The beam is the wake of the head: full height at its brightest, fading back into the fill.
    val tail = 64.dp.toPx().coerceAtMost(headX)
    if (tail > 1f) {
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, KiCyan.copy(alpha = 0.30f), KiWhite.copy(alpha = 0.55f)),
                startX = headX - tail,
                endX = headX,
            ),
            topLeft = Offset(headX - tail, h * 0.34f),
            size = Size(tail, h * 0.32f),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, KiWhite.copy(alpha = 0.85f)),
                startX = headX - tail,
                endX = headX,
            ),
            topLeft = Offset(headX - tail, h * 0.50f - 1.5.dp.toPx()),
            size = Size(tail, 3.dp.toPx()),
        )
    }
    // Ki wisps climb off the beam at every height.
    for (i in 0 until 5) {
        val p = (phase * 1.4f + i / 5f) % 1f
        val x = (headX - 10.dp.toPx() - p * 46.dp.toPx()).coerceAtLeast(0f)
        val y = h * (0.30f + 0.40f * i / 4f) - sin(PI.toFloat() * p) * 10.dp.toPx()
        drawCircle(
            color = KiWhite.copy(alpha = (1f - p) * 0.32f),
            radius = (0.8f + p * 0.8f) * 1.dp.toPx(),
            center = Offset(x, y),
        )
    }
}

/** The body of water behind the wave's fixed half-circle face. */
private fun DrawScope.tsunamiTrail(phase: Float, filled: Float) {
    val h = size.height
    val turns = 2f * PI.toFloat()
    val back = 30.dp.toPx().coerceAtMost(filled)
    val crest = Path()
    val steps = 18
    crest.moveTo(filled, 0f)
    for (i in 0..steps) {
        val y = h * i / steps
        val x = filled + sin((y / h) * turns * 1.2f + phase * turns) * 5.dp.toPx()
        crest.lineTo(x, y)
    }
    val body = Path().apply {
        addPath(crest)
        lineTo(filled - back, h)
        lineTo(filled - back, 0f)
        close()
    }
    drawPath(
        body,
        brush = Brush.verticalGradient(
            listOf(WaveBlue.copy(alpha = 0.42f), WaveBlue.copy(alpha = 0.14f), WaveBlue.copy(alpha = 0.30f)),
        ),
    )
    drawPath(crest, WaveFoam.copy(alpha = 0.62f), style = Stroke(1.6.dp.toPx()))
    // Foam curls at the two edges of the card, where the wall is breaking.
    for (edge in 0..1) {
        val dir = if (edge == 0) 1f else -1f
        val baseY = if (edge == 0) h * 0.08f else h * 0.92f
        val lip = Path().apply {
            moveTo(filled - 2.dp.toPx(), baseY)
            cubicTo(
                filled - 13.dp.toPx(), baseY + dir * 10.dp.toPx(),
                filled - 22.dp.toPx(), baseY - dir * 2.dp.toPx(),
                filled - 27.dp.toPx(), baseY + dir * 13.dp.toPx(),
            )
        }
        drawPath(lip, WaveFoam.copy(alpha = 0.50f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
    // Spray leaves the crest top and bottom and falls back in.
    for (i in 0 until 8) {
        val p = (phase * 1.6f + i / 8f) % 1f
        val top = i % 2 == 0
        val x = (filled - 4.dp.toPx() + p * 12.dp.toPx()).coerceAtMost(size.width)
        val y = if (top) {
            h * 0.06f - sin(PI.toFloat() * p) * 8.dp.toPx() + p * 5.dp.toPx()
        } else {
            h * 0.94f + sin(PI.toFloat() * p) * 8.dp.toPx() - p * 5.dp.toPx()
        }
        drawCircle(
            color = WaveFoam.copy(alpha = (1f - p) * 0.55f),
            radius = (0.8f + p * 1.1f) * 1.dp.toPx(),
            center = Offset(x, y),
        )
    }
    // Foam streaks run back along the body.
    for (i in 0 until 3) {
        val y = h * (0.30f + 0.20f * i) + sin(phase * turns + i) * 2.dp.toPx()
        drawLine(
            color = WaveFoam.copy(alpha = 0.16f),
            start = Offset((filled - back).coerceAtLeast(0f), y),
            end = Offset((filled - 6.dp.toPx()).coerceAtLeast(0f), y),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** The flame wake and embers behind the ball. */
private fun DrawScope.fireballTrail(phase: Float, filled: Float) {
    val h = size.height
    val headX = (filled - 10.dp.toPx()).coerceAtLeast(0f)
    ambientStreaks(phase, filled, FireOrange, 6)
    val tail = 64.dp.toPx().coerceAtMost(headX)
    if (tail > 1f) {
        val flame = Path().apply {
            moveTo(headX, h * 0.28f)
            cubicTo(headX - tail * 0.35f, h * 0.16f, headX - tail * 0.70f, h * 0.36f, headX - tail, h * 0.50f)
            cubicTo(headX - tail * 0.70f, h * 0.64f, headX - tail * 0.35f, h * 0.84f, headX, h * 0.72f)
            close()
        }
        drawPath(
            flame,
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, FireRed.copy(alpha = 0.35f), FireOrange.copy(alpha = 0.65f)),
                startX = headX - tail,
                endX = headX,
            ),
        )
    }
    // Embers lift off the wake and fall back under gravity.
    for (i in 0 until 7) {
        val p = (phase + i / 7f) % 1f
        val x = (headX - 6.dp.toPx() - p * 48.dp.toPx()).coerceAtLeast(0f)
        val y = h * (0.16f + 0.68f * hashUnit(i * 17 + 3)) - sin(PI.toFloat() * p) * 14.dp.toPx()
        drawCircle(
            color = (if (i % 2 == 0) FireGold else FireOrange).copy(alpha = (1f - p) * 0.55f),
            radius = (0.7f + p * 1.0f) * 1.dp.toPx(),
            center = Offset(x, y),
        )
    }
}

/** Grit and the ground line the stone grinds along. */
private fun DrawScope.boulderTrail(phase: Float, filled: Float) {
    val h = size.height
    ambientStreaks(phase, filled, Dust, 5)
    val headX = (filled - 4.dp.toPx()).coerceAtLeast(0f)
    for (i in 0 until 6) {
        val p = (phase + i / 6f) % 1f
        val x = (headX - 10.dp.toPx() - p * 60.dp.toPx()).coerceAtLeast(0f)
        val y = h * (0.58f + 0.32f * hashUnit(i * 29 + 7)) - sin(PI.toFloat() * p) * 7.dp.toPx()
        drawCircle(
            color = Dust.copy(alpha = (1f - p) * 0.26f),
            radius = (1.4f + p * 3.6f) * 1.dp.toPx(),
            center = Offset(x, y),
        )
    }
    // The ground: one worn line along the bottom of the card.
    drawLine(
        color = RockDark.copy(alpha = 0.28f),
        start = Offset(0f, h - 1.5.dp.toPx()),
        end = Offset(filled, h - 1.5.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

/** The corkscrew of air behind the round. */
private fun DrawScope.bulletTrail(phase: Float, filled: Float) {
    val h = size.height
    val turns = 2f * PI.toFloat()
    val cy = h * 0.50f
    val headX = (filled - 9.dp.toPx()).coerceAtLeast(0f)
    val spiral = 72.dp.toPx().coerceAtMost(headX)
    ambientStreaks(phase, filled, Brass, 5)
    if (spiral > 2f) {
        // Two strands of the helix, half a turn apart, so the trail reads as a corkscrew.
        for (strand in 0 until 2) {
            val path = Path()
            var x = headX - spiral
            var first = true
            val step = 2.dp.toPx()
            while (x <= headX - 2.dp.toPx()) {
                val y = cy + sin((headX - x) / (8.dp.toPx()) + phase * turns * 2f + strand * PI.toFloat()) *
                    h * 0.16f
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += step
            }
            drawPath(
                path,
                Brass.copy(alpha = if (strand == 0) 0.45f else 0.22f),
                style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/** The shaft, fletching and slipstream behind the arrowhead. */
private fun DrawScope.arrowTrail(phase: Float, filled: Float) {
    val h = size.height
    val turns = 2f * PI.toFloat()
    val cy = h * 0.50f
    val headX = (filled - 9.dp.toPx()).coerceAtLeast(0f)
    val shaft = 58.dp.toPx().coerceAtMost(headX)
    val tailX = (headX - shaft).coerceAtLeast(0f)
    // The slipstream fills the card, not just the arrow's own lane.
    ambientStreaks(phase, filled, Brass, 6)
    // Speed lines: the air the arrow has already passed through.
    for (i in 0 until 4) {
        val y = cy + (i - 1.5f) * 4.dp.toPx()
        drawLine(
            color = Brass.copy(alpha = 0.15f),
            start = Offset((tailX - 14.dp.toPx()).coerceAtLeast(0f), y),
            end = Offset(tailX, y),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    drawLine(
        color = Color.White.copy(alpha = 0.82f),
        start = Offset(tailX, cy),
        end = Offset(headX, cy),
        strokeWidth = 2.6.dp.toPx(),
        cap = StrokeCap.Round,
    )
    // Fletching: two swept vanes opening toward the tip, so the tail never reads as a second head.
    val flutter = sin(phase * turns * 2f) * 1.dp.toPx()
    val vane = (tailX + 10.dp.toPx()).coerceAtMost(headX)
    for (i in 0 until 2) {
        val root = (vane - i * 2.dp.toPx()).coerceAtLeast(tailX)
        val spread = 5.dp.toPx() + i * 2.dp.toPx() + flutter * i
        drawLine(
            color = Brass.copy(alpha = 0.65f),
            start = Offset(root, cy),
            end = Offset(tailX, cy - spread),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Brass.copy(alpha = 0.65f),
            start = Offset(root, cy),
            end = Offset(tailX, cy + spread),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Three beams cutting the whole card, each with its own flicker and packet. */
private fun DrawScope.laserTrail(phase: Float, filled: Float) {
    val h = size.height
    val turns = 2f * PI.toFloat()
    // Sparks in the air between the beams fill the rest of the card.
    ambientStreaks(phase, filled, LaserRed, 5)
    for (b in 0 until 3) {
        val cy = h * (0.24f + 0.26f * b)
        val flicker = 0.62f + 0.38f * sin(phase * turns * (4f + b) + b * 1.7f)
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, LaserRed.copy(alpha = 0.28f * flicker), Color.Transparent),
                startY = cy - 7.dp.toPx(),
                endY = cy + 7.dp.toPx(),
            ),
            topLeft = Offset(0f, cy - 7.dp.toPx()),
            size = Size(filled, 14.dp.toPx()),
        )
        drawRect(
            color = Color.White.copy(alpha = 0.85f * flicker),
            topLeft = Offset(0f, cy - 1.2.dp.toPx()),
            size = Size(filled, 2.4.dp.toPx()),
        )
        // A bright packet rides each beam toward the head.
        val packet = filled * ((phase * (1.6f + b * 0.35f) + b * 0.33f) % 1f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.8f), LaserRed.copy(alpha = 0f)),
                Offset(packet, cy),
                5.5.dp.toPx(),
            ),
            radius = 5.5.dp.toPx(),
            center = Offset(packet, cy),
        )
    }
}

/** The strike inside the fill. The bolt at the head is drawn unclipped below. */
private fun DrawScope.thunderTrail(phase: Float, filled: Float) {
    val h = size.height
    // One strike per loop, then a short afterglow.
    val window = 0.22f
    if (phase > window) return
    val fade = 1f - phase / window
    val seed = hashUnit((filled * 0.37f).toInt() + 3)
    val x = filled * (0.24f + 0.52f * seed)
    val steps = 8
    val bolt = Path()
    bolt.moveTo(x, 0f)
    for (i in 1..steps) {
        val y = h * i / steps
        val jitter = (hashUnit(i * 31 + seed.toInt() * 7) - 0.5f) * 14.dp.toPx()
        bolt.lineTo(x + jitter, y)
    }
    drawPath(bolt, SpeedYellow.copy(alpha = 0.30f * fade), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
    drawPath(bolt, BoltWhite.copy(alpha = 0.90f * fade), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
    // A branch forks off the main bolt.
    val branch = Path().apply {
        val by = h * 0.55f
        moveTo(x, by)
        lineTo(x + 10.dp.toPx(), by + 7.dp.toPx())
        lineTo(x + 4.dp.toPx(), by + 14.dp.toPx())
    }
    drawPath(branch, BoltWhite.copy(alpha = 0.55f * fade), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
    // The flash that comes with the strike.
    drawRect(Color.White.copy(alpha = 0.08f * fade), size = size)
}

/** The soft bed of light the counted digits sit in. */
private fun DrawScope.countingTrail(filled: Float) {
    val center = Offset((filled - 18.dp.toPx()).coerceAtLeast(0f), size.height / 2f)
    val radius = 30.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            listOf(CounterTeal.copy(alpha = 0.18f), Color.Transparent),
            center,
            radius,
        ),
        radius = radius,
        center = center,
    )
}

/** The torn air behind the speedster. */
private fun DrawScope.flashTrail(phase: Float, filled: Float) {
    val h = size.height
    val turns = 2f * PI.toFloat()
    // Horizontal streaks, the air torn by something already past.
    for (i in 0 until 7) {
        val seed = hashUnit(i * 23 + 5)
        val y = h * (0.12f + 0.76f * hashUnit(i * 53 + 1))
        val length = 16.dp.toPx() + seed * 34.dp.toPx()
        val x = filled * ((phase * 3f + i / 7f) % 1.15f) - 0.15f * filled
        drawLine(
            color = if (i % 3 == 0) SpeedYellow.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.16f),
            start = Offset(x.coerceAtLeast(0f), y),
            end = Offset((x + length).coerceAtMost(filled), y),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    // The lightning slash that follows the run.
    val boltY = h * (0.16f + 0.04f * sin(phase * turns))
    val bolt = Path().apply {
        moveTo(0f, boltY)
        var x = 0f
        var up = true
        while (x < filled) {
            x += 10.dp.toPx()
            lineTo(x, boltY + if (up) -5.dp.toPx() else 5.dp.toPx())
            up = !up
        }
    }
    val flicker = 0.4f + 0.6f * (0.5f + 0.5f * sin(phase * turns * 6f))
    drawPath(bolt, SpeedYellow.copy(alpha = 0.30f * flicker), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
    drawPath(bolt, BoltWhite.copy(alpha = 0.55f * flicker), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
}

/** Smoke and the skid left by the tire. */
private fun DrawScope.wheelTrail(phase: Float, filled: Float) {
    val h = size.height
    ambientStreaks(phase, filled, SmokeGrey, 5)
    val headX = (filled - 4.dp.toPx()).coerceAtLeast(0f)
    for (i in 0 until 6) {
        val p = (phase + i / 6f) % 1f
        val x = (headX - 12.dp.toPx() - p * 58.dp.toPx()).coerceAtLeast(0f)
        val y = h * (0.56f + 0.34f * hashUnit(i * 37 + 9)) - p * 9.dp.toPx()
        drawCircle(
            color = SmokeGrey.copy(alpha = (1f - p) * 0.20f),
            radius = (1.4f + p * 4.6f) * 1.dp.toPx(),
            center = Offset(x, y),
        )
    }
    // The skid: the line the tire has been laying down since the left edge.
    drawLine(
        color = Color.Black.copy(alpha = 0.26f),
        start = Offset(0f, h - 2.dp.toPx()),
        end = Offset(headX, h - 2.dp.toPx()),
        strokeWidth = 2.4.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

/** Nitrous flames and torn air behind the car. */
private fun DrawScope.nosTrail(phase: Float, filled: Float) {
    val h = size.height
    val turns = 2f * PI.toFloat()
    val carW = h * 1.18f
    val rear = (filled - 2.dp.toPx() - carW).coerceAtLeast(0f)
    ambientStreaks(phase, filled, NosBlue, 6)
    // Nitrous: three tongues from the exhausts, flickering at different rates.
    for (i in 0 until 3) {
        val flicker = 0.5f + 0.5f * sin(phase * turns * 6f + i * 1.4f)
        val length = 18.dp.toPx() + 26.dp.toPx() * flicker
        val y = h * (0.62f + 0.07f * i)
        drawLine(
            color = NosBlue.copy(alpha = 0.55f),
            start = Offset(rear, y),
            end = Offset((rear - length).coerceAtLeast(0f), y),
            strokeWidth = (h * 0.06f).coerceAtLeast(3.dp.toPx()),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = NosWhite.copy(alpha = 0.62f),
            start = Offset(rear, y),
            end = Offset((rear - length * 0.55f).coerceAtLeast(0f), y),
            strokeWidth = (h * 0.02f).coerceAtLeast(1.4.dp.toPx()),
            cap = StrokeCap.Round,
        )
    }
    // Speed lines across the height of the card.
    for (i in 0 until 5) {
        val y = h * (0.14f + 0.17f * i)
        val length = 18.dp.toPx() + 20.dp.toPx() * hashUnit(i * 41 + 2)
        val x = filled * ((phase * 2.2f + i * 0.19f) % 1.1f)
        if (x >= filled) continue
        drawLine(
            color = NosBlue.copy(alpha = 0.16f),
            start = Offset(x.coerceAtLeast(0f), y),
            end = Offset((x + length).coerceAtMost(filled), y),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * A fixed half circle at the byte front: diameter tall enough to touch both edges, dome leading.
 * The shape does not breathe; only the light on it does.
 */
private fun DrawScope.kamehamehaHead(filled: Float, phase: Float) {
    val h = size.height
    val r = h / 2f
    val cy = h / 2f
    val flatX = (filled - 1.5.dp.toPx()).coerceIn(0f, size.width)
    val turns = 2f * PI.toFloat()
    val pulse = 0.5f + 0.5f * sin(phase * turns * 2.4f)
    // The charge in the air around the leading face.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(KiCyan.copy(alpha = 0.28f), Color.Transparent),
            Offset(flatX, cy),
            r * 1.35f,
        ),
        radius = r * 1.35f,
        center = Offset(flatX, cy),
    )
    // The head is one fixed half circle: a diameter on the byte front and a dome ahead of it.
    drawArc(
        brush = Brush.verticalGradient(
            listOf(KiWhite.copy(alpha = 0.92f), KiCyan.copy(alpha = 0.58f), KiWhite.copy(alpha = 0.92f)),
        ),
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(flatX - r, 0f),
        size = Size(2f * r, h),
    )
    // Fixed rim, so the shape never changes while the light moves.
    drawArc(
        color = KiWhite.copy(alpha = 0.90f),
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(flatX - r, 0f),
        size = Size(2f * r, h),
        style = Stroke(2.4.dp.toPx()),
    )
    drawArc(
        color = KiCyan.copy(alpha = 0.45f + 0.30f * pulse),
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(flatX - r + 3.dp.toPx(), 3.dp.toPx()),
        size = Size(2f * r - 6.dp.toPx(), h - 6.dp.toPx()),
        style = Stroke(1.2.dp.toPx()),
    )
    drawLine(
        color = KiWhite.copy(alpha = 0.85f),
        start = Offset(flatX, 0f),
        end = Offset(flatX, h),
        strokeWidth = 2.dp.toPx(),
    )
    // Charged flecks on the dome, fixed in place, only their brightness moving.
    for (i in 0 until 7) {
        val angle = (-75f + 25f * i) * PI.toFloat() / 180f
        val rr = r * (0.78f + 0.16f * (i % 2))
        drawCircle(
            color = KiWhite.copy(alpha = 0.25f + 0.35f * pulse),
            radius = (0.9f + (i % 3) * 0.4f) * 1.dp.toPx(),
            center = Offset(flatX + kotlin.math.cos(angle) * rr, cy + sin(angle) * rr),
        )
    }
}

/** A fixed half circle of water, foam rings running through it, spray leaving the rim. */
private fun DrawScope.tsunamiHead(filled: Float, phase: Float) {
    val h = size.height
    val r = h / 2f
    val cy = h / 2f
    val flatX = (filled - 1.dp.toPx()).coerceIn(0f, size.width)
    val turns = 2f * PI.toFloat()
    drawArc(
        brush = Brush.verticalGradient(
            listOf(WaveFoam.copy(alpha = 0.92f), WaveBlue.copy(alpha = 0.62f), WaveFoam.copy(alpha = 0.92f)),
        ),
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(flatX - r, 0f),
        size = Size(2f * r, h),
    )
    // Layered foam, the rings a breaking wave leaves in its face.
    for (i in 0 until 3) {
        val inset = (i + 1) * 0.16f * r
        drawArc(
            color = WaveFoam.copy(alpha = 0.42f - i * 0.10f),
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(flatX - r + inset, inset),
            size = Size(2f * r - 2f * inset, h - 2f * inset),
            style = Stroke((1.6f - i * 0.3f).dp.toPx(), cap = StrokeCap.Round),
        )
    }
    // Spray leaves the rim, top and bottom, and falls back in.
    for (i in 0 until 8) {
        val p = (phase * 1.5f + i / 8f) % 1f
        val top = i % 2 == 0
        val x = (flatX + r * 0.25f + p * 12.dp.toPx()).coerceAtMost(size.width)
        val y = if (top) {
            h * 0.04f - sin(PI.toFloat() * p) * 9.dp.toPx() + p * 6.dp.toPx()
        } else {
            h * 0.96f + sin(PI.toFloat() * p) * 9.dp.toPx() - p * 6.dp.toPx()
        }
        drawCircle(
            color = WaveFoam.copy(alpha = (1f - p) * 0.60f),
            radius = (0.8f + p * 1.2f) * 1.dp.toPx(),
            center = Offset(x, y),
        )
    }
    // The foam line on the diameter.
    drawLine(
        color = WaveFoam.copy(alpha = 0.65f),
        start = Offset(flatX, 0f),
        end = Offset(flatX, h),
        strokeWidth = 2.dp.toPx(),
    )
    for (i in 0 until 3) {
        val y = h * (0.20f + 0.30f * i) + sin(phase * turns + i) * 3.dp.toPx()
        drawLine(
            color = WaveFoam.copy(alpha = 0.20f),
            start = Offset((flatX - 18.dp.toPx()).coerceAtLeast(0f), y),
            end = Offset(flatX, y),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** A ball whose flame tongues reach the card's two edges. */
private fun DrawScope.fireballHead(filled: Float, phase: Float) {
    val h = size.height
    val turns = 2f * PI.toFloat()
    val r = h * 0.42f
    val cx = (filled - r * 0.55f).coerceAtLeast(r * 0.42f)
    val cy = h / 2f
    val flicker = 0.5f + 0.5f * sin(phase * turns * 4f + 0.7f)
    // Heat in the air.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(FireOrange.copy(alpha = 0.35f), Color.Transparent),
            Offset(cx, cy),
            r * 1.5f,
        ),
        radius = r * 1.5f,
        center = Offset(cx, cy),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(FireGold.copy(alpha = 0.98f), FireOrange.copy(alpha = 0.92f), FireRed.copy(alpha = 0.55f)),
            Offset(cx - r * 0.25f, cy - r * 0.25f),
            r * 1.6f,
        ),
        radius = r,
        center = Offset(cx, cy),
    )
    // Flame tongues reach the top and bottom edges.
    for (i in 0 until 4) {
        val up = i < 2
        val p = (phase * 1.8f + i / 4f) % 1f
        val sway = sin(p * turns) * h * 0.04f
        val rootX = cx + (if (up) -1f else 1f) * r * 0.45f
        val rootY = cy + (if (up) -1f else 1f) * r * 0.72f
        val tipY = if (up) h * 0.02f else h * 0.98f
        val tongue = Path().apply {
            moveTo(rootX - r * 0.18f, rootY)
            cubicTo(
                rootX - r * 0.5f + sway, (rootY + tipY) / 2f,
                rootX + r * 0.2f + sway, tipY + (rootY - tipY) * 0.3f,
                rootX + sway, tipY,
            )
            cubicTo(
                rootX + r * 0.55f + sway, tipY + (rootY - tipY) * 0.2f,
                rootX + r * 0.30f, (rootY + tipY) / 2f,
                rootX + r * 0.18f, rootY,
            )
            close()
        }
        drawPath(tongue, (if (i % 2 == 0) FireGold else FireOrange).copy(alpha = 0.40f + 0.30f * flicker))
    }
    // A white-hot core that breathes.
    drawCircle(FireGold.copy(alpha = 0.55f + 0.35f * flicker), r * 0.38f, Offset(cx, cy))
}

/** A stone as tall as the card, rolling with the bytes, with the figure leaning into it. */
private fun DrawScope.boulderHead(filled: Float, phase: Float) {
    val h = size.height
    val r = h / 2f - 1.dp.toPx()
    val cy = h / 2f
    val cx = (filled - r * 0.38f).coerceAtLeast(r * 0.45f)
    val spin = filled / r
    val turns = 2f * PI.toFloat()
    // The stone fills the card's height.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(RockGrey.copy(alpha = 0.82f), RockDark.copy(alpha = 0.82f)),
            Offset(cx - r * 0.28f, cy - r * 0.28f),
            r * 1.5f,
        ),
        radius = r,
        center = Offset(cx, cy),
    )
    // Facets that turn with the stone.
    for (i in 0 until 4) {
        val a = spin + i * turns / 4f
        val fr = r * (0.32f + 0.10f * (i % 2))
        drawCircle(
            color = RockDark.copy(alpha = 0.28f),
            radius = r * (0.12f + 0.05f * (i % 3)),
            center = Offset(cx + kotlin.math.cos(a) * fr, cy + sin(a) * fr),
        )
    }
    drawCircle(RockDark.copy(alpha = 0.55f), r, Offset(cx, cy), style = Stroke(2.dp.toPx()))
    // Cracks, turning as it rolls.
    for (i in 0 until 5) {
        val a = spin * 0.8f + i * turns / 5f
        drawLine(
            color = RockDark.copy(alpha = 0.50f),
            start = Offset(cx + kotlin.math.cos(a) * r * 0.22f, cy + sin(a) * r * 0.22f),
            end = Offset(cx + kotlin.math.cos(a) * r * 0.78f, cy + sin(a) * r * 0.78f),
            strokeWidth = (h * 0.022f).coerceAtLeast(1.dp.toPx()),
            cap = StrokeCap.Round,
        )
    }
    // The figure, as tall as the card, leaning into the stone.
    val ink = Dust.copy(alpha = 0.78f)
    val fx = (cx - r * 0.88f).coerceAtLeast(h * 0.05f)
    drawCircle(ink, h * 0.045f, Offset(fx, h * 0.24f))
    drawLine(
        color = ink,
        start = Offset(fx, h * 0.30f),
        end = Offset(fx + h * 0.02f, h * 0.64f),
        strokeWidth = (h * 0.035f).coerceAtLeast(2.dp.toPx()),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = ink,
        start = Offset(fx + h * 0.01f, h * 0.38f),
        end = Offset(cx - r * 0.72f, h * 0.44f),
        strokeWidth = (h * 0.028f).coerceAtLeast(1.5.dp.toPx()),
        cap = StrokeCap.Round,
    )
    val stride = sin(phase * turns * 2f) * h * 0.035f
    drawLine(
        color = ink,
        start = Offset(fx + h * 0.02f, h * 0.64f),
        end = Offset(fx - h * 0.03f + stride, h * 0.94f),
        strokeWidth = (h * 0.03f).coerceAtLeast(1.6.dp.toPx()),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = ink,
        start = Offset(fx + h * 0.02f, h * 0.64f),
        end = Offset(fx + h * 0.06f - stride, h * 0.94f),
        strokeWidth = (h * 0.03f).coerceAtLeast(1.6.dp.toPx()),
        cap = StrokeCap.Round,
    )
}

/** A long round, nearly the card's height, at the head of its own spiral. */
private fun DrawScope.bulletHead(filled: Float, phase: Float) {
    val h = size.height
    val turns = 2f * PI.toFloat()
    val tipX = (filled - 1.dp.toPx()).coerceIn(0f, size.width)
    val length = h * 0.62f
    val base = (tipX - length).coerceAtLeast(0f)
    val half = h * 0.38f
    val cy = h / 2f
    val body = Path().apply {
        moveTo(tipX, cy)
        cubicTo(
            tipX - length * 0.08f, cy - half * 0.55f,
            tipX - length * 0.34f, cy - half,
            tipX - length * 0.62f, cy - half * 0.86f,
        )
        lineTo(base, cy - half * 0.62f)
        lineTo(base, cy + half * 0.62f)
        lineTo(tipX - length * 0.62f, cy + half * 0.86f)
        cubicTo(
            tipX - length * 0.34f, cy + half,
            tipX - length * 0.08f, cy + half * 0.55f,
            tipX, cy,
        )
        close()
    }
    drawPath(
        body,
        brush = Brush.horizontalGradient(
            listOf(Brass.copy(alpha = 0.55f), Brass.copy(alpha = 0.92f), Color.White.copy(alpha = 0.92f)),
            startX = base,
            endX = tipX,
        ),
    )
    // Casing rim and the hot tip.
    drawLine(
        color = Brass.copy(alpha = 0.85f),
        start = Offset(base, cy - half * 0.62f),
        end = Offset(base, cy + half * 0.62f),
        strokeWidth = 2.dp.toPx(),
    )
    drawLine(
        color = Color.White.copy(alpha = 0.85f),
        start = Offset(tipX - length * 0.22f, cy),
        end = Offset(tipX, cy),
        strokeWidth = 2.4.dp.toPx(),
        cap = StrokeCap.Round,
    )
    // Sonic rings, growing to the card's height.
    for (i in 0 until 2) {
        val p = (phase * 1.5f + i * 0.5f) % 1f
        drawCircle(
            color = Brass.copy(alpha = (1f - p) * 0.26f),
            radius = h * (0.10f + p * 0.42f),
            center = Offset(tipX, cy),
            style = Stroke(1.4.dp.toPx()),
        )
    }
}

/** An arrowhead as tall as the card, barbs at both edges. */
private fun DrawScope.arrowHead(filled: Float) {
    val h = size.height
    val tipX = (filled + 2.dp.toPx()).coerceAtMost(size.width)
    val cy = h / 2f
    val barb = (tipX - h * 0.34f).coerceAtLeast(0f)
    val notch = (tipX - h * 0.20f).coerceAtLeast(0f)
    val head = Path().apply {
        moveTo(tipX, cy)
        lineTo(barb, 1.dp.toPx())
        lineTo(notch, cy - h * 0.08f)
        lineTo(notch, cy + h * 0.08f)
        lineTo(barb, h - 1.dp.toPx())
        close()
    }
    drawPath(
        head,
        brush = Brush.verticalGradient(
            listOf(Brass.copy(alpha = 0.72f), Brass.copy(alpha = 0.98f), Brass.copy(alpha = 0.72f)),
        ),
    )
    drawLine(
        color = Color.White.copy(alpha = 0.85f),
        start = Offset(notch, cy),
        end = Offset(tipX, cy),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color.White.copy(alpha = 0.35f),
        start = Offset(barb, h * 0.06f),
        end = Offset(tipX, cy),
        strokeWidth = 1.2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color.White.copy(alpha = 0.35f),
        start = Offset(barb, h * 0.94f),
        end = Offset(tipX, cy),
        strokeWidth = 1.2.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

/** A laser blade spanning the card, with the flare at its middle. */
private fun DrawScope.laserHead(filled: Float, phase: Float) {
    val h = size.height
    val turns = 2f * PI.toFloat()
    val x = (filled - 2.dp.toPx()).coerceIn(0f, size.width)
    val flicker = 0.62f + 0.38f * sin(phase * turns * 5f)
    val left = (x - 9.dp.toPx()).coerceAtLeast(0f)
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                LaserRed.copy(alpha = 0.28f * flicker),
                Color.White.copy(alpha = 0.30f * flicker),
                LaserRed.copy(alpha = 0.20f * flicker),
                Color.Transparent,
            ),
            startX = left,
            endX = x + 9.dp.toPx(),
        ),
        topLeft = Offset(left, 0f),
        size = Size((x + 9.dp.toPx() - left).coerceAtMost(size.width - left), h),
    )
    drawRect(
        color = Color.White.copy(alpha = 0.90f * flicker),
        topLeft = Offset(x - 1.dp.toPx(), 0f),
        size = Size(2.dp.toPx(), h),
    )
    // Lens flare on the middle of the blade.
    val flare = Offset(x, h / 2f)
    drawCircle(Color.White.copy(alpha = 0.9f), 3.dp.toPx(), flare)
    drawLine(
        color = Color.White.copy(alpha = 0.55f),
        start = Offset(flare.x - 16.dp.toPx(), flare.y),
        end = Offset(flare.x + 16.dp.toPx(), flare.y),
        strokeWidth = 1.4.dp.toPx(),
    )
    drawLine(
        color = LaserRed.copy(alpha = 0.45f),
        start = Offset(flare.x, flare.y - h * 0.36f),
        end = Offset(flare.x, flare.y + h * 0.36f),
        strokeWidth = 1.2.dp.toPx(),
    )
    // Sparks along the blade.
    for (i in 0 until 6) {
        val p = (phase + i / 6f) % 1f
        drawCircle(
            color = Color.White.copy(alpha = (1f - p) * 0.45f),
            radius = (0.7f + (i % 3) * 0.3f) * 1.dp.toPx(),
            center = Offset(x + (hashUnit(i * 19 + 5) - 0.5f) * 8.dp.toPx(), h * p),
        )
    }
}

/** A bolt from the top edge to the bottom, lit on the strike and kept as a channel between. */
private fun DrawScope.thunderHead(filled: Float, phase: Float) {
    val h = size.height
    val window = 0.26f
    val strike = if (phase <= window) 1f - phase / window else 0f
    val baseX = (filled - 5.dp.toPx()).coerceIn(0f, size.width)
    val seed = hashUnit((filled * 0.53f).toInt() + 17)
    val steps = 9
    val bolt = Path()
    bolt.moveTo(baseX, 0f)
    for (i in 1..steps) {
        val y = h * i / steps
        val wobble = (hashUnit(i * 23 + seed.toInt() * 13) - 0.5f) * 16.dp.toPx()
        bolt.lineTo((baseX + wobble).coerceIn(0f, size.width), y)
    }
    // The fixed channel keeps the style present between strikes; the strike lights it.
    drawPath(
        bolt,
        SpeedYellow.copy(alpha = 0.10f + 0.30f * strike),
        style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
    )
    drawPath(
        bolt,
        BoltWhite.copy(alpha = 0.18f + 0.77f * strike),
        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
    )
    if (strike > 0f) {
        val fork = Path().apply {
            val y = h * 0.46f
            moveTo(baseX, y)
            lineTo(baseX + 13.dp.toPx(), y + 8.dp.toPx())
            lineTo(baseX + 6.dp.toPx(), y + 18.dp.toPx())
        }
        drawPath(fork, BoltWhite.copy(alpha = 0.60f * strike), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** The percentage counted out in digits as tall as the card. */
private fun DrawScope.countingHead(filled: Float, fraction: Float) {
    val percent = (fraction * 100f).roundToInt().coerceIn(0, 100)
    val digitHeight = (size.height * 0.62f).coerceAtLeast(12.dp.toPx())
    val digitWidth = digitHeight * 0.50f
    val gap = 2.6.dp.toPx()
    // Ghost counts, then the live count right-aligned at the head.
    for (ghost in 3 downTo 0) {
        val value = (percent - ghost * 3).coerceAtLeast(0)
        val text = value.toString()
        val total = text.length * digitWidth + (text.length - 1) * gap
        var x = filled - 10.dp.toPx() - total - ghost * 30.dp.toPx()
        val alpha = if (ghost == 0) 0.95f else 0.10f + 0.05f * (3 - ghost)
        val top = (size.height - digitHeight) / 2f
        for (ch in text) {
            drawDigit(ch, x, top, digitWidth, digitHeight, CounterTeal.copy(alpha = alpha))
            x += digitWidth + gap
        }
    }
}

/** A full-height lightning slash and the red afterimage of the run. */
private fun DrawScope.flashHead(filled: Float, phase: Float) {
    val h = size.height
    val turns = 2f * PI.toFloat()
    val headX = (filled - 3.dp.toPx()).coerceIn(0f, size.width)
    val flicker = 0.4f + 0.6f * (0.5f + 0.5f * sin(phase * turns * 6f))
    val left = (headX - 26.dp.toPx()).coerceAtLeast(0f)
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, SpeedRed.copy(alpha = 0.38f), SpeedYellow.copy(alpha = 0.24f)),
            startX = left,
            endX = headX,
        ),
        topLeft = Offset(left, 0f),
        size = Size((headX - left).coerceAtLeast(0f), h),
    )
    // A vertical slash from the top edge to the bottom.
    val bolt = Path()
    bolt.moveTo(headX, 0f)
    for (i in 1..4) {
        val y = h * i / 4f
        val x = headX + if (i % 2 == 0) -6.dp.toPx() else 7.dp.toPx()
        bolt.lineTo(x, y)
    }
    drawPath(bolt, SpeedYellow.copy(alpha = 0.30f * flicker), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
    drawPath(bolt, BoltWhite.copy(alpha = 0.85f * flicker), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    drawLine(
        color = SpeedYellow.copy(alpha = 0.55f),
        start = Offset(headX, 0f),
        end = Offset(headX, h),
        strokeWidth = 1.dp.toPx(),
    )
}

/** A tire the height of the card, its rotation driven by the bytes travelled. */
private fun DrawScope.wheelHead(filled: Float) {
    val h = size.height
    val r = h / 2f
    val cy = h / 2f
    val cx = (filled - r * 0.30f).coerceAtLeast(r * 0.60f)
    // Rolling without slipping: the angle is the distance the head has covered.
    val spin = filled / r
    val turns = 2f * PI.toFloat()
    // The tire is smoke-grey rather than black, so the text drawn over it keeps its contrast.
    drawCircle(SmokeGrey.copy(alpha = 0.42f), r, Offset(cx, cy))
    drawCircle(Color(0xFF2C2C2C).copy(alpha = 0.68f), r * 0.70f, Offset(cx, cy))
    // Tread blocks around the rim, turning with the tire.
    for (i in 0 until 18) {
        val a = spin + i * turns / 18f
        drawLine(
            color = Color(0xFF2C2C2C).copy(alpha = 0.55f),
            start = Offset(cx + kotlin.math.cos(a) * r * 0.82f, cy + sin(a) * r * 0.82f),
            end = Offset(cx + kotlin.math.cos(a) * r * 0.99f, cy + sin(a) * r * 0.99f),
            strokeWidth = (h * 0.028f).coerceAtLeast(1.4.dp.toPx()),
        )
    }
    drawCircle(SmokeGrey.copy(alpha = 0.75f), r * 0.97f, Offset(cx, cy), style = Stroke(1.8.dp.toPx()))
    drawCircle(SmokeGrey.copy(alpha = 0.55f), r * 0.70f, Offset(cx, cy), style = Stroke(1.2.dp.toPx()))
    // Five spokes, turning with the fill.
    for (i in 0 until 5) {
        val a = spin + i * turns / 5f
        drawLine(
            color = SmokeGrey.copy(alpha = 0.90f),
            start = Offset(cx + kotlin.math.cos(a) * r * 0.14f, cy + sin(a) * r * 0.14f),
            end = Offset(cx + kotlin.math.cos(a) * r * 0.62f, cy + sin(a) * r * 0.62f),
            strokeWidth = (h * 0.034f).coerceAtLeast(1.8.dp.toPx()),
            cap = StrokeCap.Round,
        )
    }
    drawCircle(SmokeGrey.copy(alpha = 0.95f), r * 0.11f, Offset(cx, cy))
}

/** The car, stretched to the card's height, riding the head on its flames. */
private fun DrawScope.nosHead(filled: Float, phase: Float) {
    val h = size.height
    val turns = 2f * PI.toFloat()
    val carW = h * 1.18f
    val nose = (filled - 2.dp.toPx()).coerceAtMost(size.width)
    val carX = nose - carW
    val bounce = sin(phase * turns * 3f) * 0.8.dp.toPx()
    val bodyTop = h * 0.52f + bounce
    val bodyBottom = h * 0.80f + bounce
    // The body: a low wedge pointing at the byte front.
    val body = Path().apply {
        moveTo(carX + carW * 0.02f, bodyBottom)
        lineTo(carX + carW * 0.02f, bodyTop + h * 0.06f)
        lineTo(carX + carW * 0.46f, bodyTop + h * 0.02f)
        lineTo(carX + carW * 0.74f, bodyTop + h * 0.10f)
        lineTo(nose, bodyTop + h * 0.18f)
        lineTo(nose, bodyBottom)
        close()
    }
    drawPath(body, NosWhite.copy(alpha = 0.90f))
    // Cabin: glass swept back over the rear axle.
    val cabin = Path().apply {
        moveTo(carX + carW * 0.08f, bodyTop + h * 0.10f)
        lineTo(carX + carW * 0.30f, h * 0.16f + bounce)
        lineTo(carX + carW * 0.62f, h * 0.16f + bounce)
        lineTo(carX + carW * 0.72f, bodyTop + h * 0.10f)
        close()
    }
    drawPath(cabin, NosBlue.copy(alpha = 0.62f))
    // Wheels.
    for (i in 0 until 2) {
        val wx = carX + carW * (0.20f + 0.62f * i)
        val wy = h * 0.88f + bounce
        val wr = h * 0.10f
        drawCircle(Color(0xFF1C1C1C), wr, Offset(wx, wy))
        drawCircle(NosBlue.copy(alpha = 0.55f), wr * 0.45f, Offset(wx, wy))
    }
    // The nose light.
    drawCircle(
        color = NosWhite.copy(alpha = 0.95f),
        radius = (h * 0.025f).coerceAtLeast(1.5.dp.toPx()),
        center = Offset(nose - h * 0.03f, bodyTop + h * 0.24f),
    )
}

// --- Helpers -----------------------------------------------------------------------------------

/**
 * One seven-segment digit, used by the counting meter.
 *
 * Drawn rather than typeset so the digits stay the same weight at every text size and cannot be
 * reflowed by the phone's font scale. The segment map is the standard one.
 */
private fun DrawScope.drawDigit(
    digit: Char,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    val segments = when (digit) {
        '0' -> "abcdef"
        '1' -> "bc"
        '2' -> "abged"
        '3' -> "abgcd"
        '4' -> "fgbc"
        '5' -> "afgcd"
        '6' -> "afgecd"
        '7' -> "abc"
        '8' -> "abcdefg"
        '9' -> "abcdfg"
        else -> ""
    }
    if (segments.isEmpty()) return
    val stroke = (width * 0.16f).coerceAtLeast(1.dp.toPx())
    val m = stroke * 0.9f
    val right = left + width
    val bottom = top + height
    val mid = top + height / 2f
    if ('a' in segments) drawLine(color, Offset(left + m, top), Offset(right - m, top), stroke, StrokeCap.Round)
    if ('b' in segments) drawLine(color, Offset(right, top + m), Offset(right, mid - m), stroke, StrokeCap.Round)
    if ('c' in segments) drawLine(color, Offset(right, mid + m), Offset(right, bottom - m), stroke, StrokeCap.Round)
    if ('d' in segments) drawLine(color, Offset(left + m, bottom), Offset(right - m, bottom), stroke, StrokeCap.Round)
    if ('e' in segments) drawLine(color, Offset(left, mid + m), Offset(left, bottom - m), stroke, StrokeCap.Round)
    if ('f' in segments) drawLine(color, Offset(left, top + m), Offset(left, mid - m), stroke, StrokeCap.Round)
    if ('g' in segments) drawLine(color, Offset(left + m, mid), Offset(right - m, mid), stroke, StrokeCap.Round)
}

/** A deterministic 0..1 sample, so each rain drop, bubble and bolt keeps its lane. */
private fun hashUnit(value: Int): Float {
    val raw = sin(value * 12.9898f) * 43758.5453f
    return raw - floor(raw)
}

/**
 * Speed lines spread from the top of the card to the bottom, so a fast style fills the panel
 * instead of only drawing a lane under the type. They stay dim: the actors own the contrast.
 */
private fun DrawScope.ambientStreaks(phase: Float, filled: Float, color: Color, count: Int) {
    if (filled <= 1f) return
    val h = size.height
    for (i in 0 until count) {
        val seed = hashUnit(i * 59 + 13)
        val y = h * (0.10f + 0.80f * ((i + 0.5f) / count))
        val length = (18.dp.toPx() + seed * 38.dp.toPx()).coerceAtMost(filled)
        val speed = 0.45f + seed * 0.75f
        val x = filled * (((phase * speed + seed) % 1f) * 1.2f - 0.1f)
        drawLine(
            color = color.copy(alpha = 0.16f + 0.12f * seed),
            start = Offset(x.coerceAtLeast(0f), y),
            end = Offset((x + length).coerceAtMost(filled), y),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
