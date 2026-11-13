package dev.aurastore.store.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import dev.aurastore.store.theme.RatingStar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The glyphs the storefront needs, drawn as paths.
 *
 * The Play Store's iconography is geometric and thin-stroked, so it redraws cleanly here without
 * adding the (very large) extended Material icon artifact to the build. Every path is expressed in
 * unit space and scaled to the requested size.
 */
enum class Glyph {
    Home, Search, Grid, Star, ChevronRight, ChevronLeft, Back, Check, Close, More,
    Download, Pause, Play, Person, Update, Shield, Brand, Info, Share, ArrowDown, Tick,
    Gamepad, Library, Trash, Copy,
}

/**
 * A glyph. [label] is what a screen reader says: a `Canvas` path is invisible to TalkBack otherwise, so
 * every glyph that *is* the control — a back arrow, a tab pill, the queue badge — needs one. A glyph
 * sitting next to its own text stays unlabelled, or the screen reads the same word twice.
 */
@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    filled: Boolean = false,
    /**
     * 0 is the outline and 1 the solid mark; the bar animates it as a tab is selected.
     *
     * A fraction rather than a switch because the fill is motion, not a state: the glyph solidifies
     * while it pops, so a tap reads as one gesture instead of two changes landing apart.
     */
    fill: Float = 0f,
    /** The surface a solid glyph's details are cut out of — the selection pill behind it. */
    cutout: Color = Color.Transparent,
    strokeWidth: Dp = 2.dp,
    label: String? = null,
) {
    // `filled` predates the fraction, and a caller that only wants a solid glyph should not have to
    // know about the animation seam it left behind.
    val solid = if (filled) 1f else fill.coerceIn(0f, 1f)
    Canvas(
        if (label == null) modifier.size(size)
        else modifier.clearAndSetSemantics { contentDescription = label }.size(size),
    ) { drawGlyph(glyph, tint, solid, cutout, strokeWidth.toPx()) }
}

/** Rating stars: a value like 4.4 renders as four full stars and a half. */
@Composable
fun RatingStars(
    rating: Double,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    tint: Color = RatingStar,
    emptyTint: Color = LocalContentColor.current.copy(alpha = 0.35f),
) {
    Row(modifier) {
        for (i in 0 until 5) {
            val fraction = (rating - i).coerceIn(0.0, 1.0)
            val color = if (fraction >= 0.5) tint else emptyTint
            Canvas(Modifier.size(size)) {
                val star = starPath(this.size.width, this.size.height)
                drawPath(star, color = color)
                if (fraction in 0.01..0.49) {
                    clipRect(right = this.size.width * fraction.toFloat()) { drawPath(star, color = tint) }
                }
            }
        }
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, tint: Color, fill: Float, cutout: Color, stroke: Float) {
    val w = unit(size.width)
    val h = unit(size.height)
    val strokeStyle = Stroke(width = stroke, cap = StrokeCap.Round)
    val r = androidx.compose.ui.geometry.CornerRadius(stroke, stroke)

    fun p(x: Float, y: Float) = Offset(w(x), h(y))

    when (glyph) {
        Glyph.Home -> {
            drawPath(
                Path().apply {
                    moveTo(w(0.12f), h(0.46f)); lineTo(w(0.5f), h(0.12f)); lineTo(w(0.88f), h(0.46f))
                },
                color = tint, style = strokeStyle,
            )
            drawRoundRect(tint, p(0.22f, 0.44f), Size(w(0.56f), h(0.42f)), r, style = strokeStyle)
        }

        Glyph.Search -> {
            // The lens tilts and sweeps as the tab is chosen, and a specular glint arcs across the
            // glass while the handle thickens into a solid grip.
            val sweep = kotlin.math.sin(fill * Math.PI.toFloat())
            val center = p(0.43f, 0.42f)
            val lensRadius = w(0.27f + 0.015f * fill)
            rotate(degrees = -12f * sweep, pivot = p(0.5f, 0.5f)) {
                if (fill > 0.01f) {
                    drawCircle(
                        color = tint.copy(alpha = tint.alpha * 0.18f * fill),
                        radius = lensRadius - stroke * 0.3f,
                        center = center,
                    )
                    // Specular glint arc inside the magnifying glass
                    drawArc(
                        color = tint.copy(alpha = tint.alpha * (0.5f + 0.5f * fill)),
                        startAngle = 200f,
                        sweepAngle = 72f * fill,
                        useCenter = false,
                        topLeft = Offset(center.x - lensRadius * 0.58f, center.y - lensRadius * 0.58f),
                        size = Size(lensRadius * 1.16f, lensRadius * 1.16f),
                        style = Stroke(width = stroke * 0.72f, cap = StrokeCap.Round),
                    )
                }
                drawCircle(tint, radius = lensRadius, center = center, style = strokeStyle)
                drawLine(
                    tint,
                    p(0.62f, 0.61f),
                    p(0.86f, 0.85f),
                    stroke * (1f + 0.32f * fill),
                    StrokeCap.Round,
                )
            }
        }

        Glyph.Grid -> {
            val s = Size(w(0.32f), h(0.32f))
            val cellCorner = CornerRadius(w(0.08f + 0.02f * fill), w(0.08f + 0.02f * fill))
            // Top-right tile rotates into a floating diamond badge as the tab is selected, while
            // the remaining three tiles solidify into rounded app squircles.
            val baseCells = listOf(0.12f to 0.12f, 0.12f to 0.56f, 0.56f to 0.56f)
            if (fill > 0.01f) {
                baseCells.forEach { (x, y) ->
                    drawRoundRect(tint.copy(alpha = tint.alpha * fill), p(x, y), s, cellCorner)
                }
            }
            baseCells.forEach { (x, y) ->
                drawRoundRect(tint, p(x, y), s, cellCorner, style = strokeStyle)
            }
            val badgePivot = p(0.72f, 0.28f)
            withTransform({
                rotate(degrees = 45f * fill, pivot = badgePivot)
                val badgeScale = 1f - 0.08f * fill
                scale(badgeScale, badgeScale, pivot = badgePivot)
            }) {
                if (fill > 0.01f) {
                    drawRoundRect(
                        tint.copy(alpha = tint.alpha * fill),
                        p(0.56f, 0.12f),
                        s,
                        cellCorner,
                    )
                }
                drawRoundRect(tint, p(0.56f, 0.12f), s, cellCorner, style = strokeStyle)
            }
        }

        Glyph.Star -> drawPath(starPath(w(1f), h(1f)), color = tint)

        Glyph.ChevronRight -> drawPath(
            Path().apply { moveTo(w(0.38f), h(0.22f)); lineTo(w(0.64f), h(0.5f)); lineTo(w(0.38f), h(0.78f)) },
            color = tint, style = strokeStyle,
        )

        Glyph.ChevronLeft -> drawPath(
            Path().apply { moveTo(w(0.62f), h(0.22f)); lineTo(w(0.36f), h(0.5f)); lineTo(w(0.62f), h(0.78f)) },
            color = tint, style = strokeStyle,
        )

        Glyph.Back -> {
            drawLine(tint, p(0.16f, 0.5f), p(0.86f, 0.5f), stroke, StrokeCap.Round)
            drawPath(
                Path().apply { moveTo(w(0.42f), h(0.18f)); lineTo(w(0.12f), h(0.5f)); lineTo(w(0.42f), h(0.82f)) },
                color = tint, style = strokeStyle,
            )
        }

        Glyph.Tick, Glyph.Check -> drawPath(
            Path().apply { moveTo(w(0.18f), h(0.54f)); lineTo(w(0.42f), h(0.76f)); lineTo(w(0.84f), h(0.26f)) },
            color = tint, style = strokeStyle,
        )

        Glyph.Close -> {
            drawLine(tint, p(0.2f, 0.2f), p(0.8f, 0.8f), stroke, StrokeCap.Round)
            drawLine(tint, p(0.8f, 0.2f), p(0.2f, 0.8f), stroke, StrokeCap.Round)
        }

        Glyph.More -> {
            val radius = w(0.1f)
            listOf(0.22f, 0.5f, 0.78f).forEach { y ->
                drawCircle(tint, radius = radius, center = p(0.5f, y))
            }
        }

        Glyph.Download, Glyph.ArrowDown -> {
            drawLine(tint, p(0.5f, 0.12f), p(0.5f, 0.62f), stroke, StrokeCap.Round)
            drawPath(
                Path().apply { moveTo(w(0.3f), h(0.44f)); lineTo(w(0.5f), h(0.66f)); lineTo(w(0.7f), h(0.44f)) },
                color = tint, style = strokeStyle,
            )
            if (glyph == Glyph.Download) {
                drawLine(tint, p(0.16f, 0.84f), p(0.84f, 0.84f), stroke, StrokeCap.Round)
            }
        }

        Glyph.Pause -> {
            val bar = androidx.compose.ui.geometry.CornerRadius(w(0.06f), w(0.06f))
            drawRoundRect(tint, p(0.3f, 0.2f), Size(w(0.14f), h(0.6f)), bar)
            drawRoundRect(tint, p(0.56f, 0.2f), Size(w(0.14f), h(0.6f)), bar)
        }

        Glyph.Play -> drawPath(
            Path().apply {
                moveTo(w(0.3f), h(0.16f)); lineTo(w(0.84f), h(0.5f)); lineTo(w(0.3f), h(0.84f)); close()
            },
            color = tint,
        )

        Glyph.Person -> {
            val head = w(0.17f + 0.01f * fill)
            val headCenter = p(0.5f, 0.31f - 0.025f * fill)
            val shoulders = CornerRadius(w(0.20f), w(0.20f))
            val shoulderTop = p(0.19f, 0.57f - 0.01f * fill)
            val shoulderSize = Size(w(0.62f), h(0.33f))
            if (fill > 0.01f) {
                val solid = tint.copy(alpha = tint.alpha * fill)
                drawCircle(solid, radius = head, center = headCenter)
                drawRoundRect(solid, shoulderTop, shoulderSize, shoulders)
            }
            drawCircle(tint, radius = head, center = headCenter, style = strokeStyle)
            drawRoundRect(
                tint,
                topLeft = shoulderTop,
                size = shoulderSize,
                cornerRadius = shoulders,
                style = strokeStyle,
            )
        }

        Glyph.Update -> {
            drawCircle(tint, radius = w(0.32f), center = p(0.5f, 0.5f), style = strokeStyle)
            drawPath(
                Path().apply { moveTo(w(0.5f), h(0.22f)); lineTo(w(0.5f), h(0.5f)); lineTo(w(0.7f), h(0.62f)) },
                color = tint, style = strokeStyle,
            )
        }

        Glyph.Shield -> {
            val path = Path().apply {
                moveTo(w(0.5f), h(0.1f))
                lineTo(w(0.86f), h(0.24f))
                lineTo(w(0.86f), h(0.5f))
                cubicTo(w(0.86f), h(0.72f), w(0.68f), h(0.86f), w(0.5f), h(0.92f))
                cubicTo(w(0.32f), h(0.86f), w(0.14f), h(0.72f), w(0.14f), h(0.5f))
                lineTo(w(0.14f), h(0.24f))
                close()
            }
            if (fill >= 0.5f) drawPath(path, color = tint) else drawPath(path, color = tint, style = strokeStyle)
            if (fill >= 0.5f) {
                drawPath(
                    Path().apply { moveTo(w(0.34f), h(0.5f)); lineTo(w(0.46f), h(0.62f)); lineTo(w(0.68f), h(0.38f)) },
                    color = Color.Black.copy(alpha = 0.75f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        // The storefront's own mark: the initial A, flat apex and crossbar. It is `logo/mark-a.svg`
        // normalised into unit space, and it replaced the shield in the brand slot only —
        // [Glyph.Shield] still means "this build was checked" wherever a row says so, which is what
        // the shield is actually for.
        Glyph.Brand -> {
            drawPath(
                Path().apply {
                    // Left leg
                    moveTo(w(0.259f), h(0.648f)); lineTo(w(0.468f), h(0.287f))
                    lineTo(w(0.532f), h(0.287f)); lineTo(w(0.324f), h(0.648f)); close()
                    // Right leg
                    moveTo(w(0.468f), h(0.287f)); lineTo(w(0.532f), h(0.287f))
                    lineTo(w(0.741f), h(0.648f)); lineTo(w(0.676f), h(0.648f)); close()
                    // Crossbar
                    moveTo(w(0.366f), h(0.491f)); lineTo(w(0.634f), h(0.491f))
                    lineTo(w(0.634f), h(0.565f)); lineTo(w(0.366f), h(0.565f)); close()
                },
                color = tint,
            )
        }

        Glyph.Info -> {
            drawCircle(tint, radius = w(0.36f), center = p(0.5f, 0.5f), style = strokeStyle)
            drawCircle(tint, radius = w(0.05f), center = p(0.5f, 0.31f))
            drawLine(tint, p(0.5f, 0.46f), p(0.5f, 0.7f), stroke, StrokeCap.Round)
        }

        // The bottom-bar pair that stands in for Play's "Games" and "Browse" tabs.
        Glyph.Gamepad -> {
            val tilt = -6f * kotlin.math.sin(fill * Math.PI.toFloat())
            rotate(degrees = tilt, pivot = p(0.5f, 0.52f)) {
                val padPath = Path().apply {
                    moveTo(w(0.28f), h(0.26f))
                    lineTo(w(0.72f), h(0.26f))
                    cubicTo(w(0.88f), h(0.26f), w(0.95f), h(0.42f), w(0.91f), h(0.66f))
                    cubicTo(w(0.89f), h(0.79f), w(0.77f), h(0.82f), w(0.69f), h(0.71f))
                    lineTo(w(0.62f), h(0.62f))
                    lineTo(w(0.38f), h(0.62f))
                    lineTo(w(0.31f), h(0.71f))
                    cubicTo(w(0.23f), h(0.82f), w(0.11f), h(0.79f), w(0.09f), h(0.66f))
                    cubicTo(w(0.05f), h(0.42f), w(0.12f), h(0.26f), w(0.28f), h(0.26f))
                    close()
                }
                val padStroke = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                if (fill > 0.01f) {
                    drawPath(padPath, color = tint.copy(alpha = tint.alpha * fill))
                    // A solid controller has its d-pad and buttons cut out of it rather than painted on
                    // top, so they are drawn in the surface the bar is drawing on: the pill's own tint.
                    val hole = cutout.copy(alpha = cutout.alpha * fill)
                    if (hole.alpha > 0f) {
                        val cut = stroke * 1.45f
                        drawLine(hole, p(0.29f, 0.37f), p(0.29f, 0.53f), cut, StrokeCap.Round)
                        drawLine(hole, p(0.21f, 0.45f), p(0.37f, 0.45f), cut, StrokeCap.Round)
                        drawCircle(hole, radius = w(0.055f), center = p(0.67f, 0.40f))
                        drawCircle(hole, radius = w(0.055f), center = p(0.76f, 0.49f))
                    }
                }
                drawPath(padPath, color = tint, style = padStroke)
                if (fill < 0.85f) {
                    val detailTint = tint.copy(alpha = tint.alpha * (1f - fill))
                    drawLine(detailTint, p(0.29f, 0.37f), p(0.29f, 0.53f), stroke * 0.9f, StrokeCap.Round)
                    drawLine(detailTint, p(0.21f, 0.45f), p(0.37f, 0.45f), stroke * 0.9f, StrokeCap.Round)
                    drawCircle(detailTint, radius = w(0.048f), center = p(0.67f, 0.40f))
                    drawCircle(detailTint, radius = w(0.048f), center = p(0.76f, 0.49f))
                }
            }
        }

        Glyph.Library -> {
            // Layered category cards that fan open horizontally as the tab is selected.
            val corner = CornerRadius(w(0.07f), w(0.07f))
            val bars = listOf(
                Triple(0.14f + 0.08f * fill, 0.16f, 0.72f - 0.16f * fill),
                Triple(0.14f + 0.03f * fill, 0.42f, 0.72f - 0.06f * fill),
                Triple(0.14f, 0.68f, 0.72f),
            )
            bars.forEachIndexed { idx, (left, top, widthFrac) ->
                val lift = (2 - idx) * 0.015f * fill
                val rectTop = p(left, top - lift)
                val rectSize = Size(w(widthFrac), h(0.16f))
                if (fill > 0.01f) {
                    drawRoundRect(
                        tint.copy(alpha = tint.alpha * fill * (0.65f + 0.175f * idx)),
                        topLeft = rectTop,
                        size = rectSize,
                        cornerRadius = corner,
                    )
                }
                drawRoundRect(tint, topLeft = rectTop, size = rectSize, cornerRadius = corner, style = strokeStyle)
            }
        }

        Glyph.Share -> {
            drawCircle(tint, radius = w(0.09f), center = p(0.72f, 0.24f))
            drawCircle(tint, radius = w(0.09f), center = p(0.28f, 0.5f))
            drawCircle(tint, radius = w(0.09f), center = p(0.72f, 0.76f))
            drawLine(tint, p(0.35f, 0.46f), p(0.65f, 0.29f), stroke * 0.8f, StrokeCap.Round)
            drawLine(tint, p(0.35f, 0.54f), p(0.65f, 0.71f), stroke * 0.8f, StrokeCap.Round)
        }

        Glyph.Trash -> {
            drawLine(tint, p(0.24f, 0.28f), p(0.76f, 0.28f), stroke, StrokeCap.Round)
            drawPath(
                Path().apply {
                    moveTo(w(0.38f), h(0.28f))
                    lineTo(w(0.38f), h(0.20f))
                    lineTo(w(0.62f), h(0.20f))
                    lineTo(w(0.62f), h(0.28f))
                },
                color = tint, style = strokeStyle,
            )
            val canBody = Path().apply {
                moveTo(w(0.29f), h(0.34f))
                lineTo(w(0.33f), h(0.76f))
                cubicTo(w(0.33f), h(0.82f), w(0.39f), h(0.82f), w(0.44f), h(0.82f))
                lineTo(w(0.56f), h(0.82f))
                cubicTo(w(0.61f), h(0.82f), w(0.67f), h(0.82f), w(0.67f), h(0.76f))
                lineTo(w(0.71f), h(0.34f))
            }
            drawPath(canBody, color = tint, style = strokeStyle)
            drawLine(tint, p(0.44f, 0.44f), p(0.44f, 0.72f), stroke * 0.85f, StrokeCap.Round)
            drawLine(tint, p(0.56f, 0.44f), p(0.56f, 0.72f), stroke * 0.85f, StrokeCap.Round)
        }

        Glyph.Copy -> {
            val corner = CornerRadius(w(0.06f), w(0.06f))
            drawRoundRect(
                tint,
                topLeft = p(0.34f, 0.16f),
                size = Size(w(0.48f), h(0.56f)),
                cornerRadius = corner,
                style = strokeStyle,
            )
            drawRoundRect(
                tint,
                topLeft = p(0.18f, 0.30f),
                size = Size(w(0.48f), h(0.56f)),
                cornerRadius = corner,
                style = strokeStyle,
            )
        }
    }
}

/** Unit space helpers: paths are written 0..1 and scaled here. */
private fun unit(extent: Float): (Float) -> Float = { v -> v * extent }

private fun starPath(width: Float, height: Float): Path {
    val cx = width / 2f
    val cy = height / 2f
    val outer = minOf(width, height) / 2f
    val inner = outer * 0.382f
    return Path().apply {
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians((-90 + i * 36).toDouble())
            val x = cx + (radius * Math.cos(angle)).toFloat()
            val y = cy + (radius * Math.sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
