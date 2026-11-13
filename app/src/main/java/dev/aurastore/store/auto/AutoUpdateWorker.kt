package dev.aurastore.store.auto

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.aurastore.store.MainActivity
import dev.aurastore.store.R
import dev.aurastore.store.data.AutoUpdateMode
import dev.aurastore.store.data.Settings
import dev.aurastore.store.install.AutoUpdater
import dev.aurastore.store.install.UpdateTarget
import dev.aurastore.store.install.Updates
import java.util.concurrent.TimeUnit

/**
 * The daily update check, run by the platform rather than by a screen.
 *
 * Until now the auto-update policy was only honoured while the You tab was open, which meant
 * "update automatically over Wi-Fi" really meant "update automatically if you happen to look". This
 * worker is the missing half: WorkManager schedules it, then the *same* [AutoUpdater] rules the
 * on-screen flow uses decide whether anything may be queued, so the policy is enforced in one place
 * and cannot drift between the two paths.
 *
 * It deliberately does nothing privileged: queueing hands the package to the system installer like
 * any other install, and Android still shows its own confirmation.
 */
class AutoUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        Settings.load(app)
        val mode = Settings.autoUpdate.value
        val targets = AutoUpdater.available(app)
        UpdateNotifications.ensureChannel(app)

        if (targets.isEmpty()) {
            UpdateNotifications.clear(app)
            return Result.success()
        }
        // The policy decides whether bytes may move now, not whether the apps have updates — so the
        // notification still names every app with one, it just does not pretend anything was installed.
        if (!Settings.allowedNow(app, mode)) {
            UpdateNotifications.report(app, targets, queued = emptySet(), wifiOnly = mode == AutoUpdateMode.WIFI)
            return Result.success()
        }
        val queued = AutoUpdater.queueAll(app, targets).toSet()
        UpdateNotifications.report(app, targets, queued = queued, wifiOnly = false)
        return Result.success()
    }
}

/** Scheduling, so the call sites do not have to know WorkManager's builder types. */
object AutoUpdate {

    private const val UNIQUE = "aurastore-auto-update"

    /**
     * Daily, once the device is connected. The interval is not tighter because the catalogue is
     * version-label based (see [dev.aurastore.store.install.Updates]) — an hourly check of the same
     * labels would be traffic for no information.
     */
    fun schedule(context: Context) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutoUpdateWorker>(1, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .setInitialDelay(4, TimeUnit.HOURS)
                    .build(),
            )
        }
    }
}

/**
 * One notification per app with an update, each wearing that app's own icon and naming its version
 * pair.
 *
 * The earlier version reported a count, which left the shade saying "3 apps have a newer build" and
 * made the user open the store to find out which three. A count is not the fact anyone wants: the
 * name, the icon and the version pair are. The cards are deliberately ungrouped — a group collapses
 * into one summary and hides every app's icon behind it, which is the thing this notification exists
 * to show.
 *
 * The channel is the same one the count used, so an install that already granted it keeps its
 * setting. Nothing here downloads anything; the worker has queued what the policy allows and the
 * text says which state each app is in.
 */
object UpdateNotifications {

    private const val CHANNEL_ID = "updates"
    private const val APP_ID = 4713
    private const val PREFS = "aurastore_update_notices"
    private const val SHOWN = "shown"

    /** 64 dp at xxxhdpi: larger than the shade draws, so the system's own downscale stays sharp. */
    private const val ICON_PX = 256

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.updates_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.updates_channel_description)
                },
            )
        }
    }

    fun report(
        context: Context,
        targets: List<UpdateTarget>,
        queued: Set<String>,
        wifiOnly: Boolean,
    ) {
        ensureChannel(context)
        // Android 13+ refuses notify() outright without this, and the user can revoke it later.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)

        // A child from an earlier check whose update has since gone in would keep claiming it, so the
        // set posted last time is the cancel list. It is persisted because the worker's process need
        // not be the one that answers the next check.
        val packages = targets.map { it.packageName }
        shown(context).filterNot { it in packages }.forEach { gone ->
            runCatching { manager.cancel(tag(gone), APP_ID) }
        }
        prefs(context).edit { putStringSet(SHOWN, packages.toSet()) }

        val open = openIntent(context)
        targets.forEachIndexed { index, target ->
            // The first card may sound once; the rest of a batch land quietly, so a morning with six
            // updates is one chime and six readable cards rather than six chimes in a row.
            val notification = appNotification(context, target, queued, wifiOnly, open, silent = index > 0)
            runCatching { manager.notify(tag(target.packageName), APP_ID, notification) }
        }
    }

    fun clear(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        shown(context).forEach { gone ->
            runCatching { manager.cancel(tag(gone), APP_ID) }
        }
        prefs(context).edit { remove(SHOWN) }
    }

    /** The app's own icon, so the shade shows the thing that has an update rather than our mark. */
    private fun icon(context: Context, packageName: String): Bitmap? = runCatching {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val bitmap = createBitmap(ICON_PX, ICON_PX)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    }.getOrNull()

    private fun appNotification(
        context: Context,
        target: UpdateTarget,
        queued: Set<String>,
        wifiOnly: Boolean,
        open: PendingIntent,
        silent: Boolean,
    ): Notification {
        val pair = Updates.delta(target.installedVersion, target.catalogueVersion)
        val state = stateText(context, target, queued, wifiOnly)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setLargeIcon(icon(context, target.packageName))
            .setContentTitle(target.title)
            .setContentText(pair)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$pair\n$state"))
            .setOnlyAlertOnce(true)
            .setSilent(silent)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
    }

    private fun stateText(
        context: Context,
        target: UpdateTarget,
        queued: Set<String>,
        wifiOnly: Boolean,
    ): String = when {
        target.packageName in queued -> context.getString(R.string.updates_one_queued)
        wifiOnly -> context.getString(R.string.updates_one_wifi)
        !target.autoUpdatable -> context.getString(R.string.updates_one_outside)
        else -> context.getString(R.string.updates_one_ready)
    }

    private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Notifications are keyed by (tag, id), so every app can share [APP_ID] and stay distinct. */
    private fun tag(packageName: String): String = "aurastore.update:$packageName"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun shown(context: Context): Set<String> =
        prefs(context).getStringSet(SHOWN, emptySet())?.toSet().orEmpty()
}
