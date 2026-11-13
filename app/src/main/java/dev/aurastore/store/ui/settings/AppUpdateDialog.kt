package dev.aurastore.store.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aurastore.store.R
import dev.aurastore.store.data.AppRelease
import dev.aurastore.store.data.AppReleases
import dev.aurastore.store.update.AppUpdateFailure
import dev.aurastore.store.update.AppUpdateState
import dev.aurastore.store.ui.humanSize
import kotlin.math.roundToInt

/**
 * The offer to replace this installation with a newer GitHub release.
 *
 * The notes are the release body, so they scroll rather than push the buttons off the dialog. The
 * download's bar is pinned above them, not inside: the notes are longer than the dialog, and a bar
 * that scrolls with them is a bar nobody sees while the bytes arrive. The hash is verified before the
 * installer opens, and a failure says which check refused the file rather than leaving the dialog
 * silent.
 */
@Composable
fun AppUpdateDialog(
    release: AppRelease,
    state: AppUpdateState,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val downloading = state as? AppUpdateState.Downloading
    val busy = downloading != null || state is AppUpdateState.Installing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_update_title, release.version.text)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.app_update_size, humanSize(release.apkSize)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                when (state) {
                    is AppUpdateState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.app_update_downloading, (state.progress * 100).roundToInt()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(
                                R.string.app_update_bytes,
                                humanSize(state.received),
                                humanSize(if (state.total > 0L) state.total else release.apkSize),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is AppUpdateState.Installing -> Text(
                        stringResource(R.string.app_update_installing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is AppUpdateState.Failed -> Text(
                        stringResource(failureText(state.failure)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
                // The notes are the one part that scrolls, and they are capped so a long release body
                // cannot squeeze the progress block or the two buttons off the phone.
                Column(
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        release.notes.ifBlank { stringResource(R.string.app_update_no_notes) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.app_update_source, AppReleases.REPO),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = !busy) {
                Text(stringResource(R.string.app_update_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.app_update_later))
            }
        },
    )
}

/** The sentence for the check that refused an update. */
private fun failureText(failure: AppUpdateFailure): Int = when (failure) {
    AppUpdateFailure.NETWORK -> R.string.app_update_failed_network
    AppUpdateFailure.NO_CHECKSUM -> R.string.app_update_failed_no_hash
    AppUpdateFailure.CHECKSUM -> R.string.app_update_failed_hash
    AppUpdateFailure.INSTALLER -> R.string.app_update_failed_installer
}
