package dev.aurastore.store.ui

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The rules `strings.xml` has to keep for a translation to be possible at all.
 *
 * The sweep across rounds sixteen, seventeen and eighteen moved every sentence the composables draw
 * out of Kotlin and into this file, and the compiler can only check half of it: an id that exists
 * type-checks, whether the entry it points at is dead, duplicated, or shaped so that a translator
 * cannot reorder it without editing code is not something the build notices. The other half of the
 * reason is Android's own: a string with two placeholders has to number them, because the dative case
 * in another language puts them the other way round.
 *
 * Parsed straight from disk — a JVM test has no resource table, and what matters here is the XML.
 */
class StringsResourceTest {

    private val module = File(
        listOf(".", "..").first { File(it, "src/main/res/values/strings.xml").isFile },
    )
    private val values: File = File(module, "src/main/res/values/strings.xml")
    private val sources: List<File> = walk(File(module, "src/main/java")) +
        walk(File(module, "src/test/java")) +
        walk(File(module, "src/main/res")) +
        listOf(File(module, "src/main/AndroidManifest.xml")).filter { it.isFile }

    @Test
    fun `every name is used once within its own table`() {
        val strings = entryNames("string")
        val plurals = entryNames("plurals")
        assertEquals(
            "duplicate <string name> entries — the later one silently wins at build time",
            strings.size,
            strings.toSet().size,
        )
        assertEquals(
            "duplicate <plurals name> entries — the later one silently wins at build time",
            plurals.size,
            plurals.toSet().size,
        )
        assertTrue(
            "resource names are lowercase words joined by underscores: " + (strings + plurals).filter { !it.matches(Regex("[a-z0-9_]+")) },
            (strings + plurals).all { it.matches(Regex("[a-z0-9_]+")) },
        )
    }

    @Test
    fun `every id the code or the manifest reaches for exists`() {
        assertEquals(
            "ids used but not defined in values/strings.xml",
            emptySet<String>(),
            referenced() - defined(),
        )
    }

    @Test
    fun `no entry sits in the file unreferenced`() {
        assertEquals(
            "entries nothing draws any more — delete them, do not leave them for the next locale",
            emptySet<String>(),
            defined() - referenced(),
        )
    }

    @Test
    fun `a sentence with more than one placeholder numbers them`() {
        val bad = entries().mapNotNull { (name, body) ->
            val found = SPEC.findAll(body).map { it.value }.toList()
            if (found.size > 1 && found.any { !it.contains("\$") }) name else null
        }
        assertEquals(
            "a translator has to be able to reorder the arguments, which Android only allows when each is %n\$",
            emptyList<String>(),
            bad,
        )
    }

    @Test
    fun `the placeholder indexes run from one with no gaps`() {
        val bad = entries().mapNotNull { (name, body) ->
            val indexes = SPEC.findAll(body).mapNotNull { it.groupValues[1].removeSuffix("\$").toIntOrNull() }.sorted().toList()
            if (indexes.isNotEmpty() && indexes != (1..indexes.size).toList()) "$name -> $indexes" else null
        }
        assertEquals("placeholder indexes must be 1..n in the order they appear", emptyList<String>(), bad)
    }

    @Test
    fun `an apostrophe inside a sentence is escaped`() {
        val bad = entries().filter { (_, body) -> Regex("""(?<!\\)'""").containsMatchIn(body) }.map { it.first }
        assertEquals(
            "an unescaped apostrophe ends the string for Android's own parser",
            emptyList<String>(),
            bad,
        )
    }

    @Test
    fun `every plurals entry carries one and other`() {
        val missing = ArrayList<String>()
        document.documentElement.getNodeList("plurals").forEach { node ->
            val name = (node as Element).getAttribute("name")
            val quantities = node.getNodeList("item").map { (it as Element).getAttribute("quantity") }
            if (!quantities.containsAll(listOf("one", "other"))) missing += "$name -> $quantities"
        }
        assertEquals(
            "English needs 'one', every language needs 'other' as the fallback",
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun `the words the phone pass and the docs quote still read the same`() {
        val expected = linkedMapOf(
            "app_name" to "Aura Store",
            "tab_you" to "You",
            "action_install" to "Install",
            "action_update" to "Update",
            "action_open" to "Open",
            "settings_title" to "Settings",
            "feed_categories" to "Categories",
            "cat_all" to "All categories",
            "src_title" to "Download from",
            "toast_link_copied" to "Download link copied",
            "percent_int" to "%1\$d%%",
            // The four sentences a paged list's bottom can say, quoted verbatim in the docs and read off a
            // handset in §4s and §4t. The raw value keeps Android's own backslash, because this test reads the
            // XML rather than the compiled resources.
            "try_again" to "Try again",
            "list_finding" to "Loading more…",
            "list_end" to "That\\'s everything in %1\$s.",
            "list_end_capped" to "%1\$s has more than %2\$d rows — the catalogue pages no further.",
            "list_load_more_failed" to "Could not load more",
        )
        val actual = entries().filter { !it.first.contains('(') }.toMap()
        val drifted = expected.filter { (name, want) -> actual[name] != want }
        assertEquals(
            "rewritten copy — say so in README's round section, then update this list",
            emptyMap<String, String>(),
            drifted,
        )
    }

    // ------------------------------------------------------------------------------------- plumbing

    private fun defined(): Set<String> = (entryNames("string") + entryNames("plurals")).toSet()

    private fun referenced(): Set<String> = HashSet<String>().apply {
        sources.forEach { file ->
            REFERENCE.findAll(file.readText()).forEach { match -> add(match.groupValues[1].ifEmpty { match.groupValues[2] }) }
        }
    }

    /** Every sentence in the file, with `<plurals>` items keyed `name (quantity)`. */
    private fun entries(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        document.documentElement.getNodeList("string").forEach { node ->
            out += (node as Element).getAttribute("name") to node.textContent
        }
        document.documentElement.getNodeList("plurals").forEach { parent ->
            val name = (parent as Element).getAttribute("name")
            parent.getNodeList("item").forEach { item ->
                out += "$name (${(item as Element).getAttribute("quantity")})" to item.textContent
            }
        }
        return out
    }

    private fun entryNames(tag: String): List<String> {
        val out = ArrayList<String>()
        document.documentElement.getNodeList(tag).forEach { out += (it as Element).getAttribute("name") }
        return out
    }

    private val document by lazy {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.newDocumentBuilder().parse(values)
    }

    private fun Element.getNodeList(tag: String) =
        (0 until getElementsByTagName(tag).length).map { getElementsByTagName(tag).item(it) }

    private fun walk(root: File): List<File> =
        if (!root.isDirectory) emptyList()
        else root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList()

    private companion object {
        /** `R.string.<name>` and `R.plurals.<name>`, plus the XML form `@string/<name>` the layouts use. */
        val REFERENCE = Regex("""@(?:string|plurals)/([a-z0-9_]+)|R\.(?:string|plurals)\.([a-z0-9_]+)""")

        /** A conversion: `%s`, `%d`, `%1$s`, `%2$d`. `%%` is a literal percent and is not one. */
        val SPEC = Regex("""%(\d+\$)?[sd]""")
    }
}
