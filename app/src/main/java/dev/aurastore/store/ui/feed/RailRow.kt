package dev.aurastore.store.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aurastore.store.R
import dev.aurastore.store.ui.AppCard
import dev.aurastore.store.ui.SectionHeader
import dev.aurastore.store.ui.playEnter

/**
 * One rail: its title, its cards, and the next page of them.
 *
 * A rail is the shortest list in the app and the only horizontal one, so its end arrives on a swipe
 * rather than on a scroll — and a swipe can be repeated, which is what makes a rail that stopped
 * growing at twelve cards a bug rather than a limit. [onLoadMore] is keyed by title because the title
 * is what the list keys are made of, and the catalogue guarantees one rail per name on a screen.
 */
@Composable
fun RailRow(
    rail: Rail,
    generation: Any?,
    onOpenPost: (Long) -> Unit,
    onLoadMore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberLazyListState()
    // Four cards before the last, which on this width is roughly one swipe of screen left to read.
    val nearTheEnd by remember {
        derivedStateOf {
            val info = rowState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearTheEnd, rail.posts.size) {
        if (nearTheEnd) onLoadMore(rail.title)
    }

    Column(modifier) {
        SectionHeader(rail.title, subtitle = rail.subtitle)
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(rail.posts) { position, post ->
                AppCard(
                    post = post,
                    modifier = Modifier.playEnter(generation, position),
                    onClick = { onOpenPost(post.id) },
                )
            }
            if (rail.loadingMore) {
                item {
                    Box(
                        Modifier.size(width = 90.dp, height = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }
            // The one ending a rail is allowed to name. A flat list gets a line under its last row; a
            // horizontal row has no under, so the sentence takes the last slot instead — and only the
            // route's window is a claim a rail can make, since a rail that ran short simply stops growing.
            if (rail.capped && !rail.loadingMore) {
                item {
                    Box(
                        Modifier.size(width = 180.dp, height = 120.dp).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.list_end_capped, rail.title, rail.reach),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
