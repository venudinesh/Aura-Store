package dev.aurastore.store.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbering system, on its own.
 *
 * Two consumers depend on this class agreeing with itself: the Gradle build turns the name into the
 * version code Android installs by, and the updater turns a GitHub tag into the name it compares.
 * A disagreement here is an update that downloads and then refuses to install, so the edges — a
 * two-digit minor, a pre-release, a tag that is not a version — are all pinned.
 */
class AppVersionTest {

    @Test
    fun aVersionReadsAndWritesTheSameText() {
        assertEquals("1.1.0", AppVersion.parse("1.1.0")?.text)
        assertEquals("1.1.0", AppVersion.parse("v1.1.0")?.text)
        assertEquals("2.10.3", AppVersion.parse("V2.10.3")?.text)
        assertEquals("1.2.0-rc.1", AppVersion.parse("v1.2.0-rc.1")?.text)
        // Build metadata is not part of the order, so it is not part of the name either.
        assertEquals("1.0.0", AppVersion.parse("1.0.0+ci.7")?.text)
        assertEquals("1.0.0", AppVersion.parse("  1.0.0  ")?.text)
    }

    @Test
    fun textThatIsNotAVersionIsRejectedRatherThanGuessedAt() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1.1"))
        assertNull(AppVersion.parse("1.1.0.0"))
        assertNull(AppVersion.parse("1.1.x"))
        // Outside the version-code bounds: a tag that cannot become a code is not installable.
        assertNull(AppVersion.parse("1000.0.0"))
        assertNull(AppVersion.parse("1.100.0"))
        assertNull(AppVersion.parse("1.0.100"))
    }

    @Test
    fun theCodeGrowsWithTheName() {
        assertEquals(10_100, AppVersion.parse("1.1.0")!!.code)
        assertEquals(10_000, AppVersion.parse("1.0.0")!!.code)
        assertEquals(10_001, AppVersion.parse("1.0.1")!!.code)
        assertEquals(10_100, AppVersion.parse("1.1.0")!!.code)
        assertEquals(11_000, AppVersion.parse("1.10.0")!!.code)
        assertEquals(20_000, AppVersion.parse("2.0.0")!!.code)
        assertEquals(9_999_999, AppVersion.parse("999.99.99")!!.code)
        assertEquals(10_100, AppVersion.codeOf("v1.1.0"))
        assertNull(AppVersion.codeOf("nope"))
    }

    @Test
    fun aCodeAlwaysFitsAndroid() {
        val top = AppVersion.parse("999.99.99")!!
        assertTrue("version codes must stay inside Android's limit", top.code < 2_100_000_000)
    }

    @Test
    fun versionsOrderByNumberNotByText() {
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.9")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)
        assertTrue(AppVersion.parse("1.1.1")!! > AppVersion.parse("1.1.0")!!)
        assertEquals(0, AppVersion.parse("1.1.0")!!.compareTo(AppVersion.parse("v1.1.0")!!))
        // The code and the name must sort the same way, or the updater and the installer disagree.
        val ordered = listOf("1.0.0", "1.0.1", "1.1.0", "1.9.9", "1.10.0", "2.0.0")
        val versions = ordered.map { AppVersion.parse(it)!! }
        assertEquals(ordered, versions.sorted().map { it.text })
        assertEquals(versions.map { it.code }.sorted(), versions.map { it.code })
    }

    @Test
    fun aPreReleaseSitsBelowItsRelease() {
        assertTrue(AppVersion.parse("1.2.0")!! > AppVersion.parse("1.2.0-rc.1")!!)
        assertTrue(AppVersion.parse("1.2.0-rc.2")!! > AppVersion.parse("1.2.0-rc.1")!!)
        assertTrue(AppVersion.parse("1.2.0-rc.1")!! > AppVersion.parse("1.2.0-beta.9")!!)
        assertTrue(AppVersion.parse("1.2.0-beta.2")!! > AppVersion.parse("1.2.0-beta.1")!!)
        assertTrue(AppVersion.parse("1.2.0-alpha")!! < AppVersion.parse("1.2.0-beta")!!)
        // Semver: a numeric identifier is below an alphanumeric one, and a longer set wins its prefix.
        assertTrue(AppVersion.parse("1.2.0-1")!! < AppVersion.parse("1.2.0-alpha")!!)
        assertTrue(AppVersion.parse("1.2.0-rc.1.1")!! > AppVersion.parse("1.2.0-rc.1")!!)
        // A pre-release of the next number is still above the previous release.
        assertTrue(AppVersion.parse("1.3.0-beta.1")!! > AppVersion.parse("1.2.0")!!)
    }
}
