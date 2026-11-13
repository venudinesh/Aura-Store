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
import dev.aurastore.store.data.sources.SourceId
import dev.aurastore.store.data.sources.SourceSection
import dev.aurastore.store.theme.ArtShape
import dev.aurastore.store.ui.PlayChip
import dev.aurastore.store.ui.playClickable

/**
 * The other catalogues, as the Browse tab shows them.
 *
 * Each source gets its own block: its name, the categories it groups its apps under, and the apps it
 * published last. The name is on the block because a row from 9MOD and a row from ApkVision look the
 * same otherwise, and the source is the thing that tells them apart.
 *
 * Not a composable function: a lazy list's content lambda is not a composable scope, so the blocks
 * are added through `item` and `items` and the draws happen inside them.
 */
internal fun LazyListScope.sourceSections(
    sections: List<SourceSection>,
    onOpenSource: (SourceId, String?) -> Unit,
    onPick: (SourceApp) -> Unit,
) {
    sections.forEach { section ->
        item(key = "source-head-${section.source.name}") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        section.source.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        section.source.site,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (section.failed) {
                        // The source answered nothing. Saying so is better than an empty block that
                        // looks like a source with nothing new.
                        Text(
                            stringResource(R.string.src_unavailable, section.source.label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = { onOpenSource(section.source, null) }) {
                    Text(stringResource(R.string.feed_source_browse))
                }
            }
        }

        if (section.categories.isNotEmpty()) {
            item(key = "source-cats-${section.source.name}") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    items(section.categories, key = { it.id }) { category ->
                        PlayChip(
                            label = category.name,
                            selected = false,
                            onClick = { onOpenSource(section.source, category.id) },
                        )
                    }
                }
            }
        }

        if (section.apps.isNotEmpty()) {
            item(key = "source-apps-${section.source.name}") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    items(section.apps, key = { it.key }) { app ->
                        SourceAppCard(app = app, onClick = { onPick(app) })
                    }
                }
            }
        }
    }
}

/**
 * One app of another catalogue, as a card.
 *
 * The source's own name is not on the card: the block above it says which site this row came from,
 * and repeating it on every card would say the same thing twelve times.
 */
@Composable
private fun SourceAppCard(app: SourceApp, onClick: () -> Unit) {
    Column(
        Modifier.width(96.dp).playClickable(onClick = onClick),
    ) {
        Box(
            Modifier.size(64.dp).clip(ArtShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = app.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(
            app.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(app.version, app.size).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
