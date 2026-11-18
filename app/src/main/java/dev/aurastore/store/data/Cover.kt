package dev.aurastore.store.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.aurastore.store.R
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The picture behind the profile.
 *
 * Four of them are drawn in code, so a fresh install has a cover without a gallery visit and the art
 * follows the phone's own density. The thirty-one in the Special pack are painted art from other
 * hands, each an anime with a gallery of thirty posters shipped at 1024 x 576 in `drawable-nodpi`.
 * The last is the user's photograph, kept as a file beside the avatar rather than in preferences.
 */
enum class CoverStyle(
    val id: String,
    @StringRes val label: Int,
    @StringRes val note: Int,
    val anime: SpecialAnime? = null,
) {
    HOUSE("house", R.string.cover_house, R.string.cover_house_note),
    AURORA("aurora", R.string.cover_aurora, R.string.cover_aurora_note),
    EMBER("ember", R.string.cover_ember, R.string.cover_ember_note),
    INK("ink", R.string.cover_ink, R.string.cover_ink_note),
    SPECIAL_1("special-1", R.string.special_1, R.string.special_covers_note, SpecialAnime.NARUTO),
    SPECIAL_2("special-2", R.string.special_2, R.string.special_covers_note, SpecialAnime.ONE_PIECE),
    SPECIAL_3("special-3", R.string.special_3, R.string.special_covers_note, SpecialAnime.ATTACK_ON_TITAN),
    SPECIAL_4("special-4", R.string.special_4, R.string.special_covers_note, SpecialAnime.DEMON_SLAYER),
    SPECIAL_5("special-5", R.string.special_5, R.string.special_covers_note, SpecialAnime.JUJUTSU_KAISEN),
    SPECIAL_6("special-6", R.string.special_6, R.string.special_covers_note, SpecialAnime.MY_HERO_ACADEMIA),
    SPECIAL_7("special-7", R.string.special_7, R.string.special_covers_note, SpecialAnime.DEATH_NOTE),
    SPECIAL_8("special-8", R.string.special_8, R.string.special_covers_note, SpecialAnime.FULLMETAL_ALCHEMIST),
    SPECIAL_9("special-9", R.string.special_9, R.string.special_covers_note, SpecialAnime.DRAGON_BALL),
    SPECIAL_10("special-10", R.string.special_10, R.string.special_covers_note, SpecialAnime.SWORD_ART_ONLINE),
    SPECIAL_11("special-11", R.string.special_11, R.string.special_covers_note, SpecialAnime.TOKYO_GHOUL),
    SPECIAL_12("special-12", R.string.special_12, R.string.special_covers_note, SpecialAnime.CHAINSAW_MAN),
    SPECIAL_13("special-13", R.string.special_13, R.string.special_covers_note, SpecialAnime.SPY_X_FAMILY),
    SPECIAL_14("special-14", R.string.special_14, R.string.special_covers_note, SpecialAnime.ONE_PUNCH_MAN),
    SPECIAL_15("special-15", R.string.special_15, R.string.special_covers_note, SpecialAnime.HUNTER_X_HUNTER),
    SPECIAL_16("special-16", R.string.special_16, R.string.special_covers_note, SpecialAnime.BLEACH),
    SPECIAL_17("special-17", R.string.special_17, R.string.special_covers_note, SpecialAnime.SOLO_LEVELING),
    SPECIAL_18("special-18", R.string.special_18, R.string.special_covers_note, SpecialAnime.FRIEREN),
    SPECIAL_19("special-19", R.string.special_19, R.string.special_covers_note, SpecialAnime.EVANGELION),
    SPECIAL_20("special-20", R.string.special_20, R.string.special_covers_note, SpecialAnime.COWBOY_BEBOP),
    SPECIAL_21("special-21", R.string.special_21, R.string.special_covers_note, SpecialAnime.SAILOR_MOON),
    SPECIAL_22("special-22", R.string.special_22, R.string.special_covers_note, SpecialAnime.POKEMON),
    SPECIAL_23("special-23", R.string.special_23, R.string.special_covers_note, SpecialAnime.SPIRITED_AWAY),
    SPECIAL_24("special-24", R.string.special_24, R.string.special_covers_note, SpecialAnime.YOUR_NAME),
    SPECIAL_25("special-25", R.string.special_25, R.string.special_covers_note, SpecialAnime.VINLAND_SAGA),
    SPECIAL_26("special-26", R.string.special_26, R.string.special_covers_note, SpecialAnime.MOB_PSYCHO),
    SPECIAL_27("special-27", R.string.special_27, R.string.special_covers_note, SpecialAnime.BLACK_CLOVER),
    SPECIAL_28("special-28", R.string.special_28, R.string.special_covers_note, SpecialAnime.BLUE_LOCK),
    SPECIAL_29("special-29", R.string.special_29, R.string.special_covers_note, SpecialAnime.CODE_GEASS),
    SPECIAL_30("special-30", R.string.special_30, R.string.special_covers_note, SpecialAnime.DANDADAN),
    SPECIAL_31("special-31", R.string.special_31, R.string.special_covers_note, SpecialAnime.JOJO),
    ;

    /** True for the painted pack: [CoverArt] reads a bitmap instead of drawing. */
    val special: Boolean get() = anime != null

    /** The first poster of the pack, or 0 for a drawn cover. */
    @get:DrawableRes val art: Int get() = anime?.posters?.first() ?: 0

    /** The poster at [index], clamped to the gallery; null for a drawn cover, which has no file. */
    @DrawableRes fun poster(index: Int): Int? =
        anime?.let { it.posters[index.coerceIn(0, it.posters.lastIndex)] }

    companion object {
        fun from(id: String?): CoverStyle = entries.firstOrNull { it.id == id } ?: HOUSE

        /** The pack entry for [anime]; the ids are numbered the same way as the enum. */
        fun forAnime(anime: SpecialAnime): CoverStyle = from("special-${anime.id}")
    }
}

/** The wide photograph the user cropped, kept as a PNG in the app's own files. */
object CoverStore {

    private const val FOLDER = "profile"
    private const val NAME = "cover.png"

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun file(context: Context): File = File(File(context.filesDir, FOLDER), NAME)

    fun load(context: Context): Bitmap? = runCatching {
        val file = file(context)
        if (!file.isFile) return null
        BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()

    fun save(context: Context, bitmap: Bitmap): Boolean = runCatching {
        val file = file(context)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        _version.value += 1
        true
    }.getOrDefault(false)

    /** Deletes the cropped photograph, answering whether there was a file to delete. */
    fun clear(context: Context): Boolean {
        val gone = runCatching { file(context).delete() }.getOrDefault(false)
        if (gone) _version.value += 1
        return gone
    }
}
