package dev.aurastore.store.ui

/**
 * The AGSL source behind the iOS 13+ engine.
 *
 * It is the same idea as the reference shader recorded in `docs/NAVBAR_SETTINGS.md`: take the sampled
 * page as a child shader, bend it along the rounded rectangle's normal at the rim, magnify it around a
 * cylindrical lens, and sample the three colour channels at slightly different offsets. The uniforms
 * are the reference's names for the same quantities, minus the ones this app has no use for
 * (`surfaceOffset`), so the two sources can be read side by side.
 *
 * AGSL is SkSL: `main` takes the destination pixel and returns its colour. The child shader is sampled
 * in the same space as [fragCoord] — the sheet's own pixels — and its `BitmapShader` carries the scale
 * from the 640 px sample up to that sheet.
 */
internal const val LIQUID_GLASS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float cornerRadius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float lensCenter;
uniform float lensHalfWidth;
uniform float lensStrength;
uniform float chromaDistance;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + float2(r);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float2 sdfNormal(float2 p, float2 halfSize, float r) {
    float e = 1.0;
    float dx = sdRoundRect(p + float2(e, 0.0), halfSize, r)
             - sdRoundRect(p - float2(e, 0.0), halfSize, r);
    float dy = sdRoundRect(p + float2(0.0, e), halfSize, r)
             - sdRoundRect(p - float2(0.0, e), halfSize, r);
    float2 n = float2(dx, dy);
    float len = length(n);
    return len > 0.0001 ? n / len : float2(0.0);
}

half4 main(float2 fragCoord) {
    float2 halfSize = size * 0.5;
    float2 p = fragCoord - halfSize;
    float d = sdRoundRect(p, halfSize, cornerRadius);
    float2 n = sdfNormal(p, halfSize, cornerRadius);

    // The rim pulls the interior outward over a band refractionHeight deep, strongest at the edge.
    float band = max(refractionHeight, 1.0);
    float rim = 1.0 - smoothstep(-band, 0.0, d);
    float2 rimOffset = -n * (rim * rim) * refractionAmount;

    // A convex pill lens across the sheet: pixels near lensCenter read closer to the centre, so what is under the bar magnifies smoothly.
    float lensD = abs(fragCoord.x - lensCenter);
    float lensMask = 1.0 - smoothstep(0.0, max(lensHalfWidth, 1.0), lensD);
    float2 lensOffset = float2(
        -(fragCoord.x - lensCenter) / max(lensHalfWidth, 1.0),
        -p.y / max(halfSize.y, 1.0) * 0.45
    ) * lensMask * lensStrength;

    float2 offset = rimOffset + lensOffset;
    // Red reads farthest out, blue farthest in: the colour split follows the lens and rim dispersion.
    float2 radial = p / max(halfSize, float2(1.0));
    float2 dir = normalize(n * rim + radial * 0.65 + float2(0.0001, 0.0));
    float2 split = dir * chromaDistance * (0.45 + 0.85 * rim);

    float2 maxCoord = max(size - float2(1.0), float2(1.0));
    float2 rPos = clamp(fragCoord + offset + split, float2(0.5), maxCoord);
    float2 gPos = clamp(fragCoord + offset, float2(0.5), maxCoord);
    float2 bPos = clamp(fragCoord + offset - split, float2(0.5), maxCoord);

    half4 color;
    color.r = content.eval(rPos).r;
    color.g = content.eval(gPos).g;
    color.b = content.eval(bPos).b;
    color.a = 1.0;
    return color;
}
"""
