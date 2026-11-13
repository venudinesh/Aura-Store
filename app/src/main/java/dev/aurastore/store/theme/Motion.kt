package dev.aurastore.store.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt

/**
 * The storefront's motion, ported from the Play Store's timings.
 *
 * Play animates with the Material 3 motion tokens rather than the flat `spring()` Compose defaults to:
 * an emphasized deceleration for anything arriving, a faster emphasized acceleration for anything
 * leaving, and a duration that says how far the thing travelled. A chip's colour and a page's entrance
 * therefore take different times even though both are "animated", and the app reads as one system.
 *
 * Every duration goes through [PlayMotion.ms], which is the platform's "Animator duration scale"
 * applied to the token, so a device that asked for no motion gets none anywhere rather than half a
 * system still animating.
 */

/** The M3 "emphasized" spline: slow to settle, with a little authority at the start. */
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Anything entering or moving into place, which is most of what Play animates. */
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Anything leaving: it accelerates away, so it is gone well before the newcomer settles. */
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Small, in-place changes — a chip filling, a colour settling. */
val StandardEasing = CubicBezierEasing(0.2f, 0f, 0.1f, 1f)

/** The tail of [StandardEasing], for a value that only has to arrive. */
val StandardDecelerateEasing = CubicBezierEasing(0f, 0f, 0f, 1f)

/** The head of [StandardEasing], for a value that only has to leave. */
val StandardAccelerateEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

object PlayMotion {
    /** Press and release feedback — long enough to see, short enough to feel instant. */
    const val MICRO = 100

    /** A colour, an icon, a chip's check. */
    const val SHORT = 150

    /** A control's own change: a status line swapping, a row expanding. */
    const val MEDIUM = 250

    /** Two pages sliding past each other. */
    const val LONG = 300

    /** A card opening into its page: the container transform, and the icon riding it. */
    const val SCREEN = 400

    /** The same page leaving, which never needs as long as arriving did. */
    const val SCREEN_EXIT = 250

    /**
     * The device's animation scale, where `0f` means the user turned motion off.
     *
     * A plain var rather than a composition local because [scheme] is read from inside animation specs,
     * which are not composable — and because a test can set it, which a `Settings.Global` read cannot.
     */
    @Volatile
    var scale: Float = 1f
        private set

    /** Reads the platform option once, at startup. A change while running takes the next launch. */
    fun syncFrom(context: Context) {
        scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f).coerceIn(0f, 2f)
    }

    /** A duration this device will actually run: scaled, and zero when motion is switched off. */
    fun ms(baseMs: Int): Int = if (scale <= 0f) 0 else (baseMs * scale).roundToInt()

    /** A token turned into a spec. Zero scale means "be there already", not "arrive quickly". */
    fun <T> spec(baseMs: Int, easing: Easing = StandardEasing): FiniteAnimationSpec<T> =
        if (ms(baseMs) <= 0) snap() else tween(ms(baseMs), easing = easing)

    fun <T> spec(baseMs: Int, baseDelayMs: Int, easing: Easing): FiniteAnimationSpec<T> =
        if (ms(baseMs) <= 0) snap() else tween(ms(baseMs), ms(baseDelayMs), easing)

    val enabled: Boolean get() = scale > 0f
}

/** The spec for a value that animates in place, with the token and spline Play uses for it. */
fun <T> motionTween(
    baseMs: Int,
    easing: Easing = StandardEasing,
    baseDelayMs: Int = 0,
): FiniteAnimationSpec<T> = PlayMotion.spec(baseMs, baseDelayMs, easing)
