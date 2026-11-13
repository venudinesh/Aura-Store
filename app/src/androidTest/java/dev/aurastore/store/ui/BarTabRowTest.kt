package dev.aurastore.store.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import dev.aurastore.store.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The bar's tabs answer a tap anywhere in their own fifth.
 *
 * This is here because they did not. Measured through the shipped bar on the Redmi, the Material item
 * only answered within about the icon's own 24 dp — x=100 inside the Games tab's fifth did nothing, and
 * so did x=965 inside You's — while x=134 and x=950 switched tabs, so most of a 68 dp bar was dead. A
 * tap eight dp from the pill's left edge and one eight dp from its right edge are the two that used to
 * fail, and they are the two this test makes.
 *
 * It also pins the semantics box, because that is what a screen reader puts its focus ring around: the
 * whole fifth, not the icon's halo. A future `NavigationBarItem` or a row that forgets `fillMaxHeight`
 * fails here rather than on a handset.
 */
class BarTabRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val tabs = listOf(
        R.string.tab_games,
        R.string.tab_apps,
        R.string.tab_search,
        R.string.tab_browse,
        R.string.tab_you,
    ).mapIndexed { screen, label -> BarTab(label, screen) }

    /** A 400 dp row, clipped to the host's width when the screen is narrower — so measure the fifth. */
    private fun showRow(onSelect: (Int) -> Unit) {
        compose.setContent {
            Box(Modifier.width(400.dp)) {
                BarTabRow(tabs = tabs, selectedIndex = 0, icon = { _, _ -> }, onSelect = onSelect)
            }
        }
        compose.waitForIdle()
    }

    private fun rowWidth() = compose.onRoot().getUnclippedBoundsInRoot().width

    @Test
    fun aTapNearTheLeftEdgeSelectsTheFirstTab() {
        var picked = -1
        showRow { picked = it }
        compose.onRoot().performTouchInput { click(Offset(8.dp.toPx(), 34.dp.toPx())) }
        compose.waitForIdle()
        assertEquals(0, picked)
    }

    @Test
    fun aTapNearTheRightEdgeSelectsTheLastTab() {
        var picked = -1
        showRow { picked = it }
        val edge = rowWidth() - 8.dp
        compose.onRoot().performTouchInput { click(Offset(edge.toPx(), 34.dp.toPx())) }
        compose.waitForIdle()
        assertEquals(4, picked)
    }

    @Test
    fun aTabsSemanticsBoxIsItsWholeFifth() {
        showRow {}
        val games = compose.onNodeWithText(context.getString(R.string.tab_games))
        games.assertWidthIsEqualTo(rowWidth() / 5)
        // The row sizes itself from the phone's text scale, so the assertion has to as well.
        games.assertHeightIsEqualTo(FloatingBar.heightFor(compose.density.fontScale))
        games.assertIsSelected()
    }
}
