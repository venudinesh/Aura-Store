package dev.aurastore.store.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The row and the file are two different facts. A listing's `lastest_version` is the version of the
 * *original* app the post was written for; the newest artefact actually attached to it can be older,
 * and then "Get" hands the installer the build already on the device.
 * [AutoUpdater.matchedBuild] decides what a row may show, [AutoUpdater.buildIsOffered] re-reads the
 * same rule at the moment of the tap.
 *
 * Pairs below are the live catalogue state of post 8333 (AutoResponder for Telegram) on the Redmi:
 * `lastest_version 5.3.1`, files v4.4.4 Premium and an original 5.2.8 bundle.
 */
class AutoUpdaterTest {

    private fun target(installed: String?, autoUpdatable: Boolean) = UpdateTarget(
        postId = 8333,
        packageName = "tkstudio.autoresponderfortg",
        title = "AutoResponder for TG",
        catalogueVersion = if (autoUpdatable) "v5.3.1 - Premium" else "5.3.1",
        installedVersion = installed,
        autoUpdatable = autoUpdatable,
    )

    @Test
    fun `a matched row names the build its files carry, not the original app's version`() {
        // The 5.2.8 artefact is what the tap hands over, so that is the number the row prints.
        assertEquals("5.2.8", AutoUpdater.matchedBuild(original = "5.3.1", pickLabel = "5.2.8", installed = "4.4.4"))
        // ... and a row drawn from `lastest_version` alone would have promised 5.3.1, which no file is.
        assertNull(AutoUpdater.matchedBuild(original = "5.3.1", pickLabel = "v4.4.4 - Premium", installed = "4.4.4"))
    }

    @Test
    fun `a matched row needs both readings to be ahead`() {
        // The catalogue lags Play as often as it leads it: an original version the device already runs
        // is not an update, whatever a later file happens to be labelled.
        assertNull(AutoUpdater.matchedBuild(original = "4.4.4", pickLabel = "v5.3.1 - Premium", installed = "5.2.8"))
        assertNull(AutoUpdater.matchedBuild(original = "5.3.1", pickLabel = "v4.4.4 - Premium", installed = "5.2.8"))
    }

    @Test
    fun `no file label and no installed version are both not an update`() {
        // A post with no installable artefact, or a check that has not answered: no row, not a wrong one.
        assertNull(AutoUpdater.matchedBuild(original = "5.3.1", pickLabel = null, installed = "4.4.4"))
        // An app whose version name carries no number is unknown, and unknown is not out of date.
        assertNull(AutoUpdater.matchedBuild(original = "5.3.1", pickLabel = "v5.3.1 - Premium", installed = null))
        assertNull(AutoUpdater.matchedBuild(original = null, pickLabel = "v5.3.1 - Premium", installed = "4.4.4"))
    }

    @Test
    fun `a name-matched row is refused when the only file is the build it already runs`() {
        assertFalse(AutoUpdater.buildIsOffered(target("4.4.4", autoUpdatable = false), "v4.4.4 - Premium"))
        // Even a file older than the installed one must not be offered as an update.
        assertFalse(AutoUpdater.buildIsOffered(target("5.2.8", autoUpdatable = false), "v4.4.4 - Premium"))
    }

    @Test
    fun `a name-matched row is kept when the file really is ahead`() {
        assertTrue(AutoUpdater.buildIsOffered(target("4.4.4", autoUpdatable = false), "5.2.8"))
        assertTrue(AutoUpdater.buildIsOffered(target("5.2.8", autoUpdatable = false), "v5.3.1 - Premium"))
    }

    @Test
    fun `an app this store installed is judged label against label`() {
        // The pick is the same numeric build under different decoration: not the change the row promised.
        assertFalse(AutoUpdater.buildIsOffered(target("v4.4.4 - Premium", autoUpdatable = true), "v4.4.4 - Unlocked"))
        assertTrue(AutoUpdater.buildIsOffered(target("v4.4.4 - Premium", autoUpdatable = true), "v5.3.1 - Premium"))
    }
}
