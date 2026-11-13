package dev.aurastore.store

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.data.AppState
import dev.aurastore.store.data.NavOrder
import dev.aurastore.store.data.Settings
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.install.Installer
import dev.aurastore.store.install.UpdateScanner
import dev.aurastore.store.theme.EmphasizedAccelerateEasing
import dev.aurastore.store.theme.EmphasizedDecelerateEasing
import dev.aurastore.store.theme.EmphasizedEasing
import dev.aurastore.store.theme.Ink50
import dev.aurastore.store.theme.AuraStoreTheme
import dev.aurastore.store.theme.NightBase
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardAccelerateEasing
import dev.aurastore.store.data.glassEngineRuns
import dev.aurastore.store.theme.glassReadsBackdrop
import dev.aurastore.store.theme.resolvesToDark
import dev.aurastore.store.theme.systemIsDark
import dev.aurastore.store.ui.FloatingBar
import dev.aurastore.store.ui.BarTab
import dev.aurastore.store.ui.BarIcon
import dev.aurastore.store.ui.BarTabRow
import dev.aurastore.store.ui.FloatingBarSurface
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.InfoRow
import dev.aurastore.store.ui.LocalRouteScope
import dev.aurastore.store.ui.NAV_TABS
import dev.aurastore.store.ui.LocalSharedScope
import dev.aurastore.store.ui.Route
import dev.aurastore.store.ui.rememberTabsPillWidth
import dev.aurastore.store.ui.decodeRoute
import dev.aurastore.store.ui.encodeRoute
import dev.aurastore.store.ui.stacksOn
import dev.aurastore.store.ui.toScreen
import dev.aurastore.store.ui.Screen
import dev.aurastore.store.ui.TabSwipe
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.playScreenTransform
import dev.aurastore.store.ui.category.CategoryScreen
import dev.aurastore.store.ui.detail.DetailScreen
import dev.aurastore.store.ui.downloads.DownloadDock
import dev.aurastore.store.ui.downloads.DownloadDetailScreen
import dev.aurastore.store.ui.downloads.dockCandidate
import dev.aurastore.store.ui.feed.FeedKind
import dev.aurastore.store.ui.feed.FeedScreen
import dev.aurastore.store.ui.search.SearchScreen
import dev.aurastore.store.ui.source.SourceScreen
import dev.aurastore.store.ui.source.SourcePicker
import dev.aurastore.store.ui.source.SourcePickerSheet
import dev.aurastore.store.ui.settings.SettingsScreen
import dev.aurastore.store.ui.you.YouScreen

class MainActivity : ComponentActivity() {

    private lateinit var notificationPermission: androidx.activity.result.ActivityResultLauncher<String>

    /** A download id handed in by the queue's notification; the shell consumes it on composition. */
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The starting window is the system's own paint, taken from the theme's night qualifier, which
        // is the phone's scheme. This is the same paint one step later, from the scheme the app chose.
        // The colour is assigned rather than passed to the constructor because lint's UseKtx rule
        // suggests `Int.toDrawable`, which no version of androidx.core actually ships.
        window.setBackgroundDrawable(
            ColorDrawable().apply { color = (if (themeIsDark()) NightBase else Ink50).toArgb() },
        )

        notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        DownloadEngine.ensureChannel(this)
        AppState.refresh(this)
        askNotificationPermission()
        deepLink.value = intent?.getStringExtra(EXTRA_DOWNLOAD)

        setContent {
            // Collected rather than read once, so moving the Appearance row repaints the whole store
            // without a restart — the same reason the theme is a composable parameter and not a field.
            val themeMode by Settings.themeMode.collectAsState()
            val palette by Settings.palette.collectAsState()
            val seed by Settings.paletteSeed.collectAsState()
            val dark = themeMode.resolvesToDark(isSystemInDarkTheme())
            // The bars sit over our own surface, so their icons follow the scheme the app resolved to.
            // enableEdgeToEdge()'s default follows the phone instead, which leaves dark icons on a dark
            // bar as soon as anyone picks Dark while the system is light.
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = systemBarsFor(dark),
                    navigationBarStyle = systemBarsFor(dark),
                )
            }
            AuraStoreTheme(themeMode = themeMode, palette = palette, seed = seed) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StoreApp(deepLink)
                }
            }
        }
    }

    /** The scheme this activity is about to draw, read while no composition exists yet. */
    private fun themeIsDark() = Settings.themeMode.value.resolvesToDark(systemIsDark())

    /** Transparent scrims; only the icon colour is ours to choose, and it answers the surface below it. */
    private fun systemBarsFor(dark: Boolean) = if (dark) {
        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop: a notification tapped while the app is alive arrives here rather than restarting it.
        setIntent(intent)
        intent.getStringExtra(EXTRA_DOWNLOAD)?.let { deepLink.value = it }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted "install unknown apps" or removed an app while we were away.
        AppState.refresh(this)
        // If the permission was just granted, the install that was waiting for it continues here,
        // and an install finished through the system sheet is marked installed.
        DownloadEngine.resumePendingInstall(this)
        DownloadEngine.reconcileInstalls(this)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        // One ask per install. After a refusal the dialog would otherwise cover the first screen of every
        // launch, and the remaining affordance is Settings' own notification row.
        if (Settings.notificationsAsked.value) return
        Settings.markNotificationsAsked(this)
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        /** A download queue row id, carried by the notification's tap intent. */
        const val EXTRA_DOWNLOAD = "extra_download_id"
    }
}

/**
 * The storefront shell, shaped like the Play Store: a brand bar with the download badge and the
 * account avatar, five bottom tabs whose selected state is a filled pill, and the detail page as a
 * full-screen route on top of whichever tab is showing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun StoreApp(deepLink: MutableState<String?> = mutableStateOf(null)) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // The pushed pages, bottom first. Everything that used to be its own flag lives here now, so back
    // is a pop and "where was I" is the whole list rather than whichever flag was set last.
    var stack by rememberSaveable { mutableStateOf(listOf<String>()) }
    // The tabs actually visited, most recent last. Back walks this before it considers leaving, which
    // is what stops a user who has been moving between tabs from hitting the exit gesture after one
    // step. Bounded because this is orientation history, not a journal.
    var tabBack by rememberSaveable { mutableStateOf(listOf<Int>()) }
    // The query the source screen was opened with. It belongs to the visit, not to the history, so it
    // is kept beside the stack rather than inside it.
    var sourceQuery by rememberSaveable { mutableStateOf("") }
    var libraryRefresh by remember { mutableIntStateOf(0) }
    var profileOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }

    val routes = remember(stack) { stack.mapNotNull(::decodeRoute) }
    val top = routes.lastOrNull()

    /** Pushes [route], collapsing a repeat of the page already showing. */
    fun push(route: Route) {
        if (!stacksOn(top, route)) return
        stack = stack + encodeRoute(route)
    }

    /** Pops one page. The stack is the only thing back touches. */
    fun pop() {
        if (stack.isNotEmpty()) stack = stack.dropLast(1)
    }

    /** Replaces the whole stack with one page, for a jump that is not a walk (a notification, a tab). */
    fun jumpTo(route: Route?) {
        stack = if (route == null) emptyList() else listOf(encodeRoute(route))
    }

    /**
     * Switches tabs as a move rather than a jump, so Back can come back here.
     *
     * The old handler sent every tab to Games and then straight to the exit prompt: two backs from
     * anywhere in the app closed it. Remembering the tab that was left makes the first back a step to
     * where the user just was, the way every tabbed Android app behaves.
     */
    fun visit(next: Int) {
        if (next == tab) return
        tabBack = (tabBack + tab).takeLast(8)
        tab = next
    }

    val glass by Settings.glass.collectAsStateWithLifecycle()

    val navSlide by Settings.navSlide.collectAsStateWithLifecycle()
    val navIconMotion by Settings.navIconMotion.collectAsStateWithLifecycle()
    val navOrder by Settings.navOrder.collectAsStateWithLifecycle()
    // The strip in the order it was dragged into. Every reader — the bar, the swipe, the selected
    // pill — works from this one permutation, so they cannot disagree about which tab is where.
    val orderedTabs = remember(navOrder) {
        navOrder.map { screen -> BarTab(NAV_TABS[screen].label, screen) }
    }
    // Hidden on the source picker, which is a chooser that takes the whole screen. Settings shows the
    // bar: it is where the four glass sliders live, and a visual control with no visible subject
    // is not a preview. Every other pushed page hides it too: a detail or category page is a layer
    // over a tab, and a floating bar sitting on top of its content read as clutter rather than as
    // navigation. Back is that page's way out.
    //
    // Also hidden while the keyboard is up. The bar is a window of its own now, and measured on the
    // handset that window sits *above* the keyboard rather than under it: the pill covered the keys'
    // bottom row and, being a window, took their taps. A bar that eats the space bar is worse than no
    // bar for the two seconds someone is typing.
    val showNavBar = (stack.isEmpty() || top is Route.Settings) &&
        top !is Route.Source && !WindowInsets.isImeVisible
    var catalogueRefresh by remember { mutableIntStateOf(0) }
    // Retapping the tab already showing scrolls its list up, the way Play's does.
    var scrollTop by remember { mutableIntStateOf(0) }
    val downloads by DownloadEngine.items.collectAsStateWithLifecycle()
    val activeDownloads = downloads.count { it.isActive }
    // The one row the floating panel follows, and the row the user has closed it for. Hiding is per
    // row rather than a switch: the next download the user starts is news and must reopen the panel,
    // while the one they put away must stay away until it finishes.
    var dockHiddenId by rememberSaveable { mutableStateOf<String?>(null) }
    val dockItem = remember(downloads) { dockCandidate(downloads) }?.takeIf { it.id != dockHiddenId }
    // The panel is a floating surface like the bar, and it is hidden by the same modals the bar is
    // hidden by: a dialog or the profile sheet owns the screen, and the keyboard sits under a child
    // window. Stated once so the room a page reserves and the panel drawn in it cannot disagree.
    val dockShown = dockItem != null && !profileOpen && !aboutOpen && !WindowInsets.isImeVisible
    // Measured rather than assumed: the panel grows with the phone's text size, and the room the lists
    // reserve under it has to grow by exactly the same amount.
    var dockHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // Play puts the pending-update count on the account icon; here it belongs on the tab that lists them.
    val pendingUpdates by UpdateScanner.pending.collectAsStateWithLifecycle()
    // Fully expanded from the first frame. The sheet's content is taller than the phone at a 2.0×
    // text size, and the half-expanded state it opened in cut the fifth item mid-sentence with no
    // sign that the list continued; opened at full height the cut is at the screen's own edge and
    // the list scrolls. At the default size the sheet is shorter than the screen and looks the same.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // The stack is the routing; this is what the shell animates between two of them with, because a
    // transition needs to know both ends of the move, not just the one it is heading for.
    val screen: Screen = top?.toScreen(sourceQuery) ?: Screen.Feed(tab)

    /**
     * Back, in the order a phone means it.
     *
     * A pushed page pops. A tab the user came from is a real place, so back walks the tabs already
     * visited before it reaches Games. Only at the root does back ask to leave, and it asks with a
     * dialog: nothing a gesture does can close this app without a deliberate answer.
     */
    // Whether the exit confirmation is up. Back never closes this app on its own any more: the old
    // handler closed it on a second press inside a four-second window, and on the device that read
    // as "two swipes and it is gone" — the toast was easy to miss and the window long enough that an
    // ordinary double-swipe landed in it. A dialog states what is about to happen and needs one
    // deliberate tap, so no gesture can take the app away by accident.
    var exitConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        when {
            // A modal owns the screen; back closes it before it touches the page underneath.
            profileOpen -> profileOpen = false
            aboutOpen -> aboutOpen = false
            // The confirmation is a modal too, and back is how it is dismissed.
            exitConfirm -> exitConfirm = false
            stack.isNotEmpty() -> pop()
            // Where the user came from is a real place; only an empty history falls back to Games.
            tabBack.isNotEmpty() -> {
                tab = tabBack.last()
                tabBack = tabBack.dropLast(1)
            }
            tab != 0 -> tab = 0
            else -> exitConfirm = true
        }
    }
    // Tapping the queue's notification names the row it was built from; the shell replaces the stack
    // with that page and clears the request so a rotation does not reopen it.
    LaunchedEffect(deepLink.value) {
        deepLink.value?.let {
            jumpTo(Route.Download(it))
            deepLink.value = null
        }
    }

    Scaffold(
        topBar = {
            // The brand bar belongs to the tabs. A pushed page carries its own top row, with the back
            // control that made it a page in the first place.
            if (stack.isEmpty()) {
                BrandBar(
                    activeDownloads = activeDownloads,
                    onDownloads = {
                        visit(4)
                        libraryRefresh++
                    },
                    onProfile = { profileOpen = true },
                )
            }
        },
        bottomBar = {
            // Empty on purpose: a bar in this slot pushes the list up off the glass. The bar lives in
            // the content area instead, so the page runs underneath it.
        },
    ) { padding ->
        // One `SharedTransitionLayout` over one `AnimatedContent` is what lets an icon leave a row and
        // arrive in a header: the layout gives the routes a common coordinate space, and the animated
        // content gives each of them the scope the shared element flies inside.
        // The inset is provided around the screens, not around the bar. A composition local resolves
        // where it is read, so wrapping only the bar left every list seeing the default 0 dp and the
        // last row of a page could sit under it.
        CompositionLocalProvider(
            // The bar grows with the phone's text size, and the room a list reserves has to grow with it —
            // from the same function, so a large font cannot leave the last row under the sheet.
            LocalNavBarInset provides (
                // One sum, in one place: everything floating over the content, so a screen that pads
                // by this clears both surfaces. `FloatingBar.reserve` is the definition; it counts
                // the bar and the panel only while each is actually up.
                FloatingBar.reserve(
                    fontScale = density.fontScale,
                    barShown = showNavBar,
                    floating = glass.floating,
                    offset = glass.offset,
                    dockHeight = if (dockShown) with(density) { dockHeightPx.toDp() } else null,
                    // Both up: the panel and the sheet share an edge, so the page reserves no gap.
                    dockJoined = dockShown && showNavBar,
                )
            ),
        ) {
            val pageBg = MaterialTheme.colorScheme.background
            val needsPageLayer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                glassReadsBackdrop(
                    liquid = glass.floating,
                    blur = glass.blur,
                    refraction = glass.refraction,
                    chromatic = glass.chromatic,
                    engine = glassEngineRuns(glass.engine),
                )
            val pageLayer = if (needsPageLayer) rememberGraphicsLayer() else null
            Box(Modifier.fillMaxSize().padding(padding)) {
                SharedTransitionLayout(
                    if (pageLayer != null) {
                        Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                pageLayer.record {
                                    drawRect(pageBg)
                                    this@drawWithContent.drawContent()
                                }
                                drawLayer(pageLayer)
                            }
                    } else {
                        Modifier.fillMaxSize()
                    },
                ) {
                val shared = this
                // Swiping between the five tabs. The page follows the finger, and either a fifth of
                // the width or a flick commits the move; the shell's own slide then plays, so a
                // swipe lands on the same transition a tap does rather than a second, different one.
                // Horizontal scrollers inside a page — the featured carousel, the chip rails — are
                // deeper in the tree and take the drag first, so this detector cancels over them.
                // The drag is a plain float, not an `Animatable`: snapping an Animatable from every
                // pointer event launched a coroutine per frame, so the page trailed the finger by a
                // frame or two — the sticky part of the gesture. A float set synchronously tracks the
                // finger exactly, and the same float is the target of the settle animation, which is
                // the only part that has to be animated at all.
                var swipeTarget by remember { mutableFloatStateOf(0f) }
                var swiping by remember { mutableStateOf(false) }
                val swipeX by animateFloatAsState(
                    targetValue = swipeTarget,
                    animationSpec = if (swiping) snap() else PlayMotion.spec(PlayMotion.MEDIUM),
                    label = "tab-swipe",
                )
                // The content's own width, measured rather than read from the display: the screen is
                // not the page in a split window, and lint is right to say so.
                var pageWidth by remember { mutableFloatStateOf(0f) }
                val screenWidth = pageWidth
                // Only a tab is swipeable. A detail page, settings or the source picker is a route
                // laid over the bar and leaves by its own back gesture.
                val swipeable = screen is Screen.Feed && showNavBar && !profileOpen && !aboutOpen
                Box(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { pageWidth = it.width.toFloat() }
                        .graphicsLayer { translationX = swipeX }
                        .pointerInput(swipeable, tab, pageWidth) {
                            if (!swipeable) return@pointerInput
                            val tracker = VelocityTracker()
                            var travelled = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    travelled = 0f
                                    swipeTarget = 0f
                                    swiping = true
                                    tracker.resetTracking()
                                },
                                onDragCancel = {
                                    swiping = false
                                    swipeTarget = 0f
                                },
                                onDragEnd = {
                                    val position = NavOrder.positionOf(navOrder, tab)
                                    val target = TabSwipe.target(
                                        tab = position,
                                        lastIndex = navOrder.lastIndex,
                                        dx = swipeTarget,
                                        velocityX = tracker.calculateVelocity().x,
                                        widthPx = screenWidth,
                                    )
                                    // The gesture speaks in positions on the strip; the shell opens
                                    // screens. A reordered bar therefore swipes in its own order,
                                    // which is the only order the user can see.
                                    val next = navOrder.getOrElse(target) { tab }
                                    if (next != tab) {
                                        // Far enough to commit: hand the movement to the tab
                                        // transition instead of springing back first, which was two
                                        // animations for one gesture. `swiping` stays true, so the
                                        // offset resets in this frame rather than animating home.
                                        swipeTarget = 0f
                                        visit(next)
                                        if (next == 4) libraryRefresh++
                                    } else {
                                        swiping = false
                                        swipeTarget = 0f
                                    }
                                },
                                onHorizontalDrag = { change, amount ->
                                    change.consume()
                                    travelled += amount
                                    // The ends resist instead of refusing: the page still moves a
                                    // little under the finger, which is what says "this is the edge".
                                    val position = NavOrder.positionOf(navOrder, tab)
                                    val atEdge = (position == 0 && travelled > 0) ||
                                        (position == navOrder.lastIndex && travelled < 0)
                                    val step = amount * if (atEdge) 0.18f else 1f
                                    swipeTarget = (swipeTarget + step).coerceIn(
                                        -screenWidth * 0.4f,
                                        screenWidth * 0.4f,
                                    )
                                },
                            )
                        },
                ) {
                AnimatedContent(
                    targetState = screen,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { playScreenTransform() },
                    label = "store-route",
                ) { route ->
                    CompositionLocalProvider(
                        LocalSharedScope provides shared,
                        LocalRouteScope provides this,
                    ) {
                        when (route) {
                            is Screen.Source -> SourceScreen(
                                initialSource = route.source,
                                initialQuery = route.query,
                                initialCategory = route.category,
                                onBack = ::pop,
                                onOpenCataloguePost = { postId ->
                                    // A source page is a place you were; the app you open from it goes on
                                    // top. Back returns to the source search rather than past it.
                                    push(Route.Detail(postId))
                                },
                            )

                            is Screen.Detail -> DetailScreen(
                                postId = route.postId,
                                onBack = ::pop,
                                onOpenPost = { push(Route.Detail(it)) },
                            )

                            is Screen.Download -> DownloadDetailScreen(
                                downloadId = route.id,
                                onBack = ::pop,
                                onOpenPost = { push(Route.Detail(it)) },
                            )

                            is Screen.Category -> CategoryScreen(
                                categoryId = route.id,
                                onBack = ::pop,
                                onOpenPost = { push(Route.Detail(it)) },
                            )

                            Screen.Settings -> SettingsScreen(
                                onBack = ::pop,
                                onRefreshCatalogue = {
                                    catalogueRefresh++
                                    Settings.markSynced(context)
                                },
                            )

                            is Screen.Feed -> when (route.index) {
                                0 -> FeedScreen(
                                    FeedKind.GAMES,
                                    onOpenPost = { push(Route.Detail(it)) },
                                    refreshKey = catalogueRefresh,
                                    scrollTopKey = scrollTop,
                                )

                                1 -> FeedScreen(
                                    FeedKind.APPS,
                                    onOpenPost = { push(Route.Detail(it)) },
                                    refreshKey = catalogueRefresh,
                                    scrollTopKey = scrollTop,
                                )

                                2 -> SearchScreen(
                                    onOpenPost = { push(Route.Detail(it)) },
                                    onOpenSource = { id, query ->
                                        sourceQuery = query
                                        push(Route.Source(id))
                                    },
                                )

                                3 -> FeedScreen(
                                    FeedKind.BROWSE,
                                    onOpenPost = { push(Route.Detail(it)) },
                                    onOpenCategory = { push(Route.Category(it)) },
                                    // A source's own page, opened from the Browse tab: the source's
                                    // newest apps and the categories it groups them under.
                                    onOpenSource = { id, category -> push(Route.Source(id, category)) },
                                    refreshKey = catalogueRefresh,
                                    scrollTopKey = scrollTop,
                                )

                                else -> YouScreen(
                                    onOpenPost = { push(Route.Detail(it)) },
                                    onOpenDownload = { push(Route.Download(it)) },
                                    refreshKey = libraryRefresh,
                                    scrollTopKey = scrollTop,
                                )
                            }
                        }
                    }
                }
                }
            }

                // The bar and the download panel, in one window. Hidden on the two routes that take
                // the whole screen over it, exactly as the flush bar was — and behind a modal, because
                // its window draws above one. Measured on the handset: with the profile sheet open, a
                // tap at the You tab's fifth navigated the page underneath. A modal owns the screen, so
                // neither surface is drawn while it is up; the inset above is deliberately left alone
                // so the page behind does not reflow as it appears.
                //
                // The panel is a child of this surface rather than a node beside it: the two used to
                // be placed by separate arithmetic (a padding in the page, a window offset in the bar)
                // and met at the seam. As a column child its gap is layout, so the two cannot overlap.
                if (dockShown || (showNavBar && !profileOpen && !aboutOpen)) {
                    val dock = dockItem
                    FloatingBarSurface(
                        style = glass,
                        tint = MaterialTheme.colorScheme.surfaceContainer,
                        pageLayer = pageLayer,
                        barShown = showNavBar && !profileOpen && !aboutOpen,
                        // Measured from the five labels at this phone's text size, so the sheet is a
                        // pill on every screen instead of a fraction of one.
                        pillWidth = rememberTabsPillWidth(orderedTabs),
                        dockShown = dockShown,
                        dock = { seam ->
                            if (dock != null) {
                                DownloadDock(
                                    item = dock,
                                    // Everything else the panel is not showing, so a second download
                                    // never looks like it is not running.
                                    more = downloads.count {
                                        it.id != dock.id &&
                                            (it.isActive || it.state == DownloadState.PAUSED ||
                                                it.state == DownloadState.READY ||
                                                it.state == DownloadState.NEEDS_PERMISSION ||
                                                it.state == DownloadState.AWAITING_CONFIRMATION)
                                    },
                                    onOpen = { push(Route.Download(dock.id)) },
                                    onToggle = { DownloadEngine.toggle(context, dock.id) },
                                    onInstall = {
                                        when {
                                            dock.state == DownloadState.NEEDS_PERMISSION ->
                                                Installer.openUnknownSourcesSettings(context)
                                            dock.uninstallFirst ->
                                                dock.apkPackage?.let { Installer.uninstall(context, it) }
                                            else ->
                                                DownloadEngine.install(context, dock.id)
                                        }
                                    },
                                    onDismiss = { dockHiddenId = dock.id },
                                    seam = seam,
                                    // Measured here, in the window that draws it, and fed to the
                                    // page as its reserve — the panel's height is the page's business.
                                    modifier = Modifier.onSizeChanged { dockHeightPx = it.height },
                                )
                            }
                        },
                    ) {
                        BarTabRow(
                            tabs = orderedTabs,
                            slide = navSlide,
                            iconMotion = navIconMotion,
                            // A route over the bar owns the screen; the bar is hidden then, but the
                            // selected tab must not claim to be showing its page either. The pill
                            // travels by *position*, because that is what the row knows.
                            selectedIndex = if (stack.isEmpty()) NavOrder.positionOf(navOrder, tab) else -1,
                            icon = { position, fill ->
                                val screen = orderedTabs[position].screen
                                TabIcon(
                                    NAV_TABS[screen].glyph,
                                    badge = if (screen == 4) pendingUpdates else 0,
                                    fill = fill,
                                )
                            },
                            onSelect = { position ->
                                val screen = orderedTabs[position].screen
                                // The bar is hidden while a page is over it, so a tap here on the tab
                                // already showing is Play's "go back to the top". Switching tabs
                                // clears the stack rather than unwinding it: a tab is a floor, and
                                // pressing one is a decision to be there, not a step backwards.
                                val retapped = tab == screen && stack.isEmpty()
                                stack = emptyList()
                                visit(screen)
                                if (screen == 4) libraryRefresh++
                                if (retapped) scrollTop++
                            },
                        )
                    }
                }
            }
        }
    }

    if (profileOpen) {
        ModalBottomSheet(
            onDismissRequest = { profileOpen = false },
            sheetState = sheetState,
            // Play's account sheet sits a step above the page behind it.
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            ProfileSheet(
                onManageApps = {
                    profileOpen = false
                    visit(4)
                    libraryRefresh++
                },
                onDownloads = {
                    profileOpen = false
                    visit(4)
                    libraryRefresh++
                },
                onAbout = {
                    profileOpen = false
                    aboutOpen = true
                },
                onInstallSettings = {
                    profileOpen = false
                    Installer.openUnknownSourcesSettings(context)
                },
                onSettings = {
                    profileOpen = false
                    push(Route.Settings)
                },
            )
        }
    }

    if (aboutOpen) {
        AboutDialog(onDismiss = { aboutOpen = false })
    }

    // The sheet that asks which source a download comes from. It lives with the other modals so that
    // any screen can open it: an app page, a source page, the Browse tab, the search results.
    val pickerRequest by SourcePicker.request.collectAsStateWithLifecycle()
    if (pickerRequest != null) {
        SourcePickerSheet(onDismiss = { SourcePicker.close() })
    }

    // Back at the root asks rather than acts. Both answers are one tap, and the dialog says what
    // leaving costs: a queued download keeps running, because it is a foreground service.
    if (exitConfirm) {
        AlertDialog(
            onDismissRequest = { exitConfirm = false },
            title = { Text(stringResource(R.string.exit_title)) },
            text = { Text(stringResource(R.string.exit_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        exitConfirm = false
                        context.findActivity()?.finish()
                    },
                ) { Text(stringResource(R.string.exit_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { exitConfirm = false }) {
                    Text(stringResource(R.string.exit_stay))
                }
            },
        )
    }
}

/** Play's top bar: brand mark and name on the left, activity icons and the avatar on the right. */
@Composable
private fun BrandBar(
    activeDownloads: Int,
    onDownloads: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Brand, size = 18.dp, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        BadgeButton(count = activeDownloads, onClick = onDownloads)
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(36.dp).clip(CircleShape).playClickable(onClick = onProfile),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlyphIcon(
                    Glyph.Person,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    label = stringResource(R.string.a11y_account),
                )
            }
        }
    }
}

@Composable
private fun BadgeButton(count: Int, onClick: () -> Unit) {
    Box(Modifier.size(40.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(36.dp).align(Alignment.Center).clip(CircleShape).playClickable(onClick = onClick),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlyphIcon(
                    Glyph.Download,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    // The quantity is not a format argument: without passing `count` twice the label
                    // reads "Downloads, %1$d active" out loud.
                    label = if (count > 0) pluralStringResource(R.plurals.a11y_downloads_count, count, count)
                    else stringResource(R.string.a11y_downloads),
                )
            }
        }
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.TopEnd),
            visible = count > 0,
            enter = fadeIn(PlayMotion.spec(PlayMotion.SHORT, EmphasizedEasing)) +
                scaleIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing), 0.6f),
            exit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                scaleOut(PlayMotion.spec(PlayMotion.SHORT, EmphasizedAccelerateEasing), 0.6f),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** A bottom-bar glyph, with Play's count bubble on the tab that owns the pending updates. */
@Composable
private fun TabIcon(glyph: Glyph, badge: Int = 0, fill: Float = 0f) {
    Box(Modifier.size(BarIcon.box), contentAlignment = Alignment.Center) {
        // The pill behind the glyph is what a solid gamepad's buttons are cut out of, so the bar's own
        // container colour is the cut-out rather than a guess at the surface underneath.
        GlyphIcon(
            glyph,
            size = BarIcon.glyph,
            fill = fill,
            cutout = MaterialTheme.colorScheme.primaryContainer,
            strokeWidth = BarIcon.strokeFor(fill),
        )
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.TopEnd),
            visible = badge > 0,
            enter = fadeIn(PlayMotion.spec(PlayMotion.SHORT, EmphasizedEasing)) +
                scaleIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing), 0.6f),
            exit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                scaleOut(PlayMotion.spec(PlayMotion.SHORT, EmphasizedAccelerateEasing), 0.6f),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (badge > 9) "9+" else badge.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSheet(
    onManageApps: () -> Unit,
    onDownloads: () -> Unit,
    onAbout: () -> Unit,
    onInstallSettings: () -> Unit,
    onSettings: () -> Unit,
) {
    // At a 2.0× text size the five items are taller than the phone, and a Column that simply
    // overflows is cut by the screen: measured on the Redmi, "Downloads" ended mid-sentence at the
    // bottom edge and *Settings* and *About* were below it and unreachable. Scrolling the content
    // inside the sheet lets the sheet end at the screen and every item be reached.
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.Person, size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column {
                Text(stringResource(R.string.account_guest), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.account_guest_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        SheetItem(Glyph.Update, R.string.sheet_manage_apps, R.string.sheet_manage_apps_note, onManageApps)
        SheetItem(Glyph.Download, R.string.sheet_downloads, R.string.sheet_downloads_note, onDownloads)
        SheetItem(
            Glyph.Shield,
            R.string.sheet_install_permission,
            R.string.sheet_install_permission_note,
            onInstallSettings,
        )
        SheetItem(Glyph.Update, R.string.sheet_settings, R.string.sheet_settings_note, onSettings)
        SheetItem(Glyph.Info, R.string.sheet_about, R.string.sheet_about_note, onAbout)
    }
}

@Composable
private fun SheetItem(glyph: Glyph, @StringRes title: Int, @StringRes subtitle: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .playClickable(onClick = onClick, pressedScale = 0.99f)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_close)) } },
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            // AlertDialog bounds its text slot but does not scroll it, so at a 2.0× text size the
            // third section (and half of the second) sat below the dialog's edge with no way to
            // reach it. Scrolling the column keeps every word reachable inside the same dialog.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                InfoRow(
                    Glyph.Shield,
                    stringResource(R.string.about_verified),
                    subtitle = stringResource(R.string.about_verified_body),
                )
                InfoRow(
                    Glyph.Info,
                    stringResource(R.string.about_cannot_prove),
                    subtitle = stringResource(R.string.about_cannot_prove_body),
                )
                InfoRow(
                    Glyph.Update,
                    stringResource(R.string.about_updates),
                    subtitle = stringResource(R.string.about_updates_body),
                )
                InfoRow(
                    Glyph.Library,
                    stringResource(R.string.about_servers),
                    subtitle = stringResource(R.string.about_servers_body),
                )
            }
        },
    )
}

/**
 * The activity a composition context belongs to, for the one action that is not a navigation.
 *
 * `LocalContext` is usually the activity already, but a themed wrapper or a preview is not, and
 * calling `finish()` on the wrong thing would be silent. Walking the wrappers is the same rule
 * `NavBar` uses to find the window.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
