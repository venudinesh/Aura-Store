package dev.aurastore.store.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/** One app that came through this store, and the time Android says it was in front. */
data class StoreUsageEntry(
    val packageName: String,
    val label: String,
    val todayMs: Long,
    val totalMs: Long,
    val lastUsedAt: Long,
)

/** The whole screen-time answer: the rows, and the two figures the header adds up. */
data class UsageReport(
    val entries: List<StoreUsageEntry>,
    val todayMs: Long,
    val totalMs: Long,
)

/**
 * Android's usage-access switch.
 *
 * Screen time is not a runtime permission. The user turns it on in a system page this app cannot draw
 * and cannot read, so the profile asks once, opens that page, and asks again on return. [granted] reads
 * the same app-op the system writes, which is the only answer that matters.
 */
object UsageAccess {

    fun granted(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun open(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
    }
}

/**
 * Screen time for the apps that were installed from this store.
 *
 * The list is the store's own install record (`post:<package>` in [InstalledApps]), not every package
 * on the phone: the tab is a fact about this shop, and a phone's whole app drawer is not. Time comes
 * from [UsageStatsManager], aggregated per package, so one query answers both the day and the total.
 * The arithmetic is kept out of the screen so a JVM test can pin the formatting and the sort.
 */
object StoreUsage {

    /** "42s" is noise at a glance; anything under a minute reads as less than one. */
    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0m"
        if (ms < 60_000L) return "<1m"
        val minutes = ms / 60_000L
        if (minutes < 60L) return "${minutes}m"
        val hours = minutes / 60L
        val rest = minutes % 60L
        return if (rest == 0L) "${hours}h" else "${hours}h ${rest}m"
    }

    /**
     * The rows, newest use first and then by time. A package with no stats is still a row: an app that
     * was installed and never opened is a zero worth seeing, not a gap in the list.
     */
    internal fun sortRows(rows: List<StoreUsageEntry>): List<StoreUsageEntry> =
        rows.sortedWith(
            compareByDescending<StoreUsageEntry> { it.todayMs }
                .thenByDescending { it.totalMs }
                .thenBy { it.label.lowercase() },
        )

    /**
     * Reads usage for every installed package this store remembers installing.
     *
     * Runs off the main thread: both the package manager and the usage service are IPC. A missing
     * permission and a service that answers nothing both produce an empty map, which the screen tells
     * apart from "no store apps" by its own [UsageAccess] check.
     */
    fun load(context: Context): UsageReport {
        val packages = InstalledApps.storePackages(context)
        if (packages.isEmpty()) return UsageReport(emptyList(), 0L, 0L)

        val manager = context.getSystemService(UsageStatsManager::class.java)
            ?: return UsageReport(emptyList(), 0L, 0L)
        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val today = runCatching {
            manager.queryAndAggregateUsageStats(todayStart, now)
        }.getOrNull().orEmpty()
        // The total is asked over the widest range the service keeps. A device that refuses the range
        // still answers the day, and the row falls back to today rather than printing a smaller total
        // than its own day.
        val all = runCatching {
            manager.queryAndAggregateUsageStats(0L, now)
        }.getOrNull().orEmpty()

        val pm = context.packageManager
        val rows = packages.mapNotNull { packageName ->
            val info = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
                ?: return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrDefault(packageName)
            val todayStats = today[packageName]
            val allStats = all[packageName]
            val todayMs = todayStats?.totalTimeInForeground ?: 0L
            val totalMs = max(allStats?.totalTimeInForeground ?: 0L, todayMs)
            StoreUsageEntry(
                packageName = packageName,
                label = label,
                todayMs = todayMs,
                totalMs = totalMs,
                lastUsedAt = max(
                    todayStats?.lastTimeUsed ?: 0L,
                    allStats?.lastTimeUsed ?: 0L,
                ),
            )
        }
        val sorted = sortRows(rows)
        return UsageReport(
            entries = sorted,
            todayMs = sorted.sumOf { it.todayMs },
            totalMs = sorted.sumOf { it.totalMs },
        )
    }
}
