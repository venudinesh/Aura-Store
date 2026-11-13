package dev.aurastore.store.install

import dev.aurastore.store.data.Api
import dev.aurastore.store.data.DownloadFile
import dev.aurastore.store.data.Hosts
import dev.aurastore.store.data.PostDetail

/**
 * Chooses which file of a post to download for this device.
 *
 * The API returns every artefact of every version of a post, each tagged with `role`
 * (`main` / `patch` / `obb` / ...) and an `abi` (`universal`, `arm64-v8a`, ...). There is no
 * single "best" field, so the order below is the rule the UI follows:
 *
 * 1. base APK built for one of this device's own ABIs
 * 2. base APK that carries no ABI of its own (`universal`, blank, or a bundle of `splits`)
 * 3. the artefact the catalogue itself marks `recommended`
 * 4. the first file that runs on this device at all
 *
 * A file built for a processor this device does not have is never chosen, however little else the
 * post offers: handing one over used to end at the install sheet with
 * `INSTALL_FAILED_NO_MATCHING_ABIS` after the whole download had been paid for. When nothing in the
 * post can run here, [pick] returns null and [choose] says which of the two reasons applies.
 *
 * Files whose host is refusing requests are considered last: the catalogue mixes storage domains
 * in one post (some files on `sdk.appsupload.com`, some on a `liteapks.dev` host that answers 403
 * to everything), so "best build" means "best build that can actually be fetched".
 */
object QuickInstall {

    data class Pick(val versionLabel: String, val file: DownloadFile, val extras: List<DownloadFile> = emptyList())

    /** Why a post can or cannot be downloaded, so a tap can say which it was. */
    sealed interface Outcome {
        data class Ready(val pick: Pick) : Outcome
        data object None : Outcome
        data class NotForDevice(val abis: List<String>) : Outcome
    }

    private val PORTABLE = setOf("universal", "all", "any", "splits")

    suspend fun pick(postId: Long): Pick? = pick(Api.post(postId), Api.deviceAbis)

    suspend fun choose(postId: Long): Outcome {
        val detail = runCatching { Api.post(postId) }.getOrElse { return Outcome.None }
        val pick = pick(detail, Api.deviceAbis)
        if (pick != null) return Outcome.Ready(pick)
        // Published, but nothing in it runs on this phone — a different answer from "no file at all".
        return if (detail.versions.any { version -> version.files.isNotEmpty() }) {
            Outcome.NotForDevice(Api.deviceAbis)
        } else {
            Outcome.None
        }
    }

    fun pick(detail: PostDetail, abi: String): Pick? = pick(detail, listOf(abi))

    fun pick(detail: PostDetail, abis: List<String>): Pick? {
        val all = detail.versions.flatMap { version ->
            version.files.map { file -> version.label to file }
        }
        if (all.isEmpty()) return null

        // Prefer a host that is still answering; fall back to the full list if every host is down.
        val serving = all.filterNot { (_, f) -> Hosts.isBlocked(f.url) }
        val pool = serving.ifEmpty { all }.filter { (_, f) -> runsOn(f, abis) }
        if (pool.isEmpty()) return null

        // In the device's own ABI order, so a phone that can run both arm64 and v7a still gets arm64.
        val byAbi = abis.firstNotNullOfOrNull { want ->
            pool.firstOrNull { (_, f) -> isBase(f) && matches(f, want) }
        }
        val chosen = byAbi
            ?: pool.firstOrNull { (_, f) -> isBase(f) && isPortable(f) }
            ?: pool.firstOrNull { (_, f) -> isBase(f) }
            ?: pool.firstOrNull { (_, f) -> f.recommended }
            ?: pool.first()

        return Pick(
            versionLabel = chosen.first,
            file = chosen.second,
            // A game's archive ships in the same version entry as its package; a build chosen without
            // it stops at a loading screen.
            extras = Obb.companions(detail, chosen.first, chosen.second.url),
        )
    }

    /**
     * Other installable files of the same post, in the order a failed download should try them.
     * Only base APKs are offered — an OBB or a patch cannot stand in for the app itself — and only
     * ones this device can run, since a retry that cannot install is not a retry.
     */
    fun alternatives(detail: PostDetail, abi: String, exclude: Set<String>): List<DownloadFile> =
        alternatives(detail, listOf(abi), exclude)

    fun alternatives(detail: PostDetail, abis: List<String>, exclude: Set<String>): List<DownloadFile> =
        detail.versions.flatMap { it.files }
            .filter {
                isBase(it) && it.url.isNotBlank() && it.url !in exclude &&
                    !Hosts.isBlocked(it.url) && runsOn(it, abis)
            }
            .sortedWith(
                compareBy(
                    { !matchesAny(it, abis) },
                    { !isPortable(it) },
                ),
            )
            .distinctBy { it.url }

    fun isBase(file: DownloadFile): Boolean {
        val role = file.role.lowercase()
        val kind = file.kind.lowercase()
        return (role.isBlank() || role == "main") && kind != "obb" && kind != "patch"
    }

    /** True when [file] names one of this device's ABIs, as opposed to merely being installable. */
    fun matchesAny(file: DownloadFile, abis: List<String>): Boolean =
        abis.any { want -> matches(file, want) }

    fun matches(file: DownloadFile, abi: String): Boolean {
        val want = abi.trim().lowercase()
        if (want.isEmpty()) return false
        return abiTokens(file.abi).any { it == want }
    }

    /**
     * True when [file] can be installed on a device that reports [abis].
     *
     * An untagged file passes: with no ABI of its own there is nothing to contradict, and the
     * package manager is the one that decides. A tag listing several ABIs passes when any of them is
     * this device's, which is how a split bundle is published.
     */
    fun runsOn(file: DownloadFile, abis: List<String>): Boolean {
        val tags = abiTokens(file.abi)
        if (tags.isEmpty()) return true
        if (tags.any { it in PORTABLE }) return true
        return tags.any { tag -> abis.any { it.equals(tag, ignoreCase = true) } }
    }

    private fun isPortable(file: DownloadFile): Boolean {
        val tags = abiTokens(file.abi)
        return tags.isEmpty() || tags.any { it in PORTABLE }
    }

    private fun abiTokens(abi: String): List<String> =
        abi.lowercase().split(',', ';', ' ', '/', '|').map { it.trim() }.filter { it.isNotEmpty() }
}
