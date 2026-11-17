package dev.aurastore.store.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.data.DockAnimation
import dev.aurastore.store.data.Settings
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.ui.FloatingBar
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.downloads.DockProgressLayer
import dev.aurastore.store.ui.downloads.rememberDockPhase
import dev.aurastore.store.ui.library.PageBar
import dev.aurastore.store.ui.playClickable
import kotlinx.coroutines.delay

/**
 * The download panel's meters, chosen by watching one.
 *
 * The page is a stage, not a form. The meter in use plays at the top exactly as the panel plays it;
 * the styles are tiles below a two-way filter, and a tap swaps the stage in place. A pick writes the
 * preference, so the panel over the bar changes while the sample is still sweeping.
 */
@Composable
internal fun DockAnimationPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val selected by Settings.dockAnimation.collectAsStateWithLifecycle()
    var limitedOnly by remember { mutableStateOf(selected.limited) }
    val standard = DockAnimation.entries.filter { !it.limited }
    val limited = DockAnimation.entries.filter { it.limited }
    val styles = if (limitedOnly) limited else standard

    Column(modifier.fillMaxSize()) {
        PageBar(stringResource(R.string.settings_section_dock), onBack)
        DockStage(
            animation = selected,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        DockFilter(
            limited = limitedOnly,
            onStandard = { limitedOnly = false },
            onLimited = { limitedOnly = true },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 24.dp + LocalNavBarInset.current,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(styles, key = { it.name }) { animation ->
                DockStyleTile(
                    animation = animation,
                    selected = animation == selected,
                    onClick = { Settings.setDockAnimation(context, animation) },
                )
            }
        }
    }
}

/** The live meter in use, with the name it answers to and the line that describes it. */
@Composable
private fun DockStage(animation: DockAnimation, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dock_anim_live_preview).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Surface(shape = RoundedCornerShape(50), color = scheme.primaryContainer) {
                    Text(
                        stringResource(animation.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            DockSample(animation)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(animation.description),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The panel at the size it takes over the bar, sweeping the same linear pass the dock sweeps. */
@Composable
private fun DockSample(animation: DockAnimation) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(animation.label)
    val description = stringResource(R.string.dock_anim_preview_a11y, label)
    val motion = PlayMotion.enabled
    val sample = remember { Animatable(if (motion) 0.06f else 0.62f) }
    LaunchedEffect(motion, animation) {
        if (!motion) {
            sample.snapTo(0.62f)
            return@LaunchedEffect
        }
        while (true) {
            sample.snapTo(0.06f)
            delay(350)
            sample.animateTo(
                targetValue = 0.96f,
                animationSpec = tween(5200, easing = LinearEasing),
            )
            delay(650)
        }
    }
    val fraction = sample.value
    val phase = if (motion) rememberDockPhase() else 0f

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
    ) {
        Box(Modifier.fillMaxWidth()) {
            DockProgressLayer(
                animation = animation,
                fraction = fraction,
                state = DownloadState.DOWNLOADING,
                done = false,
                phase = phase,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlyphIcon(
                            Glyph.Brand,
                            size = 19.dp,
                            tint = scheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dock_anim_preview_name),
                        style = FloatingBar.legibleStyle(MaterialTheme.typography.titleSmall, 1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.dock_anim_preview_status),
                        style = FloatingBar.legibleStyle(MaterialTheme.typography.bodySmall, 1f),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Decorative: the stage is the sample, and a second live control would pause a
                // transfer that does not exist.
                Surface(
                    shape = CircleShape,
                    color = scheme.primaryContainer,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlyphIcon(
                            Glyph.Pause,
                            size = 15.dp,
                            tint = scheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/** The two families, as one segmented control. */
@Composable
private fun DockFilter(
    limited: Boolean,
    onStandard: () -> Unit,
    onLimited: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            DockFilterChip(
                text = stringResource(R.string.dock_anim_standard),
                selected = !limited,
                onClick = onStandard,
                modifier = Modifier.weight(1f),
            )
            DockFilterChip(
                text = stringResource(R.string.dock_anim_limited_tab),
                selected = limited,
                onClick = onLimited,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DockFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) scheme.primaryContainer else Color.Transparent,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .playClickable(onClick = onClick),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

/** One style: the name, the look in a line, and a check when it is on the panel. */
@Composable
private fun DockStyleTile(
    animation: DockAnimation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (selected) scheme.primary.copy(alpha = 0.55f)
            else scheme.outlineVariant.copy(alpha = 0.30f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(animation.label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    GlyphIcon(
                        Glyph.Check,
                        size = 15.dp,
                        tint = scheme.onPrimaryContainer,
                    )
                }
            }
            Text(
                stringResource(animation.description),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.72f)
                else scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
