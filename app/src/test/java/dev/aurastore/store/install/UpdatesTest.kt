package dev.aurastore.store.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update badge went wrong in a way that only a real install could show: the catalogue's
 * `lastest_version` is the original app's version, the installed name is the mod's, and the file
 * label carries decoration. Every case below is a string pair taken from the live catalogue.
 *
 * [Updates.isNewer] is pinned too, because an app this store never installed can only be judged
 * against that original-app version — and there the question is which one is *newer*, not whether
 * they differ.
 */
class UpdatesTest {

    @Test
    fun `a decorated file label matching the installed name is not an update`() {
        assertFalse(Updates.isAvailable("4.4.4", "v4.4.4 - Premium"))
        assertFalse(Updates.isAvailable("3.3.0", "v3.3.0 - Premium"))
        assertFalse(Updates.isAvailable("18.4.0", "18.4.0 build 245"))
    }

    @Test
    fun `a genuinely newer build is an update`() {
        assertTrue(Updates.isAvailable("3.3.0", "v3.4.0 - Premium"))
        assertTrue(Updates.isAvailable("3.2.11", "v3.3.0 - Premium"))
    }

    @Test
    fun `the same label never reads as an update even when the apk's own version differs`() {
        // Win Slot: the post ships "v5.0", the APK inside it reports 1.0.6. Compared label to label
        // the catalogue has nothing new; compared name to label it offers the same file forever.
        assertFalse(Updates.isAvailable("v5.0", "v5.0"))
        assertTrue(Updates.isAvailable("1.0.6", "v5.0"))
    }

    @Test
    fun `an unknown version is never reported as out of date`() {
        assertFalse(Updates.isAvailable("4.4.4", null))
        assertFalse(Updates.isAvailable("4.4.4", ""))
        assertFalse(Updates.isAvailable(null, "v4.4.4 - Premium"))
        assertFalse(Updates.isAvailable("", ""))
        assertFalse(Updates.isAvailable("4.4.4", "Premium"))
    }

    @Test
    fun `the leading dotted number is what gets compared`() {
        assertEquals("4.4.4", Updates.versionOf("v4.4.4 - Premium"))
        assertEquals("18.4.0", Updates.versionOf("18.4.0 build 245"))
        assertEquals("5.2.8", Updates.versionOf("5.2.8"))
        assertEquals("", Updates.versionOf("Premium only"))
        assertEquals("", Updates.versionOf(null))
    }

    @Test
    fun `an app from elsewhere is listed only for a strictly newer version`() {
        // The catalogue lags behind the Play Store as often as it leads it. "Different" would offer a
        // downgrade for the older listing, then offer it again every time the tab opened.
        assertTrue(Updates.isNewer("9.1.32", "9.1.94"))
        assertTrue(Updates.isNewer("9.1.94", "9.1.94.1912"))
        assertFalse(Updates.isNewer("9.1.94.1912", "9.1.94"))
        assertFalse(Updates.isNewer("9.1.94", "9.1.94"))
    }

    @Test
    fun `an unknown version on either side never reads as out of date`() {
        assertFalse(Updates.isNewer("9.1.94", null))
        assertFalse(Updates.isNewer("9.1.94", ""))
        assertFalse(Updates.isNewer("9.1.94", "Premium only"))
        assertFalse(Updates.isNewer(null, "9.1.94"))
        assertFalse(Updates.isNewer("", ""))
    }

    @Test
    fun `versions are ordered by number, not as text`() {
        assertTrue(Updates.compare("1.10.0", "1.9.0") > 0)
        assertTrue(Updates.compare("2.0", "10.0") < 0)
        assertEquals(0, Updates.compare("v1.2.3 - Mod", "1.2.3"))
    }
}
