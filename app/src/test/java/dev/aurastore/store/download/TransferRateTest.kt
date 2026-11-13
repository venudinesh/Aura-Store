package dev.aurastore.store.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The meter on the download page.
 *
 * A CDN delivers in 64 KB blocks, so one tick's arithmetic swings between 0 and 8 MB/s; the figures
 * shown are smoothed and rounded on purpose, and both decisions are pinned here.
 */
class TransferRateTest {

    @Test
    fun `the first sample is taken as it stands`() {
        assertEquals(3_000L, TransferRate.speed(current = 0L, bytesDelta = 3_000L, millisDelta = 1_000L))
    }

    @Test
    fun `a new sample moves the average by its weight only`() {
        // 1 MB/s of history, 3 MB/s in the last second: 0.7 * 1000 + 0.3 * 3000.
        assertEquals(1_600L, TransferRate.speed(current = 1_000L, bytesDelta = 3_000L, millisDelta = 1_000L))
    }

    @Test
    fun `a stalled tick pulls the meter down but not to zero`() {
        assertEquals(700L, TransferRate.speed(current = 1_000L, bytesDelta = 0L, millisDelta = 250L))
    }

    @Test
    fun `a restarted counter drops the stale average`() {
        // A resume that had to begin again on another host: the old speed describes bytes no longer fetched.
        assertEquals(0L, TransferRate.speed(current = 5_000L, bytesDelta = -4_000L, millisDelta = 1_000L))
        assertEquals(5_000L, TransferRate.speed(current = 5_000L, bytesDelta = 100L, millisDelta = 0L))
    }

    @Test
    fun `a blended speed never reads as stopped`() {
        // A near-idle tick drags the average down (1000 * 0.7) but the meter never falls to zero.
        assertEquals(700L, TransferRate.speed(current = 1_000L, bytesDelta = 1L, millisDelta = 10_000L))
        assertEquals(1L, TransferRate.speed(current = 1L, bytesDelta = 0L, millisDelta = 100L))
    }

    @Test
    fun `time left is rounded up and needs both numbers`() {
        assertEquals(3L, TransferRate.etaSeconds(remaining = 2_500L, bytesPerSecond = 1_000L))
        assertNull(TransferRate.etaSeconds(remaining = 0L, bytesPerSecond = 1_000L))
        assertNull(TransferRate.etaSeconds(remaining = 2_500L, bytesPerSecond = 0L))
        // A crawl is capped: "28 days left" is not advice, it is a number nobody can use.
        assertEquals(60L * 60 * 24 * 7, TransferRate.etaSeconds(remaining = Long.MAX_VALUE / 2, bytesPerSecond = 1L))
    }

    @Test
    fun `the text is coarse enough to survive four ticks a second`() {
        assertEquals("starting…", TransferRate.formatSpeed(0L))
        assertEquals("2.0 KB/s", TransferRate.formatSpeed(2_048L))
        assertEquals("time unknown", TransferRate.formatEta(null))
        assertEquals("1 s", TransferRate.formatEta(0L))
        assertEquals("59 s", TransferRate.formatEta(59L))
        assertEquals("2 min", TransferRate.formatEta(150L))
        assertEquals("1 h 5 min", TransferRate.formatEta(3_900L))
        assertEquals("2 days", TransferRate.formatEta(2L * 86_400 + 60))
    }
}
