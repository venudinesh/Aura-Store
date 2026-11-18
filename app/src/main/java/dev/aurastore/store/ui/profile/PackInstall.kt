package dev.aurastore.store.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.data.PackInstaller
import dev.aurastore.store.data.PackLibrary
import dev.aurastore.store.data.SpecialAnime
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.playClickable

/**
 * Downloading deleted posters back from GitHub, straight from the catalogue.
 *
 * A deleted poster is not hidden: it stays in its slot, dimmed, so the catalogue reads as the same
 * catalogue with one picture faded out. The download mark on the picture fetches exactly that file;
 * the mark on the anime card fetches every deleted poster of that set; the one beside the pack's
 * title fetches everything that is faded. All three are [PackInstaller]'s work, so a transfer keeps
 * running when the sheet closes, and the picture sharpens under the finger as the file lands.
 */

/** The count on an anime card: how many of its posters are faded, or how far the download has come. */
@Composable
fun PackBadge(
    missing: Int,
    install: PackInstaller.Install?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (missing <= 0 && install == null) return
    val running = install?.running == true
    val failed = install != null && install.failed > 0 && !install.running
    val tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val label = stringResource(R.string.special_download_missing, missing)
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .semantics { contentDescription = label }
            .playClickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            CircularProgressIndicator(
                progress = { install?.fraction ?: 0f },
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = tint,
            )
        } else {
            GlyphIcon(
                if (failed) Glyph.Update else Glyph.Download,
                size = 12.dp,
                tint = tint,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            when {
                running -> "${install?.done}/${install?.total}"
                failed -> stringResource(R.string.special_download_retry)
                else -> "$missing"
            },
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

/**
 * A poster that was deleted: the same picture at a quarter strength, with the download mark on it.
 *
 * Dimming rather than a blank is the point. The slot says what is gone, so the deletion is never a
 * mystery, and the mark turns the same slot into the way back. While that one file is in flight the
 * mark is a ring, so a slow connection is visible rather than guessed at.
 */
@Composable
fun PackDeletedPoster(
    anime: SpecialAnime,
    poster: Int,
    label: String,
    install: PackInstaller.Install?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    size: Dp? = null,
    height: Dp = 66.dp,
    labelLines: Int = 1,
    artPx: Int = 320,
) {
    val description = stringResource(R.string.special_download_one, poster + 1)
    val busy = install != null && install.running && poster in install.active
    Column(
        modifier
            .semantics { contentDescription = description }
            .playClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .then(if (size != null) Modifier.size(size) else Modifier.fillMaxWidth().height(height))
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            PackPoster(
                anime = anime,
                poster = poster,
                targetPx = artPx,
                modifier = Modifier.fillMaxSize().alpha(0.24f),
            )
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        progress = { install?.fraction ?: 0f },
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    GlyphIcon(
                        Glyph.Download,
                        size = 14.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = labelLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The one download mark for the whole catalogue, sitting beside the pack's title.
 *
 * It counts what is faded and fetches all of it. Silent at zero, because a permanent "nothing is
 * missing" button is furniture; it appears with the first deletion and leaves when the last file
 * lands. A refusal keeps it on screen in the error colour with the retry on it.
 */
@Composable
fun PackDownloadAll(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val removed by PackLibrary.removed.collectAsStateWithLifecycle()
    val installs by PackInstaller.state.collectAsStateWithLifecycle()
    val running = installs.values.filter { it.running }
    val failed = installs.values.sumOf { if (it.running) 0 else it.failed }
    val moving = running.isNotEmpty()
    if (removed.isEmpty() && installs.isEmpty()) return
    val done = running.sumOf { it.done }
    val total = running.sumOf { it.total }
    val missing = removed.size
    val tint = if (failed > 0 && !moving) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val description = stringResource(R.string.special_download_missing, removed.size)
    Row(
        modifier
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .playClickable(
                onClick = { PackInstaller.installMissing(context, PackLibrary.removedGroups()) },
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (moving) {
            CircularProgressIndicator(
                progress = { if (total <= 0) 0f else done.toFloat() / total },
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = tint,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "$done/$total",
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        } else {
            GlyphIcon(
                if (failed > 0) Glyph.Update else Glyph.Download,
                size = 18.dp,
                tint = tint,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (failed > 0) {
                    stringResource(R.string.special_download_retry)
                } else {
                    "$missing"
                },
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}

/** Kept next to the badge so both read the same set: the anime a card stands for. */
fun missingOf(anime: SpecialAnime): Int = anime.posters.size - PackLibrary.visible(anime).size
