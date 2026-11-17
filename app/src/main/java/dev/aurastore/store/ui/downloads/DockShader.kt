package dev.aurastore.store.ui.downloads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.aurastore.store.R
import dev.aurastore.store.data.DockAnimation

/**
 * The limited meters, drawn once in AGSL.
 *
 * The twelve limited editions used to be built from Compose paths: a circle for a tire, a triangle
 * for an arrow, a wave profile for water. At the size of the panel those read as icons. This layer
 * replaces them with a shaded scene per style - a rounded solid, a light rig, a rim, a moving
 * surface and a trail - so the actor has depth and the fill has weather. The shader is a single
 * program with a style uniform: one compile per panel, one draw per frame.
 *
 * The base wash is Compose's; this layer sits over it and returns premultiplied colour so the dark
 * solids (tire, boulder, car) stay solid instead of turning into glow. Empty pixels return nothing,
 * which keeps the unfilled track and the text bed untouched.
 */
internal val DockAnimation.shaderStyle: Int?
    get() = when (this) {
        DockAnimation.KAMEHAMEHA -> 0
        DockAnimation.TSUNAMI -> 1
        DockAnimation.FIREBALL -> 2
        DockAnimation.BOULDER -> 3
        DockAnimation.BULLET -> 4
        DockAnimation.ARROW -> 5
        DockAnimation.LASER -> 6
        DockAnimation.THUNDER -> 7
        DockAnimation.COUNTING -> 8
        DockAnimation.FLASH -> 9
        DockAnimation.WHEEL -> 10
        DockAnimation.NOS -> 11
        else -> null
    }

/** The baked NOS car, decoded once for the process rather than per card. */
private object DockCarArt {
    var car: Bitmap? = null
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        car = BitmapFactory.decodeResource(context.resources, R.drawable.car_nos)
        if (car == null) Log.e("DockShader", "nos car art failed to decode")
    }
}

/** The baked NOS arrow, decoded once for the process rather than per card. */
private object DockArrowArt {
    var arrow: Bitmap? = null
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        arrow = BitmapFactory.decodeResource(context.resources, R.drawable.arrow_nos)
        if (arrow == null) Log.e("DockShader", "nos arrow art failed to decode")
    }
}

/** The shader for a limited style, or null off the release that can compile it. */
@Composable
internal fun rememberDockLimitedShader(animation: DockAnimation): RuntimeShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    if (animation.shaderStyle == null) return null
    val context = LocalContext.current
    return remember(animation, context) {
        runCatching {
            RuntimeShader(DOCK_LIMITED_SHADER).apply {
                if (animation == DockAnimation.ARROW) {
                    DockArrowArt.load(context)
                    val arrow = DockArrowArt.arrow
                    if (arrow == null) return@runCatching null
                    setInputShader("arrowTex", BitmapShader(arrow, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                    setFloatUniform("arrowSize", arrow.width.toFloat(), arrow.height.toFloat())
                }
                if (animation == DockAnimation.NOS) {
                    DockCarArt.load(context)
                    val car = DockCarArt.car
                    if (car == null) return@runCatching null
                    setInputShader("carTex", BitmapShader(car, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                    setFloatUniform("carSize", car.width.toFloat(), car.height.toFloat())
                }
            }
        }
            .onFailure { Log.e("DockShader", "limited shader failed to compile", it) }
            .getOrNull()
    }
}

/**
 * AGSL for the limited styles.
 *
 * Every scene is written in the same camera: the card is a dark room, the fill is the room's light,
 * and the actor stands at the byte front. Helpers are shared; the twelve `styleN` functions are the
 * looks. Colour is returned premultiplied because the layer is drawn straight over the wash rather
 * than added to it, which is what lets a tire be black and a beam be white in the same frame.
 */
internal const val DOCK_LIMITED_SHADER = """
uniform float2 size;
uniform float fill;
uniform float phase;
uniform float style;
uniform float percent;
uniform float live;
uniform shader carTex;
uniform float2 carSize;
uniform shader arrowTex;
uniform float2 arrowSize;

const float PI = 3.14159265;
const float TAU = 6.2831853;

float sat(float x) { return clamp(x, 0.0, 1.0); }
float3 sat3(float3 c) { return clamp(c, float3(0.0), float3(1.0)); }
float lum(float3 c) { return dot(c, float3(0.299, 0.587, 0.114)); }

float hash11(float n) { return fract(sin(n * 127.1 + 311.7) * 43758.5453); }
float hash21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash21(i), hash21(i + float2(1.0, 0.0)), u.x),
        mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

float2 rot(float2 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return float2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float sdCircle(float2 p, float r) { return length(p) - r; }
float sdBox(float2 p, float2 b) {
    float2 d = abs(p) - b;
    return length(max(d, float2(0.0))) + min(max(d.x, d.y), 0.0);
}
float sdRoundBox(float2 p, float2 b, float r) { return sdBox(p, b - float2(r)) - r; }
float sdSegment(float2 p, float2 a, float2 b) {
    float2 pa = p - a;
    float2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
    return length(pa - ba * h);
}
float sdCapsule(float2 p, float2 a, float2 b, float r) { return sdSegment(p, a, b) - r; }
float sdTriangle(float2 p, float2 p0, float2 p1, float2 p2) {
    float2 e0 = p1 - p0;
    float2 e1 = p2 - p1;
    float2 e2 = p0 - p2;
    float2 v0 = p - p0;
    float2 v1 = p - p1;
    float2 v2 = p - p2;
    float2 pq0 = v0 - e0 * sat(dot(v0, e0) / max(dot(e0, e0), 0.0001));
    float2 pq1 = v1 - e1 * sat(dot(v1, e1) / max(dot(e1, e1), 0.0001));
    float2 pq2 = v2 - e2 * sat(dot(v2, e2) / max(dot(e2, e2), 0.0001));
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    float2 d = min(
        min(float2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
            float2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
        float2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x))
    );
    return -sqrt(d.x) * sign(d.y);
}

float glow(float d, float r) { return exp(-(d * d) / max(r * r, 0.0001)); }
float rim(float d, float w) { return 1.0 - smoothstep(0.0, w, abs(d)); }
float fillMask(float x, float edge, float soft) {
    return 1.0 - smoothstep(edge - soft, edge + soft, x);
}

float4 over(float4 dst, float3 c, float a) {
    a = sat(a);
    float outA = a + dst.a * (1.0 - a);
    float3 outC = (c * a + dst.rgb * dst.a * (1.0 - a)) / max(outA, 0.0001);
    return float4(outC, outA);
}

// A bitmap shader hands back premultiplied colour; divide it out so the scene can keep one
// straight-alpha composite for every layer.
float4 overSpr(float4 dst, float4 src) {
    float a = sat(src.a);
    return over(dst, src.rgb / max(a, 0.0001), a);
}

float3 metal(float t) {
    float s = pow(sat(t), 5.0);
    return mix(float3(0.35, 0.33, 0.30), float3(1.0, 0.97, 0.90), s) +
        float3(0.55, 0.52, 0.46) * (1.0 - abs(t - 0.62) * 2.0) * 0.35;
}

// ---------------------------------------------------------------------------------------------
// 0. Kamehameha: a fixed half-dome at the front, a charged beam down the fill, ionised rings.
// ---------------------------------------------------------------------------------------------

float4 styleKame(float2 p, float w, float h, float edge, float t) {
    float4 res = float4(0.0);
    float inside = fillMask(p.x, edge, 1.5);
    float mid = h * 0.5;
    float2 c = float2(edge, mid);
    float2 q = p - c;
    float d = length(q);
    float r = h * 0.52;

    float body = inside * (0.08 + 0.05 * sin(p.y * 0.055 - t * 3.0) + 0.05 * fbm(p * 0.025 + float2(-t * 0.2, 0.0)));
    res = over(res, float3(0.08, 0.48, 0.95), body);

    float beamW = h * 0.110;
    float beam = exp(-pow((p.y - mid) / beamW, 2.0)) * inside;
    float flick = 0.72 + 0.28 * sin(t * 4.0 + p.x * 0.04);
    res = over(res, float3(0.25, 0.72, 1.0), beam * 0.34 * flick);
    res = over(res, float3(0.85, 0.98, 1.0), beam * beam * 0.72 * flick);

    // The wake is the fireball's comet in blue: broader than the beam at the orb, narrowing and
    // cooling as it runs back, with a hot core inside the sheath. Without the core the beam was a
    // straight rod; without the sheath the trail had no body.
    float back = edge - p.x;
    float wakeT = sat(back / (h * 2.2));
    float sway = sin(back * 0.016 + t * 3.0) * h * 0.045 * wakeT;
    float wakeW = h * (0.34 * (1.0 - wakeT) + 0.07);
    float coreW = h * (0.11 * (1.0 - wakeT) + 0.022);
    float wakeN = 0.74 + 0.38 * fbm(float2(p.x * 0.018 - t * 1.2, p.y * 0.06));
    float wake = exp(-pow((p.y - mid - sway) / wakeW, 2.0)) * pow(1.0 - wakeT, 1.3) * inside;
    float core = exp(-pow((p.y - mid - sway) / coreW, 2.0)) * pow(1.0 - wakeT, 1.9) * inside;
    res = over(res, mix(float3(0.06, 0.40, 1.0), float3(0.50, 0.90, 1.0), pow(1.0 - wakeT, 2.0)), wake * 0.42 * wakeN);
    res = over(res, mix(float3(0.55, 0.92, 1.0), float3(0.90, 1.0, 1.0), pow(1.0 - wakeT, 3.0)), core * 0.50 * wakeN);

    float dome = (1.0 - smoothstep(r - 2.5, r, d)) * step(0.0, q.x);
    res = over(res, float3(0.10, 0.55, 1.0), dome * 0.30);
    res = over(res, float3(0.60, 0.94, 1.0), rim(d - r * 0.84, 2.2) * 0.55);
    res = over(res, float3(1.0), glow(d, r * 0.26) * 0.92);

    float ring = rim(d - r * (0.52 + 0.06 * sin(t * 2.2)), 1.6) * 0.42;
    res = over(res, float3(0.45, 0.92, 1.0), ring);

    float ang = atan(q.y, q.x);
    float arc = rim(d - r * 0.95 + sin(ang * 9.0 + t * 3.0) * h * 0.022, 1.3);
    res = over(res, float3(0.92, 1.0, 1.0), arc * 0.45 * inside);

    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        float seed = hash11(fi + floor(t * 3.0) * 7.0);
        float2 sp = float2(edge - h * (0.15 + seed * 0.85), h * (0.12 + hash11(fi * 3.1 + 1.0) * 0.76));
        float2 sv = float2(-1.0, (hash11(fi + 2.0) - 0.5) * 0.8) * (1.0 - fract(t * 2.0 + seed));
        float spark = glow(length(p - sp), h * 0.012);
        float tail = glow(sdSegment(p, sp, sp + sv * h * 0.20), h * 0.006);
        res = over(res, float3(0.9, 0.98, 1.0), (spark + tail * 0.4) * 0.65);
    }
    return res;
}

// ---------------------------------------------------------------------------------------------
// 1. Tsunami: a curling wall at the front, caustics in the body, foam and spray.
// ---------------------------------------------------------------------------------------------

float4 styleTsunami(float2 p, float w, float h, float edge, float t) {
    float4 res = float4(0.0);
    float inside = fillMask(p.x, edge, 1.5);
    float ny = p.y / h;

    res = over(res, float3(0.02, 0.20, 0.33), inside * 0.42);
    float ca = fbm(p * 0.028 + float2(-t * 0.55, t * 0.22));
    float caustic = pow(sat(ca * 1.35 - 0.22), 3.0);
    res = over(res, float3(0.25, 0.85, 0.88), caustic * inside * 0.30);

    float lean = smoothstep(0.0, 0.85, ny);
    float crest = edge - h * 0.30 * lean + sin(ny * 8.5 + t * 1.6) * h * 0.028;
    float wall = 1.0 - smoothstep(h * 0.02, h * 0.15, abs(p.x - crest - h * 0.035));
    float slope = cos(ny * 8.5 + t * 1.6);
    float wallLit = 0.55 + 0.45 * slope;
    res = over(res, float3(0.10, 0.52, 0.68) * wallLit, wall * 0.72);

    float lip = rim(p.x - crest, h * 0.012);
    res = over(res, float3(0.78, 0.98, 1.0), lip * (0.35 + 0.65 * lean) * 0.85);

    float foamNoise = fbm(p * 0.07 + float2(0.0, -t * 2.4));
    float foam = smoothstep(0.48, 0.78, foamNoise) *
        (1.0 - smoothstep(h * 0.02, h * 0.34, ny)) *
        (1.0 - smoothstep(h * 0.03, h * 0.16, abs(p.x - crest - h * 0.04)));
    res = over(res, float3(0.92, 1.0, 1.0), foam * 0.85);

    // Successive swells behind the wall. One crest at the byte front was a single curling lip; the
    // sea only reads when there are wave faces receding into the filled part of the card. Four
    // crests, each leaning further back, give the fill a slope instead of a flat blue panel.
    for (int k = 1; k <= 4; k++) {
        float fk = float(k);
        float kCrest = crest - h * 0.30 * fk +
            sin(ny * 6.6 + t * 1.25 + fk * 1.9) * h * (0.030 + 0.010 * fk);
        float fade = (0.34 - 0.05 * fk) * (0.30 + 0.70 * lean) * inside;
        float swell = exp(-pow((p.x - kCrest + h * 0.060) / (h * 0.105), 2.0));
        res = over(res, float3(0.07, 0.40, 0.58), swell * fade);
        float kLip = rim(p.x - kCrest, h * 0.011);
        res = over(res, float3(0.66, 0.96, 1.0), kLip * fade * 1.2);
        float kFoam = smoothstep(0.50, 0.78, fbm(float2(p.x * 0.07 + fk * 11.0, p.y * 0.07 - t * 2.0)));
        res = over(res, float3(0.92, 1.0, 1.0), kLip * kFoam * fade);
    }

    for (int i = 0; i < 14; i++) {
        float fi = float(i);
        float seed = hash11(fi * 2.7 + floor(t * 2.0));
        float2 sp = float2(crest - h * (0.02 + seed * 0.5), h * (0.05 + hash11(fi + 4.0) * 0.4));
        float2 sv = float2(-0.18 - seed * 0.25, -0.55 + seed * 0.3);
        float drop = glow(length(p - sp - sv * h * 0.10), h * 0.008);
        res = over(res, float3(0.92, 1.0, 1.0), drop * 0.62);
    }
    return res;
}

// ---------------------------------------------------------------------------------------------
// 2. Fireball: a rolling, noisy sphere with a hot core and a comet tail.
// ---------------------------------------------------------------------------------------------

float4 styleFire(float2 p, float w, float h, float edge, float t) {
    float4 res = float4(0.0);
    float inside = fillMask(p.x, edge, 1.5);
    float mid = h * 0.5;
    float r = h * 0.485;
    float2 c = float2(edge - h * 0.035, mid);
    float2 q = (p - c) / r;
    float d = length(q);

    // The comet. Two layers: a soft sheath the width of the flame, and a hot core inside it that
    // narrows faster than the sheath and dies sooner. A single wide band looked like a painted
    // stripe. The core is what gives the trail a direction and a decreasing thickness.
    float back = edge - p.x;
    float tailT = sat(back / (h * 1.9));
    float sway = sin(back * 0.020 - t * 4.0) * h * 0.050 * tailT;
    float sheathW = h * (0.33 * (1.0 - tailT) + 0.06);
    float coreW = h * (0.105 * (1.0 - tailT) + 0.016);
    float flame = fbm(float2(back * 0.024 + t * 1.2, p.y * 0.07));
    float dy = p.y - mid - sway;
    float sheath = exp(-pow(dy / sheathW, 2.0)) * pow(1.0 - tailT, 1.15) * inside;
    float core = exp(-pow(dy / coreW, 2.0)) * pow(1.0 - tailT, 1.9) * inside;
    res = over(res, mix(float3(0.92, 0.18, 0.01), float3(0.38, 0.02, 0.005), tailT), sheath * 0.42);
    res = over(res, float3(1.0, 0.52, 0.05), sheath * flame * (1.0 - tailT) * 0.42);
    res = over(res, mix(float3(1.0, 0.72, 0.18), float3(0.85, 0.24, 0.02), tailT), core * 0.72);
    res = over(res, float3(1.0, 0.94, 0.72), core * core * (1.0 - tailT) * 0.55);

    if (d < 1.06) {
        float z = sqrt(max(0.0, 1.0 - d * d));
        float n = fbm(q * 4.3 + float2(-t * 0.7, t * 0.4));
        float3 nrm = normalize(float3(q * (0.72 + 0.45 * n), z + 0.001));
        float3 lightDir = normalize(float3(-0.55, 0.55, 0.64));
        float diff = max(dot(nrm, lightDir), 0.0);
        float spec = pow(max(dot(reflect(-lightDir, nrm), float3(0.0, 0.0, 1.0)), 0.0), 26.0);
        float heat = fbm(q * 7.0 + float2(-t * 1.5, t * 0.7) + n);
        float3 col = mix(float3(0.45, 0.02, 0.0), float3(1.0, 0.30, 0.015), heat);
        col = mix(col, float3(1.0, 0.78, 0.22), pow(diff, 2.2));
        col += float3(1.0, 0.92, 0.65) * spec * 0.9;
        float a = 1.0 - smoothstep(0.88, 1.0, d);
        res = over(res, col, a);
    }

    float halo = glow(length(p - c) - r * 1.02, h * 0.20);
    res = over(res, float3(1.0, 0.32, 0.03), halo * 0.34);

    for (int i = 0; i < 16; i++) {
        float fi = float(i);
        float seed = hash11(fi * 4.1);
        float travel = fract(t * 0.42 + seed);
        float2 ep = float2(edge - h * (0.15 + travel * 1.05), mid + (hash11(fi + 8.0) - 0.5) * h * 0.72);
        ep.y += sin(t * 3.0 + fi) * h * 0.03;
        float ember = glow(length(p - ep), h * 0.010) * (1.0 - travel);
        res = over(res, float3(1.0, 0.55 + seed * 0.3, 0.12), ember * 0.85);
    }
    return res;
}

// ---------------------------------------------------------------------------------------------
// 3. Boulder: a faceted rock, a leaning silhouette and a dust plume.
// ---------------------------------------------------------------------------------------------

float4 styleBoulder(float2 p, float w, float h, float edge, float t) {
    float4 res = float4(0.0);
    float inside = fillMask(p.x, edge, 1.5);
    float mid = h * 0.52;
    float r = h * 0.46;
    float2 c = float2(edge - h * 0.09, mid);
    float2 q = (p - c) / r;
    // The rock texture turns slowly, so the boulder reads as being ground forward by the walker.
    float2 qr = rot(q, -t * 0.30);
    float rockNoise = fbm(qr * 2.6 + 7.0);
    float d = length(q) - (0.86 + 0.13 * rockNoise);

    float dust = fbm(p * 0.035 + float2(-t * 0.25, t * 0.12));
    res = over(res, float3(0.28, 0.24, 0.20), inside * 0.25);
    res = over(res, float3(0.55, 0.48, 0.38), pow(sat(dust * 1.3 - 0.15), 3.0) * inside * 0.35);

    if (d < 0.03) {
        float z = sqrt(max(0.0, 1.0 - sat(length(q)) * sat(length(q))));
        float3 nrm = normalize(float3(q * 0.9, z + 0.18));
        float3 lightDir = normalize(float3(-0.5, -0.55, 0.66));
        float diff = max(dot(nrm, lightDir), 0.0);
        float rimLight = pow(1.0 - max(dot(nrm, float3(0.0, 0.0, 1.0)), 0.0), 2.2);
        float3 col = mix(float3(0.11, 0.10, 0.09), float3(0.62, 0.54, 0.43), diff);
        col += float3(1.0, 0.82, 0.56) * rimLight * 0.23;
        float facet = pow(sat(fbm(qr * 6.0 + 3.0)), 2.0);
        col *= 0.82 + 0.30 * facet;
        float a = 1.0 - smoothstep(0.0, 0.03, d);
        res = over(res, col, a);
        float crack = 1.0 - smoothstep(0.0, 0.015, abs(fbm(qr * 5.0 + 21.0) - 0.5));
        res = over(res, float3(0.06, 0.05, 0.04), crack * a * 0.7);
    }

    // The figure walks: the legs scissor, one foot lifts while the other plants, the body dips on
    // each landing, and the free arm swings against the stride. The pushing hand stays on the rock
    // so the gait reads as effort rather than as a man jogging beside a boulder.
    float gait = sin(t * 5.0);
    float stride = h * 0.050 * gait;
    float liftA = max(0.0, gait) * h * 0.030;
    float liftB = max(0.0, -gait) * h * 0.030;
    float bob = h * 0.007 * (1.0 - cos(t * 10.0)) * 0.5;
    float2 f = p - float2(edge - h * 0.64, mid + h * 0.12 + bob);
    float head = sdCircle(f + float2(0.0, -h * 0.16), h * 0.045);
    float torso = sdCapsule(f, float2(0.0, -h * 0.10), float2(-h * 0.06, h * 0.05), h * 0.038);
    float2 hip = float2(-h * 0.03, h * 0.04);
    float2 footA = float2(-h * 0.13 + stride, h * 0.21 - liftA);
    float2 footB = float2(h * 0.05 - stride, h * 0.20 - liftB);
    float legA = sdCapsule(f, hip, footA, h * 0.030);
    float legB = sdCapsule(f, hip, footB, h * 0.030);
    float2 shoulder = float2(-h * 0.03, -h * 0.07);
    float2 hand = float2(h * 0.14, -h * 0.02 + sin(t * 5.0 + 1.0) * h * 0.007);
    float arm = sdCapsule(f, shoulder, hand, h * 0.026);
    float armB = sdCapsule(f, float2(-h * 0.06, -h * 0.05), float2(-h * 0.12 - stride * 0.6, h * 0.05), h * 0.022);
    float figure = min(min(head, torso), min(min(arm, armB), min(legA, legB)));
    res = over(res, float3(0.05, 0.045, 0.04), (1.0 - smoothstep(0.0, 4.0, figure)) * 0.92);
    res = over(res, float3(1.0, 0.80, 0.54), rim(figure, 2.2) * 0.40);

    for (int i = 0; i < 10; i++) {
        float fi = float(i);
        float seed = hash11(fi * 5.3 + 2.0);
        float2 dp = float2(edge - h * (0.2 + fract(t * 0.18 + seed) * 0.85),
                           h * (0.42 + (hash11(fi + 3.0) - 0.5) * 0.42));
        float cloud = glow(length(p - dp), h * (0.07 + seed * 0.06));
        res = over(res, float3(0.72, 0.64, 0.54), cloud * 0.22);
    }
    return res;
}

// ---------------------------------------------------------------------------------------------
// 4. Bullet: a brass round at the front, a spiral vapour trail and a shock ring.
// ---------------------------------------------------------------------------------------------

float4 styleBullet(float2 p, float w, float h, float edge, float t) {
    float4 res = float4(0.0);
    float inside = fillMask(p.x, edge, 1.5);
    float mid = h * 0.5;

    res = over(res, float3(0.24, 0.22, 0.20), inside * 0.16);

    // Double helix vapour: two strands in antiphase, wide at the muzzle and thinning with distance.
    // The old swirl was a radial fan near the case, which read as a shadow rather than a trail.
    // The phase runs minus time: the coils have to unwind away from the round as it drills forward.
    // Running plus time pulled the spirals into the nose, which read as the trail moving backwards.
    float back = edge - p.x;
    float fade = 1.0 - smoothstep(h * 2.0, h * 3.0, back);
    float wrist = sat(back / (h * 0.40));
    for (int s = 0; s < 2; s++) {
        float fs = float(s);
        float amp = h * 0.20 * wrist * fade;
        float y = mid + sin(back * 0.085 - t * 7.0 + fs * PI) * amp;
        float width = h * (0.012 - 0.007 * sat(back / (h * 2.6)));
        float strand = glow(abs(p.y - y), max(width, 0.5)) * fade * inside;
        res = over(res, float3(0.80, 0.52, 0.24), strand * 0.40);
        res = over(res, float3(1.0, 0.82, 0.48), pow(strand, 2.0) * 0.38);
    }
    for (int v = 0; v < 2; v++) {
        float fv = float(v);
        float amp = h * 0.30 * wrist * fade;
        float y = mid + sin(back * 0.052 - t * 6.0 + fv * PI + 0.6) * amp;
        float mist = glow(abs(p.y - y), h * 0.045) * pow(fade, 2.0) * inside;
        res = over(res, float3(0.40, 0.30, 0.22), mist * 0.10);
    }

    // A sniper round: long sharp point, straight jacket, boat-tail. It is slimmer and brighter than
    // the old stub: a fat capsule read as a lozenge, and the nose has to be the rightmost paint.
    float tipX = edge - h * 0.01;
    float2 noseBase = float2(tipX - h * 0.24, mid);
    float2 tip = float2(tipX, mid);
    float2 noseTop = float2(tipX - h * 0.24, mid - h * 0.062);
    float2 noseBottom = float2(tipX - h * 0.24, mid + h * 0.062);
    float nose = sdTriangle(p, tip, noseTop, noseBottom);
    float jacket = sdCapsule(p, float2(tipX - h * 0.40, mid), noseBase, h * 0.062);
    float boat = sdCapsule(p, float2(tipX - h * 0.50, mid), float2(tipX - h * 0.38, mid), h * 0.040);
    float bullet = min(nose, min(jacket, boat));
    float copperBand = sat(0.5 - (p.y - mid) / (h * 0.124));
    float3 jacketCol = mix(float3(0.26, 0.13, 0.05), float3(1.0, 0.72, 0.32), copperBand);
    res = over(res, jacketCol, 1.0 - smoothstep(0.0, 2.0, bullet));
    // The underside sits in shade so the round reads as a cylinder, not a flat cut-out.
    float shade = smoothstep(-h * 0.020, h * 0.062, p.y - mid) * 0.45;
    res = over(res, float3(0.06, 0.03, 0.01), shade * (1.0 - smoothstep(0.0, 2.0, bullet)));
    float spec = exp(-pow((p.y - mid + h * 0.034) / (h * 0.010), 2.0)) *
        (1.0 - smoothstep(0.0, 3.0, bullet));
    res = over(res, float3(1.0, 0.95, 0.78), spec * 0.95);
    float seam = exp(-pow((p.x - noseBase.x) / (h * 0.006), 2.0)) *
        (1.0 - smoothstep(h * 0.052, h * 0.064, abs(p.y - mid)));
    res = over(res, float3(0.30, 0.15, 0.05), seam * 0.80);
    float groove = exp(-pow((p.x - (tipX - h * 0.355)) / (h * 0.005), 2.0)) *
        (1.0 - smoothstep(h * 0.052, h * 0.064, abs(p.y - mid)));
    res = over(res, float3(1.0, 0.80, 0.40), groove * 0.65);
    res = over(res, float3(0.12, 0.06, 0.02), rim(bullet, 1.4) * 0.70);
    res = over(res, float3(1.0, 0.97, 0.84), glow(length(p - tip), h * 0.014) * 0.70);
    return res;
}

// ---------------------------------------------------------------------------------------------
// 5. Arrow: a gold shaft, fletching at mid-depth and a speed arc.
// ---------------------------------------------------------------------------------------------

float4 styleArrow(float2 p, float w, float h, float edge, float t) {
    float4 res = float4(0.0);
    float inside = fillMask(p.x, edge, 1.5);
    float mid = h * 0.5;
    float bob = sin(t * 5.0) * h * 0.004;

    // The shaft is a baked side elevation of a real broadhead arrow: steel blade with ground
    // bevels, brass ferrule, carbon shaft and four feather vanes. It carries its own light, so
    // the scene only places it, rolls it at speed and lays the wake behind the nock.
    // The sheet is 800x125: nose at 0.98077 of the width, shaft line at 0.47265 of the height.
    float L = h * 1.14;
    float SW = L / 0.96077;
    float texel = SW / max(arrowSize.x, 1.0);
    float SH = SW * arrowSize.y / max(arrowSize.x, 1.0);
    float xN = edge - h * 0.02;
    float left = xN - SW * 0.98077;
    float top = mid + bob - SH * 0.47265;
    float xT = left + SW * 0.020;

    res = over(res, float3(0.24, 0.17, 0.08), inside * 0.16);

    // The wake: a warm haze on the flight line and gold filaments peeling off the nock.
    float back = edge - p.x;
    float wake = exp(-pow((p.y - mid - bob) / (h * 0.075), 2.0)) *
        (1.0 - smoothstep(h * 0.20, h * 2.40, back)) * inside;
    res = over(res, float3(0.85, 0.55, 0.18), wake * 0.14);
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float run = fract(t * 1.3 + hash11(fi + 21.0));
        float yy = mid + bob + (hash11(fi * 2.7 + 3.0) - 0.5) * h * 0.26;
        float x = xT - h * (0.02 + run * 1.00);
        float len = h * (0.14 + hash11(fi + 8.0) * 0.36);
        float drift = (yy - mid - bob) * 0.06 * run;
        float streak = glow(sdSegment(p, float2(x, yy), float2(x + len, yy + drift)), h * 0.0055);
        res = over(res, float3(1.0, 0.76, 0.32), streak * (1.0 - run) * 0.30 * inside);
    }

    float2 uv = (p - float2(left, top)) / texel;
    if (arrowSize.x > 1.0 && uv.x >= 0.0 && uv.y >= 0.0 &&
        uv.x < arrowSize.x && uv.y < arrowSize.y) {
        res = overSpr(res, arrowTex.eval(uv) * inside);
    }

    // The point burns and throws a small flare ahead of the blade.
    float tipGlow = glow(length(p - float2(xN - h * 0.02, mid + bob)), h * 0.030);
    res = over(res, float3(1.0, 0.92, 0.72), tipGlow * 0.45 * inside);
    return res;
}

// ---------------------------------------------------------------------------------------------
// 6. Laser: a hot beam, a star flare and a shimmering heat trail.
// ---------------------------------------------------------------------------------------------

float4 styleLaser(float2 p, float w, float h, float edge, float t) {
    float4 res = float4(0.0);
    float inside = fillMask(p.x, edge, 1.5);
    float mid = h * 0.5;

    res = over(res, float3(0.16, 0.02, 0.04), inside * 0.30);
    float shimmer = fbm(float2(p.x * 0.05 - t * 1.6, p.y * 0.12));
    float heat = exp(-pow((p.y - mid) / (h * (0.08 + 0.03 * shimmer)), 2.0)) * inside;
    res = over(res, float3(0.95, 0.10, 0.16), heat * 0.42);
    res = over(res, float3(1.0, 0.55, 0.55), pow(heat, 2.0) * 0.35);

    float beam = exp(-pow((p.y - mid) / (h * 0.027), 2.0)) * inside;
    res = over(res, float3(1.0, 0.18, 0.20), beam * 0.80);
    res = over(res, float3(1.0, 0.94, 0.94), pow(beam, 3.0) * 0.95);

    float2 q = p - float2(edge, mid);
    float d = length(q);
    float ang = atan(q.y, q.x);
    float star = pow(abs(cos(ang * 3.0)), 24.0) * glow(d, h * 0.55) * 0.55;
    star += pow(abs(cos(ang * 3.0 + PI * 0.5)), 24.0) * glow(d, h * 0.32) * 0.35;
    res = over(res, float3(1.0, 0.36, 0.30), star);

    float vertical = exp(-pow((p.x - edge) / (h * 0.016), 2.0)) * exp(-abs(p.y - mid) / (h * 0.42));
    res = over(res, float3(1.0, 0.30, 0.34), vertical * 0.55);
    res = over(res, float3(1.0), glow(d, h * 0.055) * 0.95);

    for (int i = 0; i < 8; i++) {
        float fi = float(i);
        float run = fract(t * 1.8 + hash11(fi + 3.0));
        float x = edge - h * (0.05 + run * 0.95);
        float yy = mid + sin(t * 8.0 + fi) * h * 0.02;
        res = over(res, float3(1.0, 0.45, 0.38),
            glow(length(p - float2(x, yy)), h * 0.02) * (1.0 - run) * 0.35);
    }
    return res;
}

// ---------------------------------------------------------------------------------------------
// 7. Thunder: a plasma front, branching bolts and a dark electric room.
// ---------------------------------------------------------------------------------------------

float boltLine(float2 p, float h, float t, float seed, float amp) {
    // Each slot owns one slanted segment between its own two jittered heights, so the chain
    // connects into a ragged bolt instead of a row of floating dashes. The time step keeps the
    // bolt re-striking in place.
    float stepX = max(h * 0.18, 1.0);
    float slot = floor(p.x / stepX);
    float strike = floor(t * 4.0);
    float y0 = h * 0.5 + (hash11(slot + seed + strike * 9.0) - 0.5) * h * amp;
    float y1 = h * 0.5 + (hash11(slot + 1.0 + seed + strike * 9.0) - 0.5) * h * amp;
    float2 a = float2(slot * stepX, y0);
    float2 b = float2((slot + 1.0) * stepX, y1);
    float w = h * 0.006 + h * 0.010 * hash11(slot + seed * 2.0);
    return 1.0 - smoothstep(0.0, w, sdSegment(p, a, b));
}

float4 styleThunder(float2 p, float w, float h, float edge, float t) {
    float4 res = float4(0.0);
    float inside = fillMask(p.x, edge, 1.5);
    float mid = h * 0.5;
    float flick = 0.60 + 0.40 * hash11(floor(phase * 14.0) + 2.0);

    res = over(res, float3(0.04, 0.05, 0.13), inside * 0.34);
    float haze = fbm(p * 0.03 + float2(-t * 0.2, t * 0.3));
    res = over(res, float3(0.20, 0.28, 0.65), inside * haze * 0.18);

    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float seed = fi * 7.0 + 1.0;
        float bolt = boltLine(p, h, t, seed, 0.30);
        float len = 0.55 + hash11(seed + 3.0) * 0.65;
        float span = smoothstep(edge - h * len - h * 0.10, edge - h * len + h * 0.20, p.x);
        // A bolt lives behind the byte front. Without the fill mask the chain ran on over the
        // empty track, which read as lightning in the part that had not downloaded yet.
        bolt *= span * inside;
        res = over(res, float3(0.30, 0.55, 1.0), bolt * 0.10 * flick);
        res = over(res, float3(0.85, 0.94, 1.0), pow(bolt, 5.0) * 0.95 * flick);
    }

    float2 forkBase = float2(edge - h * 0.55, mid);
    for (int j = 0; j < 3; j++) {
        float fj = float(j);
        float y = mid + (fj - 1.0) * h * 0.22;
        float2 a = float2(edge - h * 0.25, y);
        float2 b = float2(edge - h * (0.65 + fj * 0.08), y + (hash11(fj + 5.0) - 0.5) * h * 0.22);
        float fork = 1.0 - smoothstep(0.0, h * 0.006, sdSegment(p, a, b));
        res = over(res, float3(0.75, 0.88, 1.0), fork * 0.55 * flick);
    }

    float2 c = float2(edge, mid);
    float d = length(p - c);
    float plasma = glow(d, h * 0.16) * 0.55;
    res = over(res, float3(0.45, 0.72, 1.0), plasma);
    res = over(res, float3(1.0), glow(d, h * 0.045) * 0.95);

    for (int k = 0; k < 12; k++) {
        float fk = float(k);
        float seed = hash11(fk * 6.1 + 1.0);
        float2 sp = float2(edge - h * (0.1 + fract(t * 1.3 + seed) * 0.9),
                           h * (0.08 + hash11(fk + 7.0) * 0.84));
        res = over(res, float3(0.7, 0.9, 1.0), glow(length(p - sp), h * 0.009) * 0.5);
    }
    return res;
}

// ---------------------------------------------------------------------------------------------
// 8. Counting: seven-segment digits at the front, older counts trailing in the fill.
// ---------------------------------------------------------------------------------------------

float segmentDigit(float2 p, int digit, float h) {
    float w = h * 0.050;
    float hw = h * 0.225;
    float hh = h * 0.40;
    float gap = h * 0.020;
    float top = 1.0 - smoothstep(0.0, 2.0, sdRoundBox(p - float2(0.0, -hh), float2(hw, w), w));
    float mid = 1.0 - smoothstep(0.0, 2.0, sdRoundBox(p - float2(0.0, 0.0), float2(hw, w), w));
    float bot = 1.0 - smoothstep(0.0, 2.0, sdRoundBox(p - float2(0.0, hh), float2(hw, w), w));
    float tl = 1.0 - smoothstep(0.0, 2.0, sdRoundBox(p - float2(-hw + w * 0.6, -hh * 0.5), float2(w, hh * 0.40), w));
    float tr = 1.0 - smoothstep(0.0, 2.0, sdRoundBox(p - float2(hw - w * 0.6, -hh * 0.5), float2(w, hh * 0.40), w));
    float bl = 1.0 - smoothstep(0.0, 2.0, sdRoundBox(p - float2(-hw + w * 0.6, hh * 0.5), float2(w, hh * 0.40), w));
    float br = 1.0 - smoothstep(0.0, 2.0, sdRoundBox(p - float2(hw - w * 0.6, hh * 0.5), float2(w, hh * 0.40), w));

    float onA = (digit == 0 || digit == 2 || digit == 3 || digit == 5 || digit == 6 || digit == 7 || digit == 8 || digit == 9) ? 1.0 : 0.0;
    float onB = (digit == 0 || digit == 1 || digit == 2 || digit == 3 || digit == 4 || digit == 7 || digit == 8 || digit == 9) ? 1.0 : 0.0;
    float onC = (digit == 0 || digit == 1 || digit == 3 || digit == 4 || digit == 5 || digit == 6 || digit == 7 || digit == 8 || digit == 9) ? 1.0 : 0.0;
    float onD = (digit == 0 || digit == 2 || digit == 3 || digit == 5 || digit == 6 || digit == 8 || digit == 9) ? 1.0 : 0.0;
    float onE = (digit == 0 || digit == 2 || digit == 6 || digit == 8) ? 1.0 : 0.0;
    float onF = (digit == 0 || digit == 4 || digit == 5 || digit == 6 || digit == 8 || digit == 9) ? 1.0 : 0.0;
    float onG = (digit == 2 || digit == 3 || digit == 4 || digit == 5 || digit == 6 || digit == 8 || digit == 9) ? 1.0 : 0.0;
    return max(max(max(top * onA, mid * onG), max(bot * onD, tl * onF)),
               max(max(tr * onB, bl * onE), br * onC));
}

float4 styleCounting(float2 p, float w, float h, float edge, float t) {
    float4 res = float4(0.0);
    float inside = fillMask(p.x, edge, 1.5);
    float mid = h * 0.5;
    res = over(res, float3(0.02, 0.22, 0.18), inside * 0.35);

    float scan = 0.5 + 0.5 * sin(p.y * 0.20 - t * 6.0);
    res = over(res, float3(0.10, 0.55, 0.45), inside * scan * 0.10);

    float count = min(floor(percent), 99.0);
    int ones = int(count - floor(count / 10.0) * 10.0);
    int tens = int(floor(count / 10.0));
    // A seven-segment digit is 0.45 h wide. 0.47 h left a 3 px gap, and at reading size the two
    // glyphs touched. The step is now 0.60 h, so the pair reads as two numbers with air between
    // them, and the whole figure still finishes just short of the byte front.
    float baseX = edge - h * 0.89;
    float2 dp = p - float2(baseX, mid);
    float units = segmentDigit(dp - float2(h * 0.60, 0.0), ones, h);
    float tensMask = segmentDigit(dp, tens, h);

    int ghostOne = ones == 0 ? 9 : ones - 1;
    int ghostTwo = ones <= 1 ? ones + 8 : ones - 2;
    float ghost1 = segmentDigit(dp - float2(-h * 0.60, 0.0), ghostOne, h * 0.92);
    float ghost2 = segmentDigit(dp - float2(-h * 1.20, 0.0), ghostTwo, h * 0.84);
    res = over(res, float3(0.20, 0.75, 0.65), ghost2 * 0.12);
    res = over(res, float3(0.24, 0.85, 0.72), ghost1 * 0.20);

    res = over(res, float3(0.10, 0.75, 0.65), (units + tensMask) * 0.22);
    res = over(res, float3(0.75, 1.0, 0.94), (units + tensMask) * 0.85);
    return res;
}

// ---------------------------------------------------------------------------------------------
// 9. Flash: speed-force streaks, lightning at the front and chromatic afterimages.
// ---------------------------------------------------------------------------------------------

float4 styleFlash(float2 p, float w, float h, float edge, float t) {
    float4 res = float4(0.0);
    float inside = fillMask(p.x, edge, 1.5);
    float mid = h * 0.5;

    res = over(res, float3(0.28, 0.20, 0.02), inside * 0.24);
    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        float y = h * (0.08 + hash11(fi + 1.0) * 0.84);
        float run = fract(t * (1.3 + hash11(fi * 2.0)) + hash11(fi * 5.0));
        float x = edge - h * (0.1 + run * 1.0);
        float len = h * (0.20 + hash11(fi + 9.0) * 0.55);
        float streak = glow(sdSegment(p, float2(x, y), float2(x + len, y)), h * 0.03);
        float3 c = i == 0 ? float3(1.0, 0.22, 0.08) : float3(1.0, 0.88, 0.30);
        res = over(res, c, streak * (1.0 - run) * 0.65);
    }

    float2 c = float2(edge, mid);
    float d = length(p - c);
    float aura = glow(d, h * 0.25) * 0.35;
    res = over(res, float3(1.0, 0.88, 0.20), aura);
    res = over(res, float3(1.0), glow(d, h * 0.055) * 0.95);

    float bolt = boltLine(p, h, t, 4.0, 0.55);
    res = over(res, float3(1.0, 0.95, 0.55), bolt * 0.85);
    float vertical = exp(-pow((p.x - edge) / (h * 0.020), 2.0)) * (0.4 + 0.6 * sin(p.y * 0.12 + t * 8.0) * 0.5 + 0.5);
    res = over(res, float3(1.0, 0.92, 0.35), vertical * 0.35);

    for (int k = 0; k < 8; k++) {
        float fk = float(k);
        float2 sp = float2(edge - h * (0.1 + fract(t * 2.0 + hash11(fk + 4.0)) * 0.85),
                           h * (0.1 + hash11(fk + 6.0) * 0.8));
        res = over(res, float3(1.0, 0.95, 0.55), glow(length(p - sp), h * 0.008) * 0.55);
    }
    return res;
}

// ---------------------------------------------------------------------------------------------
// 10. Wheel: a solid tire at the front, tread and spokes turning with the bytes.
// ---------------------------------------------------------------------------------------------

float4 styleWheel(float2 p, float w, float h, float edge, float t) {
    float4 res = float4(0.0);
    float inside = fillMask(p.x, edge, 1.5);
    float mid = h * 0.5;
    float r = h * 0.47;
    float2 c = float2(edge - h * 0.055, mid);
    float2 q = p - c;

    for (int i = 0; i < 8; i++) {
        float fi = float(i);
        float seed = hash11(fi + 3.0);
        float2 sp = float2(edge - h * (0.35 + fract(t * 0.20 + seed) * 0.75),
                           h * (0.30 + (hash11(fi + 8.0) - 0.5) * 0.55));
        float cloud = fbm((p - sp) * 0.07 + float2(-t * 0.4, 0.0));
        res = over(res, float3(0.62, 0.60, 0.58), glow(length(p - sp), h * (0.07 + seed * 0.07)) * cloud * 0.24);
    }

    float skidY = mid + h * 0.30;
    float skid = exp(-pow((p.y - skidY) / (h * 0.016), 2.0)) * (1.0 - smoothstep(edge - h * 1.1, edge, p.x)) * inside;
    res = over(res, float3(0.10, 0.09, 0.08), skid * 0.55);

    float spin = fill * 26.0;
    float d = length(q);
    float tire = 1.0 - smoothstep(r - 2.0, r, d);
    float inner = 1.0 - smoothstep(r * 0.55 - 2.0, r * 0.55, d);
    float side = tire - inner;
    float3 tireCol = mix(float3(0.045, 0.045, 0.050), float3(0.17, 0.16, 0.15), sat(0.5 + 0.5 * (q.y / r)));
    float ang = atan(q.y, q.x) - spin;
    float lightKick = sat(dot(normalize(q + float2(0.0001, 0.0)), float2(0.45, -0.89)));
    res = over(res, tireCol, side * 0.98);
    // A rubber sidewall still reads at this size only if the lip catches light.
    res = over(res, float3(0.55, 0.53, 0.49), rim(d - r + 1.2, 1.6) * 0.50);
    res = over(res, float3(0.34, 0.33, 0.31), rim(d - r * 0.78, h * 0.010) * 0.35);
    res = over(res, float3(0.42, 0.40, 0.36), side * lightKick * 0.20);

    float tread = pow(sat(cos(ang * 18.0) * 0.5 + 0.5), 5.0) * (1.0 - smoothstep(r * 0.78, r, d)) * step(r * 0.55, d);
    res = over(res, float3(0.025, 0.025, 0.028), tread * 0.9);

    float rimMask = 1.0 - smoothstep(r * 0.48 - 1.5, r * 0.48, d);
    float brush = 0.5 + 0.5 * sin(ang * 26.0);
    res = over(res, mix(float3(0.28, 0.27, 0.26), float3(0.56, 0.54, 0.49), brush * 0.65), rimMask * 0.98);
    float rimHi = rim(d - r * 0.44, 2.0) * 0.65;
    res = over(res, float3(1.0, 0.95, 0.84), rimHi);

    for (int s = 0; s < 5; s++) {
        float fs = float(s);
        float a = spin + fs * TAU / 5.0;
        float2 dir = float2(cos(a), sin(a));
        float spoke = sdCapsule(q, dir * r * 0.10, dir * r * 0.40, h * 0.022);
        res = over(res, float3(0.82, 0.79, 0.71), (1.0 - smoothstep(0.0, 1.5, spoke)) * 0.80);
    }
    float hub = 1.0 - smoothstep(r * 0.12 - 1.5, r * 0.12, d);
    res = over(res, float3(0.92, 0.88, 0.78), hub * 0.92);

    float contact = exp(-pow((p.y - (mid + r * 0.92)) / (h * 0.02), 2.0)) *
        exp(-pow((p.x - (c.x - h * 0.02)) / (h * 0.18), 2.0));
    res = over(res, float3(1.0, 0.60, 0.20), contact * 0.30);
    return res;
}

// ---------------------------------------------------------------------------------------------
// 11. NOS: a low, wide car at the front on a blue nitrous flame.
// ---------------------------------------------------------------------------------------------


// The car sheet is composited straight, so a dark shell would sink into a dark fill. Reading the
// coverage lets the scene lay a thin cold rim on the outside edge.
float carAlpha(float2 uv) {
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x >= carSize.x || uv.y >= carSize.y) return 0.0;
    return carTex.eval(uv).a;
}
float4 styleNos(float2 p, float w, float h, float edge, float t) {
    float4 res = float4(0.0);
    float inside = fillMask(p.x, edge, 1.5);
    float mid = h * 0.5;
    float bob = sin(t * 7.0) * h * 0.002;
    float ground = mid + h * 0.30 + bob;

    // The shell is a side elevation baked from a real 3D model, with the paint, glass, lamps and
    // arches already shaded. The scene only has to place it, spin the wheels and light the road.
    // The sheet is 1024x295: nose at 0.9717 of the width, ground at 0.948 of the height.
    float L = h * 1.58;
    float SW = L / 0.9434;
    float texel = SW / max(carSize.x, 1.0);
    float SH = SW * carSize.y / max(carSize.x, 1.0);
    float xN = edge - h * 0.02;
    float left = xN - SW * 0.9717;
    float top = ground - SH * 0.948;
    float xTail = left + SW * 0.0283;

    res = over(res, float3(0.010, 0.050, 0.115), inside * 0.16);

    // Road: a shadow pressed under the car and a cold bloom off the exhausts.
    float shadow = exp(-pow((p.y - ground) / (h * 0.026), 2.0)) *
        exp(-pow((p.x - (left + SW * 0.50)) / (SW * 0.42), 2.0)) * inside;
    res = over(res, float3(0.0), shadow * 0.55);
    float underglow = exp(-pow((p.y - ground) / (h * 0.040), 2.0)) *
        exp(-pow((p.x - (left + SW * 0.46)) / (SW * 0.30), 2.0)) * inside;
    res = over(res, float3(0.05, 0.42, 1.0), underglow * 0.14);

    // Speed: long cold filaments at car height, behind the tail, not vertical bars.
    for (int j = 0; j < 8; j++) {
        float fj = float(j);
        float run = fract(t * 1.55 + hash11(fj + 4.0));
        float yy = ground - h * (0.05 + hash11(fj * 3.1 + 2.0) * 0.34);
        float x = xTail - h * (0.02 + run * 0.95);
        float len = h * (0.14 + hash11(fj + 7.0) * 0.30);
        float streak = glow(sdSegment(p, float2(x, yy), float2(x + len, yy)), h * 0.0045);
        res = over(res, float3(0.30, 0.76, 1.0), streak * (1.0 - run) * 0.28 * inside);
    }

    // Twin plumes out of the tail: a broad sheath, a white-cyan core and sparks that peel away.
    float exX = xTail + SW * 0.010;
    float exY = ground - h * 0.185;
    for (int e = 0; e < 2; e++) {
        float fe = float(e);
        float2 ex = float2(exX, exY + h * fe * 0.045);
        float len = h * (0.62 + 0.08 * sin(t * 16.0 + fe * 2.1));
        float along = sat((ex.x - p.x) / len);
        float sway = sin(along * 6.5 + t * 7.0 + fe * 2.0) * h * 0.010 * along;

        float2 nTop = ex + float2(0.0, -h * 0.020);
        float2 nBot = ex + float2(0.0, h * 0.020);
        float2 mTop = ex + float2(-len * 0.55, -h * 0.070) + float2(0.0, sway);
        float2 mBot = ex + float2(-len * 0.55, h * 0.070) + float2(0.0, sway);
        float2 fTip = ex + float2(-len, sway * 0.5);
        float plume = min(sdTriangle(p, nTop, mTop, fTip), sdTriangle(p, nBot, fTip, mBot));
        float plumeMask = (1.0 - smoothstep(0.0, h * 0.018, plume)) * inside;
        float flick = 0.76 + 0.24 * sin(t * 23.0 + p.x * 0.115 + fe * 3.0);

        res = over(res, float3(0.05, 0.34, 1.0), plumeMask * 0.44 * flick);
        res = over(res, float3(0.40, 0.82, 1.0), plumeMask * plumeMask * 0.55 * flick);
        float core = exp(-pow((p.y - ex.y - sway) / (h * 0.030), 2.0)) *
            (1.0 - along) * inside;
        res = over(res, float3(0.72, 0.98, 1.0), core * 0.70 * flick);
        res = over(res, float3(1.0), core * core * 0.55 * flick);

        for (int s = 0; s < 4; s++) {
            float fs = float(s);
            float runS = fract(t * 2.2 + hash11(fs + 12.0 + fe));
            float sx = ex.x - len * (0.25 + runS * 0.85);
            float sy = ex.y + (hash11(fs + 20.0 + fe) - 0.5) * h * 0.10 * (0.4 + runS);
            float spark = glow(length(p - float2(sx, sy)), h * 0.006);
            res = over(res, float3(0.60, 0.92, 1.0), spark * (1.0 - runS) * 0.55 * inside);
        }
    }

    // Wheels go down first so the arches cut them, then the baked shell sits over the top. A sheet
    // sampled at a fifth of its size turned the spokes into a hollow ring, so they are drawn.
    float WS = L * 0.1583;
    float wr = WS * 0.5;
    float spin = t * 15.0;
    for (int k = 0; k < 2; k++) {
        float fx = k == 0 ? 0.7398 : 0.1870;
        float2 wc = float2(left + SW * fx, top + SH * 0.6888);
        float2 q = p - wc;
        float d = length(q);
        float tire = 1.0 - smoothstep(wr - 1.2, wr, d);
        if (tire > 0.0) {
            float3 tireCol = float3(0.030, 0.032, 0.038) +
                float3(0.075, 0.095, 0.125) * sat(0.45 - q.y / (wr * 1.6));
            res = over(res, tireCol, tire * inside);

            float rin = wr * 0.54;
            float rimMask = 1.0 - smoothstep(rin - 1.0, rin, d);
            float3 rimCol = mix(float3(0.12, 0.13, 0.16), float3(0.50, 0.56, 0.64),
                sat(0.52 - q.y / (rin * 1.9)));
            res = over(res, rimCol, rimMask * inside);

            // Five spokes, cut so the gaps read as dark windows, then a hub and a lip.
            float ang = atan(q.y, q.x) + spin;
            float spoke = smoothstep(0.42, 0.86, abs(cos(ang * 2.5)));
            float arms = spoke * (1.0 - smoothstep(rin * 0.78, rin * 0.96, d));
            res = over(res, float3(0.78, 0.84, 0.92), arms * rimMask * 0.92 * inside);
            res = over(res, float3(0.72, 0.77, 0.84),
                (1.0 - smoothstep(rin * 0.12, rin * 0.18, d)) * inside);
            res = over(res, float3(0.34, 0.74, 1.0),
                rim(1.0 - smoothstep(wr - 1.4, wr, d), 1.1) * 0.40 * inside);
        }
    }

    // Rim: a cold light off the shell edge. Without it the sills and the nose vanish on the card.
    if (carSize.x > 1.0) {
        float2 uv = (p - float2(left, top)) / texel;
        float2 e1 = float2(2.6, 0.0) * texel;
        float2 e2 = float2(0.0, 2.6) * texel;
        float around = max(max(carAlpha(uv + e1), carAlpha(uv - e1)),
                           max(carAlpha(uv + e2), carAlpha(uv - e2)));
        float rim = around * (1.0 - carAlpha(uv));
        res = over(res, float3(0.22, 0.72, 1.0), rim * 0.45 * inside);
    }

    float2 uv = (p - float2(left, top)) / texel;
    if (uv.x >= 0.0 && uv.y >= 0.0 && uv.x < carSize.x && uv.y < carSize.y) {
        float4 cs = carTex.eval(uv) * inside;
        res = overSpr(res, cs);
    }

    // Headlamp bloom, then the two contact patches where the tires meet the road.
    float noseY = ground - h * 0.165;
    res = over(res, float3(0.70, 0.96, 1.0),
        glow(length(p - float2(xN - h * 0.045, noseY)), h * 0.040) * 0.45 * inside);
    float contact = exp(-pow((p.y - ground) / (h * 0.018), 2.0)) *
        (exp(-pow((p.x - (left + SW * 0.7398)) / (h * 0.07), 2.0)) +
         exp(-pow((p.x - (left + SW * 0.1870)) / (h * 0.07), 2.0))) * inside;
    res = over(res, float3(1.0, 0.42, 0.12), contact * 0.20);
    return res;
}

// The body of every limited card, painted once in the shader so the fill can end under the actor
// with a feather instead of a clipped vertical edge. The style scenes add their weather on top.
float3 styleBody(int s) {
    if (s == 0) return float3(0.02, 0.16, 0.34);
    if (s == 1) return float3(0.015, 0.13, 0.24);
    if (s == 2) return float3(0.22, 0.03, 0.01);
    if (s == 3) return float3(0.16, 0.14, 0.12);
    if (s == 4) return float3(0.14, 0.11, 0.07);
    if (s == 5) return float3(0.15, 0.11, 0.05);
    if (s == 6) return float3(0.16, 0.015, 0.03);
    if (s == 7) return float3(0.025, 0.04, 0.12);
    if (s == 8) return float3(0.015, 0.13, 0.10);
    if (s == 9) return float3(0.16, 0.11, 0.015);
    if (s == 10) return float3(0.045, 0.060, 0.105);
    return float3(0.01, 0.08, 0.16);
}

half4 main(float2 fragCoord) {
    float w = max(size.x, 1.0);
    float h = max(size.y, 1.0);
    if (fill <= 0.002) return half4(0.0);
    float edge = fill * w;
    float t = phase * TAU;
    float4 c = float4(0.0);
    int s = int(style + 0.5);
    float body = fillMask(fragCoord.x, edge, max(h * 0.055, 2.0));
    c = over(c, styleBody(s), body * 0.62);
    c = over(c, styleBody(s) * 0.62, body * 0.20 * sat(fragCoord.y / h));
    // The scene is a layer, not a replacement. Every style shipped with its own dark bed, and the
    // feather that lets the card end under the actor lives in the body above. Assigning the scene
    // over the body threw that feather away and put the hard cut back.
    float4 sc = float4(0.0);
    if (s == 0) sc = styleKame(fragCoord, w, h, edge, t);
    else if (s == 1) sc = styleTsunami(fragCoord, w, h, edge, t);
    else if (s == 2) sc = styleFire(fragCoord, w, h, edge, t);
    else if (s == 3) sc = styleBoulder(fragCoord, w, h, edge, t);
    else if (s == 4) sc = styleBullet(fragCoord, w, h, edge, t);
    else if (s == 5) sc = styleArrow(fragCoord, w, h, edge, t);
    else if (s == 6) sc = styleLaser(fragCoord, w, h, edge, t);
    else if (s == 7) sc = styleThunder(fragCoord, w, h, edge, t);
    else if (s == 8) sc = styleCounting(fragCoord, w, h, edge, t);
    else if (s == 9) sc = styleFlash(fragCoord, w, h, edge, t);
    else if (s == 10) sc = styleWheel(fragCoord, w, h, edge, t);
    else sc = styleNos(fragCoord, w, h, edge, t);
    c = over(c, sc.rgb, sc.a);

    float energy = mix(0.55, 1.0, live);
    c.rgb = mix(float3(lum(c.rgb)), c.rgb, energy);
    c.a *= mix(0.72, 1.0, live);
    float grain = (hash21(fragCoord + phase * 311.0) - 0.5) * 0.022;
    c.rgb = sat3(c.rgb + grain);
    return half4(c.rgb * c.a, c.a);
}
"""
