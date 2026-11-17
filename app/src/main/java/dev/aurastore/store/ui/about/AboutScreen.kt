package dev.aurastore.store.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aurastore.store.R
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.InfoCard
import dev.aurastore.store.ui.InfoRow
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.library.PageBar
import dev.aurastore.store.ui.library.TABULAR
import dev.aurastore.store.update.AppUpdater

/**
 * What this client verifies, on a page.
 *
 * It was an `AlertDialog` with four sections of prose inside it. At a 2.0× text size the dialog was
 * taller than the phone and the third section sat under its edge with a scrollbar nobody could see, so
 * the one page that explains the checking was itself unreadable. A page scrolls by construction, and
 * the back arrow is already the way out of every other page here.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onReplayTour: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val installed = remember(context) { AppUpdater.installed(context) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current),
    ) {
        item { PageBar(stringResource(R.string.about_title), onBack) }

        item {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.about_version, installed.name),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Text(
                stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        item {
            InfoCard {
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
                InfoRow(
                    Glyph.Play,
                    stringResource(R.string.about_tour),
                    subtitle = stringResource(R.string.about_tour_body),
                    onClick = onReplayTour,
                )
            }
        }

        item {
            Text(
                stringResource(R.string.settings_about_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}
