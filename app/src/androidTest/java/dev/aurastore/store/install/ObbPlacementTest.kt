package dev.aurastore.store.install

import android.content.ContentUris
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The one step of an install that Android will not let this app do, run on a device.
 *
 * `ObbInstaller` has existed since the second round with its naming and pairing rules under JVM tests,
 * and its actual job — get a file from the app's cache into shared storage — never performed anywhere.
 * A JVM test can only assert which branch was chosen; it cannot know whether a write through
 * `MediaStore` on API 36 produces a file that is really there, really readable, and really named what a
 * game will look for.
 *
 * The `Placed` branch is not reachable on this hardware: LineageOS here ships no `su` binary at all, so
 * the root probe fails before it is asked. That makes the export branch the one users actually get — and
 * the one worth proving.
 */
@RunWith(AndroidJUnit4::class)
class ObbPlacementTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Whether this device could take the root branch at all. With no `su` binary the export branch is the
     * only one reachable, so the assertions that read the exported bytes back are demanded rather than
     * offered — otherwise a device that took the other path would quietly skip them and still pass.
     */
    private val suPresent: Boolean =
        listOf("/system/bin/su", "/system/xbin/su", "/sbin/su").any { File(it).exists() }

    private fun assertExported(landed: Landed, outcome: ObbInstaller.Outcome) {
        if (!suPresent) {
            assertTrue("no su on this device, so placement must export: $outcome", landed.exportedTo != null)
        }
    }

    /** What a placement claims happened, whatever shape it came in. */
    private data class Landed(val names: List<String>, val dir: String, val exportedTo: String?)

    private fun landed(outcome: ObbInstaller.Outcome): Landed? = when (outcome) {
        is ObbInstaller.Outcome.Placed -> Landed(outcome.names, outcome.dir, null)
        is ObbInstaller.Outcome.Manual -> Landed(outcome.names, outcome.dir, outcome.exportedTo)
        is ObbInstaller.Outcome.Refused -> null
    }

    /** A pack as the catalogue ships one: the `.obb` buried in a folder, its path to be dropped. */
    private fun pack(id: String, vararg entries: Pair<String, ByteArray>): File {
        val file = File(context.cacheDir, "obbtest/$id.zip").apply { parentFile!!.mkdirs() }
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    /** Reads an exported download back out of `MediaStore` by display name, then removes it again. */
    private fun readBack(displayName: String): List<Byte>? {
        val id = context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L } ?: return null
        if (id < 0) return null
        val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        context.contentResolver.delete(uri, null, null)
        return bytes?.toList()
    }

    @Test
    fun dataInsideAPackReachesDownloadsUnderTheNameTheGameLooksFor() {
        val payload = "obb-bytes".toByteArray()
        val name = "main.123.com.example.game.obb"
        val source = pack("basic", "Android/obb/com.example.game/$name" to payload)

        val outcome = runBlocking { ObbInstaller.place(context, "t1", source) }
        val landed = landed(outcome)
        assertNotNull("placement refused: ${(outcome as? ObbInstaller.Outcome.Refused)?.message}", landed)
        assertEquals(listOf(name), landed!!.names)
        assertTrue(
            "the destination must be the directory Android scans: $landed",
            landed.dir.endsWith("/Android/obb/com.example.game"),
        )
        // The path the reader is told and the bytes behind it have to agree.
        landed.exportedTo?.let {
            assertTrue(it.startsWith("Downloads/AuraStore/com.example.game/"))
            assertEquals(payload.toList(), readBack(name))
        }
        assertExported(landed, outcome)
        source.delete()
    }

    @Test
    fun aRenamedPackIsRewrittenToTheVersionCodeItIsInstalledBeside() {
        // The case the naming rule exists for: modded builds ship `main.<original code>` and the game
        // asks for the code of the package actually on the device.
        val payload = "renamed".toByteArray()
        val source = pack("renamed", "obb/main.999.com.example.game.obb" to payload)

        val outcome = runBlocking {
            ObbInstaller.place(context, "t2", source, packageHint = "com.example.game", versionCode = 456L)
        }
        val landed = landed(outcome)
        assertNotNull("placement refused: ${(outcome as? ObbInstaller.Outcome.Refused)?.message}", landed)
        assertEquals(listOf("main.456.com.example.game.obb"), landed!!.names)
        landed.exportedTo?.let { assertEquals(payload.toList(), readBack(landed.names.first())) }
        assertExported(landed, outcome)
        source.delete()
    }

    @Test
    fun anArchiveWithNoExpansionInsideIsRefusedRatherThanReportedAsPlaced() {
        val source = pack("empty", "AndroidManifest.xml" to "<manifest/>".toByteArray())
        val outcome = runBlocking { ObbInstaller.place(context, "t3", source) }
        assertTrue("got $outcome", outcome is ObbInstaller.Outcome.Refused)
        source.delete()
    }

    @Test
    fun aPackThatNamesNoPackageAnywhereIsRefusedWithTheReasonThatMatters() {
        // `payload.obb` carries no package, and with nothing installed yet there is no hint to fall back
        // on. Guessing a directory here would put the data where no game will ever look.
        val source = pack("anonymous", "payload.obb" to "x".toByteArray())
        val outcome = runBlocking { ObbInstaller.place(context, "t4", source, packageHint = null) }
        assertTrue("got $outcome", outcome is ObbInstaller.Outcome.Refused)
        source.delete()
    }
}
