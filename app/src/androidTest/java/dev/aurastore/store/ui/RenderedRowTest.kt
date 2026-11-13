package dev.aurastore.store.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import dev.aurastore.store.R
import dev.aurastore.store.data.Api
import dev.aurastore.store.data.PostSummary
import dev.aurastore.store.ui.feed.Rail
import dev.aurastore.store.ui.feed.RailRow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What these screens actually draw, checked on a device.
 *
 * Every other test in this project runs on a JVM, which cannot see a layout. That has cost the app four
 * separate bugs — a rank column that split `1000` over two lines, a footer sentence that ran edge to
 * edge, and two parameter orders the compiler could not catch because the swapped parameters share a
 * type — and each was found by a human looking at a screenshot. These tests exist so the next one of
 * those is a failing build instead of a round.
 */
class RenderedRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** 16 dp in the device's own pixels; `setContent` may only be called once per test. */
    private fun densityOf(block: @Composable () -> Unit): Float {
        var density = 0f
        compose.setContent {
            density = LocalDensity.current.density
            block()
        }
        compose.waitForIdle()
        check(density > 0f)
        return density
    }

    private fun row(id: Long, title: String = "App $id") = PostSummary(
        id = id,
        title = title,
        imageUrl = null,
        bannerUrl = null,
        publisher = null,
        genre = null,
        size = "12 MB",
        type = null,
        modInfo = null,
        latestVersion = null,
        packageName = null,
        ratingAvg = 4.2,
        ratingCount = 12,
        views = 1_000,
        updatedAt = null,
    )

    @Test
    fun aFourDigitRankStaysOnOneLine() {
        // The rank column was `width(28.dp)` — right for two digits, wrong for four — and the list it
        // broke in was 1 000 rows deep, which no test had ever built. Row height is the tell: a wrapped
        // number makes its own row taller than its neighbour.
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                AppRow(post = row(7, "Seven"), onClick = {}, rank = 7)
                AppRow(post = row(2500, "Deep"), onClick = {}, rank = 2500)
            }
        }
        compose.onNodeWithText("2500").assertIsDisplayed()
        val heights = compose.onRoot().fetchSemanticsNode().children
            .map { it.layoutInfo.height }
        assertTrue("a four-digit rank must not grow its row: $heights", heights.distinct().size == 1)
    }

    @Test
    fun infoRowPrintsItsSubtitleUnderItsTitle() {
        compose.setContent { InfoRow(Glyph.Info, "the title", subtitle = "the subtitle") }
        compose.onNodeWithText("the title").assertIsDisplayed()
        compose.onNodeWithText("the subtitle").assertIsDisplayed()
    }

    @Test
    fun noticeCardKeepsBodyAndActionApart() {
        // `body` and `actionLabel` are both `String?`; a positional mix-up between them compiles, runs,
        // and shows the wrong words. This is the case the type checker cannot cover.
        compose.setContent {
            NoticeCard("!", "the title", body = "the body", actionLabel = "the action", onAction = {})
        }
        compose.onNodeWithText("the title").assertIsDisplayed()
        compose.onNodeWithText("the body").assertIsDisplayed()
        compose.onNodeWithText("the action").assertIsDisplayed()
    }

    @Test
    fun stateMessagePrintsItsBodyAndItsRetry() {
        compose.setContent { StateMessage("the heading", body = "the explanation", onRetry = {}) }
        compose.onNodeWithText("the heading").assertIsDisplayed()
        compose.onNodeWithText("the explanation").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.try_again)).assertIsDisplayed()
    }

    @Test
    fun theFooterEndsAPlainListOnItsName() {
        compose.setContent {
            PagingFooter(
                loading = false,
                hasMore = false,
                capped = false,
                error = null,
                name = "Casual",
                onRetry = {},
            )
        }
        compose.onNodeWithText(context.getString(R.string.list_end, "Casual")).assertIsDisplayed()
    }

    @Test
    fun theCappedFooterNamesItsReachAndKeepsItsMargin() {
        // Two bugs at once: the sentence used to say "more than this", pointing at whatever happened to
        // be on screen, and the End arm had no horizontal inset — which only mattered once the capped
        // sentence was long enough to fill a line.
        val density = densityOf {
            PagingFooter(
                loading = false,
                hasMore = false,
                capped = true,
                error = null,
                name = "Games",
                onRetry = {},
            )
        }
        val margin = density * 16f
        val node = compose.onNodeWithText(
            context.getString(R.string.list_end_capped, "Games", Api.LIST_REACH),
        )
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("the capped sentence must not touch the left edge: $bounds", bounds.left >= margin - 1)
        assertTrue("nor the right edge: $bounds in $root", bounds.right <= root.right - margin + 1)
    }

    @Test
    fun aRailThatHitTheWallSaysSoAtItsEnd() {
        // A horizontal row cannot carry a footer, so until §3u a rail that stopped growing looked
        // exactly like a rail the reader had finished swiping. Its number is the rail's own: 50 pages of
        // the module's `limit`, not the screen's reach.
        compose.setContent {
            RailRow(
                rail = Rail(
                    title = "Popular",
                    posts = (1..3).map { row(it.toLong()) },
                    query = mapOf("limit" to "6"),
                    hasMore = false,
                    capped = true,
                ),
                generation = 0,
                onOpenPost = {},
                onLoadMore = {},
            )
        }
        // The tail is the last item of a horizontal row, so it is composed but off-screen until the
        // reader swipes — which is the whole point of the widget. Scroll to it rather than assert on a
        // node nobody can see.
        compose.onNode(hasScrollAction()).performScrollToIndex(3)
        compose.onNodeWithText(
            context.getString(R.string.list_end_capped, "Popular", 50 * 6),
        ).assertIsDisplayed()
    }
}
