package dev.aurastore.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aurastore.store.R
import dev.aurastore.store.download.DownloadItem

/**
 * The honest version of the Play Store's "Data safety" card: what this client can and cannot
 * establish about a downloaded package.
 */
@Composable
fun VerificationDialog(onDismiss: () -> Unit, item: DownloadItem? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        title = { Text(stringResource(R.string.verify_title)) },
        text = {
            // Four sections (five with a download named) outrun the dialog at a 2.0× text size, and
            // AlertDialog bounds its text slot without scrolling it. The scroll keeps the last
            // section — the one that says what verification cannot prove — readable.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                InfoRow(
                    Glyph.Shield,
                    stringResource(R.string.verify_hashed),
                    subtitle = stringResource(R.string.verify_hashed_body),
                )
                InfoRow(
                    Glyph.Info,
                    stringResource(R.string.verify_parsed),
                    subtitle = stringResource(R.string.verify_parsed_body),
                )
                InfoRow(
                    Glyph.Update,
                    stringResource(R.string.verify_pinned),
                    subtitle = stringResource(R.string.verify_pinned_body),
                )
                InfoRow(
                    Glyph.Close,
                    stringResource(R.string.verify_cannot),
                    subtitle = stringResource(R.string.verify_cannot_body),
                )
                if (item != null) {
                    InfoRow(
                        Glyph.Download,
                        stringResource(R.string.verify_this_download),
                        subtitle = stringResource(
                            R.string.verify_this_download_body,
                            item.apkPackage ?: "—",
                            item.apkVersionCode?.toString() ?: "—",
                            item.sha256?.take(16) ?: "—",
                            item.apkSignerSha256?.take(16) ?: "—",
                        ),
                    )
                }
            }
        },
    )
}
