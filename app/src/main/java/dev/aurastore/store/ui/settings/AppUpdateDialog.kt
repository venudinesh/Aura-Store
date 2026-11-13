package dev.aurastore.store.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * button becomes a progress bar while the bytes arrive, because a tap that seems to do nothing for
 * fifteen seconds reads as a broken button; the hash is verified before the installer opens, and a
 * failure says which check refused the file rather than leaving the dialog silent.
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
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.app_update_size, humanSize(release.apkSize)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    release.notes.ifBlank { stringResource(R.string.app_update_no_notes) },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.app_update_source, AppReleases.REPO),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (state) {
                    is AppUpdateState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        Text(
                            stringResource(R.string.app_update_downloading, (state.progress * 100).roundToInt()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    is AppUpdateState.Installing -> Text(
                        stringResource(R.string.app_update_installing),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is AppUpdateState.Failed -> Text(
                        stringResource(failureText(state.failure)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
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
