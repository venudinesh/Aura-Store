package dev.aurastore.store.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aurastore.store.data.sources.SourceApp

/**
 * One app as a source published it: the art, the name, the facts beside it and the source's label.
 *
 * Shared by the source screens and the home's cross-server feed, so a row from any server reads the
 * same wherever it is drawn. The caller decides where a tap goes: a catalogue row opens its post,
 * every other row opens the source's own app page.
 */
@Composable
fun SourceAppRow(
    app: SourceApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().playClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(
            url = app.iconUrl,
            title = app.title,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(app.version, app.size, app.subtitle).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                app.source.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(8.dp))
        GlyphIcon(Glyph.ChevronRight, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
