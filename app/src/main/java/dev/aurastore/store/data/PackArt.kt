package dev.aurastore.store.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The posters that live on the phone as files rather than inside the APK.
 *
 * Everything in the pack ships compiled into the app, so nothing needs downloading to be drawn. A
 * download puts the same bitmap on the phone under the name it was committed to; from then on the
 * app draws the file, and a later removal deletes it. That is the one path in the pack where delete
 * hands bytes back, and it is why the source is the repository rather than a server of our own: the
 * art is already there, at these names, and nothing has to be published twice.
 */
object PackArt {

    private const val REPO = "venudinesh/Aura-Store"
    private const val BRANCH = "kotlin"
    private const val FOLDER = "pack"

    /** Where the committed posters sit: the resource folder, on the branch that holds them. */
    const val BASE = "https://raw.githubusercontent.com/$REPO/$BRANCH/app/src/main/res/drawable-nodpi/"

    private val _version = MutableStateFlow(0L)

    /** Bumped whenever a file lands or is deleted, so a picture already on screen reloads. */
    val version: StateFlow<Long> = _version.asStateFlow()

    /** The committed name of one poster: its resource name with the extension. */
    fun name(animeId: String, poster: Int): String = "special_poster_${animeId}_${poster + 1}.jpg"

    fun url(animeId: String, poster: Int): String = BASE + name(animeId, poster)

    fun file(context: Context, animeId: String, poster: Int): File =
        File(File(context.filesDir, FOLDER), name(animeId, poster))

    fun has(context: Context, animeId: String, poster: Int): Boolean =
        file(context, animeId, poster).isFile

    /** Everything downloaded so far, in bytes. Zero when nothing has been. */
    fun bytes(context: Context): Long = runCatching {
        File(context.filesDir, FOLDER).listFiles()?.sumOf { it.length() } ?: 0L
    }.getOrDefault(0L)

    fun delete(context: Context, anime: SpecialAnime, poster: Int): Boolean {
        val gone = runCatching { file(context, anime.id, poster).delete() }.getOrDefault(false)
        if (gone) _version.value += 1
        return gone
    }

    /**
     * Fetches [posters] of [anime] and keeps them, answering the ones that landed.
     *
     * One request per poster, written under a temporary name and renamed into place, so a transfer
     * that dies halfway leaves no half a JPEG for a picker to draw. A failed file is not answered,
     * which is how the caller knows to leave it on the removed list and offer it again.
     */
    suspend fun fetch(
        context: Context,
        anime: SpecialAnime,
        posters: List<Int>,
        onProgress: (Int, Int, Int?) -> Unit = { _, _, _ -> },
    ): List<Int> = withContext(Dispatchers.IO) {
        val kept = mutableListOf<Int>()
        var done = 0
        posters.forEach { poster ->
            val landed = runCatching { pull(context, anime, poster) }.getOrDefault(false)
            if (landed) {
                kept += poster
                _version.value += 1
            }
            done += 1
            // The poster that just landed is answered with the count, so the caller can close its
            // hole now rather than waiting for the rest of the set.
            onProgress(done, posters.size, poster.takeIf { landed })
        }
        kept
    }

    /** One poster, streamed to a temporary file and renamed. Throws when the host refuses. */
    private fun pull(context: Context, anime: SpecialAnime, poster: Int): Boolean {
        val file = file(context, anime.id, poster)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.part")
        val written = AppReleases.open(url(anime.id, poster)).use { response ->
            response.body.byteStream().use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            temp.length() > 0L
        }
        if (!written || (file.exists() && !file.delete())) {
            temp.delete()
            return false
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            return false
        }
        return true
    }
}
