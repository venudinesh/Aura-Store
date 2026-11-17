package dev.aurastore.store.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
 * follows the phone's own density. The fifth is the user's photograph, kept as a file beside the
 * avatar rather than in preferences.
 */
enum class CoverStyle(val id: String, @StringRes val label: Int, @StringRes val note: Int) {
    HOUSE("house", R.string.cover_house, R.string.cover_house_note),
    AURORA("aurora", R.string.cover_aurora, R.string.cover_aurora_note),
    EMBER("ember", R.string.cover_ember, R.string.cover_ember_note),
    INK("ink", R.string.cover_ink, R.string.cover_ink_note),
    ;

    companion object {
        fun from(id: String?): CoverStyle = entries.firstOrNull { it.id == id } ?: HOUSE
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
}
