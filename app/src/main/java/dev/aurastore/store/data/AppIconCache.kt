package dev.aurastore.store.data

import android.content.Context
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import dev.aurastore.store.ui.toImageBitmap

/**
 * In-memory bitmap cache for device app icons so the LazyColumn in the You tab never performs
 * blocking PackageInfo/Drawable IPC on the main thread during scrolling.
 */
object AppIconCache {

    private val cache = LruCache<String, ImageBitmap>(160)

    fun get(packageName: String): ImageBitmap? = synchronized(cache) { cache.get(packageName) }

    fun load(context: Context, packageName: String): ImageBitmap? {
        synchronized(cache) {
            cache.get(packageName)?.let { return it }
        }
        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName).toImageBitmap(144)
        }.getOrNull()
        if (bitmap != null) {
            synchronized(cache) {
                cache.put(packageName, bitmap)
            }
        }
        return bitmap
    }
}
