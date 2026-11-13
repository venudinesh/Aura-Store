package dev.aurastore.store.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The corner scale.
 *
 * Radii used to be written inline at the point of need, which produced eight different values across
 * the app — including a 19.dp and a 22.dp that were each an eyeballed one-off for one screen. They
 * are now five steps that mean something: tighter as you go inward, and a radius chosen for what the
 * surface *is* rather than for what looked right in the editor that day.
 *
 * App art keeps its own squircle: catalogue icons arrive pre-shaped and rounding them again fights
 * the source.
 */
val StoreShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** A control the height of a pill — the install button, the segment switch, filter chips. */
val PillShape: Shape = RoundedCornerShape(percent = 50)

/** Cards that hold app art: the icon's own corners win. */
val ArtShape: Shape = RoundedCornerShape(14.dp)

/**
 * The split install control: a wide action butted against a narrow build-chooser.
 *
 * Each half is round on the edge that faces outward and nearly square on the edge that faces the
 * other half, so the pair reads as one pill with a seam rather than as two separate buttons. Both
 * numbers come off the scale — [StoreRadii.splitOuter] is `extraLarge`, [StoreRadii.splitInner] is
 * `extraSmall` — which makes the pair a proportion instead of two figures chosen by eye. Written
 * inline at three call sites before it was pulled here.
 */
val SplitActionShape: Shape = RoundedCornerShape(
    topStart = StoreRadii.splitOuter,
    bottomStart = StoreRadii.splitOuter,
    topEnd = StoreRadii.splitInner,
    bottomEnd = StoreRadii.splitInner,
)

/** The chooser half: the same shape, mirrored. */
val SplitChooserShape: Shape = RoundedCornerShape(
    topEnd = StoreRadii.splitOuter,
    bottomEnd = StoreRadii.splitOuter,
    topStart = StoreRadii.splitInner,
    bottomStart = StoreRadii.splitInner,
)

internal object StoreRadii {
    val splitOuter: Dp = 24.dp
    val splitInner: Dp = 4.dp
}
