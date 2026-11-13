"""Render Aura Store's launcher art from one description of the badge.

The badge is an amber disc with a ring-lit gradient and the white A with its cream counter. Every
deliverable comes from `badge()` below, so the 1024 master, the adaptive foreground and the density
rasters cannot drift apart. Run from the project root:

    python tools/render_logo.py

Outputs (all paths relative to the project root):

    logo/icon-1024.png                          the 1:1 master: dark field, badge centred
    logo/mark-amber.png                         the A alone, in the house amber, for docs
    app/src/main/res/mipmap-*/ic_launcher.webp          legacy square (API < 26)
    app/src/main/res/mipmap-*/ic_launcher_round.webp    legacy round
    app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png   adaptive foreground, 72dp of 108

The adaptive background is the flat dark field in drawable/ic_launcher_background.xml; the foreground
carries the disc at 71 of the 108 units a launcher scales, so the mask lands exactly on the disc's own
edge and no dark ring shows. The themed icon is drawable/ic_launcher_monochrome.xml — the same A path,
which Android tints, because a gradient and a wordmark have no monochrome form.

Colours are the app's own tokens: FIELD is `Accent`, INK is `OnAccentDark`. IconGeometryTest asserts
that this file, the vectors and Color.kt agree, because a comment cannot.
"""

import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SUPERSAMPLE = 4
MASTER = 1024

FIELD = (0xE0, 0x90, 0x2C)      # Accent — the house amber the disc is built on
DEEP = (0xB2, 0x5C, 0x14)       # the disc's rim, darker than the field
LIGHT = (0xF7, 0xC9, 0x7C)      # the lit centre
GREEN = (0x9C, 0x9E, 0x4E)      # the wash that runs up the right of the disc
RED = (0xC0, 0x4A, 0x22)        # the wash at its lower left
INK = (0x1C, 0x14, 0x08)        # OnAccentDark — the mark's shadow
MARK = (0xFF, 0xFF, 0xFF)       # the A
CREAM = (0xF7, 0xEA, 0xD2)      # the V cut into it
BACKGROUND = (0x1E, 0x1E, 0x1E)  # the field the badge sits on

# The A in 108dp space, copied from drawable/ic_launcher_monochrome.xml: two legs sharing a flat apex
# and a crossbar, 52 wide and 39 tall, its furthest point 32.5 from the centre — inside the 33.1 the
# safe-zone arithmetic allows and the 72dp circle a launcher masks.
MARK_POINTS = [
    (28.0, 70.0), (50.5, 31.0), (57.5, 31.0), (35.0, 70.0),
    (50.5, 31.0), (57.5, 31.0), (80.0, 70.0), (73.0, 70.0),
    (39.5, 53.0), (68.5, 53.0), (68.5, 61.0), (39.5, 61.0),
]
MARK_SHAPES = [MARK_POINTS[0:4], MARK_POINTS[4:8], MARK_POINTS[8:12]]

# The cream wedge that fills the A's counter, in the same 108 space: apex under the flat top, base
# between the open ends of the legs. It is the cream V of the original art, seen as a solid.
COUNTER = [(54.0, 35.0), (40.5, 64.0), (67.5, 64.0)]


def _mix(a, b, t):
    """`a` towards `b` by t, per channel, t broadcastable over the image."""
    return a + (np.asarray(b, float) - a) * t[..., None]


def _disc(n):
    """The gradient disc at n pixels square, transparent outside a radius of n/2."""
    y, x = np.mgrid[0:n, 0:n]
    u = (x + 0.5) / n * 2.0 - 1.0
    v = (y + 0.5) / n * 2.0 - 1.0
    r = np.hypot(u, v)

    col = np.zeros((n, n, 3)) + np.asarray(FIELD, float)
    col = _mix(col, LIGHT, (np.clip(1.0 - r, 0.0, 1.0) ** 1.4) * 0.55)
    col = _mix(col, DEEP, (np.clip((r - 0.40) / 0.60, 0.0, 1.0) ** 1.6) * 0.88)

    green = np.exp(-(((u - 0.38) ** 2 + (v + 0.42) ** 2) / (2 * 0.40 ** 2)))
    col = _mix(col, GREEN, green * 0.46)
    red = np.exp(-(((u + 0.52) ** 2 + (v - 0.50) ** 2) / (2 * 0.44 ** 2)))
    col = _mix(col, RED, red * 0.42)

    for radius, width, strength in ((0.38, 0.045, 0.16), (0.58, 0.040, 0.10), (0.78, 0.035, 0.07)):
        ring = np.exp(-((r - radius) / width) ** 2)
        col = _mix(col, (0xFF, 0xEE, 0xC6), ring * strength)

    rim = np.exp(-((r - 0.96) / 0.04) ** 2) * np.clip(-(u + v) * 0.55, 0.0, 1.0)
    col = _mix(col, (0xFF, 0xFA, 0xEC), rim * 0.50)

    alpha = np.clip((1.0 - r) * n * 0.5 + 0.5, 0.0, 1.0)
    return Image.fromarray(np.dstack([np.clip(col, 0, 255), alpha * 255]).astype(np.uint8), "RGBA")


def _box(n, width_ratio, height_ratio, centre_y_ratio):
    """Where the 52×39 mark lands on an n-pixel disc: scale plus the offset for its own centre."""
    w, h = width_ratio * n, height_ratio * n
    k = min(w / 52.0, h / 39.0)
    cx, cy = (28.0 + 80.0) / 2, (31.0 + 70.0) / 2
    return (lambda x, y: ((x - cx) * k + n / 2, (y - cy) * k + n / 2 + centre_y_ratio * n)), k


def _mark(n):
    """The white A with its cream V, transparent everywhere else."""
    # With the wordmark gone the letter is the whole badge, so it is drawn bigger and centred.
    place, k = _box(n, 0.60, 0.555, 0.0)

    # A soft shadow so the mark lifts off the disc rather than sitting flat on it.
    shadow = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    for polygon in MARK_SHAPES[:2]:
        sdraw.polygon([(x + n * 0.006, y + n * 0.008) for x, y in map(lambda p: place(*p), polygon)],
                      fill=INK + (120,))
    layer = shadow.filter(ImageFilter.GaussianBlur(n * 0.012))

    # The badge draws the two legs; the cream V takes the crossbar's place, which is what makes the
    # mark read as A·V at icon sizes instead of a letter with a stripe through it. The V's ends sit
    # inside the legs, so no cream tip pokes out above a shoulder.
    draw = ImageDraw.Draw(layer)
    draw.polygon([place(x, y) for x, y in COUNTER], fill=CREAM + (255,))
    for polygon in MARK_SHAPES[:2]:
        draw.polygon([place(x, y) for x, y in polygon], fill=MARK + (255,))
    return layer


def badge(px):
    """The disc and the A, edge to edge, at px pixels square."""
    n = px * SUPERSAMPLE
    art = _disc(n)
    art.alpha_composite(_mark(n))
    return art.resize((px, px), Image.LANCZOS)


def field(px):
    """The dark field the badge sits on: a soft vignette, so 1024 does not read as a flat tile."""
    y, x = np.mgrid[0:px, 0:px]
    r = np.hypot((x + 0.5) / px * 2 - 1, (y + 0.5) / px * 2 - 1) / math.sqrt(2)
    col = _mix(np.zeros((px, px, 3)) + np.asarray(BACKGROUND, float), (0x16, 0x16, 0x16), r ** 2)
    return Image.fromarray(np.dstack([np.clip(col, 0, 255), np.full((px, px), 255)]).astype(np.uint8), "RGBA")


def square_icon(px, margin=0.985):
    """The master composition: the badge filling the square, only the corners showing the field."""
    out = field(px)
    disc = badge(max(1, round(px * margin)))
    out.alpha_composite(disc, ((px - disc.width) // 2, (px - disc.height) // 2))
    return out


def round_icon(px, margin=1.0):
    """The legacy round icon: the badge edge to edge, corners transparent."""
    out = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    disc = badge(max(1, round(px * margin)))
    out.alpha_composite(disc, ((px - disc.width) // 2, (px - disc.height) // 2))
    return out


def bare_mark(px, colour=FIELD):
    """The A on its own, fitted with a tenth of the canvas around it."""
    xs = [p[0] for p in MARK_POINTS]
    ys = [p[1] for p in MARK_POINTS]
    k = px * 0.8 / max(max(xs) - min(xs), max(ys) - min(ys))
    dx = (px - (max(xs) - min(xs)) * k) / 2 - min(xs) * k
    dy = (px - (max(ys) - min(ys)) * k) / 2 - min(ys) * k
    layer = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for polygon in MARK_SHAPES:
        draw.polygon([(x * k + dx, y * k + dy) for x, y in polygon], fill=colour + (255,))
    return layer


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    res = os.path.join(root, "app", "src", "main", "res")

    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, px in densities.items():
        folder = os.path.join(res, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)
        square_icon(px).save(os.path.join(folder, "ic_launcher.webp"), "WEBP", quality=95)
        round_icon(px).save(os.path.join(folder, "ic_launcher_round.webp"), "WEBP", quality=95)

    # 71 of the 108 units a launcher scales: the mask then lands on the disc's own edge.
    os.makedirs(os.path.join(res, "drawable-xxxhdpi"), exist_ok=True)
    foreground = Image.new("RGBA", (108, 108), (0, 0, 0, 0))
    disc = badge(round(71 * SUPERSAMPLE)).resize((71, 71), Image.LANCZOS)
    foreground.alpha_composite(disc, ((108 - 71) // 2, (108 - 71) // 2))
    foreground.resize((432, 432), Image.LANCZOS).save(
        os.path.join(res, "drawable-xxxhdpi", "ic_launcher_foreground.png"))

    logo = os.path.join(root, "logo")
    os.makedirs(logo, exist_ok=True)
    square_icon(MASTER).save(os.path.join(logo, "icon-1024.png"))
    bare_mark(MASTER, FIELD).save(os.path.join(logo, "mark-amber.png"))
    print(f"wrote {len(densities) * 2} mipmaps, the adaptive foreground and 2 logo PNGs")
