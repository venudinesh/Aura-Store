package dev.aurastore.store.ui.device

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.data.AppState
import dev.aurastore.store.data.InstalledApps
import dev.aurastore.store.data.LibraryEntry
import dev.aurastore.store.install.Installer
import dev.aurastore.store.install.PackageLookup
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.PlayBadge
import dev.aurastore.store.ui.PlayChip
import dev.aurastore.store.ui.library.AppGlyph
import dev.aurastore.store.ui.library.LoadingRows
import dev.aurastore.store.ui.library.PageBar
import dev.aurastore.store.ui.library.StatusStrip
import dev.aurastore.store.ui.library.TABULAR
import dev.aurastore.store.ui.library.rememberAppIcon
import dev.aurastore.store.ui.playClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The apps on this phone.
 *
 * The same list the You tab showed behind its "All apps" segment, now its own page: a segment named
 * after a page read as a filter over the queue, so an uninstall sat under a heading called You.
 * Nothing here can update an app; that is Manage's job. This page opens and removes what is already
 * installed.
 */
@Composable
fun DeviceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val installs by AppState.installs.collectAsStateWithLifecycle()
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var showSystem by remember { mutableStateOf(false) }
    // Packages the sweep asked about and the catalogue does not carry. Cached misses are the only
    // apps with a verdict of "nothing here", so a row can say so instead of going quiet.
    var notInCatalogue by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refresh by remember { mutableIntStateOf(0) }
    var resumed by remember { mutableStateOf(false) }

    // A page opened again after an install happened somewhere else reloads: the list is the device's,
    // and the device changes while this page is not looking at it. The first resume is the open
    // itself, which the effect below already covers, so it must not count as a return.
    LifecycleResumeEffect(context) {
        if (resumed) refresh++
        resumed = true
        onPauseOrDispose { }
    }

    LaunchedEffect(refresh, installs) {
        AppState.refresh(context)
        entries = withContext(Dispatchers.IO) { InstalledApps.load(context) }
        val cache = withContext(Dispatchers.IO) { PackageLookup.readCache(context) }
        notInCatalogue = cache.filterValues { it.postId == null }.keys
    }

    val visible = entries.filter { showSystem || !it.isSystem }
    val ourInstalls = installs.values.map { it.packageName }.toSet()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current),
    ) {
        item { PageBar(stringResource(R.string.you_device), onBack) }

        item {
            StatusStrip(text = pluralStringResource(R.plurals.you_count_apps, visible.size, visible.size)) {
                PlayChip(
                    label = stringResource(R.string.you_system),
                    selected = showSystem,
                    onClick = { showSystem = !showSystem },
                )
            }
        }

        if (entries.isEmpty()) {
            item { LoadingRows() }
        } else {
            items(visible, key = { it.packageName }) { entry ->
                AppRowLine(
                    entry = entry,
                    fromAuraStore = entry.packageName in ourInstalls,
                    notInCatalogue = entry.postId == null && entry.packageName in notInCatalogue,
                    onOpen = { Installer.launch(context, entry.packageName) },
                    onUninstall = {
                        Installer.uninstall(context, entry.packageName)
                        Installer.forget(context, entry.packageName)
                    },
                )
            }
        }
    }
}

@Composable
private fun AppRowLine(
    entry: LibraryEntry,
    fromAuraStore: Boolean,
    notInCatalogue: Boolean,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
) {
    val icon = rememberAppIcon(entry.packageName)
    // The row is the open action. "Open" is what a tap on an app has meant since the first phone, and
    // one primary label per row across thirteen rows was half the noise on this page. Uninstall stays
    // a visible target: it is the one thing here that cannot be undone by tapping the row again.
    Row(
        (if (entry.hasLaunchIntent) Modifier.fillMaxWidth().playClickable(onClick = onOpen)
         else Modifier.fillMaxWidth())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyph(icon = icon, label = entry.label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // The package name used to follow the version here, and every one of the rows cut
                    // it to "com.and…" — long enough to eat the line, short enough to identify nothing.
                    // The version is the fact a reader can act on; the package stays on the app's own
                    // page, and is all that is left to say when there is no version.
                    entry.versionName?.let { "v$it" } ?: entry.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (fromAuraStore) {
                    Spacer(Modifier.width(6.dp))
                    PlayBadge(stringResource(R.string.app_name))
                }
            }
            // A searched-and-missed app otherwise looks exactly like an app the sweep has not reached.
            // Short, because the row's one remaining action claims the rest of the line.
            if (notInCatalogue) {
                Text(
                    stringResource(R.string.you_not_in_catalogue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!entry.isSystem) {
            TextButton(onClick = onUninstall, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(
                    stringResource(R.string.action_uninstall),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
