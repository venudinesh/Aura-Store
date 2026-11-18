package dev.aurastore.store.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.data.PackLibrary
import dev.aurastore.store.data.SpecialAnime
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.playClickable

/**
 * Taking posters off the shelf.
 *
 * A hold on a poster in either picker opens [DeletePostersDialog] for that anime: every poster still
 * on the shelf is there, the one held is already ticked, and a second tick is a second deletion. A
 * hold on the anime itself opens the same window with the whole set ticked, because "delete the
 * collection" is the same choice made thirty times. What was taken off is not hidden anywhere: the
 * catalogue keeps the holes, and tapping one is how it comes back.
 */

/** The dialog a hold opens: pick posters of [anime], then take them off the shelf. */
@Composable
fun DeletePostersDialog(
    anime: SpecialAnime,
    initial: Set<Int>,
    onDismiss: () -> Unit,
    onDelete: (Set<Int>) -> Unit,
) {
    val removed by PackLibrary.removed.collectAsStateWithLifecycle()
    val available = remember(removed, anime) { PackLibrary.visible(anime) }
    var picked by remember(anime) { mutableStateOf(initial) }
    val all = available.isNotEmpty() && picked.containsAll(available)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.profile_cancel))
                    }
                    Text(
                        stringResource(R.string.special_delete_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(
                        enabled = picked.isNotEmpty(),
                        onClick = { onDelete(picked) },
                    ) {
                        Text(
                            stringResource(R.string.special_delete_count, picked.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (picked.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.special_delete_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            picked = if (all) emptySet() else available.toSet()
                        },
                    ) {
                        Text(
                            stringResource(
                                if (all) R.string.special_select_none else R.string.special_select_all,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    available.chunked(3).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            row.forEach { poster ->
                                PosterTick(
                                    anime = anime,
                                    poster = poster,
                                    marked = poster in picked,
                                    onClick = {
                                        picked = if (poster in picked) picked - poster else picked + poster
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** One poster with a tick, drawn in the circle it is worn in so the crop is what is judged. */
@Composable
private fun PosterTick(
    anime: SpecialAnime,
    poster: Int,
    marked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.playClickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .then(
                    if (marked) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            PackPoster(
                anime = anime,
                poster = poster,
                targetPx = 320,
                modifier = Modifier.size(64.dp).clip(CircleShape),
            )
            if (marked) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
                )
                GlyphIcon(
                    Glyph.Tick,
                    size = 26.dp,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text(
            "${poster + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = if (marked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The short confirmation the two photographs get: their file is gone for good. */
@Composable
fun ConfirmDeleteDialog(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes confirm: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(confirm),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_cancel))
            }
        },
    )
}
