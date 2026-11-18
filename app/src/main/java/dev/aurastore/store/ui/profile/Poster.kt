package dev.aurastore.store.ui.profile

import android.content.Context
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
 * 1024-pixel poster down instead of decoding all of it for a thumbnail, and the decoded squares are
 * kept in one budget rather than per cell.
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
    val downloaded by produceState<ImageBitmap?>(null, anime.id, index, targetPx, version) {
        value = withContext(Dispatchers.IO) { kept(context, anime, index, targetPx) }
    }
    val art = downloaded
    if (art != null) {
        Image(
            bitmap = art,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Image(
            painter = painterResource(anime.posters[index]),
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

/** The downloaded square, decoded at [targetPx], or null when this poster has no file. */
private fun kept(context: Context, anime: SpecialAnime, poster: Int, targetPx: Int): ImageBitmap? {
    val file = PackArt.file(context, anime.id, poster)
    if (!file.isFile) return null
    val key = "${anime.id}:$poster:$targetPx:${file.length()}"
    cache.get(key)?.let { return it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
    val bitmap = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    return bitmap.asImageBitmap().also { cache.put(key, it) }
}
