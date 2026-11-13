package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the catalogue writes into its own text.
 *
 * WordPress HTML-escapes whatever an editor typed, so titles, genres and mod notes arrive as entities —
 * and a post that uses an emoji carries one WordPress splits into two digits no `Char` can hold.
 */
class EntityDecodeTest {

    @Test
    fun `punctuation comes back the way the editors wrote it`() {
        assertEquals("Minecraft – Pocket", "Minecraft &#8211; Pocket".decodeEntities())
        assertEquals("Today’s Build", "Today&#8217;s Build".decodeEntities())
        assertEquals("Spotify \"Premium\"", "Spotify &quot;Premium&quot;".decodeEntities())
        assertEquals("Pubg New State", "Pubg&nbsp;New&nbsp;State".decodeEntities())
        assertEquals("Music & Audio", "Music &amp; Audio".decodeEntities())
    }

    @Test
    fun `an emoji is the pair of characters it really is`() {
        // Live from the mod notes: `&#129513;` is 🧩, which is above the BMP. Turning it into a single
        // `Char` wraps the number and shows a private-use box instead of the puzzle piece.
        assertEquals("Mods 🧩 Unlocked", "Mods &#129513; Unlocked".decodeEntities())
        assertEquals("Mods 🧩 Unlocked", "Mods &#x1F9E9; Unlocked".decodeEntities())
        assertEquals(2, "🧩".length)
    }

    @Test
    fun `a number that is not a code point is left as it was written`() {
        assertEquals("&#1114112;", "&#1114112;".decodeEntities())
        assertEquals("&#9999999;", "&#9999999;".decodeEntities())
        assertEquals("&#x1000000;", "&#x1000000;".decodeEntities())
    }

    @Test
    fun `escaping is undone once, not on every pass`() {
        // An editor who typed "&amp;" into the post gets `&amp;` in the feed and wants the literal
        // characters back — a second decode would drop the ampersand they meant to show.
        assertEquals("&amp;", "&amp;amp;".decodeEntities())
    }
}
