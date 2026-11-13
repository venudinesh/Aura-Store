package dev.aurastore.store

import android.app.Application
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dev.aurastore.store.auto.AutoUpdate
import dev.aurastore.store.data.Settings
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.install.PackageEventReceiver
import dev.aurastore.store.theme.PlayMotion
import okio.Path.Companion.toOkioPath

class AuraStoreApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // The installer sheet this app opens lives in another process and sends nothing back, so the
        // platform's own package broadcasts are the only way a queue row can learn — without waiting
        // for the next resume — that its package landed or was removed. Every action here is a
        // protected system broadcast, which is why the receiver can be registered as not-exported.
        ContextCompat.registerReceiver(
            this,
            PackageEventReceiver(),
            IntentFilter().apply {
                PackageEventReceiver.actions.forEach { addAction(it) }
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // The auto-update policy has to run whether or not a screen is open, so its scheduling lives
        // with the process rather than with the You tab that used to be the only thing honouring it.
        Settings.load(this)
        PlayMotion.syncFrom(this)
        AutoUpdate.schedule(this)
        // The queue outlives its rows' jobs: a restart used to empty *Downloads* while every verified
        // APK was still sitting in the cache. Rebuilding it here, before the first screen reads it, is
        // what lets a finished download survive being opened again next week.
        DownloadEngine.restore(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(96L * 1024 * 1024)
                    .build()
            }
            .build()
}
