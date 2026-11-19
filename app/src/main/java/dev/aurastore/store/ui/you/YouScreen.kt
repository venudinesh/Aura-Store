package dev.aurastore.store.ui.you

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.data.AvatarFrame
import dev.aurastore.store.data.AvatarStore
import dev.aurastore.store.data.CUSTOM_AVATAR
import dev.aurastore.store.data.CoverStore
import dev.aurastore.store.data.CoverStyle
import dev.aurastore.store.data.PackInstaller
import dev.aurastore.store.data.PackLibrary
import dev.aurastore.store.data.SpecialAnime
import dev.aurastore.store.data.Settings
import dev.aurastore.store.data.StoreUsage
import dev.aurastore.store.data.StoreUsageEntry
import dev.aurastore.store.data.UsageAccess
import dev.aurastore.store.data.UsageReport
import dev.aurastore.store.data.Wishlist
import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.ui.profile.ConfirmDeleteDialog
import dev.aurastore.store.ui.profile.DeletePostersDialog
import dev.aurastore.store.ui.profile.PackBadge
import dev.aurastore.store.ui.profile.PackDeletedPoster
import dev.aurastore.store.ui.profile.PackDownloadAll
import dev.aurastore.store.ui.profile.missingOf
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.install.AutoUpdater
import dev.aurastore.store.install.Installer
import dev.aurastore.store.install.UpdateTarget
import dev.aurastore.store.install.Updates
import dev.aurastore.store.theme.EmphasizedDecelerateEasing
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardAccelerateEasing
import dev.aurastore.store.ui.ActionKind
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.InstallButton
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.RowAction
import dev.aurastore.store.ui.ScrollToTopOnRetap
import dev.aurastore.store.ui.formatWhen
import dev.aurastore.store.ui.library.AppGlyph
import dev.aurastore.store.ui.library.GroupCard
import dev.aurastore.store.ui.library.GroupHeader
import dev.aurastore.store.ui.library.LoadingRows
import dev.aurastore.store.ui.library.PermissionStrip
import dev.aurastore.store.ui.library.RowCount
import dev.aurastore.store.ui.library.RowRule
import dev.aurastore.store.ui.library.StatusStrip
import dev.aurastore.store.ui.library.rememberAppIcon
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.profile.AvatarCropDialog
import dev.aurastore.store.ui.profile.AvatarPicker
import dev.aurastore.store.ui.profile.CoverArt
import dev.aurastore.store.ui.profile.ImageCropDialog
import dev.aurastore.store.ui.profile.ProfilePicture
import dev.aurastore.store.ui.profile.rememberPhaseClock
import dev.aurastore.store.ui.rememberStoreActions
import dev.aurastore.store.ui.wishlist.EmptyWishlist
import dev.aurastore.store.ui.wishlist.WishRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The You tab, as the profile it always was under the doors.
 *
 * The five subjects that used to be scattered between this tab, a sheet and a segmented control now
 * live in one place with a name each: the doors, the time this store's apps have spent on screen, the
 * saved list, the updates and the three things the profile can wear. The picture, the frame and the
 * cover are one drawing each, so the top bar, the hero and every picker show the same choice without a
 * bitmap being copied around.
 */
@Composable
fun YouScreen(
    onOpenPost: (Long) -> Unit,
    onOpenSourceApp: (SourceApp) -> Unit,
    onOpenDevice: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    /** Bumped by the shell when the tab already showing is tapped again. */
    scrollTopKey: Int = 0,
    /** The page's scroll position, owned by the shell so Back from a pushed page returns to it. */
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    val section = ProfileSection.entries[sectionIndex.coerceIn(0, ProfileSection.entries.lastIndex)]

    // Customisation is a page inside this tab, not a route, so the shell cannot see it. Without this
    // handler back falls through to the tab history and leaves You for whichever tab was open
    // before. The pill row is the page stack here: back returns any other section to the profile.
    BackHandler(enabled = sectionIndex != ProfileSection.OVERVIEW.ordinal) {
        sectionIndex = ProfileSection.OVERVIEW.ordinal
        scope.launch {
            if (PlayMotion.enabled) listState.animateScrollToItem(0) else listState.scrollToItem(0)
        }
    }

    // One clock for every ring on this page. Three pictures keeping their own time meant three
    // whole-page redraws a tick; this way the hero turns, the bar stands still, and the page has
    // one heartbeat. Ten a second suits a ring this slow.
    val framePhase = rememberPhaseClock(
        animate = PlayMotion.enabled,
        intervalNanos = 100_000_000L,
    )

    val avatar by Settings.avatar.collectAsStateWithLifecycle()
    val frame by Settings.avatarFrame.collectAsStateWithLifecycle()
    val coverStyle by Settings.coverStyle.collectAsStateWithLifecycle()
    val coverPoster by Settings.coverPoster.collectAsStateWithLifecycle()
    val coverCustom by Settings.coverCustom.collectAsStateWithLifecycle()
    val coverVersion by CoverStore.version.collectAsStateWithLifecycle()
    val coverPhoto = remember(coverCustom, coverVersion) {
        if (coverCustom) CoverStore.load(context) else null
    }

    var pickerOpen by remember { mutableStateOf(false) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var coverCropSource by remember { mutableStateOf<Bitmap?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) { AvatarStore.decode(context, uri) }
                if (decoded != null) cropSource = decoded
            }
        }
    }
    val coverGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) { AvatarStore.decode(context, uri) }
                if (decoded != null) coverCropSource = decoded
            }
        }
    }

    // A count, not the queue: a progress tick changes neither figure, and this tab must not recompose
    // four times a second while a file moves.
    val activeDownloads by remember {
        DownloadEngine.items.map { rows -> rows.count { it.isActive } }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(0)
    var canInstall by remember { mutableStateOf(Installer.canInstall(context)) }

    val actions = rememberStoreActions()
    val downloads by DownloadEngine.items.collectAsStateWithLifecycle()
    val lastSync by Settings.lastSync.collectAsStateWithLifecycle()
    val wishes by Wishlist.entries.collectAsStateWithLifecycle()

    var usageGranted by remember { mutableStateOf(UsageAccess.granted(context)) }
    var usage by remember { mutableStateOf<UsageReport?>(null) }
    var usageToken by remember { mutableIntStateOf(0) }

    var updateTargets by remember { mutableStateOf<List<UpdateTarget>?>(null) }
    var updateToken by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(context) {
        canInstall = Installer.canInstall(context)
        usageGranted = UsageAccess.granted(context)
        usageToken++
        onPauseOrDispose { }
    }

    LaunchedEffect(section, usageGranted, usageToken) {
        if (section != ProfileSection.TIME) return@LaunchedEffect
        if (!usageGranted) {
            usage = null
            return@LaunchedEffect
        }
        usage = withContext(Dispatchers.IO) { StoreUsage.load(context) }
    }

    LaunchedEffect(section, updateToken) {
        if (section != ProfileSection.UPDATES) return@LaunchedEffect
        // The first visit reads through the scanner's cache; a tap on "Check again" is the only thing
        // that forces a network pass, so returning to the tab is cheap.
        if (updateTargets != null && updateToken == 0) return@LaunchedEffect
        updateTargets = withContext(Dispatchers.IO) {
            AutoUpdater.available(context, force = updateToken > 0)
        }
    }

    ScrollToTopOnRetap(scrollTopKey, listState)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp + LocalNavBarInset.current),
    ) {
        item {
            ProfileHero(
                avatar = avatar,
                frame = frame,
                coverStyle = coverStyle,
                coverPoster = coverPoster,
                coverPhoto = coverPhoto,
                phase = { framePhase.floatValue },
                onPicture = { pickerOpen = true },
                onCustomise = { sectionIndex = ProfileSection.CUSTOMISE.ordinal },
            )
        }

        item {
            SectionTabs(
                current = section,
                onSelect = { sectionIndex = it.ordinal },
            )
        }

        when (section) {
            ProfileSection.OVERVIEW -> {
                // The strip that goes away the moment the user grants the permission should leave,
                // not vanish: everything under it slides up behind it as it does.
                item {
                    AnimatedVisibility(
                        visible = !canInstall,
                        enter = fadeIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing)) +
                            slideInVertically(
                                PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing),
                            ) { it / 2 },
                        exit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                            slideOutVertically(
                                PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing),
                            ) { -it / 2 },
                    ) {
                        PermissionStrip(onAllow = { Installer.openUnknownSourcesSettings(context) })
                    }
                }

                item { GroupHeader(stringResource(R.string.you_library)) }
                item {
                    GroupCard {
                        HubRow(
                            glyph = Glyph.Grid,
                            title = stringResource(R.string.you_device),
                            note = stringResource(R.string.you_device_note),
                            count = 0,
                            onClick = onOpenDevice,
                        )
                        RowRule()
                        HubRow(
                            glyph = Glyph.Download,
                            title = stringResource(R.string.sheet_downloads),
                            note = stringResource(R.string.sheet_downloads_note),
                            count = activeDownloads,
                            onClick = onOpenDownloads,
                        )
                    }
                }

                item { GroupHeader(stringResource(R.string.you_store)) }
                item {
                    GroupCard {
                        HubRow(
                            glyph = Glyph.Shield,
                            title = stringResource(R.string.sheet_settings),
                            note = stringResource(R.string.sheet_settings_note),
                            count = 0,
                            onClick = onOpenSettings,
                        )
                        RowRule()
                        HubRow(
                            glyph = Glyph.Info,
                            title = stringResource(R.string.sheet_about),
                            note = stringResource(R.string.sheet_about_note),
                            count = 0,
                            onClick = onOpenAbout,
                        )
                    }
                }
            }

            ProfileSection.TIME -> {
                item { GroupHeader(stringResource(R.string.profile_tab_time)) }
                if (!usageGranted) {
                    item {
                        UsageGate(onAllow = { UsageAccess.open(context) })
                    }
                } else {
                    val report = usage
                    if (report == null) {
                        item { GroupCard { LoadingRows(3) } }
                    } else {
                        item { UsageTotals(report) }
                        if (report.entries.isEmpty()) {
                            item { NothingHere(R.string.profile_usage_empty_title, R.string.profile_usage_empty_body) }
                        } else {
                            item {
                                GroupCard {
                                    report.entries.forEachIndexed { index, entry ->
                                        if (index > 0) RowRule()
                                        UsageRow(entry)
                                    }
                                }
                            }
                        }
                        item {
                            Text(
                                stringResource(R.string.profile_usage_footnote),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                        }
                    }
                }
            }

            ProfileSection.WISHLIST -> {
                item {
                    GroupHeader(
                        stringResource(R.string.tab_wishlist),
                        stringResource(R.string.you_wishlist_note),
                    )
                }
                if (wishes.isEmpty()) {
                    item { EmptyWishlist() }
                } else {
                    items(wishes, key = { it.key }) { entry ->
                        WishRow(
                            entry = entry,
                            onOpen = {
                                val postId = entry.postId
                                val app = entry.toSourceApp()
                                when {
                                    postId != null -> onOpenPost(postId)
                                    app != null -> onOpenSourceApp(app)
                                }
                            },
                            onRemove = {
                                Wishlist.remove(context, entry.key)
                                Toast.makeText(context, R.string.wishlist_removed, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }

            ProfileSection.UPDATES -> {
                item {
                    GroupHeader(
                        stringResource(R.string.you_manage),
                        stringResource(R.string.you_manage_note),
                    )
                }
                val targets = updateTargets
                if (targets == null) {
                    item {
                        StatusStrip(text = stringResource(R.string.you_checking)) {}
                        GroupCard { LoadingRows(3) }
                    }
                } else {
                    // AutoUpdater already removed the ignored builds; the second read is only here
                    // because a row can be on screen while the ignore is written.
                    val available = targets.filterNot {
                        Settings.isUpdateIgnored(it.packageName, it.catalogueVersion)
                    }
                    val inPlace = available.filter { it.autoUpdatable }
                    val notFromHere = available.filterNot { it.autoUpdatable }

                    if (available.isEmpty()) {
                        item {
                            NothingHere(R.string.you_up_to_date, R.string.you_up_to_date_body)
                        }
                    } else {
                        item {
                            StatusStrip(
                                text = stringResource(
                                    R.string.you_checked,
                                    formatWhen(lastSync) ?: stringResource(R.string.you_just_now),
                                ),
                            ) {
                                if (inPlace.isNotEmpty()) {
                                    InstallButton(
                                        RowAction(
                                            ActionKind.UPDATE,
                                            label = stringResource(R.string.you_update_all),
                                        ),
                                        onClick = { inPlace.forEach { target -> actions.update(target) } },
                                        compact = true,
                                    )
                                }
                            }
                        }
                        if (inPlace.isNotEmpty()) {
                            item { GroupHeader(stringResource(R.string.you_from_us)) }
                            item {
                                GroupCard {
                                    inPlace.forEachIndexed { index, target ->
                                        if (index > 0) RowRule()
                                        ProfileUpdateRow(
                                            target = target,
                                            busy = downloads.firstOrNull {
                                                it.postId == target.postId && !it.dataFile && it.isActive
                                            },
                                            onOpen = { onOpenPost(target.postId) },
                                            onUpdate = { actions.update(target) },
                                        )
                                    }
                                }
                            }
                        }
                        if (notFromHere.isNotEmpty()) {
                            item { GroupHeader(stringResource(R.string.you_outside)) }
                            item {
                                GroupCard {
                                    notFromHere.forEachIndexed { index, target ->
                                        if (index > 0) RowRule()
                                        ProfileUpdateRow(
                                            target = target,
                                            busy = downloads.firstOrNull {
                                                it.postId == target.postId && !it.dataFile && it.isActive
                                            },
                                            onOpen = { onOpenPost(target.postId) },
                                            onUpdate = { actions.update(target) },
                                            kind = ActionKind.INSTALL,
                                            label = R.string.you_get,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { updateToken++ }) {
                                Text(stringResource(R.string.you_check_again))
                            }
                        }
                    }
                }
            }

            ProfileSection.CUSTOMISE -> {
                item {
                    GroupHeader(
                        stringResource(R.string.profile_picker_title),
                        stringResource(R.string.profile_picker_body),
                    )
                }
                item {
                    GroupCard {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfilePicture(
                                choice = avatar,
                                size = 64.dp,
                                frame = frame,
                                phase = { framePhase.floatValue },
                                contentDescription = stringResource(R.string.a11y_profile_picture),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .playClickable(onClick = { pickerOpen = true }),
                            )
                            Spacer(Modifier.width(14.dp))
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { pickerOpen = true }) {
                                Text(stringResource(R.string.profile_photo))
                            }
                        }
                    }
                }

                item {
                    GroupHeader(
                        stringResource(R.string.profile_frame_title),
                        stringResource(R.string.profile_frame_note),
                    )
                }
                item {
                    GroupCard {
                        FramePicker(
                            current = frame,
                            choice = avatar,
                            onPick = { Settings.setAvatarFrame(context, it) },
                        )
                    }
                }

                item {
                    GroupHeader(
                        stringResource(R.string.profile_cover_title),
                        stringResource(R.string.profile_cover_note),
                    )
                }
                item {
                    GroupCard {
                        CoverPicker(
                            style = coverStyle,
                            poster = coverPoster,
                            custom = coverCustom,
                            photo = coverPhoto,
                            onStyle = { picked, index -> Settings.setCover(context, picked, index) },
                            onCustom = {
                                if (coverPhoto != null) {
                                    Settings.setCustomCover(context)
                                } else {
                                    coverGallery.launch("image/*")
                                }
                            },
                            onGallery = { coverGallery.launch("image/*") },
                        )
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        AvatarPicker(
            current = avatar,
            onDismiss = { pickerOpen = false },
            onPick = { choice ->
                Settings.setAvatar(context, choice)
                pickerOpen = false
            },
            onGallery = {
                pickerOpen = false
                gallery.launch("image/*")
            },
        )
    }

    cropSource?.let { source ->
        AvatarCropDialog(
            source = source,
            onCancel = { cropSource = null },
            onSave = { square ->
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { AvatarStore.save(context, square) }
                    if (saved) {
                        Settings.setAvatar(context, CUSTOM_AVATAR)
                        cropSource = null
                    }
                }
            },
        )
    }

    coverCropSource?.let { source ->
        ImageCropDialog(
            source = source,
            aspect = 16f / 9f,
            outputWidth = 1600,
            title = R.string.profile_cover_crop_title,
            hint = R.string.profile_cover_crop_hint,
            onCancel = { coverCropSource = null },
            onSave = { wide ->
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { CoverStore.save(context, wide) }
                    if (saved) {
                        Settings.setCustomCover(context)
                        coverCropSource = null
                    }
                }
            },
        )
    }
}

/** The five pages of the profile, in the order the pill row draws them. */
private enum class ProfileSection(@StringRes val label: Int, val glyph: Glyph) {
    OVERVIEW(R.string.profile_tab_overview, Glyph.Person),
    TIME(R.string.profile_tab_time, Glyph.Tick),
    WISHLIST(R.string.tab_wishlist, Glyph.Heart),
    UPDATES(R.string.you_manage, Glyph.Update),
    CUSTOMISE(R.string.profile_tab_customise, Glyph.Brand),
}

/**
 * The cover, the picture and the one tap that changes both.
 *
 * The avatar sits inside the cover rather than below it so the profile has a single object at its
 * top; a row of two stacked headers is what made the old screen read as a title and then a separate
 * photo control.
 */
@Composable
private fun ProfileHero(
    avatar: String?,
    frame: AvatarFrame,
    coverStyle: CoverStyle,
    coverPoster: Int,
    coverPhoto: Bitmap?,
    phase: (() -> Float)?,
    onPicture: () -> Unit,
    onCustomise: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(136.dp)
                .clip(RoundedCornerShape(26.dp)),
        ) {
            CoverArt(
                style = coverStyle,
                photo = coverPhoto,
                poster = coverPoster,
                modifier = Modifier.fillMaxSize(),
            )
            // A floor under the avatar, so a pale cover cannot swallow the picture's edge.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.34f)),
                        ),
                    ),
            )
            ProfilePicture(
                choice = avatar,
                size = 78.dp,
                frame = frame,
                phase = phase,
                contentDescription = stringResource(R.string.a11y_profile_picture),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
                    .clip(CircleShape)
                    .playClickable(onClick = onPicture),
            )
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.42f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .clip(CircleShape)
                    .playClickable(onClick = onCustomise),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GlyphIcon(Glyph.Brand, size = 14.dp, tint = Color.White)
                    Text(
                        stringResource(R.string.profile_customise),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/** Five equal cells, so every page is visible at once on the narrowest phone this ships to. */
@Composable
private fun SectionTabs(current: ProfileSection, onSelect: (ProfileSection) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ProfileSection.entries.forEach { entry ->
            val selected = entry == current
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    )
                    .playClickable(onClick = { onSelect(entry) })
                    .padding(horizontal = 2.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GlyphIcon(
                    entry.glyph,
                    size = 18.dp,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(entry.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The day and the lifetime, side by side above the rows. */
@Composable
private fun UsageTotals(report: UsageReport) {
    GroupCard {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.profile_usage_today),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    StoreUsage.formatDuration(report.todayMs),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.profile_usage_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    StoreUsage.formatDuration(report.totalMs),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

/** One app the store installed, with the two figures that make the row worth reading. */
@Composable
private fun UsageRow(entry: StoreUsageEntry) {
    val icon = rememberAppIcon(entry.packageName)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyph(icon = icon, label = entry.label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    R.string.profile_usage_row,
                    StoreUsage.formatDuration(entry.todayMs),
                    StoreUsage.formatDuration(entry.totalMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Android's usage-access switch, explained before it is opened. */
@Composable
private fun UsageGate(onAllow: () -> Unit) {
    GroupCard {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Shield, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.profile_usage_gate_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.profile_usage_gate_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
            )
            InstallButton(
                action = RowAction(ActionKind.PERMISSION),
                onClick = onAllow,
                compact = true,
            )
        }
    }
}

/** A good outcome or a quiet list, said plainly instead of leaving a blank panel. */
@Composable
private fun NothingHere(@StringRes title: Int, @StringRes body: Int) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(60.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlyphIcon(
                    Glyph.Tick,
                    size = 28.dp,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 14.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** One app the catalogue has a newer build for, drawn the way the Updates page draws it. */
@Composable
private fun ProfileUpdateRow(
    target: UpdateTarget,
    busy: DownloadItem?,
    onOpen: () -> Unit,
    onUpdate: () -> Unit,
    kind: ActionKind = ActionKind.UPDATE,
    @StringRes label: Int = R.string.action_update,
) {
    val icon = rememberAppIcon(target.packageName)
    Row(
        Modifier
            .fillMaxWidth()
            .playClickable(onClick = onOpen, pressedScale = 0.99f)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyph(icon = icon, label = target.title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                target.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Updates.delta(target.installedVersion, target.catalogueVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        InstallButton(
            action = busy?.let { progressAction(it) }
                ?: RowAction(kind, label = stringResource(label)),
            onClick = { if (busy == null) onUpdate() },
            compact = true,
        )
    }
}

/** A row whose download is moving shows the ring and the percentage, like every other row. */
private fun progressAction(item: DownloadItem): RowAction = when (item.state) {
    DownloadState.QUEUED -> RowAction(ActionKind.QUEUED, item.progress)
    DownloadState.VERIFYING -> RowAction(ActionKind.VERIFYING, item.progress)
    else -> RowAction(ActionKind.DOWNLOADING, item.progress)
}

/** Sixty-nine rings, four to a row, each drawn around the picture the profile actually wears. */
@Composable
private fun FramePicker(current: AvatarFrame, choice: String?, onPick: (AvatarFrame) -> Unit) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        FrameRows(
            entries = AvatarFrame.entries.filter { it.anime == null },
            current = current,
            choice = choice,
            onPick = onPick,
        )
        // The pack rings are one set drawn from one pack: they sit under their own name rather
        // than mixed through the house rings, which are a different kind of picture.
        Text(
            stringResource(R.string.profile_frame_anime),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 2.dp),
        )
        FrameRows(
            entries = AvatarFrame.entries.filter { it.anime != null },
            current = current,
            choice = choice,
            onPick = onPick,
        )
    }
}

@Composable
private fun FrameRows(
    entries: List<AvatarFrame>,
    current: AvatarFrame,
    choice: String?,
    onPick: (AvatarFrame) -> Unit,
) {
    entries.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { entry ->
                val selected = entry == current
                Column(
                    Modifier
                        .weight(1f)
                        .playClickable(onClick = { onPick(entry) })
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                } else {
                                    Color.Transparent
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Previews hold still. Sixty-nine rings turning made the sheet redraw the
                        // page for every tick; the ring that moves is the one worn up top.
                        ProfilePicture(
                            choice = choice,
                            size = 48.dp,
                            frame = entry,
                            animate = false,
                        )
                    }
                    Text(
                        stringResource(entry.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        // The pack's labels are anime titles: two lines fit "JoJo's Bizarre
                        // Adventure" where one line clipped it to a guess.
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
                    )
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Drawn covers, the Special pack and the user's own photograph, in a two-column grid. */
@Composable
private fun CoverPicker(
    style: CoverStyle,
    poster: Int,
    custom: Boolean,
    photo: Bitmap?,
    onStyle: (CoverStyle, Int) -> Unit,
    onCustom: () -> Unit,
    onGallery: () -> Unit,
) {
    val context = LocalContext.current
    // Which anime's gallery is open inside the card. The pack is thirty-one doors, each with
    // thirty banners behind it: one tile per anime is the only list that stays readable at this size.
    var openAnime by remember { mutableStateOf<SpecialAnime?>(null) }
    val removed by PackLibrary.removed.collectAsStateWithLifecycle()
    val installs by PackInstaller.state.collectAsStateWithLifecycle()
    // Every card stays in the catalogue: one whose banners were all taken off is the door to its
    // thirty holes, which is where the installer lives.
    val facesByAnime = remember(removed) {
        SpecialAnime.entries.associateWith { PackLibrary.visible(it).firstOrNull() }
    }
    var deleting by remember { mutableStateOf<Pair<SpecialAnime, Set<Int>>?>(null) }
    var deletePhoto by remember { mutableStateOf(false) }
    val hold = stringResource(R.string.a11y_hold_delete)
    val open = openAnime
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The Special pack sits under its own header rather than as more drawn-looking tiles:
        // a painted banner and a drawing are two kinds of cover, and the label says which is which.
        val drawn = CoverStyle.entries.filterNot { it.special }
        drawn.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { entry ->
                    CoverCell(
                        label = stringResource(entry.label),
                        style = entry,
                        photo = null,
                        selected = !custom && entry == style,
                        onClick = { onStyle(entry, 0) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        // The photo doors come before the pack: the last row of a long card sits against the gesture
        // bar, and an action that can only be reached by a scroll at the very edge is half lost.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (photo == null) {
                // Nothing to draw yet: a slot waiting for the picker, not the House art wearing
                // the label "Your photo".
                CoverSlot(
                    label = stringResource(R.string.profile_cover_custom),
                    onClick = onCustom,
                    modifier = Modifier.weight(1f),
                )
            } else {
                CoverCell(
                    label = stringResource(R.string.profile_cover_custom),
                    style = style,
                    photo = photo,
                    selected = custom,
                    onClick = onCustom,
                    onLongClick = { deletePhoto = true },
                    onLongClickLabel = hold,
                    modifier = Modifier.weight(1f),
                )
            }
            CoverSlot(
                label = stringResource(R.string.profile_cover_gallery),
                onClick = onGallery,
                modifier = Modifier.weight(1f),
                glyph = Glyph.Grid,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.special_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            PackDownloadAll()
        }
        if (open == null) {
            Text(
                stringResource(R.string.special_covers_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Three to a row: the names run long ("Fullmetal Alchemist"), and the tile is wide, so
            // the third column is what fits without a two-line label.
            SpecialAnime.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { anime ->
                        val entry = CoverStyle.forAnime(anime)
                        val face = facesByAnime[anime]
                        CoverCell(
                            label = stringResource(anime.label),
                            style = entry,
                            poster = face ?: 0,
                            photo = null,
                            selected = !custom && entry == style,
                            onClick = { openAnime = anime },
                            onLongClick = { deleting = anime to PackLibrary.visible(anime).toSet() },
                            onLongClickLabel = hold,
                            modifier = Modifier.weight(1f),
                            labelLines = 2,
                            faded = face == null,
                            overlay = {
                                PackBadge(
                                    missing = missingOf(anime),
                                    install = installs[anime.id],
                                    onClick = {
                                        PackInstaller.install(
                                            context,
                                            anime,
                                            PackLibrary.removedIn(anime),
                                        )
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                )
                            },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            val entry = CoverStyle.forAnime(open)
            val gone = remember(removed, open) { PackLibrary.removedIn(open) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .playClickable(onClick = { openAnime = null })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Back, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(open.label),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.special_covers_gallery),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (gone.isNotEmpty()) {
                    PackBadge(
                        missing = gone.size,
                        install = installs[open.id],
                        onClick = { PackInstaller.install(context, open, gone) },
                    )
                }
            }
            open.posters.indices.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { index ->
                        if (PackLibrary.isRemoved(open, index)) {
                            PackDeletedPoster(
                                anime = open,
                                poster = index,
                                label = "${index + 1}",
                                install = installs[open.id],
                                onClick = { PackInstaller.install(context, open, listOf(index)) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            CoverCell(
                                label = "${index + 1}",
                                style = entry,
                                poster = index,
                                photo = null,
                                selected = !custom && entry == style && poster == index,
                                onClick = { onStyle(entry, index) },
                                onLongClick = { deleting = open to setOf(index) },
                                onLongClickLabel = hold,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    deleting?.let { (anime, initial) ->
        DeletePostersDialog(
            anime = anime,
            initial = initial,
            onDismiss = { deleting = null },
            onDelete = { posters ->
                PackLibrary.remove(context, anime, posters)
                deleting = null
            },
        )
    }
    if (deletePhoto) {
        ConfirmDeleteDialog(
            title = R.string.special_photo_delete_title,
            body = R.string.special_cover_photo_delete_body,
            confirm = R.string.special_photo_delete,
            onDismiss = { deletePhoto = false },
            onConfirm = {
                PackLibrary.deleteCoverPhoto(context)
                deletePhoto = false
            },
        )
    }
}

/** An empty cover slot: the same tile the covers use, waiting for a picture. */
@Composable
private fun CoverSlot(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph = Glyph.Person,
) {
    Column(
        modifier.playClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph,
                size = 22.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CoverCell(
    label: String,
    style: CoverStyle,
    photo: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    poster: Int = 0,
    labelLines: Int = 1,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    faded: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Column(
        modifier.playClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClickLabel,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (selected) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            CoverArt(
                style = style,
                photo = photo,
                poster = poster,
                artPx = 320,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (faded) Modifier.alpha(0.24f) else Modifier),
            )
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlyphIcon(
                            Glyph.Tick,
                            size = 13.dp,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            overlay()
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = labelLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * One door: a glyph, what is behind it, and how much is waiting there.
 *
 * The count is silent at zero on purpose. "0 updates" is a fact about a sweep, not an invitation, and
 * a row that prints it reads as a warning that something did not happen.
 */
@Composable
private fun HubRow(
    glyph: Glyph,
    title: String,
    note: String,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().playClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(40.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlyphIcon(
                    glyph,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RowCount(count)
        Spacer(Modifier.width(6.dp))
        GlyphIcon(
            Glyph.ChevronRight,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}
