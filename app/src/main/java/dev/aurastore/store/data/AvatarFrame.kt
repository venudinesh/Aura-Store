package dev.aurastore.store.data

import androidx.annotation.StringRes
import dev.aurastore.store.R

/**
 * The ring worn around the profile picture.
 *
 * A frame is a drawing, not a bitmap: it is painted in the same 100-unit space at 32 dp in the top bar
 * and 112 dp on the profile, so one choice is the same picture at every size and no density folder has
 * to hold variants. [NONE] is the shipped look: an install that never opens the picker keeps the bare
 * circle it already had.
 *
 * Every frame past the first four turns under its own power. Still rings are the house's oldest
 * four; the rest were added as a moving set and are marked in one place, [animated], so the picker
 * knows which previews need a clock.
 */
enum class AvatarFrame(
    val id: String,
    @StringRes val label: Int,
    val animated: Boolean,
) {
    NONE("none", R.string.frame_none, false),
    RING("ring", R.string.frame_ring, false),
    GOLD("gold", R.string.frame_gold, false),
    LAUREL("laurel", R.string.frame_laurel, false),
    NEON("neon", R.string.frame_neon, true),
    ORBIT("orbit", R.string.frame_orbit, true),
    PULSE("pulse", R.string.frame_pulse, true),
    EMBER("ember", R.string.frame_ember, true),
    COMET("comet", R.string.frame_comet, true),
    NOVA("nova", R.string.frame_nova, true),
    HALO("halo", R.string.frame_halo, true),
    CIRCUIT("circuit", R.string.frame_circuit, true),
    SAKURA("sakura", R.string.frame_sakura, true),
    FROST("frost", R.string.frame_frost, true),
    INFERNO("inferno", R.string.frame_inferno, true),
    TIDE("tide", R.string.frame_tide, true),
    STORM("storm", R.string.frame_storm, true),
    VORTEX("vortex", R.string.frame_vortex, true),
    PRISM("prism", R.string.frame_prism, true),
    RUNE("rune", R.string.frame_rune, true),
    STARFALL("starfall", R.string.frame_starfall, true),
    AURORA("aurora", R.string.frame_aurora, true),
    SERPENT("serpent", R.string.frame_serpent, true),
    ECLIPSE("eclipse", R.string.frame_eclipse, true),
    ECHO("echo", R.string.frame_echo, true),
    SHARDS("shards", R.string.frame_shards, true),
    TWINKLE("twinkle", R.string.frame_twinkle, true),
    CHAIN("chain", R.string.frame_chain, true),
    COMPASS("compass", R.string.frame_compass, true),
    HEARTBEAT("heartbeat", R.string.frame_heartbeat, true),
    CINDER("cinder", R.string.frame_cinder, true),
    LOTUS("lotus", R.string.frame_lotus, true),
    HEX("hex", R.string.frame_hex, true),
    RIPPLE("ripple", R.string.frame_ripple, true),
    BLADE("blade", R.string.frame_blade, true),
    METEOR("meteor", R.string.frame_meteor, true),
    QUASAR("quasar", R.string.frame_quasar, true),
    ZENITH("zenith", R.string.frame_zenith, true),
    ;

    companion object {
        /** The frame named by a stored id, or [NONE] when the value is missing or unknown. */
        fun from(id: String?): AvatarFrame = entries.firstOrNull { it.id == id } ?: NONE
    }
}
