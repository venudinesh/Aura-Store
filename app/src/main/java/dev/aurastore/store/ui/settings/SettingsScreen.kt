package dev.aurastore.store.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.data.DockAnimation
import dev.aurastore.store.data.Settings
import dev.aurastore.store.data.sources.SourceId
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.humanSize
import dev.aurastore.store.ui.library.PageBar
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.update.AppUpdateState
import dev.aurastore.store.update.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Settings as a wall of doors rather than a form.
 *
 * Eight categories sit in cards, two to a row, each carrying its glyph, its name and the state of
 * everything behind it. The state line is why the hub is worth the visit: the usual question ("is
 * the panel on Wash?") is answered without opening anything. About is a wide card at the end
 * because it is one page, not a category.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val autoUpdate by Settings.autoUpdate.collectAsStateWithLifecycle()
    val enabledSources by Settings.sources.collectAsStateWithLifecycle()
    val themeMode by Settings.themeMode.collectAsStateWithLifecycle()
    val palette by Settings.palette.collectAsStateWithLifecycle()
    val glass by Settings.glass.collectAsStateWithLifecycle()
    val keyboard by Settings.searchKeyboard.collectAsStateWithLifecycle()
    val dockAnimation by Settings.dockAnimation.collectAsStateWithLifecycle()
    val backgroundTheme by Settings.backgroundTheme.collectAsStateWithLifecycle()
    val updateBuild by Settings.appUpdateBuild.collectAsStateWithLifecycle()
    val updateState by AppUpdater.state.collectAsStateWithLifecycle()
    val lastRelease by AppUpdater.lastRelease.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val installed = remember(context) { AppUpdater.installed(context) }
    val offered = remember(lastRelease, installed) {
        lastRelease?.takeIf { AppUpdater.isNewer(context, it) }
    }
    // Only the bytes, not the queue: a progress tick must not repaint the hub four times a second.
    val cachedBytes by remember {
        DownloadEngine.items
            .map { rows ->
                rows.sumOf { item ->
                    (listOfNotNull(item.localPath) + item.bundleFiles).sumOf { path ->
                        runCatching { File(path).length() }.getOrDefault(0L)
                    }
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }.collectAsStateWithLifecycle(0L)

    val updateNote = when {
        offered != null -> stringResource(R.string.settings_version_available, offered.version.text)
        updateState is AppUpdateState.Checking -> stringResource(R.string.app_update_checking)
        updateState is AppUpdateState.UpToDate -> stringResource(R.string.settings_version_latest)
        else -> stringResource(updateBuild.description)
    }
    val otherSources = SourceId.entries.size - 1
    val sourcesOn = enabledSources.count { it != SourceId.LITEAPKS.name }

    val updates = HubCard(
        title = stringResource(R.string.settings_section_updates),
        note = updateNote,
        glyph = SettingsSection.UPDATES.glyph,
        onClick = { onOpenSection(SettingsSection.UPDATES) },
    )
    val apps = HubCard(
        title = stringResource(R.string.settings_section_apps),
        note = pluralStringResource(
            R.plurals.settings_category_apps_note,
            otherSources,
            stringResource(autoUpdate.label),
            sourcesOn,
            otherSources,
        ),
        glyph = SettingsSection.APPS.glyph,
        onClick = { onOpenSection(SettingsSection.APPS) },
    )
    val appearance = HubCard(
        title = stringResource(R.string.settings_section_appearance),
        note = stringResource(
            R.string.settings_category_appearance_note,
            stringResource(themeMode.label),
            stringResource(palette.label),
        ),
        glyph = SettingsSection.APPEARANCE.glyph,
        onClick = { onOpenSection(SettingsSection.APPEARANCE) },
    )
    val navigation = HubCard(
        title = stringResource(R.string.settings_section_navigation),
        note = navigationNote(glass.floating, glass.liquid),
        glyph = SettingsSection.NAVIGATION.glyph,
        onClick = { onOpenSection(SettingsSection.NAVIGATION) },
    )
    val dock = HubCard(
        title = stringResource(R.string.settings_section_dock),
        note = stringResource(
            R.string.settings_dock_note,
            stringResource(dockAnimation.label),
            DockAnimation.entries.size,
        ),
        glyph = SettingsSection.DOCK.glyph,
        onClick = { onOpenSection(SettingsSection.DOCK) },
    )
    val background = HubCard(
        title = stringResource(R.string.settings_section_background),
        note = stringResource(
            R.string.settings_background_note,
            stringResource(backgroundTheme.label),
        ),
        glyph = SettingsSection.BACKGROUND.glyph,
        onClick = { onOpenSection(SettingsSection.BACKGROUND) },
    )
    val downloads = HubCard(
        title = stringResource(R.string.settings_section_downloads),
        note = if (cachedBytes > 0L) {
            stringResource(R.string.settings_queue_bytes, humanSize(cachedBytes))
        } else {
            stringResource(R.string.settings_queue_none)
        },
        glyph = SettingsSection.DOWNLOADS.glyph,
        onClick = { onOpenSection(SettingsSection.DOWNLOADS) },
    )
    val search = HubCard(
        title = stringResource(R.string.settings_section_search),
        note = stringResource(
            if (keyboard) R.string.settings_search_keyboard_on else R.string.settings_search_keyboard_off,
        ),
        glyph = SettingsSection.SEARCH.glyph,
        onClick = { onOpenSection(SettingsSection.SEARCH) },
    )
    val about = HubCard(
        title = stringResource(R.string.settings_section_about),
        note = stringResource(R.string.app_update_version, installed.name, installed.code),
        glyph = Glyph.Info,
        onClick = onOpenAbout,
    )

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current),
    ) {
        item { PageBar(stringResource(R.string.settings_title), onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                HubRow(updates, apps)
                Spacer(Modifier.height(12.dp))
                HubRow(appearance, background)
                Spacer(Modifier.height(12.dp))
                HubRow(navigation, dock)
                Spacer(Modifier.height(12.dp))
                HubRow(downloads, search)
                Spacer(Modifier.height(22.dp))
                HubWideCard(about)
            }
        }
    }
}

/** One door on the hub, as data rather than as a composable argument. */
private data class HubCard(
    val title: String,
    val note: String,
    val glyph: Glyph,
    val onClick: () -> Unit,
)

/** Two doors, equal width and equal height, with the empty half kept when only one is passed. */
@Composable
private fun HubRow(left: HubCard, right: HubCard?) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        HubTile(
            card = left,
            modifier = Modifier.weight(1f).heightIn(min = 152.dp).fillMaxHeight(),
        )
        if (right != null) {
            HubTile(
                card = right,
                modifier = Modifier.weight(1f).heightIn(min = 152.dp).fillMaxHeight(),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** The tall door: glyph, name, state, and the way in. */
@Composable
private fun HubTile(card: HubCard, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Surface(
        shape = shape,
        color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.clip(shape).playClickable(onClick = card.onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphBadge(card.glyph)
                Spacer(Modifier.weight(1f))
                GlyphIcon(
                    Glyph.ChevronRight,
                    size = 16.dp,
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                card.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                card.note,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** The wide door: the About page, and the one-category rows. */
@Composable
private fun HubWideCard(card: HubCard) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Surface(
        shape = shape,
        color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().clip(shape).playClickable(onClick = card.onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphBadge(card.glyph)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            GlyphIcon(
                Glyph.ChevronRight,
                size = 18.dp,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun GlyphBadge(glyph: Glyph) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.primaryContainer,
        modifier = Modifier.size(40.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, size = 20.dp, tint = scheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun navigationNote(floating: Boolean, glass: Boolean): String =
    stringResource(if (floating) R.string.settings_nav_floating else R.string.settings_nav_flush) +
        " · " +
        stringResource(if (glass) R.string.settings_nav_glass_on else R.string.settings_nav_glass_off)
