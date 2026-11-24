package dev.aurastore.store.ui.source

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
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
import dev.aurastore.store.theme.PillShape
import dev.aurastore.store.ui.ArtImage
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.formatSize
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.skeletonPulse
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
    /**
     * The art the catalogue already holds for this title.
     *
     * A source's own search row does not always carry an image. The queue keeps the icon so the
     * brand-bar button and the download panel can show the app being fetched, not just a generic
     * arrow; when a match has no image of its own this is the one it borrows.
     */
    val iconUrl: String? = null,
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
 * The answer is a place first and a file second, so the sheet is arranged that way: one row of
 * places, each saying whether it has the app, and under it the files of the place that is open.
 * A site with no answer stays in the row and says so instead of disappearing, because "this one does
 * not have it" is the reason the next one gets picked. Every file shown is already resolved to a
 * direct link — the tap queues bytes, it does not start a page fetch.
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
    var loading by remember { mutableStateOf<Set<SourceId>>(emptySet()) }

    /** The app a source has for this request, if it has one at all. */
    fun appFor(source: SourceId): SourceApp? {
        val current = request ?: return null
        if (source == SourceId.LITEAPKS && current.cataloguePostId != null) {
            return SourceApp(
                source = SourceId.LITEAPKS,
                key = current.cataloguePostId.toString(),
                title = current.title,
                iconUrl = current.iconUrl,
                cataloguePostId = current.cataloguePostId,
            )
        }
        return matches[source]?.app
    }

    /** Reads one source's files and puts them under its name. A failed read leaves no list to reuse. */
    fun loadFiles(source: SourceId) {
        val app = appFor(source) ?: return
        if (files.containsKey(source) || source in loading) return
        loading = loading + source
        fileError = fileError - source
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) { Sources.byId(source).files(app) }
            }
            loading = loading - source
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
        loading = emptySet()
        openSource = null
        if (current.only != null) {
            // The row named the site; the sheet only has to show that site's files.
            current.app?.let { matches = mapOf(it.source to SourceMatchRow(it)) }
            searching = false
            openSource = current.only
            loadFiles(current.only)
            return@LaunchedEffect
        }
        val found = SourceHub.find(current.title, enabled)
        matches = found.associate { match ->
            // A site's answer often comes without art. The catalogue's own picture is the same app,
            // so it fills the gap rather than leaving the sheet and the queue with a blank row.
            val app = if (match.app.iconUrl == null) match.app.copy(iconUrl = current.iconUrl) else match.app
            match.source to SourceMatchRow(app)
        }
        searching = false
        // The sheet opens on the source the user named in Settings, or on the store's own catalogue.
        val preferred = current.preferred ?: SourceId.LITEAPKS
        openSource = preferred
        loadFiles(preferred)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val only = request?.only
    val sources = Sources.enabled(enabled).filter { only == null || it.id == only }
    val selected = openSource ?: only ?: sources.firstOrNull()?.id
    val selectedApp = selected?.let(::appFor)
    val iconUrl = request?.iconUrl ?: selectedApp?.iconUrl
    val queued = selectedApp?.let { app ->
        stringResource(R.string.src_downloading, app.title, app.source.label)
    }.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            // ---------------------------------------------------------------- what is being fetched
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtImage(
                    url = iconUrl,
                    title = request?.title,
                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.src_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        request?.title.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                if (only != null) {
                    stringResource(R.string.src_pick_file_body, only.label)
                } else {
                    stringResource(R.string.src_picker_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            // ----------------------------------------------------------------------- the places
            if (only == null) {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sources, key = { it.id }) { source ->
                        val app = appFor(source.id)
                        SourceChip(
                            label = source.id.label,
                            status = when {
                                searching -> stringResource(R.string.src_picker_searching)
                                app != null -> listOfNotNull(
                                    stringResource(R.string.src_picker_found),
                                    stringResource(R.string.src_picker_default)
                                        .takeIf { defaultSource == source.id },
                                    app.version ?: app.size,
                                ).joinToString(" · ")
                                else -> stringResource(R.string.src_picker_none, source.id.label)
                            },
                            selected = selected == source.id,
                            found = app != null,
                            onClick = {
                                openSource = source.id
                                loadFiles(source.id)
                            },
                        )
                    }
                }
            }

            // ------------------------------------------------------------------- the files themselves
            Spacer(Modifier.height(20.dp))
            if (selected != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.src_files),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (selectedApp != null && !searching) {
                        Text(
                            selectedApp.version ?: selectedApp.size.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when {
                        searching || selected in loading -> FileSkeletons()

                        selectedApp == null -> EmptyNotice(
                            title = stringResource(R.string.src_picker_none, selected.label),
                            body = stringResource(R.string.src_none_body, request?.title.orEmpty()),
                        )

                        fileError[selected] != null -> EmptyNotice(
                            title = fileError[selected].orEmpty()
                                .ifBlank { stringResource(R.string.src_file_list_failed) },
                            body = stringResource(R.string.src_picker_body),
                            action = stringResource(R.string.try_again),
                            onAction = { loadFiles(selected) },
                        )

                        files[selected]?.isEmpty() == true -> EmptyNotice(
                            title = stringResource(R.string.src_no_file),
                            body = stringResource(R.string.src_picker_none, selected.label),
                        )

                        else -> files[selected].orEmpty().forEach { file ->
                            SourceFileCard(
                                file = file,
                                onDownload = {
                                    if (file.viaBrowser) {
                                        openSourceFile(context, file)
                                    } else {
                                        enqueueSourceFile(context, selectedApp, file, request?.cataloguePostId)
                                        Toast.makeText(context, queued, Toast.LENGTH_SHORT).show()
                                    }
                                    onDismiss()
                                },
                            )
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
 * A place, and whether it has the app.
 *
 * The chip carries the verdict so the row answers the question without tapping through every site:
 * a filled chip is the open one, and "Not on 5play" is as useful as the version number beside it.
 */
@Composable
private fun SourceChip(
    label: String,
    status: String,
    selected: Boolean,
    found: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "source-chip",
    )
    val title = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val meta = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier
            .widthIn(min = 128.dp, max = 210.dp)
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                },
            )
            .playClickable(onClick = onClick, pressedScale = 0.98f)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (selected) {
                Spacer(Modifier.width(6.dp))
                GlyphIcon(Glyph.Check, size = 15.dp, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = if (found || selected) meta else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One artefact, resolved: what it is, how big it is, and the button that puts it in the queue.
 *
 * The label is the source's own description of the file, and its order differs per site — one puts
 * the build first, another the app's name — so it is printed whole rather than parsed into a title.
 * The button sits on the footer beside the facts, leaving the description the card's full width.
 * The digest is printed when the site publishes one, because that is the value the engine compares
 * before the row becomes installable.
 */
@Composable
private fun SourceFileCard(file: SourceFile, onDownload: () -> Unit) {
    val size = formatSize(file.size)
    // A site that already puts the size in the label gets it once, not twice. Compare the whole
    // printed size, not its digits: `20` is inside `2023`, and a year is not a byte count.
    val sizeInLabel = file.size?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { file.label.contains(it, ignoreCase = true) } == true
    val facts = buildList {
        if (size != null && !sizeInLabel) add(size)
        file.note?.takeIf { it.isNotBlank() && !file.label.contains(it, true) }?.let(::add)
        file.sha256?.let { add("sha-256 ${it.take(16)}…") }
    }.joinToString(" · ")
    SurfaceCard {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        Glyph.ArrowDown,
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    file.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    facts,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onDownload,
                    shape = PillShape,
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(
                        stringResource(if (file.viaBrowser) R.string.action_open else R.string.action_download),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** The bordered sheet card, in the corner size the store uses for a medium surface. */
@Composable
private fun SurfaceCard(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** A source that cannot offer a file, and why — or the way to ask it again. */
@Composable
private fun EmptyNotice(
    title: String,
    body: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    SurfaceCard {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(3.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null && onAction != null) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                    Text(action, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Two breathing cards while a site is being read, so the sheet never jumps from nothing to a list. */
@Composable
private fun FileSkeletons() {
    val pulse = skeletonPulse()
    Column(
        Modifier.fillMaxWidth().alpha(pulse),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(2) {
            SurfaceCard {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.fillMaxWidth(0.65f).height(14.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                        Box(
                            Modifier.fillMaxWidth(0.35f).height(10.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.width(96.dp).height(38.dp).clip(PillShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
        }
    }
}

/**
 * Opens a file whose host gates its download behind a page of its own.
 *
 * The browser is the only client that can answer those hosts' human checks; handing the page to it
 * is the honest alternative to hiding the file. A resolved file is never opened this way — it goes
 * to the queue, where pause, resume, hash and install already work.
 */
internal fun openSourceFile(context: Context, file: SourceFile): Boolean {
    if (!file.viaBrowser) return false
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, file.url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}

/**
 * Sends a resolved source file into the same queue the catalogue's downloads use.
 *
 * A source row has no catalogue post behind it, so the row's post is a stable pseudo-id built from
 * the source's own key: the queue keeps one row per (post, url), and the key is what tells two
 * downloads of the same site apart.
 */
internal fun enqueueSourceFile(
    context: Context,
    app: SourceApp,
    file: SourceFile,
    originPostId: Long? = null,
) {
    if (file.viaBrowser) {
        openSourceFile(context, file)
        return
    }
    // A catalogue file carries the post it belongs to, so the app's own page recognises the row while
    // it downloads and after it installs. A source-only answer has no post, and its stable pseudo-id
    // is built from the source's own key: the queue keeps one row per (post, url), and the key is
    // what tells two downloads of the same site apart.
    val postId = app.cataloguePostId ?: app.key.hashCode().toLong()
    val post = PostSummary(
        id = postId,
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
    val segments = file.label.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
    // The source's label is `build · type · abi`; the queue already prints the version and the
    // ABI beside the type, so only the type belongs here. Passing the whole label made the row
    // read `v454… - Mod · apk · universal · universal · 420.8 MB`, saying it twice.
    val downloadFile = DownloadFile(
        kind = segments.drop(1).firstOrNull().orEmpty(),
        role = "main",
        abi = segments.getOrNull(2) ?: "universal",
        sizeText = file.size.orEmpty(),
        url = file.url,
        note = file.note ?: file.sha256?.let { "published sha-256 ${it.take(16)}…" },
        recommended = false,
    )
    // The source-level version describes the app, not the file; when a site answers with several
    // builds, the file's own note is the one that names the build in the chooser.
    val fileVersion = file.note?.trim()?.takeIf { note ->
        note.isNotEmpty() && (note.first().isDigit() ||
            (note.startsWith("v", true) && note.getOrNull(1)?.isDigit() == true))
    }
    DownloadEngine.enqueue(
        context = context,
        post = post,
        file = downloadFile,
        versionLabel = app.version ?: fileVersion ?: segments.firstOrNull().orEmpty(),
        postId = postId,
        // A source that publishes a sha-256 per file has it compared before the row is READY rather
        // than left as a note next to the button.
        expectedSha256 = file.sha256,
        originPostId = originPostId,
        fileName = file.fileName,
    )
}
