package dev.aurastore.store.ui.source

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.data.DownloadFile
import dev.aurastore.store.data.PostSummary
import dev.aurastore.store.data.Settings
import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.data.sources.SourceFile
import dev.aurastore.store.data.sources.SourceHub
import dev.aurastore.store.data.sources.SourceId
import dev.aurastore.store.data.sources.Sources
import dev.aurastore.store.download.DownloadEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the sheet is being asked about.
 *
 * The catalogue post is carried because that is where the store's own build of the app comes from:
 * the sheet offers the catalogue's artefact first and the other sources' answers beside it, so the
 * user chooses a place rather than a file the app has not found yet.
 */
data class SourceRequest(
    val title: String,
    /** The catalogue post, when the request came from an app page. */
    val cataloguePostId: Long? = null,
    /** The source the sheet opens on: the user's default, or the catalogue. */
    val preferred: SourceId? = null,
    /**
     * The only source the sheet may offer, when the row that opened it already named one.
     *
     * A search row carries the site it came from, so asking which site again is a question the row
     * has answered. The sheet then asks only which *file*, when that site published more than one.
     */
    val only: SourceId? = null,
    /** The app the row already resolved, so the file list needs no second search. */
    val app: SourceApp? = null,
)

/**
 * The one place the "which source?" question is asked.
 *
 * A download can come from seven places, and the app cannot know which one the person wants. So it
 * asks, once, in a sheet that every screen can open, and the answer goes into the same queue, hash
 * and install pipeline as a catalogue download.
 */
object SourcePicker {

    private val _request = MutableStateFlow<SourceRequest?>(null)
    val request: StateFlow<SourceRequest?> = _request.asStateFlow()

    fun open(request: SourceRequest) {
        _request.value = request
    }

    fun close() {
        _request.value = null
    }
}

/**
 * The sheet that asks which source a download should come from.
 *
 * It answers with what each source actually has: the catalogue's own artefact, and the other sites'
 * answers to the same title, each resolved to a direct file link before it is offered. A source with
 * no answer says so instead of being hidden, because "this one does not have it" is the reason the
 * user picks another.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePickerSheet(onDismiss: () -> Unit) {
    val request by SourcePicker.request.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val enabled by Settings.sources.collectAsStateWithLifecycle()
    val defaultSource by Settings.defaultSource.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var matches by remember { mutableStateOf<Map<SourceId, SourceMatchRow>>(emptyMap()) }
    var searching by remember { mutableStateOf(false) }
    var openSource by remember { mutableStateOf<SourceId?>(null) }
    var files by remember { mutableStateOf<Map<SourceId, List<SourceFile>>>(emptyMap()) }
    var fileError by remember { mutableStateOf<Map<SourceId, String>>(emptyMap()) }
    var loadingFiles by remember { mutableStateOf<SourceId?>(null) }

    /** The app a source has for this request, if it has one at all. */
    fun appFor(source: SourceId): SourceApp? {
        val current = request ?: return null
        if (source == SourceId.LITEAPKS && current.cataloguePostId != null) {
            return SourceApp(
                source = SourceId.LITEAPKS,
                key = current.cataloguePostId.toString(),
                title = current.title,
                cataloguePostId = current.cataloguePostId,
            )
        }
        return matches[source]?.app
    }

    /** Reads one source's files and puts them under its name. */
    fun loadFiles(source: SourceId) {
        val app = appFor(source) ?: return
        if (files.containsKey(source) || loadingFiles == source) return
        loadingFiles = source
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) { Sources.byId(source).files(app) }
            }
            loadingFiles = null
            outcome.fold(
                onSuccess = { list -> files = files + (source to list) },
                onFailure = { e ->
                    fileError = fileError + (source to (e.message ?: e::class.simpleName.orEmpty()))
                },
            )
        }
    }

    LaunchedEffect(request) {
        val current = request ?: return@LaunchedEffect
        searching = true
        matches = emptyMap()
        files = emptyMap()
        fileError = emptyMap()
        if (current.only != null) {
            // The row named the site; the sheet only has to show that site's files.
            current.app?.let { matches = mapOf(it.source to SourceMatchRow(it)) }
            searching = false
            openSource = current.only
            loadFiles(current.only)
            return@LaunchedEffect
        }
        val found = SourceHub.find(current.title, enabled)
        matches = found.associate { match -> match.source to SourceMatchRow(match.app) }
        searching = false
        // The sheet opens on the source the user named in Settings, or on the store's own catalogue.
        val preferred = current.preferred ?: SourceId.LITEAPKS
        openSource = preferred
        loadFiles(preferred)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            val only = request?.only
            Text(
                if (only != null) {
                    stringResource(R.string.src_pick_file_title, request?.title.orEmpty())
                } else {
                    stringResource(R.string.src_picker_title, request?.title.orEmpty())
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                if (only != null) {
                    stringResource(R.string.src_pick_file_body, only.label)
                } else {
                    stringResource(R.string.src_picker_body)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            val sources = Sources.enabled(enabled).filter { only == null || it.id == only }
            sources.forEach { source ->
                val app = appFor(source.id)
                val queued = stringResource(R.string.src_downloading, app?.title.orEmpty(), source.id.label)
                val isDefault = defaultSource == source.id
                val isOpen = openSource == source.id

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                source.id.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (isDefault) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.src_picker_default),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val detail = when {
                            searching -> stringResource(R.string.src_picker_searching)
                            app != null -> listOfNotNull(app.version, app.size).joinToString(" · ")
                                .ifBlank { app.title }
                            else -> stringResource(R.string.src_picker_none, source.id.label)
                        }
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (app != null) {
                        TextButton(
                            onClick = {
                                if (isOpen) {
                                    openSource = null
                                } else {
                                    openSource = source.id
                                    loadFiles(source.id)
                                }
                            },
                        ) {
                            Text(stringResource(if (isOpen) R.string.src_hide_files else R.string.src_files))
                        }
                    }
                }

                if (isOpen) {
                    when {
                        loadingFiles == source.id -> Row(
                            Modifier.fillMaxWidth().padding(start = 32.dp, top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.src_resolving),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        fileError[source.id] != null -> Text(
                            fileError[source.id].orEmpty().ifBlank { stringResource(R.string.src_file_list_failed) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 32.dp, top = 6.dp),
                        )

                        files[source.id]?.isEmpty() == true -> Text(
                            stringResource(R.string.src_no_file),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 32.dp, top = 6.dp),
                        )

                        else -> files[source.id].orEmpty().forEach { file ->
                            Row(
                                Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        file.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val meta = listOfNotNull(
                                        file.size,
                                        file.note,
                                        file.sha256?.let { "sha-256 ${it.take(12)}…" },
                                    ).joinToString(" · ")
                                    if (meta.isNotBlank()) {
                                        Text(
                                            meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = {
                                    val current = app ?: return@TextButton
                                    enqueueSourceFile(context, current, file)
                                    Toast.makeText(context, queued, Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }) {
                                    Text(stringResource(R.string.action_download))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One source's answer, kept for the sheet. */
private data class SourceMatchRow(val app: SourceApp)

/**
 * Sends a resolved source file into the same queue the catalogue's downloads use.
 *
 * A source row has no catalogue post behind it, so the row's post is a stable pseudo-id built from
 * the source's own key: the queue keeps one row per (post, url), and the key is what tells two
 * downloads of the same site apart.
 */
internal fun enqueueSourceFile(context: Context, app: SourceApp, file: SourceFile) {
    val post = PostSummary(
        id = app.key.hashCode().toLong(),
        title = app.title,
        imageUrl = app.iconUrl,
        bannerUrl = null,
        publisher = app.source.label,
        genre = app.version,
        size = file.size,
        type = null,
        modInfo = null,
        latestVersion = app.version,
        packageName = null,
        ratingAvg = 0.0,
        ratingCount = 0,
        views = 0L,
        updatedAt = null,
    )
    val downloadFile = DownloadFile(
        kind = file.label,
        role = "main",
        abi = "universal",
        sizeText = file.size.orEmpty(),
        url = file.url,
        note = file.note ?: file.sha256?.let { "published sha-256 ${it.take(16)}…" },
        recommended = false,
    )
    DownloadEngine.enqueue(
        context = context,
        post = post,
        file = downloadFile,
        versionLabel = app.version ?: "",
        postId = post.id,
        // A source that publishes a sha-256 per file has it compared before the row is READY rather
        // than left as a note next to the button.
        expectedSha256 = file.sha256,
    )
}
