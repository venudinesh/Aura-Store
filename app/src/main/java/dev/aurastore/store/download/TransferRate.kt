package dev.aurastore.store.download

/**
 * The two numbers a download page has to answer: how fast it is going, and how long is left.
 *
 * The engine ticks progress about four times a second, and one tick's bytes-over-time is far too
 * jumpy to show — a CDN that buffers in 64 KB blocks reads as 0 MB/s and then 8 MB/s. So the figure
 * shown is an exponentially smoothed speed: recent samples count, one stalled second does not.
 *
 * Everything here is arithmetic on numbers, no clocks and no Android, so the rules are testable and
 * the smoothing constant is a decision rather than a side effect.
 */
object TransferRate {

    /** Weight of the newest sample in the smoothed speed. */
    const val SMOOTHING = 0.3f

    /**
     * The smoothed bytes-per-second after one more sample of [bytesDelta] in [millisDelta]
     * milliseconds.
     *
     * A negative delta means the counter restarted — a resumed transfer that had to begin again on
     * another host. The old average describes bytes that are no longer being fetched, so it is
     * dropped rather than blended.
     */
    fun speed(current: Long, bytesDelta: Long, millisDelta: Long): Long {
        if (millisDelta <= 0L) return current
        val instant = if (bytesDelta <= 0L) 0L else bytesDelta * 1000L / millisDelta
        if (bytesDelta < 0L || current <= 0L) return instant
        return (current * (1f - SMOOTHING) + instant * SMOOTHING).toLong().coerceAtLeast(1L)
    }

    /** Seconds left at [bytesPerSecond], or null when the total or the speed is not known yet. */
    fun etaSeconds(remaining: Long, bytesPerSecond: Long): Long? {
        if (remaining <= 0L || bytesPerSecond <= 0L) return null
        return ((remaining + bytesPerSecond - 1) / bytesPerSecond).coerceAtMost(MAX_ETA_SECONDS)
    }

    /** A transfer this slow is not worth a countdown; the byte counts say it better. */
    private const val MAX_ETA_SECONDS = 60L * 60 * 24 * 7

    fun formatSpeed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0L) "starting…" else "${DownloadEngine.humanSize(bytesPerSecond)}/s"

    /**
     * "4 min left"-style text.
     *
     * Rounded hard (seconds only under a minute, minutes under an hour) because an ETA recomputed
     * four times a second reads as a lie; the user needs "a few minutes", not a countdown.
     */
    fun formatEta(seconds: Long?): String = when {
        seconds == null -> "time unknown"
        seconds < 60 -> "${maxOf(seconds, 1)} s"
        seconds < 3_600 -> "${seconds / 60} min"
        seconds < 86_400 -> "${seconds / 3_600} h ${(seconds % 3_600) / 60} min"
        else -> "${seconds / 86_400} days"
    }
}
