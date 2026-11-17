package dev.aurastore.store.ui.backdrop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import dev.aurastore.store.data.BackgroundTheme
import dev.aurastore.store.theme.PlayMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * True while an animated wallpaper is painting behind the shell.
 *
 * Pages leave their own background unpainted so the wallpaper shows through; the shell reads this to
 * make the scaffold transparent. It is a composition local rather than a settings read because the
 * answer is already known at the top of the activity, and every page should agree with the backdrop
 * that was actually composed beside it.
 */
val LocalBackdropActive = staticCompositionLocalOf { false }

/**
 * The store's weather: snow, fireworks or a slow four-colour drift.
 *
 * The three effects follow the holiday effects of the open-source Telegram clients in spirit only;
 * the drawing and the motion here are written from scratch against Compose's canvas. Everything is a
 * function of the clock, so [animate] off gives a still frame instead of a paused object graph, and a
 * region smaller than a phone (the picker's tiles) shows the same scene at its own scale.
 *
 * The canvas reads the clock from inside the draw pass, so a tick invalidates the drawing without
 * recomposing the page underneath it. [MOTION] is deliberately throttled: four full-screen gradients
 * at display rate is work a slow drift does not need.
 *
 * [amoled] is a separate switch, not a theme: when it is on the page's base is true black so those
 * pixels can go dark, and whatever weather is chosen still draws over it.
 */
@Composable
fun TelegramBackdrop(
    theme: BackgroundTheme,
    modifier: Modifier = Modifier,
    animate: Boolean = PlayMotion.enabled,
    amoled: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interval = if (theme == BackgroundTheme.MOTION) 1f / 30f else 0f
    val clock = rememberBackdropClock(animate = animate && theme != BackgroundTheme.NONE, interval = interval)
    val still = remember(theme) { theme.stillTime }

    Canvas(modifier.fillMaxSize()) {
        drawBackdrop(theme = theme, time = if (animate) clock.floatValue else still, scheme = scheme, amoled = amoled)
    }
}

/** Draws one frame. The time is seconds since the effect started, at whatever pace the caller chose. */
private fun DrawScope.drawBackdrop(
    theme: BackgroundTheme,
    time: Float,
    scheme: ColorScheme,
    amoled: Boolean,
) {
    // True black is the whole point of AMOLED: those pixels are off, so the base cannot be the
    // scheme's near-black night colour. Every other page sits on its own paper.
    drawRect(if (amoled) Color.Black else scheme.background)
    when (theme) {
        BackgroundTheme.NONE -> Unit
        BackgroundTheme.SNOW -> drawSnow(time, scheme)
        BackgroundTheme.FIREWORKS -> drawFireworks(time, scheme)
        BackgroundTheme.MOTION -> drawMotion(time, scheme)
    }
}

// --------------------------------------------------------------------------------------------- snow

/** Cold air, then flakes: six-armed stars when large, plain dots when small. */
private fun DrawScope.drawSnow(time: Float, scheme: ColorScheme) {
    drawRect(
        Brush.verticalGradient(
            listOf(
                scheme.primary.copy(alpha = 0.05f),
                Color.Transparent,
                scheme.tertiary.copy(alpha = 0.05f),
            ),
        ),
    )
    val dark = scheme.background.luminance() < 0.4f
    // White on a night page, a deep winter blue on a paper one: the same snow, visible on both.
    val ink = if (dark) Color.White else Color(0xFF56718F)
    val dp = density
    val count = snowflakeCount(size.width, size.height, dp)
    for (index in 0 until count) {
        val flake = snowflakeAt(index, time, size.width, size.height, dp)
        val colour = ink.copy(alpha = flake.alpha)
        if (flake.arms) {
            val arm = flake.radius * 1.5f
            for (branch in 0 until 3) {
                val angle = flake.spin + branch * (PI / 3.0).toFloat()
                val dx = cos(angle) * arm
                val dy = sin(angle) * arm
                drawLine(
                    color = colour,
                    start = Offset(flake.x - dx, flake.y - dy),
                    end = Offset(flake.x + dx, flake.y + dy),
                    strokeWidth = max(1f, dp * 0.6f),
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(
                color = colour.copy(alpha = flake.alpha * 0.55f),
                radius = flake.radius * 0.55f,
                center = Offset(flake.x, flake.y),
            )
        } else {
            drawCircle(colour, radius = flake.radius, center = Offset(flake.x, flake.y))
        }
    }
}

// ---------------------------------------------------------------------------------------- fireworks

/** The five bursts Telegram's fireworks cycle through, lifted to read on paper and on night. */
private val BurstPalette = listOf(
    Color(0xFF6E8BFF),
    Color(0xFFFF7A6E),
    Color(0xFFFFD166),
    Color(0xFF58D68D),
    Color(0xFFD08BFF),
)

/** Seconds between launches. Short enough to keep the page alive, long enough to read each burst. */
private const val BURST_PERIOD = 2.6f

/** How long one burst stays in the air. */
private const val BURST_LIFE = 1.35f

private const val TWO_PI = (2.0 * PI).toFloat()

/** A spark's downward pull, in dp per second squared. */
private const val SPARK_GRAVITY = 120f

private fun DrawScope.drawFireworks(time: Float, scheme: ColorScheme) {
    drawRect(
        Brush.verticalGradient(
            listOf(scheme.primary.copy(alpha = 0.07f), Color.Transparent),
        ),
    )
    val dp = density
    val current = floor(time / BURST_PERIOD).toInt()
    // Three slots, because a burst lives about half a period and one can still be fading when the
    // next is lit. Negative slots are the quiet moment before the first launch.
    for (back in 0..2) {
        val index = current - back
        if (index < 0) continue
        val start = index * BURST_PERIOD + 0.30f + 0.55f * hash01(index * 31 + 1)
        val age = time - start
        if (age < 0f || age > BURST_LIFE) continue
        val centre = Offset(
            x = (0.18f + 0.64f * hash01(index * 31 + 2)) * size.width,
            y = (0.16f + 0.36f * hash01(index * 31 + 3)) * size.height,
        )
        val colour = BurstPalette[
            (hash01(index * 31 + 4) * BurstPalette.size).toInt().coerceIn(0, BurstPalette.lastIndex)
        ]

        // The report: an expanding ring for the first half second, so the eye finds the burst
        // before the sparks have travelled far enough to be seen.
        val ringLife = 0.5f
        if (age < ringLife) {
            val fade = 1f - age / ringLife
            drawCircle(
                color = colour.copy(alpha = fade * 0.32f),
                radius = (4f + age * 110f) * dp,
                center = centre,
                style = Stroke(width = dp * 1.1f),
            )
        }

        val sparks = 12
        for (spark in 0 until sparks) {
            val angle = spark.toFloat() / sparks * TWO_PI + hash01(index * 31 + 10 + spark) * 0.5f
            val speed = (65f + 85f * hash01(index * 31 + 40 + spark)) * dp
            val life = 0.85f + 0.50f * hash01(index * 31 + 70 + spark)
            if (age > life) continue
            val fade = (1f - age / life).coerceIn(0f, 1f)
            val alpha = fade * fade
            val head = ballistic(centre, angle, speed, age, SPARK_GRAVITY * dp)
            val tail = ballistic(centre, angle, speed, (age - 0.07f).coerceAtLeast(0f), SPARK_GRAVITY * dp)
            drawLine(
                color = colour.copy(alpha = alpha),
                start = tail,
                end = head,
                strokeWidth = dp * 1.2f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = colour.copy(alpha = alpha),
                radius = dp * 1.5f * fade.coerceAtLeast(0.3f),
                center = head,
            )
        }
    }
}

/** A spark's offset from its burst after [age] seconds, with gravity bending it back down. */
private fun ballistic(origin: Offset, angle: Float, speed: Float, age: Float, gravity: Float): Offset =
    Offset(
        x = origin.x + cos(angle) * speed * age,
        y = origin.y + sin(angle) * speed * age + 0.5f * gravity * age * age,
    )

// ------------------------------------------------------------------------------------------ motion

/**
 * Four large radial washes drifting on slow, out-of-step orbits.
 *
 * The centres use different speeds and phases so the field never returns to the same arrangement;
 * at 0.07 to 0.13 rad/s a full traverse takes the better part of a minute. The alpha is stronger on
 * a night page, where colour has room, and muted on paper so text keeps its contrast.
 */
private fun DrawScope.drawMotion(time: Float, scheme: ColorScheme) {
    val dark = scheme.background.luminance() < 0.4f
    val strength = if (dark) 1f else 0.55f
    val radius = max(size.width, size.height) * 0.62f

    fun blob(colour: Color, phase: Float, ax: Float, ay: Float, speed: Float, alpha: Float) {
        val centre = Offset(
            x = (0.5f + ax * sin(time * speed + phase)) * size.width,
            y = (0.5f + ay * cos(time * speed * 0.83f + phase * 1.7f)) * size.height,
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(colour.copy(alpha = alpha * strength), colour.copy(alpha = 0f)),
                center = centre,
                radius = radius,
            ),
        )
    }

    blob(scheme.primary, 0.4f, 0.30f, 0.26f, 0.110f, 0.30f)
    blob(scheme.tertiary, 2.3f, 0.34f, 0.22f, 0.085f, 0.26f)
    blob(scheme.secondary, 4.1f, 0.26f, 0.30f, 0.130f, 0.22f)
    blob(scheme.primaryContainer, 5.6f, 0.30f, 0.24f, 0.070f, 0.20f)
}

// ------------------------------------------------------------------------------------------- clock

/**
 * A monotonic seconds clock that only recomposes the drawing, not the page.
 *
 * [interval] above zero throttles updates by skipping frames; zero updates every frame. The state is
 * read inside the canvas draw, so a tick invalidates only that drawing.
 */
@Composable
private fun rememberBackdropClock(animate: Boolean, interval: Float): MutableFloatState {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, interval) {
        if (!animate) {
            clock.floatValue = 0f
            return@LaunchedEffect
        }
        val origin = withFrameNanos { it }
        var last = 0L
        val gap = (interval * 1_000_000_000L).toLong()
        while (true) {
            withFrameNanos { now ->
                if (now - last >= gap) {
                    last = now
                    clock.floatValue = (now - origin) / 1_000_000_000f
                }
            }
        }
    }
    return clock
}

/**
 * The moment each effect is frozen at when motion is off.
 *
 * Chosen, not arbitrary: snow part-way down a full field, fireworks with a burst in the air, and the
 * drift in an arrangement where all four colours are on screen.
 */
private val BackgroundTheme.stillTime: Float
    get() = when (this) {
        BackgroundTheme.NONE -> 0f
        BackgroundTheme.SNOW -> 13.4f
        BackgroundTheme.FIREWORKS -> 1.05f
        BackgroundTheme.MOTION -> 9f
    }
