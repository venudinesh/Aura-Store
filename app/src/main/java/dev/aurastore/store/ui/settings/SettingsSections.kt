package dev.aurastore.store.ui.settings

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.data.AutoUpdateMode
import dev.aurastore.store.data.GlassEngine
import dev.aurastore.store.data.NavOrder
import dev.aurastore.store.data.PaletteMode
import dev.aurastore.store.data.Settings
import dev.aurastore.store.data.ThemeMode
import dev.aurastore.store.data.UpdateBuild
import dev.aurastore.store.data.dynamicPaletteSupported
import dev.aurastore.store.data.glassEngineRuns
import dev.aurastore.store.data.sources.SourceId
import dev.aurastore.store.data.withDefaultMaterial
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.install.Installer
import dev.aurastore.store.theme.Accent
import dev.aurastore.store.theme.glassWidthFraction
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.InfoCard
import dev.aurastore.store.ui.InfoRow
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.formatTimestamp
import dev.aurastore.store.ui.humanSize
import dev.aurastore.store.ui.library.PageBar
import dev.aurastore.store.ui.rememberStoreActions
import dev.aurastore.store.update.AppUpdateState
import dev.aurastore.store.update.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt
import java.io.File

/**
 * The eight category pages behind the Settings hub.
 *
 * Each one is the same rows the old single form held, grouped by what they change rather than by the
 * order they were written in. The page bar names the category and back returns to the hub; nothing
 * here pushes further except the dialogs a row opens.
 */
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    onBack: () -> Unit,
    onRefreshCatalogue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (section) {
        SettingsSection.UPDATES -> UpdatesPage(onBack, modifier)
        SettingsSection.APPS -> AppsPage(onBack, onRefreshCatalogue, modifier)
        SettingsSection.APPEARANCE -> AppearancePage(onBack, modifier)
        SettingsSection.BACKGROUND -> BackgroundPage(onBack, modifier)
        SettingsSection.NAVIGATION -> NavigationPage(onBack, modifier)
        SettingsSection.DOCK -> DockAnimationPage(onBack, modifier)
        SettingsSection.DOWNLOADS -> DownloadsPage(onBack, modifier)
        SettingsSection.SEARCH -> SearchPage(onBack, modifier)
    }
}

/**
 * One category's page: the shared bar, then the category's rows as one block.
 *
 * A settings page is a handful of short rows, so they are one lazy item rather than one item each.
 * The list still scrolls and still reserves the bar's inset; what it does not do is measure a dozen
 * items to draw nine.
 */
@Composable
internal fun SectionPage(
    section: SettingsSection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current),
    ) {
        item { PageBar(stringResource(section.label), onBack) }
        item { Column(Modifier.fillMaxWidth()) { content() } }
    }
}

// ------------------------------------------------------------------------------------- Aura Store

@Composable
private fun UpdatesPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appAutoUpdate by Settings.appAutoUpdate.collectAsStateWithLifecycle()
    val updateBuild by Settings.appUpdateBuild.collectAsStateWithLifecycle()
    val updateState by AppUpdater.state.collectAsStateWithLifecycle()
    val lastRelease by AppUpdater.lastRelease.collectAsStateWithLifecycle()
    val installed = remember { AppUpdater.installed(context) }
    val debuggable = remember {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val offered = remember(lastRelease, installed) {
        lastRelease?.takeIf { AppUpdater.isNewer(context, it) }
    }
    var buildDialog by remember { mutableStateOf(false) }
    val update = updateState

    SectionPage(SettingsSection.UPDATES, onBack, modifier) {
        // The version is a fact, so it stays on screen whether a check is running, has failed, or
        // found nothing. The action and its state live on the row below.
        SettingsRow(
            title = stringResource(R.string.settings_app_version),
            subtitle = stringResource(R.string.app_update_version, installed.name, installed.code),
            value = stringResource(
                if (debuggable) R.string.settings_version_debug else R.string.settings_version_release,
            ),
            onClick = null,
        )
        SettingsRow(
            title = stringResource(R.string.settings_app_update),
            subtitle = when {
                update is AppUpdateState.Checking -> stringResource(R.string.app_update_checking)
                offered != null -> stringResource(R.string.settings_version_available, offered.version.text)
                // Only a check that never reached GitHub has no dialog to explain itself.
                update is AppUpdateState.Failed && update.release == null ->
                    stringResource(R.string.app_update_failed_network)
                update is AppUpdateState.UpToDate -> stringResource(R.string.settings_version_latest)
                else -> stringResource(R.string.settings_version_unchecked)
            },
            value = when {
                update is AppUpdateState.Checking -> null
                offered != null -> stringResource(R.string.app_update_now)
                else -> stringResource(R.string.app_update_check_now)
            },
            onClick = {
                if (offered != null) AppUpdater.present(offered) else AppUpdater.check(context)
            },
        )
        SwitchRow(
            title = stringResource(R.string.app_update_auto),
            subtitle = stringResource(R.string.app_update_auto_note),
            checked = appAutoUpdate,
            onCheckedChange = { Settings.setAppAutoUpdate(context, it) },
        )
        SettingsRow(
            title = stringResource(R.string.settings_update_build),
            subtitle = stringResource(R.string.settings_update_build_note),
            value = stringResource(updateBuild.label),
            onClick = { buildDialog = true },
        )
    }

    if (buildDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_update_build),
            options = UpdateBuild.entries.toList(),
            selected = updateBuild,
            labelOf = { stringResource(it.label) },
            noteOf = { stringResource(it.description) },
            onPick = { Settings.setAppUpdateBuild(context, it); buildDialog = false },
            onDismiss = { buildDialog = false },
        )
    }
}

// ---------------------------------------------------------------------------- apps and the catalogue

@Composable
private fun AppsPage(
    onBack: () -> Unit,
    onRefreshCatalogue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val autoUpdate by Settings.autoUpdate.collectAsStateWithLifecycle()
    val lastSync by Settings.lastSync.collectAsStateWithLifecycle()
    val enabledSources by Settings.sources.collectAsStateWithLifecycle()
    val defaultSource by Settings.defaultSource.collectAsStateWithLifecycle()
    var modeDialog by remember { mutableStateOf(false) }
    var sourceDialog by remember { mutableStateOf(false) }

    SectionPage(SettingsSection.APPS, onBack, modifier) {
        SettingsRow(
            title = stringResource(R.string.settings_auto_update),
            subtitle = stringResource(autoUpdate.description),
            value = stringResource(autoUpdate.label),
            onClick = { modeDialog = true },
        )
        SettingsRow(
            title = stringResource(R.string.settings_catalogue_sync),
            subtitle = lastSync.takeIf { it > 0 }?.let {
                stringResource(R.string.settings_last_sync, formatTimestamp(it) ?: "—")
            } ?: stringResource(R.string.settings_check_live),
            value = stringResource(R.string.settings_refresh_now),
            onClick = onRefreshCatalogue,
        )
        Text(
            stringResource(R.string.settings_sources_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        SourceId.entries.filter { it != SourceId.LITEAPKS }.forEach { source ->
            SwitchRow(
                title = source.label,
                subtitle = stringResource(source.note),
                checked = source.name in enabledSources,
                onCheckedChange = { Settings.setSourceEnabled(context, source, it) },
            )
        }
        SettingsRow(
            title = stringResource(R.string.settings_default_source),
            subtitle = stringResource(R.string.settings_default_source_note),
            value = defaultSource?.label ?: stringResource(R.string.settings_source_store),
            onClick = { sourceDialog = true },
        )
    }

    if (modeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_auto_update),
            options = AutoUpdateMode.entries.toList(),
            selected = autoUpdate,
            labelOf = { stringResource(it.label) },
            noteOf = { stringResource(it.description) },
            onPick = { Settings.setAutoUpdate(context, it); modeDialog = false },
            onDismiss = { modeDialog = false },
        )
    }
    if (sourceDialog) {
        // The store's own catalogue is the first option and the default: it is the one the app was
        // built around and the one that answers for every post it publishes.
        val options: List<SourceId?> = listOf<SourceId?>(null) +
            SourceId.entries.filter { it != SourceId.LITEAPKS && it.name in enabledSources }
        ChoiceDialog(
            title = stringResource(R.string.settings_default_source),
            options = options,
            selected = defaultSource,
            labelOf = { source ->
                source?.label ?: stringResource(R.string.settings_source_store)
            },
            noteOf = { source ->
                source?.let { stringResource(it.note) } ?: stringResource(R.string.settings_source_store_note)
            },
            onPick = { source ->
                Settings.setDefaultSource(context, source)
                sourceDialog = false
            },
            onDismiss = { sourceDialog = false },
        )
    }
}

// --------------------------------------------------------------------------------------- appearance

@Composable
private fun AppearancePage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val themeMode by Settings.themeMode.collectAsStateWithLifecycle()
    val amoled by Settings.amoled.collectAsStateWithLifecycle()
    val palette by Settings.palette.collectAsStateWithLifecycle()
    val paletteSeed by Settings.paletteSeed.collectAsStateWithLifecycle()
    var themeDialog by remember { mutableStateOf(false) }
    var paletteDialog by remember { mutableStateOf(false) }
    var colourPicker by remember { mutableStateOf(false) }

    SectionPage(SettingsSection.APPEARANCE, onBack, modifier) {
        SettingsRow(
            title = stringResource(R.string.settings_theme),
            subtitle = stringResource(themeMode.description),
            value = stringResource(themeMode.label),
            onClick = { themeDialog = true },
        )
        // Its own switch, beside the theme it overrides: AMOLED is not a wallpaper, and it must be
        // reachable without opening the background picker.
        SwitchRow(
            title = stringResource(R.string.settings_amoled),
            subtitle = stringResource(R.string.settings_amoled_note),
            checked = amoled,
            onCheckedChange = { Settings.setAmoled(context, it) },
        )
        // The row reports what is actually on screen, not what was tapped: on a pre-12 handset
        // "match my wallpaper" has nothing to match, and saying so beats a control that lies.
        val wallpaperSticky = palette == PaletteMode.WALLPAPER && !dynamicPaletteSupported()
        SettingsRow(
            title = stringResource(R.string.settings_palette),
            subtitle = stringResource(
                if (wallpaperSticky) R.string.palette_wallpaper_unsupported else palette.description
            ),
            value = stringResource(palette.label),
            onClick = { paletteDialog = true },
        )
    }

    if (themeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.toList(),
            selected = themeMode,
            labelOf = { stringResource(it.label) },
            noteOf = { stringResource(it.description) },
            onPick = { Settings.setThemeMode(context, it); themeDialog = false },
            onDismiss = { themeDialog = false },
        )
    }
    if (paletteDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_palette),
            options = PaletteMode.entries.toList(),
            selected = palette,
            labelOf = { stringResource(it.label) },
            noteOf = {
                stringResource(
                    if (it == PaletteMode.WALLPAPER && !dynamicPaletteSupported())
                        R.string.palette_wallpaper_unsupported else it.description
                )
            },
            onPick = {
                Settings.setPalette(context, it)
                paletteDialog = false
                // Choosing "custom" without a colour yet is half an action, so the wheel opens on
                // the pick rather than waiting for the row to be tapped a second time.
                if (it == PaletteMode.CUSTOM) colourPicker = true
            },
            onDismiss = { paletteDialog = false },
        )
    }
    if (colourPicker) {
        ColourPickerDialog(
            seed = paletteSeed ?: Accent.toArgb(),
            onPreview = { Settings.showPaletteSeed(it) },
            onCommit = { Settings.setPaletteSeed(context, it) },
            onDismiss = { colourPicker = false },
        )
    }
}

// ------------------------------------------------------------------------------------ navigation bar

@Composable
private fun NavigationPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val glass by Settings.glass.collectAsStateWithLifecycle()
    val navSlide by Settings.navSlide.collectAsStateWithLifecycle()
    val navIconMotion by Settings.navIconMotion.collectAsStateWithLifecycle()
    val navOrder by Settings.navOrder.collectAsStateWithLifecycle()

    SectionPage(SettingsSection.NAVIGATION, onBack, modifier) {
        SettingsCard(
            title = stringResource(R.string.nav_group_bar),
        ) {
            SwitchRow(
                title = stringResource(R.string.nav_floating),
                subtitle = stringResource(
                    if (glass.floating) R.string.nav_floating_note else R.string.nav_flush
                ),
                checked = glass.floating,
                tile = false,
                onCheckedChange = { on -> Settings.setGlass(context) { it.copy(floating = on) } },
            )
            // The two motions, next to the shape they belong to: a bar that glides and one that
            // jumps are the same layout, and the icons are the same drawings.
            SwitchRow(
                title = stringResource(R.string.nav_slide),
                subtitle = stringResource(R.string.nav_slide_note),
                checked = navSlide,
                tile = false,
                onCheckedChange = { on -> Settings.setNavSlide(context, on) },
            )
            SwitchRow(
                title = stringResource(R.string.nav_icon_motion),
                subtitle = stringResource(R.string.nav_icon_motion_note),
                checked = navIconMotion,
                tile = false,
                onCheckedChange = { on -> Settings.setNavIconMotion(context, on) },
            )
        }

        // The reference's first card: the five icons themselves, dragged into a new order. The strip
        // behind the settings sheet is live, so a drop is visible without leaving this screen.
        SettingsCard(
            title = stringResource(R.string.nav_reorder),
            note = stringResource(R.string.nav_reorder_note),
        ) {
            NavOrderRow(
                order = navOrder,
                onMove = { from, to ->
                    Settings.setNavOrder(context, NavOrder.move(navOrder, from, to))
                },
            )
            Text(
                stringResource(R.string.nav_reorder_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
            SettingsCardRow(
                title = stringResource(R.string.nav_reorder_reset),
                value = stringResource(R.string.nav_restore),
                onClick = { Settings.resetNavOrder(context) },
            )
        }

        // The reference's second card. The chip under the switch reports the engine that actually
        // runs, and the rows below it let the choice be made — including on a phone where asking for
        // the shader engine resolves to the drawn one, which is why the chip is not the selection.
        SettingsCard(
            title = stringResource(R.string.nav_group_glass),
            note = stringResource(R.string.nav_glass_note),
        ) {
            SwitchRow(
                title = stringResource(R.string.nav_glass),
                subtitle = "",
                checked = glass.liquid,
                tile = false,
                onCheckedChange = { on -> Settings.setGlass(context) { it.copy(liquid = on) } },
            )
            val running = glassEngineRuns(glass.engine)
            SettingsChip(stringResource(R.string.nav_engine, stringResource(running.label)))
            // The chip above reports which engine draws; this group is where one is chosen, and it
            // wears the same small label as the two groups below it so the card reads as three parts.
            SettingsCardLabel(stringResource(R.string.nav_engine_group))
            GlassEngine.entries.forEach { engine ->
                EngineOption(
                    label = stringResource(engine.label),
                    selected = glass.engine == engine,
                    onSelect = { Settings.setGlass(context) { it.copy(engine = engine) } },
                )
            }

            if (glass.liquid) {
                SettingsCardLabel(stringResource(R.string.nav_effects))
                // One row shape for every effect, because they are all fractions of their own range
                // and the bar on the screen behind reacts while the thumb is still down. Every one of
                // them starts at zero — the shipped bar is fully transparent — so each is "add this
                // much".
                listOf(
                    GlassKnob(R.string.nav_blur, glass.blur) { s, v -> s.copy(blur = v) },
                    GlassKnob(R.string.nav_chromatic, glass.chromatic) { s, v -> s.copy(chromatic = v) },
                    GlassKnob(R.string.nav_refraction, glass.refraction) { s, v -> s.copy(refraction = v) },
                    GlassKnob(R.string.nav_depth, glass.depth) { s, v -> s.copy(depth = v) },
                ).forEach { knob ->
                    GlassSliderRow(
                        title = stringResource(knob.label),
                        value = knob.value,
                        display = stringResource(R.string.nav_level, knob.display(knob.value)),
                        onDrag = { v -> Settings.showGlass { knob.set(it, v) } },
                        onDone = { Settings.commitGlass(context) },
                    )
                }

                SettingsCardLabel(stringResource(R.string.nav_layout))
                listOf(
                    GlassKnob(R.string.nav_corner, glass.corner) { s, v -> s.copy(corner = v) },
                    // Width is the one value whose percentage is not the raw fraction: the slider says
                    // 60..150 % of the content width, so 0.5 is 105 %, not 50 %.
                    GlassKnob(
                        R.string.nav_width,
                        glass.width,
                        display = { (glassWidthFraction(it) * 100).roundToInt() },
                    ) { s, v -> s.copy(width = v) },
                    GlassKnob(R.string.nav_offset, glass.offset) { s, v -> s.copy(offset = v) },
                ).forEach { knob ->
                    GlassSliderRow(
                        title = stringResource(knob.label),
                        value = knob.value,
                        display = stringResource(R.string.nav_level, knob.display(knob.value)),
                        onDrag = { v -> Settings.showGlass { knob.set(it, v) } },
                        onDone = { Settings.commitGlass(context) },
                    )
                }

                Spacer(Modifier.height(8.dp))
                // Five continuous controls with no reference point need a way out, or the only way
                // back to the shipped look is to eyeball five sliders. It resets the material only —
                // the shape toggles and the engine above are a choice about the bar, not its glass.
                SettingsCardRow(
                    title = stringResource(R.string.nav_reset),
                    subtitle = stringResource(R.string.nav_reset_note),
                    value = stringResource(R.string.nav_restore),
                    onClick = {
                        Settings.setGlass(context) { it.withDefaultMaterial() }
                        Toast.makeText(context, R.string.nav_reset_done, Toast.LENGTH_SHORT).show()
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ----------------------------------------------------------------------------------------- downloads

@Composable
private fun DownloadsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val actions = rememberStoreActions()
    var canInstall by remember { mutableStateOf(Installer.canInstall(context)) }
    var notified by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    // The two figures this page prints, not the queue. A transfer's progress tick changes neither the
    // row count nor the bytes on disk, so it must not recompose the page or stat every cached file
    // again four times a second; the stat runs off the main thread and only a real difference lands.
    val queue by remember {
        DownloadEngine.items
            .map { rows ->
                val bytes = rows.sumOf { item ->
                    // A bundle owns two sets of bytes: the archive it downloaded and the packages
                    // unpacked out of it.
                    (listOfNotNull(item.localPath) + item.bundleFiles).sumOf { path ->
                        runCatching { File(path).length() }.getOrDefault(0L)
                    }
                }
                rows.size to bytes
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }.collectAsStateWithLifecycle(0 to 0L)

    LifecycleResumeEffect(context) {
        canInstall = Installer.canInstall(context)
        notified = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }

    SectionPage(SettingsSection.DOWNLOADS, onBack, modifier) {
        SettingsRow(
            title = stringResource(R.string.settings_install_unknown),
            subtitle = stringResource(
                if (canInstall) R.string.settings_install_allowed else R.string.settings_install_needed
            ),
            value = stringResource(if (canInstall) R.string.settings_allowed else R.string.action_allow),
            onClick = { Installer.openUnknownSourcesSettings(context) },
        )
        SettingsRow(
            title = stringResource(R.string.settings_download_notifications),
            subtitle = stringResource(
                if (notified) R.string.settings_notif_on else R.string.settings_notif_off
            ),
            value = stringResource(
                if (notified) R.string.settings_notif_managed else R.string.action_allow
            ),
            onClick = {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                    ).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        )
        InfoCard {
            InfoRow(
                Glyph.Shield,
                stringResource(R.string.settings_why_switch),
                subtitle = stringResource(R.string.settings_why_switch_body),
            )
        }
        SettingsRow(
            title = stringResource(R.string.settings_downloaded_packages),
            subtitle = stringResource(R.string.settings_queue_summary, queue.first, humanSize(queue.second)),
            value = stringResource(R.string.settings_clear_finished),
            onClick = actions::clearFinished,
        )
        SettingsRow(
            title = stringResource(R.string.settings_save_apks),
            subtitle = stringResource(R.string.settings_save_apks_note),
            value = null,
            onClick = null,
        )
    }
}

// -------------------------------------------------------------------------------------------- search

@Composable
private fun SearchPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboard by Settings.searchKeyboard.collectAsStateWithLifecycle()

    SectionPage(SettingsSection.SEARCH, onBack, modifier) {
        SwitchRow(
            title = stringResource(R.string.settings_search_keyboard),
            subtitle = stringResource(R.string.settings_search_keyboard_note),
            checked = keyboard,
            onCheckedChange = { Settings.setSearchKeyboard(context, it) },
        )
    }
}
