package dev.aurastore.store

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * State that is supposed to outlive the thing holding it.
 *
 * §4v found this the hard way: killing the app and relaunching it from the launcher restores nothing at
 * all, because that path hands the activity no saved bundle — so every round until then had been
 * "testing" restoration by doing the one thing that cannot test it. `ActivityScenario.recreate()` is
 * the path that does, and it is the same one a rotation or a process death under memory pressure takes.
 *
 * The screen under test is `RestoreProbeActivity`, which lives in `src/debug` and so is absent from the
 * release build. `cancelAndRecreateRecomposer()` was tried first and dropped: it preserves plain
 * `remember`, which the third test below exists to notice.
 */
class SavedStateTest {

    @get:Rule
    val rule = createAndroidComposeRule<RestoreProbeActivity>()

    private fun recreate() {
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test
    fun aRememberedLongSurvivesTheActivityBeingRecreated() {
        // The shell's two `rememberSaveable { mutableStateOf(-1L) }` became `mutableLongStateOf(-1L)` in
        // §3v. A Long that silently failed to restore is invisible to every JVM test in this project.
        rule.onNodeWithText("open=-1").performClick()
        rule.onNodeWithText("open=4242").assertIsDisplayed()

        recreate()

        rule.onNodeWithText("open=4242").assertIsDisplayed()
    }

    @Test
    fun anIntStateSurvivesTheSameWay() {
        // `tab` predates §3v. The two must survive alike, or the Long change is a regression hiding
        // behind a passing suite.
        rule.onNodeWithText("tab=0").performClick()
        rule.onNodeWithText("tab=3").assertIsDisplayed()

        recreate()

        rule.onNodeWithText("tab=3").assertIsDisplayed()
    }

    @Test
    fun theRecreationIsRealBecauseUnsavedStateIsGone() {
        rule.onNodeWithText("transient=0").performClick()
        rule.onNodeWithText("transient=7").assertIsDisplayed()

        recreate()

        rule.onNodeWithText("transient=0").assertIsDisplayed()
    }
}
