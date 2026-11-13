package dev.aurastore.store.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.aurastore.store.data.GlassEngine
import dev.aurastore.store.data.GlassStyle
import dev.aurastore.store.data.glassEngineRuns
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.glassBlurDp
import dev.aurastore.store.theme.glassBottomGapDp
import dev.aurastore.store.theme.glassChromaticDp
import dev.aurastore.store.theme.glassCornerDp
import dev.aurastore.store.theme.glassEdge
import dev.aurastore.store.theme.glassLensBand
import dev.aurastore.store.theme.glassReadsBackdrop
import dev.aurastore.store.theme.glassRefractionScale
import dev.aurastore.store.theme.glassRefractionShiftDp
import dev.aurastore.store.theme.glassSheenAlpha
import dev.aurastore.store.theme.glassWidthFraction
import dev.aurastore.store.theme.motionTween
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** How much trailing room a list leaves for the bar that floats over it. */
val LocalNavBarInset = compositionLocalOf { 0.dp }

/** The bar's geometry in one place, because the surface, the inset and the copy all have to agree. */
object FloatingBar {
    /** Air above the pill and below the label. Kept constant as the label grows. */
    private val air = 10.dp

    /** The pill, and the label's line at the default text size. */
    private val pill = 32.dp
    private val labelLine = 16.dp

    /** The sheet at the default text size: 10 + 32 + 16 + 10 dp. */
    val height: Dp = air * 2 + pill + labelLine

    /**
     * The largest text scale the bar's own label is drawn at.
     *
     * Five tabs share one row, so the label is the one piece of type on this screen whose width is
     * divided by an integer. At the phone's 2.0× text size *Browse* measured wider than its fifth and
     * the label fell back to an ellipsis — "Brow…" reads as an unfinished word rather than as a
     * navigation item. 1.4× is the largest size at which the longest shipped label still fits its
     * cell on a 360 dp page; past it the label stops growing and the rest of the app keeps scaling.
     */
    const val maxLabelScale = 1.4f

    /** The phone's text size, capped for the bar's label. */
    fun labelScale(fontScale: Float): Float = fontScale.coerceIn(1f, maxLabelScale)

    /** [base] set at [labelScale] of the phone's text size rather than at all of it. */
    fun labelStyle(base: TextStyle, fontScale: Float): TextStyle =
        if (fontScale <= maxLabelScale) base
        else base.copy(fontSize = base.fontSize / fontScale * maxLabelScale)

    /**
     * The sheet's height at the phone's text size.
     *
     * A fixed 68 dp was measured on the Redmi at a 2.0× font scale and the label's line grew to 32 dp, so
     * the content was 64 dp in a 68 dp sheet: the pill sat against the top edge and the label against the
     * bottom. Growing the sheet by exactly what the label gains keeps the same ten dp of air at both ends,
     * and because the inset and the sampled rect are computed from this same function, nothing else has to
     * be told about it.
     */
    fun heightFor(fontScale: Float): Dp = air * 2 + pill + labelLine * labelScale(fontScale)

    /** What a screen adds to its own bottom padding so its last row clears the bar. The bar is an
     *  overlay in either shape, so its height is reserved either way; only the gap it floats on is
     *  optional. */
    fun inset(enabled: Boolean, height: Dp = FloatingBar.height, offset: Float = 0f): Dp =
        height + (if (enabled) glassBottomGapDp(offset).dp else 0.dp)

    /**
     * Air between two stacked floating surfaces, and between one and the edge it floats over.
     *
     * One number rather than two: the gap under the bar is the popup's own offset, and the gap between
     * the bar and the download panel is this constant, so a change to one cannot leave the other behind.
     */
    val dockGap: Dp = 8.dp

    /** The air beside the five cells inside the sheet, so a label never touches the pill's edge. */
    private val sideAir = 6.dp

    /**
     * The selection pill under an icon. Narrower than the cell's old 64 dp floor: with five of them
     * that floor alone made the sheet 320 dp on a 360 dp page, which is a slab across the bottom
     * rather than the pill the bar is supposed to be.
     */
    val pillWidth: Dp = 54.dp
    val pillHeight: Dp = 30.dp

    /**
     * How wide one tab cell has to be: its label with air on both sides, and never less than the
     * selection pill plus its own hair of margin.
     */
    fun cellWidth(labelWidth: Dp): Dp = maxOf(pillWidth + 2.dp, labelWidth + 6.dp)

    /**
     * The sheet's width when it hugs the tabs instead of spanning the page.
     *
     * Measured from the labels rather than set as a fraction, because a fraction is only right on the
     * screen it was chosen for: "94 % of the content width" is a slab on a tablet and a squeeze on a
     * small phone, while five labels are what the bar actually holds. The labels are measured at the
     * phone's own font scale, so a 2× text size widens the pill instead of clipping words inside it.
     */
    fun pillWidth(labelWidth: Dp, tabs: Int = 5): Dp = cellWidth(labelWidth) * tabs + sideAir * 2

    /**
     * The width actually drawn: the pill, scaled by the Bar Width slider and capped by the page itself.
     *
     * The cap is what keeps a wide label set — or a tablet in landscape — inside the content area.
     * The Bar Width slider sweeps from 60 % up to 150 %: below 100 % it squeezes the pill, at 100 %
     * it draws at the measured pill width, and above 100 % it expands the pill up to 150 % (capped at content width).
     */
    fun sheetWidth(pill: Dp, widthFraction: Float, content: Dp): Dp {
        val fraction = widthFraction.coerceIn(0.6f, 1.5f)
        val width = if (fraction > 1f) pill * fraction else minOf(pill, content * fraction)
        return width.coerceAtMost(content)
    }

    /** The panel's margins, so its card lines up with the sheet under it. */
    val dockSide: Dp = 12.dp

    /**
     * How much room a screen has to leave under its content for the surfaces floating over it.
     *
     * This is the *only* sum written for that band: the bar's share comes from [inset], the panel's
     * from its measured height plus [dockGap], and each is included only while its surface is actually
     * up. The panel used to place itself by this same arithmetic in the page, which is how the two came
     * to disagree by one gap; it is now a column child of the bar's own window, so this figure is used
     * for the reserve and for nothing else.
     */
    fun reserve(
        fontScale: Float,
        barShown: Boolean,
        floating: Boolean,
        offset: Float,
        dockHeight: Dp?,
        dockJoined: Boolean = false,
    ): Dp = (if (barShown) inset(floating, heightFor(fontScale), offset) else 0.dp) +
        // A joined panel is part of the sheet: the seam is a shared edge, not air, so the page
        // reserves the two heights and no gap.
        (if (dockHeight != null) dockHeight + (if (dockJoined) 0.dp else dockGap) else 0.dp)
}

/**
 * One tab's label and the screen it opens.
 *
 * The screen is carried alongside the label because a reordered strip draws the fives in an order that
 * is not the screens' own: index 0 of the row can open screen 3, and every callback in [BarTabRow]
 * must keep speaking in screens.
 */
data class BarTab(@StringRes val label: Int, val screen: Int)

/**
 * The bar's icon geometry, in one place.
 *
 * The tab glyph was drawn at 24 dp inside a 32 dp pill, which left four dp of ring and read as a
 * picture set into a button rather than as the button's own mark. 22 dp is the size Material's
 * navigation icons are specified at against a 32 dp container, and the stroke thickens as the glyph
 * fills, so a selected tab reads bolder without the icon growing into the pill's edge.
 */
object BarIcon {
    /** The drawn size of a tab glyph. */
    val glyph = 22.dp

    /** The box it is centred in; the badge hangs off its corner, so the row does not re-space. */
    val box = 26.dp

    /** The stroke, from the resting outline to the solid mark's own edge. */
    fun strokeFor(fill: Float): Dp = 1.8.dp + 1.0.dp * fill.coerceIn(0f, 1f)
}

/**
 * The width the five tabs need on this phone.
 *
 * Reads the widest label at the size the row draws it — including the phone's font scale — so the pill
 * is as narrow as the bar can be without ellipsising a tab. Measuring is the only way to know: "Browse"
 * and "Games" are not the same width, and neither are the same word in two languages.
 */
@Composable
fun rememberTabsPillWidth(tabs: List<BarTab>): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    // Measured at the size the row draws — capped like the label itself — so the pill the bar asks
    // for is the pill it needs, no wider.
    val drawn = FloatingBar.labelStyle(style, LocalDensity.current.fontScale)
    val widest = tabs.maxOfOrNull { tab ->
        measurer.measure(stringResource(tab.label), drawn).size.width
    } ?: 0
    return FloatingBar.pillWidth(
        with(LocalDensity.current) { widest.toDp() },
        tabs = tabs.size.coerceAtLeast(1),
    )
}

/**
 * The five tabs, each given its whole fifth of the sheet.
 *
 * A hand-built row rather than `NavigationBar`, because the Material item failed to answer most of its
 * own slot on this handset: measured through the bar, a tap at x=100 — inside the Games tab's fifth —
 * did nothing, and so did x=965 inside You's, while x=134 and x=950 switched tabs. The live band was
 * about the icon's own 24 dp, so seven tenths of a 68 dp bar was dead. A `weight(1f)` cell that carries
 * the `selectable` itself cannot have that gap, and it also gives the tab one semantics node the width
 * of the fifth, which is what a screen reader should focus rather than a 24 dp halo.
 *
 * The look is Material's: a 64×32 pill under the icon, the icon in `onSecondaryContainer` when selected
 * and `onSurfaceVariant` otherwise, and the label beneath both. Colours cross-fade on the short token
 * every other in-place change in this app uses, so a device with motion switched off gets no fade.
 */
@Composable
fun BarTabRow(
    tabs: List<BarTab>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    icon: @Composable (index: Int, fill: Float) -> Unit,
    onSelect: (index: Int) -> Unit,
    /** Whether the selection pill glides to the new tab, or is simply there. */
    slide: Boolean = true,
    /** Whether the glyph pops and fills on selection, or changes with no motion at all. */
    iconMotion: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    // The selection pill is one object that travels between the fives rather than five pills fading.
    // Play's bar moves the container under the new tab, and that movement is what tells you the two
    // tabs are neighbours on one strip; a fade says only that something changed. The width is measured
    // because a fifth is not a constant: it is 187 px on this handset and would differ on any other.
    var rowWidth by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    // Where the pill sits inside a cell: the cell centres an icon box plus the label line, so the
    // pill's top has to follow the box rather than be a constant that drifts at a larger text size.
    val pillTop = (FloatingBar.heightFor(density.fontScale) -
        (FloatingBar.pillHeight + 16.dp * FloatingBar.labelScale(density.fontScale))) / 2f
    val cellWidth = if (tabs.isEmpty()) 0f else rowWidth / tabs.size
    val targetPillX = if (cellWidth <= 0f || selectedIndex < 0) {
        0.dp
    } else {
        with(density) {
            (cellWidth * (selectedIndex + 0.5f) - FloatingBar.pillWidth.toPx() / 2f).toDp()
        }
    }
    val pillX by animateDpAsState(
        targetValue = targetPillX,
        // A zero-length tween is the honest "no animation": the same state, applied on the frame
        // the selection changes, with none of the animator's bookkeeping left running.
        animationSpec = if (slide && PlayMotion.enabled) {
            spring(dampingRatio = 0.72f, stiffness = 540f)
        } else {
            tween(0)
        },
        label = "barPillX",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(FloatingBar.heightFor(LocalDensity.current.fontScale))
            .onSizeChanged { rowWidth = it.width.toFloat() }
            .selectableGroup(),
    ) {
        if (selectedIndex in tabs.indices && rowWidth > 0f) {
            val pillSpanDp = with(density) { (cellWidth.coerceAtLeast(1f)).toDp() }
            val travelFraction = ((abs((pillX - targetPillX).value)) / pillSpanDp.value.coerceAtLeast(1f))
                .coerceIn(0f, 1f)
            val pillStretch = sin(travelFraction * Math.PI.toFloat())
            Box(
                Modifier
                    // The lambda overload: layout reads the offset without recomposing the bar.
                    .offset { IntOffset(pillX.roundToPx(), pillTop.roundToPx()) }
                    .size(width = FloatingBar.pillWidth, height = FloatingBar.pillHeight)
                    .graphicsLayer {
                        scaleX = 1f + 0.14f * pillStretch
                        scaleY = 1f - 0.08f * pillStretch
                    }
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.primaryContainer),
            )
        }
        Row(Modifier.fillMaxSize()) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val ink by animateColorAsState(
                if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                motionTween(PlayMotion.SHORT),
                label = "tabInk",
            )
            // The pill moves under the five; the icon that owns it also settles a hair larger and
            // lifts a pixel into the pill. It is the same token as the colour, so the movement reads
            // as the strip acknowledging the tap rather than as a second, unrelated animation.
            // One spring for the glyph: it pops a hair and solidifies at once, so a tap reads as one
            // gesture. A tween on the scale alone made the pop and the fill land at different moments.
            // The phone's own "remove animations" wins over the app's switch: a spring is not
            // something the animator can shorten, so it is not used at all when motion is off.
            val pop = iconMotion && PlayMotion.enabled
            val iconScale by animateFloatAsState(
                targetValue = if (pop && selected) 1.10f else 1f,
                animationSpec = if (pop) spring(dampingRatio = 0.45f, stiffness = 560f) else tween(0),
                label = "tabIconScale",
            )
            val fill by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                // The fill still happens with motion off — a selected tab must read as selected —
                // it just arrives on the same frame rather than solidifying over a spring.
                animationSpec = if (pop) spring(dampingRatio = 0.62f, stiffness = 540f) else tween(0),
                label = "tabIconFill",
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(width = FloatingBar.pillWidth, height = FloatingBar.pillHeight)
                        // Read in the draw phase: selecting a tab must not recompose the other four
                        // cells, and the icon is already redrawn for its colour.
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            translationY = -(iconScale - 1f) * 10.dp.toPx()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides ink) { icon(index, fill) }
                }
                Text(
                    stringResource(tab.label),
                    style = FloatingBar.labelStyle(MaterialTheme.typography.labelMedium, density.fontScale),
                    color = ink,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    // At the phone's default text size every label fits its fifth with room to spare;
                    // measured at 1.3× *Games* is 140 px of a 187 px cell. The style above stops the
                    // label growing past 1.4× for exactly this reason, so the ellipsis is now a
                    // backstop for a longer translation rather than the 2× behaviour. The semantics
                    // still carry the whole label either way.
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        }
    }
}

/**
 * The navigation bar as a sheet of glass, with whatever the caller puts on top.
 *
 * Every material is painted here and starts at zero, so the shipped bar is just the tabs over the page:
 * a sampled, blurred copy of the page once Blur is above zero, a sheen and lens band from
 * [GlassStyle.refraction], red/cyan rims from [GlassStyle.chromatic], and a corner and width from
 * [GlassStyle.corner] / [GlassStyle.width].
 *
 * The copy is drawn three ways, one per [dev.aurastore.store.data.GlassEngine]: a runtime shader bends
 * and splits it on Android 13+, a magnified image stands in for the lens down to Android 8, and Classic
 * keeps it flat. With `liquid` off none of them runs — the sheet is a near-opaque surface instead.
 *
 * **The sheet is drawn in a window of its own** — a popup, which is transparent wherever it paints
 * nothing. That is the whole trick: the page under the bar is not sampled, copied or blurred, it is
 * the activity's own surface showing through the hole the bar leaves. The version before this one read
 * the window back at 320 px and drew the result under the sheet, which is why the text under the bar
 * looked like a blocky smear; there is nothing to read back any more.
 *
 * The window is exactly the pill's rectangle, gap included: everything the sheet paints is clipped to
 * its own shape, so the margin the window used to keep around it drew nothing and only swallowed taps.
 * A tap beside or above a floating bar now reaches the page instead of dying in a transparent window.
 *
 * With [GlassStyle.floating] off the sheet loses its margins and its radius and becomes a bar pinned
 * flat to the bottom of the content area. The bar sits inside `Scaffold`'s already-inset content area,
 * so it must not add `navigationBarsPadding` of its own; the first version did, and the bar floated
 * 48 dp clear of the system bar on a gesture-nav panel.
 */
@Composable
fun FloatingBarSurface(
    style: GlassStyle,
    tint: Color,
    modifier: Modifier = Modifier,
    /** Live GPU display-list layer of the page content, drawn synchronously with zero frame delay. */
    pageLayer: GraphicsLayer? = null,
    // The download panel, when there is one. It is drawn by this overlay rather than by the page so
    // that the two surfaces are laid out by one node instead of by two formulas that have to agree
    // about where the bottom of the screen is. See the column below.
    dockShown: Boolean = false,
    /** The panel; its argument is true when it is joined to the bar and must square its bottom. */
    dock: @Composable (joined: Boolean) -> Unit = {},
    barShown: Boolean = true,
    /** The width the tabs need, from [rememberTabsPillWidth]. Null draws the slider's share instead. */
    pillWidth: Dp? = null,
    content: @Composable () -> Unit,
) {
    // The content area in window pixels: where the overlay sits and the space the sampled rect is cut
    // out of. Measured rather than derived from the window, because what is left after the system bars
    // and the brand bar is something only layout knows.
    var area by remember { mutableStateOf(IntRect.Zero) }
    val window = LocalContext.current.activityWindow()
    val density = LocalDensity.current
    val gapDp = if (style.floating) glassBottomGapDp(style.offset).dp else 0.dp
    val gapPx = with(density) { gapDp.roundToPx() }
    val stackGapDp = if (barShown) gapDp else FloatingBar.dockGap

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val measured = IntRect(
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt(),
                    bounds.right.roundToInt(),
                    bounds.bottom.roundToInt(),
                )
                if (measured != area) area = measured
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (!area.isEmpty) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = stackGapDp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = dockShown,
                    // The band opens and closes; the card inside it is the panel's own business.
                    // Growing from the bottom keeps the bar pinned where it is while the panel
                    // arrives above it, which is what "stacked" has to look like.
                    enter = fadeIn(PlayMotion.spec(PlayMotion.SHORT)) +
                        expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut(PlayMotion.spec(PlayMotion.SHORT)) +
                        shrinkVertically(shrinkTowards = Alignment.Bottom),
                ) {
                    // One surface: the panel is drawn at exactly the bar's width, its bottom
                    // corners are squared, and the sheet's top corners are squared to meet it, so
                    // the two read as a single object rather than two cards with a gap. With no
                    // bar (a route page) the panel keeps its own rounded shape and the ordinary
                    // gap against the content edge.
                    val joined = barShown
                    val widthFraction = if (style.floating) glassWidthFraction(style.width) else 1f
                    val drawnWidth = if (pillWidth != null) {
                        FloatingBar.sheetWidth(pillWidth, widthFraction, with(density) { area.width.toDp() })
                    } else null
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = if (drawnWidth != null) Modifier.width(drawnWidth)
                        else Modifier.fillMaxWidth().padding(horizontal = FloatingBar.dockSide),
                    ) {
                        dock(joined)
                    }
                }
                if (barShown) {
                    // Joined only when the panel is actually seated on the bar: otherwise the
                    // sheet is a pill and needs all four corners round. Passing `true` here
                    // squared the top edge even with no panel above it, so the bar read as a
                    // slab cut off at the top rather than as a floating sheet.
                    BarSheet(
                        style = style,
                        tint = tint,
                        area = area,
                        window = window,
                        pageLayer = pageLayer,
                        gapPx = gapPx,
                        pillWidth = pillWidth,
                        joined = dockShown,
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun BarSheet(
    style: GlassStyle,
    tint: Color,
    area: IntRect,
    window: Window?,
    pageLayer: GraphicsLayer?,
    gapPx: Int,
    pillWidth: Dp?,
    joined: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val floating = style.floating
    val liquid = style.liquid
    val barHeight = FloatingBar.heightFor(density.fontScale)
    val corner = if (floating) glassCornerDp(style.corner).dp else 0.dp
    // Joined, the sheet's top edge is the seam it shares with the panel above it.
    val shape = if (joined) RoundedCornerShape(0.dp, 0.dp, corner, corner) else RoundedCornerShape(corner)
    // The reference's three layout values: how wide the sheet is, how round and how far off the edge.
    val widthFraction = if (floating) glassWidthFraction(style.width) else 1f
    val edge = glassEdge(tint)
    val blurDp = glassBlurDp(style.blur)
    // The copy is read when any layer actually consumes it: Blur always, the shader's bend and
    // channel split, and iOS 8's magnification. Refraction at Blur 0 is therefore a sharp lens rather
    // than a dead slider; with every effect at zero nothing is read and the page stays untouched.
    val engine = glassEngineRuns(style.engine)
    val sampled = glassReadsBackdrop(liquid, style.blur, style.refraction, style.chromatic, engine)
    val sidePx = with(density) {
        val drawn = if (floating && pillWidth != null) {
            FloatingBar.sheetWidth(pillWidth, widthFraction, area.width.toDp())
        } else {
            area.width.toDp() * widthFraction
        }
        ((area.width.toDp() - drawn) / 2f).roundToPx()
    }
    val rect = with(density) {
        barRectIn(
            // The sampled strip lives exactly where the sheet is drawn, inside the content area.
            area = area,
            heightPx = barHeight.roundToPx(),
            sidePx = sidePx,
            gapPx = gapPx,
            floating = floating,
        )
    }
    val blurPx = with(density) { blurDp.dp.roundToPx() }
    val useLiveLayer = sampled && pageLayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val backdrop = rememberBarBackdrop(
        window = window,
        rect = rect,
        enabled = sampled && !useLiveLayer,
        blurPx = blurPx,
    )
    val refractionScale = glassRefractionScale(style.refraction)
    val sheen = glassSheenAlpha(style.refraction)
    val band = glassLensBand(style.depth)
    val fringe = glassChromaticDp(style.chromatic).dp
    val refractionPx = with(density) { glassRefractionShiftDp(style.refraction).dp.toPx() }
    val chromaPx = with(density) { glassChromaticDp(style.chromatic).dp.toPx() }
    // The shader is built once and fed new uniforms per frame; only the iOS 13 engine on a release
    // with RuntimeShader asks for one. A build failure leaves the sampled copy drawn the older way
    // rather than blanking the bar.
    val shader = if (engine == GlassEngine.IOS_13 && Build.VERSION.SDK_INT >= 33 &&
        sampled && (useLiveLayer || backdrop != null)
    ) {
        remember {
            runCatching { RuntimeShader(LIQUID_GLASS_SHADER) }
                .onFailure { Log.w("GlassShader", "AGSL unavailable; drawing the copy flat", it) }
                .getOrNull()
        }
    } else {
        null
    }

    // The sheet is the pill and nothing else — the same rect the sampler reads, so the sheet and its
    // backdrop cannot drift apart. Anything wider or taller would paint nothing (the sheet is clipped to
    // its own shape) and only eat taps meant for the page underneath.
    Box(
        Modifier
            .width(with(density) { rect.width.toDp() })
            .height(with(density) { rect.height.toDp() }),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape),
        ) {
            if (!liquid || !sampled || (!useLiveLayer && backdrop == null)) {
                // Liquid glass off is the reference's flat bar: the page is not sampled and no rim is
                // drawn, just the surface the tabs have always sat on.
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            if (liquid) Brush.verticalGradient(
                                0f to tint.copy(alpha = 0.88f),
                                1f to tint.copy(alpha = 0.94f),
                            ) else Brush.verticalGradient(
                                0f to tint.copy(alpha = 0.94f),
                                1f to tint.copy(alpha = 0.94f),
                            ),
                        ),
                )
            }
            if (useLiveLayer) {
                LiveLayerBackdrop(
                    pageLayer = pageLayer,
                    backdropBg = MaterialTheme.colorScheme.background,
                    area = area,
                    rect = rect,
                    engine = engine,
                    shader = shader,
                    blurPx = blurPx.toFloat(),
                    band = band,
                    corner = corner,
                    refractionScale = refractionScale,
                    refractionPx = refractionPx,
                    chromaPx = chromaPx,
                    modifier = Modifier.matchParentSize(),
                )
            } else if (sampled && backdrop != null) {
                if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                    ShaderBackdrop(
                        shader = shader,
                        backdrop = backdrop,
                        band = band,
                        corner = corner,
                        refractionPx = refractionPx,
                        chromaPx = chromaPx,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    // The drawn engines: a real copy of the page, blurred and, on iOS 8+, magnified
                    // from the top edge so the lens reads as one piece of glass rather than as a
                    // picture pasted on the bar. Classic keeps the copy flat. iOS 13 also lands here
                    // when its shader could not compile, so the fallback keeps the lens instead of
                    // turning a compile failure into a missing effect.
                    Image(
                        bitmap = backdrop,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                if (engine != GlassEngine.CLASSIC) {
                                    scaleX = refractionScale
                                    scaleY = refractionScale
                                }
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            },
                    )
                }
            }
            if (liquid && sampled && (useLiveLayer || backdrop != null)) {
                // Translucent glass body tint so tab glyphs and labels remain crisp over high-contrast
                // scrolling text while letting the refracted, chromatic-split backdrop shine through.
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to tint.copy(alpha = 0.42f),
                                1f to tint.copy(alpha = 0.62f),
                            ),
                        ),
                )
            }
            if (liquid && sheen > 0f) {
                // Light entering the lens: a band whose height is Refraction depth.
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            val height = size.height * band
                            if (height > 0f) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.White.copy(alpha = sheen),
                                        1f to Color.Transparent,
                                        endY = height,
                                    ),
                                )
                            }
                        },
                )
            }
            if (liquid && style.chromatic > 0f) {
                // Two rims, one each side of the edge, reading as the colour split of a thick lens.
                Box(
                    Modifier
                        .matchParentSize()
                        .offset(x = -fringe)
                        .border(
                            width = 1.2.dp,
                            color = Color(0xFFFF3B30).copy(alpha = 0.55f * style.chromatic),
                            shape = shape,
                        ),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .offset(x = fringe)
                        .border(
                            width = 1.2.dp,
                            color = Color(0xFF00E5FF).copy(alpha = 0.55f * style.chromatic),
                            shape = shape,
                        ),
                )
            }
            if (sampled && (useLiveLayer || backdrop != null)) {
                // A blurred page still needs an edge on a dark screen; this is the glass catching the
                // room, not a control of its own.
                Box(
                    Modifier
                        .matchParentSize()
                        .border(
                            width = 1.dp,
                            color = edge.color.copy(alpha = 0.14f * edge.scale),
                            shape = shape,
                        ),
                )
            } else if (liquid && floating) {
                // The flat fallback is still a floating pill, so it gets the same hairline. Without
                // it the tint's rounded edge dissolved into a page of a similar colour and the bar
                // read as a floating row of labels rather than as one surface.
                Box(
                    Modifier
                        .matchParentSize()
                        .border(
                            width = 1.dp,
                            color = edge.color.copy(alpha = 0.14f * edge.scale),
                            shape = shape,
                        ),
                )
            }
            Box(Modifier.matchParentSize()) { content() }
        }
    }
}

/**
 * Draws the live GPU [pageLayer] inside the sheet on the exact same vsync frame as the scrolling page,
 * applying hardware [RenderEffect] blur and AGSL [RuntimeShader] refraction/chromatic dispersion
 * with zero readback latency.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun LiveLayerBackdrop(
    pageLayer: GraphicsLayer,
    backdropBg: Color,
    area: IntRect,
    rect: IntRect,
    engine: GlassEngine,
    shader: RuntimeShader?,
    blurPx: Float,
    band: Float,
    corner: Dp,
    refractionScale: Float,
    refractionPx: Float,
    chromaPx: Float,
    modifier: Modifier = Modifier,
) {
    val offsetX = (area.left - rect.left).toFloat()
    val offsetY = (area.top - rect.top).toFloat()
    val barLayer = rememberGraphicsLayer()
    Canvas(
        modifier.graphicsLayer {
            clip = true
            val widthPx = size.width.coerceAtLeast(1f)
            val heightPx = size.height.coerceAtLeast(1f)
            val blurEffect = if (blurPx >= 1f) {
                RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            } else {
                null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                shader.setFloatUniform("size", widthPx, heightPx)
                shader.setFloatUniform("cornerRadius", corner.toPx())
                shader.setFloatUniform("refractionHeight", heightPx * band)
                shader.setFloatUniform("refractionAmount", refractionPx)
                shader.setFloatUniform("lensCenter", widthPx * 0.5f)
                shader.setFloatUniform("lensHalfWidth", widthPx * 0.46f)
                shader.setFloatUniform("lensStrength", refractionPx * 0.85f)
                shader.setFloatUniform("chromaDistance", chromaPx * 2.2f)
                val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                val combined = if (blurEffect != null) {
                    RenderEffect.createChainEffect(shaderEffect, blurEffect)
                } else {
                    shaderEffect
                }
                renderEffect = combined.asComposeRenderEffect()
            } else {
                renderEffect = blurEffect?.asComposeRenderEffect()
                if (engine != GlassEngine.CLASSIC) {
                    scaleX = refractionScale
                    scaleY = refractionScale
                }
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        },
    ) {
        val w = size.width.roundToInt().coerceAtLeast(1)
        val h = size.height.roundToInt().coerceAtLeast(1)
        barLayer.record(IntSize(w, h)) {
            drawRect(backdropBg)
            clipRect(0f, 0f, size.width, size.height) {
                translate(left = offsetX, top = offsetY) {
                    drawLayer(pageLayer)
                }
            }
        }
        drawLayer(barLayer)
    }
}

/**
 * The sampled page through the iOS 13 shader.
 *
 * Uniforms are rewritten on every draw rather than rebuilt: the shader itself is compiled once per
 * composition, and a slider moving at sixty frames a second only changes numbers. The child
 * `BitmapShader` is sampled in the sheet's pixels, so its matrix scales the 640 px copy up to the
 * sheet; without it the shader would read the top-left corner of the sample at every pixel.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderBackdrop(
    shader: RuntimeShader,
    backdrop: ImageBitmap,
    band: Float,
    corner: Dp,
    refractionPx: Float,
    chromaPx: Float,
    modifier: Modifier = Modifier,
) {
    val bitmap = backdrop.asAndroidBitmap()
    val bitmapShader = remember(bitmap) {
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    val matrix = remember { Matrix() }
    val paint = remember(shader) { Paint().apply { this.shader = shader } }
    Canvas(modifier) {
        matrix.reset()
        matrix.setScale(size.width / bitmap.width, size.height / bitmap.height)
        bitmapShader.setLocalMatrix(matrix)
        shader.setInputShader("content", bitmapShader)
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("cornerRadius", corner.toPx())
        shader.setFloatUniform("refractionHeight", size.height * band)
        shader.setFloatUniform("refractionAmount", refractionPx)
        shader.setFloatUniform("lensCenter", size.width * 0.5f)
        shader.setFloatUniform("lensHalfWidth", size.width * 0.46f)
        shader.setFloatUniform("lensStrength", refractionPx * 0.85f)
        shader.setFloatUniform("chromaDistance", chromaPx * 2.2f)
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
    }
}

/** The activity a context belongs to, whose window the sampler reads, or null in a preview. */
private fun Context.activityWindow(): Window? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context.window
        context = context.baseContext
    }
    return null
}
