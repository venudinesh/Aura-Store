package dev.aurastore.store.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.aurastore.store.theme.EmphasizedAccelerateEasing
import dev.aurastore.store.theme.EmphasizedDecelerateEasing
import dev.aurastore.store.theme.EmphasizedEasing
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardAccelerateEasing

/**
 * The two pieces of motion the framework cannot decide for itself.
 *
 * First, the page transitions: the shell routes with saved state rather than a navigation graph, so
 * nothing knew which way a page should travel until [playScreenTransform] read the direction out of the
 * two [Screen]s and built Play's slide. Second, the shared app icon: the card you tap becomes the
 * header of the page it opens, which is the one animation that makes a storefront read as a single
 * surface instead of two screens pasted together.
 *
 * Both reach their components through composition locals, and both are optional — a composable outside
 * a `SharedTransitionLayout`, or a preview, simply gets no modifier. That is what keeps [sharedIcon]
 * safe to call from every row in the app.
 */

/** Set by the shell's `SharedTransitionLayout`, one level above the animated routes. */
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The scope of the route currently animating, which is what a shared element flies inside. */
val LocalRouteScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * The icon that morphs between a list row and the page it opens.
 *
 * Only the icon is shared. The title beside it goes from a row's small type to a page's display type,
 * and morphing bounds between two type sizes stretches the glyphs on the way; Play crossfades the
 * title instead, which the detail page's own entrance does here.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedIcon(postId: Long): Modifier {
    val transition = LocalSharedScope.current
    val route = LocalRouteScope.current
    if (postId <= 0 || transition == null || route == null) return this
    return with(transition) {
        // The flight gets its own spec rather than the library's default, because the card opening into
        // its page is the one movement in the app that has to read as one object travelling a distance.
        this@sharedIcon.sharedElement(
            rememberSharedContentState("icon-$postId"),
            route,
            boundsTransform = { _, _ -> PlayMotion.spec(PlayMotion.SCREEN, EmphasizedDecelerateEasing) },
        )
    }
}

/** A press that takes the control down with the finger, the way Play's pills do. */
@Composable
fun Modifier.playPressed(source: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = PlayMotion.spec(PlayMotion.MICRO, EmphasizedEasing),
        label = "play-press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** [clickable] plus [playPressed] over one interaction source, so a call site stays one line. */
@Composable
fun Modifier.playClickable(onClick: () -> Unit, pressedScale: Float = 0.97f): Modifier {
    val source = remember { MutableInteractionSource() }
    return playPressed(source, pressedScale)
        .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
}

/**
 * Content arriving rather than appearing, staggered by [index].
 *
 * Keyed on [generation] so it runs once per load: a row that replayed its entrance every time the list
 * recycled it would animate forever while the user scrolled. That is why a feed's rails pass the
 * ViewModel's own load counter as the generation and their position as the index.
 */
@Composable
fun Modifier.playEnter(generation: Any? = Unit, index: Int = 0, rise: Float = 12f): Modifier {
    if (!PlayMotion.enabled) return this
    val delay = staggerDelay(index)
    val visible = remember(generation) { mutableStateOf(false) }
    LaunchedEffect(generation) { visible.value = true }
    val progress by animateFloatAsState(
        targetValue = if (visible.value) 1f else 0f,
        animationSpec = PlayMotion.spec(PlayMotion.MEDIUM, delay, EmphasizedDecelerateEasing),
        label = "play-enter",
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * rise.dp.toPx()
    }
}

/**
 * A page entering while the one behind it leaves, in whichever direction the routes moved.
 *
 * The entering page only ever travels a third of the screen and the leaving one a fraction of that: a
 * full-width push is what a pager does, while Play's tabs and detail pages overlap in the move, which
 * is what makes the second page feel *placed on* the first rather than swapped for it.
 */
fun AnimatedContentTransitionScope<Screen>.playScreenTransform(): ContentTransform {
    // Two tabs are not a push. During a swipe the page has already travelled with the finger, and a
    // full-width slide on top of that read as two animations for one gesture; Play's own tab change
    // is a crossfade with a short sideways drift.
    val from = initialState as? Screen.Feed
    val to = targetState as? Screen.Feed
    if (from != null && to != null) {
        val forward = to.index > from.index
        val enter = fadeIn(spec(PlayMotion.SHORT, EmphasizedEasing)) +
            slideInHorizontally(spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing)) {
                if (forward) it / 12 else -it / 12
            }
        val exit = fadeOut(spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
            slideOutHorizontally(spec(PlayMotion.MEDIUM, EmphasizedAccelerateEasing)) {
                if (forward) -it / 24 else it / 24
            }
        return ContentTransform(enter, exit, targetContentZIndex = 0f)
    }
    val move = moveOf(initialState, targetState)
    val zIndex = targetState.depth().toFloat()
    val enter: EnterTransition
    val exit: ExitTransition
    when (move) {
        Move.PUSH, Move.STAY -> {
            enter = fadeIn(spec(PlayMotion.SCREEN, PlayMotion.SHORT, EmphasizedEasing)) +
                slideInHorizontally(spec(PlayMotion.SCREEN, EmphasizedDecelerateEasing)) { it / 3 }
            exit = fadeOut(spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                slideOutHorizontally(spec(PlayMotion.SCREEN_EXIT, EmphasizedAccelerateEasing)) { -it / 8 }
        }

        Move.POP -> {
            enter = fadeIn(spec(PlayMotion.LONG, EmphasizedDecelerateEasing)) +
                slideInHorizontally(spec(PlayMotion.LONG, EmphasizedDecelerateEasing)) { -it / 8 }
            exit = fadeOut(spec(PlayMotion.MEDIUM, StandardAccelerateEasing)) +
                slideOutHorizontally(spec(PlayMotion.LONG, EmphasizedAccelerateEasing)) { it / 3 }
        }

        Move.SLIDE_FORWARD -> {
            enter = fadeIn(spec(PlayMotion.MEDIUM, PlayMotion.SHORT, EmphasizedEasing)) +
                slideInHorizontally(spec(PlayMotion.LONG, EmphasizedDecelerateEasing)) { it / 4 }
            exit = fadeOut(spec(PlayMotion.MEDIUM, StandardAccelerateEasing)) +
                slideOutHorizontally(spec(PlayMotion.LONG, EmphasizedAccelerateEasing)) { -it / 4 }
        }

        Move.SLIDE_BACK -> {
            enter = fadeIn(spec(PlayMotion.MEDIUM, PlayMotion.SHORT, EmphasizedEasing)) +
                slideInHorizontally(spec(PlayMotion.LONG, EmphasizedDecelerateEasing)) { -it / 4 }
            exit = fadeOut(spec(PlayMotion.MEDIUM, StandardAccelerateEasing)) +
                slideOutHorizontally(spec(PlayMotion.LONG, EmphasizedAccelerateEasing)) { it / 4 }
        }
    }
    return ContentTransform(enter, exit, targetContentZIndex = zIndex)
}

private fun <T> spec(baseMs: Int, easing: Easing): FiniteAnimationSpec<T> =
    PlayMotion.spec(baseMs, 0, easing)

private fun <T> spec(baseMs: Int, baseDelayMs: Int, easing: Easing): FiniteAnimationSpec<T> =
    PlayMotion.spec(baseMs, baseDelayMs, easing)

/**
 * The breathing of a skeleton, which is what [PlayMotion] calls a placeholder that has not been
 * replaced yet.
 *
 * A still grey block reads as a screen that stopped; Play's placeholders keep moving, so the wait looks
 * like work. Motion off means the block simply sits at full strength.
 *
 * **One clock for the whole screen.** `rememberInfiniteTransition` allocates a transition and a
 * running animation per call site, and a loading feed calls this once per skeleton row — a screen of
 * placeholders was several independent clocks all ticking to the same beat, each one invalidating its
 * own reader every frame. The transition is hoisted to a composition local so every skeleton on the
 * screen shares one animation, and a screen with no skeleton in it never starts one at all.
 */
private val LocalSkeletonPulse = staticCompositionLocalOf<Float?> { null }

/**
 * Provides the one skeleton clock for everything composed inside [content].
 *
 * Wrapping the loading state rather than the whole app is deliberate: the transition only exists
 * while something is actually loading, so a settled screen carries no animation at all.
 */
@Composable
fun SkeletonPulseProvider(content: @Composable () -> Unit) {
    if (!PlayMotion.enabled) {
        CompositionLocalProvider(LocalSkeletonPulse provides 1f, content = content)
        return
    }
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PlayMotion.ms(PlayMotion.LONG), easing = EmphasizedEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-pulse",
    )
    CompositionLocalProvider(LocalSkeletonPulse provides pulse, content = content)
}

/**
 * The current skeleton alpha, read from the shared clock when one is provided.
 *
 * The fallback keeps this callable anywhere — a preview, or a screen that forgot the provider — at the
 * cost of one clock, which is what every call site used to pay.
 */
@Composable
fun skeletonPulse(): Float {
    LocalSkeletonPulse.current?.let { return it }
    if (!PlayMotion.enabled) return 1f
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PlayMotion.ms(PlayMotion.LONG), easing = EmphasizedEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-pulse",
    ).value
}

/**
 * Retapping the tab you are on scrolls its list back up, smoothly.
 *
 * A jump is what a client without motion does; Play eases it, so a list two hundred rows down reads as
 * going somewhere rather than the page breaking.
 */
@Composable
fun ScrollToTopOnRetap(trigger: Int, listState: LazyListState) {
    val list by rememberUpdatedState(listState)
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        if (PlayMotion.enabled) list.animateScrollToItem(0) else list.scrollToItem(0)
    }
}
