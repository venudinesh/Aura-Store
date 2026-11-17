package dev.aurastore.store.ui.profile

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import dev.aurastore.store.data.AvatarFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The frame's slow turn, or a still frame when the phone asked for less motion.
 *
 * Only a frame that actually moves asks for a transition. The top bar turns with the header now:
 * a ring that stops when it leaves the profile page read as a different picture, not a smaller one.
 */
@Composable
internal fun rememberFramePhase(frame: AvatarFrame, animate: Boolean): Float {
    if (!animate || !frame.animated) return 0f
    val transition = rememberInfiniteTransition(label = "avatar-frame")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "avatar-frame-phase",
    )
    return phase
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

    fun arc(
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
            topLeft = ringTopLeft,
            size = ringSize,
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }

    fun bead(angle: Float, color: Color, dot: Float) {
        val radians = angle * PI.toFloat() / 180f
        drawCircle(
            color = color,
            radius = dot,
            center = Offset(
                center.x + radius * cos(radians),
                center.y + radius * sin(radians),
            ),
        )
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
    }
}
