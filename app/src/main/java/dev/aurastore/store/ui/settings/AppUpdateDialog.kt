package dev.aurastore.store.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aurastore.store.R
import dev.aurastore.store.data.AppBuild
import dev.aurastore.store.data.AppRelease
import dev.aurastore.store.data.AppReleases
import dev.aurastore.store.data.Settings
import dev.aurastore.store.update.AppUpdateFailure
import dev.aurastore.store.update.AppUpdateState
import dev.aurastore.store.update.AppUpdater
import dev.aurastore.store.ui.humanSize
import kotlin.math.roundToInt

/**
 * The offer to replace this installation with a newer GitHub release.
 *
 * When the release published both builds, the dialog lets the user choose. The choice is a file, not
 * a promise: a build signed with another key cannot be installed over this copy — Android refuses it
 * — so the action becomes *Save APK* and the file lands in the phone's Downloads folder with the same
 * hash check. The offer defaults to the build this installation can actually take.
 *
 * The notes are the release body, so they scroll rather than push the buttons off the dialog. The
 * download's bar is pinned above them: the notes are longer than the dialog, and a bar that scrolls
 * with them is a bar nobody sees while the bytes arrive. The hash is verified before the installer
 * opens, and a failure says which check refused the file rather than leaving the dialog silent.
 */
@Composable
fun AppUpdateDialog(
    release: AppRelease,
    state: AppUpdateState,
    onInstall: (AppBuild) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val downloading = state as? AppUpdateState.Downloading
    val busy = downloading != null || state is AppUpdateState.Installing
    val saved = state as? AppUpdateState.Saved
    var picked by remember(release.tag) { mutableStateOf<AppBuild?>(null) }
    val chosen = picked
        ?: release.build(Settings.appUpdateBuild.value)
        ?: release.installed
    val installable = AppUpdater.canReplace(context, chosen)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_update_title, release.version.text)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // The two published builds, when there are two. A pill carries its own size, because
                // the debug APK is several times the signed one and that is the fact that decides it.
                if (release.builds.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        release.builds.forEach { build ->
                            val selected = build == chosen
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = !busy) { picked = build }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Column {
                                    Text(
                                        stringResource(buildLabel(build)),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        humanSize(build.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        stringResource(
                            R.string.app_update_build_size,
                            stringResource(buildLabel(chosen)),
                            humanSize(chosen.size),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (!installable && !busy && saved == null) {
                    Text(
                        stringResource(R.string.app_update_other_signature),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
                    is AppUpdateState.Saved -> Text(
                        stringResource(R.string.app_update_saved, state.location),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
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
            TextButton(
                onClick = {
                    if (saved != null) onDismiss() else onInstall(chosen)
                },
                enabled = !busy,
            ) {
                Text(
                    stringResource(
                        when {
                            saved != null -> R.string.app_update_done
                            installable -> R.string.app_update_now
                            else -> R.string.app_update_save
                        },
                    ),
                )
            }
        },
        dismissButton = {
            if (saved == null) {
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text(stringResource(R.string.app_update_later))
                }
            }
        },
    )
}

/** Which published build a pill names. */
private fun buildLabel(build: AppBuild): Int =
    if (build.debug) R.string.update_build_debug else R.string.update_build_release

/** The sentence for the check that refused an update. */
private fun failureText(failure: AppUpdateFailure): Int = when (failure) {
    AppUpdateFailure.NETWORK -> R.string.app_update_failed_network
    AppUpdateFailure.NO_CHECKSUM -> R.string.app_update_failed_no_hash
    AppUpdateFailure.CHECKSUM -> R.string.app_update_failed_hash
    AppUpdateFailure.INSTALLER -> R.string.app_update_failed_installer
    AppUpdateFailure.SIGNATURE -> R.string.app_update_failed_signature
}
