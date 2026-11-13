package dev.aurastore.store.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a remembered install still describes something this app did.
 *
 * Rows used to be marked installed on the strength of the package merely being present, so an update
 * sheet the user dismissed left a record behind. Honouring that record takes the app out of the
 * catalogue name sweep — it reads as one of ours — which is how a real update stopped showing up in
 * the You tab.
 */
class InstalledAppsTest {

    @Test
    fun `a package that changed after we recorded the install is ours`() {
        assertTrue(InstalledApps.recordIsLive(claimedAt = 5_000L, lastUpdateAt = 6_000L))
        // The commit and our own record are two different clocks' readings of the same event.
        assertTrue(InstalledApps.recordIsLive(claimedAt = 5_000L, lastUpdateAt = 5_000L - 1_000L))
    }

    @Test
    fun `a package untouched since before the claimed install is not ours`() {
        // The Redmi's false positive: a record written on 27 Oct for a package last updated on 19 Oct.
        assertFalse(InstalledApps.recordIsLive(claimedAt = 1_700_000_600_000L, lastUpdateAt = 1_700_000_000_000L))
        // Just outside the slack still counts — a slow installer commits first and reports later.
        assertTrue(
            InstalledApps.recordIsLive(
                claimedAt = 1_700_000_600_000L,
                lastUpdateAt = 1_700_000_600_000L - 60_000L,
            ),
        )
    }

    @Test
    fun `a record with no timestamp is taken at its word`() {
        assertTrue(InstalledApps.recordIsLive(claimedAt = 0L, lastUpdateAt = 1L))
    }
}
