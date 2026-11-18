package dev.aurastore.store.ui.profile

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import dev.aurastore.store.data.SpecialAnime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The thirty-one anime rings, one per entry in the Special pack.
 *
 * Each one is painted, not shipped: the pack already carries a hundred posters of a given anime, and
 * a frame that is a fourth kind of file would have to be cut for every size the avatar appears at.
 * Instead every ring is the same 100-unit circle the house rings use, wearing that anime's colours
 * and carrying one mark that is recognisably its own: Naruto's spiral, the straw hat, the survey
 * blades, the alchemy circle, the four-star ball.
 *
 * The mark sits on the band, never inside it, so the picture is never covered. All of them turn;
 * each keeps its own speed and direction, which is what makes a row of thirty read as thirty rings
 * rather than one ring in thirty colours.
 */
internal fun DrawScope.drawAnimeFrame(anime: SpecialAnime, phase: Float) {
    val side = size.minDimension
    val stroke = side * 0.05f
    val pen = RingPen(this, center, side / 2f - stroke * 0.9f, stroke)
    when (anime) {
        SpecialAnime.NARUTO -> {
            pen.ring(pen.orange, stroke * 0.9f)
            rotate(phase * 0.3f, pivot = center) {
                pen.arc(pen.navy, -40f, 210f, stroke * 0.9f, pen.radius * 0.72f)
                pen.arc(pen.navy, 10f, 170f, stroke * 0.7f, pen.radius * 0.5f)
                pen.arc(pen.navy, 60f, 120f, stroke * 0.5f, pen.radius * 0.3f)
            }
            pen.dot(270f, pen.leaf, stroke * 0.9f)
        }

        SpecialAnime.ONE_PIECE -> {
            pen.ring(pen.crimson)
            pen.hat()
            pen.dot(90f, pen.straw, stroke * 0.4f)
        }

        SpecialAnime.ATTACK_ON_TITAN -> {
            pen.ring(pen.emerald, stroke * 0.55f)
            pen.ring(pen.steel, stroke * 0.2f, pen.radius - stroke * 1.5f)
            repeat(6) { index -> pen.tick(index * 60f + 30f, pen.steel, stroke * 0.5f, 0.84f, 0.98f) }
            pen.blades()
        }

        SpecialAnime.DEMON_SLAYER -> {
            repeat(12) { index ->
                pen.arc(
                    if (index % 2 == 0) pen.teal else pen.ink,
                    index * 30f,
                    30f,
                    stroke * 0.8f,
                )
            }
            repeat(3) { index ->
                pen.flame(-118f + index * 12f, pen.flame, stroke * 1.1f)
            }
        }

        SpecialAnime.JUJUTSU_KAISEN -> {
            pen.ring(pen.violet, stroke * 0.65f)
            rotate(phase * 0.45f, pivot = center) {
                repeat(3) { index ->
                    val base = 200f + index * 120f
                    pen.crack(base, pen.crimson, stroke * 0.9f)
                }
            }
            rotate(-phase * 0.3f, pivot = center) {
                repeat(6) { index -> pen.dot(index * 60f + 20f, pen.violet, stroke * 0.4f, pen.radius * 0.9f) }
            }
        }

        SpecialAnime.MY_HERO_ACADEMIA -> {
            pen.ring(pen.sun, stroke * 0.9f)
            rotate(phase * 0.35f, pivot = center) {
                repeat(16) { index ->
                    pen.tick(index * 22.5f, if (index % 2 == 0) pen.sun else pen.red, stroke * 0.4f, 1.02f, 1.14f)
                }
            }
            pen.dot(270f, pen.red, stroke * 0.55f)
        }

        SpecialAnime.DEATH_NOTE -> {
            pen.ring(Color(0xFFD9D9DE), stroke * 0.85f)
            pen.ring(pen.ink, stroke * 0.3f, pen.radius - stroke * 1.35f)
            pen.pageCorner()
            pen.dot(90f, pen.apple, stroke * 0.72f)
        }

        SpecialAnime.FULLMETAL_ALCHEMIST -> {
            pen.ring(pen.crimson, stroke * 0.75f)
            pen.ring(pen.gold, stroke * 0.18f, pen.radius - stroke * 1.55f)
            rotate(phase * 0.2f, pivot = center) {
                pen.poly(
                    (0 until 3).map { pen.at(it * 120f - 90f, pen.radius - stroke * 0.2f) },
                    pen.gold,
                    stroke * 0.45f,
                    close = true,
                )
                repeat(6) { index -> pen.dot(index * 60f, pen.gold, stroke * 0.26f, pen.radius * 0.72f) }
            }
        }

        SpecialAnime.DRAGON_BALL -> {
            pen.ring(pen.orange, stroke * 0.85f)
            rotate(-phase * 0.5f, pivot = center) {
                repeat(4) { index ->
                    pen.star(index * 90f + 45f, stroke * 0.7f, stroke * 0.28f, pen.starGold, pen.radius * 0.8f)
                }
            }
            pen.dot(270f, pen.starGold, stroke * 0.75f)
            pen.ring(pen.starGold, stroke * 0.14f, pen.radius - stroke * 1.2f)
        }

        SpecialAnime.SWORD_ART_ONLINE -> {
            pen.ring(pen.cyan, stroke * 0.7f)
            pen.crossedBlades(pen.steel, pen.cyan)
            pen.arc(pen.leaf, 200f, 70f, stroke * 0.4f, pen.radius * 0.84f, alpha = 0.7f)
            pen.arc(pen.leaf, 20f, 70f, stroke * 0.4f, pen.radius * 0.84f, alpha = 0.7f)
        }

        SpecialAnime.TOKYO_GHOUL -> {
            pen.ring(pen.crimson, stroke * 0.7f)
            rotate(phase * 0.55f, pivot = center) {
                repeat(4) { index -> pen.tendril(index * 90f, pen.crimson, stroke * 0.7f) }
            }
            pen.dot(0f, pen.crimson, stroke * 0.42f)
        }

        SpecialAnime.CHAINSAW_MAN -> {
            rotate(phase * 0.5f, pivot = center) {
                repeat(20) { index ->
                    pen.tooth(index * 18f, pen.steel, stroke * 0.55f)
                }
            }
            pen.ring(pen.crimson, stroke * 0.45f)
            pen.dot(90f, pen.crimson, stroke * 0.6f)
        }

        SpecialAnime.SPY_X_FAMILY -> {
            pen.ring(pen.pink, stroke * 0.85f)
            pen.crosshair(pen.sage)
            pen.dot(270f, pen.sage, stroke * 0.4f)
        }

        SpecialAnime.ONE_PUNCH_MAN -> {
            pen.ring(pen.sun, stroke * 0.8f)
            pen.arc(pen.red, 120f, 80f, stroke * 1.6f, pen.radius, alpha = 0.55f)
            pen.arc(pen.red, 250f, 60f, stroke * 1.6f, pen.radius, alpha = 0.55f)
            pen.dot(270f, Color.White, stroke * 0.55f)
        }

        SpecialAnime.HUNTER_X_HUNTER -> {
            pen.ring(pen.leaf, stroke * 0.55f)
            rotate(-phase * 0.4f, pivot = center) {
                pen.arc(pen.leaf, -60f, 120f, stroke * 1.3f, pen.radius, alpha = 0.28f)
                pen.arc(pen.gold, 120f, 120f, stroke * 1.3f, pen.radius, alpha = 0.28f)
            }
            pen.cross(pen.gold, stroke * 0.6f, 0.9f)
        }

        SpecialAnime.BLEACH -> {
            pen.ring(Color.White, stroke * 0.85f)
            pen.ring(pen.ink, stroke * 0.42f, pen.radius - stroke * 1.35f)
            rotate(phase * 0.18f, pivot = center) {
                pen.slash(-120f, Color.White, stroke * 0.95f)
            }
            pen.dot(270f, pen.orange, stroke * 0.5f)
        }

        SpecialAnime.SOLO_LEVELING -> {
            pen.ring(pen.violet, stroke * 0.5f)
            rotate(phase * 0.25f, pivot = center) {
                repeat(6) { index ->
                    val angle = index * 60f
                    pen.tick(angle, pen.ice, stroke * 0.45f, 0.86f, 1.0f)
                    pen.dot(angle + 30f, pen.violet, stroke * 0.24f, pen.radius * 0.92f)
                }
            }
            pen.dagger(pen.ice)
        }

        SpecialAnime.FRIEREN -> {
            pen.ring(pen.blue, stroke * 0.8f)
            rotate(phase * 0.22f, pivot = center) {
                pen.poly((0 until 6).map { pen.at(it * 60f - 90f) }, pen.blue, stroke * 0.4f, close = true)
                repeat(3) { index ->
                    pen.star(index * 120f + 30f, stroke * 0.55f, stroke * 0.2f, pen.gold, pen.radius * 0.72f)
                }
            }
        }

        SpecialAnime.EVANGELION -> {
            pen.ring(pen.violet, stroke * 0.7f)
            pen.cross(pen.green, stroke * 0.85f, 1.0f)
            pen.dot(90f, pen.orange, stroke * 0.5f)
            pen.arc(pen.green, 190f, 40f, stroke * 0.35f, pen.radius * 0.88f)
            pen.arc(pen.green, 310f, 40f, stroke * 0.35f, pen.radius * 0.88f)
        }

        SpecialAnime.COWBOY_BEBOP -> {
            pen.ring(pen.straw, stroke * 0.85f)
            pen.ring(pen.straw, stroke * 0.22f, pen.radius * 0.88f, alpha = 0.7f)
            pen.ring(pen.straw, stroke * 0.22f, pen.radius * 0.74f, alpha = 0.45f)
            rotate(-phase * 0.6f, pivot = center) {
                pen.dot(0f, pen.crimson, stroke * 0.5f, pen.radius * 0.81f)
            }
        }

        SpecialAnime.SAILOR_MOON -> {
            pen.ring(pen.pink, stroke * 0.8f)
            pen.crescent(pen.gold, stroke * 2.2f, 270f)
            repeat(3) { index ->
                pen.star(30f + index * 120f, stroke * 0.75f, stroke * 0.3f, Color.White, pen.radius * 0.92f)
            }
        }

        SpecialAnime.POKEMON -> {
            pen.arc(pen.crimson, 180f, 180f, stroke * 0.95f)
            pen.arc(Color.White, 0f, 180f, stroke * 0.95f)
            pen.dot(180f, pen.ink, stroke * 0.55f)
            pen.dot(0f, pen.ink, stroke * 0.55f)
            pen.dot(270f, Color.White, stroke * 0.72f)
            pen.dot(270f, pen.ink, stroke * 0.38f)
        }

        SpecialAnime.SPIRITED_AWAY -> {
            pen.ring(pen.lanternRed, stroke * 0.75f)
            pen.ring(pen.gold, stroke * 0.16f, pen.radius - stroke * 1.35f, alpha = 0.75f)
            rotate(phase * 0.18f, pivot = center) {
                repeat(3) { index -> pen.lantern(210f + index * 60f, pen.lanternRed, stroke * 1.25f) }
            }
            pen.dot(90f, pen.soot, stroke * 0.6f)
            pen.dot(120f, pen.soot, stroke * 0.4f)
        }

        SpecialAnime.YOUR_NAME -> {
            pen.ring(pen.blue, stroke * 0.75f)
            rotate(phase * 0.42f, pivot = center) {
                pen.arc(pen.blue, -30f, 90f, stroke * 1.6f, pen.radius, alpha = 0.3f)
                pen.dot(-30f, Color.White, stroke * 0.55f)
                pen.dot(-18f, pen.pink, stroke * 0.35f, pen.radius * 0.9f)
            }
            pen.arc(pen.pink, 120f, 130f, stroke * 0.5f, pen.radius * 0.82f, alpha = 0.8f)
            pen.dot(270f, pen.pink, stroke * 0.3f)
        }

        SpecialAnime.VINLAND_SAGA -> {
            pen.ring(pen.steel, stroke * 0.6f)
            pen.axe(pen.steel, pen.crimson)
            repeat(4) { index -> pen.tick(index * 90f + 45f, pen.ice, stroke * 0.35f, 0.84f, 0.96f) }
        }

        SpecialAnime.MOB_PSYCHO -> {
            pen.ring(pen.ice, stroke * 0.7f)
            rotate(-phase * 0.3f, pivot = center) {
                repeat(10) { index -> pen.tick(index * 36f, pen.ice, stroke * 0.35f, 0.94f, 1.06f) }
            }
            pen.percent(pen.crimson)
            pen.dot(90f, pen.crimson, stroke * 0.32f, pen.radius * 0.78f)
        }

        SpecialAnime.BLACK_CLOVER -> {
            pen.ring(pen.leaf, stroke * 0.7f)
            rotate(phase * 0.28f, pivot = center) {
                repeat(3) { index ->
                    pen.leafAt(270f + (index - 1) * 34f, pen.clover, stroke * 1.25f)
                }
                pen.dot(270f, pen.gold, stroke * 0.4f)
            }
        }

        SpecialAnime.BLUE_LOCK -> {
            repeat(10) { index ->
                pen.arc(
                    if (index % 2 == 0) pen.blue else Color.White,
                    index * 36f,
                    36f,
                    stroke * 0.85f,
                )
            }
            pen.dot(270f, pen.blue, stroke * 0.5f, pen.radius * 0.86f)
        }

        SpecialAnime.CODE_GEASS -> {
            pen.ring(pen.violet, stroke * 0.75f)
            rotate(phase * 0.24f, pivot = center) {
                pen.sigil(pen.violet)
            }
            pen.dot(90f, pen.crimson, stroke * 0.42f)
        }

        SpecialAnime.DANDADAN -> {
            pen.ring(pen.teal, stroke * 0.65f)
            pen.saucer(pen.teal, pen.pink)
            rotate(-phase * 0.4f, pivot = center) {
                pen.dot(120f, pen.pink, stroke * 0.3f, pen.radius * 0.9f)
                pen.dot(60f, pen.teal, stroke * 0.22f, pen.radius * 0.86f)
            }
        }

        SpecialAnime.JOJO -> {
            pen.ring(pen.gold, stroke * 0.7f)
            pen.ring(pen.violet, stroke * 0.22f, pen.radius - stroke * 1.5f, alpha = 0.8f)
            rotate(phase * 0.2f, pivot = center) {
                pen.star(270f, stroke * 1.7f, stroke * 0.66f, pen.gold, pen.radius, points = 5)
                repeat(3) { index ->
                    val angle = 250f + index * 20f
                    pen.tick(angle, pen.violet, stroke * 0.35f, 0.8f, 1.14f)
                }
            }
            pen.dot(90f, pen.gold, stroke * 0.5f)
        }
    }
}

/** The pen the anime rings share: the same geometry, the same 0.05 stroke of the house rings. */
private class RingPen(
    private val scope: DrawScope,
    private val center: Offset,
    val radius: Float,
    private val stroke: Float,
) {
    val orange = Color(0xFFFF8A3D)
    val navy = Color(0xFF232B4D)
    val leaf = Color(0xFF54C98A)
    val crimson = Color(0xFFE23B3B)
    val straw = Color(0xFFF2C14E)
    val emerald = Color(0xFF2F8C60)
    val steel = Color(0xFFBFCAD3)
    val teal = Color(0xFF1FA98C)
    val ink = Color(0xFF15161C)
    val flame = Color(0xFFFF6B3D)
    val violet = Color(0xFF7B4BFF)
    val sun = Color(0xFFFFD23F)
    val red = Color(0xFFE8402A)
    val gold = Color(0xFFE0A44A)
    val apple = Color(0xFFD62828)
    val starGold = Color(0xFFFFC93C)
    val cyan = Color(0xFF38D6E8)
    val pink = Color(0xFFF26FA6)
    val sage = Color(0xFF7FA98A)
    val ice = Color(0xFF8FD3FF)
    val blue = Color(0xFF6FA8FF)
    val green = Color(0xFF6FE36F)
    val lanternRed = Color(0xFFD1493F)
    val soot = Color(0xFF2E2B33)
    val clover = Color(0xFF3FA34D)

    fun at(angle: Float, r: Float = radius): Offset {
        val radians = angle * PI.toFloat() / 180f
        return Offset(center.x + r * cos(radians), center.y + r * sin(radians))
    }

    fun ring(color: Color, width: Float = stroke, r: Float = radius, alpha: Float = 1f) {
        scope.drawCircle(
            color = color.copy(alpha = color.alpha * alpha),
            radius = r,
            center = center,
            style = Stroke(width),
        )
    }

    fun arc(
        color: Color,
        start: Float,
        sweep: Float,
        width: Float = stroke,
        r: Float = radius,
        alpha: Float = 1f,
    ) {
        scope.drawArc(
            color = color.copy(alpha = color.alpha * alpha),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }

    fun line(
        from: Float,
        to: Float,
        color: Color,
        width: Float = stroke,
        fromR: Float = radius,
        toR: Float = radius,
        alpha: Float = 1f,
    ) {
        scope.drawLine(
            color = color.copy(alpha = color.alpha * alpha),
            start = at(from, fromR),
            end = at(to, toR),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }

    fun tick(angle: Float, color: Color, width: Float, from: Float, to: Float, alpha: Float = 1f) {
        line(
            from = angle,
            to = angle,
            color = color,
            width = width,
            fromR = radius * from,
            toR = radius * to,
            alpha = alpha,
        )
    }

    fun dot(angle: Float, color: Color, size: Float, r: Float = radius, alpha: Float = 1f) {
        scope.drawCircle(color.copy(alpha = color.alpha * alpha), size, at(angle, r))
    }

    fun poly(points: List<Offset>, color: Color, width: Float, close: Boolean = false, alpha: Float = 1f) {
        val path = Path()
        points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        if (close) path.close()
        scope.drawPath(
            path = path,
            color = color.copy(alpha = color.alpha * alpha),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }

    fun fill(points: List<Offset>, color: Color, alpha: Float = 1f) {
        val path = Path()
        points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        scope.drawPath(path = path, color = color.copy(alpha = color.alpha * alpha))
    }

    fun star(
        angle: Float,
        outer: Float,
        inner: Float,
        color: Color,
        r: Float = radius,
        points: Int = 4,
        alpha: Float = 1f,
    ) {
        val hub = at(angle, r)
        val path = Path()
        repeat(points * 2) { index ->
            val size = if (index % 2 == 0) outer else inner
            val radians = (angle + index * (180f / points)) * PI.toFloat() / 180f
            val point = Offset(hub.x + size * cos(radians), hub.y + size * sin(radians))
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        scope.drawPath(path, color.copy(alpha = color.alpha * alpha))
    }

    fun oval(cx: Float, cy: Float, width: Float, height: Float, color: Color, lineWidth: Float) {
        scope.drawOval(
            color = color,
            topLeft = Offset(center.x + cx - width / 2f, center.y + cy - height / 2f),
            size = Size(width, height),
            style = Stroke(lineWidth),
        )
    }

    fun filledOval(cx: Float, cy: Float, width: Float, height: Float, color: Color) {
        scope.drawOval(
            color = color,
            topLeft = Offset(center.x + cx - width / 2f, center.y + cy - height / 2f),
            size = Size(width, height),
        )
    }

    fun hat() {
        val top = center.y - radius
        filledOval(0f, -radius + stroke * 0.1f, stroke * 3.6f, stroke * 0.95f, straw)
        scope.drawRect(
            color = straw,
            topLeft = Offset(center.x - stroke * 0.95f, top - stroke * 1.0f),
            size = Size(stroke * 1.9f, stroke * 1.1f),
        )
        scope.drawRect(
            color = crimson,
            topLeft = Offset(center.x - stroke * 0.95f, top - stroke * 0.35f),
            size = Size(stroke * 1.9f, stroke * 0.34f),
        )
    }

    fun blades() {
        val left = at(220f, radius * 0.72f)
        val right = at(320f, radius * 0.72f)
        val top = at(270f, radius * 1.02f)
        scope.drawLine(steel, left, top, strokeWidth = stroke * 0.55f, cap = StrokeCap.Round)
        scope.drawLine(steel, right, top, strokeWidth = stroke * 0.55f, cap = StrokeCap.Round)
        scope.drawLine(ink, left, top, strokeWidth = stroke * 0.16f, cap = StrokeCap.Round)
        scope.drawLine(ink, right, top, strokeWidth = stroke * 0.16f, cap = StrokeCap.Round)
        scope.drawLine(steel, at(250f, radius * 0.9f), at(290f, radius * 0.9f), strokeWidth = stroke * 0.2f)
    }

    fun flame(angle: Float, color: Color, size: Float) {
        val hub = at(angle)
        val tip = at(angle, radius + size * 0.9f)
        fill(listOf(Offset(hub.x - size * 0.5f, hub.y), tip, Offset(hub.x + size * 0.5f, hub.y)), color)
    }

    fun crack(angle: Float, color: Color, width: Float) {
        val a = at(angle - 9f, radius * 0.92f)
        val b = at(angle - 3f, radius * 0.62f)
        val c = at(angle + 5f, radius * 0.48f)
        val d = at(angle + 11f, radius * 0.16f)
        poly(listOf(a, b, c, d), color, width)
    }

    fun crossedBlades(blade: Color, glow: Color) {
        val one = at(238f, radius * 0.98f) to at(302f, radius * 0.38f)
        val two = at(302f, radius * 0.98f) to at(238f, radius * 0.38f)
        listOf(one, two).forEach { (start, end) ->
            scope.drawLine(glow, start, end, strokeWidth = stroke * 1.1f, cap = StrokeCap.Round)
            scope.drawLine(blade, start, end, strokeWidth = stroke * 0.5f, cap = StrokeCap.Round)
        }
        dot(270f, glow, stroke * 0.4f, radius * 0.7f)
    }

    fun tendril(angle: Float, color: Color, width: Float) {
        val base = at(angle, radius)
        val mid = at(angle + 12f, radius * 0.76f)
        val tip = at(angle + 4f, radius * 0.55f)
        poly(listOf(base, mid, tip), color, width)
        dot(angle + 4f, color, width * 0.4f, radius * 0.55f)
    }

    fun tooth(angle: Float, color: Color, width: Float) {
        val left = at(angle - 4.5f)
        val tip = at(angle, radius + stroke * 0.75f)
        val right = at(angle + 4.5f)
        poly(listOf(left, tip, right), color, width)
    }

    fun crosshair(color: Color) {
        line(250f, 290f, color, stroke * 0.55f, radius * 1.06f, radius * 1.06f)
        line(260f, 280f, color, stroke * 0.55f, radius * 0.9f, radius * 0.9f)
        ring(color, stroke * 0.3f, radius * 0.72f)
        dot(270f, color, stroke * 0.4f, radius * 0.72f)
    }

    fun cross(color: Color, width: Float, reach: Float) {
        line(270f, 270f, color, width, radius * (1f - reach * 0.28f), radius * (1f + reach * 0.18f))
        val bar = at(270f, radius)
        scope.drawLine(
            color = color,
            start = Offset(bar.x - radius * 0.18f, bar.y),
            end = Offset(bar.x + radius * 0.18f, bar.y),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }

    fun slash(angle: Float, color: Color, width: Float) {
        val from = at(angle, radius * 1.02f)
        val to = at(angle + 26f, radius * 0.62f)
        scope.drawLine(color, from, to, strokeWidth = width * 1.6f, cap = StrokeCap.Round)
        scope.drawLine(ink, from, to, strokeWidth = width * 0.6f, cap = StrokeCap.Round)
    }

    fun dagger(color: Color) {
        val tip = at(270f, radius * 0.98f)
        val left = at(262f, radius * 0.8f)
        val right = at(278f, radius * 0.8f)
        fill(listOf(tip, left, right), color)
        dot(270f, violet, stroke * 0.3f, radius * 0.72f)
    }

    fun crescent(color: Color, width: Float, angle: Float) {
        val r = radius * 0.9f
        val hub = at(angle, radius * 0.82f)
        scope.drawArc(
            color = color,
            startAngle = angle - 250f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(hub.x - r, hub.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }

    fun lantern(angle: Float, color: Color, size: Float) {
        val hub = at(angle)
        scope.drawLine(
            color = gold,
            start = Offset(hub.x, hub.y - size * 1.4f),
            end = Offset(hub.x, hub.y - size * 0.4f),
            strokeWidth = size * 0.18f,
        )
        filledOval(0f, (hub.y - center.y), size * 1.1f, size * 1.5f, color)
        dot(angle, gold, size * 0.3f)
    }

    fun axe(blade: Color, edge: Color) {
        val hub = at(270f, radius * 0.82f)
        scope.drawLine(
            color = gold,
            start = Offset(hub.x, hub.y - radius * 0.34f),
            end = Offset(hub.x, hub.y + radius * 0.2f),
            strokeWidth = stroke * 0.4f,
            cap = StrokeCap.Round,
        )
        val head = Path().apply {
            moveTo(hub.x - radius * 0.34f, hub.y - radius * 0.3f)
            lineTo(hub.x + radius * 0.06f, hub.y - radius * 0.42f)
            lineTo(hub.x + radius * 0.12f, hub.y - radius * 0.1f)
            lineTo(hub.x - radius * 0.26f, hub.y - radius * 0.02f)
            close()
        }
        scope.drawPath(head, blade)
        scope.drawPath(head, edge, style = Stroke(stroke * 0.16f))
    }

    fun percent(color: Color) {
        dot(214f, color, stroke * 0.4f, radius * 0.86f)
        dot(326f, color, stroke * 0.4f, radius * 0.86f)
        line(232f, 308f, color, stroke * 0.5f, radius * 0.86f, radius * 0.86f)
    }

    fun leafAt(angle: Float, color: Color, size: Float) {
        val hub = at(angle)
        scope.drawOval(
            color = color,
            topLeft = Offset(hub.x - size * 0.45f, hub.y - size * 0.8f),
            size = Size(size * 0.9f, size * 1.6f),
            style = Stroke(stroke * 0.45f),
        )
    }

    fun sigil(color: Color) {
        val hub = at(270f, radius * 0.86f)
        poly(
            listOf(
                Offset(hub.x - radius * 0.22f, hub.y - radius * 0.16f),
                Offset(hub.x, hub.y + radius * 0.1f),
                Offset(hub.x + radius * 0.22f, hub.y - radius * 0.16f),
            ),
            color,
            stroke * 0.5f,
        )
        ring(color, stroke * 0.3f, radius * 0.62f)
        dot(270f, crimson, stroke * 0.3f, radius * 0.62f)
    }

    fun saucer(color: Color, accent: Color) {
        val top = center.y - radius
        oval(0f, -radius + stroke * 0.2f, stroke * 3.2f, stroke * 0.8f, color, stroke * 0.4f)
        filledOval(0f, -radius - stroke * 0.3f, stroke * 1.5f, stroke * 0.9f, accent)
        dot(270f, Color.White, stroke * 0.24f, radius * 1.06f)
        dot(250f, accent, stroke * 0.18f, radius * 1.06f)
        dot(290f, accent, stroke * 0.18f, radius * 1.06f)
    }

    fun pageCorner() {
        val hub = at(315f, radius * 0.92f)
        val page = Path().apply {
            moveTo(hub.x - stroke * 0.9f, hub.y - stroke * 0.9f)
            lineTo(hub.x + stroke * 0.5f, hub.y - stroke * 0.9f)
            lineTo(hub.x + stroke * 0.5f, hub.y + stroke * 0.2f)
            lineTo(hub.x - stroke * 0.9f, hub.y + stroke * 0.9f)
            close()
        }
        scope.drawPath(page, Color.White.copy(alpha = 0.9f))
        scope.drawPath(page, ink, style = Stroke(stroke * 0.18f))
    }
}
