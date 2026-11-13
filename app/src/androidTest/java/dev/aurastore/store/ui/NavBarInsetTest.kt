package dev.aurastore.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The two rules the floating bar's bottom padding depends on, checked on a device.
 *
 * Both were broken when the feature shipped and neither is visible to a JVM test. A composition local
 * declared `staticCompositionLocalOf` was carrying a value that changes — the bar's height plus the gap
 * it floats on — and a static local does not invalidate readers that are already composed, so a list
 * keeps the padding it was first given. And the local has to be provided *above* the screens: it
 * resolves where it is read, so wrapping only the bar left every list reading the 0 dp default and
 * running its last row underneath the glass.
 *
 * The second test in this file used to assert that a reader of the local picks up a *changed* value
 * without being recomposed — the `staticCompositionLocalOf` defect. It could not: every arrangement
 * tried, including a parameterless reader and no scroll between the change and the assertion, still
 * recomposed the reader, so the test passed against the bug and was deleted rather than kept as a
 * decoration. That half of the fix rests on the documented difference between the two builders.
 */
class NavBarInsetTest {

    @get:Rule
    val compose = createComposeRule()

    private companion object { const val ROWS = 20 }

    /** Reads the local without being told its value, so only invalidation can update it. */
    @Composable
    private fun ListBody() {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalNavBarInset.current),
        ) {
            items(ROWS) { index -> Text("row $index", Modifier.fillMaxWidth().height(60.dp)) }
        }
    }

    @Composable
    private fun Shell(inset: Dp) {
        CompositionLocalProvider(LocalNavBarInset provides inset) {
            Box(Modifier.fillMaxSize()) {
                ListBody()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .testTag("bar")
                            .fillMaxWidth()
                            .height(FloatingBar.height)
                            .background(Color.Red),
                    )
                }
            }
        }
    }

    private fun scrollToFooter() {
        compose.onNode(hasScrollAction()).performScrollToIndex(ROWS - 1)
        compose.waitForIdle()
    }



    @Test
    fun theLastRowStopsAboveTheBarThatFloatsOverIt() {
        compose.setContent { Shell(FloatingBar.inset(true)) }
        scrollToFooter()
        val last = compose.onNodeWithText("row 19").getBoundsInRoot()
        val bar = compose.onNode(hasTestTag("bar")).getBoundsInRoot()
        assertTrue(
            "last row bottom ${last.bottom.value}dp lies under the bar top ${bar.top.value}dp",
            last.bottom.value <= bar.top.value + 1f,
        )
    }
}
