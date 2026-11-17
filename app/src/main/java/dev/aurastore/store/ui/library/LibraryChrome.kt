package dev.aurastore.store.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aurastore.store.R
import dev.aurastore.store.data.AppIconCache
import dev.aurastore.store.install.PackageLookup
import dev.aurastore.store.theme.ArtShape
import dev.aurastore.store.ui.ActionKind
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.InstallButton
import dev.aurastore.store.ui.RowAction
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.skeletonPulse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The chrome the four library pages share.
 *
 * The You tab used to be one screen holding updates, the installed apps and the queue, so the small
 * pieces that framed each group — the group label, the card the rows sit in, the status strip, the
 * app glyph — lived inside it. Splitting the tab into pages that are actually separate would have
 * copied that chrome four times and let the copies drift. It lives here once: Manage, Device,
 * Downloads and You draw from the same parts.
 */

/** Tabular figures, so a column of version strings and hashes lines up. */
internal const val TABULAR = "\"tnum\""

/**
 * A page's top row: the back control every pushed page has, and the page's own name.
 *
 * Settings, a category and an app page each draw this by hand; new pages go through one definition so
 * "the back button is a 44 dp circle" is a fact rather than six coincidences.
 */
@Composable
fun PageBar(
    title: String,
    onBack: () -> Unit,
    action: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(44.dp).clip(CircleShape).playClickable(onClick = onBack),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Back, size = 22.dp, label = stringResource(R.string.a11y_back))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}

/** The small tracked label that names a group of rows, with its caveat under it. */
@Composable
fun GroupHeader(label: String, note: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (!note.isNullOrBlank()) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The card a run of rows sits in. */
@Composable
fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(content = content)
    }
}

/** Hairline between two rows in a card, inset to where the text starts. */
@Composable
fun RowRule() {
    Box(
        Modifier.fillMaxWidth().padding(start = 60.dp, end = 12.dp).height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

/**
 * The strip under a page's title: what the list is, and its batch action.
 *
 * It carries no heading on purpose — the page bar above already named the page, and a heading here is
 * what put four same-size titles on one screen before.
 */
@Composable
fun StatusStrip(text: String, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}

/** One row, one control: the permission a pending install is waiting on. */
@Composable
fun PermissionStrip(onAllow: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Shield, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.you_installs_blocked),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                InstallButton(RowAction(ActionKind.PERMISSION), onClick = onAllow, compact = true)
            }
        }
    }
}

/** How far the budgeted name sweep has got, in one line. */
@Composable
fun coverageNote(coverage: PackageLookup.Coverage): String? {
    // Each clause drops out when it is empty, so the line never prints "0 not asked yet" — and when
    // every clause is empty the whole line goes, rather than reporting that nothing is wrong.
    // One clause, not three. The sweep's arithmetic is a footnote, and three fragments joined by dot
    // separators read like a status page. The number that changes a reader's next move is how much of
    // the phone the sweep has actually seen.
    return when {
        coverage.pending > 0 ->
            stringResource(R.string.you_coverage_checked, coverage.answered, coverage.total)
        coverage.missed > 0 -> stringResource(R.string.you_coverage_missed, coverage.missed)
        else -> null
    }
}

@Composable
fun LoadingRows(rows: Int = 3) {
    // One pulse for the whole screen, from the same helper the feeds use, so a waiting store
    // breathes the same way wherever the user happens to be looking at it.
    val pulse = skeletonPulse()
    Column(Modifier.fillMaxWidth().alpha(pulse)) {
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(ArtShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier.fillMaxWidth(0.55f).height(14.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        Modifier.fillMaxWidth(0.32f).height(11.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.height(38.dp).width(92.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
fun AppGlyph(icon: ImageBitmap?, label: String) {
    Box(
        Modifier.size(48.dp).clip(ArtShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = label, modifier = Modifier.size(48.dp))
        }
    }
}

/** An installed app's own icon, read off the phone once per package. */
@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(AppIconCache.get(packageName)) }
    LaunchedEffect(packageName) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                AppIconCache.load(context, packageName)
            }
        }
    }
    return bitmap
}

/** A count in the tabular figures the rows use, or nothing when the number is zero. */
@Composable
fun RowCount(count: Int) {
    if (count <= 0) return
    Text(
        count.toString(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontFeatureSettings = TABULAR,
            fontWeight = FontWeight.Medium,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
