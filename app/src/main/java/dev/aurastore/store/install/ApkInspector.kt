package dev.aurastore.store.install

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

data class ApkInspection(
    val valid: Boolean,
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val signerSha256: String? = null,
    val error: String? = null,
)

/**
 * Local verification of a downloaded package.
 *
 * The catalogue API does not publish file hashes or signing-certificate digests (see the gap
 * noted in blend_blueprint/03_blend), so this can only establish that the file *is* a parseable
 * APK, record its identity, and pin the signing certificate for later comparisons. It cannot
 * prove the artefact matches what the catalogue intended to serve.
 */
object ApkInspector {

    fun inspect(context: Context, file: File): ApkInspection {
        if (!file.isFile || file.length() == 0L) {
            return ApkInspection(valid = false, error = "empty file")
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val info: PackageInfo? = try {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        } catch (e: Exception) {
            null
        }
        if (info == null) {
            return ApkInspection(valid = false, error = "not a valid APK (failed to parse)")
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        val signer = try {
            val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION") info.signatures?.firstOrNull()?.toByteArray()
            }
            bytes?.let { sha256(it) }
        } catch (e: Exception) {
            null
        }
        return ApkInspection(
            valid = true,
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = versionCode,
            signerSha256 = signer,
        )
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
