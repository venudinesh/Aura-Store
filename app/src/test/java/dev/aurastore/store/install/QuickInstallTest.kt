package dev.aurastore.store.install

import dev.aurastore.store.data.DownloadFile
import dev.aurastore.store.data.Hosts
import dev.aurastore.store.data.PostDetail
import dev.aurastore.store.data.PostSummary
import dev.aurastore.store.data.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue returns every artefact of every version of a post, so the selection rule is the
 * part of the download flow that most easily goes wrong. These cases pin it down.
 */
class QuickInstallTest {

    private fun file(
        url: String,
        abi: String = "universal",
        role: String = "main",
        kind: String = "apk",
        recommended: Boolean = false,
    ) = DownloadFile(
        kind = kind,
        role = role,
        abi = abi,
        sizeText = "1 MB",
        url = url,
        note = null,
        recommended = recommended,
    )

    private fun detail(vararg versions: Version) = PostDetail(
        summary = PostSummary(
            id = 1L,
            title = "Test",
            imageUrl = null,
            bannerUrl = null,
            publisher = null,
            genre = null,
            size = null,
            type = null,
            modInfo = null,
            latestVersion = "1.0",
            packageName = null,
            ratingAvg = 0.0,
            ratingCount = 0,
            views = 0L,
            updatedAt = null,
        ),
        description = "",
        tabs = emptyList(),
        gallery = emptyList(),
        versions = versions.toList(),
        originalUrl = null,
    )

    @Test
    fun `prefers the base apk matching this device abi`() {
        val pick = QuickInstall.pick(
            detail(
                Version(
                    label = "1.0",
                    note = null,
                    files = listOf(
                        file("https://host/universal.apk"),
                        file("https://host/arm64.apk", abi = "arm64-v8a"),
                        file("https://host/armeabi.apk", abi = "armeabi-v7a"),
                    ),
                ),
            ),
            abi = "arm64-v8a",
        )
        assertEquals("https://host/arm64.apk", pick?.file?.url)
        assertEquals("1.0", pick?.versionLabel)
    }

    @Test
    fun `falls back to universal when the device abi is absent`() {
        val pick = QuickInstall.pick(
            detail(
                Version(
                    label = "2.0",
                    note = null,
                    files = listOf(
                        file("https://host/armeabi.apk", abi = "armeabi-v7a"),
                        file("https://host/universal.apk"),
                    ),
                ),
            ),
            abi = "x86_64",
        )
        assertEquals("https://host/universal.apk", pick?.file?.url)
    }

    @Test
    fun `skips obb and patch artefacts when a base apk exists`() {
        val pick = QuickInstall.pick(
            detail(
                Version(
                    label = "3.0",
                    note = null,
                    files = listOf(
                        file("https://host/patch.apk", kind = "patch", role = "patch"),
                        file("https://host/obb.zip", kind = "obb", role = "obb"),
                        file("https://host/base.apk"),
                    ),
                ),
            ),
            abi = "arm64-v8a",
        )
        assertEquals("https://host/base.apk", pick?.file?.url)
    }

    @Test
    fun `falls back to the recommended artefact when nothing is a base apk`() {
        val pick = QuickInstall.pick(
            detail(
                Version(
                    label = "4.0",
                    note = null,
                    files = listOf(
                        file("https://host/obb.zip", kind = "obb", role = "obb"),
                        file("https://host/extra.zip", kind = "obb", role = "obb", recommended = true),
                    ),
                ),
            ),
            abi = "arm64-v8a",
        )
        assertEquals("https://host/extra.zip", pick?.file?.url)
    }

    @Test
    fun `returns the newest version first when several are listed`() {
        val pick = QuickInstall.pick(
            detail(
                Version(label = "5.0", note = null, files = listOf(file("https://host/new.apk"))),
                Version(label = "4.0", note = null, files = listOf(file("https://host/old.apk"))),
            ),
            abi = "arm64-v8a",
        )
        assertEquals("https://host/new.apk", pick?.file?.url)
        assertEquals("5.0", pick?.versionLabel)
    }

    @Test
    fun `prefers a build on a serving host over a better match on a refused one`() {
        val refused = "https://refusing.example.org/arm64.apk"
        Hosts.markBlocked(refused)
        try {
            val pick = QuickInstall.pick(
                detail(
                    Version(
                        label = "1.0",
                        note = null,
                        files = listOf(
                            file(refused, abi = "arm64-v8a"),
                            file("https://sdk.appsupload.com/universal.apk"),
                        ),
                    ),
                ),
                abi = "arm64-v8a",
            )
            assertEquals("https://sdk.appsupload.com/universal.apk", pick?.file?.url)
        } finally {
            Hosts.markServing(refused)
        }
    }

    @Test
    fun `offers alternatives from serving hosts only, best match first`() {
        val blocked = "https://refusing.example.org/arm64.apk"
        Hosts.markBlocked(blocked)
        try {
            val detail = detail(
                Version(
                    label = "1.0",
                    note = null,
                    files = listOf(
                        file(blocked, abi = "arm64-v8a"),
                        file("https://sdk.appsupload.com/universal.apk"),
                        file("https://sdk.appsupload.com/arm64.apk", abi = "arm64-v8a"),
                        file("https://sdk.appsupload.com/pack.zip", kind = "obb", role = "obb"),
                    ),
                ),
            )
            val alternatives = QuickInstall.alternatives(detail, abi = "arm64-v8a", exclude = setOf(blocked))
            assertEquals(
                listOf("https://sdk.appsupload.com/arm64.apk", "https://sdk.appsupload.com/universal.apk"),
                alternatives.map { it.url },
            )
            assertTrue(alternatives.none { Hosts.isBlocked(it.url) })
            // The refused file must not be offered back as an alternative.
            assertTrue(alternatives.none { it.url == blocked })
        } finally {
            Hosts.markServing(blocked)
        }
    }

    @Test
    fun `never offers a build for a processor this device does not have`() {
        // The catalogue publishes 32-bit mods on posts that have no 64-bit build at all. Installing
        // one ends at the system sheet with NO_MATCHING_ABIS after the whole download.
        val pick = QuickInstall.pick(
            detail(Version("1.0", null, listOf(file("https://host/v7a.apk", abi = "armeabi-v7a")))),
            abi = "arm64-v8a",
        )
        assertNull(pick)
    }

    @Test
    fun `prefers an exact abi over universal even when universal is listed first`() {
        val pick = QuickInstall.pick(
            detail(
                Version(
                    label = "1.0",
                    note = null,
                    files = listOf(
                        file("https://host/universal.apk"),
                        file("https://host/arm64.apk", abi = "arm64-v8a"),
                    ),
                ),
            ),
            abi = "arm64-v8a",
        )
        assertEquals("https://host/arm64.apk", pick?.file?.url)
    }

    @Test
    fun `accepts a file that carries several abis`() {
        val pick = QuickInstall.pick(
            detail(Version("1.0", null, listOf(file("https://host/multi.apk", abi = "x86_64, arm64-v8a")))),
            abi = "arm64-v8a",
        )
        assertEquals("https://host/multi.apk", pick?.file?.url)
    }

    @Test
    fun `follows the device abi order when it runs more than one`() {
        val pick = QuickInstall.pick(
            detail(
                Version(
                    label = "1.0",
                    note = null,
                    files = listOf(
                        file("https://host/v7a.apk", abi = "armeabi-v7a"),
                        file("https://host/arm64.apk", abi = "arm64-v8a"),
                    ),
                ),
            ),
            abis = listOf("arm64-v8a", "armeabi-v7a"),
        )
        assertEquals("https://host/arm64.apk", pick?.file?.url)
    }

    @Test
    fun `runsOn reads blank and split bundles as installable`() {
        assertTrue(QuickInstall.runsOn(file("https://host/plain.apk", abi = ""), listOf("arm64-v8a")))
        assertTrue(QuickInstall.runsOn(file("https://host/split.apk", abi = "splits"), listOf("arm64-v8a")))
        assertTrue(QuickInstall.runsOn(file("https://host/x86.apk", abi = "x86"), listOf("arm64-v8a", "x86")))
        assertTrue(!QuickInstall.runsOn(file("https://host/x86.apk", abi = "x86"), listOf("arm64-v8a")))
    }

    @Test
    fun `alternatives leave out builds for other processors`() {
        val detail = detail(
            Version(
                label = "1.0",
                note = null,
                files = listOf(
                    file("https://host/v7a.apk", abi = "armeabi-v7a"),
                    file("https://host/arm64.apk", abi = "arm64-v8a"),
                ),
            ),
        )
        assertEquals(
            listOf("https://host/arm64.apk"),
            QuickInstall.alternatives(detail, abi = "arm64-v8a", exclude = emptySet()).map { it.url },
        )
    }

    @Test
    fun `returns null when the post has no downloadable file`() {
        assertNull(QuickInstall.pick(detail(), abi = "arm64-v8a"))
        assertNull(QuickInstall.pick(detail(Version("1.0", null, emptyList())), abi = "arm64-v8a"))
    }
}
