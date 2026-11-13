package dev.aurastore.store.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps the process alive while packages download.
 *
 * The engine drives it: [ensureRunning] is called when the first download is enqueued and
 * [stop] when nothing is active any more.
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DownloadEngine.ensureChannel(this)
        val notification = DownloadEngine.buildNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        refresh(this)
        return START_STICKY
    }

    companion object {
        const val ACTION_STOP = "dev.aurastore.store.STOP_DOWNLOADS"
        private const val NOTIFICATION_ID = 4711

        fun ensureRunning(context: Context) {
            context.startForegroundService(Intent(context, DownloadService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }

        /** Push the current aggregate progress into the notification. */
        fun refresh(context: Context) {
            DownloadEngine.notifyAggregate(context)
        }
    }
}
