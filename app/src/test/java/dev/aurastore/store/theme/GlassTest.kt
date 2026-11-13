package dev.aurastore.store.theme

import androidx.compose.ui.graphics.Color
import dev.aurastore.store.data.GlassEngine
import dev.aurastore.store.data.GlassStyle
import dev.aurastore.store.data.defaultGlassStyle
import dev.aurastore.store.data.glassEngineRuns
import dev.aurastore.store.data.withDefaultMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The navbar settings' numbers, with no canvas in sight.
 *
 * Every control is a 0..1 fraction of its own range and the bar ships painting nothing, so these
 * mappings answer one question each: what does a slider at zero do, and what does it do at one. None of
 * them throws when it is wrong — a zero that still draws, a width that inverts, a corner larger than
 * the bar — so they are asserted rather than eyeballed.
 */
class GlassTest {

    @Test
    fun theShippedBarPaintsNothingAtAll() {
        val d = defaultGlassStyle()
        assertTrue(d.floating && d.liquid)
        assertEquals(GlassEngine.IOS_13, d.engine)
        assertEquals("blur at zero must not draw a copy", 0f, glassBlurDp(d.blur), 0f)
        assertEquals(0f, glassChromaticDp(d.chromatic), 0f)
        assertEquals(1f, glassRefractionScale(d.refraction), 0f)
        assertEquals(0f, glassRefractionShiftDp(d.refraction), 0f)
        assertEquals(0f, glassSheenAlpha(d.refraction), 0f)
        // Geometry, not paint: the shipped sheet is a pill (36 dp on a 68 dp bar) so first boot is
        // not a full-width slab, while every material slider still starts at zero.
        assertEquals(36f, glassCornerDp(d.corner), 0f)
        assertEquals(0f, glassBottomGapDp(d.offset), 0f)
        // Width is the one value whose shipped position is not zero: a bar at 0 is 60 % wide, and the
        // shipped bar is the full content width.
        assertEquals(1f, glassWidthFraction(d.width), 0.001f)
        for ((name, v) in listOf(
            "blur" to d.blur, "chromatic" to d.chromatic, "refraction" to d.refraction,
            "depth" to d.depth, "corner" to d.corner, "width" to d.width, "offset" to d.offset,
        )) {
            assertTrue("$name=$v is off the 0..1 scale the sliders offer", v in 0f..1f)
        }
    }

    @Test
    fun everySliderSweepsItsWholeRange() {
        assertEquals(0f, glassBlurDp(0f), 0.01f)
        assertEquals(20f, glassBlurDp(1f), 0.01f)
        assertEquals(0f, glassChromaticDp(0f), 0.001f)
        assertEquals(2.4f, glassChromaticDp(1f), 0.001f)
        assertEquals(1f, glassRefractionScale(0f), 0.0001f)
        assertEquals(1.06f, glassRefractionScale(1f), 0.0001f)
        assertEquals(0f, glassRefractionShiftDp(0f), 0.001f)
        assertEquals(16f, glassRefractionShiftDp(1f), 0.001f)
        assertEquals(0f, glassSheenAlpha(0f), 0.0001f)
        assertEquals(0.18f, glassSheenAlpha(1f), 0.0001f)
        assertEquals(0.18f, glassLensBand(0f), 0.0001f)
        assertEquals(0.70f, glassLensBand(1f), 0.0001f)
        assertEquals(0f, glassCornerDp(0f), 0.001f)
        assertEquals(36f, glassCornerDp(1f), 0.001f)
        assertEquals(0.6f, glassWidthFraction(0f), 0.0001f)
        assertEquals(1.5f, glassWidthFraction(1f), 0.0001f)
        assertEquals(0f, glassBottomGapDp(0f), 0.001f)
        assertEquals(24f, glassBottomGapDp(1f), 0.001f)
    }

    @Test
    fun outOfRangeInputCannotEscapeTheMapping() {
        assertEquals(20f, glassBlurDp(9f), 0.01f)
        assertEquals(0f, glassBlurDp(-3f), 0.01f)
        assertEquals(2.4f, glassChromaticDp(4f), 0.001f)
        assertEquals(0f, glassChromaticDp(-1f), 0.001f)
        assertEquals(1.03f, glassRefractionScale(0.5f), 0.0001f)
        assertEquals(1f, glassRefractionScale(-2f), 0.0001f)
        assertEquals(16f, glassRefractionShiftDp(4f), 0.001f)
        assertEquals(0f, glassRefractionShiftDp(-1f), 0.001f)
        assertEquals(0.18f, glassSheenAlpha(5f), 0.0001f)
        assertEquals(0f, glassSheenAlpha(-1f), 0.0001f)
        assertEquals(0.70f, glassLensBand(3f), 0.0001f)
        assertEquals(0.18f, glassLensBand(-1f), 0.0001f)
        assertEquals(36f, glassCornerDp(2f), 0.001f)
        assertEquals(0f, glassCornerDp(-2f), 0.001f)
        assertEquals(1.5f, glassWidthFraction(2f), 0.0001f)
        assertEquals(0.6f, glassWidthFraction(-2f), 0.0001f)
        assertEquals(24f, glassBottomGapDp(2f), 0.001f)
        assertEquals(0f, glassBottomGapDp(-2f), 0.001f)
    }

    @Test
    fun blurAndRefractionOnlyGrow() {
        var blur = -1f
        var band = -1f
        for (step in 0..20) {
            val v = step / 20f
            assertTrue(glassBlurDp(v) > blur)
            blur = glassBlurDp(v)
            assertTrue(glassLensBand(v) > band)
            band = glassLensBand(v)
        }
    }

    @Test
    fun theEdgeFlipsWithTheScheme() {
        // Found on the phone, not in the code: a white rim on the light scheme's near-white sheet is
        // invisible, so the light mode bar had no edge at all.
        assertEquals(Color.Black, glassEdge(Color.White).color)
        assertEquals(Color.White, glassEdge(Color.Black).color)
        assertEquals(1f, glassEdge(Color.Black).scale, 0.001f)
        assertTrue("black reads far harder than white", glassEdge(Color.White).scale < 1f)
    }

    @Test
    fun aMidGreySheetStillGetsAnEdge() {
        // The crossover is at 0.5 luminance; either answer is fine, `null` is not — an edge that is
        // neither colour means the bar has no rim at that step.
        val mid = glassEdge(Color(0xFF808080))
        assertTrue(mid.color == Color.White || mid.color == Color.Black)
        assertTrue(mid.scale > 0f)
    }

    @Test
    fun resettingTheGlassKeepsTheBarYouChose() {
        // *Reset all glass settings* restores the material, not the enable switch or the engine: a
        // flush bar must not jump back to floating, and a chosen renderer must not change, because
        // someone disliked a slider.
        val chosen = GlassStyle(
            floating = false, liquid = false, engine = GlassEngine.CLASSIC, blur = 1f,
            chromatic = 1f, refraction = 1f, depth = 1f, corner = 1f, width = 0f, offset = 1f,
        )
        val back = chosen.withDefaultMaterial()
        val d = defaultGlassStyle()
        assertFalse("the shape is not part of the look", back.floating)
        assertFalse("the enable switch is layout, not material", back.liquid)
        assertEquals("the engine is a choice about the bar, not a slider", GlassEngine.CLASSIC, back.engine)
        assertEquals(d.blur, back.blur, 0f)
        assertEquals(d.chromatic, back.chromatic, 0f)
        assertEquals(d.refraction, back.refraction, 0f)
        assertEquals(d.depth, back.depth, 0f)
        assertEquals(d.corner, back.corner, 0f)
        assertEquals(d.width, back.width, 0f)
        assertEquals(d.offset, back.offset, 0f)
    }

    @Test
    fun theCopyIsReadForEveryLayerThatNeedsOne() {
        // Blur was once the only reason to read the page, which made Refraction and the shader's
        // Chromatic Split dead sliders at Blur 0. Each engine now asks for the copy its own layers
        // consume, and Classic (whose refraction is a drawn sheen) still asks only for blur.
        assertFalse(glassReadsBackdrop(false, 1f, 1f, 1f, GlassEngine.IOS_13))
        assertTrue(glassReadsBackdrop(true, 0.05f, 0f, 0f, GlassEngine.CLASSIC))
        assertTrue(glassReadsBackdrop(true, 0f, 0.5f, 0f, GlassEngine.IOS_13))
        assertTrue(glassReadsBackdrop(true, 0f, 0f, 0.5f, GlassEngine.IOS_13))
        assertTrue(glassReadsBackdrop(true, 0f, 0.5f, 0f, GlassEngine.IOS_8))
        assertFalse(
            "Classic's refraction is drawn, not sampled",
            glassReadsBackdrop(true, 0f, 0.5f, 0f, GlassEngine.CLASSIC),
        )
        assertFalse(glassReadsBackdrop(true, 0f, 0f, 0.5f, GlassEngine.IOS_8))
        assertFalse(glassReadsBackdrop(true, 0.04f, 0f, 0f, GlassEngine.IOS_13))
        assertFalse(glassReadsBackdrop(true, 0f, 0f, 0f, GlassEngine.IOS_13))
    }

    @Test
    fun theEngineThatRunsIsTheOneThePhoneCanDraw() {
        // The shader engine only exists from API 33. Asking for it on an older phone must report the
        // drawn iOS engine, because the running chip is a statement of fact, not of preference.
        assertEquals(GlassEngine.IOS_13, glassEngineRuns(GlassEngine.IOS_13, 36))
        assertEquals(GlassEngine.IOS_13, glassEngineRuns(GlassEngine.IOS_13, 33))
        assertEquals(GlassEngine.IOS_8, glassEngineRuns(GlassEngine.IOS_13, 32))
        assertEquals(GlassEngine.IOS_8, glassEngineRuns(GlassEngine.IOS_13, 27))
        // The other two engines never resolve to something else.
        assertEquals(GlassEngine.IOS_8, glassEngineRuns(GlassEngine.IOS_8, 27))
        assertEquals(GlassEngine.CLASSIC, glassEngineRuns(GlassEngine.CLASSIC, 27))
        assertEquals(GlassEngine.CLASSIC, glassEngineRuns(GlassEngine.CLASSIC, 36))
    }
}
