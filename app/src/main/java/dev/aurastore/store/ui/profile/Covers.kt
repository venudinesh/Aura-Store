package dev.aurastore.store.ui.profile

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import dev.aurastore.store.data.CoverStyle
import kotlin.math.abs
import kotlin.math.sin

/**
 * The cover behind the profile, drawn or photographed.
 *
 * The four presets are code, like the faces and the frames: one drawing follows the surface it is
 * given, from a 70 dp tile in the picker to the full-width hero, with no bitmap to keep in a density
 * folder. A photograph takes the same slot when the user cropped one, and is cropped again by the
 * layout rather than letterboxed.
 */
@Composable
fun CoverArt(style: CoverStyle, photo: Bitmap?, modifier: Modifier = Modifier) {
    if (photo != null) {
        Image(
            bitmap = photo.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    Canvas(modifier.fillMaxSize()) { drawCoverStyle(style) }
}

private val HouseNight = Color(0xFF0C1024)
private val HouseBlue = Color(0xFF1B2450)
private val HouseInk = Color(0xFF080A14)
private val HouseGold = Color(0xFFE7B25C)
private val AuroraDeep = Color(0xFF04141A)
private val AuroraMid = Color(0xFF07242A)
private val AuroraGreen = Color(0xFF61E6B0)
private val AuroraCyan = Color(0xFF45C8E8)
private val AuroraViolet = Color(0xFF8F7BFF)
private val EmberBase = Color(0xFF150607)
private val EmberDeep = Color(0xFF2A0D0A)
private val EmberOrange = Color(0xFFFF9A3C)
private val EmberHot = Color(0xFFFF5A1F)
private val Paper = Color(0xFFF2EAD9)
private val PaperShade = Color(0xFFD9CBB0)
private val CoverInk = Color(0xFF16130F)

private fun DrawScope.drawCoverStyle(style: CoverStyle) {
    when (style) {
        CoverStyle.HOUSE -> drawHouse()
        CoverStyle.AURORA -> drawAurora()
        CoverStyle.EMBER -> drawEmber()
        CoverStyle.INK -> drawInk()
    }
}

private fun DrawScope.drawHouse() {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.linearGradient(
            listOf(HouseNight, HouseBlue, HouseInk),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        ),
    )
    val glow = h * 0.9f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(HouseGold.copy(alpha = 0.30f), Color.Transparent),
            center = Offset(w * 0.84f, h * 0.12f),
            radius = glow,
        ),
        radius = glow,
        center = Offset(w * 0.84f, h * 0.12f),
    )
    // A field of small stars, placed on a fixed grid so the picture never crawls between frames.
    repeat(26) { index ->
        val x = w * (0.04f + 0.9f * abs(sin(index * 12.9898f)))
        val y = h * (0.08f + 0.6f * abs(sin(index * 4.1414f + 1.7f)))
        drawCircle(
            color = Color.White.copy(alpha = 0.18f + 0.34f * abs(sin(index * 2.3f))),
            radius = if (index % 5 == 0) 2.2f else 1.1f,
            center = Offset(x, y),
        )
    }
    drawCoverMark(
        centerX = w * 0.72f,
        top = h * 0.02f,
        height = h * 0.96f,
        color = Color.White.copy(alpha = 0.12f),
        stroke = h * 0.022f,
    )
    drawLine(
        color = HouseGold.copy(alpha = 0.74f),
        start = Offset(w * 0.07f, h * 0.76f),
        end = Offset(w * 0.34f, h * 0.76f),
        strokeWidth = h * 0.012f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawAurora() {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.linearGradient(
            listOf(AuroraDeep, AuroraMid, AuroraDeep),
            start = Offset(0f, 0f),
            end = Offset(0f, h),
        ),
    )
    val bands = listOf(
        Triple(-0.08f, 0.62f, AuroraGreen),
        Triple(0.18f, 0.48f, AuroraCyan),
        Triple(0.46f, 0.36f, AuroraViolet),
    )
    bands.forEachIndexed { index, (startY, lift, color) ->
        val path = Path().apply {
            moveTo(-w * 0.1f, h * (startY + 0.72f))
            cubicTo(
                w * 0.22f, h * (startY + lift),
                w * 0.58f, h * (startY + 0.06f),
                w * 1.1f, h * (startY + 0.52f),
            )
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    color.copy(alpha = 0.30f - index * 0.05f),
                    color.copy(alpha = 0.46f - index * 0.08f),
                    Color.Transparent,
                ),
                start = Offset(0f, h * startY),
                end = Offset(w, h * (startY + lift + 0.3f)),
            ),
            style = Stroke(width = h * (0.30f - index * 0.05f), cap = StrokeCap.Round),
        )
    }
    repeat(18) { index ->
        val x = w * (0.03f + 0.94f * abs(sin(index * 7.71f + 0.4f)))
        val y = h * (0.08f + 0.4f * abs(sin(index * 3.37f)))
        drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = 1f,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawEmber() {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.linearGradient(
            listOf(EmberDeep, EmberBase, Color(0xFF0C0405)),
            start = Offset(0f, 0f),
            end = Offset(0f, h),
        ),
    )
    val glow = w * 0.72f
    val core = Offset(w * 0.16f, h * 1.04f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(EmberHot.copy(alpha = 0.58f), EmberOrange.copy(alpha = 0.20f), Color.Transparent),
            center = core,
            radius = glow,
        ),
        radius = glow,
        center = core,
    )
    repeat(34) { index ->
        val x = w * abs(sin(index * 9.123f + 2.2f))
        val lift = h * (0.08f + 0.78f * abs(sin(index * 1.77f + 0.6f)))
        val y = h - lift * (0.35f + 0.65f * abs(sin(index * 3.1f)))
        val radius = 1.2f + 2.6f * abs(sin(index * 5.7f))
        val heat = 0.25f + 0.55f * abs(sin(index * 2.9f))
        drawCircle(
            color = if (index % 3 == 0) EmberOrange.copy(alpha = heat) else EmberHot.copy(alpha = heat * 0.8f),
            radius = radius,
            center = Offset(x, y),
        )
    }
    drawLine(
        color = EmberOrange.copy(alpha = 0.22f),
        start = Offset(w * 0.58f, h * 0.18f),
        end = Offset(w * 0.96f, h * 0.18f),
        strokeWidth = h * 0.008f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawInk() {
    val w = size.width
    val h = size.height
    drawRect(Paper)
    drawRect(
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, PaperShade.copy(alpha = 0.55f)),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        ),
    )
    // Two broad nib strokes crossing the paper, then the mark, then a seal.
    drawLine(
        color = CoverInk.copy(alpha = 0.08f),
        start = Offset(-w * 0.05f, h * 0.82f),
        end = Offset(w * 0.62f, -h * 0.06f),
        strokeWidth = h * 0.10f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = CoverInk.copy(alpha = 0.06f),
        start = Offset(w * 0.38f, h * 1.04f),
        end = Offset(w * 0.94f, h * 0.10f),
        strokeWidth = h * 0.16f,
        cap = StrokeCap.Round,
    )
    drawCoverMark(
        centerX = w * 0.74f,
        top = h * 0.02f,
        height = h * 0.96f,
        color = CoverInk.copy(alpha = 0.82f),
        stroke = h * 0.018f,
    )
    drawCircle(
        color = Color(0xFFB4372A).copy(alpha = 0.88f),
        radius = h * 0.055f,
        center = Offset(w * 0.12f, h * 0.20f),
    )
    drawLine(
        color = CoverInk.copy(alpha = 0.20f),
        start = Offset(w * 0.07f, h * 0.86f),
        end = Offset(w * 0.30f, h * 0.86f),
        strokeWidth = h * 0.008f,
        cap = StrokeCap.Round,
    )
}

/**
 * The signature A in the cover's own space: the same 136 × 212 letter the splash writes, with
 * [height] standing for the ink itself rather than a padded box. The legs open, the bar sits just
 * above the middle, and it runs past both feet.
 */
private fun DrawScope.drawCoverMark(
    centerX: Float,
    top: Float,
    height: Float,
    color: Color,
    stroke: Float,
) {
    val w = height * 112f / 180f
    val left = centerX - w / 2f
    fun x(u: Float) = left + (u - 12f) * w / 112f
    fun y(v: Float) = top + (v - 16f) * height / 180f
    val body = Path().apply {
        moveTo(x(32f), y(192f))
        quadraticTo(x(46f), y(98f), x(68f), y(16f))
        quadraticTo(x(90f), y(100f), x(104f), y(196f))
    }
    drawPath(body, color, style = Stroke(stroke, cap = StrokeCap.Round))
    drawPath(
        Path().apply {
            moveTo(x(12f), y(110f))
            lineTo(x(124f), y(110f))
        },
        color,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
}
