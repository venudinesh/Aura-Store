package dev.aurastore.store.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.annotation.StringRes
import dev.aurastore.store.R
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.aurastore.store.data.sources.SourceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Play's three auto-update choices, kept in the same wording.
 *
 * The wording is a resource, not a field: a setting's name has to read in the language the phone is
 * in, and nothing here is compared against text from the catalogue.
 */
enum class AutoUpdateMode(@StringRes val label: Int, @StringRes val description: Int) {
    OFF(R.string.auto_update_off, R.string.auto_update_off_note),
    WIFI(R.string.auto_update_wifi, R.string.auto_update_wifi_note),
    ANY(R.string.auto_update_any, R.string.auto_update_any_note),
}

/**
 * Which of the three schemes to draw in.
 *
 * [SYSTEM] is the default because it is what every other app promises; the two fixed modes exist
 * because the storefront's dark scheme is the one it was designed in and some people want it
 * regardless of the hour.
 */
enum class ThemeMode(@StringRes val label: Int, @StringRes val description: Int) {
    SYSTEM(R.string.theme_system, R.string.theme_system_note),
    LIGHT(R.string.theme_light, R.string.theme_light_note),
    DARK(R.string.theme_dark, R.string.theme_dark_note),
}

/**
 * Where the colours come from.
 *
 * [WALLPAPER] is Material's dynamic colour, and it is the setting that undid this app's own palette
 * for its first twenty-eight rounds — the house colours existed in `Color.kt` and were never
 * rendered on a modern handset. It is offered rather than imposed: some people want the accent to
 * match their launcher. On anything below Android 12 the platform has no dynamic palette, so this
 * choice resolves to [HOUSE] there and says so in its own note.
 *
 * [CUSTOM] is the same idea with the user as the wallpaper: one seed colour, and
 * [dev.aurastore.store.theme.monetScheme] derives the whole scheme from it. It works on every supported
 * version because the arithmetic is ours, not the platform's.
 */
enum class PaletteMode(@StringRes val label: Int, @StringRes val description: Int) {
    HOUSE(R.string.palette_house, R.string.palette_house_note),
    WALLPAPER(R.string.palette_wallpaper, R.string.palette_wallpaper_note),
    CUSTOM(R.string.palette_custom, R.string.palette_custom_note),
}

/** Whether this handset can honour [PaletteMode.WALLPAPER] at all. */
fun dynamicPaletteSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * What a fresh install chooses before anyone has been asked.
 *
 * The device decides, which is what the platform's own apps do: a Material You handset opens in its
 * own colours, an older one opens in the house palette. It is a *starting* value only — the first
 * move of the Colours row writes a preference and this answer is never consulted again.
 */
fun firstRunPalette(): PaletteMode = if (dynamicPaletteSupported()) PaletteMode.WALLPAPER else PaletteMode.HOUSE

/**
 * Which of the reference's three glass engines draws the bar.
 *
 * [IOS_13] is the real thing: an AGSL shader that bends the sampled page at the rim, splits its colour
 * and magnifies a lens, and it needs [android.os.Build.VERSION_CODES.TIRAMISU] for `RuntimeShader`.
 * [IOS_8] is the same look drawn in Compose — a magnified copy and a lit band — and runs on every
 * supported release. [CLASSIC] keeps the blur and the rim but none of the lens.
 *
 * The reference picks between these itself; this app lets the choice be made and then reports the one
 * that actually runs, because a shader cannot run on a release that has no shader API.
 */
enum class GlassEngine(@StringRes val label: Int, val needsShader: Boolean) {
    IOS_13(R.string.glass_engine_ios13, true),
    IOS_8(R.string.glass_engine_ios8, false),
    CLASSIC(R.string.glass_engine_classic, false),
}

/**
 * The engine that will really draw this bar on [sdk].
 *
 * Asking for the shader engine on a release without `RuntimeShader` resolves to the drawn iOS engine
 * rather than silently falling back to something the user did not choose. [sdk] is a parameter so the
 * resolution can be asserted off the handset.
 */
fun glassEngineRuns(engine: GlassEngine, sdk: Int = Build.VERSION.SDK_INT): GlassEngine =
    if (engine.needsShader && sdk < Build.VERSION_CODES.TIRAMISU) GlassEngine.IOS_8 else engine

/**
 * The floating navigation bar and the glass it is made of.
 *
 * The field set mirrors the reference in `docs/NAVBAR_SETTINGS.md`: the switch, the engine, four glass
 * effects and three shape values. Every number is a 0..1 fraction of its own range, so the same value
 * means the same look at any screen size and density.
 */
data class GlassStyle(
    val floating: Boolean,
    val liquid: Boolean,
    /** Which renderer the user asked for; [glassEngineRuns] says which one this phone can run. */
    val engine: GlassEngine,
    /** 0..1 of the backdrop blur. Zero draws no copy of the page at all. */
    val blur: Float,
    /** 0..1 of a red/cyan fringe on the sheet's edge. */
    val chromatic: Float,
    /** 0..1 of the magnification and sheen that stand in for refraction. */
    val refraction: Float,
    /** 0..1 of how far down the sheet that lens reaches. */
    val depth: Float,
    /** 0..1 of the corner radius. */
    val corner: Float,
    /** The widest the sheet may be, 0..1 of 60..150 % of the content width. It is a cap: the
     *  sheet itself is as wide as its five labels need. */
    val width: Float,
    /** 0..1 of the gap under the sheet, 0..24 dp. */
    val offset: Float,
)

/**
 * The install-time style: a bar that paints nothing at all.
 *
 * The bar lives in a window of its own over the activity, and a popup window is transparent where it
 * paints nothing — so at zero the page behind the tabs is not a copy, a blur or an approximation, it
 * is the page, at its own sharpness, scrolling under the labels. That is the shape the bar ships in:
 * every material slider starts at zero, and raising one adds that layer back.
 *
 * The selection pill is part of the tab strip rather than of the sheet, so it is unaffected: the bar
 * still shows where you are without painting a background across the page.
 */
fun defaultGlassStyle(): GlassStyle = GlassStyle(
    floating = true,
    liquid = true,
    engine = GlassEngine.IOS_13,
    blur = 0f,
    chromatic = 0f,
    refraction = 0f,
    depth = 0f,
    corner = 1f,
    width = (1f - 0.6f) / 0.9f,
    offset = 0f,
)

/**
 * The shipped material with the caller's shape choices kept.
 *
 * *Reset all glass settings* restores a look, not a layout: someone who chose the flush bar should not
 * lose it as a side effect of getting the sliders back to their shipped values. The enable switch and
 * the engine are choices about the bar rather than about the material, so they stay too.
 */
fun GlassStyle.withDefaultMaterial(): GlassStyle {
    val d = defaultGlassStyle()
    return copy(
        blur = d.blur,
        chromatic = d.chromatic,
        refraction = d.refraction,
        depth = d.depth,
        corner = d.corner,
        width = d.width,
        offset = d.offset,
    )
}

/**
 * Small, explicit preference store. Only what the storefront actually acts on lives here — there is
 * no hidden configuration, and every value has a visible control in Settings.
 */
object Settings {

    private const val PREFS = "aurastore_settings"
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_SOURCES = "enabled_sources"
    private const val KEY_NOTIF_ASKED = "notifications_asked"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_PALETTE = "palette"
    private const val KEY_PALETTE_SEED = "palette_seed"
    private const val KEY_GLASS_FLOATING = "glass_floating"
    private const val KEY_GLASS_LIQUID = "glass_liquid"
    private const val KEY_GLASS_ENGINE = "glass_engine"
    private const val KEY_GLASS_BLUR = "glass_blur"
    private const val KEY_GLASS_CHROMATIC = "glass_chromatic"
    private const val KEY_GLASS_REFRACTION = "glass_refraction"
    private const val KEY_GLASS_DEPTH = "glass_depth"
    private const val KEY_GLASS_CORNER = "glass_corner"
    private const val KEY_GLASS_WIDTH = "glass_width"
    private const val KEY_GLASS_OFFSET = "glass_offset"
    private const val KEY_GLASS_VERSION = "glass_version"
    private const val KEY_GLASS_SHAPE = "glass_shape_version"
    private const val KEY_NAV_ORDER = "nav_order"
    private const val KEY_NAV_SLIDE = "nav_slide"
    private const val KEY_NAV_ICON_MOTION = "nav_icon_motion"
    private const val KEY_IGNORED_UPDATES = "ignored_updates"
    private const val KEY_DEFAULT_SOURCE = "default_source"

    private val _autoUpdate = MutableStateFlow(AutoUpdateMode.WIFI)
    val autoUpdate: StateFlow<AutoUpdateMode> = _autoUpdate.asStateFlow()

    /**
     * The source a download is taken from when the user has not chosen another one.
     *
     * Null means "no preference": the store's own catalogue is the default, which is what the app did
     * before there was a choice to make. The value is the source's own id, so a source that is later
     * switched off in [sources] simply stops being offered — the preference is not deleted for the
     * user, it is just not among the answers.
     */
    private val _defaultSource = MutableStateFlow<SourceId?>(null)
    val defaultSource: StateFlow<SourceId?> = _defaultSource.asStateFlow()

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    /**
     * Whether the system has already been asked for `POST_NOTIFICATIONS`. An unanswered dialog is not a
     * refusal, but a refusal is permanent until the user goes to Settings, and Android shows whichever
     * it is once per launch if asked again — covering the first screen and eating the first tap.
     */
    /**
     * The bar's two motions, as choices rather than constants.
     *
     * Both default on: a bar that jumps between tabs and swaps icons without a gesture reads as a
     * different app from the rest of this one, where every change is animated. But motion is an
     * accessibility setting on a phone too, and a store has no business insisting on its own.
     */
    private val _navSlide = MutableStateFlow(true)
    val navSlide: StateFlow<Boolean> = _navSlide.asStateFlow()

    private val _navIconMotion = MutableStateFlow(true)
    val navIconMotion: StateFlow<Boolean> = _navIconMotion.asStateFlow()

    private val _notificationsAsked = MutableStateFlow(false)
    val notificationsAsked: StateFlow<Boolean> = _notificationsAsked.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _palette = MutableStateFlow(firstRunPalette())
    val palette: StateFlow<PaletteMode> = _palette.asStateFlow()

    /**
     * The seed behind [PaletteMode.CUSTOM], as ARGB, or null while nobody has chosen one.
     *
     * Null rather than a default colour so that this file stays free of the theme layer — the theme
     * decides what "custom with nothing picked yet" looks like, and it is the house amber.
     */
    private val _paletteSeed = MutableStateFlow<Int?>(null)
    val paletteSeed: StateFlow<Int?> = _paletteSeed.asStateFlow()

    private val _glass = MutableStateFlow(defaultGlassStyle())
    val glass: StateFlow<GlassStyle> = _glass.asStateFlow()

    /** The five tabs in the order the bar draws them; screen indices, never positions. */
    private val _navOrder = MutableStateFlow(NavOrder.DEFAULT)
    val navOrder: StateFlow<List<Int>> = _navOrder.asStateFlow()

    /**
     * The source names the user allows, as the comma-separated pref that was written last. Nothing
     * is stored until a switch is moved, so a fresh install means "every source" rather than an
     * empty set that a caller would have to second-guess.
     */
    private val _sources = MutableStateFlow(SourceId.entries.map { it.name }.toSet())
    val sources: StateFlow<Set<String>> = _sources.asStateFlow()

    /**
     * The meaning of the stored material values.
     *
     * 3 is the reference-shaped model in `docs/NAVBAR_SETTINGS.md`. Stored values from an earlier shape
     * of this object were tuned against controls that no longer exist, so they are discarded once — the
     * shape toggles are not, because they are still the user's choice.
     */
    private const val GLASS_VERSION = 3

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _autoUpdate.value = runCatching {
            AutoUpdateMode.valueOf(prefs.getString(KEY_AUTO_UPDATE, null) ?: AutoUpdateMode.WIFI.name)
        }.getOrDefault(AutoUpdateMode.WIFI)
        _lastSync.value = prefs.getLong(KEY_LAST_SYNC, 0L)
        _notificationsAsked.value = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        _themeMode.value = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        _palette.value = runCatching {
            PaletteMode.valueOf(prefs.getString(KEY_PALETTE, null) ?: firstRunPalette().name)
        }.getOrDefault(firstRunPalette())
        _paletteSeed.value = if (prefs.contains(KEY_PALETTE_SEED)) prefs.getInt(KEY_PALETTE_SEED, 0) else null
        val d = defaultGlassStyle()
        // The material changed meaning with the control set: an old 0.69 tint was a different slider
        // from today's Blur intensity and would put a sheet over the page the bar exists to show. Drop
        // those once, keep the two shape choices, which are still the user's.
        val material = prefs.getInt(KEY_GLASS_VERSION, 0) >= GLASS_VERSION
        fun kept(key: String, fallback: Float) =
            if (material) prefs.getFloat(key, fallback) else fallback
        _glass.value = GlassStyle(
            floating = prefs.getBoolean(KEY_GLASS_FLOATING, d.floating),
            liquid = prefs.getBoolean(KEY_GLASS_LIQUID, d.liquid),
            engine = runCatching {
                GlassEngine.valueOf(prefs.getString(KEY_GLASS_ENGINE, null) ?: d.engine.name)
            }.getOrDefault(d.engine),
            blur = kept(KEY_GLASS_BLUR, d.blur),
            chromatic = kept(KEY_GLASS_CHROMATIC, d.chromatic),
            refraction = kept(KEY_GLASS_REFRACTION, d.refraction),
            depth = kept(KEY_GLASS_DEPTH, d.depth),
            corner = kept(KEY_GLASS_CORNER, d.corner),
            width = kept(KEY_GLASS_WIDTH, d.width),
            offset = kept(KEY_GLASS_OFFSET, d.offset),
        )
        // The shape the bar shipped in until now was square and edge to edge, which read as an old
        // system navigation bar rather than as an object floating over the page. One quiet correction
        // for anyone still on that look: the material values above are left untouched, and a corner
        // that has been chosen since is never overridden again.
        if (prefs.getInt(KEY_GLASS_SHAPE, 0) < 1) {
            if (_glass.value.corner == 0f) _glass.value = _glass.value.copy(corner = 0.55f)
            prefs.edit { putInt(KEY_GLASS_SHAPE, 1) }
        }
        _navOrder.value = NavOrder.sanitize(prefs.getString(KEY_NAV_ORDER, null))
        _navSlide.value = prefs.getBoolean(KEY_NAV_SLIDE, true)
        _navIconMotion.value = prefs.getBoolean(KEY_NAV_ICON_MOTION, true)
        _ignoredUpdates.value = decodeIgnored(prefs.getString(KEY_IGNORED_UPDATES, null))
        prefs.getString(KEY_SOURCES, null)?.let { stored ->
            _sources.value = stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
        prefs.getString(KEY_DEFAULT_SOURCE, null)?.let { stored ->
            _defaultSource.value = runCatching { SourceId.valueOf(stored) }.getOrNull()
        }
    }

    /**
     * Sets the source a download is taken from when the user has not chosen another one.
     *
     * Null is a choice too: it means the store's own catalogue, and it is what the row shows when no
     * source has been named.
     */
    fun setDefaultSource(context: Context, source: SourceId?) {
        _defaultSource.value = source
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (source == null) remove(KEY_DEFAULT_SOURCE) else putString(KEY_DEFAULT_SOURCE, source.name)
        }
    }

    fun markNotificationsAsked(context: Context) {
        if (_notificationsAsked.value) return
        _notificationsAsked.value = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_NOTIF_ASKED, true)
        }
    }

    fun setSourceEnabled(context: Context, id: SourceId, enabled: Boolean) {
        val next = _sources.value.toMutableSet()
        if (enabled) next += id.name else next -= id.name
        _sources.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_SOURCES, next.joinToString(","))
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_THEME_MODE, mode.name)
        }
    }

    /** Live preview while the wheel is being dragged. A drag is sixty samples a second; the
     *  preference file is written once, when the picker closes. */
    fun showPaletteSeed(argb: Int) {
        _paletteSeed.value = argb
    }

    /** Live preview while a glass slider is dragged. Same reason as [showPaletteSeed]: the flow is
     *  the preview surface, the file is written once when the finger lifts. */
    fun showGlass(transform: (GlassStyle) -> GlassStyle) {
        _glass.value = transform(_glass.value)
    }

    fun commitGlass(context: Context) {
        val next = _glass.value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_GLASS_FLOATING, next.floating)
            putBoolean(KEY_GLASS_LIQUID, next.liquid)
            putString(KEY_GLASS_ENGINE, next.engine.name)
            putInt(KEY_GLASS_VERSION, GLASS_VERSION)
            putFloat(KEY_GLASS_BLUR, next.blur)
            putFloat(KEY_GLASS_CHROMATIC, next.chromatic)
            putFloat(KEY_GLASS_REFRACTION, next.refraction)
            putFloat(KEY_GLASS_DEPTH, next.depth)
            putFloat(KEY_GLASS_CORNER, next.corner)
            putFloat(KEY_GLASS_WIDTH, next.width)
            putFloat(KEY_GLASS_OFFSET, next.offset)
        }
    }

    /** Commit a reordered strip. The list is sanitised first, so a buggy drag cannot lose a tab. */
    fun setNavOrder(context: Context, order: List<Int>) {
        val next = NavOrder.sanitize(order)
        _navOrder.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_NAV_ORDER, NavOrder.join(next))
        }
    }

    fun resetNavOrder(context: Context) { setNavOrder(context, NavOrder.DEFAULT) }

    /** Whether the selection pill travels between tabs, or jumps straight to the new one. */
    fun setNavSlide(context: Context, on: Boolean) {
        _navSlide.value = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_NAV_SLIDE, on)
        }
    }

    /** Whether a selected tab's glyph pops and solidifies, or changes state with no motion. */
    fun setNavIconMotion(context: Context, on: Boolean) {
        _navIconMotion.value = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_NAV_ICON_MOTION, on)
        }
    }

    fun setGlass(context: Context, transform: (GlassStyle) -> GlassStyle) {
        showGlass(transform)
        commitGlass(context)
    }

    fun setPaletteSeed(context: Context, argb: Int) {
        _paletteSeed.value = argb
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_PALETTE_SEED, argb)
        }
    }

    fun setPalette(context: Context, palette: PaletteMode) {
        _palette.value = palette
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_PALETTE, palette.name)
        }
    }

    fun setAutoUpdate(context: Context, mode: AutoUpdateMode) {
        _autoUpdate.value = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_AUTO_UPDATE, mode.name)
        }
    }

    fun markSynced(context: Context, at: Long = System.currentTimeMillis()) {
        _lastSync.value = at
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_SYNC, at)
        }
    }

    /**
     * Updates the user has told this store to stop offering, as `packageName -> the version ignored`.
     *
     * The version is part of the key on purpose. "Ignore this update" means *this* build, not the app
     * forever: when the catalogue moves past the version that was dismissed, the row comes back on its
     * own, which is what every store does and what makes the action safe to offer at all. A package
     * whose entry is missing has never been ignored.
     */
    private val _ignoredUpdates = MutableStateFlow<Map<String, String>>(emptyMap())
    val ignoredUpdates: StateFlow<Map<String, String>> = _ignoredUpdates.asStateFlow()

    /**
     * Records that [version] of [packageName] is not to be offered again.
     *
     * A blank version is not stored: it would mean "ignore every version of this app", and the whole
     * point of the entry is that it expires by itself when a newer build appears.
     */
    fun ignoreUpdate(context: Context, packageName: String, version: String?) {
        val label = version?.trim().orEmpty()
        if (packageName.isBlank() || label.isEmpty()) return
        val updated = _ignoredUpdates.updateAndGet { it + (packageName to label) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_IGNORED_UPDATES, encodeIgnored(updated))
        }
    }

    /** Puts an ignored app back in the update list, whatever version it was dismissed at. */
    fun unignoreUpdate(context: Context, packageName: String) {
        if (!_ignoredUpdates.value.containsKey(packageName)) return
        val updated = _ignoredUpdates.updateAndGet { it - packageName }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_IGNORED_UPDATES, encodeIgnored(updated))
        }
    }

    /** Forgets every ignored update, so the next sweep offers all of them again. */
    fun clearIgnoredUpdates(context: Context) {
        if (_ignoredUpdates.value.isEmpty()) return
        _ignoredUpdates.value = emptyMap()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove(KEY_IGNORED_UPDATES)
        }
    }

    /**
     * Whether [version] of [packageName] is one the user dismissed.
     *
     * Compared as the label the row was drawn from, because that is the string the user saw when they
     * tapped Ignore. A different label is a different build, and is offered.
     */
    fun isUpdateIgnored(packageName: String, version: String?): Boolean {
        val ignored = _ignoredUpdates.value[packageName] ?: return false
        return ignored == version?.trim().orEmpty()
    }

    /** `pkg=version` pairs, newline separated. Package names cannot contain `=` or a newline. */
    private fun encodeIgnored(map: Map<String, String>): String =
        map.entries.joinToString("\n") { "${it.key}=${it.value}" }

    private fun decodeIgnored(stored: String?): Map<String, String> = stored
        ?.lineSequence()
        ?.mapNotNull { line ->
            val at = line.indexOf('=')
            if (at <= 0) null else line.substring(0, at) to line.substring(at + 1)
        }
        ?.filter { it.second.isNotBlank() }
        ?.toMap()
        .orEmpty()

    /** Whether downloads are allowed right now under [mode]. */
    fun allowedNow(context: Context, mode: AutoUpdateMode): Boolean = when (mode) {
        AutoUpdateMode.OFF -> false
        AutoUpdateMode.ANY -> true
        AutoUpdateMode.WIFI -> onWifi(context)
    }

    fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
