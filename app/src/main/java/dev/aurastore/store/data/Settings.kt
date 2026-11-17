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
import kotlin.math.abs

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
 * Which published build of Aura Store an update should offer.
 *
 * [AUTO] follows the signature of the copy on the phone, which is the only build Android can replace
 * it with. The two explicit choices exist because the release publishes a debug build beside the
 * signed one and someone testing the store may want that file; a build whose signature does not match
 * is saved instead of installed, because the platform refuses it either way.
 */
enum class UpdateBuild(@StringRes val label: Int, @StringRes val description: Int) {
    AUTO(R.string.update_build_auto, R.string.update_build_auto_note),
    RELEASE(R.string.update_build_release, R.string.update_build_release_note),
    DEBUG(R.string.update_build_debug, R.string.update_build_debug_note),
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
 * How the floating download panel draws a transfer in progress.
 *
 * The panel is the one surface that sits over the page for the whole length of a download, so its
 * meter is the app's only long-running animation. Twenty-four styles, one chosen at a time: the first
 * twelve are the standard set, in the theme's colours, and the last twelve are the limited editions,
 * each with its own light. The first remains the shipped wash, so an install that never chooses keeps
 * the default it already had. Every style falls back to a still fill when the transfer is paused or
 * finished, and to a single frame when the phone has motion turned off — the choice changes the look,
 * never whether the state can be read.
 */
enum class DockAnimation(
    @StringRes val label: Int,
    @StringRes val description: Int,
    /** True for the limited-edition group shown on its own card in the picker. */
    val limited: Boolean = false,
) {
    WASH(R.string.dock_anim_wash, R.string.dock_anim_wash_note),
    TIDE(R.string.dock_anim_tide, R.string.dock_anim_tide_note),
    AURORA(R.string.dock_anim_aurora, R.string.dock_anim_aurora_note),
    SCAN(R.string.dock_anim_scan, R.string.dock_anim_scan_note),
    COMET(R.string.dock_anim_comet, R.string.dock_anim_comet_note),
    GLOW(R.string.dock_anim_glow, R.string.dock_anim_glow_note),
    RAIN(R.string.dock_anim_rain, R.string.dock_anim_rain_note),
    BUBBLES(R.string.dock_anim_bubbles, R.string.dock_anim_bubbles_note),
    NEON(R.string.dock_anim_neon, R.string.dock_anim_neon_note),
    PULSE(R.string.dock_anim_pulse, R.string.dock_anim_pulse_note),
    MATRIX(R.string.dock_anim_matrix, R.string.dock_anim_matrix_note),
    CHASE(R.string.dock_anim_chase, R.string.dock_anim_chase_note),
    KAMEHAMEHA(R.string.dock_anim_kamehameha, R.string.dock_anim_kamehameha_note, limited = true),
    TSUNAMI(R.string.dock_anim_tsunami, R.string.dock_anim_tsunami_note, limited = true),
    FIREBALL(R.string.dock_anim_fireball, R.string.dock_anim_fireball_note, limited = true),
    BOULDER(R.string.dock_anim_boulder, R.string.dock_anim_boulder_note, limited = true),
    BULLET(R.string.dock_anim_bullet, R.string.dock_anim_bullet_note, limited = true),
    ARROW(R.string.dock_anim_arrow, R.string.dock_anim_arrow_note, limited = true),
    LASER(R.string.dock_anim_laser, R.string.dock_anim_laser_note, limited = true),
    THUNDER(R.string.dock_anim_thunder, R.string.dock_anim_thunder_note, limited = true),
    COUNTING(R.string.dock_anim_counting, R.string.dock_anim_counting_note, limited = true),
    FLASH(R.string.dock_anim_flash, R.string.dock_anim_flash_note, limited = true),
    WHEEL(R.string.dock_anim_wheel, R.string.dock_anim_wheel_note, limited = true),
    NOS(R.string.dock_anim_nos, R.string.dock_anim_nos_note, limited = true),
}

/**
 * The animated wallpaper behind the store.
 *
 * The snow and the fireworks are borrowed in spirit from the open-source Telegram clients, where a
 * chat can wear falling flakes or a burst of sparks over its background. The maths here is new and
 * carried by the clock rather than by a flock of objects: every particle is a function of its index
 * and the time, so a rotation redraws the same scene instead of restarting it and the picker's still
 * tiles are the same renderer at a fixed moment.
 *
 * [NONE] is the shipped page: an install that never opens this setting keeps exactly the surface it
 * had, and the backdrop itself is not composed at all.
 */
enum class BackgroundTheme(@StringRes val label: Int, @StringRes val description: Int) {
    NONE(R.string.background_none, R.string.background_none_note),
    SNOW(R.string.background_snow, R.string.background_snow_note),
    FIREWORKS(R.string.background_fireworks, R.string.background_fireworks_note),
    MOTION(R.string.background_motion, R.string.background_motion_note),
}

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
    width = DEFAULT_GLASS_WIDTH,
    offset = 0f,
)

/**
 * The shipped bar width, as the 0..1 position of the slider that draws it.
 *
 * 116 %, not 100 %: at the measured pill the five labels already sat at the ends, and the bar read
 * narrower than the content it floats over. The extra sixth widens the pill without asking the page
 * for room it does not have.
 */
const val DEFAULT_GLASS_WIDTH = (1.16f - 0.6f) / 0.9f

/** The width the bar shipped at before [DEFAULT_GLASS_WIDTH]: 100 % of the content. */
private const val LEGACY_GLASS_WIDTH = (1f - 0.6f) / 0.9f

/**
 * The width to keep, adopting the shipped 116 % for an install still sitting on the old 100 %.
 *
 * A width someone dragged is theirs and is never touched. A width identical to the previous default
 * is indistinguishable from "never moved", so it moves once with the default; [migrated] records
 * that it has, and a later choice of 100 % stays 100 %.
 */
internal fun adoptDefaultGlassWidth(stored: Float, migrated: Boolean): Float =
    if (!migrated && abs(stored - LEGACY_GLASS_WIDTH) < 0.0001f) {
        DEFAULT_GLASS_WIDTH
    } else {
        stored
    }

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
    private const val KEY_TOUR_SEEN = "tour_seen"
    private const val KEY_AVATAR = "profile_avatar"
    private const val KEY_AVATAR_FRAME = "profile_avatar_frame"
    private const val KEY_COVER_STYLE = "profile_cover_style"
    private const val KEY_COVER_CUSTOM = "profile_cover_custom"
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
    private const val KEY_GLASS_WIDTH_VERSION = "glass_width_version"
    private const val KEY_NAV_ORDER = "nav_order"
    private const val KEY_NAV_SLIDE = "nav_slide"
    private const val KEY_NAV_ICON_MOTION = "nav_icon_motion"
    private const val KEY_IGNORED_UPDATES = "ignored_updates"
    private const val KEY_DEFAULT_SOURCE = "default_source"
    private const val KEY_APP_AUTO_UPDATE = "app_auto_update"
    private const val KEY_APP_UPDATE_CHECKED = "app_update_checked_at"
    private const val KEY_APP_UPDATE_PROMPTED = "app_update_prompted"
    private const val KEY_APP_UPDATE_BUILD = "app_update_build"
    private const val KEY_SEARCH_KEYBOARD = "search_keyboard"
    private const val KEY_DOCK_ANIMATION = "dock_animation"
    private const val KEY_BACKGROUND_THEME = "background_theme"
    private const val KEY_AMOLED = "amoled"

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
     * Whether the store may look for a newer build of itself when it opens.
     *
     * On by default: this app is delivered through GitHub releases, and an install that never looks
     * is one that stays on the build it was installed with. Turning it off leaves the manual check in
     * Settings working.
     */
    private val _appAutoUpdate = MutableStateFlow(true)
    val appAutoUpdate: StateFlow<Boolean> = _appAutoUpdate.asStateFlow()

    /** When GitHub was last asked for a release, in wall-clock milliseconds; 0 means never. */
    private val _appUpdateCheckedAt = MutableStateFlow(0L)
    val appUpdateCheckedAt: StateFlow<Long> = _appUpdateCheckedAt.asStateFlow()

    /** The version whose offer was already shown, so opening the store does not ask twice. */
    private val _appUpdatePrompted = MutableStateFlow<String?>(null)
    val appUpdatePrompted: StateFlow<String?> = _appUpdatePrompted.asStateFlow()

    /** Which published Aura Store build an update offers: this install's signature, or a named one. */
    private val _appUpdateBuild = MutableStateFlow(UpdateBuild.AUTO)
    val appUpdateBuild: StateFlow<UpdateBuild> = _appUpdateBuild.asStateFlow()

    /**
     * Whether landing on the Search tab raises the keyboard.
     *
     * On by default, which is what Play does and what makes the tab a field rather than a page. Off is
     * for the people who open Search to read what the last term returned without the keys covering
     * half of it; the field still takes focus and raises the keyboard when it is tapped.
     */
    private val _searchKeyboard = MutableStateFlow(true)
    val searchKeyboard: StateFlow<Boolean> = _searchKeyboard.asStateFlow()

    /**
     * How the floating download panel draws progress. The shipped wash is the default, so an install
     * that never opens this setting looks exactly as it did before the choice existed.
     */
    private val _dockAnimation = MutableStateFlow(DockAnimation.WASH)
    val dockAnimation: StateFlow<DockAnimation> = _dockAnimation.asStateFlow()

    /**
     * The weather behind the store. [BackgroundTheme.NONE] by default, which is the page this app
     * has always drawn; the choice is a whole-app change and is written the moment a tile is tapped.
     */
    private val _backgroundTheme = MutableStateFlow(BackgroundTheme.NONE)
    val backgroundTheme: StateFlow<BackgroundTheme> = _backgroundTheme.asStateFlow()

    /**
     * Whether the store paints true black and wears its dark scheme.
     *
     * This is its own switch in Appearance, not a wallpaper: it sits alongside the theme because it
     * changes how every page is painted, and it can be worn with any of the weather effects above.
     * Off by default, so an install that never asks keeps the page it already has.
     */
    private val _amoled = MutableStateFlow(false)
    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()

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

    /** Whether the first-open tour has been finished or skipped. */
    private val _tourSeen = MutableStateFlow(false)
    val tourSeen: StateFlow<Boolean> = _tourSeen.asStateFlow()

    /**
     * The profile picture: null for the Aura mark, `face:<id>` for a built-in game face, `custom`
     * for the square the user cropped from their own gallery. The picture itself lives in files,
     * not in preferences, so a large photo cannot bloat the settings store.
     */
    private val _avatar = MutableStateFlow<String?>(null)
    val avatar: StateFlow<String?> = _avatar.asStateFlow()

    /** The ring around [avatar], worn everywhere the picture is drawn. */
    private val _avatarFrame = MutableStateFlow(AvatarFrame.NONE)
    val avatarFrame: StateFlow<AvatarFrame> = _avatarFrame.asStateFlow()

    /** The drawn cover, or the one the user cropped when [coverCustom] is true. */
    private val _coverStyle = MutableStateFlow(CoverStyle.HOUSE)
    val coverStyle: StateFlow<CoverStyle> = _coverStyle.asStateFlow()

    private val _coverCustom = MutableStateFlow(false)
    val coverCustom: StateFlow<Boolean> = _coverCustom.asStateFlow()

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
        _appAutoUpdate.value = prefs.getBoolean(KEY_APP_AUTO_UPDATE, true)
        _appUpdateCheckedAt.value = prefs.getLong(KEY_APP_UPDATE_CHECKED, 0L)
        _appUpdatePrompted.value = prefs.getString(KEY_APP_UPDATE_PROMPTED, null)?.takeIf { it.isNotBlank() }
        _appUpdateBuild.value = runCatching {
            UpdateBuild.valueOf(prefs.getString(KEY_APP_UPDATE_BUILD, null) ?: UpdateBuild.AUTO.name)
        }.getOrDefault(UpdateBuild.AUTO)
        _searchKeyboard.value = prefs.getBoolean(KEY_SEARCH_KEYBOARD, true)
        _dockAnimation.value = runCatching {
            DockAnimation.valueOf(prefs.getString(KEY_DOCK_ANIMATION, null) ?: DockAnimation.WASH.name)
        }.getOrDefault(DockAnimation.WASH)
        _backgroundTheme.value = runCatching {
            BackgroundTheme.valueOf(
                prefs.getString(KEY_BACKGROUND_THEME, null) ?: BackgroundTheme.NONE.name,
            )
        }.getOrDefault(BackgroundTheme.NONE)
        _amoled.value = prefs.getBoolean(KEY_AMOLED, false)
        _notificationsAsked.value = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        _tourSeen.value = prefs.getBoolean(KEY_TOUR_SEEN, false)
        _avatar.value = prefs.getString(KEY_AVATAR, null)?.takeIf { it.isNotBlank() }
        _avatarFrame.value = AvatarFrame.from(prefs.getString(KEY_AVATAR_FRAME, null))
        _coverStyle.value = CoverStyle.from(prefs.getString(KEY_COVER_STYLE, null))
        _coverCustom.value = prefs.getBoolean(KEY_COVER_CUSTOM, false)
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
        // The bar's shipped width moved from 100 % to 116 %; an install still on the old default
        // takes the new one once, while any width actually dragged is kept. The migration is
        // versioned so choosing 100 % again later does not get rewritten on the next boot.
        val widthMigrated = prefs.getInt(KEY_GLASS_WIDTH_VERSION, 0) >= 1
        val width = if (material) {
            adoptDefaultGlassWidth(prefs.getFloat(KEY_GLASS_WIDTH, d.width), widthMigrated)
        } else {
            d.width
        }
        // Only an install that already owns a material is migrated: before the current control set
        // there is nothing to adopt, and stamping the version there would skip the move on the
        // upgrade that actually introduces these values.
        if (material && !widthMigrated) {
            prefs.edit {
                putFloat(KEY_GLASS_WIDTH, width)
                putInt(KEY_GLASS_WIDTH_VERSION, 1)
            }
        }
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
            width = width,
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

    /** The tour is worth exactly one first open; both Skip and the last page land here. */
    fun markTourSeen(context: Context) {
        if (_tourSeen.value) return
        _tourSeen.value = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_TOUR_SEEN, true)
        }
    }

    /**
     * Puts the first-open tour back in front of the store.
     *
     * The flag is a plain boolean so replay is the same write as the first install: the activity is
     * already watching it, and clearing it is enough for the tour to open over whatever is showing.
     */
    fun replayTour(context: Context) {
        if (!_tourSeen.value) return
        _tourSeen.value = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_TOUR_SEEN, false)
        }
    }

    /** Writes the chosen picture. Null puts the Aura mark back. */
    fun setAvatar(context: Context, value: String?) {
        _avatar.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (value == null) remove(KEY_AVATAR) else putString(KEY_AVATAR, value)
        }
    }

    /** One frame around every copy of the picture. A pick on the profile is the whole action. */
    fun setAvatarFrame(context: Context, frame: AvatarFrame) {
        _avatarFrame.value = frame
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_AVATAR_FRAME, frame.id)
        }
    }

    /** Chooses a drawn cover, and turns the photograph off without deleting its file. */
    fun setCoverStyle(context: Context, style: CoverStyle) {
        _coverStyle.value = style
        _coverCustom.value = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_COVER_STYLE, style.id)
            putBoolean(KEY_COVER_CUSTOM, false)
        }
    }

    /** Wears the cropped photograph; the file has already been written by [CoverStore]. */
    fun setCustomCover(context: Context) {
        _coverCustom.value = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_COVER_CUSTOM, true)
        }
    }

    fun setAppAutoUpdate(context: Context, enabled: Boolean) {
        _appAutoUpdate.value = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_APP_AUTO_UPDATE, enabled)
        }
    }

    fun markAppUpdateChecked(context: Context, at: Long = System.currentTimeMillis()) {
        _appUpdateCheckedAt.value = at
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_APP_UPDATE_CHECKED, at)
        }
    }

    /** Records that [version]'s offer has been shown, so an automatic check leaves it alone. */
    fun markAppUpdatePrompted(context: Context, version: String) {
        _appUpdatePrompted.value = version
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_APP_UPDATE_PROMPTED, version)
        }
    }

    fun setAppUpdateBuild(context: Context, build: UpdateBuild) {
        _appUpdateBuild.value = build
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_APP_UPDATE_BUILD, build.name)
        }
    }

    fun setSearchKeyboard(context: Context, on: Boolean) {
        _searchKeyboard.value = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_SEARCH_KEYBOARD, on)
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

    /** The chosen download-panel style. Written immediately: a pick on the page is the whole action. */
    fun setDockAnimation(context: Context, animation: DockAnimation) {
        _dockAnimation.value = animation
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_DOCK_ANIMATION, animation.name)
        }
    }

    /** The wallpaper behind the store. One tap on the picker is the whole action. */
    fun setBackgroundTheme(context: Context, theme: BackgroundTheme) {
        _backgroundTheme.value = theme
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_BACKGROUND_THEME, theme.name)
        }
    }

    /** True black and the dark scheme together; one switch in Appearance is the whole action. */
    fun setAmoled(context: Context, on: Boolean) {
        _amoled.value = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_AMOLED, on)
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
