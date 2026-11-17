package dev.aurastore.store.data

import androidx.annotation.StringRes
import dev.aurastore.store.R

/**
 * The ring worn around the profile picture.
 *
 * A frame is a drawing, not a bitmap: it is painted in the same 100-unit space at 32 dp in the top bar
 * and 112 dp on the profile, so one choice is the same picture at every size and no density folder has
 * to hold eight variants. [NONE] is the shipped look: an install that never opens the picker keeps the
 * bare circle it already had.
 */
enum class AvatarFrame(val id: String, @StringRes val label: Int) {
    NONE("none", R.string.frame_none),
    RING("ring", R.string.frame_ring),
    GOLD("gold", R.string.frame_gold),
    NEON("neon", R.string.frame_neon),
    ORBIT("orbit", R.string.frame_orbit),
    LAUREL("laurel", R.string.frame_laurel),
    PULSE("pulse", R.string.frame_pulse),
    EMBER("ember", R.string.frame_ember),
    ;

    /** A frame that turns under its own power. The profile and the picker animate it; the top bar does not. */
    val animated: Boolean
        get() = when (this) {
            NEON, ORBIT, PULSE, EMBER -> true
            NONE, RING, GOLD, LAUREL -> false
        }

    companion object {
        /** The frame named by a stored id, or [NONE] when the value is missing or unknown. */
        fun from(id: String?): AvatarFrame = entries.firstOrNull { it.id == id } ?: NONE
    }
}
