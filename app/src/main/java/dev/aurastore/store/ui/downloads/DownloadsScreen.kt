package dev.aurastore.store.ui.downloads

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.ui.DownloadRow
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.StoreActions
import dev.aurastore.store.ui.library.GroupHeader
import dev.aurastore.store.ui.library.PageBar
import dev.aurastore.store.ui.rememberStoreActions

/**
 * The download queue, on its own page.
 *
 * It used to be the tail of the You tab, under the updates list and the installed apps. That put the
 * queue below two things it has nothing to do with, and it made the brand bar's download badge switch
 * tabs — the one button on the screen that names downloads landed on a page called You. A queue is a
 * place with a length, so it is a page.
 */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = rememberStoreActions()
    val downloads by DownloadEngine.items.collectAsStateWithLifecycle()
    val active = downloads.filter { it.isActive }
    val finished = downloads.filterNot { it.isActive }
    val disposable = downloads.filter { it.isDisposable }
    // A long press starts selection; from then on a tap adds or removes a row, and the page's own
    // header turns into the action bar. The set is saved so a rotation does not drop the selection.
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val selectedItems = downloads.filter { it.id in selected }

    fun leaveSelection() {
        selecting = false
        selected = emptySet()
    }

    fun toggle(id: String) {
        val next = if (id in selected) selected - id else selected + id
        selected = next
        if (next.isEmpty()) selecting = false
    }

    /**
     * A hold on a row starts selection and takes that row in one motion; a tap while selecting only
     * toggles. [toggle] keeps the header honest when the last row is taken back out.
     */
    fun select(id: String) {
        selecting = true
        toggle(id)
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current),
    ) {
        item {
            PageBar(
                title = if (selecting) {
                    pluralStringResource(R.plurals.dl_select_count, selectedItems.size, selectedItems.size)
                } else {
                    stringResource(R.string.sheet_downloads)
                },
                onBack = if (selecting) ::leaveSelection else onBack,
            ) {
                if (selecting) {
                    TextButton(
                        onClick = {
                            actions.removeAll(selectedItems)
                            leaveSelection()
                        },
                        enabled = selectedItems.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.dl_select_delete), style = MaterialTheme.typography.labelLarge)
                    }
                } else if (disposable.isNotEmpty()) {
                    TextButton(onClick = actions::clearFinished) {
                        Text(stringResource(R.string.you_clear), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (downloads.isNotEmpty() && !selecting) {
            item {
                Text(
                    stringResource(R.string.dl_select_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                )
            }
        }

        if (downloads.isEmpty()) {
            item {
                StateMessage(
                    title = stringResource(R.string.downloads_empty),
                    body = stringResource(R.string.downloads_empty_body),
                )
            }
        }

        if (active.isNotEmpty()) {
            item { GroupHeader(stringResource(R.string.you_in_progress)) }
            items(active, key = { it.id }) { item ->
                DownloadLine(
                    item = item,
                    actions = actions,
                    selecting = selecting,
                    selected = item.id in selected,
                    onSelect = { select(item.id) },
                    onOpenPost = onOpenPost,
                    onOpenDownload = onOpenDownload,
                )
            }
        }

        if (finished.isNotEmpty()) {
            item { GroupHeader(stringResource(R.string.you_not_in_progress)) }
            items(finished, key = { it.id }) { item ->
                DownloadLine(
                    item = item,
                    actions = actions,
                    selecting = selecting,
                    selected = item.id in selected,
                    onSelect = { select(item.id) },
                    onOpenPost = onOpenPost,
                    onOpenDownload = onOpenDownload,
                )
            }
        }
    }
}

/** The queue row. The digest it verified against is on the download's own page, not under the row. */
@Composable
private fun DownloadLine(
    item: DownloadItem,
    actions: StoreActions,
    selecting: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenDownload: (String) -> Unit,
) {
    DownloadRow(
        item = item,
        selecting = selecting,
        selected = selected,
        onSelect = onSelect,
        onToggle = { actions.toggle(item) },
        onInstall = { actions.install(item) },
        onDetails = {
            if (item.state == DownloadState.INSTALLED) actions.open(item) else onOpenPost(item.postId)
        },
        onRemove = { actions.remove(item) },
        onSave = { actions.saveToDevice(item) },
        onDelete = { actions.deleteFromDevice(item) },
        onInfo = { onOpenDownload(item.id) },
        onCopyLink = { actions.copyLink(item) },
        onUninstallFirst = { actions.uninstallFirst(item) },
    )
}
