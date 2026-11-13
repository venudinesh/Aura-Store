package dev.aurastore.store.ui

import androidx.annotation.StringRes
import dev.aurastore.store.R

/**
 * One navigation tab in **screen** order: its list index is the screen it opens.
 *
 * Shared with the settings screen, which draws the same five icons to reorder them. The order the bar
 * shows lives in `Settings.navOrder`, not here, so a reorder never has to touch this list.
 */
data class NavTab(@StringRes val label: Int, val glyph: Glyph)

val NAV_TABS: List<NavTab> = listOf(
    NavTab(R.string.tab_games, Glyph.Gamepad),
    NavTab(R.string.tab_apps, Glyph.Grid),
    NavTab(R.string.tab_search, Glyph.Search),
    NavTab(R.string.tab_browse, Glyph.Library),
    NavTab(R.string.tab_you, Glyph.Person),
)
