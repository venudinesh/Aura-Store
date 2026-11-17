package dev.aurastore.store.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.aurastore.store.data.BackgroundTheme
import dev.aurastore.store.data.Settings
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.backdrop.TelegramBackdrop
import dev.aurastore.store.ui.library.PageBar

/**
 * The wallpaper behind the store, chosen by watching it.
 *
 * The page is a stage and four tiles, the shape the download-panel picker already uses: the live
 * effect fills the card at the top at the same density as the real page, and each tile shows a still
 * frame of its own at tile size. A tap writes the preference, so the whole store changes behind the
 * page while the sample is still in front of it.
 *
 * The still frames are the same renderer with motion off, not screenshots: an effect that changes
 * here cannot leave a stale thumbnail behind.
 */
@Composable
internal fun BackgroundPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val selected by Settings.backgroundTheme.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        PageBar(stringResource(R.string.settings_section_background), onBack)
        BackgroundStage(
            theme = selected,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
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
            items(BackgroundTheme.entries, key = { it.name }) { theme ->
                BackgroundTile(
                    theme = theme,
                    selected = theme == selected,
                    onClick = { Settings.setBackgroundTheme(context, theme) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.background_credit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                )
            }
        }
    }
}

/** The live effect in use, with its name and the line that describes it. */
@Composable
private fun BackgroundStage(theme: BackgroundTheme, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.background_preview_a11y, stringResource(theme.label))

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_background_live).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Surface(shape = RoundedCornerShape(50), color = scheme.primaryContainer) {
                    Text(
                        stringResource(theme.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .semantics { contentDescription = description },
            ) {
                TelegramBackdrop(
                    theme = theme,
                    modifier = Modifier.fillMaxSize(),
                    animate = PlayMotion.enabled,
                )
                if (theme == BackgroundTheme.NONE) {
                    Text(
                        stringResource(R.string.background_none),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                // A quiet pill at the bottom reads as the store's own bar, so the sample is a page
                // wearing the wallpaper rather than a colour swatch with weather on it.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .fillMaxWidth(0.72f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(scheme.surface.copy(alpha = 0.45f)),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(theme.description),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One choice: a still frame of the effect, its name, and a check when the store is wearing it. */
@Composable
private fun BackgroundTile(
    theme: BackgroundTheme,
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
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(74.dp)) {
                TelegramBackdrop(
                    theme = theme,
                    modifier = Modifier.fillMaxSize(),
                    animate = false,
                )
                if (selected) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(scheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(Glyph.Check, size = 12.dp, tint = scheme.onPrimary)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(
                    stringResource(theme.label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(theme.description),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.72f)
                    else scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
