package dev.aurastore.store.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aurastore.store.R
import dev.aurastore.store.data.DockAnimation
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.downloads.DockProgressLayer
import dev.aurastore.store.ui.downloads.rememberDockPhase
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGES = 3

/**
 * The first-open tour: three pages over the store, each one naming a place that actually exists.
 *
 * It is not a coach-mark pass with arrows to controls that moved while the page was still settling.
 * Every page is a card the user can read at their own text size, with Skip in the same corner on all
 * of them and Back on every page after the first. Both exits persist; the tour never opens twice.
 */
@Composable
fun AppTour(onDone: () -> Unit, onSkip: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val last = page == PAGES - 1
    val skip = {
        onSkip()
    }
    BackHandler {
        if (page > 0) {
            forward = false
            page--
        } else {
            skip()
        }
    }

    val bloom = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // The sheet owns the screen: a tap in the empty field must not reach the store underneath.
        // Composed first, so every control above it wins the hit test.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        )
        // One warm bloom from the top, the same light the splash leaves behind.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(bloom.copy(alpha = 0.09f), Color.Transparent),
                    center = Offset(size.width * 0.5f, -size.height * 0.05f),
                    radius = size.width * 0.9f,
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * 0.5f, -size.height * 0.05f),
            )
        }
        // The last page is the one about the dock, so the dock comes out from under it: one special
        // meter at a time, moving from spot to spot, filling all the way before the next style takes
        // the stage. It loops for as long as the page is open and leaves with the page.
        AnimatedVisibility(
            visible = last,
            enter = fadeIn(tween(650)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize(),
        ) {
            DockField()
        }

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 26.dp),
        ) {
            Header(skip, last)
            Spacer(Modifier.weight(0.9f))
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val direction = if (forward) 1 else -1
                    (fadeIn(tween(260)) + slideInHorizontally(tween(360)) { it / 10 * direction }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(260)) { -it / 14 * direction })
                },
                label = "tour-page",
            ) { index ->
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (index) {
                        0 -> TabsArt()
                        1 -> LookArt()
                        else -> DockArt()
                    }
                    Spacer(Modifier.height(34.dp))
                    Text(
                        stringResource(
                            when (index) {
                                0 -> R.string.tour_tabs_title
                                1 -> R.string.tour_look_title
                                else -> R.string.tour_downloads_title
                            },
                        ),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(
                            when (index) {
                                0 -> R.string.tour_tabs_body
                                1 -> R.string.tour_look_body
                                else -> R.string.tour_downloads_body
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Footer(
                page = page,
                last = last,
                onBack = {
                    if (page > 0) {
                        forward = false
                        page--
                    }
                },
                onNext = {
                    if (last) {
                        onDone()
                    } else {
                        forward = true
                        page++
                    }
                },
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun Header(onSkip: () -> Unit, last: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlyphIcon(
                    Glyph.Brand,
                    size = 16.dp,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            "AURA STORE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.4.sp,
        )
        Spacer(Modifier.weight(1f))
        // Last page ends with its own button; an empty box keeps the header's height steady.
        if (!last) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.tour_skip))
            }
        } else {
            Spacer(Modifier.width(48.dp).height(40.dp))
        }
    }
}

@Composable
private fun Footer(page: Int, last: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (page > 0) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.tour_back))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(PAGES) { index ->
                val on = index == page
                val width by animateDpAsState(if (on) 20.dp else 6.dp, tween(220), label = "tour-dot")
                Box(
                    Modifier
                        .height(6.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(
                            if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                        ),
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Text(
                stringResource(R.string.tour_count, page + 1, PAGES),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            stringResource(if (last) R.string.tour_start else R.string.tour_next),
            style = MaterialTheme.typography.titleMedium,
        )
        if (!last) {
            Spacer(Modifier.width(6.dp))
            GlyphIcon(Glyph.ChevronRight, size = 18.dp)
        }
    }
}

/** Page one: the floating bar, five glyphs, one tab holding the light. */
@Composable
private fun TabsArt() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(66.dp),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val tint = MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.Gamepad, size = 22.dp, tint = MaterialTheme.colorScheme.primary, filled = true)
                    }
                }
                GlyphIcon(Glyph.Grid, size = 22.dp, tint = tint)
                GlyphIcon(Glyph.Search, size = 22.dp, tint = tint)
                GlyphIcon(Glyph.Library, size = 22.dp, tint = tint)
                GlyphIcon(Glyph.Person, size = 22.dp, tint = tint)
            }
        }
    }
}

/** Page two: three faces of the same shop, light, dark and the colour picked from the wallpaper. */
@Composable
private fun LookArt() {
    val warm = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeCard(
            background = Color(0xFFFFFDF9),
            ink = Color(0xFF15120F),
            accent = warm,
            modifier = Modifier
                .width(92.dp)
                .height(124.dp),
        )
        Spacer(Modifier.width(12.dp))
        ThemeCard(
            background = MaterialTheme.colorScheme.primary,
            ink = MaterialTheme.colorScheme.onPrimary,
            accent = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .width(104.dp)
                .height(146.dp),
        )
        Spacer(Modifier.width(12.dp))
        ThemeCard(
            background = Color(0xFF201B16),
            ink = Color(0xFFFBF8F4),
            accent = warm,
            modifier = Modifier
                .width(92.dp)
                .height(124.dp),
        )
    }
}

/**
 * A phone-shaped tile: a status dot, three lines and a pill. The palette is passed in because the
 * point of the page is that all three are the same layout wearing different light.
 */
@Composable
private fun ThemeCard(background: Color, ink: Color, accent: Color, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = background,
        border = BorderStroke(1.dp, ink.copy(alpha = 0.10f)),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(5.dp))
                Box(Modifier.height(3.dp).fillMaxWidth(0.34f).clip(CircleShape).background(ink.copy(alpha = 0.5f)))
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.height(6.dp).fillMaxWidth(0.82f).clip(CircleShape).background(ink.copy(alpha = 0.85f)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.height(4.dp).fillMaxWidth(0.62f).clip(CircleShape).background(ink.copy(alpha = 0.38f)))
            Spacer(Modifier.height(5.dp))
            Box(Modifier.height(4.dp).fillMaxWidth(0.70f).clip(CircleShape).background(ink.copy(alpha = 0.26f)))
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(50),
                color = accent,
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(14.dp),
            ) {}
        }
    }
}

/** Page three: the queue's own card, its byte front, and the count of animations it can wear. */
@Composable
private fun DockArt() {
    val fill = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fill.animateTo(0.68f, tween(900, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val strong = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .height(142.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlyphIcon(
                            Glyph.Download,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .height(7.dp)
                            .fillMaxWidth(0.52f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .height(5.dp)
                            .fillMaxWidth(0.34f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                ) {
                    Text(
                        stringResource(R.string.tour_styles),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp),
            ) {
                val trackH = 7.dp.toPx()
                val trackY = (size.height - trackH) / 2f
                drawRoundRect(
                    color = track,
                    topLeft = Offset(0f, trackY),
                    size = Size(size.width, trackH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f),
                )
                val head = size.width * fill.value
                if (head > 0f) {
                    val rect = Size(head, trackH)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(strong.copy(alpha = 0.55f), strong),
                            endX = head,
                        ),
                        size = rect,
                        topLeft = Offset(0f, trackY),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f),
                    )
                    drawByteFront(head, trackY + trackH / 2f)
                }
            }
        }
    }
}

/** The glowing head of the fill, drawn as three rings so it reads at a glance. */
private fun DrawScope.drawByteFront(x: Float, y: Float) {
    val warm = Color(0xFFFFD9A0)
    drawCircle(warm.copy(alpha = 0.14f), radius = 11.dp.toPx(), center = Offset(x, y))
    drawCircle(warm.copy(alpha = 0.30f), radius = 6.dp.toPx(), center = Offset(x, y))
    drawCircle(Color(0xFFFFF4E2), radius = 2.6.dp.toPx(), center = Offset(x, y))
}

/**
 * One special meter at a time, moving around the last page.
 *
 * Each sample fades in at a spot drawn from a fresh shuffle, fills from nothing to the byte front,
 * then fades out and hands the stage to the next style at another spot. A round walks all twelve
 * once, the next round is shuffled again, and the first spot of a round never repeats the last spot
 * the previous round ended on. The loop lives only as long as page three is on screen: Next, Start
 * exploring or Back takes the field away with it. The cards are placed around the copy rather than
 * under it, so the whole fill stays visible while the page is being read.
 */
@Composable
private fun DockField() {
    val specials = remember { DockAnimation.entries.filter { it.limited } }
    val phase = rememberDockPhase()
    var stages by remember { mutableStateOf(dealtStages(specials, after = null)) }
    var index by remember { mutableIntStateOf(0) }
    val fade = remember { Animatable(if (PlayMotion.enabled) 0f else 1f) }
    val fill = remember { Animatable(if (PlayMotion.enabled) 0.03f else 0.68f) }

    if (PlayMotion.enabled) {
        LaunchedEffect(Unit) {
            while (true) {
                fill.snapTo(0.03f)
                // The fill starts under the fade, so the card arrives with its light already
                // rising. Fading an empty black card is a fade nobody can see. Both curves are
                // eased and long: the card is never switched on or off, it swells into place and
                // thins away.
                coroutineScope {
                    launch {
                        fade.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                    }
                    launch {
                        delay(120)
                        fill.animateTo(1f, tween(2500, easing = LinearEasing))
                    }
                }
                delay(240)
                // Out on a full card, which is the frame worth holding; then the place changes
                // while the card is invisible.
                fade.animateTo(0f, tween(800, easing = FastOutLinearInEasing))
                if (index == stages.lastIndex) {
                    stages = dealtStages(specials, after = stages[index].spot)
                    index = 0
                } else {
                    index++
                }
                delay(60)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scheme = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(14.dp)
        val sheetWidth = maxWidth
        val sheetHeight = maxHeight
        val stage = stages[index]
        val spot = stage.spot
        // The spots are fractions, so a wide card on a narrow sheet would hang over the rim.
        // Clamping keeps the whole card, glow and angle included, on the page.
        val margin = 18.dp
        val left = (sheetWidth * spot.x - spot.width / 2f)
            .coerceIn(margin, (sheetWidth - spot.width - margin).coerceAtLeast(margin))
        val top = (sheetHeight * spot.y - spot.height / 2f)
            .coerceIn(margin, (sheetHeight - spot.height - margin).coerceAtLeast(margin))
        Box(
            Modifier
                .offset(x = left, y = top)
                .size(spot.width, spot.height)
                .graphicsLayer {
                    rotationZ = spot.turn
                    // The card breathes in with its own fill and is gone before it moves, so a
                    // style never cuts to the next one. Under the copy but not hidden by it.
                    alpha = 0.82f * fade.value
                    val presence = 0.92f + 0.08f * fade.value
                    scaleX = presence
                    scaleY = presence
                }
                .clip(shape)
                .background(scheme.surfaceContainerHigh)
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.55f), shape),
        ) {
            DockProgressLayer(
                animation = stage.animation,
                fraction = fill.value,
                state = DownloadState.DOWNLOADING,
                done = false,
                phase = phase,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** One turn of the parade: a style and the place on the sheet it plays. */
private data class DockStage(val animation: DockAnimation, val spot: DockSpot)

/**
 * Deals a round: every limited style once, every spot once, both shuffled. [after] is the spot the
 * previous round finished on; the new round opens somewhere else so the loop never reads as a
 * rotation.
 */
private fun dealtStages(specials: List<DockAnimation>, after: DockSpot?): List<DockStage> {
    val spots = DOCK_SPOTS.shuffled().toMutableList()
    if (after != null && spots.size > 1 && spots[0] == after) {
        spots[0] = spots[1].also { spots[1] = spots[0] }
    }
    return specials.shuffled().mapIndexed { i, animation -> DockStage(animation, spots[i % spots.size]) }
}

/**
 * Where the samples sit, as fractions of the sheet, with the size and angle each one lands at.
 * Placed by hand rather than by a grid: a grid would read as a second bar of tabs, and this is
 * meant to look scattered, with the middle kept for the copy.
 */
private data class DockSpot(
    val x: Float,
    val y: Float,
    val width: Dp,
    val height: Dp,
    val turn: Float,
)

private val DOCK_SPOTS = listOf(
    DockSpot(0.25f, 0.16f, 176.dp, 58.dp, -6f),
    DockSpot(0.58f, 0.15f, 192.dp, 64.dp, 4f),
    DockSpot(0.78f, 0.195f, 156.dp, 52.dp, 7f),
    DockSpot(0.34f, 0.215f, 168.dp, 56.dp, -4f),
    DockSpot(0.19f, 0.23f, 148.dp, 50.dp, 5f),
    DockSpot(0.64f, 0.225f, 184.dp, 60.dp, -6f),
    DockSpot(0.27f, 0.685f, 176.dp, 58.dp, 5f),
    DockSpot(0.73f, 0.675f, 184.dp, 60.dp, -5f),
    DockSpot(0.49f, 0.735f, 196.dp, 64.dp, 3f),
    DockSpot(0.21f, 0.785f, 156.dp, 52.dp, -6f),
    DockSpot(0.79f, 0.775f, 160.dp, 54.dp, 6f),
    DockSpot(0.53f, 0.805f, 172.dp, 56.dp, -3f),
)
