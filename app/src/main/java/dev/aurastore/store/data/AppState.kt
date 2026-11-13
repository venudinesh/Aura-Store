package dev.aurastore.store.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What this app knows about a package it installed earlier. */
data class InstallRecord(
    val packageName: String,
    val versionName: String?,
    val installedAt: Long,
)

/**
 * Bridge between the install bookkeeping (SharedPreferences written by the installer) and the UI,
 * so every card can render Open / Update / Get without touching the disk on each recomposition.
 */
object AppState {

    private val _installs = MutableStateFlow<Map<Long, InstallRecord>>(emptyMap())

    /** Catalogue post id -> record of the package installed from it. */
    val installs: StateFlow<Map<Long, InstallRecord>> = _installs.asStateFlow()

    fun refresh(context: Context) {
        _installs.value = read(context)
    }

    private fun read(context: Context): Map<Long, InstallRecord> {
        val prefs = context.getSharedPreferences("aurastore_installs", Context.MODE_PRIVATE)
        val out = mutableMapOf<Long, InstallRecord>()
        for ((key, value) in prefs.all) {
            if (!key.startsWith("post:") || value !is Long || value <= 0) continue
            val pkg = key.removePrefix("post:")
            val installedAt = prefs.getLong("installedAt:$pkg", 0L)
            // Only report it as installed when the package is actually present on the device.
            val present = InstalledApps.installedVersion(context, pkg)
            if (present != null) {
                out[value] = InstallRecord(pkg, present, installedAt)
            }
        }
        return out
    }
}
