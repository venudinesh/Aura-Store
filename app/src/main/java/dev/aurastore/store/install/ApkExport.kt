package dev.aurastore.store.install

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies a verified package out of the download cache and into the device's own Downloads folder,
 * so an APK can be kept, sideloaded elsewhere or archived after the queue clears. The same route
 * serves an expansion file that `Android/obb` will not let this app write to.
 */
object ApkExport {

    private const val APK_MIME = "application/vnd.android.package-archive"

    /** Returns a human-readable location on success, or null when nothing could be written. */
    fun save(context: Context, file: File, displayName: String): String? =
        save(context, file, displayName, "Aura Store", APK_MIME)

    /**
     * Writes [file] into `Downloads/[subDir]/[displayName]`. On Q and later this goes through
     * MediaStore, because a plain file write into shared storage is blocked; below Q the folder is
     * addressed directly.
     */
    fun save(context: Context, file: File, displayName: String, subDir: String, mime: String): String? {
        if (!file.isFile || file.length() == 0L) return null
        val name = displayName.takeIf { it.contains('.') } ?: "$displayName.${mimeExtension(mime)}"
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

    private fun mimeExtension(mime: String): String = if (mime == APK_MIME) "apk" else "obb"
}
