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
import dev.aurastore.store.ui.ArtImage
import dev.aurastore.store.ui.LoadingRail
import dev.aurastore.store.ui.PagingFooter
import dev.aurastore.store.ui.PlayChip
import dev.aurastore.store.ui.SectionHeader
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
    onOpenShelf: (SourceShelf) -> Unit,
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
                        selected = state.serverCategory == null && !state.serverAllOpen,
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
        // The front page: one row per shelf, in the order the site posted them. A *View all* that
        // opened the whole catalogue leaves the rows out: the grid below is what was asked for.
        if (!state.serverAllOpen) {
            state.serverShelves.forEachIndexed { index, shelf ->
                item(key = "server-shelf-${source.name}-$index") {
                    SourceShelfRow(
                        shelf = shelf,
                        onPick = onPick,
                        onOpenAll = shelf.more?.let { { onOpenShelf(shelf) } },
                    )
                }
            }
        }
        // Under its own shelves, the source's whole catalogue, in the order the source lists it.
        // The shelves are what the site chose; this is everything it has, a page at a time.
        val allShown = state.serverAll.isNotEmpty() || state.serverAllLoading ||
            (state.serverAllOpen && state.serverAllMoreError != null)
        if (allShown) {
            item(key = "server-all-header-${source.name}") {
                SectionHeader(
                    title = stringResource(R.string.src_all_apps, source.label),
                    subtitle = stringResource(R.string.src_all_apps_note),
                )
            }
            if (state.serverAllLoading && state.serverAll.isEmpty()) {
                item(key = "server-all-loading-${source.name}") { LoadingRail() }
            }
            if (state.serverAll.isEmpty()) {
                state.serverAllMoreError?.let { message ->
                    item(key = "server-all-error-${source.name}") {
                        StateMessage(
                            title = stringResource(R.string.feed_unavailable),
                            body = statusText(message),
                            onRetry = onRetry,
                        )
                    }
                }
            }
            sourceGrid(state.serverAll, keyPrefix = "server-all-${source.name}-", onPick = onPick)
            if (state.serverAll.isNotEmpty()) {
                item(key = "server-all-footer-${source.name}") {
                    PagingFooter(
                        loading = state.serverAllLoadingMore,
                        hasMore = state.serverAllHasMore,
                        capped = false,
                        error = state.serverAllMoreError,
                        name = source.label,
                        onRetry = onLoadMore,
                    )
                }
            }
        }
        if (!state.serverLoading && state.serverError == null &&
            state.serverAll.isEmpty() && !state.serverAllLoading && state.serverAllMoreError == null &&
            (state.serverShelves.isEmpty() || state.serverAllOpen)
        ) {
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
        val category = state.serverCategory
        // A shelf can lead to a listing the source does not put in its own chip row — its whole
        // games shelf, say. This heading is what names that list when no chip can.
        if (state.serverCategories.none { it.id == category.id }) {
            item(key = "server-cat-header-${source.name}-${category.id}") {
                SectionHeader(title = category.name, subtitle = source.label)
            }
        }
        sourceGrid(state.serverApps, keyPrefix = "server-cat-${source.name}-", onPick = onPick)

        if (state.serverApps.isNotEmpty()) {
            item(key = "server-footer-${source.name}") {
                PagingFooter(
                    loading = state.serverLoadingMore,
                    hasMore = state.serverHasMore,
                    capped = false,
                    error = state.serverMoreError,
                    name = category.name,
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

/** The site's own two-column grid, for a category's page and for the all-apps walk under a home. */
private fun LazyListScope.sourceGrid(
    apps: List<SourceApp>,
    keyPrefix: String,
    onPick: (SourceApp) -> Unit,
) {
    items(
        items = apps.chunked(2),
        key = { row -> keyPrefix + row.joinToString("|") { it.key } },
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
}

/** One titled rail of a source's own cards, with the way to the list behind it. */
@Composable
private fun SourceShelfRow(
    shelf: SourceShelf,
    onPick: (SourceApp) -> Unit,
    onOpenAll: (() -> Unit)?,
) {
    Column(Modifier.padding(bottom = 10.dp)) {
        SectionHeader(
            shelf.title,
            onMore = onOpenAll,
            moreLabel = if (onOpenAll != null) stringResource(R.string.feed_view_all) else null,
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
        ArtImage(
            url = app.iconUrl,
            title = app.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
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
        ArtImage(
            url = app.iconUrl,
            title = app.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
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
