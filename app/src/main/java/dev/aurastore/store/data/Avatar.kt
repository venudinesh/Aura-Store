package dev.aurastore.store.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.annotation.StringRes
import dev.aurastore.store.R
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The eight faces that ship with the profile.
 *
 * Original emblems rather than borrowed characters: a sword, a wand, a gauge, a rocket, a shield, an
 * invader, a claw and a gem. They are drawn in code, so a face is the same picture at 32 dp in a row
 * and at 96 dp on the profile, with no bitmap to keep in a density folder.
 */
enum class GameFace(val id: String, @StringRes val label: Int) {
    KNIGHT("knight", R.string.profile_face_knight),
    WIZARD("wizard", R.string.profile_face_wizard),
    RACER("racer", R.string.profile_face_racer),
    ASTRO("astro", R.string.profile_face_astro),
    ARENA("arena", R.string.profile_face_arena),
    RETRO("retro", R.string.profile_face_retro),
    BEAST("beast", R.string.profile_face_beast),
    GEM("gem", R.string.profile_face_gem),
    ;

    /** What [Settings.avatar] holds while this face is chosen. */
    val choice: String get() = PREFIX + id

    companion object {
        private const val PREFIX = "face:"

        /** The face named by a stored choice, or null for a custom photo and the default mark. */
        fun from(choice: String?): GameFace? =
            choice?.takeIf { it.startsWith(PREFIX) }
                ?.removePrefix(PREFIX)
                ?.let { id -> entries.firstOrNull { it.id == id } }
    }
}

/** The marker [Settings.avatar] holds while the gallery crop is the picture. */
const val CUSTOM_AVATAR = "custom"

/**
 * The square the user cropped, kept as a PNG in the app's own files.
 *
 * The choice in [Settings] is only a word; the pixels are here. That keeps a 1024-pixel photo out of
 * SharedPreferences and makes replacing it one atomic write rather than an append to a settings blob.
 */
object AvatarStore {

    private const val FOLDER = "profile"
    private const val NAME = "avatar.png"

    /** Bumped whenever a new square lands, so a picture already on screen reloads. */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun file(context: Context): File = File(File(context.filesDir, FOLDER), NAME)

    /** A stamp that changes every time the file is written, for `remember` keys. */
    fun stamp(context: Context): Long = file(context).lastModified()

    fun load(context: Context): Bitmap? = runCatching {
        val file = file(context)
        if (!file.isFile) return null
        BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()

    /** Writes [bitmap] as the picture and answers whether it landed. */
    fun save(context: Context, bitmap: Bitmap): Boolean = runCatching {
        val file = file(context)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        _version.value += 1
        true
    }.getOrDefault(false)

    /**
     * Reads a gallery image at up to [max] pixels on its long side, honouring the EXIF rotation.
     *
     * Two passes: bounds first, so a 12-megapixel photo is never decoded at full size just to be
     * cropped to a square, then the sample. The orientation matters because the crop window is
     * square: a sideways photo would be cropped across the wrong axis with no way to tell.
     */
    fun decode(context: Context, uri: Uri, max: Int = 2048): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > max || bounds.outHeight / sample > max) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val orientation = resolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        if (matrix.isIdentity) {
            raw
        } else {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                .also { turned -> if (turned !== raw) raw.recycle() }
        }
    }.getOrNull()
}
