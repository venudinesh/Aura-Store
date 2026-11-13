package dev.aurastore.store.install

import dev.aurastore.store.data.DownloadFile
import dev.aurastore.store.data.Hosts
import dev.aurastore.store.data.PostDetail

/**
 * The extra data a game ships beside its APK.
 *
 * The catalogue puts those files in the *same version entry* as the package they belong to, tagged
 * `role`/`version_download_type` = `obb` (sometimes a plain `.zip` holding the `.obb` files). Play
 * downloads them without the user seeing them; here they are separate queue rows, because Android
 * will not let a third-party app write into `Android/obb` itself, so the user has to be able to see
 * and act on that step.
 *
 * Everything in this object is pure string and model work — the naming rule and the pairing rule are
 * the parts that can be wrong, and both are tested without a device.
 */
object Obb {

    /** The [DownloadItem.dataFile] marker for these artefacts. */
    const val DATA_KIND = "obb"

    /** True when a catalogue artefact is additional data rather than the package itself. */
    fun isDataFile(file: DownloadFile): Boolean {
        if (!QuickInstall.isBase(file)) return true
        // A host can mislabel an archive as `main`; the extension is the second opinion.
        val name = pathName(file.url)
        return name.endsWith(".obb", true) || (name.endsWith(".zip", true) && !name.endsWith(".apk", true))
    }

    /**
     * Files that belong to the chosen build: same version entry, not the APK that was chosen, and
     * from a host that is serving. Order is kept as published.
     */
    fun companions(detail: PostDetail, versionLabel: String, chosenUrl: String): List<DownloadFile> {
        val version = detail.versions.firstOrNull { it.label == versionLabel } ?: return emptyList()
        return version.files
            .filter { it.url.isNotBlank() && it.url != chosenUrl && isDataFile(it) && !Hosts.isBlocked(it.url) }
            .distinctBy { it.url }
    }

    /** `main.12345.com.game.obb` / `split.12345.com.game.obb` */
    fun isObbName(name: String): Boolean = obbParts(name) != null

    /** The package an obb file name declares, or null when the name is not an obb name. */
    fun packageOf(name: String): String? = obbParts(name)?.packageName

    /** The version code an obb file name declares. */
    fun versionCodeOf(name: String): Long? = obbParts(name)?.versionCode?.toLongOrNull()

    /**
     * The name Android will accept for this file.
     *
     * A game looks its expansion up by name, and the lookup uses the *installed* package's version
     * code. Modded builds rename the obb to the original app's code all the time, which leaves the
     * game asking for data it cannot find, so the version component is rewritten to match the package
     * it is being installed next to. Returns null when the name says nothing we can place.
     */
    fun canonicalName(name: String, packageName: String, versionCode: Long?): String? {
        val parts = obbParts(name) ?: return null
        val code = versionCode ?: parts.versionCode ?: return null
        return "${parts.prefix}.$code.$packageName.obb"
    }

    /** A directory or file name that is safe to build a path — and a shell command — from. */
    fun safeSegment(value: String): String? =
        value.takeIf { it.isNotBlank() && it.length <= 200 && SEGMENT.matches(it) }

    /** Where Android expects a package's expansion files. */
    fun obbDir(storageRoot: String, packageName: String): String = "$storageRoot/Android/obb/$packageName"

    private val SEGMENT = Regex("[A-Za-z0-9._+-]+")

    private data class ObbParts(val prefix: String, val versionCode: String?, val packageName: String)

    /**
     * Splits `main.<code>.<pkg>.obb`. The package itself contains dots, so the parts are counted
     * from the end rather than split on every dot.
     */
    private fun obbParts(name: String): ObbParts? {
        val file = name.substringAfterLast('/').substringAfterLast('\\')
        if (!file.endsWith(".obb", ignoreCase = true)) return null
        val pieces = file.dropLast(4).split('.')
        if (pieces.size < 3) return null
        val prefix = pieces.first()
        if (prefix != "main" && prefix != "split" && prefix != "patch") return null
        // A numeric second piece is the version code; anything else means the archive ignored the
        // convention and the package starts right after the prefix.
        val code = pieces[1].takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }
        val packageName = pieces.subList(if (code == null) 1 else 2, pieces.size).joinToString(".")
        if (!SEGMENT.matches(packageName) || packageName.length < 3) return null
        return ObbParts(prefix, code, packageName)
    }

    private fun pathName(url: String): String =
        url.substringBefore('#').substringBefore('?').substringAfterLast('/')
}
