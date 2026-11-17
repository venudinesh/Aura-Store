package dev.aurastore.store.ui.wishlist

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.data.WishEntry
import dev.aurastore.store.data.Wishlist
import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.ui.ArtImage
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.library.PageBar
import dev.aurastore.store.ui.playClickable

/**
 * One list of everything the heart saved, reached from the You tab.
 *
 * It used to be the sixth tab. The row carries what the page needs rather than a live catalogue
 * object, so a wish opened after the process was killed still resolves: a catalogue entry by its post
 * id, a source entry by its whole [SourceApp]. Removing is the same heart that added it, filled, on
 * the row's right edge; a row that could only be cleared from inside the app page would make the list
 * itself read only.
 */
@Composable
fun WishlistScreen(
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenSourceApp: (SourceApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val entries by Wishlist.entries.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val removed = stringResource(R.string.wishlist_removed)

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 28.dp + LocalNavBarInset.current),
    ) {
        item { PageBar(stringResource(R.string.tab_wishlist), onBack) }

        if (entries.isNotEmpty()) {
            item {
                Text(
                    pluralStringResource(R.plurals.wishlist_count, entries.size, entries.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
                )
            }
        }

        if (entries.isEmpty()) {
            item { EmptyWishlist() }
        } else {
            items(entries, key = { it.key }) { entry ->
                WishRow(
                    entry = entry,
                    onOpen = {
                        val postId = entry.postId
                        val app = entry.toSourceApp()
                        when {
                            postId != null -> onOpenPost(postId)
                            app != null -> onOpenSourceApp(app)
                        }
                    },
                    onRemove = {
                        Wishlist.remove(context, entry.key)
                        Toast.makeText(context, removed, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}

/** One saved app. Shared with the profile's Wishlist page, which is the same list under a tab. */
@Composable
fun WishRow(
    entry: WishEntry,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .playClickable(onClick = onOpen, pressedScale = 0.99f)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(
            url = entry.iconUrl,
            title = entry.title,
            modifier = Modifier.size(60.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = entry.metaLine()
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
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .playClickable(onClick = onRemove),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlyphIcon(
                    Glyph.Heart,
                    size = 22.dp,
                    filled = true,
                    tint = MaterialTheme.colorScheme.primary,
                    label = stringResource(R.string.a11y_wishlist_remove),
                )
            }
        }
    }
}

/** The empty list, drawn where the list would be. */
@Composable
fun EmptyWishlist() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlyphIcon(
                    Glyph.Heart,
                    size = 32.dp,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Text(
            stringResource(R.string.wishlist_empty_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(R.string.wishlist_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
