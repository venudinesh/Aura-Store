package dev.aurastore.store.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The part of the pack the user keeps on the shelf.
 *
 * A poster ships inside the APK, so taking one off cannot hand its bytes back; what removal does is
 * take the poster out of both pickers, which is what "delete" means for art that came with the app.
 * A poster downloaded from the repository is a file on the phone, so removing that one deletes the
 * file as well. The record is the same either way: one set of `anime:poster` keys in preferences.
 *
 * The two cropped pictures are the user's own files, not the pack's, so those are deleted rather than
 * remembered. Nothing here can bring one back.
 */
object PackLibrary {

    private const val PREFS = "aurastore_pack"
    private const val KEY_REMOVED = "removed_posters"

    private val _removed = MutableStateFlow<Set<String>>(emptySet())
    val removed: StateFlow<Set<String>> = _removed.asStateFlow()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val stored = decode(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_REMOVED, null),
        )
        // A transfer that dies between its file landing and the shelf noting the return would leave
        // a poster on disk and on the removed list. The file wins: it is the picture, and the key is
        // only a note about a deletion whose bytes are already back.
        val shelf = stored.filterNot { downloaded(context, it) }.toSet()
        _removed.value = shelf
        if (shelf != stored) write(context, shelf)
    }

    /** Whether a `id:index` key already has its downloaded copy on the phone. */
    private fun downloaded(context: Context, key: String): Boolean {
        val parts = key.split(':')
        val poster = parts.getOrNull(1)?.toIntOrNull() ?: return false
        val id = parts.getOrNull(0) ?: return false
        return SpecialAnime.entries.any { it.id == id && PackArt.has(context, id, poster) }
    }

    /** A poster's key: the anime id, then the 0-based index. */
    fun key(anime: SpecialAnime, poster: Int): String = "${anime.id}:$poster"

    fun key(animeId: String, poster: Int): String = "$animeId:$poster"

    /**
     * Pure: the stored set back out of one string, or the empty set.
     *
     * Anything that is not `id:index` is dropped rather than trusted: the preference is a list of
     * numbers, and a corrupted tail must cost the tail, not the shelf.
     */
    fun decode(stored: String?): Set<String> = stored
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { KEY_SHAPE.matches(it) }
        ?.toSet()
        .orEmpty()

    fun isRemoved(anime: SpecialAnime, poster: Int): Boolean = key(anime, poster) in _removed.value

    /** The posters of [anime] still on the shelf, in gallery order. */
    fun visible(anime: SpecialAnime): List<Int> =
        anime.posters.indices.filterNot { isRemoved(anime, it) }

    /** The posters of [anime] that were taken off, in gallery order. */
    fun removedIn(anime: SpecialAnime): List<Int> =
        anime.posters.indices.filter { isRemoved(anime, it) }

    /** Every anime that still has at least one poster to show. */
    fun anime(): List<SpecialAnime> = SpecialAnime.entries.filter { visible(it).isNotEmpty() }

    /** Every anime that has something removed, with the posters that are gone. */
    fun removedGroups(): List<Pair<SpecialAnime, List<Int>>> = SpecialAnime.entries
        .mapNotNull { anime -> removedIn(anime).takeIf { it.isNotEmpty() }?.let { anime to it } }

    fun removedCount(): Int = SpecialAnime.entries.sumOf { removedIn(it).size }

    /**
     * Takes [posters] of [anime] off the shelf.
     *
     * A downloaded copy is deleted with it, which is the only removal that gives bytes back; the
     * bundled copy stays in the APK and simply stops being offered. [settle] then moves the profile
     * off a poster that is no longer there.
     */
    fun remove(context: Context, anime: SpecialAnime, posters: Collection<Int>) {
        val taken = posters.filter { it in anime.posters.indices }.toSet()
        if (taken.isEmpty()) return
        write(context, _removed.value + taken.map { key(anime, it) })
        taken.forEach { PackArt.delete(context, anime, it) }
        settle(context)
    }

    /** Puts [posters] of [anime] back on the shelf. The art itself never left for the bundled ones. */
    fun restore(context: Context, anime: SpecialAnime, posters: Collection<Int>) {
        val kept = posters.filter { it in anime.posters.indices }.toSet()
        if (kept.isEmpty()) return
        write(context, _removed.value - kept.map { key(anime, it) }.toSet())
    }

    /**
     * A worn choice that no longer exists moves to the next poster of its anime, or the one before it
     * when the deleted poster was the last on the shelf.
     *
     * The next poster rather than the mark or the House cover: the user deleted one picture, not the
     * whole profile, so the profile keeps a picture of the same anime until that anime runs out.
     */
    fun settle(context: Context) {
        val avatar = SpecialPick.from(Settings.avatar.value)
        if (avatar != null && isRemoved(avatar.anime, avatar.poster)) {
            val next = successor(avatar.poster, visible(avatar.anime))
            Settings.setAvatar(context, next?.let { SpecialPick(avatar.anime, it).choice })
        }
        val style = Settings.coverStyle.value
        val anime = style.anime ?: return
        if (!isRemoved(anime, Settings.coverPoster.value)) return
        val next = successor(Settings.coverPoster.value, visible(anime))
        if (next == null) {
            Settings.setCover(context, CoverStyle.HOUSE, 0)
        } else {
            Settings.setCover(context, style, next)
        }
    }

    /**
     * The poster that takes [poster]'s place on a [shelf] the poster left: the next number up, and
     * the last one below when there is nothing above it. Pure, so the choice can be read at a glance.
     */
    fun successor(poster: Int, shelf: List<Int>): Int? =
        shelf.firstOrNull { it > poster } ?: shelf.lastOrNull()

    /** Deletes the cropped avatar and returns to the Aura mark. */
    fun deleteAvatarPhoto(context: Context): Boolean {
        val gone = AvatarStore.clear(context)
        if (Settings.avatar.value == CUSTOM_AVATAR) Settings.setAvatar(context, null)
        return gone
    }

    /** Deletes the cropped cover and returns to the House art. */
    fun deleteCoverPhoto(context: Context): Boolean {
        val gone = CoverStore.clear(context)
        if (Settings.coverCustom.value) Settings.setCover(context, CoverStyle.HOUSE, 0)
        return gone
    }

    private fun write(context: Context, next: Set<String>) {
        _removed.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_REMOVED, next.sorted().joinToString(","))
        }
    }

    private val KEY_SHAPE = Regex("[0-9]+:[0-9]+")
}
