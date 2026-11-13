package dev.aurastore.store.ui.feed

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.aurastore.store.R
import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.data.sources.SourceCategory
import dev.aurastore.store.data.sources.SourceShelf
import dev.aurastore.store.theme.ArtShape
import dev.aurastore.store.ui.LoadingRail
import dev.aurastore.store.ui.PagingFooter
import dev.aurastore.store.ui.PlayChip
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.statusText

/**
 * One server's own page inside Browse.
 *
 * The strip above this list chose the server; this draws what that server's own front page draws:
 * its category terms with their counts, then the shelves it publishes in its own order (*New games*,
 * *Popular games*, *Editor's choice*), and the chosen category as a grid of cards.
 *
 * The cards carry the numbers the site prints on them: the version where the site lists one, the
 * size where it lists that, and the mod note or rating where that is what the source publishes.
 * A site whose list has no version is not given an invented one.
 *
 * Not a composable function: a lazy list's content lambda is not a composable scope, so the rows are
 * added through `item` and `items` and the draws happen inside them.
 */
internal fun LazyListScope.serverBrowse(
    state: FeedState,
    onSelectCategory: (SourceCategory) -> Unit,
    onHome: () -> Unit,
    onPick: (SourceApp) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val source = state.server

    if (state.serverCategories.isNotEmpty() || state.serverCategory != null) {
        item(key = "server-cats-${source.name}") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                // The source's own name is the way back to its front page. A category tap opens a
                // list; this chip goes back to the shelves without reaching for the back gesture.
                item(key = "server-home-${source.name}") {
                    PlayChip(
                        label = source.label,
                        selected = state.serverCategory == null,
                        onClick = onHome,
                    )
                }
                items(state.serverCategories, key = { "${source.name}-${it.id}" }) { category ->
                    PlayChip(
                        label = category.name,
                        selected = state.serverCategory?.id == category.id,
                        count = category.count,
                        onClick = { onSelectCategory(category) },
                    )
                }
            }
        }
    }

    if (state.serverLoading) {
        item(key = "server-loading-${source.name}") { LoadingRail() }
    }

    state.serverError?.let { message ->
        item(key = "server-error-${source.name}") {
            StateMessage(
                title = stringResource(R.string.feed_unavailable),
                body = statusText(message),
                onRetry = onRetry,
            )
        }
    }

    if (state.serverCategory == null) {
        // The front page: one row per shelf, in the order the site posted them.
        state.serverShelves.forEachIndexed { index, shelf ->
            item(key = "server-shelf-${source.name}-$index") {
                SourceShelfRow(shelf = shelf, onPick = onPick)
            }
        }
        if (!state.serverLoading && state.serverError == null && state.serverShelves.isEmpty()) {
            item(key = "server-empty-${source.name}") {
                StateMessage(
                    title = stringResource(R.string.feed_unavailable),
                    body = stringResource(R.string.cat_empty),
                    onRetry = onRetry,
                )
            }
        }
    } else {
        // One category: the two-column card grid the sites use on their own listing pages.
        items(
            items = state.serverApps.chunked(2),
            key = { row -> row.joinToString("|") { it.key } },
        ) { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { app ->
                    SourceGridCard(app = app, onPick = { onPick(app) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (state.serverApps.isNotEmpty()) {
            item(key = "server-footer-${source.name}") {
                PagingFooter(
                    loading = state.serverLoadingMore,
                    hasMore = state.serverHasMore,
                    capped = false,
                    error = state.serverMoreError,
                    name = state.serverCategory?.name ?: source.label,
                    onRetry = onLoadMore,
                )
            }
        } else if (!state.serverLoading && state.serverError == null) {
            item(key = "server-category-empty-${source.name}") {
                StateMessage(
                    title = stringResource(R.string.feed_unavailable),
                    body = stringResource(R.string.cat_empty),
                    onRetry = onRetry,
                )
            }
        }
    }
}

/** One titled rail of a source's own cards. */
@Composable
private fun SourceShelfRow(shelf: SourceShelf, onPick: (SourceApp) -> Unit) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(
            shelf.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shelf.apps, key = { it.key }) { app ->
                SourceRailCard(app = app, onPick = { onPick(app) })
            }
        }
    }
}

/** The site's card as a rail draws it: art, name, and the numbers the site prints under it. */
@Composable
private fun SourceRailCard(app: SourceApp, onPick: () -> Unit) {
    Column(Modifier.width(120.dp).playClickable(onClick = onPick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(ArtShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = app.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        appMeta(app)?.let { meta ->
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The same card in a two-column grid, so a category page reads like the site's own listing. */
@Composable
private fun SourceGridCard(app: SourceApp, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.playClickable(onClick = onPick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(ArtShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = app.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        appMeta(app)?.let { meta ->
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The line under a source card: only the fields that source actually published. */
private fun appMeta(app: SourceApp): String? =
    listOfNotNull(app.version, app.size, app.subtitle)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
        .ifBlank { null }
