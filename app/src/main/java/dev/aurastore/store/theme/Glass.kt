package dev.aurastore.store.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.aurastore.store.data.GlassEngine

/**
 * The numbers behind the navigation bar's settings.
 *
 * The bar is a window of its own over the activity ([dev.aurastore.store.ui.FloatingBarSurface]), so
 * wherever it paints nothing the page shows through untouched. Every control below is a 0..1 fraction
 * that starts at "no material" and adds one layer: a blurred copy of the page, a colour fringe, a
 * magnified lens band, or a piece of geometry. See `docs/NAVBAR_SETTINGS.md` for the reference these
 * controls were modelled on and for what is deliberately not copied.
 */

/** Blur intensity, in dp, where the platform has a render effect (Android 12+). */
fun glassBlurDp(blur: Float): Float = 20f * blur.coerceIn(0f, 1f)

/**
 * How far each colour fringe sits outside the sheet's edge, in dp.
 *
 * Two copies of the rim are drawn, one red and one cyan, on opposite sides of the line; at 2.4 dp the
 * pair reads as the colour split of a thick lens rather than as a printing error.
 */
fun glassChromaticDp(chromatic: Float): Float = 2.4f * chromatic.coerceIn(0f, 1f)

/** How much the sampled page is magnified: 1 at zero, 1.06 at full refraction. */
fun glassRefractionScale(refraction: Float): Float = 1f + 0.06f * refraction.coerceIn(0f, 1f)

/**
 * How far the shader engine bends the page at the rim and under its lens, in dp.
 *
 * [glassRefractionScale] is what the drawn engines can do to a bitmap; a shader can move individual
 * pixels, so the same slider is worth a visible 16 dp instead of six per cent.
 */
fun glassRefractionShiftDp(refraction: Float): Float = 16f * refraction.coerceIn(0f, 1f)

/** How bright the light entering the lens is drawn. */
fun glassSheenAlpha(refraction: Float): Float = 0.18f * refraction.coerceIn(0f, 1f)

/**
 * Whether this material needs a copy of the page at all.
 *
 * Blur is the obvious reason, but it is not the only one: the iOS 13 shader reads the copy to bend
 * it and to split its channels, and the iOS 8 engine magnifies it. Gating those behind Blur made two
 * sliders do nothing when Blur was at zero, which is indistinguishable from a control that was never
 * wired. Classic is the exception — its refraction is the drawn sheen, so a copy would add nothing.
 *
 * A copy taken for refraction alone is unblurred, which is what a lens over text should be; Blur
 * decides how soft it is, not whether there is one. A value of zero on every effect still reads
 * nothing, so the shipped bar remains the untouched page.
 */
fun glassReadsBackdrop(
    liquid: Boolean,
    blur: Float,
    refraction: Float,
    chromatic: Float,
    engine: GlassEngine,
): Boolean {
    if (!liquid) return false
    if (glassBlurDp(blur) >= 1f) return true
    return when (engine) {
        GlassEngine.IOS_13 -> refraction > 0f || chromatic > 0f
        GlassEngine.IOS_8 -> refraction > 0f
        GlassEngine.CLASSIC -> false
    }
}

/** How far down the sheet the lens band reaches, as a fraction of its height. */
fun glassLensBand(depth: Float): Float = 0.18f + 0.52f * depth.coerceIn(0f, 1f)

/** Corner roundness, 0..36 dp. Only used while the bar floats. */
fun glassCornerDp(corner: Float): Float = 36f * corner.coerceIn(0f, 1f)

/**
 * The widest the sheet may be, as a fraction of the content width: 60 % at zero, 150 % at one.
 *
 * A cap rather than a width: the sheet is drawn as wide as its five labels need ([FloatingBar.pillWidth]),
 * so a pill stays a pill on every screen, squeezes when dragged down to 60 %, and expands up to 150 %.
 */
fun glassWidthFraction(width: Float): Float = 0.6f + 0.9f * width.coerceIn(0f, 1f)

/** The gap under a floating bar, 0..24 dp. */
fun glassBottomGapDp(offset: Float): Float = 24f * offset.coerceIn(0f, 1f)

/**
 * What a glass edge is drawn in, and how much of the computed alpha it needs to read at all.
 *
 * On a dark page the edge is light: that is the rim catching the room. On a light scheme a white rim on
 * a white sheet is nothing, so the edge is drawn dark instead — the shadow a bevel throws on itself —
 * and at a fraction of the alpha, because black shows far harder than white does.
 */
data class GlassEdge(val color: Color, val scale: Float)

fun glassEdge(tint: Color): GlassEdge =
    if (tint.luminance() > 0.5f) GlassEdge(Color.Black, 0.45f) else GlassEdge(Color.White, 1f)
