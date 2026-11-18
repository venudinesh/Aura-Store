package dev.aurastore.store.ui.profile

import android.content.Context
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.data.PackArt
import dev.aurastore.store.data.SpecialAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One poster, from the copy on the phone when there is one and from the APK when there is not.
 *
 * The two are the same picture, so the choice is never visible as a change of art: it is where the
 * bytes are read from. [targetPx] is the size the caller draws at, so a 56 dp tile samples the
 * 1024-pixel poster down, and both sources are decoded to that size off the main thread and kept in
 * one budget rather than per cell. Decoding the packaged art at its full 1024 pixels for a grid of
 * thumbnails was tens of megabytes and a stall on every visit; the shelf reads the same picture at
 * the size it is shown.
 */
@Composable
fun PackPoster(
    anime: SpecialAnime,
    poster: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    targetPx: Int = 480,
) {
    val context = LocalContext.current
    val version by PackArt.version.collectAsStateWithLifecycle()
    val index = poster.coerceIn(0, anime.posters.lastIndex)
    val packaged = remember(anime.id, index, targetPx) {
        cache.get(packedKey(anime, index, targetPx))
    }
    val art by produceState<ImageBitmap?>(packaged, anime.id, index, targetPx, version) {
        value = withContext(Dispatchers.IO) {
            kept(context, anime, index, targetPx) ?: packed(context, anime, index, targetPx)
        }
    }
    val bitmap = art
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

/** A quarter of the app's usual image budget: these are small squares, and most are the APK's. */
private const val CACHE_BYTES = 24 * 1024 * 1024

private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

private fun packedKey(anime: SpecialAnime, poster: Int, targetPx: Int): String =
    "apk:${anime.posters[poster]}:$targetPx"

/** The downloaded square, decoded at [targetPx], or null when this poster has no file. */
private fun kept(context: Context, anime: SpecialAnime, poster: Int, targetPx: Int): ImageBitmap? {
    val file = PackArt.file(context, anime.id, poster)
    if (!file.isFile) return null
    val key = "${anime.id}:$poster:$targetPx:${file.length()}"
    cache.get(key)?.let { return it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0) return null
    val bitmap = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, targetPx) },
    ) ?: return null
    return bitmap.asImageBitmap().also { cache.put(key, it) }
}

/** The packaged square, decoded at [targetPx] rather than at the 1024 pixels it ships at. */
private fun packed(context: Context, anime: SpecialAnime, poster: Int, targetPx: Int): ImageBitmap? {
    val id = anime.posters[poster]
    val key = packedKey(anime, poster, targetPx)
    cache.get(key)?.let { return it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(context.resources, id, bounds)
    if (bounds.outWidth <= 0) return null
    val bitmap = BitmapFactory.decodeResource(
        context.resources,
        id,
        BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, targetPx) },
    ) ?: return null
    return bitmap.asImageBitmap().also { cache.put(key, it) }
}

/** The largest power of two that still leaves at least [targetPx] across. */
private fun sample(width: Int, targetPx: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= targetPx) sample *= 2
    return sample
}
