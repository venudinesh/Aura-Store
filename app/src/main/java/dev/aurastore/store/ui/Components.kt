package dev.aurastore.store.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.aurastore.store.R
import dev.aurastore.store.data.Api
import dev.aurastore.store.data.PostSummary
import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.theme.ArtShape
import dev.aurastore.store.theme.SplitActionShape
import dev.aurastore.store.theme.SplitChooserShape
import dev.aurastore.store.theme.EmphasizedAccelerateEasing
import dev.aurastore.store.theme.EmphasizedDecelerateEasing
import dev.aurastore.store.theme.EmphasizedEasing
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardAccelerateEasing
import dev.aurastore.store.theme.StandardEasing

/**
 * The storefront's building blocks.
 *
 * Shapes and rhythm follow the Play Store: 16dp card radius, 12dp icon radius, pill-shaped actions,
 * one-line grey meta under a medium-weight title, and thin `outlineVariant` borders instead of
 * shadows.
 */

private val CardShape = RoundedCornerShape(16.dp)
private val IconShape = ArtShape

/** Where a row's action stands, resolved from the download engine and the install bookkeeping. */
enum class ActionKind { INSTALL, UPDATE, OPEN, QUEUED, DOWNLOADING, VERIFYING, PAUSED, PERMISSION, FAILED }

@Immutable
data class RowAction(
    val kind: ActionKind,
    val progress: Float = 0f,
    val label: String? = null,
)

// --------------------------------------------------------------------------------------- chips

/** Play's filter chip: filled tonal when selected, hairline outlined when not. */
@Composable
fun PlayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expandable: Boolean = false,
    /** A secondary number after the label, as a search site's tab counts its matches. */
    count: Int? = null,
) {
    // A chip's fill arrives and leaves on the same short spline, which is what makes a row of them
    // read as one control being moved rather than a row of buttons being pressed.
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = PlayMotion.spec(PlayMotion.SHORT, StandardEasing),
        label = "chip-container",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = PlayMotion.spec(PlayMotion.SHORT, StandardEasing),
        label = "chip-content",
    )
    // The hairline fades out instead of switching off, so a selected chip does not step sideways.
    val outline by animateColorAsState(
        targetValue = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = PlayMotion.spec(PlayMotion.SHORT, StandardEasing),
        label = "chip-outline",
    )
    Surface(
        modifier = modifier.heightIn(min = 34.dp).clip(CircleShape).playClickable(onClick = onClick),
        color = container,
        shape = CircleShape,
        border = BorderStroke(1.dp, outline),
    ) {
        Row(
            Modifier
                .padding(horizontal = 14.dp, vertical = 7.dp)
                // The check is part of the chip's width, so removing it must not step the pill narrower
                // in one frame. The size settles while the fill and the ink cross-fade, which is what
                // makes selecting a filter read as one control changing rather than two.
                .animateContentSize(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(PlayMotion.spec(PlayMotion.SHORT, EmphasizedEasing)) +
                    scaleIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing), 0.4f),
                exit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                    scaleOut(PlayMotion.spec(PlayMotion.SHORT, EmphasizedAccelerateEasing), 0.4f),
            ) {
                GlyphIcon(Glyph.Check, size = 16.dp, tint = content)
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
            if (count != null) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = content.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
            if (expandable) GlyphIcon(Glyph.ChevronRight, size = 16.dp, tint = content)
        }
    }
}

/** The small grey pills Play puts on rows: size, rating, "Event", "Major update". */
@Composable
fun PlayBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * The site an app came from, as a small tonal pill.
 *
 * A search that mixes sites puts each site's rows under its own header, but a header scrolls away
 * while its rows stay on screen. The pill is the header that travels with the row: it sits beside
 * the size, because the two answer the same question about the file at a glance. Tonal rather than
 * outlined so it does not read as a second size badge.
 */
@Composable
fun SourcePill(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// ------------------------------------------------------------------------------ install action

/**
 * Play's install control: a tonal pill, optionally split into the action plus a chooser segment.
 */
@Composable
fun InstallButton(
    action: RowAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onChooser: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    // A row-sized control hugs its label; the detail page's control fills the width.
    val mainShape = if (onChooser == null) {
        CircleShape
    } else {
        SplitActionShape
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = mainShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .height(if (compact) 38.dp else 46.dp)
                .then(if (compact) Modifier.widthIn(min = 92.dp) else Modifier.weight(1f))
                .clip(mainShape)
                .playClickable(onClick = onClick),
        ) {
            // What the control *says* is the state here, so that is what morphs: Install becomes a
            // quarter-filled ring and then Open, and the pill grows and settles through each of them
            // instead of stepping between two widths. The percentage is not in the key, because a
            // transition restarting sixty times a second is not an animation.
            AnimatedContent(
                targetState = action.kind to action.label,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(PlayMotion.spec(PlayMotion.SHORT, EmphasizedEasing)) +
                            scaleIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing), 0.92f),
                        initialContentExit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                            scaleOut(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedAccelerateEasing), 0.92f),
                        sizeTransform = SizeTransform(clip = false),
                    )
                },
                label = "install-action",
            ) { (kind, word) ->
                Row(
                    Modifier.padding(horizontal = if (compact) 16.dp else 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (kind) {
                        ActionKind.DOWNLOADING, ActionKind.QUEUED, ActionKind.VERIFYING, ActionKind.PAUSED -> {
                            CircularProgressIndicator(
                                progress = { action.progress },
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                word ?: when (kind) {
                                    ActionKind.VERIFYING -> stringResource(R.string.state_verifying)
                                    ActionKind.PAUSED -> stringResource(R.string.state_paused)
                                    ActionKind.QUEUED -> stringResource(R.string.state_queued)
                                    ActionKind.DOWNLOADING -> stringResource(R.string.percent_int, (action.progress * 100).toInt())
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        else -> Text(
                            word ?: when (kind) {
                                ActionKind.INSTALL -> stringResource(R.string.action_install)
                                ActionKind.UPDATE -> stringResource(R.string.action_update)
                                ActionKind.OPEN -> stringResource(R.string.action_open)
                                ActionKind.PERMISSION -> stringResource(R.string.action_allow)
                                ActionKind.FAILED -> stringResource(R.string.action_retry)
                                // Covered by the branch above; listed so the compiler sees an exhaustive when.
                                ActionKind.DOWNLOADING, ActionKind.QUEUED, ActionKind.VERIFYING,
                                ActionKind.PAUSED -> stringResource(R.string.action_install)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
        if (onChooser != null) {
            // The two halves are one control: Play draws the chevron in the same fill as the word and
            // separates them with a hairline. A tonal second half made the smaller target look like the
            // heavier one on every palette where `primaryContainer` is the saturated colour.
            Spacer(Modifier.width(2.dp))
            Surface(
                shape = SplitChooserShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .height(46.dp)
                    .width(52.dp)
                    .clip(SplitChooserShape)
                    .playClickable(onClick = onChooser),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlyphIcon(
                        Glyph.ChevronRight,
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        // The one control on the page that changes *which* build gets fetched, so it has
                        // to be sayable: a chevron with no word beside it is nothing to TalkBack and
                        // nothing for a harness to press.
                        label = stringResource(R.string.chooser_build_title),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- app pieces

/** Play's list row: 64dp icon, title, meta line, badges, trailing action. */
@Composable
fun AppRow(
    post: PostSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    action: RowAction? = null,
    onAction: (() -> Unit)? = null,
    onChooser: (() -> Unit)? = null,
    /** The site the row came from, when the list mixes sites. Null leaves the pill off. */
    sourceLabel: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .playClickable(onClick = onClick, pressedScale = 0.99f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // A walk now reaches four-digit ranks, and a column fixed at the width of a two-digit one
                // printed 1000 as 10 over 00. The floor keeps short ranks from crowding the icon; the
                // number gets the room its own digits need rather than the room rank 7 needed.
                maxLines = 1,
                modifier = Modifier.widthIn(min = 28.dp),
            )
        }
        PostIcon(post, size = 64.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                post.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                metaLine(post),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (post.ratingAvg > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Star, size = 12.dp, tint = dev.aurastore.store.theme.RatingStar)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            formatScore(post.ratingAvg),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                formatSize(post.size)?.let { PlayBadge(it) }
                sourceLabel?.let { SourcePill(it) }
            }
        }
        if (action != null) {
            Spacer(Modifier.width(10.dp))
            InstallButton(action, onClick = { onAction?.invoke() }, compact = true, onChooser = onChooser)
        }
    }
}

/** Play's rail card: icon, one or two title lines, rating and size underneath. */
@Composable
fun AppCard(
    post: PostSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
) {
    Column(modifier.width(width).playClickable(onClick = onClick, pressedScale = 0.98f)) {
        Box(
            Modifier
                .size(72.dp)
                .clip(IconShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            PostIcon(post, size = 72.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            post.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (post.ratingAvg > 0) {
                GlyphIcon(Glyph.Star, size = 12.dp, tint = dev.aurastore.store.theme.RatingStar)
                Spacer(Modifier.width(3.dp))
                Text(
                    formatScore(post.ratingAvg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            formatSize(post.size)?.let {
                if (post.ratingAvg > 0) Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Play's hero carousel card: full-bleed image with an overlaid title and an optional badge. */
@Composable
fun HeroCard(
    imageUrl: String?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .playClickable(onClick = onClick, pressedScale = 0.99f),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
        )
        if (badge != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.padding(12.dp).align(Alignment.TopStart),
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Play's phone-screenshot card: 9:19.5, 12dp radius, hairline outline. */
@Composable
fun ScreenshotCard(
    url: String,
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    portrait: Boolean = true,
) {
    Box(
        modifier
            .width(width)
            // Phone screenshots get Play's tall card; a feature graphic used in their place keeps
            // its own landscape shape instead of being cropped into a portrait slice.
            .aspectRatio(if (portrait) 9f / 19.5f else 16f / 9f)
            .clip(IconShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, IconShape),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** App icon with a placeholder fill, so an empty URL still reads as a card. */
@Composable
fun PostIcon(post: PostSummary, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(IconShape)
            // Every row in the storefront draws its icon through here, so this one line is what makes
            // the icon of the card you tapped fly into the header of the page it opens.
            .sharedIcon(post.id)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = post.imageUrl,
            contentDescription = post.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ------------------------------------------------------------------------------------ sections

/**
 * Play's section header, with the round chevron button it puts on the right.
 *
 * A chevron only ever means "there is more to look at", so a trailing control that *does* something —
 * clearing rows away, say — passes a [moreLabel] instead and gets a word that says what it is.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onMore: (() -> Unit)? = null,
    moreLabel: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onMore != null && moreLabel != null) {
            TextButton(onClick = onMore) { Text(moreLabel, style = MaterialTheme.typography.labelLarge) }
        } else if (onMore != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(36.dp).clip(CircleShape).playClickable(onClick = onMore),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlyphIcon(
                        Glyph.ChevronRight,
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        label = stringResource(R.string.a11y_see_more),
                    )
                }
            }
        }
    }
}

/**
 * Play's bordered info block — the shape it uses for the Data safety card, reused here for the
 * download verification facts so the section is legible at a glance.
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = CardShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

@Composable
fun InfoRow(
    glyph: Glyph,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        GlyphIcon(glyph, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Play's editorial row: a small mark, two lines of copy, and a text action. */
@Composable
fun NoticeCard(
    emblem: String,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(emblem, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!body.isNullOrBlank()) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Play's "Ratings and reviews" block: a huge average beside a star row and the review count. */
@Composable
fun RatingBlock(
    average: Double,
    count: Int,
    note: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(112.dp)) {
            Text(
                if (average > 0) formatScore(average) else "—",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp),
                fontWeight = FontWeight.Normal,
            )
            RatingStars(average, size = 13.dp)
            Text(
                formatCount(count.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            if (!note.isNullOrBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A row's status line before it is words: a string resource and its arguments, or the sentence the engine
 * wrote itself.
 *
 * The rule in [downloadRowStatus] has to stay a plain function — it is the one place the engine's state
 * becomes a claim the user reads, and `ScreenMotionTest` pins the claims. The words have to come from
 * `strings.xml` like every other sentence on the screen. A resource id plus its arguments is the shape that
 * satisfies both: the JVM test compares ids, and the composable resolves them.
 *
 * [text] is the exception, not the fallback: a failed row prints the engine's own reason, which is stored
 * with the row in `aurastore_queue.xml` and therefore stays in the language the app wrote it in.
 */
data class StatusLine(
    @StringRes val id: Int? = null,
    val args: List<Any> = emptyList(),
    val text: String? = null,
)

@Composable
internal fun statusText(line: StatusLine): String = when {
    line.id != null -> stringResource(line.id, *line.args.toTypedArray())
    else -> line.text.orEmpty()
}

/** Which of the three sentences the bottom of a paged list is allowed to say. */
internal enum class FooterArm { Failed, Loading, End, None }

/**
 * The footer's choice, taken out of the composable so its order is a fact a test can hold.
 *
 * A failure wins because it is the only arm with a button in it, and the state sets `moreError` a beat
 * before it drops `loadingMore` — a spinner printed over *try again* hides the one control the reader has
 * left. A page in flight outranks the end because [hasMore] is the previous page's opinion, not this one's.
 */
internal fun footerArm(loading: Boolean, hasMore: Boolean, error: StatusLine?): FooterArm = when {
    error != null -> FooterArm.Failed
    loading -> FooterArm.Loading
    !hasMore -> FooterArm.End
    else -> FooterArm.None
}

/**
 * The sentence under a failed page: the one that came with the failure, or [fallback] when it came with
 * nothing but space. An exception with no message is common enough, and a blank line beside *try again*
 * explains nothing about why the page did not arrive.
 */
internal fun footerFailureText(sentence: String, fallback: String): String = sentence.ifBlank { fallback }

/**
 * The sentence for the end of a walk. The two ends are not the same event: a list that ran out is finished,
 * and a walk that stopped at the page the route will not answer is missing rows the reader has no way to
 * reach otherwise, so the second one has to say so rather than borrow the first one's confidence.
 */
internal fun footerEndText(capped: Boolean, everything: String, noFurther: String): String =
    if (capped) noFurther else everything

/**
 * The line at the bottom of a paged list, for the three things that can be true about its next page.
 *
 * One slot rather than three widgets, so a list never ends in a spinner that a later page turns into a
 * sentence, and never shows *try again* next to the sentence that says there is nothing left to load.
 * [name] is what the list is called — the tab, the category — for the line that says it is finished, and
 * [capped] is which of the two kinds of finished that line is allowed to claim.
 */
@Composable
fun PagingFooter(
    loading: Boolean,
    hasMore: Boolean,
    capped: Boolean,
    error: StatusLine?,
    name: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arm = footerArm(loading, hasMore, error)
    val failure = error
    when {
        // The same three arguments decided both, so a Failed arm always has its sentence to show.
        arm == FooterArm.Failed && failure != null -> Row(
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // The state carries the server's sentence when it gave one; this owns the words for the
                // case where it gave nothing.
                footerFailureText(statusText(failure), stringResource(R.string.list_load_more_failed)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.try_again))
            }
        }

        arm == FooterArm.Loading -> Row(
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.list_finding),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        arm == FooterArm.End -> Text(
            footerEndText(
                capped,
                stringResource(R.string.list_end, name),
                // The number is the point: without it, "has more than this" and "that's everything" read
                // the same to anyone who has not counted, and the reader cannot tell a finished list from
                // one the route refused to page through.
                stringResource(R.string.list_end_capped, name, Api.LIST_REACH),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // The horizontal inset the other two arms already carry: the capped sentence is long enough to
            // reach both edges without it, which is the one thing a centred line should never do.
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        )
    }
}

/**
 * The line a queue row prints under its title.
 *
 * Pulled out of the row because it is the one place the engine's state becomes a sentence the user
 * reads — including the two lies it must not tell: a build the platform will refuse is not "ready to
 * install", and a data file is not a package.
 */
internal fun downloadRowStatus(item: DownloadItem): StatusLine = when (item.state) {
    DownloadState.DOWNLOADING, DownloadState.QUEUED -> StatusLine(
        R.string.row_progress_bytes,
        listOf(
            humanSize(item.bytesDownloaded),
            if (item.totalBytes > 0) humanSize(item.totalBytes) else "—",
        ),
    )
    DownloadState.VERIFYING -> when {
        // A bundle's wait is longer than a parse: the set comes out of the archive before it is read.
        item.dataFile -> StatusLine(R.string.row_reading_archive)
        item.isBundle -> StatusLine(R.string.row_unpacking)
        else -> StatusLine(R.string.row_verifying)
    }
    DownloadState.READY -> when {
        item.dataFile -> StatusLine(R.string.row_ready_to_add)
        // The file is fine and the platform is the obstacle: saying "Ready to install" beside a build
        // that cannot go in would be a lie about the row.
        item.uninstallFirst -> StatusLine(R.string.row_needs_uninstall)
        else -> StatusLine(R.string.dl_state_ready)
    }
    DownloadState.INSTALLING -> StatusLine(R.string.row_installing)
    DownloadState.AWAITING_CONFIRMATION -> StatusLine(R.string.row_awaiting_confirm)
    DownloadState.INSTALLED -> StatusLine(
        if (item.dataFile) R.string.dl_state_data_placed else R.string.dl_state_installed,
    )
    DownloadState.NEEDS_PERMISSION -> StatusLine(R.string.row_needs_permission)
    DownloadState.PAUSED -> StatusLine(R.string.row_paused_bytes, listOf(humanSize(item.bytesDownloaded)))
    DownloadState.FAILED -> item.error?.let { StatusLine(text = it) } ?: StatusLine(R.string.dl_state_failed)
    DownloadState.CANCELED -> StatusLine(R.string.dl_state_canceled)
}

/** Play's queue row: icon, title, a thin progress bar and its controls. */
@Composable
fun DownloadRow(
    item: DownloadItem,
    onToggle: () -> Unit,
    onInstall: () -> Unit,
    onDetails: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
    onInfo: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onUninstallFirst: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(IconShape).background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = item.iconUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (item.dataFile) stringResource(R.string.dl_title_with_data, item.title) else item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The sentence swaps rather than being repainted: a row going from "Verifying package…"
                // to "Ready to install" is the engine finishing something, and it should look like that.
                AnimatedContent(
                    targetState = downloadRowStatus(item),
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = fadeIn(PlayMotion.spec(PlayMotion.SHORT, EmphasizedEasing)) +
                                slideInVertically(
                                    PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing),
                                ) { it / 3 },
                            initialContentExit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                                slideOutVertically(
                                    PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedAccelerateEasing),
                                ) { -it / 3 },
                            sizeTransform = SizeTransform(clip = false),
                        )
                    },
                    label = "download-status",
                ) { status ->
                    Text(
                        statusText(status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // A host-side substitution is worth surfacing, so the row never silently changes
                // which build it is fetching.
                item.notice?.takeIf { item.state != DownloadState.FAILED }?.let { notice ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.state == DownloadState.DOWNLOADING && item.totalBytes > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when (item.state) {
                DownloadState.DOWNLOADING, DownloadState.QUEUED ->
                    SmallCircleAction(Glyph.Pause, stringResource(R.string.a11y_pause), onToggle)
                DownloadState.PAUSED, DownloadState.FAILED, DownloadState.CANCELED ->
                    SmallCircleAction(Glyph.Play, stringResource(R.string.a11y_resume), onToggle)
                DownloadState.READY, DownloadState.NEEDS_PERMISSION, DownloadState.AWAITING_CONFIRMATION ->
                    // Re-opening the sheet is the only thing that can still move an awaiting row, so the
                    // control stays a working Install rather than a dead spinner. Except for the one row
                    // that cannot be installed at all yet: its next step is a removal, and the button that
                    // opens the system's own uninstall sheet is the honest control to put there.
                    InstallButton(
                        RowAction(
                            if (item.state == DownloadState.NEEDS_PERMISSION) ActionKind.PERMISSION
                            else ActionKind.INSTALL,
                            label = when {
                                item.uninstallFirst -> stringResource(R.string.action_uninstall)
                                item.dataFile -> stringResource(R.string.action_add_data)
                                else -> null
                            },
                        ),
                        onClick = if (item.uninstallFirst) onUninstallFirst else onInstall,
                        compact = true,
                    )
                DownloadState.INSTALLED ->
                    // A placed data row stays actionable: re-adding it is how a game whose data went
                    // missing gets fixed without re-downloading a gigabyte.
                    InstallButton(
                        RowAction(
                            if (item.dataFile) ActionKind.INSTALL else ActionKind.OPEN,
                            label = if (item.dataFile) stringResource(R.string.action_open_again)
                            else stringResource(R.string.action_open),
                        ),
                        onClick = if (item.dataFile) onInstall else onDetails,
                        compact = true,
                    )
                else -> SmallCircleAction(Glyph.Close, stringResource(R.string.a11y_remove), onRemove)
            }
            // Fixed 44dp anchor: the install pill beside it must not steal the tap.
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                SmallCircleAction(Glyph.More, stringResource(R.string.a11y_more)) { menuOpen = true }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .widthIn(min = 180.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(20.dp),
                        )
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                ) {
                    DownloadMenuItemPill(
                        icon = Glyph.Info,
                        label = stringResource(R.string.row_menu_details),
                        onClick = {
                            menuOpen = false
                            onInfo()
                        },
                    )
                    DownloadMenuItemPill(
                        icon = Glyph.Copy,
                        label = stringResource(R.string.row_menu_copy_link),
                        onClick = {
                            menuOpen = false
                            onCopyLink()
                        },
                    )
                    if (item.sha256 != null) {
                        DownloadMenuItemPill(
                            icon = Glyph.Download,
                            label = stringResource(R.string.row_menu_save),
                            onClick = {
                                menuOpen = false
                                onSave()
                            },
                        )
                    }
                    DownloadMenuItemPill(
                        icon = Glyph.Close,
                        label = stringResource(R.string.a11y_remove),
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                    DownloadMenuItemPill(
                        icon = Glyph.Trash,
                        label = stringResource(R.string.row_menu_delete_device),
                        isDestructive = true,
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadMenuItemPill(
    icon: Glyph,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val tint = when {
        !enabled -> colorScheme.onSurface.copy(alpha = 0.38f)
        isDestructive -> colorScheme.error
        else -> colorScheme.onSurface
    }
    val container = when {
        !enabled -> Color.Transparent
        isDestructive -> colorScheme.errorContainer.copy(alpha = 0.2f)
        else -> colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    }
    val borderStroke = BorderStroke(
        1.dp,
        when {
            !enabled -> colorScheme.outlineVariant.copy(alpha = 0.25f)
            isDestructive -> colorScheme.error.copy(alpha = 0.45f)
            else -> colorScheme.outlineVariant.copy(alpha = 0.55f)
        },
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = container,
        border = borderStroke,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlyphIcon(icon, size = 18.dp, tint = tint)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/**
 * A menu row whose label alone does not say what it costs. Material 3's `DropdownMenuItem` has no
 * supporting-text slot, so the note is part of the label column rather than a second parameter —
 * and the two doors this is used for are one gesture apart, so the words have to do the separating.
 */
@Composable
internal fun MenuRowText(label: String, note: String) {
    Column {
        Text(label)
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SmallCircleAction(glyph: Glyph, label: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(40.dp).clip(CircleShape).playClickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = glyph,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(PlayMotion.spec(PlayMotion.SHORT, EmphasizedEasing)) +
                            scaleIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing), 0.7f),
                        initialContentExit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                            scaleOut(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedAccelerateEasing), 0.7f),
                        sizeTransform = SizeTransform(clip = false),
                    )
                },
                label = "circle-action",
            ) { icon ->
                // The word the caller already had is the only thing that makes this control findable:
                // a drawn path is invisible to TalkBack, to a `uiautomator` dump and therefore to any
                // regression test that wants to press it.
                GlyphIcon(icon, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant, label = label)
            }
        }
    }
}

// --------------------------------------------------------------------------------------- states

@Composable
fun StateMessage(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
        }
    }
}

/** Play's rail skeleton, in the same 120dp rhythm as [AppCard]. */
@Composable
fun LoadingRail(cards: Int = 4) {
    val pulse = skeletonPulse()
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(cards) {
            Column(
                Modifier.width(120.dp).graphicsLayer { alpha = pulse },
            ) {
                Box(
                    Modifier.size(72.dp).clip(IconShape).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth(0.8f).height(14.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth(0.5f).height(12.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

/**
 * Play's grey line under a row title: the categories and the collection the build belongs to.
 * Rating and size are pills, so they are not repeated here.
 */
@Composable
fun metaLine(post: PostSummary): String = remember(post.genre, post.type) {
    listOfNotNull(
        post.genre?.takeIf { it.isNotBlank() },
        post.type?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}.ifBlank { stringResource(R.string.meta_line_fallback) }

/** The unit table is a constant: a list literal here allocated one per call, per row. */
private val SIZE_UNITS = arrayOf("B", "KB", "MB", "GB")

fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < SIZE_UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${value.toInt()} ${SIZE_UNITS[unit]}" else "%.1f %s".format(value, SIZE_UNITS[unit])
}
