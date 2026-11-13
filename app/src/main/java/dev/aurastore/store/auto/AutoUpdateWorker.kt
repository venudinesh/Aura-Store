package dev.aurastore.store.auto

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
        // notification still reports the count, it just does not pretend anything was installed.
        if (!Settings.allowedNow(app, mode)) {
            UpdateNotifications.report(app, targets.size, queued = 0, wifiOnly = mode == AutoUpdateMode.WIFI)
            return Result.success()
        }
        val queued = AutoUpdater.queueAll(app, targets)
        UpdateNotifications.report(app, targets.size, queued = queued, wifiOnly = false)
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
 * One notification for the update state, so a background check that found something is visible without
 * opening the app. Downloads have their own channel and their own notification.
 */
object UpdateNotifications {

    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 4712

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

    fun report(context: Context, available: Int, queued: Int, wifiOnly: Boolean) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val resources = context.resources
        val title = resources.getQuantityString(R.plurals.updates_available, available, available)
        val body = when {
            queued > 0 -> resources.getQuantityString(R.plurals.updates_queued, queued, queued)
            wifiOnly -> context.getString(R.string.updates_wifi_blocked)
            else -> context.getString(R.string.updates_open_app, context.getString(R.string.app_name))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$body"))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        // Android 13+ refuses notify() outright without this, and the user can revoke it later.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun clear(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}
