package dev.aurastore.store.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aurastore.store.R
import dev.aurastore.store.data.AppRelease
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.humanSize
import dev.aurastore.store.ui.playClickable

/**
 * The line inside the store that says a newer Aura Store release is waiting.
 *
 * The offer is a dialog until the user puts it down; after that the update is still there, and a
 * Settings row is not where anyone looks for it. This banner sits under the brand bar, names the
 * version and its size, and reopens the same offer without asking GitHub again. [onDismiss] hides it
 * for this launch only: the app keeps no memory of a release the user has not installed.
 */
@Composable
fun AppUpdateBanner(
    release: AppRelease,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier
                .playClickable(onClick = onOpen)
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(
                Glyph.Update,
                size = 22.dp,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_update_banner_title, release.version.text),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.app_update_banner_note, humanSize(release.apkSize)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.app_update_later))
            }
        }
    }
}
