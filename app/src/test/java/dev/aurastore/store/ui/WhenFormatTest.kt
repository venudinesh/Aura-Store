package dev.aurastore.store.ui

import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How much of a timestamp a status line is allowed to spend.
 *
 * "Checked 29 Oct 2026, 11:26" under a heading that is read dozens of times a day is twenty characters
 * of ceremony around four of news. The rule is "say the part that is not already obvious", and which
 * part that is depends on when you are looking, so each arm is checked against a date built off a
 * calendar rather than against a fixed string.
 */
class WhenFormatTest {

    private fun millis(dayOffset: Int): String? =
        formatWhen(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }.timeInMillis,
        )

    @Test
    fun nothingComesOutOfNothing() {
        assertNull(formatWhen(0L))
        assertNull(formatWhen(-1L))
    }

    @Test
    fun todaySaysOnlyTheClock() {
        val out = millis(0)!!
        assertTrue("expected digits and a colon, got $out", out.all { it.isDigit() || it == ':' })
        assertTrue(out.length <= 5)
    }

    @Test
    fun anotherDayThisYearSaysTheDayAndStopsThere() {
        val out = millis(-3)!!
        assertFalse("a clock is not the news three days ago: $out", out.contains(':'))
        assertTrue("expected a day and a month, got $out", out.contains(' '))
        assertTrue(
            "the year is still obvious, so it should not be printed: $out",
            out.split(' ').none { it.length == 4 && it.all { c -> c.isDigit() } },
        )
    }

    @Test
    fun aDateOverAYearAwayEarnsTheYear() {
        val out = millis(-400)!!
        val tokens = out.split(' ')
        assertTrue(
            "expected a four-digit year somewhere in $out",
            tokens.any { it.length == 4 && it.all { c -> c.isDigit() } },
        )
    }
}
