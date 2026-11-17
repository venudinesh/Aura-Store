package dev.aurastore.store.ui.settings

import androidx.annotation.StringRes
import dev.aurastore.store.R
import dev.aurastore.store.ui.Glyph

/**
 * The categories Settings is cut into.
 *
 * The page used to be one form: updates, appearance, the whole navigation bar, security, sources and
 * storage under one scroll. A category is a door on the hub and a page of its own behind it, so the
 * thing being changed is never more than one level away and back returns to the list of doors.
 *
 * About is deliberately not here: it already has its own page, and a second About made of one row
 * would only be a door to a door.
 */
enum class SettingsSection(@StringRes val label: Int, val glyph: Glyph) {
    UPDATES(R.string.settings_section_updates, Glyph.Update),
    APPS(R.string.settings_section_apps, Glyph.Grid),
    APPEARANCE(R.string.settings_section_appearance, Glyph.Star),
    BACKGROUND(R.string.settings_section_background, Glyph.Backdrop),
    NAVIGATION(R.string.settings_section_navigation, Glyph.Brand),
    DOCK(R.string.settings_section_dock, Glyph.Play),
    DOWNLOADS(R.string.settings_section_downloads, Glyph.Download),
    SEARCH(R.string.settings_section_search, Glyph.Search),
}
