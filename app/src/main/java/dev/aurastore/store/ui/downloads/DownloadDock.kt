package dev.aurastore.store.ui.downloads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.aurastore.store.R
import dev.aurastore.store.data.Settings
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.download.TransferRate
import dev.aurastore.store.theme.EmphasizedAccelerateEasing
import dev.aurastore.store.theme.EmphasizedDecelerateEasing
import dev.aurastore.store.theme.EmphasizedEasing
import dev.aurastore.store.ui.ArtImage
import dev.aurastore.store.ui.FloatingBar
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardAccelerateEasing
import dev.aurastore.store.theme.StandardEasing
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.downloadRowStatus
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.playPressed
import dev.aurastore.store.ui.statusText
import kotlin.math.abs

/**
 * Every row the dock can show, in the order it shows them.
 *
 * The order is the one a person would care about: transfers actually moving, then the ones waiting
 * their turn, then the ones the user paused, then the finished files whose install is the next thing
 * to do. The newest row wins inside a group because the queue is appended to, and a fresh request is
 * the one the user just made. A finished (installed), cancelled or failed row is deliberately absent:
 * the dock is a control panel, not a second queue, and those rows already say what they are in the list.
 *
 * The first row is what the panel opens on. The rest are what a horizontal swipe flips through, and
 * what a long press lists, so both gestures and the `+N` badge read from this one order.
 */
internal fun dockCandidates(items: List<DownloadItem>): List<DownloadItem> {
    val newest = items.asReversed()
    fun group(vararg states: DownloadState) = newest.filter { it.state in states }
    return group(DownloadState.DOWNLOADING, DownloadState.VERIFYING) +
        group(DownloadState.QUEUED) +
        group(DownloadState.PAUSED) +
        // A saved archive has no next action: its file already sits in Downloads and the 3-dots menu
        // is where a second look belongs. Keeping it here would put an Install button on a zip.
        newest.filter {
            (it.state == DownloadState.READY && !it.archive) ||
                it.state == DownloadState.NEEDS_PERMISSION ||
                it.state == DownloadState.AWAITING_CONFIRMATION
        }
}

/**
 * The dock's page-turn rule.
 *
 * The same shape as [dev.aurastore.store.ui.SwipeStep], on a much narrower card: a fifth of the card
 * or a flick turns it, a slow nudge springs back, and the ends stay put. The rule lives here rather
 * than in the gesture so the six cases can be checked without a screen.
 */
internal object DockSwipe {
    /** How far the card must travel before letting go turns it: 18 % of its width. */
    const val COMMIT_FRACTION = 0.18f

    /** A flick this fast turns the card even when it ends early: 900 px a second. */
    const val FLICK_VELOCITY = 900f

    /**
     * The step to take: `1` for the next row, `-1` for the previous, `0` to stay.
     *
     * Dragging left (negative [dx]) asks for the next row, because the card moves left the way a
     * pager's next page comes in from the right. The ends clamp, so a swipe outward on the first or
     * last row springs back instead of wrapping to a row the finger was not pointing at.
     */
    fun step(position: Int, count: Int, dx: Float, velocityX: Float, widthPx: Float): Int {
        if (count < 2 || position !in 0 until count || widthPx <= 0f) return 0
        val farEnough = abs(dx) > widthPx * COMMIT_FRACTION
        val flicked = abs(velocityX) > FLICK_VELOCITY
        if (!farEnough && !flicked) return 0
        // A flick says the direction even when the finger barely moved, so distance and speed each
        // get a turn; distance wins when both speak, because it is the movement the user saw.
        val forward = if (farEnough) dx < 0f else velocityX < 0f
        val next = (position + if (forward) 1 else -1).coerceIn(0, count - 1)
        return next - position
    }
}

/** The one control the panel offers, derived from the row so the two can never disagree. */
private enum class DockControl { PAUSE, RESUME, INSTALL, UNINSTALL, ALLOW }

/**
 * The floating download panel above the navigation bar.
 *
 * It answers four questions without opening anything: which app is downloading, how far along it is,
 * how fast it is going, and what the one useful control is right now — pause while bytes move, resume
 * once they stop, Install when the file has verified. The whole card is a way into that download's
 * page, so the numbers that do not fit here (digest, host, history) are one tap away.
 *
 * Everything that is a number is also a drawing, because a panel that sits over the page for the
 * length of a 300 MB download has to look like it is doing something: the app's icon is wrapped in a
 * ring that fills with the transfer, the bar under it fills on the same curve, and both carry one
 * travelling highlight while bytes move. When the file verifies, the ring closes, a tick lands on the
 * icon and the pause control becomes Install — the same card, changed rather than replaced.
 *
 * Deliberately opaque rather than glass: it sits over moving content, and a translucent panel with
 * two lines of small type over a scrolling list was exactly the unreadable part of an old bar.
 */
@Composable
fun DownloadDock(
    item: DownloadItem,
    /** Where this row sits in [dockCandidates], for the ends and the page-turn rule. */
    position: Int,
    /** How many rows [dockCandidates] holds; the `+N` badge counts the others. */
    total: Int,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onInstall: () -> Unit,
    /** Take a page turn: `1` for the next row, `-1` for the previous. */
    onStep: (Int) -> Unit,
    /** A hold on the card opens the list of every row the panel can show. */
    onCatalogue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** True when the navigation bar sits directly under this card: the seam is a shared edge. */
    seam: Boolean = false,
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var cardWidth by remember { mutableFloatStateOf(0f) }
    // The card follows the finger just enough to say it is draggable. A float set synchronously,
    // snapped into the layer while the finger is down, avoids the frame of lag an Animatable launched
    // from every pointer event costs; the same float is the target of the settle once the hand lifts.
    val settle by animateFloatAsState(
        targetValue = dragX,
        animationSpec = if (dragging) snap() else PlayMotion.spec(PlayMotion.MEDIUM, StandardEasing),
        label = "dock-drag",
    )
    val pressSource = remember { MutableInteractionSource() }
    val nextLabel = stringResource(R.string.dock_next)
    val previousLabel = stringResource(R.string.dock_previous)
    val dockShape = RoundedCornerShape(
        topStart = 26.dp,
        topEnd = 26.dp,
        bottomStart = if (seam) 0.dp else 26.dp,
        bottomEnd = if (seam) 0.dp else 26.dp,
    )
    val scheme = MaterialTheme.colorScheme
    // Same cap as the bar's own label, for the same reason: this card is as wide as the bar, so its
    // two lines are the first thing a 2× text size pushes into an ellipsis. Past 1.4× they hold.
    val fontScale = LocalDensity.current.fontScale
    val percent = (item.progress * 100).toInt().coerceIn(0, 100)
    val active = item.isActive
    // The ring and the bar are motion, not decoration: a queued row has not moved a byte, so its ring
    // must be still even though the row is still "active" enough to offer Pause.
    val moving = item.state == DownloadState.DOWNLOADING || item.state == DownloadState.VERIFYING
    val ready = item.state == DownloadState.READY ||
        item.state == DownloadState.NEEDS_PERMISSION ||
        item.state == DownloadState.AWAITING_CONFIRMATION
    // The ring and the bar run on the same animated fraction, so the two never disagree about how
    // full the transfer is — and a progress tick that lands 60 ms late moves both at once.
    val fraction by animateFloatAsState(
        targetValue = item.progress.coerceIn(0f, 1f),
        // Linear, not sprung: bytes arrive at a rate, and the fill should read as that rate. A tween
        // retargets on every tick, so the head never stops between them and never overshoots a
        // number the sentence beside it is already showing.
        animationSpec = PlayMotion.spec(900, LinearEasing),
        label = "dock-progress",
    )
    // The percentage counts rather than jumping: a percentage is the number the eye watches, and a
    // number that teleports reads as a screen that was not looking.
    val shownPercent by animateIntAsState(
        targetValue = percent,
        animationSpec = PlayMotion.spec(PlayMotion.MEDIUM, StandardEasing),
        label = "dock-percent",
    )
    // One travelling phase shared by both drawings. A paused, queued or finished row is still: a
    // panel that keeps moving after the bytes stop is claiming work that is not happening.
    val phase = if (moving) rememberDockPhase() else 0f
    // The chosen meter. The card's fill is the panel's own long-running animation, so the style is
    // read here rather than pushed in from the shell: a pick in Settings repaints this card alone.
    val dockAnimation by Settings.dockAnimation.collectAsStateWithLifecycle()
    // The border answers the state: transparent while bytes move, primary once there is a file to
    // install. It is the quietest way to say "this row is finished" without adding another line.
    val border by animateColorAsState(
        targetValue = if (ready) scheme.primary.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = PlayMotion.spec(PlayMotion.MEDIUM, StandardEasing),
        label = "dock-border",
    )
    Surface(
        shape = dockShape,
        color = scheme.surfaceContainerHigh,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .fillMaxWidth()
            .clip(dockShape)
            .onSizeChanged { cardWidth = it.width.toFloat() }
            .graphicsLayer { translationX = settle }
            // The click owns the tap and the hold; the drag is on the content, one node below, so
            // the two never race for the same down. A drag past touch slop consumes its movement and
            // the press cancels, which is what keeps a swipe from also opening the page and a hold
            // from also counting as a tap.
            .playPressed(pressSource)
            .combinedClickable(
                interactionSource = pressSource,
                indication = LocalIndication.current,
                onClick = onOpen,
                onLongClick = onCatalogue,
                onLongClickLabel = stringResource(R.string.dock_catalogue_hold),
            )
            // A screen reader cannot swipe a card, so the same two moves are named actions. They are
            // only offered when there is somewhere to go, so an inert action is never announced.
            .semantics {
                if (total > 1) {
                    customActions = listOf(
                        CustomAccessibilityAction(nextLabel) {
                            onStep(1)
                            true
                        },
                        CustomAccessibilityAction(previousLabel) {
                            onStep(-1)
                            true
                        },
                    )
                }
            },
    ) {
        Box(Modifier.fillMaxWidth()) {
            // The transfer is the card's own background now: the wash grows from the leading edge to
            // the fraction, and the name, the sentence and the controls all sit on top of it.
            DockProgressLayer(
                animation = dockAnimation,
                fraction = fraction,
                state = item.state,
                done = ready,
                phase = phase,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                // The chrome either side of the text is kept tight on purpose: the panel is as wide
                // as the bar under it (a pill hugging five labels, not the page), and every dp the
                // thumbnail, the button and the close mark give up is a dp the app's name keeps.
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    // The gesture belongs to the app's own face, not to the whole card. Pause,
                    // Install and the close mark keep their taps, and a swipe that starts on one of
                    // them never turns the card; only the icon and the two lines do.
                    Modifier
                        .weight(1f)
                        .pointerInput(position, total) {
                            if (total < 2) return@pointerInput
                            val tracker = VelocityTracker()
                            var travelled = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    travelled = 0f
                                    dragX = 0f
                                    dragging = true
                                    tracker.resetTracking()
                                },
                                onDragCancel = {
                                    dragging = false
                                    dragX = 0f
                                },
                                onDragEnd = {
                                    val step = DockSwipe.step(
                                        position = position,
                                        count = total,
                                        dx = travelled,
                                        velocityX = tracker.calculateVelocity().x,
                                        widthPx = cardWidth,
                                    )
                                    dragging = false
                                    dragX = 0f
                                    if (step != 0) onStep(step)
                                },
                                onHorizontalDrag = { change, amount ->
                                    change.consume()
                                    travelled += amount
                                    // The ends resist instead of refusing, the same language the tab
                                    // swipe uses: the card still moves a little, which is what says
                                    // "nothing there".
                                    val atEdge = (position == 0 && amount > 0) ||
                                        (position == total - 1 && amount < 0)
                                    val limit = cardWidth * 0.35f
                                    dragX = (dragX + amount * if (atEdge) 0.18f else 1f)
                                        .coerceIn(-limit, limit)
                                },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DockThumb(item = item, done = ready)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.title,
                                style = FloatingBar.legibleStyle(MaterialTheme.typography.titleSmall, fontScale),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (total > 1) {
                                Spacer(Modifier.width(6.dp))
                                MoreBadge(total - 1)
                            }
                        }
                        // The sentence swaps rather than being repainted: "Downloading" becoming
                        // "Ready to install" is the engine finishing something, and it should look like it.
                        AnimatedContent(
                            // Keyed on the *state*, never on the sentence. The sentence carries a
                            // percentage that moves several times a second, and a crossfade restarted on
                            // every tick leaves the line permanently half-faded — measured on the handset
                            // as text that looked washed out for the whole download.
                            targetState = item.state,
                            transitionSpec = {
                                ContentTransform(
                                    targetContentEnter = fadeIn(PlayMotion.spec(PlayMotion.SHORT, EmphasizedEasing)),
                                    initialContentExit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)),
                                    sizeTransform = SizeTransform(clip = false),
                                )
                            },
                            label = "dock-status",
                        ) { state ->
                            Text(
                                statusLine(item, state, shownPercent),
                                style = FloatingBar.legibleStyle(MaterialTheme.typography.bodySmall, fontScale),
                                color = if (ready) scheme.primary else scheme.onSurface.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                DockControlButton(
                    control = when {
                        ready && item.state == DownloadState.NEEDS_PERMISSION -> DockControl.ALLOW
                        ready && item.uninstallFirst -> DockControl.UNINSTALL
                        ready -> DockControl.INSTALL
                        active -> DockControl.PAUSE
                        else -> DockControl.RESUME
                    },
                    onToggle = onToggle,
                    onInstall = onInstall,
                )
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(28.dp),
                ) {
                    GlyphIcon(
                        Glyph.Close,
                        size = 14.dp,
                        tint = scheme.onSurfaceVariant,
                        label = stringResource(R.string.dock_dismiss),
                    )
                }
            }
        }
    }
}

/** The app's icon, rounded like every other thumb in the store. */
@Composable
private fun AppThumb(item: DownloadItem) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (item.iconUrl != null) {
            AsyncImage(
                model = item.iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GlyphIcon(
                Glyph.Download,
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A count of the other rows the panel is not showing.
 *
 * A number rather than a word: at a 1.3× text size "+2" fits where "+2 more" does not, and the label
 * already says which row this is.
 */
@Composable
private fun MoreBadge(more: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    ) {
        Text(
            stringResource(R.string.dock_more, more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** The app's thumbnail icon in the download dock: 40 dp with rounded corners. */
private val DockIconSize = 40.dp

/**
 * The app's thumbnail icon in the download dock, with a completion badge when ready to install.
 */
@Composable
private fun DockThumb(
    item: DownloadItem,
    done: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(DockIconSize)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
        ) {
            AppThumb(item)
        }
        AnimatedVisibility(
            visible = done,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = fadeIn(PlayMotion.spec(PlayMotion.SHORT, EmphasizedEasing)) +
                scaleIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing), 0.4f),
            exit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                scaleOut(PlayMotion.spec(PlayMotion.SHORT, EmphasizedAccelerateEasing), 0.4f),
        ) {
            Surface(
                shape = CircleShape,
                color = scheme.primary,
                modifier = Modifier.size(16.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.Tick, size = 10.dp, tint = scheme.onPrimary)
                }
            }
        }
    }
}

/**
 * The card's background as the transfer's own meter: a wash that grows from the leading edge to the
 * fraction, with a hairline where the bytes currently are.
 *
 * The panel used to carry a strip under its row. A strip is a second object to read; painting the
 * card itself means the progress *is* the surface the name, the percentage and the Pause button
 * already sit on, and it costs no height at any text size. The wash is a tint rather than a solid, so
 * both lines keep the contrast they had on a plain card, and it is drawn behind the row rather than
 * over it — the data stays in front.
 */
/**
 * The width, in px, of the card's wash for a transfer at [fraction].
 *
 * Pulled out of the draw pass so the arithmetic that decides where a download has reached can be
 * checked without a screen: a fraction is not guaranteed to stay inside 0..1 (a server that lies
 * about its length can report past the total), and a NaN must draw nothing rather than carry into
 * the canvas.
 */
internal fun dockFillWidth(width: Float, fraction: Float): Float {
    if (width <= 0f || fraction.isNaN()) return 0f
    return width * fraction.coerceIn(0f, 1f)
}

/**
 * The alphas that paint the card's meter, per row state.
 *
 * [track] is never zero: the unfilled part of the card has to be a surface, or the boundary between
 * "downloaded" and "not yet" disappears at a glance — which is the whole job of the fill. [muted]
 * says the transfer is stopped rather than moving, and [solid] says it is finished.
 */
internal data class DockFillTone(
    val start: Float,
    val end: Float,
    val track: Float,
    val muted: Boolean,
    val solid: Boolean,
)

/** The tone for [state]; [done] wins, because three states share the "file is ready" look. */
internal fun dockFillTone(state: DownloadState, done: Boolean): DockFillTone = when {
    done -> DockFillTone(start = 0.34f, end = 0.34f, track = 0.38f, muted = false, solid = true)
    state == DownloadState.DOWNLOADING || state == DownloadState.VERIFYING ->
        DockFillTone(start = 0.28f, end = 0.48f, track = 0.38f, muted = false, solid = false)
    // Paused, queued, or a row that has not started: the bytes that landed stay drawn, but in a grey
    // that says "stopped" — a paused card still wearing the moving gradient reads as a download that
    // froze, which is exactly what it is not.
    else -> DockFillTone(start = 0.16f, end = 0.20f, track = 0.38f, muted = true, solid = true)
}

/** Pause, resume, install or allow — one slot, one control, morphed in place. */
@Composable
private fun DockControlButton(control: DockControl, onToggle: () -> Unit, onInstall: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = control,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(PlayMotion.spec(PlayMotion.SHORT, EmphasizedEasing)) +
                    scaleIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing), 0.7f),
                initialContentExit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                    scaleOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing), 0.7f),
                sizeTransform = SizeTransform(clip = false),
            )
        },
        label = "dock-control",
    ) { current ->
        when (current) {
            DockControl.PAUSE, DockControl.RESUME -> Surface(
                shape = CircleShape,
                color = scheme.primaryContainer,
                modifier = Modifier.size(42.dp).clip(CircleShape).playClickable(onClick = onToggle),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlyphIcon(
                        if (current == DockControl.PAUSE) Glyph.Pause else Glyph.Play,
                        size = 18.dp,
                        tint = scheme.onPrimaryContainer,
                        filled = current == DockControl.RESUME,
                        label = stringResource(
                            if (current == DockControl.PAUSE) R.string.a11y_pause else R.string.a11y_resume,
                        ),
                    )
                }
            }

            DockControl.INSTALL, DockControl.UNINSTALL, DockControl.ALLOW -> Button(
                onClick = onInstall,
                // 12 dp rather than Material's 24: this button shares a 300-odd dp row with an icon,
                // a name and a close mark, and the label is one short word.
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    stringResource(
                        when (current) {
                            DockControl.ALLOW -> R.string.action_allow
                            DockControl.UNINSTALL -> R.string.action_uninstall
                            else -> R.string.action_install
                        },
                    ),
                )
            }
        }
    }
}

/** The meter's travelling phase, 0..1 over 1600 ms, shared by the dock and the settings preview. */
@Composable
internal fun rememberDockPhase(): Float {
    if (!PlayMotion.enabled) return 0f
    val transition = rememberInfiniteTransition(label = "dock-phase")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dock-phase-value",
    ).value
}

/** The one line under the title: enough to see whether it is moving, not a second stats page. */
@Composable
private fun statusLine(item: DownloadItem, state: DownloadState, percent: Int): String = when (state) {
    DownloadState.DOWNLOADING -> stringResource(
        R.string.dock_downloading,
        percent,
        TransferRate.formatSpeed(item.bytesPerSecond),
        item.etaSeconds?.let { TransferRate.formatEta(it) }
            ?: DownloadEngine.humanSize((item.totalBytes - item.bytesDownloaded).coerceAtLeast(0L)),
    )
    DownloadState.QUEUED -> stringResource(R.string.dock_queued)
    DownloadState.VERIFYING -> stringResource(R.string.dock_verifying)
    DownloadState.PAUSED -> stringResource(R.string.dock_paused, percent)
    DownloadState.NEEDS_PERMISSION -> stringResource(R.string.dock_needs_permission)
    DownloadState.AWAITING_CONFIRMATION -> stringResource(R.string.dock_awaiting)
    // No file size here, deliberately: the panel shares the bar's width, and the size pushed the
    // sentence past the row so it read "Downloaded · 336…". The action button already says what the
    // state means, and the size is in the download's details sheet.
    DownloadState.READY -> stringResource(
        when {
            item.archive -> R.string.dl_state_saved
            item.uninstallFirst -> R.string.row_needs_uninstall
            else -> R.string.dl_state_ready
        },
    )
    DownloadState.INSTALLING -> stringResource(R.string.dl_state_installing)
    DownloadState.INSTALLED -> stringResource(R.string.dl_state_installed)
    DownloadState.FAILED -> stringResource(R.string.dl_state_failed)
    DownloadState.CANCELED -> stringResource(R.string.dl_state_canceled)
}

/**
 * The list behind a long press on the dock: every row the card can show, in its own order.
 *
 * The panel is a single card and a queue can be longer than the eye can swipe through without
 * knowing what is there. This is the index for it: the same [dockCandidates] the swipe walks, each
 * row carrying the sentence it would carry in the queue, and a tap opens that download's page.
 *
 * Drawn as a surface above the panel, at the panel's own width, rather than as a modal sheet: it is
 * the bar's own menu, so it grows out of the bar instead of covering the page like another window.
 */
@Composable
internal fun DownloadCatalogue(
    items: List<DownloadItem>,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dock_catalogue_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.dock_catalogue_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(36.dp),
                ) {
                    GlyphIcon(
                        Glyph.Close,
                        size = 14.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        label = stringResource(R.string.action_close),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // A queue is short enough to lay out by hand, and a plain scrolling Column wraps its
            // content: a LazyColumn would claim the full cap for two rows. The cap keeps the menu
            // below the top of the page at any text size.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEach { item ->
                    CatalogueRow(item = item, onClick = { onOpen(item.id) })
                }
            }
        }
    }
}

/** One line of the catalogue: the face, the name, and what the queue row would say about it. */
@Composable
private fun CatalogueRow(item: DownloadItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .playClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(url = item.iconUrl, title = item.title, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                statusText(downloadRowStatus(item)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        GlyphIcon(
            Glyph.ChevronRight,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
