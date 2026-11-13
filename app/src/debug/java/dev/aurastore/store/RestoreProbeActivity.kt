package dev.aurastore.store

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.aurastore.store.theme.AuraStoreTheme

/**
 * The shell's saved-state shapes, on a screen a test can actually recreate.
 *
 * Lives in `src/debug`, so it is merged into the debug variant and never into the release one. It has
 * to be in the app rather than the androidTest APK because an activity declared in the test package
 * resolves to a different process, and because a content lambda handed in by the test rule is destroyed
 * with the instance that set it — so a recreation test would be asserting that nothing was drawn.
 *
 * `cancelAndRecreateRecomposer()` was tried first and rejected: it preserves plain `remember`, which
 * makes it useless as a restoration test and is caught by the control below.
 */
class RestoreProbeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuraStoreTheme {
                Column {
                    // `detailPost` and `categoryOpen` in the shell are the Long shape, `tab` the Int.
                    // -1 and 0 both mean "nothing open", so a value that failed to restore lands the
                    // reader back on a tab list instead of crashing — the kind of bug that ships.
                    var open by rememberSaveable { mutableLongStateOf(-1L) }
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    // The control. Plain `remember` is not saved, so if this survives a recreation,
                    // "it survived" in the other two tests means nothing was ever recreated.
                    var transient by remember { mutableIntStateOf(0) }
                    Text("open=$open", modifier = Modifier.clickable { open = 4242L })
                    Text("tab=$tab", modifier = Modifier.clickable { tab = 3 })
                    Text("transient=$transient", modifier = Modifier.clickable { transient = 7 })
                }
            }
        }
    }
}
