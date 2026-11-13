package dev.aurastore.store.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import dev.aurastore.store.theme.ArtShape
import dev.aurastore.store.ui.LoadingRail
import dev.aurastore.store.ui.PagingFooter
import dev.aurastore.store.ui.PlayChip
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.statusText
import dev.aurastore.store.ui.playClickable

/**
 * One server's own page inside Browse.
 *
 * The strip above this list chose the server; this draws what that server publishes: its categories,
 * and the apps under the chosen one. The rows are a page at a time, so the list can reach what the
 * website lists instead of stopping at its first screen. The source's name is not repeated on every
 * row — the tab already says which site this is.
 *
 * Not a composable function: a lazy list's content lambda is not a composable scope, so the rows are
 * added through `item` and `items` and the draws happen inside them.
 */
internal fun LazyListScope.serverBrowse(
    state: FeedState,
    onSelectCategory: (SourceCategory) -> Unit,
    onPick: (SourceApp) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val source = state.server

    if (state.serverCategories.isNotEmpty()) {
        item(key = "server-cats-${source.name}") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                items(state.serverCategories, key = { it.id }) { category ->
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

    items(state.serverApps, key = { it.key }) { app ->
        ServerAppRow(app = app, onPick = { onPick(app) })
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
    }
}

/** One app a server lists, with the one action Browse offers: fetch it. */
@Composable
private fun ServerAppRow(app: SourceApp, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().playClickable(onClick = onPick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(ArtShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = app.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(app.version, app.size, app.subtitle).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onPick) {
            Text(stringResource(R.string.you_get))
        }
    }
}
