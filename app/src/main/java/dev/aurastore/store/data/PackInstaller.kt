package dev.aurastore.store.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The pack's installer: the posters that are missing, fetched from the repository and put back.
 *
 * The catalogue is the surface; this object is the machine behind it. Installing a set from the
 * catalogue is the same transaction as restoring art, with one difference that matters: it runs on
 * the app's clock rather than a screen's, so leaving the catalogue does not cancel a transfer
 * halfway. Progress is a flow keyed by anime id, which is what the card under the finger reads to
 * draw its ring.
 *
 * One set installs at a time per anime; a second tap while it is running is the same tap again and
 * is ignored rather than queued. Every file that lands comes off the removed list, so the holes the
 * installer filled close as it fills them.
 */
object PackInstaller {

    /**
     * How far one anime's install has come: files attempted, how many in all, and which are still in
     * flight. [active] is what a poster-sized hole reads to know it is the one being fetched.
     */
    data class Install(
        val done: Int,
        val total: Int,
        val failed: Int = 0,
        val active: Set<Int> = emptySet(),
    ) {
        val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
        val running: Boolean get() = done < total
    }

    private val _state = MutableStateFlow<Map<String, Install>>(emptyMap())
    val state: StateFlow<Map<String, Install>> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val busy: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Fetches [posters] of [anime]; the ones that land come off the removed list. */
    fun install(context: Context, anime: SpecialAnime, posters: List<Int>) {
        val wanted = posters.filter { it in anime.posters.indices }
        if (wanted.isEmpty() || !busy.add(anime.id)) return
        val app = context.applicationContext
        _state.update { it + (anime.id to Install(0, wanted.size, active = wanted.toSet())) }
        scope.launch {
            val landed = runCatching {
                PackArt.fetch(app, anime, wanted) { done, total, poster ->
                    // Each file closes its own hole as it lands. The catalogues keep all thirty slots
                    // either way, so the picture sharpens under the finger and the count on the badge
                    // falls with it instead of jumping at the end.
                    if (poster != null) PackLibrary.restore(app, anime, listOf(poster))
                    // fetch walks the list in order, so what is left at this count is what is still
                    // being fetched. The last callback leaves an empty set and a finished install.
                    _state.update {
                        it + (anime.id to Install(done, total, active = wanted.drop(done).toSet()))
                    }
                }
            }.getOrDefault(emptyList())
            val failed = wanted.size - landed.size
            _state.update { current ->
                if (failed == 0) {
                    current - anime.id
                } else {
                    // A refusal is kept on the card rather than swallowed: the set is still missing
                    // that many posters, and the next tap is the retry.
                    current + (anime.id to Install(wanted.size, wanted.size, failed))
                }
            }
            busy.remove(anime.id)
        }
    }

    /** Installs every group's missing posters. [install] already skips a set that is running. */
    fun installMissing(context: Context, groups: List<Pair<SpecialAnime, List<Int>>>) {
        groups.forEach { (anime, posters) -> install(context, anime, posters) }
    }

    /** Forgets a finished set's failure mark, so the card reads as installed again. */
    fun dismiss(anime: SpecialAnime) {
        _state.update { it - anime.id }
    }
}
