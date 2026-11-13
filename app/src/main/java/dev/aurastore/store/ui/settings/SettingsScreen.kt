package dev.aurastore.store.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.data.Api
import dev.aurastore.store.data.AutoUpdateMode
import dev.aurastore.store.data.GlassEngine
import dev.aurastore.store.data.PaletteMode
import dev.aurastore.store.data.ThemeMode
import dev.aurastore.store.data.LatestVersion
import dev.aurastore.store.data.NavOrder
import dev.aurastore.store.data.Settings
import dev.aurastore.store.data.dynamicPaletteSupported
import dev.aurastore.store.data.glassEngineRuns
import dev.aurastore.store.data.sources.SourceId
import dev.aurastore.store.theme.glassWidthFraction
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.NAV_TABS
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.theme.Accent
import dev.aurastore.store.install.Installer
import dev.aurastore.store.theme.EmphasizedEasing
import dev.aurastore.store.data.GlassStyle
import dev.aurastore.store.data.withDefaultMaterial
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardAccelerateEasing
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.InfoCard
import dev.aurastore.store.ui.InfoRow
import dev.aurastore.store.ui.formatTimestamp
import dev.aurastore.store.ui.humanSize
import dev.aurastore.store.ui.playEnter
import dev.aurastore.store.ui.playClickable
import kotlin.math.roundToInt
import java.io.File

/**
 * Play's Settings, limited to the switches this client can actually honour: auto-update policy,
 * the install-permission gate, catalogue sync and the download cache.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRefreshCatalogue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Settings.load(context)
    val autoUpdate by Settings.autoUpdate.collectAsStateWithLifecycle()
    val lastSync by Settings.lastSync.collectAsStateWithLifecycle()
    val enabledSources by Settings.sources.collectAsStateWithLifecycle()
    val defaultSource by Settings.defaultSource.collectAsStateWithLifecycle()
    val themeMode by Settings.themeMode.collectAsStateWithLifecycle()
    val palette by Settings.palette.collectAsStateWithLifecycle()
    val paletteSeed by Settings.paletteSeed.collectAsStateWithLifecycle()
    val glass by Settings.glass.collectAsStateWithLifecycle()
    val navSlide by Settings.navSlide.collectAsStateWithLifecycle()
    val navIconMotion by Settings.navIconMotion.collectAsStateWithLifecycle()
    val navOrder by Settings.navOrder.collectAsStateWithLifecycle()
    val downloads by DownloadEngine.items.collectAsStateWithLifecycle()
    var modeDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var paletteDialog by remember { mutableStateOf(false) }
    var sourceDialog by remember { mutableStateOf(false) }
    var colourPicker by remember { mutableStateOf(false) }
    var canInstall by remember { mutableStateOf(Installer.canInstall(context)) }
    var notified by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var latest by remember { mutableStateOf<LatestVersion?>(null) }

    LifecycleResumeEffect(context) {
        canInstall = Installer.canInstall(context)
        notified = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }

    // The same self-update probe the official client runs from its base activity.
    LaunchedEffect(Unit) {
        latest = runCatching { Api.latestVersion() }.getOrNull()
    }

    val cachedBytes = downloads.sumOf { item ->
        // A bundle owns two sets of bytes: the archive it downloaded and the packages unpacked out of it.
        (listOfNotNull(item.localPath) + item.bundleFiles).sumOf { path ->
            runCatching { File(path).length() }.getOrDefault(0L)
        }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(44.dp).clip(CircleShape).playClickable(onClick = onBack),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.Back, size = 22.dp, label = stringResource(R.string.a11y_back))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            Text(
                stringResource(R.string.settings_section_updates),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            )
        }
        item {
            SettingsRow(
                title = stringResource(R.string.settings_auto_update),
                subtitle = stringResource(autoUpdate.description),
                value = stringResource(autoUpdate.label),
                onClick = { modeDialog = true },
            )
        }
        item {
            SettingsRow(
                title = stringResource(R.string.settings_check_updates),
                subtitle = lastSync.takeIf { it > 0 }?.let {
                    stringResource(R.string.settings_last_sync, formatTimestamp(it) ?: "—")
                } ?: stringResource(R.string.settings_check_live),
                value = stringResource(R.string.settings_refresh_now),
                onClick = onRefreshCatalogue,
            )
        }
        item {
            val remote = latest?.version
            SettingsRow(
                title = stringResource(R.string.settings_app_version),
                subtitle = if (remote.isNullOrBlank()) {
                    stringResource(R.string.settings_version_latest)
                } else {
                    stringResource(R.string.settings_version_available, remote)
                },
                value = remote?.takeIf { it.isNotBlank() },
                onClick = null,
            )
        }

        item {
            Text(
                stringResource(R.string.settings_section_appearance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            )
        }
        item {
            SettingsRow(
                title = stringResource(R.string.settings_theme),
                subtitle = stringResource(themeMode.description),
                value = stringResource(themeMode.label),
                onClick = { themeDialog = true },
            )
        }
        item {
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

        item { SettingsCardTitle(stringResource(R.string.nav_section)) }

        // ---------------------------------------------------------------- the bar's shape
        item {
            SettingsCard {
                SwitchRow(
                    title = stringResource(R.string.nav_floating),
                    subtitle = stringResource(
                        if (glass.floating) R.string.nav_floating_note else R.string.nav_flush
                    ),
                    checked = glass.floating,
                    onCheckedChange = { on -> Settings.setGlass(context) { it.copy(floating = on) } },
                )
                // The two motions, next to the shape they belong to: a bar that glides and one that
                // jumps are the same layout, and the icons are the same drawings.
                SwitchRow(
                    title = stringResource(R.string.nav_slide),
                    subtitle = stringResource(R.string.nav_slide_note),
                    checked = navSlide,
                    onCheckedChange = { on -> Settings.setNavSlide(context, on) },
                )
                SwitchRow(
                    title = stringResource(R.string.nav_icon_motion),
                    subtitle = stringResource(R.string.nav_icon_motion_note),
                    checked = navIconMotion,
                    onCheckedChange = { on -> Settings.setNavIconMotion(context, on) },
                )
            }
        }

        // The reference's first card: the five icons themselves, dragged into a new order. The strip
        // behind the settings sheet is live, so a drop is visible without leaving this screen.
        item {
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
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                SettingsCardRow(
                    title = stringResource(R.string.nav_reorder_reset),
                    value = stringResource(R.string.nav_restore),
                    onClick = { Settings.resetNavOrder(context) },
                )
            }
        }

        // The reference's second card. The chip under the switch reports the engine that actually
        // runs, and the rows below it let the choice be made — including on a phone where asking for
        // the shader engine resolves to the drawn one, which is why the chip is not the selection.
        item {
            SettingsCard(
                title = stringResource(R.string.nav_group_glass),
                note = stringResource(R.string.nav_glass_note),
            ) {
                SwitchRow(
                    title = stringResource(R.string.nav_glass),
                    subtitle = "",
                    checked = glass.liquid,
                    onCheckedChange = { on -> Settings.setGlass(context) { it.copy(liquid = on) } },
                )
                val running = glassEngineRuns(glass.engine)
                Text(
                    stringResource(R.string.nav_engine, stringResource(running.label)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                // The chip above reports which engine draws; this group is where one is chosen, and it
                // wears the same small label as the two groups below it so the card reads as three parts.
                SettingsCardLabel(stringResource(R.string.nav_engine_group))
                GlassEngine.entries.forEach { engine ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = glass.engine == engine,
                                role = Role.RadioButton,
                                onClick = { Settings.setGlass(context) { it.copy(engine = engine) } },
                            )
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = glass.engine == engine, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(engine.label), style = MaterialTheme.typography.bodyMedium)
                    }
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

                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
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


        item {
            Text(
                stringResource(R.string.settings_section_security),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            )
        }
        item {
            SettingsRow(
                title = stringResource(R.string.settings_install_unknown),
                subtitle = stringResource(
                    if (canInstall) R.string.settings_install_allowed else R.string.settings_install_needed
                ),
                value = stringResource(if (canInstall) R.string.settings_allowed else R.string.action_allow),
                onClick = {
                    Installer.openUnknownSourcesSettings(context)
                },
            )
        }
        item {
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
        }
        item {
            InfoCard {
                InfoRow(
                    Glyph.Shield,
                    stringResource(R.string.settings_why_switch),
                    subtitle = stringResource(R.string.settings_why_switch_body),
                )
            }
        }

        item {
            Text(
                stringResource(R.string.settings_section_sources),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            )
        }
        item {
            Text(
                stringResource(R.string.settings_sources_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        itemsIndexed(SourceId.entries.filter { it != SourceId.LITEAPKS }) { position, source ->
            SwitchRow(
                title = source.label,
                subtitle = stringResource(source.note),
                checked = source.name in enabledSources,
                modifier = Modifier.playEnter(Unit, position),
                onCheckedChange = { Settings.setSourceEnabled(context, source, it) },
            )
        }
        item {
            SettingsRow(
                title = stringResource(R.string.settings_default_source),
                subtitle = stringResource(R.string.settings_default_source_note),
                value = defaultSource?.label ?: stringResource(R.string.settings_source_store),
                onClick = { sourceDialog = true },
            )
        }

        item {
            Text(
                stringResource(R.string.settings_section_storage),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            )
        }
        item {
            SettingsRow(
                title = stringResource(R.string.settings_downloaded_packages),
                subtitle = stringResource(R.string.settings_queue_summary, downloads.size, humanSize(cachedBytes)),
                value = stringResource(R.string.settings_clear_finished),
                onClick = { DownloadEngine.clearFinished(context) },
            )
        }
        item {
            SettingsRow(
                title = stringResource(R.string.settings_save_apks),
                subtitle = stringResource(R.string.settings_save_apks_note),
                value = null,
                onClick = null,
            )
        }

        item {
            Text(
                stringResource(R.string.settings_section_about),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.settings_about_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

/**
 * A radio list for one persisted enum.
 *
 * Three settings rows now need exactly this, and the auto-update chooser had grown its own copy of
 * the whole dialog — a fourth would have been the point where someone "forgets" to move the
 * `RadioButton` semantics or the dismiss-on-pick into one of them.
 */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    noteOf: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // Three options with a note each are taller than the dialog at a 2.0× text size; the
            // scroll keeps the last option selectable instead of clipped by the dialog's edge.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(option) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(labelOf(option), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                noteOf(option),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String?,
    value: String?,
    onClick: (() -> Unit)?,
) {
    // The value is the state the row carries, so it is swapped rather than repainted in place.
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().playClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    }
    // A settings row is a line of title and a value on the right, but that line needs two columns to
    // be worth it. Measured on the Redmi at 2.0×, *Auto-update apps* plus *Don't auto-update apps* is
    // 783 px of a 992 px row before the title has had a pixel; splitting the row then cut the value
    // at "auto-u…" and wrapped the title one word per line. Past 1.4× the row stacks instead: the
    // title keeps the full width and the value sits under it, right-aligned like a trailing value.
    val stacked = LocalDensity.current.fontScale > 1.4f

    Column {
        if (stacked) {
            Column(rowModifier) {
                SettingsTitle(title, subtitle)
                if (value != null) {
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = value,
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = { settingValueTransition() },
                        label = "setting-value",
                    ) { text ->
                        Text(
                            text,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        } else {
        Row(
            rowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                SettingsTitle(title, subtitle)
            }
            if (value != null) {
                Spacer(Modifier.width(12.dp))
                // The value used to be measured at its full width before the title's weight, so at a
                // 2.0× text size *Don't auto-update apps* claimed 783 of the row's 992 px and left
                // the title a 176 px column that wrapped one syllable per line. The weight keeps the
                // value from claiming the row; the title gets the other half, which at the default
                // size is more than either needs.
                AnimatedContent(
                    targetState = value,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = fadeIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedEasing)),
                            initialContentExit =
                                fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)),
                        )
                    },
                    label = "setting-value",
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        // Two lines, not one: at the default size every value here is short enough
                        // for one, but at 2.0× *Don't auto-update apps* is 783 px against a 446 px
                        // column, and a single line said only "Don't a…". The value is the row's
                        // state; it has to stay readable even when it has to wrap.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The title and its note, which both layouts draw the same way. */
@Composable
private fun SettingsTitle(title: String, subtitle: String?) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    if (!subtitle.isNullOrBlank()) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The cross-fade a value uses when it changes, shared by both row layouts. */
private fun settingValueTransition(): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedEasing)),
    initialContentExit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)),
)

/** A preference with one switch, so a source can be turned off without leaving the app. */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .playClickable(onClick = { onCheckedChange(!checked) })
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * One slider and the field of [GlassStyle] it writes.
 *
 * [display] exists because not every fraction means its own percentage: Bar width maps 0..1 onto
 * 60..150 % of the content width, and printing the raw fraction there would say the bar is 0 % wide.
 */
private class GlassKnob(
    @StringRes val label: Int,
    val value: Float,
    val display: (Float) -> Int = { (it * 100).roundToInt() },
    val set: (GlassStyle, Float) -> GlassStyle,
)

/**
 * One glass parameter, dragged with the bar on screen reacting as it moves.
 *
 * Every parameter is a 0..1 fraction of its own range, so the four rows are one shape and the same
 * numbers mean the same look at any size.
 */
@Composable
private fun GlassSliderRow(
    title: String,
    value: Float,
    display: String,
    onDrag: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                display,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        SlimSlider(value = value, description = title, onDrag = onDrag, onDone = onDone)
    }
}

/**
 * The slider the glass rows use.
 *
 * Material's slider draws a 40 dp handle, which beside four lines of 14 sp labels dominated the card
 * and made the four controls read as one tall column of bars. This is a 6 dp track with a 18 dp handle:
 * still a full-height drag target, but the card's rhythm belongs to the labels again. It is built here
 * rather than themed because the height, the handle and the lack of gaps are all part of the row.
 */
@Composable
private fun SlimSlider(
    value: Float,
    description: String,
    onDrag: (Float) -> Unit,
    onDone: () -> Unit,
) {
    var width by remember { mutableIntStateOf(0) }
    val fraction = value.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val active = MaterialTheme.colorScheme.primary
    val halo = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { width = it.width }
            .pointerInput(width) {
                if (width <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onDrag((offset.x / width).coerceIn(0f, 1f)) },
                    onDragEnd = onDone,
                    onDragCancel = onDone,
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onDrag((change.position.x / width).coerceIn(0f, 1f))
                    },
                )
            }
            .pointerInput(width) {
                if (width <= 0) return@pointerInput
                detectTapGestures { offset ->
                    onDrag((offset.x / width).coerceIn(0f, 1f))
                    onDone()
                }
            }
            // The visible title is a sibling, not a label, so a screen reader reaches the track with no
            // idea which of the four parameters it is holding.
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackHeight = 6.dp.toPx()
            val centerY = size.height / 2f
            val radius = CornerRadius(trackHeight / 2f)
            drawRoundRect(
                color = track,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = radius,
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = active,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width * fraction, trackHeight),
                    cornerRadius = radius,
                )
            }
            val handle = size.width * fraction
            drawCircle(halo, radius = 11.dp.toPx(), center = Offset(handle, centerY))
            drawCircle(active, radius = 8.dp.toPx(), center = Offset(handle, centerY))
        }
    }
}

/** The heading above a stack of cards, cut the way the reference cuts its section names. */
@Composable
private fun SettingsCardTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.6.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
    )
}

/**
 * A settings card: what the reference's Themes & UI screen is made of, and what the navigation
 * section needed.
 *
 * The section used to be one continuous list in which a heading was the only thing separating
 * reordering from glass effects; on a phone the two read as one long form. A card is the boundary, and
 * its inset margin is the only space the list needs between groups.
 */
@Composable
private fun SettingsCard(
    title: String? = null,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (title != null) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 2.dp)) {
                    Text(
                        title.uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.1.sp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (note != null) {
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            content()
        }
    }
}

/** A sub-heading inside a card: GLASS EFFECTS, SHAPE & LAYOUT. */
@Composable
private fun SettingsCardLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.0.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 2.dp),
    )
}

/** A row that ends a card: a title and the one action it offers, on the card's own padding. */
@Composable
private fun SettingsCardRow(
    title: String,
    value: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) = SettingsRow(title = title, subtitle = subtitle, value = value, onClick = onClick)

/**
 * The five tabs, in their current order, dragged sideways to move one.
 *
 * Deliberately not a live-shifting list: the chip follows the finger, and on release the strip is
 * permuted. A drag that ends between two slots resolves to the nearest one, clamped to the row, so a
 * finger that leaves the five at either end cannot ask for a sixth tab.
 */
@Composable
private fun NavOrderRow(order: List<Int>, onMove: (from: Int, to: Int) -> Unit) {
    var width by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val cell = if (order.isEmpty() || width == 0) 0f else width / order.size.toFloat()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .onSizeChanged { width = it.width },
    ) {
        order.forEachIndexed { position, screen ->
            val lifted = dragging == position
            Box(
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = if (lifted) dragX else 0f
                        val scale = if (lifted) 1.1f else 1f
                        scaleX = scale
                        scaleY = scale
                    }
                    // `cell` is in the key: it is 0 until the row is measured, and a lambda
                    // captured before that would never reorder anything.
                    .pointerInput(order, cell) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragging = position
                                dragX = 0f
                            },
                            onDragEnd = {
                                if (cell > 0f) {
                                    val target = (position + (dragX / cell).roundToInt())
                                        .coerceIn(0, order.lastIndex)
                                    if (target != position) onMove(position, target)
                                }
                                dragging = null
                                dragX = 0f
                            },
                            onDragCancel = {
                                dragging = null
                                dragX = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragX += amount.x
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                if (lifted) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(
                            NAV_TABS[screen].glyph,
                            size = 22.dp,
                            tint = if (lifted) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(NAV_TABS[screen].label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
