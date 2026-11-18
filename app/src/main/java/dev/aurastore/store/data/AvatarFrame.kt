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
 *
 * The last thirty-one wear an anime: each entry in the Special pack has a ring drawn from its own
 * palette and its own mark, and [anime] carries the link so the painter reads the same name the
 * catalogue does.
 */
enum class AvatarFrame(
    val id: String,
    @StringRes val label: Int,
    val animated: Boolean,
    val anime: SpecialAnime? = null,
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
    ANIME_NARUTO("anime-1", R.string.special_1, true, SpecialAnime.NARUTO),
    ANIME_ONE_PIECE("anime-2", R.string.special_2, true, SpecialAnime.ONE_PIECE),
    ANIME_ATTACK_ON_TITAN("anime-3", R.string.special_3, true, SpecialAnime.ATTACK_ON_TITAN),
    ANIME_DEMON_SLAYER("anime-4", R.string.special_4, true, SpecialAnime.DEMON_SLAYER),
    ANIME_JUJUTSU_KAISEN("anime-5", R.string.special_5, true, SpecialAnime.JUJUTSU_KAISEN),
    ANIME_MY_HERO_ACADEMIA("anime-6", R.string.special_6, true, SpecialAnime.MY_HERO_ACADEMIA),
    ANIME_DEATH_NOTE("anime-7", R.string.special_7, true, SpecialAnime.DEATH_NOTE),
    ANIME_FULLMETAL_ALCHEMIST("anime-8", R.string.special_8, true, SpecialAnime.FULLMETAL_ALCHEMIST),
    ANIME_DRAGON_BALL("anime-9", R.string.special_9, true, SpecialAnime.DRAGON_BALL),
    ANIME_SWORD_ART_ONLINE("anime-10", R.string.special_10, true, SpecialAnime.SWORD_ART_ONLINE),
    ANIME_TOKYO_GHOUL("anime-11", R.string.special_11, true, SpecialAnime.TOKYO_GHOUL),
    ANIME_CHAINSAW_MAN("anime-12", R.string.special_12, true, SpecialAnime.CHAINSAW_MAN),
    ANIME_SPY_X_FAMILY("anime-13", R.string.special_13, true, SpecialAnime.SPY_X_FAMILY),
    ANIME_ONE_PUNCH_MAN("anime-14", R.string.special_14, true, SpecialAnime.ONE_PUNCH_MAN),
    ANIME_HUNTER_X_HUNTER("anime-15", R.string.special_15, true, SpecialAnime.HUNTER_X_HUNTER),
    ANIME_BLEACH("anime-16", R.string.special_16, true, SpecialAnime.BLEACH),
    ANIME_SOLO_LEVELING("anime-17", R.string.special_17, true, SpecialAnime.SOLO_LEVELING),
    ANIME_FRIEREN("anime-18", R.string.special_18, true, SpecialAnime.FRIEREN),
    ANIME_EVANGELION("anime-19", R.string.special_19, true, SpecialAnime.EVANGELION),
    ANIME_COWBOY_BEBOP("anime-20", R.string.special_20, true, SpecialAnime.COWBOY_BEBOP),
    ANIME_SAILOR_MOON("anime-21", R.string.special_21, true, SpecialAnime.SAILOR_MOON),
    ANIME_POKEMON("anime-22", R.string.special_22, true, SpecialAnime.POKEMON),
    ANIME_SPIRITED_AWAY("anime-23", R.string.special_23, true, SpecialAnime.SPIRITED_AWAY),
    ANIME_YOUR_NAME("anime-24", R.string.special_24, true, SpecialAnime.YOUR_NAME),
    ANIME_VINLAND_SAGA("anime-25", R.string.special_25, true, SpecialAnime.VINLAND_SAGA),
    ANIME_MOB_PSYCHO("anime-26", R.string.special_26, true, SpecialAnime.MOB_PSYCHO),
    ANIME_BLACK_CLOVER("anime-27", R.string.special_27, true, SpecialAnime.BLACK_CLOVER),
    ANIME_BLUE_LOCK("anime-28", R.string.special_28, true, SpecialAnime.BLUE_LOCK),
    ANIME_CODE_GEASS("anime-29", R.string.special_29, true, SpecialAnime.CODE_GEASS),
    ANIME_DANDADAN("anime-30", R.string.special_30, true, SpecialAnime.DANDADAN),
    ANIME_JOJO("anime-31", R.string.special_31, true, SpecialAnime.JOJO),
    ;

    companion object {
        /** The frame named by a stored id, or [NONE] when the value is missing or unknown. */
        fun from(id: String?): AvatarFrame = entries.firstOrNull { it.id == id } ?: NONE
    }
}
