package dev.aurastore.store.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.max

/**
 * The page behind the bar, read back from the activity window for the glass to blur.
 *
 * A bar drawn as a sibling of the page lives in the same surface a readback copies, so a copy of the
 * window would contain the bar instead of the page. The bar is in a popup — a surface of its own — so
 * the activity's copy still shows the page underneath; `BackdropReadTest` proved that on the handset by
 * putting a popup of a known colour over a band and reading the band back unchanged.
 *
 * This file is only used when **Blur intensity is above zero**. The shipped bar paints nothing: a popup
 * is transparent where it draws nothing, so at zero the page is not a copy at all — it is the page, at
 * its own sharpness. That is also why the sample is not a small thumbnail any more: the previous
 * version read 160 px and drew it back over the page, which is what made text under the bar look like a
 * blocky smear. At [BAR_BACKDROP_LONGEST_SIDE_PX] the sample is about half the panel's width, and the
 * blur is the render effect rather than an upscale.
 */

/** How long after the last draw the confirming read is taken. */
const val BAR_BACKDROP_SETTLE_MS = 100L

/**
 * The long side of a sample, in pixels.
 *
 * Half of a 1080 px panel, rounded. The previous 160 px was a sixth of the width and every letter under
 * the bar became a 7 px block; 640 is an eighth of a letter's stroke at modern text sizes and is only
 * ever drawn back under a blur of at least one dp. It also keeps the copy at 640 x 187 rather than
 * 1080 x 187, so a scroll's per-frame read stays in the millisecond range it was measured at.
 */
const val BAR_BACKDROP_LONGEST_SIDE_PX = 640

/**
 * The integer shrink that takes the bar's rect down to at most [BAR_BACKDROP_LONGEST_SIDE_PX].
 *
 * Integer rather than a scale factor because a bitmap has whole pixels, and a scale that divides the
 * rect exactly keeps the sample the same aspect as the rect it is drawn back into — which is what lets
 * the layer fill the sheet without cropping a row of the page off.
 */
fun backdropScale(width: Int, height: Int): Int =
    max(1, ceil(max(width, height).toFloat() / BAR_BACKDROP_LONGEST_SIDE_PX).toInt())

/**
 * The bar's rect inside the content area, in the content area's own pixels.
 *
 * The sampler and the sheet must agree about where the bar is, or the blurred band slides under a still
 * bar and nothing in the code looks wrong. [sidePx] is the empty space on *each* side, so a bar at 90 %
 * of the width passes 5 % here, not 10 %.
 */
fun barRectIn(
    area: IntRect,
    heightPx: Int,
    sidePx: Int,
    gapPx: Int,
    floating: Boolean,
): IntRect {
    val left = if (floating) area.left + sidePx else area.left
    val right = if (floating) area.right - sidePx else area.right
    val bottom = area.bottom - if (floating) gapPx else 0
    // Clamped to the room above this bottom edge, not to the area's height: a bar taller than the space
    // it has would otherwise be drawn with its top above the area it is supposed to sit in.
    val height = heightPx.coerceAtMost(max(0, bottom - area.top))
    return IntRect(left, bottom - height, right, bottom)
}

/**
 * Follows the page's draws and returns the newest sample of [rect] in [window].
 *
 * A failed read leaves the returned bitmap alone instead of clearing it, so one hiccup does not blink
 * the backdrop out. Reads are taken on the page's own draw rather than on a timer: a still page draws
 * nothing and needs no polling, a scroll draws every frame and gets the glass at the scroll's rate.
 */
@Composable
fun rememberBarBackdrop(
    window: Window?,
    rect: IntRect,
    enabled: Boolean,
    blurPx: Int = 0,
): ImageBitmap? {
    var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
    val thread = remember { HandlerThread("bar-backdrop").apply { start() } }
    val handler = remember(thread) { Handler(thread.looper) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val live = enabled && window != null && !rect.isEmpty

    LaunchedEffect(live, window, rect, lifecycle, blurPx) {
        if (!live) {
            backdrop = null
            return@LaunchedEffect
        }
        val target = window
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val frames = Channel<Unit>(Channel.CONFLATED)
            val observer = target.decorView.viewTreeObserver
            val listener = ViewTreeObserver.OnPreDrawListener {
                frames.trySend(Unit)
                true
            }
            observer.addOnPreDrawListener(listener)
            try {
                // Two buffers, ping-ponged: the one Compose is drawing is never the one PixelCopy is
                // writing. No per-frame allocation and no recycled bitmap a frame could still be reading.
                var displayed: Bitmap? = null
                var spare: Bitmap? = null
                val buffers = BlurBuffers()

                suspend fun read() {
                    val next = sample(target, rect, spare, handler, blurPx, buffers) ?: return
                    spare = displayed
                    displayed = next
                    backdrop = next.asImageBitmap()
                }

                // The page may already be drawn and still, in which case no pre-draw is coming.
                frames.trySend(Unit)
                while (isActive) {
                    frames.receive()
                    read()
                    // A pre-draw runs before its frame is presented, so the copy it triggers can be one
                    // frame behind. Follow the burst at the page's own rate; when it stops, one
                    // confirming read catches the last frame.
                    while (withTimeoutOrNull(BAR_BACKDROP_SETTLE_MS) { frames.receive() } != null) {
                        read()
                    }
                    read()
                }
            } finally {
                observer.removeOnPreDrawListener(listener)
            }
        }
    }

    DisposableEffect(thread) { onDispose { thread.quitSafely() } }
    return backdrop
}

private class BlurBuffers {
    var px: IntArray = IntArray(0)
    var tmp: IntArray = IntArray(0)

    fun ensure(size: Int) {
        if (px.size < size) {
            px = IntArray(size)
            tmp = IntArray(size)
        }
    }
}

private suspend fun sample(
    window: Window,
    rect: IntRect,
    reuse: Bitmap?,
    handler: Handler,
    blurPx: Int,
    buffers: BlurBuffers,
): Bitmap? = suspendCancellableCoroutine { cont ->
    val scale = backdropScale(rect.width, rect.height)
    val width = max(1, rect.width / scale)
    val height = max(1, rect.height / scale)
    val bitmap = reuse?.takeIf { it.width == width && it.height == height }
        ?: createBitmap(width, height)
    val settled = AtomicBoolean(false)
    val request = runCatching {
        PixelCopy.request(
            window,
            Rect(rect.left, rect.top, rect.right, rect.bottom),
            bitmap,
            { status ->
                // The listener is the only writer of `settled` unless the coroutine is cancelled first.
                // The bitmap belongs to the caller and is never recycled here: it may be the one the bar
                // is still drawing.
                if (settled.compareAndSet(false, true) && cont.isActive) {
                    // Blurred here, on the copy thread, so neither the read nor the blur can stall a
                    // UI frame. The radius is the slider's screen pixels over the same integer shrink
                    // the copy was taken at, so a 10 dp blur is 10 dp of glass at any density.
                    if (status == PixelCopy.SUCCESS) {
                        boxBlur(bitmap, blurPx / scale, buffers)
                        cont.resume(bitmap)
                    } else {
                        cont.resume(null)
                    }
                }
            },
            handler,
        )
    }
    if (request.isFailure) {
        if (settled.compareAndSet(false, true) && cont.isActive) cont.resume(null)
    }
    cont.invokeOnCancellation { settled.set(true) }
}

/**
 * A box blur, twice, in place.
 *
 * The platform's [android.graphics.RenderEffect] is the obvious tool and was the first attempt; on the
 * standing handset it silently drew the sample unblurred inside the bar's popup, which is worse than no
 * blur because the page appears copied and slightly offset. An integer box blur on the copy is a few
 * hundred thousand adds for a bar-sized bitmap, runs on the copy thread, and looks the same on every
 * supported version. Two passes rather than one: a single box leaves visible banding on a photograph,
 * the second averages it into a soft gradient.
 */
private fun boxBlur(bitmap: Bitmap, radius: Int, buffers: BlurBuffers) {
    val w = bitmap.width
    val h = bitmap.height
    if (radius <= 0 || w < 2 || h < 2) return
    val r = radius.coerceAtMost(minOf(w, h) / 2)
    buffers.ensure(w * h)
    val px = buffers.px
    val tmp = buffers.tmp
    bitmap.getPixels(px, 0, w, 0, 0, w, h)
    repeat(2) {
        blurRows(px, tmp, w, h, r)
        blurColumns(tmp, px, w, h, r)
    }
    bitmap.setPixels(px, 0, w, 0, 0, w, h)
}

private fun blurRows(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val span = radius * 2 + 1
    for (y in 0 until h) {
        val row = y * w
        var a = 0
        var red = 0
        var green = 0
        var blue = 0
        for (i in -radius..radius) {
            val p = src[row + i.coerceIn(0, w - 1)]
            a += p ushr 24
            red += (p shr 16) and 0xFF
            green += (p shr 8) and 0xFF
            blue += p and 0xFF
        }
        for (x in 0 until w) {
            dst[row + x] = ((a / span) shl 24) or ((red / span) shl 16) or
                ((green / span) shl 8) or (blue / span)
            val add = src[row + (x + radius + 1).coerceAtMost(w - 1)]
            val sub = src[row + (x - radius).coerceAtLeast(0)]
            a += (add ushr 24) - (sub ushr 24)
            red += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
            green += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
            blue += (add and 0xFF) - (sub and 0xFF)
        }
    }
}

private fun blurColumns(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val span = radius * 2 + 1
    for (x in 0 until w) {
        var a = 0
        var red = 0
        var green = 0
        var blue = 0
        for (i in -radius..radius) {
            val p = src[i.coerceIn(0, h - 1) * w + x]
            a += p ushr 24
            red += (p shr 16) and 0xFF
            green += (p shr 8) and 0xFF
            blue += p and 0xFF
        }
        for (y in 0 until h) {
            dst[y * w + x] = ((a / span) shl 24) or ((red / span) shl 16) or
                ((green / span) shl 8) or (blue / span)
            val add = src[(y + radius + 1).coerceAtMost(h - 1) * w + x]
            val sub = src[(y - radius).coerceAtLeast(0) * w + x]
            a += (add ushr 24) - (sub ushr 24)
            red += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
            green += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
            blue += (add and 0xFF) - (sub and 0xFF)
        }
    }
}
