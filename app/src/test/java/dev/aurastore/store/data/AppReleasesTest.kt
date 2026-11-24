package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GitHub's release payload into the one asset this installation may take.
 *
 * The updater trusts three things from this parse: the tag is a version, the asset matches the
 * signature of the running build, and a SHA-256 was published. Each is tested as a refusal, because
 * the failure that matters is not "the parse returned null" but "the parse returned a file the
 * installer will reject".
 */
class AppReleasesTest {

    private val sha = "1031d9ba69a60254315a91b85c2523ba4019e5ab205cfb4a4c9491f7353b67c8"

    private fun release(
        tag: String = "v1.1.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = defaultAssets(),
    ) = """
        {
          "tag_name": "$tag",
          "name": "Aura Store 1.1.0",
          "body": "Server tabs, new-and-updated first.",
          "html_url": "https://github.com/venudinesh/Aura-Store/releases/tag/$tag",
          "draft": $draft,
          "prerelease": $prerelease,
          "assets": [ $assets ]
        }
    """.trimIndent()

    private fun asset(name: String, url: String, size: Long = 1000, digest: String? = null) = """
        {
          "name": "$name",
          "size": $size,
          "browser_download_url": "$url",
          "digest": ${digest?.let { "\"$it\"" } ?: "null"}
        }
    """.trimIndent()

    private fun defaultAssets() = listOf(
        asset(
            "Aura-Store-v1.1.0.apk",
            "https://github.com/venudinesh/Aura-Store/releases/download/v1.1.0/Aura-Store-v1.1.0.apk",
            2_464_594,
            "sha256:$sha",
        ),
        asset(
            "Aura-Store-v1.1.0-debug.apk",
            "https://github.com/venudinesh/Aura-Store/releases/download/v1.1.0/Aura-Store-v1.1.0-debug.apk",
            15_664_444,
            "sha256:$sha",
        ),
        asset(
            "Aura-Store-v1.1.0.apk.sha256",
            "https://github.com/venudinesh/Aura-Store/releases/download/v1.1.0/Aura-Store-v1.1.0.apk.sha256",
            80,
        ),
    ).joinToString(",")

    @Test
    fun aReleaseBuildTakesTheSignedApk() {
        val release = AppReleases.parse(release(), debuggable = false)!!
        assertEquals("1.1.0", release.version.text)
        assertEquals("v1.1.0", release.tag)
        assertEquals("Aura-Store-v1.1.0.apk", release.apkName)
        assertEquals(2_464_594L, release.apkSize)
        assertEquals(sha, release.sha256)
        assertEquals("Server tabs, new-and-updated first.", release.notes)
        assertEquals(
            "https://github.com/venudinesh/Aura-Store/releases/download/v1.1.0/Aura-Store-v1.1.0.apk.sha256",
            release.checksumUrl,
        )
    }

    @Test
    fun aDebugBuildTakesTheDebugApkAndNotTheSignedOne() {
        val release = AppReleases.parse(release(), debuggable = true)!!
        assertEquals("Aura-Store-v1.1.0-debug.apk", release.apkName)
        assertEquals(15_664_444L, release.apkSize)
    }

    @Test
    fun bothBuildsTravelAndThePreferenceChoosesBetweenThem() {
        val release = AppReleases.parse(release(), debuggable = false)!!
        assertEquals(2, release.builds.size)
        assertFalse(release.builds.first().debug)
        assertTrue(release.has(true))
        assertTrue(release.has(false))
        assertEquals("Aura-Store-v1.1.0.apk", release.build(UpdateBuild.AUTO)?.name)
        assertEquals("Aura-Store-v1.1.0.apk", release.build(UpdateBuild.RELEASE)?.name)
        assertEquals("Aura-Store-v1.1.0-debug.apk", release.build(UpdateBuild.DEBUG)?.name)
    }

    @Test
    fun theDebugBuildIsTheInstalledOneOnADebugInstall() {
        val release = AppReleases.parse(release(), debuggable = true)!!
        assertEquals("Aura-Store-v1.1.0-debug.apk", release.installed.name)
        assertEquals("Aura-Store-v1.1.0-debug.apk", release.build(UpdateBuild.AUTO)?.name)
        assertEquals("Aura-Store-v1.1.0.apk", release.build(UpdateBuild.RELEASE)?.name)
    }

    @Test
    fun aPreferenceThatCannotReplaceThisCopyDoesNotStartTheOffer() {
        val release = AppReleases.parse(release(), debuggable = false)!!
        // A release install whose stored preference is debug must still start on the signed build:
        // the debug file can only be saved, and saving is not what "Update now" means.
        assertEquals(
            "Aura-Store-v1.1.0.apk",
            release.startingBuild(UpdateBuild.DEBUG) { !it.debug }.name,
        )
        assertEquals(
            "Aura-Store-v1.1.0-debug.apk",
            release.startingBuild(UpdateBuild.DEBUG) { true }.name,
        )
        assertEquals(
            "Aura-Store-v1.1.0.apk",
            release.startingBuild(UpdateBuild.AUTO) { true }.name,
        )
    }

    @Test
    fun aBuildTheReleaseDoesNotCarryIsNullNotTheOtherBuild() {
        val onlySigned = AppReleases.parse(
            release(assets = asset("Aura-Store-v1.1.0.apk", "https://example.test/a.apk", 10)),
            debuggable = false,
        )!!
        assertFalse(onlySigned.has(true))
        assertNull(onlySigned.build(UpdateBuild.DEBUG))
        assertEquals("Aura-Store-v1.1.0.apk", onlySigned.build(UpdateBuild.RELEASE)?.name)
    }

    @Test
    fun aBuildWithNoMatchingSignatureHasNothingToOffer() {
        val onlySigned = release(assets = asset("Aura-Store-v1.1.0.apk", "https://example.test/a.apk", 10))
        assertNull(AppReleases.parse(onlySigned, debuggable = true))
        val onlyDebug = release(assets = asset("Aura-Store-v1.1.0-debug.apk", "https://example.test/a.apk", 10))
        assertNull(AppReleases.parse(onlyDebug, debuggable = false))
    }

    @Test
    fun anAssetWithoutADownloadUrlHasNothingToOffer() {
        val noUrl = release(assets = asset("Aura-Store-v1.1.0.apk", ""))
        assertNull(AppReleases.parse(noUrl, debuggable = false))
    }

    @Test
    fun draftsAndPreReleasesAreSkipped() {
        assertNull(AppReleases.parse(release(draft = true), debuggable = false))
        assertNull(AppReleases.parse(release(prerelease = true), debuggable = false))
    }

    @Test
    fun aTagThatIsNotAVersionHasNothingToOffer() {
        assertNull(AppReleases.parse(release(tag = "nightly"), debuggable = false))
        assertNull(AppReleases.parse(release(tag = ""), debuggable = false))
        assertNull(AppReleases.parse("""{"message":"Not Found"}""", debuggable = false))
        assertNull(AppReleases.parse("not json", debuggable = false))
    }

    @Test
    fun theChecksumAssetIsNotMistakenForAnApk() {
        val onlyChecksum = release(
            assets = asset(
                "Aura-Store-v1.1.0.apk.sha256",
                "https://example.test/Aura-Store-v1.1.0.apk.sha256",
                80,
            ),
        )
        assertNull(AppReleases.parse(onlyChecksum, debuggable = false))
    }

    @Test
    fun aDigestIsReadOnlyWhenItIsASha256() {
        assertEquals(sha, AppReleases.sha256Of("sha256:$sha"))
        assertEquals(sha, AppReleases.sha256Of("sha256:${sha.uppercase()}"))
        assertNull(AppReleases.sha256Of(null))
        assertNull(AppReleases.sha256Of(""))
        assertNull(AppReleases.sha256Of("sha512:$sha"))
        assertNull(AppReleases.sha256Of("sha256:abc"))
    }

    @Test
    fun aChecksumFileIsReadByItsHexToken() {
        assertEquals(sha, AppReleases.checksum("$sha  Aura-Store-v1.1.0.apk\n"))
        assertEquals(sha, AppReleases.checksum("${sha.uppercase()} *Aura-Store-v1.1.0-debug.apk"))
        assertEquals(sha, AppReleases.checksum("SHA256 (Aura-Store-v1.1.0.apk) = $sha"))
        assertNull(AppReleases.checksum("no hash here"))
    }

    @Test
    fun theAssetNameSaysWhichSignatureItCarries() {
        assertTrue(AppReleases.isDebugAsset("Aura-Store-v1.1.0-debug.apk"))
        assertTrue(AppReleases.isDebugAsset("Aura-Store-v1.1.0-DEBUG.apk"))
        assertFalse(AppReleases.isDebugAsset("Aura-Store-v1.1.0.apk"))
    }
}
