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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import coil3.compose.AsyncImage
import dev.aurastore.store.R
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.download.TransferRate
import dev.aurastore.store.theme.EmphasizedAccelerateEasing
import dev.aurastore.store.theme.EmphasizedDecelerateEasing
import dev.aurastore.store.theme.EmphasizedEasing
import dev.aurastore.store.ui.FloatingBar
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardAccelerateEasing
import dev.aurastore.store.theme.StandardEasing
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.playClickable

/**
 * Which row the dock should follow, or null when there is nothing worth a floating panel.
 *
 * One row, in the order a person would care about it: the transfer actually moving, then one waiting
 * its turn, then one the user paused, then a finished file whose install is the next thing to do. The
 * newest row wins inside a group because the queue is appended to, and a fresh request is the one the
 * user just made. A finished (installed), cancelled or failed row is deliberately not a candidate: the
 * dock is a control panel, not a second queue, and those rows already say what they are in the list.
 */
internal fun dockCandidate(items: List<DownloadItem>): DownloadItem? {
    val newest = items.asReversed()
    fun pick(vararg states: DownloadState) = newest.firstOrNull { it.state in states }
    return pick(DownloadState.DOWNLOADING, DownloadState.VERIFYING)
        ?: pick(DownloadState.QUEUED)
        ?: pick(DownloadState.PAUSED)
        ?: pick(DownloadState.READY, DownloadState.NEEDS_PERMISSION, DownloadState.AWAITING_CONFIRMATION)
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
    more: Int,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** True when the navigation bar sits directly under this card: the seam is a shared edge. */
    seam: Boolean = false,
) {
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
        // Linear and short, so the ring *chases* the bytes: a standard ease restarted by every
        // progress tick never finishes easing and the arc visibly lags, which reads as a stuck
        // download. A linear tween re-targets from wherever the arc is, which is what Play's circular
        // indicator does.
        animationSpec = tween(durationMillis = 360, easing = LinearEasing),
        label = "dock-progress",
    )
    // The percentage counts rather than jumping: a percentage is the number the eye watches, and a
    // number that teleports reads as a screen that was not looking.
    val shownPercent by animateIntAsState(
        targetValue = percent,
        animationSpec = PlayMotion.spec(PlayMotion.MEDIUM, StandardEasing),
        label = "dock-percent",
    )
    // One travelling highlight shared by both drawings. A paused, queued or finished row is still: a
    // panel that keeps moving after the bytes stop is claiming work that is not happening.
    val shine = if (moving) rememberDockShine() else 0f
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
        modifier = modifier.fillMaxWidth().clip(dockShape).playClickable(onClick = onOpen),
    ) {
        Box(Modifier.fillMaxWidth()) {
            // The transfer is the card's own background now: the wash grows from the leading edge to
            // the fraction, and the name, the sentence and the controls all sit on top of it.
            ProgressWash(
                fraction = fraction,
                state = item.state,
                done = ready,
                shine = shine,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                // The chrome either side of the text is kept tight on purpose: the panel is as wide
                // as the bar under it (a pill hugging five labels, not the page), and every dp the
                // thumbnail, the button and the close mark give up is a dp the app's name keeps.
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockThumb(item = item, done = ready)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.title,
                            style = FloatingBar.labelStyle(MaterialTheme.typography.titleSmall, fontScale),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (more > 0) {
                            Spacer(Modifier.width(6.dp))
                            MoreBadge(more)
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
                            style = FloatingBar.labelStyle(MaterialTheme.typography.bodySmall, fontScale),
                            color = if (ready) scheme.primary else scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
        DockFillTone(start = 0.22f, end = 0.40f, track = 0.38f, muted = false, solid = false)
    // Paused, queued, or a row that has not started: the bytes that landed stay drawn, but in a grey
    // that says "stopped" — a paused card still wearing the moving gradient reads as a download that
    // froze, which is exactly what it is not.
    else -> DockFillTone(start = 0.16f, end = 0.20f, track = 0.38f, muted = true, solid = true)
}

@Composable
private fun ProgressWash(
    fraction: Float,
    state: DownloadState,
    done: Boolean,
    shine: Float,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tone = remember(state, done) { dockFillTone(state, done) }
    val moving = state == DownloadState.DOWNLOADING || state == DownloadState.VERIFYING
    // Both gradients are pictures, not per-frame state: the wash is two colours across the card and
    // the shine is one white band. The band moves by translating the canvas, not by rebuilding the
    // gradient with a new start and end sixty times a second.
    // Strength is deliberate: a 12 % tint over a dark card read as "slightly coloured card", not as
    // a meter. These alphas still leave both lines past 4.5:1 against the wash, but the filled region
    // is now unmistakably a region — which is the whole point of painting the card instead of a strip.
    val wash = remember(tone, scheme.primary, scheme.tertiary, scheme.onSurfaceVariant) {
        Brush.horizontalGradient(
            when {
                tone.muted -> listOf(
                    scheme.onSurfaceVariant.copy(alpha = tone.start),
                    scheme.onSurfaceVariant.copy(alpha = tone.end),
                )
                tone.solid -> listOf(
                    scheme.primary.copy(alpha = tone.start),
                    scheme.primary.copy(alpha = tone.end),
                )
                else -> listOf(
                    scheme.primary.copy(alpha = tone.start),
                    scheme.tertiary.copy(alpha = tone.end),
                )
            },
        )
    }
    // The part of the card the bytes have not reached yet. Without it a 30 % download is a card with
    // a coloured left third and no visible end to the fill — the track is what makes the boundary
    // legible at a glance from across the room.
    val track = scheme.surfaceVariant.copy(alpha = tone.track)
    val sheen = remember {
        Brush.horizontalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
        )
    }
    Canvas(modifier) {
        val filled = dockFillWidth(size.width, fraction)
        // Always laid down, even at 0 %: a queued row still shows where the fill will arrive.
        drawRect(color = track, size = size)
        if (filled <= 0f) return@Canvas
        // Laid down at the card's full width and clipped: a brush handed a new size on every progress
        // tick rebuilds its shader, and this one is repainted sixty times a second. Clipping the whole
        // card's width rather than a width-sized rect also keeps the gradient's colour at the edge
        // constant instead of re-stretching it on every tick.
        clipRect(right = filled) {
            drawRect(brush = wash, size = size)
        }
        if (moving && PlayMotion.enabled) {
            clipRect(right = filled) {
                val band = size.width * 0.35f
                val start = (size.width + band) * (shine / 360f) - band
                translate(left = start) {
                    drawRect(brush = sheen, size = Size(band, size.height))
                }
            }
        }
        // One hairline at the byte the transfer has reached: a 12 % wash on its own is a tinted card,
        // and the edge is what makes it read as a position rather than as a colour.
        // Embers climbing the fill while bytes move: three sparks riding the same phase as the
        // ring's shine, so the card and the icon look lit by one source rather than two effects.
        if (moving && PlayMotion.enabled) {
            val spark = 1.7.dp.toPx()
            for (i in 0 until 3) {
                val phase = ((shine / 360f) + i / 3f) % 1f
                drawCircle(
                    color = scheme.tertiary.copy(alpha = (1f - phase) * 0.55f),
                    radius = spark * (0.55f + phase * 0.75f),
                    center = Offset(
                        x = filled - 5.dp.toPx() - phase * 7.dp.toPx(),
                        y = size.height * (1f - phase),
                    ),
                )
            }
        }
        val edge = 1.5.dp.toPx()
        drawRect(
            color = if (tone.solid) scheme.primary else if (tone.muted) scheme.outline else scheme.tertiary,
            topLeft = Offset(filled - edge / 2f, 0f),
            size = Size(edge, size.height),
        )
    }
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

/** The highlight's angle, in degrees, shared by the ring and the bar. */
@Composable
private fun rememberDockShine(): Float {
    if (!PlayMotion.enabled) return 0f
    val transition = rememberInfiniteTransition(label = "dock-shine")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dock-shine-angle",
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
        if (item.uninstallFirst) R.string.row_needs_uninstall else R.string.dl_state_ready,
    )
    DownloadState.INSTALLING -> stringResource(R.string.dl_state_installing)
    DownloadState.INSTALLED -> stringResource(R.string.dl_state_installed)
    DownloadState.FAILED -> stringResource(R.string.dl_state_failed)
    DownloadState.CANCELED -> stringResource(R.string.dl_state_canceled)
}
