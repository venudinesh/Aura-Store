package dev.aurastore.store.ui

import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.createBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.aurastore.store.data.decodeEntities
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Renders an app icon [Drawable] into an [ImageBitmap] so Compose can display it. */
fun Drawable.toImageBitmap(sizePx: Int = 144): ImageBitmap {
    val existing = (this as? BitmapDrawable)?.bitmap
    if (existing != null) return existing.asImageBitmap()
    val width = if (intrinsicWidth > 0) intrinsicWidth else sizePx
    val height = if (intrinsicHeight > 0) intrinsicHeight else sizePx
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

fun formatScore(value: Double): String =
    if (value <= 0.0) "—" else String.format(Locale.US, "%.1f", value)

/**
 * The catalogue writes sizes as "8M", "147M" or "2 GB". The Play Store writes "8 MB", so this
 * normalises the shorthand without guessing at anything that is not a size.
 *
 * The pattern is a file-level constant rather than a literal in the body: `Regex(...)` compiles a
 * `Pattern` every time it is evaluated, and this runs once per row per recomposition — a scroll
 * through a hundred rows was compiling a hundred identical patterns a frame.
 */
private val SIZE_PATTERN = Regex("(?i)^([0-9]+(?:\\.[0-9]+)?)\\s*([KMG])B?$")

fun formatSize(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val match = SIZE_PATTERN.find(text) ?: return text
    val (number, unit) = match.destructured
    return "$number ${unit.uppercase()}B"
}

fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}

fun formatTimestamp(millis: Long): String? {
    if (millis <= 0L) return null
    return SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
}

/**
 * A timestamp reduced to the part that is not already obvious.
 *
 * "Checked 29 Oct 2026, 11:26" printed at 11:26 on 29 Oct 2026 spends twenty characters of ceremony on
 * four of news, and the You tab's status strip is the one line that is read dozens of times a day.
 * Inside today it says the time; inside this year it says the day; anything older earns the year.
 */
internal fun formatWhen(millis: Long): String? {
    if (millis <= 0L) return null
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    val pattern = when {
        then.get(Calendar.YEAR) != now.get(Calendar.YEAR) -> "d MMM yyyy"
        then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "HH:mm"
        else -> "d MMM"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

fun formatDate(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val date: Date = parser.parse(iso) ?: return@runCatching null
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date)
    }.getOrNull()
}

/**
 * Strips the catalogue's `content`/tab HTML into plain text.
 *
 * Entities are decoded after the tags are gone, using the same decoder as the model layer: the
 * panel bodies are WordPress HTML and carry entities such as `&#8211;` that would otherwise be
 * shown literally ("&#8211;Removed all ads").
 */
fun stripHtml(html: String): String =
    html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("<[^>]+>"), "")
        .decodeEntities()
        .replace(Regex("\n{3,}"), "\n\n")
        .replace(Regex("[ \\t]{2,}"), " ")
        .trim()
