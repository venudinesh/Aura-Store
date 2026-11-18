package dev.aurastore.store.ui.profile

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import dev.aurastore.store.data.AvatarFrame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The frame's slow turn, or a still frame when the phone asked for less motion.
 *
 * Only a frame that actually moves asks for a transition. The top bar turns with the header now:
 * a ring that stops when it leaves the profile page read as a different picture, not a smaller one.
 */
@Composable
internal fun rememberFramePhase(frame: AvatarFrame, animate: Boolean): Float =
    if (!animate || !frame.animated) 0f else rememberPhaseClock().floatValue

/**
 * One turn of the clock, for every ring on a page that should move together.
 *
 * A picture with its own transition is one whole-window redraw a frame, and a page can wear the
 * frame three times: the bar, the hero and the row that changes it. This clock is one heartbeat
 * instead, read from inside each canvas draw, so a tick redraws the rings. It is held to
 * [intervalNanos] because a ring a thumb wide does not need sixty frames a second and the phone
 * pays for every one it draws.
 */
@Composable
internal fun rememberPhaseClock(
    animate: Boolean = true,
    intervalNanos: Long = 33_000_000L,
): MutableFloatState {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, intervalNanos) {
        if (!animate) {
            clock.floatValue = 0f
            return@LaunchedEffect
        }
        val origin = withFrameNanos { it }
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (now - last >= intervalNanos) {
                    last = now
                    // 14 seconds a turn, the pace the house rings have always kept.
                    clock.floatValue = ((now - origin) / 1_000_000_000f * (360f / 14f)) % 360f
                }
            }
        }
    }
    return clock
}

private val GoldLight = Color(0xFFFFF0C2)
private val Gold = Color(0xFFE0A44A)
private val GoldDeep = Color(0xFF9A6420)
private val NeonCyan = Color(0xFF4CE0FF)
private val NeonMagenta = Color(0xFFFF4FD8)
private val EmberOrange = Color(0xFFFFB347)
private val EmberHot = Color(0xFFFF6A00)
private val EmberRed = Color(0xFFC81E1E)
private val Frost = Color(0xFFBFD9FF)
private val SakuraPink = Color(0xFFFF9EC7)
private val LeafGreen = Color(0xFF6FE3A0)
private val StormBlue = Color(0xFF7FB4FF)
private val Violet = Color(0xFFB48CFF)
private val Teal = Color(0xFF4FE3D0)
private val Sun = Color(0xFFFFD166)
private val Prism = listOf(
    Color(0xFFFF5A5A),
    Color(0xFFFFA24A),
    Color(0xFFFFD166),
    Color(0xFF6FE3A0),
    Color(0xFF4CE0FF),
    Color(0xFFB48CFF),
)

/**
 * Paints one frame in the full square, around a picture that has already been inset.
 *
 * The ring sits on a radius of 0.45 of the square with a stroke of 0.05, so its inner edge is exactly
 * where [ProfilePicture] stops the picture. Nothing here reaches outside the square, which is what lets
 * the same drawing sit in a 32 dp slot and in a 112 dp header.
 */
internal fun DrawScope.drawAvatarFrame(frame: AvatarFrame, phase: Float, scheme: ColorScheme) {
    val side = size.minDimension
    val stroke = side * 0.05f
    val radius = side / 2f - stroke * 0.9f
    val ringTopLeft = Offset(center.x - radius, center.y - radius)
    val ringSize = Size(radius * 2f, radius * 2f)

    fun arcAt(
        r: Float,
        color: Color,
        start: Float,
        sweep: Float,
        width: Float = stroke,
        alpha: Float = 1f,
    ) {
        drawArc(
            color = color.copy(alpha = color.alpha * alpha),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }

    fun arc(
        color: Color,
        start: Float,
        sweep: Float,
        width: Float = stroke,
        alpha: Float = 1f,
    ) {
        arcAt(radius, color, start, sweep, width, alpha)
    }

    fun at(angle: Float, r: Float = radius): Offset {
        val radians = angle * PI.toFloat() / 180f
        return Offset(center.x + r * cos(radians), center.y + r * sin(radians))
    }

    fun bead(angle: Float, color: Color, dot: Float, r: Float = radius) {
        drawCircle(color = color, radius = dot, center = at(angle, r))
    }

    fun band(
        from: Float,
        to: Float,
        color: Color,
        width: Float = stroke,
        fromRadius: Float = radius,
        toRadius: Float = radius,
        alpha: Float = 1f,
    ) {
        drawLine(
            color = color.copy(alpha = color.alpha * alpha),
            start = at(from, fromRadius),
            end = at(to, toRadius),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }

    fun star(
        angle: Float,
        outer: Float,
        inner: Float,
        color: Color,
        alpha: Float = 1f,
        hubRadius: Float = radius,
    ) {
        val hub = at(angle, hubRadius)
        val path = Path()
        repeat(8) { index ->
            val r = if (index % 2 == 0) outer else inner
            val radians = (angle + index * 45f) * PI.toFloat() / 180f
            val point = Offset(hub.x + r * cos(radians), hub.y + r * sin(radians))
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawPath(path, color.copy(alpha = color.alpha * alpha))
    }

    fun poly(points: List<Offset>, color: Color, width: Float, alpha: Float = 1f) {
        val path = Path()
        points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(
            path = path,
            color = color.copy(alpha = color.alpha * alpha),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }

    // The anime rings are painted by their own file: one branch per pack entry, all of them sharing
    // this radius and this stroke, so a wall of nine in the picker still reads as one set.
    val anime = frame.anime
    if (anime != null) {
        drawAnimeFrame(anime, phase)
        return
    }

    when (frame) {
        AvatarFrame.NONE -> Unit

        AvatarFrame.RING -> drawCircle(
            color = scheme.onSurfaceVariant.copy(alpha = 0.55f),
            radius = radius,
            center = center,
            style = Stroke(width = stroke * 0.7f),
        )

        AvatarFrame.GOLD -> {
            val band = Brush.sweepGradient(
                listOf(GoldLight, Gold, GoldDeep, Gold, GoldLight),
                center,
            )
            drawCircle(brush = band, radius = radius, center = center, style = Stroke(stroke))
            drawCircle(
                color = Gold.copy(alpha = 0.55f),
                radius = radius - stroke * 2f,
                center = center,
                style = Stroke(stroke * 0.32f),
            )
        }

        AvatarFrame.NEON -> rotate(phase, pivot = center) {
            arc(NeonCyan, -70f, 128f, stroke * 2.6f, 0.16f)
            arc(NeonMagenta, 110f, 128f, stroke * 2.6f, 0.16f)
            arc(NeonCyan, -70f, 128f, stroke * 0.78f)
            arc(NeonMagenta, 110f, 128f, stroke * 0.78f)
        }

        AvatarFrame.ORBIT -> rotate(-phase * 0.55f, pivot = center) {
            drawCircle(
                color = scheme.primary.copy(alpha = 0.28f),
                radius = radius,
                center = center,
                style = Stroke(stroke * 0.32f),
            )
            arc(scheme.primary, 198f, 116f, stroke * 1.1f)
            arc(scheme.tertiary, 18f, 116f, stroke * 1.1f)
            bead(198f, scheme.primary, stroke * 1.6f)
            bead(18f, scheme.tertiary, stroke * 1.6f)
        }

        AvatarFrame.LAUREL -> {
            val leaf = Brush.linearGradient(
                listOf(GoldLight, Gold, GoldDeep),
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f),
            )
            drawArc(
                brush = leaf,
                startAngle = 98f,
                sweepAngle = 158f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(stroke * 0.85f, cap = StrokeCap.Round),
            )
            drawArc(
                brush = leaf,
                startAngle = 282f,
                sweepAngle = 158f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(stroke * 0.85f, cap = StrokeCap.Round),
            )
            for (angle in listOf(124f, 150f, 176f, 334f, 360f, 26f)) {
                bead(angle, Gold, stroke * 0.82f)
            }
        }

        AvatarFrame.PULSE -> rotate(phase * 0.8f, pivot = center) {
            repeat(12) { index ->
                val start = index * 30f
                arc(
                    color = if (index % 2 == 0) scheme.primary else scheme.tertiary,
                    start = start,
                    sweep = 19f,
                    width = stroke * 0.82f,
                    alpha = if (index % 2 == 0) 0.95f else 0.72f,
                )
            }
        }

        AvatarFrame.EMBER -> {
            val fire = Brush.sweepGradient(
                listOf(EmberOrange, EmberHot, EmberRed, EmberHot, EmberOrange),
                center,
            )
            drawCircle(
                color = EmberHot.copy(alpha = 0.16f),
                radius = radius,
                center = center,
                style = Stroke(stroke * 2.4f),
            )
            drawCircle(brush = fire, radius = radius, center = center, style = Stroke(stroke))
            bead(304f, EmberOrange, stroke * 0.85f)
            bead(326f, EmberHot, stroke * 0.6f)
            bead(282f, Frost, stroke * 0.34f)
        }

        AvatarFrame.COMET -> rotate(phase * 0.73f, pivot = center) {
            arc(Gold.copy(alpha = 0.22f), 0f, 168f, stroke * 2.8f)
            arc(GoldLight, 2f, 118f, stroke * 0.75f)
            bead(0f, Color.White, stroke * 1.45f)
            bead(13f, Gold, stroke * 0.85f)
            bead(24f, GoldDeep, stroke * 0.5f)
        }

        AvatarFrame.NOVA -> rotate(-phase, pivot = center) {
            drawCircle(
                color = Gold.copy(alpha = 0.16f),
                radius = radius,
                center = center,
                style = Stroke(stroke * 0.35f),
            )
            repeat(10) { index ->
                val start = index * 36f
                arc(
                    color = if (index % 2 == 0) scheme.primary else scheme.tertiary,
                    start = start,
                    sweep = 9f,
                    width = if (index % 3 == 0) stroke * 1.4f else stroke * 0.7f,
                )
                bead(start + 17f, Gold, stroke * 0.3f)
            }
        }

        AvatarFrame.HALO -> {
            drawCircle(
                color = Gold.copy(alpha = 0.18f),
                radius = radius,
                center = center,
                style = Stroke(stroke * 2.8f),
            )
            drawCircle(GoldLight, radius, center, style = Stroke(stroke * 0.55f))
            drawCircle(
                color = Gold.copy(alpha = 0.4f),
                radius = radius - stroke * 2.1f,
                center = center,
                style = Stroke(stroke * 0.3f),
            )
            rotate(phase * 0.5f, pivot = center) {
                bead(0f, Color.White, stroke * 0.9f)
                bead(180f, Gold, stroke * 0.55f)
            }
        }

        AvatarFrame.CIRCUIT -> rotate(phase * 0.35f, pivot = center) {
            repeat(4) { quarter ->
                val start = quarter * 90f + 6f
                arc(scheme.primary.copy(alpha = 0.8f), start, 64f, stroke * 0.7f)
                band(start, start, Gold, stroke * 1.4f, radius * 0.9f, radius)
                bead(start, Gold, stroke * 0.5f)
                bead(start + 70f, Gold, stroke * 0.5f)
                band(
                    start + 70f,
                    start + 70f,
                    scheme.tertiary,
                    stroke * 0.5f,
                    radius * 0.82f,
                    radius * 0.94f,
                )
            }
        }

        AvatarFrame.SAKURA -> rotate(phase * 0.4f, pivot = center) {
            repeat(6) { index ->
                rotate(index * 60f, pivot = center) {
                    drawOval(
                        color = SakuraPink.copy(alpha = 0.88f),
                        topLeft = Offset(center.x - stroke * 1.05f, center.y - radius - stroke * 0.45f),
                        size = Size(stroke * 2.1f, stroke * 1.7f),
                    )
                    drawOval(
                        color = Gold.copy(alpha = 0.7f),
                        topLeft = Offset(center.x - stroke * 0.22f, center.y - radius - stroke * 0.1f),
                        size = Size(stroke * 0.44f, stroke * 0.44f),
                    )
                }
            }
        }

        AvatarFrame.FROST -> rotate(-phase * 0.28f, pivot = center) {
            repeat(6) { index ->
                val angle = index * 60f
                band(angle, angle, Frost, stroke * 0.55f, radius * 0.52f, radius)
                band(angle - 7f, angle - 4f, Frost, stroke * 0.35f, radius * 0.78f, radius * 0.95f)
                band(angle + 7f, angle + 4f, Frost, stroke * 0.35f, radius * 0.78f, radius * 0.95f)
                bead(angle, Color.White, stroke * 0.4f)
            }
        }

        AvatarFrame.INFERNO -> {
            rotate(phase * 0.85f, pivot = center) {
                repeat(10) { index ->
                    val angle = index * 36f
                    arc(
                        color = if (index % 2 == 0) EmberOrange else EmberRed,
                        start = angle,
                        sweep = 24f,
                        width = if (index % 3 == 0) stroke * 1.3f else stroke * 0.75f,
                        alpha = if (index % 2 == 0) 0.95f else 0.65f,
                    )
                }
            }
            drawCircle(
                color = EmberHot.copy(alpha = 0.12f),
                radius = radius,
                center = center,
                style = Stroke(stroke * 2.2f),
            )
        }

        AvatarFrame.TIDE -> {
            rotate(phase * 0.45f, pivot = center) {
                arc(Teal.copy(alpha = 0.9f), -20f, 150f, stroke * 0.8f)
                arc(Teal.copy(alpha = 0.3f), 0f, 130f, stroke * 2.2f)
                repeat(6) { index ->
                    bead(
                        -10f + index * 28f,
                        if (index % 2 == 0) Frost else Teal,
                        stroke * 0.42f,
                    )
                }
            }
            rotate(-phase * 0.45f, pivot = center) {
                arc(scheme.primary.copy(alpha = 0.7f), 160f, 150f, stroke * 0.65f)
                repeat(5) { index -> bead(170f + index * 30f, scheme.primary, stroke * 0.32f) }
            }
        }

        AvatarFrame.STORM -> rotate(phase * 0.5f, pivot = center) {
            repeat(3) { index ->
                val base = index * 120f
                poly(
                    listOf(
                        at(base, radius * 0.98f),
                        at(base + 7f, radius * 0.84f),
                        at(base - 1f, radius * 0.9f),
                        at(base + 9f, radius * 0.77f),
                        at(base + 3f, radius * 0.99f),
                    ),
                    if (index == 1) Sun else StormBlue,
                    stroke * 0.5f,
                )
            }
        }

        AvatarFrame.VORTEX -> rotate(phase * 1.15f, pivot = center) {
            repeat(4) { index ->
                val ring = radius - index * stroke * 1.45f
                arcAt(
                    r = ring,
                    color = if (index % 2 == 0) scheme.primary else scheme.tertiary,
                    start = index * 15f,
                    sweep = 230f,
                    width = stroke * 0.7f,
                    alpha = 0.9f - index * 0.15f,
                )
            }
            bead(0f, Gold, stroke * 0.75f)
        }

        AvatarFrame.PRISM -> rotate(phase * 0.22f, pivot = center) {
            Prism.forEachIndexed { index, color ->
                arc(color, index * 60f, 52f, stroke * 0.85f)
            }
        }

        AvatarFrame.RUNE -> {
            rotate(phase * 0.2f, pivot = center) {
                repeat(8) { index ->
                    val angle = index * 45f
                    band(angle, angle, Gold.copy(alpha = 0.85f), stroke * 0.5f, radius * 0.78f, radius)
                    bead(angle + 22f, scheme.primary, stroke * 0.38f)
                }
            }
            drawCircle(
                color = Gold.copy(alpha = 0.3f),
                radius = radius * 0.66f,
                center = center,
                style = Stroke(stroke * 0.28f),
            )
        }

        AvatarFrame.STARFALL -> rotate(-phase * 0.65f, pivot = center) {
            repeat(5) { index ->
                val angle = index * 72f
                arc(Gold.copy(alpha = 0.22f), angle - 32f, 22f, stroke * 0.8f)
                star(
                    angle = angle,
                    outer = stroke * 1.25f,
                    inner = stroke * 0.32f,
                    color = if (index % 2 == 0) GoldLight else Color.White,
                    hubRadius = radius * 0.97f,
                )
            }
        }

        AvatarFrame.AURORA -> {
            val auroraBand = Brush.sweepGradient(
                listOf(Teal, NeonCyan, Violet, Teal),
                center,
            )
            rotate(phase * 0.3f, pivot = center) {
                drawCircle(
                    brush = auroraBand,
                    radius = radius,
                    center = center,
                    style = Stroke(stroke * 0.85f),
                )
            }
            rotate(-phase * 0.5f, pivot = center) {
                arc(Teal.copy(alpha = 0.45f), 0f, 100f, stroke * 2.2f)
                arc(Violet.copy(alpha = 0.35f), 170f, 90f, stroke * 1.8f)
            }
            bead(phase % 360f, Color.White, stroke * 0.5f)
        }

        AvatarFrame.SERPENT -> rotate(phase, pivot = center) {
            repeat(16) { index ->
                bead(
                    index * 22.5f,
                    if (index % 2 == 0) LeafGreen else Teal,
                    stroke * 0.4f,
                )
            }
            bead(0f, GoldLight, stroke * 1.15f)
            bead(18f, Gold, stroke * 0.6f)
        }

        AvatarFrame.ECLIPSE -> {
            drawCircle(
                color = scheme.onSurface.copy(alpha = 0.12f),
                radius = radius,
                center = center,
                style = Stroke(stroke * 2.2f),
            )
            rotate(phase * 0.2f, pivot = center) {
                arc(Color.White.copy(alpha = 0.8f), -80f, 160f, stroke * 0.65f)
                arc(EmberHot.copy(alpha = 0.7f), 100f, 150f, stroke)
                bead(-80f, GoldLight, stroke * 0.7f)
                bead(80f, Gold, stroke * 0.7f)
            }
        }

        AvatarFrame.ECHO -> {
            rotate(phase * 0.35f, pivot = center) {
                arc(scheme.primary.copy(alpha = 0.8f), 0f, 200f, stroke * 0.75f)
                arcAt(
                    radius - stroke * 2.1f,
                    scheme.primary.copy(alpha = 0.42f),
                    30f,
                    190f,
                    stroke * 0.42f,
                )
                bead(0f, scheme.tertiary, stroke * 0.5f)
            }
            rotate(-phase * 0.35f, pivot = center) {
                arcAt(
                    radius - stroke * 4f,
                    scheme.tertiary.copy(alpha = 0.3f),
                    70f,
                    170f,
                    stroke * 0.3f,
                )
            }
        }

        AvatarFrame.SHARDS -> rotate(phase * 0.75f, pivot = center) {
            repeat(12) { index ->
                val angle = index * 30f
                band(
                    angle,
                    angle,
                    if (index % 2 == 0) Gold else scheme.primary,
                    stroke * 0.6f,
                    radius * 0.86f,
                    radius,
                )
                bead(angle + 15f, Gold.copy(alpha = 0.45f), stroke * 0.22f)
            }
        }

        AvatarFrame.TWINKLE -> rotate(-phase * 0.35f, pivot = center) {
            repeat(8) { index ->
                val angle = index * 45f
                val twinkle = 0.55f + 0.45f * sin((phase + index * 45f) * PI.toFloat() / 180f)
                star(
                    angle = angle,
                    outer = stroke * 1.35f * twinkle,
                    inner = stroke * 0.3f * twinkle,
                    color = if (index % 2 == 0) GoldLight else Color.White,
                    alpha = 0.5f + 0.5f * twinkle,
                    hubRadius = radius * 0.97f,
                )
            }
        }

        AvatarFrame.CHAIN -> rotate(phase * 0.45f, pivot = center) {
            repeat(6) { index ->
                val angle = index * 60f
                arc(scheme.primary, angle, 40f, stroke * 0.7f)
                arcAt(
                    radius - stroke * 1.8f,
                    scheme.tertiary.copy(alpha = 0.75f),
                    angle + 20f,
                    40f,
                    stroke * 0.55f,
                )
                bead(angle, Gold, stroke * 0.3f)
            }
        }

        AvatarFrame.COMPASS -> {
            rotate(phase * 0.18f, pivot = center) {
                repeat(4) { index ->
                    val angle = index * 90f
                    val tip = at(angle)
                    val left = at(angle - 4f, radius * 0.72f)
                    val right = at(angle + 4f, radius * 0.72f)
                    val needle = Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(left.x, left.y)
                        lineTo(right.x, right.y)
                        close()
                    }
                    drawPath(needle, if (index % 2 == 0) Gold else scheme.primary)
                }
                repeat(8) { index ->
                    bead(index * 45f + 22.5f, Gold.copy(alpha = 0.45f), stroke * 0.22f)
                }
            }
        }

        AvatarFrame.HEARTBEAT -> rotate(phase * 0.35f, pivot = center) {
            arc(EmberRed.copy(alpha = 0.5f), -150f, 300f, stroke * 0.4f)
            val base = -60f
            poly(
                listOf(
                    at(base, radius * 0.99f),
                    at(base + 3f, radius * 0.99f),
                    at(base + 5f, radius * 0.84f),
                    at(base + 7f, radius * 0.95f),
                    at(base + 9f, radius * 0.82f),
                    at(base + 11f, radius * 0.99f),
                    at(base + 17f, radius * 0.99f),
                ),
                EmberHot,
                stroke * 0.55f,
            )
            bead(base, Color.White, stroke * 0.4f)
        }

        AvatarFrame.CINDER -> {
            rotate(phase * 0.55f, pivot = center) {
                repeat(14) { index ->
                    val angle = index * 25.7f
                    val lift = radius * (0.55f + 0.45f * abs(sin(index * 2.4f)))
                    bead(
                        angle,
                        if (index % 3 == 0) Gold else EmberOrange,
                        stroke * (0.22f + 0.16f * abs(sin(index * 1.7f))),
                        lift,
                    )
                }
            }
            repeat(3) { index ->
                bead(index * 120f + phase * 0.3f, EmberHot.copy(alpha = 0.45f), stroke * 0.32f)
            }
        }

        AvatarFrame.LOTUS -> rotate(phase * 0.3f, pivot = center) {
            repeat(8) { index ->
                rotate(index * 45f, pivot = center) {
                    drawOval(
                        color = Gold.copy(alpha = 0.7f),
                        topLeft = Offset(center.x - stroke * 0.8f, center.y - radius - stroke * 0.4f),
                        size = Size(stroke * 1.6f, stroke * 1.9f),
                    )
                }
            }
            repeat(6) { index ->
                rotate(index * 60f + 22f, pivot = center) {
                    drawOval(
                        color = SakuraPink.copy(alpha = 0.5f),
                        topLeft = Offset(
                            center.x - stroke * 0.65f,
                            center.y - radius + stroke * 0.45f,
                        ),
                        size = Size(stroke * 1.3f, stroke * 1.5f),
                    )
                }
            }
        }

        AvatarFrame.HEX -> {
            rotate(phase * 0.35f, pivot = center) {
                val points = (0 until 6).map { at(it * 60f) }
                val hex = Path()
                points.forEachIndexed { index, point ->
                    if (index == 0) hex.moveTo(point.x, point.y) else hex.lineTo(point.x, point.y)
                }
                hex.close()
                drawPath(hex, Gold, style = Stroke(stroke * 0.65f))
                points.forEachIndexed { index, point ->
                    drawCircle(if (index % 2 == 0) GoldLight else scheme.primary, stroke * 0.45f, point)
                }
            }
            rotate(-phase * 0.55f, pivot = center) {
                poly(
                    (0 until 3).map { at(it * 120f + 30f, radius * 0.64f) },
                    scheme.tertiary.copy(alpha = 0.55f),
                    stroke * 0.35f,
                )
            }
        }

        AvatarFrame.RIPPLE -> {
            rotate(phase * 0.4f, pivot = center) {
                arcAt(radius, scheme.primary, 0f, 140f, stroke * 0.75f)
                arcAt(
                    radius - stroke * 1.8f,
                    scheme.primary.copy(alpha = 0.5f),
                    40f,
                    120f,
                    stroke * 0.42f,
                )
                arcAt(
                    radius - stroke * 3.4f,
                    scheme.tertiary.copy(alpha = 0.3f),
                    80f,
                    100f,
                    stroke * 0.3f,
                )
            }
            rotate(-phase * 0.4f, pivot = center) {
                bead(0f, Gold, stroke * 0.5f)
                bead(140f, scheme.tertiary, stroke * 0.35f)
            }
        }

        AvatarFrame.BLADE -> {
            rotate(phase * 0.7f, pivot = center) {
                repeat(4) { index ->
                    val angle = index * 90f
                    arcAt(
                        radius,
                        if (index % 2 == 0) Color.White.copy(alpha = 0.85f) else scheme.primary,
                        angle,
                        58f,
                        stroke,
                    )
                    bead(angle + 58f, Gold, stroke * 0.45f)
                }
            }
            drawCircle(
                color = scheme.primary.copy(alpha = 0.15f),
                radius = radius - stroke * 2f,
                center = center,
                style = Stroke(stroke * 0.28f),
            )
        }

        AvatarFrame.METEOR -> rotate(-phase, pivot = center) {
            repeat(3) { index ->
                val angle = index * 120f
                band(angle - 26f, angle, Gold.copy(alpha = 0.45f), stroke * 0.8f)
                band(angle - 34f, angle - 20f, Gold.copy(alpha = 0.2f), stroke * 1.6f)
                bead(angle, Color.White, stroke * 0.7f)
            }
        }

        AvatarFrame.QUASAR -> {
            rotate(phase * 0.25f, pivot = center) {
                drawOval(
                    brush = Brush.sweepGradient(
                        listOf(scheme.primary, scheme.tertiary, scheme.primary),
                        center,
                    ),
                    topLeft = Offset(center.x - radius, center.y - radius * 0.42f),
                    size = Size(radius * 2f, radius * 0.84f),
                    style = Stroke(stroke * 0.65f),
                )
                bead(0f, GoldLight, stroke * 0.5f)
            }
            rotate(-phase * 0.25f, pivot = center) {
                rotate(58f, pivot = center) {
                    drawOval(
                        color = scheme.tertiary.copy(alpha = 0.5f),
                        topLeft = Offset(center.x - radius * 0.96f, center.y - radius * 0.36f),
                        size = Size(radius * 1.92f, radius * 0.72f),
                        style = Stroke(stroke * 0.4f),
                    )
                }
                bead(58f, scheme.primary, stroke * 0.35f)
            }
        }

        AvatarFrame.ZENITH -> {
            rotate(-phase * 0.4f, pivot = center) {
                repeat(4) { index ->
                    star(
                        angle = index * 90f,
                        outer = stroke * 1.5f,
                        inner = stroke * 0.36f,
                        color = if (index % 2 == 0) Gold else Color.White,
                        hubRadius = radius * 0.86f,
                    )
                }
            }
            rotate(phase * 0.6f, pivot = center) {
                drawCircle(
                    color = scheme.primary.copy(alpha = 0.3f),
                    radius = radius - stroke * 3f,
                    center = center,
                    style = Stroke(stroke * 0.3f),
                )
                bead(0f, scheme.primary, stroke * 0.35f, radius - stroke * 3f)
            }
        }

        // The pack rings returned above; nothing else reaches this branch.
        else -> Unit
    }
}
