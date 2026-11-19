package dev.aurastore.store.install

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies a verified file out of the download cache and into the device's own Downloads folder, so an
 * APK can be kept, sideloaded elsewhere or archived after the queue clears. The same route serves a
 * game's archive, an expansion file that `Android/obb` will not let this app write to, and any other
 * artefact the store downloaded but cannot install.
 */
object ApkExport {

    private const val APK_MIME = "application/vnd.android.package-archive"

    /** The public folder under Downloads every saved artefact lands in. */
    const val SUB_DIR = "Aura Store"

    /** Extensions the installer owns; everything else is an archive and keeps its own name. */
    private val PACKAGE_EXTENSIONS = setOf("apk", "apks", "xapk")

    /** Whether [fileName] names something Android can install rather than an archive to keep. */
    fun isPackage(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in PACKAGE_EXTENSIONS

    /** Returns a human-readable location on success, or null when nothing could be written. */
    fun save(context: Context, file: File, displayName: String): String? =
        save(context, file, displayName, SUB_DIR, mimeFor(displayName))

    /**
     * True when `Downloads/[subDir]/[displayName]` is already there.
     *
     * On Q and later the answer comes from MediaStore, because scoped storage does not let this app
     * stat a file it did not create with a plain [File]. Below Q the folder is readable directly.
     */
    fun exists(context: Context, displayName: String, subDir: String = SUB_DIR): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relative = "${Environment.DIRECTORY_DOWNLOADS}/$subDir"
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?, ?)"
            val ids = mutableListOf<Long>()
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                // MediaStore has stored RELATIVE_PATH with and without the trailing slash across
                // releases; both spellings name the same folder.
                arrayOf(displayName, "$relative/", relative),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) ids += cursor.getLong(0)
            }
            // A row is not a file. Deleting through Files updates MediaStore, but a crash, a factory
            // reset or a shell delete can leave a row behind whose bytes are gone; opening the entry
            // is the only answer that says whether a copy is really there.
            ids.any { id ->
                runCatching {
                    context.contentResolver.openAssetFileDescriptor(
                        ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                        "r",
                    )?.use { true } ?: false
                }.getOrDefault(false)
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                subDir,
            )
            File(dir, displayName).isFile
        }
    }.getOrDefault(false)

    /**
     * The name a saved copy carries.
     *
     * A package is renamed to its title and version, which is what makes two builds of the same app
     * distinguishable in Downloads. An archive keeps the name the download was quoted: a `.zip`,
     * `.rar` or `.iso` is the thing the user came for, and its extension is the only label that says
     * what the bytes are. Path separators are stripped either way, and the generated package name is
     * capped so a very long title cannot exceed the filesystem's limit.
     */
    fun savedName(fileName: String, title: String, version: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in PACKAGE_EXTENSIONS) {
            val bare = fileName.substringAfterLast('/').substringAfterLast('\\')
            if (bare.isNotBlank() && bare != "." && bare != "..") return bare
        }
        val safeTitle = "$title-$version"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(80)
        return "$safeTitle.${extension.ifEmpty { "apk" }}"
    }

    /** The type MediaStore should file [name] under, so the file opens as what it is. */
    internal fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "apk" -> APK_MIME
        "apks", "xapk", "zip" -> "application/zip"
        "rar" -> "application/vnd.rar"
        "7z" -> "application/x-7z-compressed"
        "iso" -> "application/x-iso9660-image"
        "tar" -> "application/x-tar"
        "gz", "gzip" -> "application/gzip"
        "bz2" -> "application/x-bzip2"
        "xz" -> "application/x-xz"
        else -> "application/octet-stream"
    }

    /**
     * Writes [file] into `Downloads/[subDir]/[displayName]`. On Q and later this goes through
     * MediaStore, because a plain file write into shared storage is blocked; below Q the folder is
     * addressed directly.
     */
    fun save(context: Context, file: File, displayName: String, subDir: String, mime: String): String? {
        if (!file.isFile || file.length() == 0L) return null
        // Callers pass a final name. The one fallback is a package asset that arrived without an
        // extension: an APK needs its suffix for the installer to find it. An archive is saved as it
        // was named, extension or not.
        val name = if (displayName.contains('.') || mime != APK_MIME) displayName else "$displayName.apk"
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subDir")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: return null
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Downloads/$subDir/$name"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    subDir,
                ).apply { mkdirs() }
                val target = File(dir, name)
                file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                "Downloads/$subDir/$name"
            }
        }.getOrNull()
    }
}
