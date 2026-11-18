package dev.aurastore.store.ui.profile

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.core.graphics.createBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.data.AvatarStore
import dev.aurastore.store.data.CUSTOM_AVATAR
import dev.aurastore.store.data.GameFace
import dev.aurastore.store.data.AvatarFrame
import dev.aurastore.store.data.SpecialAnime
import dev.aurastore.store.data.SpecialPick
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.playClickable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The profile picture: the chosen game face, the square cropped from the gallery, or the Aura mark,
 * inside the frame the user chose.
 *
 * Round at every size. The custom square is decoded once per write of the file and scaled by the
 * crop, so a photo chosen at 1024 px is the same picture at 32 px and at 96 px. The frame is painted
 * on top of an inset picture, never outside the square, so the top bar and the header wear the same
 * ring without either one changing the layout of the row it sits in.
 */
@Composable
fun ProfilePicture(
    choice: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    frame: AvatarFrame = AvatarFrame.NONE,
    contentDescription: String? = null,
    /** A clock shared by a grid of pictures; null gives this picture its own. */
    phase: Float? = null,
) {
    val context = LocalContext.current
    val version by AvatarStore.version.collectAsStateWithLifecycle()
    val face = remember(choice) { GameFace.from(choice) }
    val special = remember(choice) { SpecialPick.from(choice) }
    val custom = choice == CUSTOM_AVATAR
    val scheme = MaterialTheme.colorScheme
    val bitmap = remember(choice, version, custom) {
        if (custom) AvatarStore.load(context) else null
    }
    val inset = if (frame == AvatarFrame.NONE) 0.dp else size * 0.078f
    // The bar copy turns with the header now: a moving ring is the profile's own mark, and a still
    // one in the bar read as a different picture rather than a smaller one.
    val ownPhase = rememberFramePhase(
        frame,
        animate = PlayMotion.enabled && phase == null,
    )
    val framePhase = phase ?: ownPhase
    Box(
        modifier
            .size(size)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().padding(inset).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                face != null -> Canvas(Modifier.fillMaxSize()) { drawFace(face) }

                special != null -> Image(
                    painter = painterResource(special.art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> Canvas(Modifier.fillMaxSize()) {
                    drawRect(scheme.surfaceContainerHigh)
                    val unit = this.size.minDimension / 100f
                    scale(unit, unit, Offset.Zero) {
                        // The same 136 × 212 letter the splash writes, fitted to 90 units of
                        // height inside the circle: legs opened, the bar just above the middle,
                        // and the bar running past both feet.
                        val ink = scheme.onSurface
                        drawPath(
                            Path().apply {
                                moveTo(33.1f, 88.7f)
                                quadraticTo(39.7f, 44.5f, 50f, 6f)
                                quadraticTo(60.3f, 45.5f, 66.9f, 90.6f)
                                lineTo(23.7f, 50.2f)
                            },
                            ink,
                            style = Stroke(4.5f, cap = StrokeCap.Round),
                        )
                        drawPath(
                            Path().apply {
                                moveTo(23.7f, 50.2f)
                                lineTo(76.3f, 50.2f)
                            },
                            ink,
                            style = Stroke(4.5f, cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }
        if (frame != AvatarFrame.NONE) {
            Canvas(Modifier.fillMaxSize()) { drawAvatarFrame(frame, framePhase, scheme) }
        }
    }
}

/**
 * The picker: eight drawn faces, eight painted ones, and a gallery door.
 *
 * The drawn faces are the defaults, drawn rather than fetched, so a fresh install has a full set with
 * no network and no storage. The Special pack is the painted set. The gallery row is the only way out
 * of both sets, and a long press is not needed for anything: one tap chooses, one tap leaves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPicker(
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    onGallery: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Which anime's gallery is open. The pack is thirty doors, not nine hundred cells: a sheet that
    // listed every poster at once would be a wall of thumbnails with no way to tell whose it was.
    var openAnime by remember { mutableStateOf<SpecialAnime?>(null) }
    val picked = remember(current) { SpecialPick.from(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.profile_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                stringResource(R.string.profile_picker_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            // The two doors come before the set, not after it: the last row of a sheet sits against
            // the gesture bar, and an action that can only be reached by a scroll at the very edge is
            // an action half the people never find.
            Row(
                Modifier
                    .fillMaxWidth()
                    .playClickable(onClick = onGallery)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Grid, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(14.dp))
                Text(
                    stringResource(R.string.profile_gallery),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            if (current != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .playClickable(onClick = { onPick(null) })
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(Glyph.Brand, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.profile_default),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            val faces = GameFace.entries
            faces.chunked(4).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    row.forEach { face ->
                        FaceCell(
                            label = stringResource(face.label),
                            selected = current == face.choice,
                            onClick = { onPick(face.choice) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Canvas(Modifier.size(56.dp).clip(CircleShape)) { drawFace(face) }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // The Special pack is its own category rather than more cells in the same row: the
            // drawn set is the house's own, and a painted face is a different kind of choice.
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            val open = openAnime
            if (open == null) {
                Text(
                    stringResource(R.string.special_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    stringResource(R.string.special_faces_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                // Three columns, not four: a title like "Fullmetal Alchemist" needs the room, and the
                // sheet scrolls either way. A card opens the anime rather than wearing its first
                // poster: the choice being offered is one of thirty, not whichever one is on top.
                SpecialAnime.entries.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        row.forEach { anime ->
                            FaceCell(
                                label = stringResource(anime.label),
                                labelLines = 2,
                                selected = picked?.anime == anime,
                                onClick = { openAnime = anime },
                                modifier = Modifier.weight(1f),
                            ) {
                                Image(
                                    painter = painterResource(anime.posters.first()),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(CircleShape),
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .playClickable(onClick = { openAnime = null })
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(Glyph.Back, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(open.label),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.special_faces_gallery),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Thirty posters, three to a row, each drawn in the circle the picture is worn in:
                // the cell is the crop, so what is tapped is what the profile shows.
                open.posters.toList().chunked(3).forEachIndexed { rowIndex, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        row.forEachIndexed { column, art ->
                            val index = rowIndex * 3 + column
                            FaceCell(
                                label = "${index + 1}",
                                selected = picked == SpecialPick(open, index),
                                onClick = { onPick(SpecialPick(open, index).choice) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Image(
                                    painter = painterResource(art),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(CircleShape),
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelLines: Int = 1,
    art: @Composable () -> Unit,
) {
    Column(
        modifier.playClickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            art()
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
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The picture crop: one window, a rule-of-thirds grid, and the photo moved under it.
 *
 * The grid is the only overlay, because a photo is judged by where the face falls in it. Pinch sets
 * the zoom, drag moves the photograph, and Save writes exactly what the window shows, so the picture
 * on the profile is the frame that was chosen rather than a guess at it. The square is the avatar; the
 * wide window is the cover, and both come through the same maths so the gesture behaves the same.
 */
@Composable
fun AvatarCropDialog(
    source: Bitmap,
    onCancel: () -> Unit,
    onSave: (Bitmap) -> Unit,
) {
    ImageCropDialog(
        source = source,
        aspect = 1f,
        outputWidth = 1024,
        title = R.string.profile_crop_title,
        hint = R.string.profile_crop_hint,
        onCancel = onCancel,
        onSave = onSave,
    )
}

@Composable
fun ImageCropDialog(
    source: Bitmap,
    aspect: Float,
    outputWidth: Int,
    @StringRes title: Int,
    @StringRes hint: Int,
    onCancel: () -> Unit,
    onSave: (Bitmap) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val handle = remember { CropHandle() }
    var saving by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (!saving) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel, enabled = !saving) {
                        Text(stringResource(R.string.profile_cancel))
                    }
                    Text(
                        if (saving) stringResource(R.string.profile_saving)
                        else stringResource(title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    TextButton(
                        enabled = !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                val cropped = withContext(Dispatchers.IO) {
                                    // The view's numbers are only known to the canvas; the crop is
                                    // taken from the same values it draws with.
                                    handle.produce?.invoke()
                                }
                                if (cropped != null) onSave(cropped) else saving = false
                            }
                        },
                    ) {
                        Text(stringResource(R.string.profile_save))
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CropView(
                        source = source,
                        handle = handle,
                        aspect = aspect,
                        outputWidth = outputWidth,
                    )
                }
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** The crop the canvas would produce, captured each frame so the Save button reads the same state. */
private class CropHandle {
    var produce: (() -> Bitmap?)? = null
}

@Composable
private fun CropView(source: Bitmap, handle: CropHandle, aspect: Float, outputWidth: Int) {
    val image = remember(source) { source.asImageBitmap() }
    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var pan by remember(source) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val windowWidth = minOf(maxWidth, maxHeight * aspect)
        val windowHeight = windowWidth / aspect
        val widthPx = with(density) { windowWidth.toPx() }
        val heightPx = with(density) { windowHeight.toPx() }
        val base = remember(source, widthPx, heightPx) {
            max(widthPx / source.width, heightPx / source.height)
        }
        val crop = remember(source, widthPx, heightPx, base, outputWidth) {
            {
                val scaleValue = base * zoom
                cropRegion(source, widthPx, heightPx, scaleValue, pan, outputWidth)
            }
        }
        handle.produce = crop

        Canvas(
            Modifier
                .size(windowWidth, windowHeight)
                .clipToBounds()
                .pointerInput(source, widthPx, heightPx, base) {
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val scaleValue = base * zoom
                        val nextZoom = (zoom * zoomChange).coerceIn(1f, 6f)
                        val nextScale = base * nextZoom
                        // Zoom around the fingers, not around the middle: the pixel under the
                        // pinch is the pixel that stays put.
                        val focus = centroid - Offset(widthPx / 2f, heightPx / 2f)
                        val moved = pan + panChange + focus * (1f - nextScale / scaleValue)
                        zoom = nextZoom
                        pan = clampPan(source, widthPx, heightPx, nextScale, moved)
                    }
                },
        ) {
            val scaleValue = base * zoom
            val drawnWidth = source.width * scaleValue
            val drawnHeight = source.height * scaleValue
            drawImage(
                image = image,
                dstOffset = IntOffset(
                    (this.size.width / 2f - drawnWidth / 2f + pan.x).roundToInt(),
                    (this.size.height / 2f - drawnHeight / 2f + pan.y).roundToInt(),
                ),
                dstSize = IntSize(drawnWidth.roundToInt(), drawnHeight.roundToInt()),
            )
            val line = Color.White.copy(alpha = 0.32f)
            for (i in 1..2) {
                val x = this.size.width / 3f * i
                val y = this.size.height / 3f * i
                drawLine(line, Offset(x, 0f), Offset(x, this.size.height), 1f)
                drawLine(line, Offset(0f, y), Offset(this.size.width, y), 1f)
            }
            drawRect(Color.White.copy(alpha = 0.55f), style = Stroke(1.dp.toPx()))
        }
    }
}

private fun clampPan(
    source: Bitmap,
    windowWidthPx: Float,
    windowHeightPx: Float,
    scale: Float,
    pan: Offset,
): Offset {
    val maxX = ((source.width * scale - windowWidthPx) / 2f).coerceAtLeast(0f)
    val maxY = ((source.height * scale - windowHeightPx) / 2f).coerceAtLeast(0f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/** The crop the window shows, written at [outputWidth] wide with the window's own proportions. */
private fun cropRegion(
    source: Bitmap,
    windowWidthPx: Float,
    windowHeightPx: Float,
    scale: Float,
    pan: Offset,
    outputWidth: Int,
): Bitmap? = runCatching {
    val spanX = (windowWidthPx / scale).coerceAtMost(source.width.toFloat())
    val spanY = (windowHeightPx / scale).coerceAtMost(source.height.toFloat())
    val left = (source.width / 2f - spanX / 2f - pan.x / scale)
        .coerceIn(0f, (source.width - spanX).coerceAtLeast(0f))
    val top = (source.height / 2f - spanY / 2f - pan.y / scale)
        .coerceIn(0f, (source.height - spanY).coerceAtLeast(0f))
    val outWidth = outputWidth.coerceAtLeast(1)
    val outHeight = (outWidth * (spanY / spanX)).roundToInt().coerceAtLeast(1)
    val out = createBitmap(outWidth, outHeight)
    val canvas = android.graphics.Canvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    canvas.drawBitmap(
        source,
        android.graphics.Rect(
            left.roundToInt(),
            top.roundToInt(),
            (left + spanX).roundToInt(),
            (top + spanY).roundToInt(),
        ),
        android.graphics.Rect(0, 0, outWidth, outHeight),
        paint,
    )
    out
}.getOrNull()

/** Draws a face in a 100-unit square, so one picture works at every size. */
private fun DrawScope.drawFace(face: GameFace) {
    val unit = size.minDimension / 100f
    scale(unit, unit, Offset.Zero) {
        val cream = Color(0xFFF2E9DE)
        when (face) {
            GameFace.KNIGHT -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF243A63), Color(0xFF0D1526)), Offset(0f, 0f), Offset(100f, 100f)))
                drawLine(cream, Offset(50f, 22f), Offset(50f, 62f), 7f, StrokeCap.Round)
                drawPath(
                    Path().apply {
                        moveTo(50f, 10f); lineTo(57f, 24f); lineTo(43f, 24f); close()
                    },
                    cream,
                )
                drawLine(cream, Offset(33f, 54f), Offset(67f, 54f), 7f, StrokeCap.Round)
                drawLine(Color(0xFFE0A44A), Offset(50f, 63f), Offset(50f, 74f), 6f, StrokeCap.Round)
                drawCircle(Color(0xFFE0A44A), 4.5f, Offset(50f, 79f))
            }

            GameFace.WIZARD -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF452466), Color(0xFF180C29)), Offset(0f, 0f), Offset(100f, 100f)))
                drawPath(
                    Path().apply {
                        moveTo(50f, 10f); lineTo(57f, 39f); lineTo(88f, 48f); lineTo(57f, 57f)
                        lineTo(50f, 88f); lineTo(43f, 57f); lineTo(12f, 48f); lineTo(43f, 39f)
                        close()
                    },
                    cream,
                )
                drawCircle(Color(0xFFB98AFF), 6f, Offset(50f, 48f))
                drawCircle(cream.copy(alpha = 0.8f), 2f, Offset(74f, 24f))
                drawCircle(cream.copy(alpha = 0.6f), 1.6f, Offset(26f, 70f))
            }

            GameFace.RACER -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF8A3812), Color(0xFF2A0D03)), Offset(0f, 0f), Offset(100f, 100f)))
                drawArc(
                    color = cream.copy(alpha = 0.45f),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(18f, 26f),
                    size = Size(64f, 64f),
                    style = Stroke(7f, cap = StrokeCap.Round),
                )
                drawLine(Color(0xFFFFC65C), Offset(50f, 58f), Offset(70f, 32f), 6f, StrokeCap.Round)
                drawCircle(cream, 5f, Offset(50f, 58f))
                for (row in 0..1) {
                    for (col in 0..7) {
                        if ((row + col) % 2 == 0) {
                            drawRect(
                                cream.copy(alpha = 0.85f),
                                topLeft = Offset(18f + col * 8f, 76f + row * 7f),
                                size = Size(7.4f, 6.4f),
                            )
                        }
                    }
                }
            }

            GameFace.ASTRO -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF101B2E), Color(0xFF05070C)), Offset(0f, 0f), Offset(100f, 100f)))
                drawPath(
                    Path().apply {
                        moveTo(50f, 12f)
                        quadraticTo(61f, 34f, 62f, 60f)
                        lineTo(38f, 60f)
                        quadraticTo(39f, 34f, 50f, 12f)
                        close()
                    },
                    cream,
                )
                drawCircle(Color(0xFF101B2E), 7f, Offset(50f, 38f))
                drawCircle(cream, 7f, Offset(50f, 38f), style = Stroke(2.4f))
                drawPath(
                    Path().apply {
                        moveTo(38f, 48f); lineTo(28f, 72f); lineTo(38f, 62f); close()
                    },
                    Color(0xFFE0A44A),
                )
                drawPath(
                    Path().apply {
                        moveTo(62f, 48f); lineTo(72f, 72f); lineTo(62f, 62f); close()
                    },
                    Color(0xFFE0A44A),
                )
                drawPath(
                    Path().apply {
                        moveTo(44f, 60f); lineTo(56f, 60f); lineTo(50f, 84f); close()
                    },
                    Color(0xFFFF9A3C),
                )
            }

            GameFace.ARENA -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF741826), Color(0xFF26070D)), Offset(0f, 0f), Offset(100f, 100f)))
                val shield = Path().apply {
                    moveTo(50f, 12f); lineTo(80f, 23f); lineTo(76f, 55f)
                    quadraticTo(68f, 76f, 50f, 86f)
                    quadraticTo(32f, 76f, 24f, 55f)
                    lineTo(20f, 23f); close()
                }
                drawPath(shield, cream.copy(alpha = 0.16f))
                drawPath(shield, cream, style = Stroke(5f))
                drawPath(
                    Path().apply {
                        moveTo(50f, 30f); lineTo(56f, 44f); lineTo(71f, 45f); lineTo(59f, 54f)
                        lineTo(63f, 69f); lineTo(50f, 60f); lineTo(37f, 69f); lineTo(41f, 54f)
                        lineTo(29f, 45f); lineTo(44f, 44f); close()
                    },
                    Color(0xFFE0A44A),
                )
            }

            GameFace.RETRO -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF11290E), Color(0xFF050F05)), Offset(0f, 0f), Offset(100f, 100f)))
                val pixels = listOf(
                    "..x..x..",
                    "...xx...",
                    "..xxxx..",
                    ".xx..xx.",
                    "xxxxxxxx",
                    "x.xxxx.x",
                    "x.x..x.x",
                    "...xx...",
                )
                val cell = 7f
                val origin = 50f - pixels.size * cell / 2f
                val green = Color(0xFF7CFF6B)
                pixels.forEachIndexed { y, line ->
                    line.forEachIndexed { x, mark ->
                        if (mark == 'x') {
                            drawRect(
                                green,
                                topLeft = Offset(origin + x * cell, origin + y * cell),
                                size = Size(cell - 1.2f, cell - 1.2f),
                            )
                        }
                    }
                }
            }

            GameFace.BEAST -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF5A1116), Color(0xFF160305)), Offset(0f, 0f), Offset(100f, 100f)))
                val claw = cream.copy(alpha = 0.9f)
                drawPath(
                    Path().apply { moveTo(30f, 16f); quadraticTo(34f, 50f, 38f, 84f) },
                    claw,
                    style = Stroke(6f, cap = StrokeCap.Round),
                )
                drawPath(
                    Path().apply { moveTo(48f, 12f); quadraticTo(52f, 48f, 56f, 86f) },
                    claw,
                    style = Stroke(7f, cap = StrokeCap.Round),
                )
                drawPath(
                    Path().apply { moveTo(66f, 16f); quadraticTo(70f, 50f, 74f, 84f) },
                    claw,
                    style = Stroke(6f, cap = StrokeCap.Round),
                )
            }

            GameFace.GEM -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF06504B), Color(0xFF01130F)), Offset(0f, 0f), Offset(100f, 100f)))
                val gem = Path().apply {
                    moveTo(50f, 12f); lineTo(78f, 38f); lineTo(50f, 88f); lineTo(22f, 38f); close()
                }
                drawPath(gem, Color(0xFF62D9C6))
                drawPath(gem, cream.copy(alpha = 0.55f), style = Stroke(2.6f))
                drawLine(cream.copy(alpha = 0.35f), Offset(22f, 38f), Offset(78f, 38f), 2f)
                drawLine(cream.copy(alpha = 0.35f), Offset(50f, 12f), Offset(50f, 88f), 2f)
            }
        }
    }
}
