package dev.aurastore.store.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.aurastore.store.R

/**
 * The thirty anime in the Special pack, each with a gallery of thirty posters.
 *
 * A poster is a 1024 x 576 JPEG in `drawable-nodpi`. The same file serves both pickers: the cover
 * crops it wide and the profile picture crops the same bitmap square, so one pool per anime gives
 * both without keeping a second copy of every frame.
 *
 * The array is explicit rather than looked up by name: resource shrinking is on in release, and
 * a name built at runtime is a name the shrinker cannot see.
 */
enum class SpecialAnime(
    val id: String,
    @StringRes val label: Int,
    val posters: IntArray,
) {
    NARUTO("1", R.string.special_1, intArrayOf(
        R.drawable.special_poster_1_1, R.drawable.special_poster_1_2, R.drawable.special_poster_1_3, R.drawable.special_poster_1_4, R.drawable.special_poster_1_5,
        R.drawable.special_poster_1_6, R.drawable.special_poster_1_7, R.drawable.special_poster_1_8, R.drawable.special_poster_1_9, R.drawable.special_poster_1_10,
        R.drawable.special_poster_1_11, R.drawable.special_poster_1_12, R.drawable.special_poster_1_13, R.drawable.special_poster_1_14, R.drawable.special_poster_1_15,
        R.drawable.special_poster_1_16, R.drawable.special_poster_1_17, R.drawable.special_poster_1_18, R.drawable.special_poster_1_19, R.drawable.special_poster_1_20,
        R.drawable.special_poster_1_21, R.drawable.special_poster_1_22, R.drawable.special_poster_1_23, R.drawable.special_poster_1_24, R.drawable.special_poster_1_25,
        R.drawable.special_poster_1_26, R.drawable.special_poster_1_27, R.drawable.special_poster_1_28, R.drawable.special_poster_1_29, R.drawable.special_poster_1_30,
    )),
    ONE_PIECE("2", R.string.special_2, intArrayOf(
        R.drawable.special_poster_2_1, R.drawable.special_poster_2_2, R.drawable.special_poster_2_3, R.drawable.special_poster_2_4, R.drawable.special_poster_2_5,
        R.drawable.special_poster_2_6, R.drawable.special_poster_2_7, R.drawable.special_poster_2_8, R.drawable.special_poster_2_9, R.drawable.special_poster_2_10,
        R.drawable.special_poster_2_11, R.drawable.special_poster_2_12, R.drawable.special_poster_2_13, R.drawable.special_poster_2_14, R.drawable.special_poster_2_15,
        R.drawable.special_poster_2_16, R.drawable.special_poster_2_17, R.drawable.special_poster_2_18, R.drawable.special_poster_2_19, R.drawable.special_poster_2_20,
        R.drawable.special_poster_2_21, R.drawable.special_poster_2_22, R.drawable.special_poster_2_23, R.drawable.special_poster_2_24, R.drawable.special_poster_2_25,
        R.drawable.special_poster_2_26, R.drawable.special_poster_2_27, R.drawable.special_poster_2_28, R.drawable.special_poster_2_29, R.drawable.special_poster_2_30,
    )),
    ATTACK_ON_TITAN("3", R.string.special_3, intArrayOf(
        R.drawable.special_poster_3_1, R.drawable.special_poster_3_2, R.drawable.special_poster_3_3, R.drawable.special_poster_3_4, R.drawable.special_poster_3_5,
        R.drawable.special_poster_3_6, R.drawable.special_poster_3_7, R.drawable.special_poster_3_8, R.drawable.special_poster_3_9, R.drawable.special_poster_3_10,
        R.drawable.special_poster_3_11, R.drawable.special_poster_3_12, R.drawable.special_poster_3_13, R.drawable.special_poster_3_14, R.drawable.special_poster_3_15,
        R.drawable.special_poster_3_16, R.drawable.special_poster_3_17, R.drawable.special_poster_3_18, R.drawable.special_poster_3_19, R.drawable.special_poster_3_20,
        R.drawable.special_poster_3_21, R.drawable.special_poster_3_22, R.drawable.special_poster_3_23, R.drawable.special_poster_3_24, R.drawable.special_poster_3_25,
        R.drawable.special_poster_3_26, R.drawable.special_poster_3_27, R.drawable.special_poster_3_28, R.drawable.special_poster_3_29, R.drawable.special_poster_3_30,
    )),
    DEMON_SLAYER("4", R.string.special_4, intArrayOf(
        R.drawable.special_poster_4_1, R.drawable.special_poster_4_2, R.drawable.special_poster_4_3, R.drawable.special_poster_4_4, R.drawable.special_poster_4_5,
        R.drawable.special_poster_4_6, R.drawable.special_poster_4_7, R.drawable.special_poster_4_8, R.drawable.special_poster_4_9, R.drawable.special_poster_4_10,
        R.drawable.special_poster_4_11, R.drawable.special_poster_4_12, R.drawable.special_poster_4_13, R.drawable.special_poster_4_14, R.drawable.special_poster_4_15,
        R.drawable.special_poster_4_16, R.drawable.special_poster_4_17, R.drawable.special_poster_4_18, R.drawable.special_poster_4_19, R.drawable.special_poster_4_20,
        R.drawable.special_poster_4_21, R.drawable.special_poster_4_22, R.drawable.special_poster_4_23, R.drawable.special_poster_4_24, R.drawable.special_poster_4_25,
        R.drawable.special_poster_4_26, R.drawable.special_poster_4_27, R.drawable.special_poster_4_28, R.drawable.special_poster_4_29, R.drawable.special_poster_4_30,
    )),
    JUJUTSU_KAISEN("5", R.string.special_5, intArrayOf(
        R.drawable.special_poster_5_1, R.drawable.special_poster_5_2, R.drawable.special_poster_5_3, R.drawable.special_poster_5_4, R.drawable.special_poster_5_5,
        R.drawable.special_poster_5_6, R.drawable.special_poster_5_7, R.drawable.special_poster_5_8, R.drawable.special_poster_5_9, R.drawable.special_poster_5_10,
        R.drawable.special_poster_5_11, R.drawable.special_poster_5_12, R.drawable.special_poster_5_13, R.drawable.special_poster_5_14, R.drawable.special_poster_5_15,
        R.drawable.special_poster_5_16, R.drawable.special_poster_5_17, R.drawable.special_poster_5_18, R.drawable.special_poster_5_19, R.drawable.special_poster_5_20,
        R.drawable.special_poster_5_21, R.drawable.special_poster_5_22, R.drawable.special_poster_5_23, R.drawable.special_poster_5_24, R.drawable.special_poster_5_25,
        R.drawable.special_poster_5_26, R.drawable.special_poster_5_27, R.drawable.special_poster_5_28, R.drawable.special_poster_5_29, R.drawable.special_poster_5_30,
    )),
    MY_HERO_ACADEMIA("6", R.string.special_6, intArrayOf(
        R.drawable.special_poster_6_1, R.drawable.special_poster_6_2, R.drawable.special_poster_6_3, R.drawable.special_poster_6_4, R.drawable.special_poster_6_5,
        R.drawable.special_poster_6_6, R.drawable.special_poster_6_7, R.drawable.special_poster_6_8, R.drawable.special_poster_6_9, R.drawable.special_poster_6_10,
        R.drawable.special_poster_6_11, R.drawable.special_poster_6_12, R.drawable.special_poster_6_13, R.drawable.special_poster_6_14, R.drawable.special_poster_6_15,
        R.drawable.special_poster_6_16, R.drawable.special_poster_6_17, R.drawable.special_poster_6_18, R.drawable.special_poster_6_19, R.drawable.special_poster_6_20,
        R.drawable.special_poster_6_21, R.drawable.special_poster_6_22, R.drawable.special_poster_6_23, R.drawable.special_poster_6_24, R.drawable.special_poster_6_25,
        R.drawable.special_poster_6_26, R.drawable.special_poster_6_27, R.drawable.special_poster_6_28, R.drawable.special_poster_6_29, R.drawable.special_poster_6_30,
    )),
    DEATH_NOTE("7", R.string.special_7, intArrayOf(
        R.drawable.special_poster_7_1, R.drawable.special_poster_7_2, R.drawable.special_poster_7_3, R.drawable.special_poster_7_4, R.drawable.special_poster_7_5,
        R.drawable.special_poster_7_6, R.drawable.special_poster_7_7, R.drawable.special_poster_7_8, R.drawable.special_poster_7_9, R.drawable.special_poster_7_10,
        R.drawable.special_poster_7_11, R.drawable.special_poster_7_12, R.drawable.special_poster_7_13, R.drawable.special_poster_7_14, R.drawable.special_poster_7_15,
        R.drawable.special_poster_7_16, R.drawable.special_poster_7_17, R.drawable.special_poster_7_18, R.drawable.special_poster_7_19, R.drawable.special_poster_7_20,
        R.drawable.special_poster_7_21, R.drawable.special_poster_7_22, R.drawable.special_poster_7_23, R.drawable.special_poster_7_24, R.drawable.special_poster_7_25,
        R.drawable.special_poster_7_26, R.drawable.special_poster_7_27, R.drawable.special_poster_7_28, R.drawable.special_poster_7_29, R.drawable.special_poster_7_30,
    )),
    FULLMETAL_ALCHEMIST("8", R.string.special_8, intArrayOf(
        R.drawable.special_poster_8_1, R.drawable.special_poster_8_2, R.drawable.special_poster_8_3, R.drawable.special_poster_8_4, R.drawable.special_poster_8_5,
        R.drawable.special_poster_8_6, R.drawable.special_poster_8_7, R.drawable.special_poster_8_8, R.drawable.special_poster_8_9, R.drawable.special_poster_8_10,
        R.drawable.special_poster_8_11, R.drawable.special_poster_8_12, R.drawable.special_poster_8_13, R.drawable.special_poster_8_14, R.drawable.special_poster_8_15,
        R.drawable.special_poster_8_16, R.drawable.special_poster_8_17, R.drawable.special_poster_8_18, R.drawable.special_poster_8_19, R.drawable.special_poster_8_20,
        R.drawable.special_poster_8_21, R.drawable.special_poster_8_22, R.drawable.special_poster_8_23, R.drawable.special_poster_8_24, R.drawable.special_poster_8_25,
        R.drawable.special_poster_8_26, R.drawable.special_poster_8_27, R.drawable.special_poster_8_28, R.drawable.special_poster_8_29, R.drawable.special_poster_8_30,
    )),
    DRAGON_BALL("9", R.string.special_9, intArrayOf(
        R.drawable.special_poster_9_1, R.drawable.special_poster_9_2, R.drawable.special_poster_9_3, R.drawable.special_poster_9_4, R.drawable.special_poster_9_5,
        R.drawable.special_poster_9_6, R.drawable.special_poster_9_7, R.drawable.special_poster_9_8, R.drawable.special_poster_9_9, R.drawable.special_poster_9_10,
        R.drawable.special_poster_9_11, R.drawable.special_poster_9_12, R.drawable.special_poster_9_13, R.drawable.special_poster_9_14, R.drawable.special_poster_9_15,
        R.drawable.special_poster_9_16, R.drawable.special_poster_9_17, R.drawable.special_poster_9_18, R.drawable.special_poster_9_19, R.drawable.special_poster_9_20,
        R.drawable.special_poster_9_21, R.drawable.special_poster_9_22, R.drawable.special_poster_9_23, R.drawable.special_poster_9_24, R.drawable.special_poster_9_25,
        R.drawable.special_poster_9_26, R.drawable.special_poster_9_27, R.drawable.special_poster_9_28, R.drawable.special_poster_9_29, R.drawable.special_poster_9_30,
    )),
    SWORD_ART_ONLINE("10", R.string.special_10, intArrayOf(
        R.drawable.special_poster_10_1, R.drawable.special_poster_10_2, R.drawable.special_poster_10_3, R.drawable.special_poster_10_4, R.drawable.special_poster_10_5,
        R.drawable.special_poster_10_6, R.drawable.special_poster_10_7, R.drawable.special_poster_10_8, R.drawable.special_poster_10_9, R.drawable.special_poster_10_10,
        R.drawable.special_poster_10_11, R.drawable.special_poster_10_12, R.drawable.special_poster_10_13, R.drawable.special_poster_10_14, R.drawable.special_poster_10_15,
        R.drawable.special_poster_10_16, R.drawable.special_poster_10_17, R.drawable.special_poster_10_18, R.drawable.special_poster_10_19, R.drawable.special_poster_10_20,
        R.drawable.special_poster_10_21, R.drawable.special_poster_10_22, R.drawable.special_poster_10_23, R.drawable.special_poster_10_24, R.drawable.special_poster_10_25,
        R.drawable.special_poster_10_26, R.drawable.special_poster_10_27, R.drawable.special_poster_10_28, R.drawable.special_poster_10_29, R.drawable.special_poster_10_30,
    )),
    TOKYO_GHOUL("11", R.string.special_11, intArrayOf(
        R.drawable.special_poster_11_1, R.drawable.special_poster_11_2, R.drawable.special_poster_11_3, R.drawable.special_poster_11_4, R.drawable.special_poster_11_5,
        R.drawable.special_poster_11_6, R.drawable.special_poster_11_7, R.drawable.special_poster_11_8, R.drawable.special_poster_11_9, R.drawable.special_poster_11_10,
        R.drawable.special_poster_11_11, R.drawable.special_poster_11_12, R.drawable.special_poster_11_13, R.drawable.special_poster_11_14, R.drawable.special_poster_11_15,
        R.drawable.special_poster_11_16, R.drawable.special_poster_11_17, R.drawable.special_poster_11_18, R.drawable.special_poster_11_19, R.drawable.special_poster_11_20,
        R.drawable.special_poster_11_21, R.drawable.special_poster_11_22, R.drawable.special_poster_11_23, R.drawable.special_poster_11_24, R.drawable.special_poster_11_25,
        R.drawable.special_poster_11_26, R.drawable.special_poster_11_27, R.drawable.special_poster_11_28, R.drawable.special_poster_11_29, R.drawable.special_poster_11_30,
    )),
    CHAINSAW_MAN("12", R.string.special_12, intArrayOf(
        R.drawable.special_poster_12_1, R.drawable.special_poster_12_2, R.drawable.special_poster_12_3, R.drawable.special_poster_12_4, R.drawable.special_poster_12_5,
        R.drawable.special_poster_12_6, R.drawable.special_poster_12_7, R.drawable.special_poster_12_8, R.drawable.special_poster_12_9, R.drawable.special_poster_12_10,
        R.drawable.special_poster_12_11, R.drawable.special_poster_12_12, R.drawable.special_poster_12_13, R.drawable.special_poster_12_14, R.drawable.special_poster_12_15,
        R.drawable.special_poster_12_16, R.drawable.special_poster_12_17, R.drawable.special_poster_12_18, R.drawable.special_poster_12_19, R.drawable.special_poster_12_20,
        R.drawable.special_poster_12_21, R.drawable.special_poster_12_22, R.drawable.special_poster_12_23, R.drawable.special_poster_12_24, R.drawable.special_poster_12_25,
        R.drawable.special_poster_12_26, R.drawable.special_poster_12_27, R.drawable.special_poster_12_28, R.drawable.special_poster_12_29, R.drawable.special_poster_12_30,
    )),
    SPY_X_FAMILY("13", R.string.special_13, intArrayOf(
        R.drawable.special_poster_13_1, R.drawable.special_poster_13_2, R.drawable.special_poster_13_3, R.drawable.special_poster_13_4, R.drawable.special_poster_13_5,
        R.drawable.special_poster_13_6, R.drawable.special_poster_13_7, R.drawable.special_poster_13_8, R.drawable.special_poster_13_9, R.drawable.special_poster_13_10,
        R.drawable.special_poster_13_11, R.drawable.special_poster_13_12, R.drawable.special_poster_13_13, R.drawable.special_poster_13_14, R.drawable.special_poster_13_15,
        R.drawable.special_poster_13_16, R.drawable.special_poster_13_17, R.drawable.special_poster_13_18, R.drawable.special_poster_13_19, R.drawable.special_poster_13_20,
        R.drawable.special_poster_13_21, R.drawable.special_poster_13_22, R.drawable.special_poster_13_23, R.drawable.special_poster_13_24, R.drawable.special_poster_13_25,
        R.drawable.special_poster_13_26, R.drawable.special_poster_13_27, R.drawable.special_poster_13_28, R.drawable.special_poster_13_29, R.drawable.special_poster_13_30,
    )),
    ONE_PUNCH_MAN("14", R.string.special_14, intArrayOf(
        R.drawable.special_poster_14_1, R.drawable.special_poster_14_2, R.drawable.special_poster_14_3, R.drawable.special_poster_14_4, R.drawable.special_poster_14_5,
        R.drawable.special_poster_14_6, R.drawable.special_poster_14_7, R.drawable.special_poster_14_8, R.drawable.special_poster_14_9, R.drawable.special_poster_14_10,
        R.drawable.special_poster_14_11, R.drawable.special_poster_14_12, R.drawable.special_poster_14_13, R.drawable.special_poster_14_14, R.drawable.special_poster_14_15,
        R.drawable.special_poster_14_16, R.drawable.special_poster_14_17, R.drawable.special_poster_14_18, R.drawable.special_poster_14_19, R.drawable.special_poster_14_20,
        R.drawable.special_poster_14_21, R.drawable.special_poster_14_22, R.drawable.special_poster_14_23, R.drawable.special_poster_14_24, R.drawable.special_poster_14_25,
        R.drawable.special_poster_14_26, R.drawable.special_poster_14_27, R.drawable.special_poster_14_28, R.drawable.special_poster_14_29, R.drawable.special_poster_14_30,
    )),
    HUNTER_X_HUNTER("15", R.string.special_15, intArrayOf(
        R.drawable.special_poster_15_1, R.drawable.special_poster_15_2, R.drawable.special_poster_15_3, R.drawable.special_poster_15_4, R.drawable.special_poster_15_5,
        R.drawable.special_poster_15_6, R.drawable.special_poster_15_7, R.drawable.special_poster_15_8, R.drawable.special_poster_15_9, R.drawable.special_poster_15_10,
        R.drawable.special_poster_15_11, R.drawable.special_poster_15_12, R.drawable.special_poster_15_13, R.drawable.special_poster_15_14, R.drawable.special_poster_15_15,
        R.drawable.special_poster_15_16, R.drawable.special_poster_15_17, R.drawable.special_poster_15_18, R.drawable.special_poster_15_19, R.drawable.special_poster_15_20,
        R.drawable.special_poster_15_21, R.drawable.special_poster_15_22, R.drawable.special_poster_15_23, R.drawable.special_poster_15_24, R.drawable.special_poster_15_25,
        R.drawable.special_poster_15_26, R.drawable.special_poster_15_27, R.drawable.special_poster_15_28, R.drawable.special_poster_15_29, R.drawable.special_poster_15_30,
    )),
    BLEACH("16", R.string.special_16, intArrayOf(
        R.drawable.special_poster_16_1, R.drawable.special_poster_16_2, R.drawable.special_poster_16_3, R.drawable.special_poster_16_4, R.drawable.special_poster_16_5,
        R.drawable.special_poster_16_6, R.drawable.special_poster_16_7, R.drawable.special_poster_16_8, R.drawable.special_poster_16_9, R.drawable.special_poster_16_10,
        R.drawable.special_poster_16_11, R.drawable.special_poster_16_12, R.drawable.special_poster_16_13, R.drawable.special_poster_16_14, R.drawable.special_poster_16_15,
        R.drawable.special_poster_16_16, R.drawable.special_poster_16_17, R.drawable.special_poster_16_18, R.drawable.special_poster_16_19, R.drawable.special_poster_16_20,
        R.drawable.special_poster_16_21, R.drawable.special_poster_16_22, R.drawable.special_poster_16_23, R.drawable.special_poster_16_24, R.drawable.special_poster_16_25,
        R.drawable.special_poster_16_26, R.drawable.special_poster_16_27, R.drawable.special_poster_16_28, R.drawable.special_poster_16_29, R.drawable.special_poster_16_30,
    )),
    SOLO_LEVELING("17", R.string.special_17, intArrayOf(
        R.drawable.special_poster_17_1, R.drawable.special_poster_17_2, R.drawable.special_poster_17_3, R.drawable.special_poster_17_4, R.drawable.special_poster_17_5,
        R.drawable.special_poster_17_6, R.drawable.special_poster_17_7, R.drawable.special_poster_17_8, R.drawable.special_poster_17_9, R.drawable.special_poster_17_10,
        R.drawable.special_poster_17_11, R.drawable.special_poster_17_12, R.drawable.special_poster_17_13, R.drawable.special_poster_17_14, R.drawable.special_poster_17_15,
        R.drawable.special_poster_17_16, R.drawable.special_poster_17_17, R.drawable.special_poster_17_18, R.drawable.special_poster_17_19, R.drawable.special_poster_17_20,
        R.drawable.special_poster_17_21, R.drawable.special_poster_17_22, R.drawable.special_poster_17_23, R.drawable.special_poster_17_24, R.drawable.special_poster_17_25,
        R.drawable.special_poster_17_26, R.drawable.special_poster_17_27, R.drawable.special_poster_17_28, R.drawable.special_poster_17_29, R.drawable.special_poster_17_30,
    )),
    FRIEREN("18", R.string.special_18, intArrayOf(
        R.drawable.special_poster_18_1, R.drawable.special_poster_18_2, R.drawable.special_poster_18_3, R.drawable.special_poster_18_4, R.drawable.special_poster_18_5,
        R.drawable.special_poster_18_6, R.drawable.special_poster_18_7, R.drawable.special_poster_18_8, R.drawable.special_poster_18_9, R.drawable.special_poster_18_10,
        R.drawable.special_poster_18_11, R.drawable.special_poster_18_12, R.drawable.special_poster_18_13, R.drawable.special_poster_18_14, R.drawable.special_poster_18_15,
        R.drawable.special_poster_18_16, R.drawable.special_poster_18_17, R.drawable.special_poster_18_18, R.drawable.special_poster_18_19, R.drawable.special_poster_18_20,
        R.drawable.special_poster_18_21, R.drawable.special_poster_18_22, R.drawable.special_poster_18_23, R.drawable.special_poster_18_24, R.drawable.special_poster_18_25,
        R.drawable.special_poster_18_26, R.drawable.special_poster_18_27, R.drawable.special_poster_18_28, R.drawable.special_poster_18_29, R.drawable.special_poster_18_30,
    )),
    EVANGELION("19", R.string.special_19, intArrayOf(
        R.drawable.special_poster_19_1, R.drawable.special_poster_19_2, R.drawable.special_poster_19_3, R.drawable.special_poster_19_4, R.drawable.special_poster_19_5,
        R.drawable.special_poster_19_6, R.drawable.special_poster_19_7, R.drawable.special_poster_19_8, R.drawable.special_poster_19_9, R.drawable.special_poster_19_10,
        R.drawable.special_poster_19_11, R.drawable.special_poster_19_12, R.drawable.special_poster_19_13, R.drawable.special_poster_19_14, R.drawable.special_poster_19_15,
        R.drawable.special_poster_19_16, R.drawable.special_poster_19_17, R.drawable.special_poster_19_18, R.drawable.special_poster_19_19, R.drawable.special_poster_19_20,
        R.drawable.special_poster_19_21, R.drawable.special_poster_19_22, R.drawable.special_poster_19_23, R.drawable.special_poster_19_24, R.drawable.special_poster_19_25,
        R.drawable.special_poster_19_26, R.drawable.special_poster_19_27, R.drawable.special_poster_19_28, R.drawable.special_poster_19_29, R.drawable.special_poster_19_30,
    )),
    COWBOY_BEBOP("20", R.string.special_20, intArrayOf(
        R.drawable.special_poster_20_1, R.drawable.special_poster_20_2, R.drawable.special_poster_20_3, R.drawable.special_poster_20_4, R.drawable.special_poster_20_5,
        R.drawable.special_poster_20_6, R.drawable.special_poster_20_7, R.drawable.special_poster_20_8, R.drawable.special_poster_20_9, R.drawable.special_poster_20_10,
        R.drawable.special_poster_20_11, R.drawable.special_poster_20_12, R.drawable.special_poster_20_13, R.drawable.special_poster_20_14, R.drawable.special_poster_20_15,
        R.drawable.special_poster_20_16, R.drawable.special_poster_20_17, R.drawable.special_poster_20_18, R.drawable.special_poster_20_19, R.drawable.special_poster_20_20,
        R.drawable.special_poster_20_21, R.drawable.special_poster_20_22, R.drawable.special_poster_20_23, R.drawable.special_poster_20_24, R.drawable.special_poster_20_25,
        R.drawable.special_poster_20_26, R.drawable.special_poster_20_27, R.drawable.special_poster_20_28, R.drawable.special_poster_20_29, R.drawable.special_poster_20_30,
    )),
    SAILOR_MOON("21", R.string.special_21, intArrayOf(
        R.drawable.special_poster_21_1, R.drawable.special_poster_21_2, R.drawable.special_poster_21_3, R.drawable.special_poster_21_4, R.drawable.special_poster_21_5,
        R.drawable.special_poster_21_6, R.drawable.special_poster_21_7, R.drawable.special_poster_21_8, R.drawable.special_poster_21_9, R.drawable.special_poster_21_10,
        R.drawable.special_poster_21_11, R.drawable.special_poster_21_12, R.drawable.special_poster_21_13, R.drawable.special_poster_21_14, R.drawable.special_poster_21_15,
        R.drawable.special_poster_21_16, R.drawable.special_poster_21_17, R.drawable.special_poster_21_18, R.drawable.special_poster_21_19, R.drawable.special_poster_21_20,
        R.drawable.special_poster_21_21, R.drawable.special_poster_21_22, R.drawable.special_poster_21_23, R.drawable.special_poster_21_24, R.drawable.special_poster_21_25,
        R.drawable.special_poster_21_26, R.drawable.special_poster_21_27, R.drawable.special_poster_21_28, R.drawable.special_poster_21_29, R.drawable.special_poster_21_30,
    )),
    POKEMON("22", R.string.special_22, intArrayOf(
        R.drawable.special_poster_22_1, R.drawable.special_poster_22_2, R.drawable.special_poster_22_3, R.drawable.special_poster_22_4, R.drawable.special_poster_22_5,
        R.drawable.special_poster_22_6, R.drawable.special_poster_22_7, R.drawable.special_poster_22_8, R.drawable.special_poster_22_9, R.drawable.special_poster_22_10,
        R.drawable.special_poster_22_11, R.drawable.special_poster_22_12, R.drawable.special_poster_22_13, R.drawable.special_poster_22_14, R.drawable.special_poster_22_15,
        R.drawable.special_poster_22_16, R.drawable.special_poster_22_17, R.drawable.special_poster_22_18, R.drawable.special_poster_22_19, R.drawable.special_poster_22_20,
        R.drawable.special_poster_22_21, R.drawable.special_poster_22_22, R.drawable.special_poster_22_23, R.drawable.special_poster_22_24, R.drawable.special_poster_22_25,
        R.drawable.special_poster_22_26, R.drawable.special_poster_22_27, R.drawable.special_poster_22_28, R.drawable.special_poster_22_29, R.drawable.special_poster_22_30,
    )),
    SPIRITED_AWAY("23", R.string.special_23, intArrayOf(
        R.drawable.special_poster_23_1, R.drawable.special_poster_23_2, R.drawable.special_poster_23_3, R.drawable.special_poster_23_4, R.drawable.special_poster_23_5,
        R.drawable.special_poster_23_6, R.drawable.special_poster_23_7, R.drawable.special_poster_23_8, R.drawable.special_poster_23_9, R.drawable.special_poster_23_10,
        R.drawable.special_poster_23_11, R.drawable.special_poster_23_12, R.drawable.special_poster_23_13, R.drawable.special_poster_23_14, R.drawable.special_poster_23_15,
        R.drawable.special_poster_23_16, R.drawable.special_poster_23_17, R.drawable.special_poster_23_18, R.drawable.special_poster_23_19, R.drawable.special_poster_23_20,
        R.drawable.special_poster_23_21, R.drawable.special_poster_23_22, R.drawable.special_poster_23_23, R.drawable.special_poster_23_24, R.drawable.special_poster_23_25,
        R.drawable.special_poster_23_26, R.drawable.special_poster_23_27, R.drawable.special_poster_23_28, R.drawable.special_poster_23_29, R.drawable.special_poster_23_30,
    )),
    YOUR_NAME("24", R.string.special_24, intArrayOf(
        R.drawable.special_poster_24_1, R.drawable.special_poster_24_2, R.drawable.special_poster_24_3, R.drawable.special_poster_24_4, R.drawable.special_poster_24_5,
        R.drawable.special_poster_24_6, R.drawable.special_poster_24_7, R.drawable.special_poster_24_8, R.drawable.special_poster_24_9, R.drawable.special_poster_24_10,
        R.drawable.special_poster_24_11, R.drawable.special_poster_24_12, R.drawable.special_poster_24_13, R.drawable.special_poster_24_14, R.drawable.special_poster_24_15,
        R.drawable.special_poster_24_16, R.drawable.special_poster_24_17, R.drawable.special_poster_24_18, R.drawable.special_poster_24_19, R.drawable.special_poster_24_20,
        R.drawable.special_poster_24_21, R.drawable.special_poster_24_22, R.drawable.special_poster_24_23, R.drawable.special_poster_24_24, R.drawable.special_poster_24_25,
        R.drawable.special_poster_24_26, R.drawable.special_poster_24_27, R.drawable.special_poster_24_28, R.drawable.special_poster_24_29, R.drawable.special_poster_24_30,
    )),
    VINLAND_SAGA("25", R.string.special_25, intArrayOf(
        R.drawable.special_poster_25_1, R.drawable.special_poster_25_2, R.drawable.special_poster_25_3, R.drawable.special_poster_25_4, R.drawable.special_poster_25_5,
        R.drawable.special_poster_25_6, R.drawable.special_poster_25_7, R.drawable.special_poster_25_8, R.drawable.special_poster_25_9, R.drawable.special_poster_25_10,
        R.drawable.special_poster_25_11, R.drawable.special_poster_25_12, R.drawable.special_poster_25_13, R.drawable.special_poster_25_14, R.drawable.special_poster_25_15,
        R.drawable.special_poster_25_16, R.drawable.special_poster_25_17, R.drawable.special_poster_25_18, R.drawable.special_poster_25_19, R.drawable.special_poster_25_20,
        R.drawable.special_poster_25_21, R.drawable.special_poster_25_22, R.drawable.special_poster_25_23, R.drawable.special_poster_25_24, R.drawable.special_poster_25_25,
        R.drawable.special_poster_25_26, R.drawable.special_poster_25_27, R.drawable.special_poster_25_28, R.drawable.special_poster_25_29, R.drawable.special_poster_25_30,
    )),
    MOB_PSYCHO("26", R.string.special_26, intArrayOf(
        R.drawable.special_poster_26_1, R.drawable.special_poster_26_2, R.drawable.special_poster_26_3, R.drawable.special_poster_26_4, R.drawable.special_poster_26_5,
        R.drawable.special_poster_26_6, R.drawable.special_poster_26_7, R.drawable.special_poster_26_8, R.drawable.special_poster_26_9, R.drawable.special_poster_26_10,
        R.drawable.special_poster_26_11, R.drawable.special_poster_26_12, R.drawable.special_poster_26_13, R.drawable.special_poster_26_14, R.drawable.special_poster_26_15,
        R.drawable.special_poster_26_16, R.drawable.special_poster_26_17, R.drawable.special_poster_26_18, R.drawable.special_poster_26_19, R.drawable.special_poster_26_20,
        R.drawable.special_poster_26_21, R.drawable.special_poster_26_22, R.drawable.special_poster_26_23, R.drawable.special_poster_26_24, R.drawable.special_poster_26_25,
        R.drawable.special_poster_26_26, R.drawable.special_poster_26_27, R.drawable.special_poster_26_28, R.drawable.special_poster_26_29, R.drawable.special_poster_26_30,
    )),
    BLACK_CLOVER("27", R.string.special_27, intArrayOf(
        R.drawable.special_poster_27_1, R.drawable.special_poster_27_2, R.drawable.special_poster_27_3, R.drawable.special_poster_27_4, R.drawable.special_poster_27_5,
        R.drawable.special_poster_27_6, R.drawable.special_poster_27_7, R.drawable.special_poster_27_8, R.drawable.special_poster_27_9, R.drawable.special_poster_27_10,
        R.drawable.special_poster_27_11, R.drawable.special_poster_27_12, R.drawable.special_poster_27_13, R.drawable.special_poster_27_14, R.drawable.special_poster_27_15,
        R.drawable.special_poster_27_16, R.drawable.special_poster_27_17, R.drawable.special_poster_27_18, R.drawable.special_poster_27_19, R.drawable.special_poster_27_20,
        R.drawable.special_poster_27_21, R.drawable.special_poster_27_22, R.drawable.special_poster_27_23, R.drawable.special_poster_27_24, R.drawable.special_poster_27_25,
        R.drawable.special_poster_27_26, R.drawable.special_poster_27_27, R.drawable.special_poster_27_28, R.drawable.special_poster_27_29, R.drawable.special_poster_27_30,
    )),
    BLUE_LOCK("28", R.string.special_28, intArrayOf(
        R.drawable.special_poster_28_1, R.drawable.special_poster_28_2, R.drawable.special_poster_28_3, R.drawable.special_poster_28_4, R.drawable.special_poster_28_5,
        R.drawable.special_poster_28_6, R.drawable.special_poster_28_7, R.drawable.special_poster_28_8, R.drawable.special_poster_28_9, R.drawable.special_poster_28_10,
        R.drawable.special_poster_28_11, R.drawable.special_poster_28_12, R.drawable.special_poster_28_13, R.drawable.special_poster_28_14, R.drawable.special_poster_28_15,
        R.drawable.special_poster_28_16, R.drawable.special_poster_28_17, R.drawable.special_poster_28_18, R.drawable.special_poster_28_19, R.drawable.special_poster_28_20,
        R.drawable.special_poster_28_21, R.drawable.special_poster_28_22, R.drawable.special_poster_28_23, R.drawable.special_poster_28_24, R.drawable.special_poster_28_25,
        R.drawable.special_poster_28_26, R.drawable.special_poster_28_27, R.drawable.special_poster_28_28, R.drawable.special_poster_28_29, R.drawable.special_poster_28_30,
    )),
    CODE_GEASS("29", R.string.special_29, intArrayOf(
        R.drawable.special_poster_29_1, R.drawable.special_poster_29_2, R.drawable.special_poster_29_3, R.drawable.special_poster_29_4, R.drawable.special_poster_29_5,
        R.drawable.special_poster_29_6, R.drawable.special_poster_29_7, R.drawable.special_poster_29_8, R.drawable.special_poster_29_9, R.drawable.special_poster_29_10,
        R.drawable.special_poster_29_11, R.drawable.special_poster_29_12, R.drawable.special_poster_29_13, R.drawable.special_poster_29_14, R.drawable.special_poster_29_15,
        R.drawable.special_poster_29_16, R.drawable.special_poster_29_17, R.drawable.special_poster_29_18, R.drawable.special_poster_29_19, R.drawable.special_poster_29_20,
        R.drawable.special_poster_29_21, R.drawable.special_poster_29_22, R.drawable.special_poster_29_23, R.drawable.special_poster_29_24, R.drawable.special_poster_29_25,
        R.drawable.special_poster_29_26, R.drawable.special_poster_29_27, R.drawable.special_poster_29_28, R.drawable.special_poster_29_29, R.drawable.special_poster_29_30,
    )),
    DANDADAN("30", R.string.special_30, intArrayOf(
        R.drawable.special_poster_30_1, R.drawable.special_poster_30_2, R.drawable.special_poster_30_3, R.drawable.special_poster_30_4, R.drawable.special_poster_30_5,
        R.drawable.special_poster_30_6, R.drawable.special_poster_30_7, R.drawable.special_poster_30_8, R.drawable.special_poster_30_9, R.drawable.special_poster_30_10,
        R.drawable.special_poster_30_11, R.drawable.special_poster_30_12, R.drawable.special_poster_30_13, R.drawable.special_poster_30_14, R.drawable.special_poster_30_15,
        R.drawable.special_poster_30_16, R.drawable.special_poster_30_17, R.drawable.special_poster_30_18, R.drawable.special_poster_30_19, R.drawable.special_poster_30_20,
        R.drawable.special_poster_30_21, R.drawable.special_poster_30_22, R.drawable.special_poster_30_23, R.drawable.special_poster_30_24, R.drawable.special_poster_30_25,
        R.drawable.special_poster_30_26, R.drawable.special_poster_30_27, R.drawable.special_poster_30_28, R.drawable.special_poster_30_29, R.drawable.special_poster_30_30,
    )),
    ;

    companion object {
        /** The anime named by a stored id, or null for anything else. */
        fun from(id: String?): SpecialAnime? = entries.firstOrNull { it.id == id }
    }
}

/** One anime and one poster inside it, as stored in the avatar preference. */
data class SpecialPick(val anime: SpecialAnime, val poster: Int) {
    /** What [Settings.avatar] holds while this poster is chosen. */
    val choice: String get() = "special:${anime.id}:${poster + 1}"

    @get:DrawableRes val art: Int get() = anime.posters[poster.coerceIn(0, anime.posters.lastIndex)]

    companion object {
        /**
         * The pick named by a stored choice, or null for anything else.
         *
         * The first Special pack stored `special:<anime>` with one picture per anime. That form
         * still parses, as poster 1, so an install that chose one keeps its picture after the
         * pack grows into a gallery.
         */
        fun from(choice: String?): SpecialPick? {
            if (choice == null || !choice.startsWith("special:")) return null
            val parts = choice.removePrefix("special:").split(':')
            val anime = SpecialAnime.from(parts.firstOrNull()) ?: return null
            val index = parts.getOrNull(1)?.toIntOrNull()?.minus(1) ?: 0
            return SpecialPick(anime, index.coerceIn(0, anime.posters.lastIndex))
        }
    }
}
