package dev.aurastore.store.ui.downloads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.aurastore.store.R
import dev.aurastore.store.data.Hosts
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.download.Failure
import dev.aurastore.store.download.FailureKind
import dev.aurastore.store.download.Failures
import dev.aurastore.store.download.TransferRate
import dev.aurastore.store.theme.ArtShape
import dev.aurastore.store.theme.EmphasizedDecelerateEasing
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardAccelerateEasing
import dev.aurastore.store.theme.StandardEasing
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.InfoCard
import dev.aurastore.store.ui.InfoRow
import dev.aurastore.store.ui.SectionHeader
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.StoreActions
import dev.aurastore.store.ui.formatTimestamp
import dev.aurastore.store.ui.humanSize
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.rememberStoreActions

/**
 * Play's per-download page: one download's numbers, the digest it verified to, and what to do about a
 * failure.
 *
 * The queue row can only be one line, which is why speed and time-left had nowhere to live: both are
 * estimates that change four times a second, and a row that jitters reads as a broken meter. Here the
 * figures are allowed to be labelled, rounded and explained.
 */
@Composable
fun DownloadDetailScreen(
    downloadId: String,
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = rememberStoreActions()
    val downloads by DownloadEngine.items.collectAsStateWithLifecycle()
    val item = downloads.firstOrNull { it.id == downloadId }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(Glyph.Back, stringResource(R.string.a11y_back), onBack)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.row_menu_details),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        if (item == null) {
            item {
                StateMessage(
                    title = stringResource(R.string.download_gone_title),
                    body = stringResource(R.string.download_gone_body),
                )
            }
            return@LazyColumn
        }
        item { Header(item, onOpenPost) }
        item { ProgressBlock(item) }
        item { Actions(item, actions) }
        item { TransferBlock(item) }
        item { VerificationBlock(item) }
        item { HistoryBlock(item) }
        item { FailureBlock(item, actions) }
    }
}

@Composable
private fun Header(item: DownloadItem, onOpenPost: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().playClickable(onClick = { onOpenPost(item.postId) })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).clip(ArtShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.iconUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (item.dataFile) stringResource(R.string.dl_title_with_data, item.title) else item.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    // The catalogue's labels already carry their own "v"; prefixing another read "vv5.0".
                    item.versionLabel.takeIf { it.isNotBlank() },
                    item.fileLabel.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        GlyphIcon(Glyph.ChevronRight, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProgressBlock(item: DownloadItem) {
    // The engine reports once a second, so the bar is eased between the reports: a meter that lurches
    // reads as a stalled download, while one that keeps creeping reads as progress.
    val fraction by animateFloatAsState(
        targetValue = item.progress.coerceIn(0f, 1f),
        animationSpec = PlayMotion.spec(PlayMotion.LONG, StandardEasing),
        label = "download-fraction",
    )
    val stateText = stateLine(item)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedContent(
            targetState = stateText,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter =
                        fadeIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing)) +
                            slideInVertically(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing)) { it / 3 },
                    initialContentExit =
                        fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                            slideOutVertically(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) { -it / 3 },
                )
            },
            label = "download-state",
        ) { text ->
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
        when {
            item.state == DownloadState.DOWNLOADING && item.totalBytes > 0 -> LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
            item.state == DownloadState.DOWNLOADING -> LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
            item.totalBytes > 0 -> LinearProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
        }
        Text(
            when {
                item.totalBytes > 0 -> stringResource(
                    R.string.dl_progress_bytes,
                    humanSize(item.bytesDownloaded),
                    humanSize(item.totalBytes),
                    (item.progress * 100).toInt(),
                )
                item.bytesDownloaded > 0 -> stringResource(
                    R.string.dl_progress_unbounded,
                    humanSize(item.bytesDownloaded),
                )
                else -> stringResource(R.string.dl_progress_nothing)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.state == DownloadState.DOWNLOADING) {
            Text(
                stringResource(
                    R.string.dl_eta,
                    TransferRate.formatSpeed(item.bytesPerSecond),
                    TransferRate.formatEta(item.etaSeconds),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.notice?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun stateLine(item: DownloadItem): String = when (item.state) {
    DownloadState.QUEUED -> stringResource(R.string.dl_state_queued)
    DownloadState.DOWNLOADING -> stringResource(R.string.dl_state_downloading)
    DownloadState.PAUSED -> stringResource(R.string.dl_state_paused, humanSize(item.bytesDownloaded))
    DownloadState.VERIFYING -> when {
        item.dataFile -> stringResource(R.string.dl_state_reading_archive)
        item.isBundle -> stringResource(R.string.dl_state_unpacking)
        else -> stringResource(R.string.dl_state_verifying)
    }
    DownloadState.READY -> when {
        item.dataFile -> stringResource(R.string.dl_state_data_ready)
        item.uninstallFirst -> stringResource(R.string.dl_state_waiting_uninstall)
        else -> stringResource(R.string.dl_state_ready)
    }
    DownloadState.NEEDS_PERMISSION -> stringResource(R.string.dl_state_needs_permission)
    DownloadState.AWAITING_CONFIRMATION -> stringResource(R.string.dl_state_awaiting)
    DownloadState.INSTALLING -> stringResource(R.string.dl_state_installing)
    DownloadState.INSTALLED ->
        stringResource(if (item.dataFile) R.string.dl_state_data_placed else R.string.dl_state_installed)
    // The engine's own sentence is the one that names what happened; only its absence is a resource.
    DownloadState.FAILED -> item.error ?: stringResource(R.string.dl_state_failed)
    DownloadState.CANCELED -> stringResource(R.string.dl_state_canceled)
}

@Composable
private fun Actions(item: DownloadItem, actions: StoreActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item.state) {
            DownloadState.DOWNLOADING, DownloadState.QUEUED ->
                Button(onClick = { actions.toggle(item) }) { Text(stringResource(R.string.a11y_pause)) }
            DownloadState.PAUSED, DownloadState.CANCELED ->
                Button(onClick = { actions.resume(item) }) { Text(stringResource(R.string.a11y_resume)) }
            DownloadState.FAILED ->
                Button(onClick = { actions.resume(item) }) { Text(stringResource(R.string.action_retry)) }
            DownloadState.READY, DownloadState.NEEDS_PERMISSION, DownloadState.AWAITING_CONFIRMATION ->
                if (item.uninstallFirst) {
                    Button(onClick = { actions.uninstallFirst(item) }) {
                        Text(stringResource(R.string.action_uninstall))
                    }
                } else {
                    Button(onClick = { actions.install(item) }) {
                        Text(
                            stringResource(
                                if (item.dataFile) R.string.action_add_data else R.string.action_install,
                            ),
                        )
                    }
                }
            else -> OutlinedButton(onClick = { actions.open(item) }) {
                Text(stringResource(R.string.action_open))
            }
        }
        // A bundle can carry its game data inside the same archive, and the two halves need different
        // actions: Install puts the packages on the device, this puts the data where the game looks.
        if (item.dataInside) {
            OutlinedButton(onClick = { actions.place(item) }) { Text(stringResource(R.string.action_add_data)) }
        }
        TextButton(onClick = { actions.copyLink(item) }) { Text(stringResource(R.string.action_copy_link)) }
    }
}

@Composable
private fun TransferBlock(item: DownloadItem) {
    SectionHeader(stringResource(R.string.dl_section_transfer))
    InfoCard {
        InfoRow(Glyph.Download, Hosts.hostOf(item.url) ?: item.url, subtitle = stringResource(R.string.dl_row_host))
        InfoRow(
            Glyph.Info,
            item.fileName,
            subtitle = when {
                item.dataFile -> stringResource(R.string.dl_row_file_game_data)
                // A bundle's archive stays beside the packages it was unpacked into, so the queue holds
                // both and the row should say which of them it is looking at.
                item.bundleFiles.isNotEmpty() -> pluralStringResource(
                    R.plurals.dl_row_file_bundle,
                    item.bundleFiles.size,
                    item.bundleFiles.size,
                )
                else -> stringResource(R.string.dl_row_file)
            },
        )
        InfoRow(
            Glyph.Shield,
            stringResource(
                if (Hosts.needsToken(item.url)) R.string.dl_link_signed else R.string.dl_link_public,
            ),
            subtitle = stringResource(R.string.dl_link_how),
        )
    }
}

@Composable
private fun VerificationBlock(item: DownloadItem) {
    SectionHeader(stringResource(R.string.dl_section_verification))
    InfoCard {
        Text(
            item.sha256 ?: stringResource(R.string.dl_hash_pending),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
        // Only a source that publishes a digest can say "this is the file it promised"; the catalogue
        // publishes none, so most rows show nothing here rather than a hollow tick.
        item.expectedSha256?.let { published ->
            val matched = Failures.normalizeDigest(item.sha256) == Failures.normalizeDigest(published)
            InfoRow(
                if (matched) Glyph.Tick else Glyph.Close,
                stringResource(
                    if (matched) R.string.dl_digest_matched else R.string.dl_digest_mismatch,
                ),
                subtitle = stringResource(R.string.dl_digest_published, published.take(16)),
            )
        }
        if (item.dataFile) {
            InfoRow(
                Glyph.Grid,
                item.apkPackage ?: stringResource(R.string.dl_data_belongs),
                subtitle = stringResource(R.string.dl_data_for),
            )
        } else {
            InfoRow(
                Glyph.Tick,
                item.apkPackage ?: stringResource(R.string.dl_not_parsed),
                subtitle = stringResource(R.string.dl_package),
            )
            InfoRow(
                Glyph.Shield,
                listOfNotNull(item.apkVersionName?.let { "v$it" }, item.apkVersionCode?.toString())
                    .joinToString(" · ")
                    .ifBlank { "—" },
                subtitle = stringResource(R.string.dl_version),
            )
            InfoRow(
                Glyph.Shield,
                item.apkSignerSha256?.take(32) ?: stringResource(R.string.dl_signer_pinned),
                subtitle = stringResource(R.string.dl_signing_cert),
            )
        }
    }
}

@Composable
private fun HistoryBlock(item: DownloadItem) {
    if (item.transitions.isEmpty()) return
    SectionHeader(stringResource(R.string.dl_section_history))
    InfoCard {
        item.transitions.forEach { change ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    formatTimestamp(change.at) ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(140.dp),
                )
                Text(stateName(change.state), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FailureBlock(item: DownloadItem, actions: StoreActions) {
    val failure = item.failure ?: return
    SectionHeader(stringResource(R.string.dl_section_failure))
    InfoCard {
        Text(kindTitle(failure), style = MaterialTheme.typography.titleMedium)
        Text(failure.message, style = MaterialTheme.typography.bodyMedium)
        failure.hint?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (failure.worthRetrying) {
            Button(onClick = { actions.resume(item) }) { Text(stringResource(R.string.try_again)) }
        }
    }
}

@Composable
private fun kindTitle(failure: Failure): String = stringResource(
    when (failure.kind) {
        FailureKind.REFUSED -> R.string.fail_refused
        FailureKind.MISSING -> R.string.fail_missing
        FailureKind.NETWORK -> R.string.fail_network
        FailureKind.STORAGE -> R.string.fail_storage
        FailureKind.PACKAGE -> R.string.fail_package
        FailureKind.INSTALL -> R.string.fail_install
        FailureKind.INTEGRITY -> R.string.fail_integrity
        FailureKind.UNKNOWN -> R.string.fail_unknown
    },
)

@Composable
private fun stateName(state: DownloadState): String = stringResource(
    when (state) {
        DownloadState.QUEUED -> R.string.state_queued
        DownloadState.DOWNLOADING -> R.string.dl_state_downloading
        DownloadState.PAUSED -> R.string.state_paused
        DownloadState.VERIFYING -> R.string.state_verifying
        DownloadState.READY -> R.string.state_ready
        DownloadState.NEEDS_PERMISSION -> R.string.state_needs_permission
        DownloadState.AWAITING_CONFIRMATION -> R.string.state_awaiting
        DownloadState.INSTALLING -> R.string.dl_state_installing
        DownloadState.INSTALLED -> R.string.dl_state_installed
        DownloadState.FAILED -> R.string.dl_state_failed
        DownloadState.CANCELED -> R.string.dl_state_canceled
    },
)

@Composable
private fun CircleButton(glyph: Glyph, description: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(44.dp).clip(CircleShape).playClickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, size = 22.dp, label = description)
        }
    }
}
