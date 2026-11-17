package dev.aurastore.store.ui.backdrop

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The snow, as arithmetic rather than as a flock of objects.
 *
 * Each flake is a pure function of its index and the clock: where it is now follows from where it
 * started, how fast it falls and how far it sways. Nothing is carried between frames, so a rotation
 * draws the same field instead of restarting it, and the picker's still tile is the same renderer at
 * a fixed moment rather than a second implementation that would drift from the live one.
 */
internal data class Snowflake(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    /** How far the arms of a large flake are turned at this instant. */
    val spin: Float,
    /** Large flakes are drawn as six arms; small ones are a single dot. */
    val arms: Boolean,
)

/** The most flakes a page may carry, however large the screen. */
private const val MAX_FLAKES = 170

/** The fewest a region may carry, so even a tile reads as snowfall rather than as a stray dot. */
private const val MIN_FLAKES = 12

/** One flake per this many square dp: a snowfall, not a blizzard, on a phone-sized page. */
private const val DP_AREA_PER_FLAKE = 2600f

private const val TWO_PI = (2.0 * PI).toFloat()

/**
 * How many flakes a region of this size should carry.
 *
 * Counted by area rather than by width so a short, wide stage and a tall page read at the same
 * density, and clamped at both ends so a thumbnail still shows weather and a tablet does not turn
 * into static. Zero or nonsense dimensions carry nothing.
 */
internal fun snowflakeCount(widthPx: Float, heightPx: Float, dp: Float): Int {
    if (widthPx <= 0f || heightPx <= 0f || dp <= 0f) return 0
    val areaDp = (widthPx / dp) * (heightPx / dp)
    return (areaDp / DP_AREA_PER_FLAKE).roundToInt().coerceIn(MIN_FLAKES, MAX_FLAKES)
}

/**
 * Where flake [index] is at [time] seconds, in pixels inside [width] x [height].
 *
 * The fall wraps from the bottom back to the top, and the sway is a sine of the same clock, so the
 * field never repeats as a whole even though each flake is cheap to evaluate. [dp] is the density in
 * pixels per dp, which is what keeps a flake the same physical size on every screen.
 */
internal fun snowflakeAt(
    index: Int,
    time: Float,
    width: Float,
    height: Float,
    dp: Float,
): Snowflake {
    // Every field reads a different slot of the same integer hash. The stride keeps consecutive
    // flakes from sharing a lane or a speed, which is what makes a hash-driven field look scattered.
    val speed = 0.030f + 0.070f * hash01(index * 13 + 1)
    val phase = hash01(index * 13 + 2)
    val lane = hash01(index * 13 + 3)
    val swayAmplitude = (5f + 18f * hash01(index * 13 + 4)) * dp
    val swayRate = 0.20f + 0.50f * hash01(index * 13 + 5)
    val radius = (0.8f + 2.0f * hash01(index * 13 + 6)) * dp
    val alpha = 0.22f + 0.52f * hash01(index * 13 + 7)
    val spinRate = 0.25f + 0.50f * hash01(index * 13 + 8)

    // A flake starts at a phase along its own fall and wraps at the bottom. The span is a radius
    // taller than the page at each end so a flake is never seen popping into existence.
    val span = height + radius * 2f
    val drop = positiveFraction(phase + time * speed)
    val y = drop * span - radius
    val x = lane * width + sin((time * swayRate + phase * 4f) * TWO_PI) * swayAmplitude
    return Snowflake(
        x = x,
        y = y,
        radius = radius,
        alpha = alpha,
        spin = phase * TWO_PI + time * spinRate,
        arms = radius >= 1.9f * dp,
    )
}

/** [value] folded into `[0, 1)`, including for a negative clock or a wrapped phase. */
private fun positiveFraction(value: Float): Float = ((value % 1f) + 1f) % 1f

/**
 * A repeatable number in `[0, 1)` from an integer seed.
 *
 * This is the whole random source for the backdrop: no `Random`, no allocation, and the same seed
 * gives the same flake on every phone and every frame. The three xorshift-multiply rounds are the
 * usual 32-bit integer mixers; the top 24 bits become the fraction.
 */
internal fun hash01(seed: Int): Float {
    var x = seed * GOLDEN
    x = x xor (x ushr 16)
    x *= 0x7FEB352D
    x = x xor (x ushr 15)
    x *= 0x846CA68B.toInt()
    x = x xor (x ushr 16)
    return (x ushr 8) / 16_777_216f
}

/** 2^32 / the golden ratio, as the signed int Kotlin overflows to. */
private val GOLDEN = 0x9E3779B1.toInt()
