"""Generates Play Store assets (512 icon, 1024x500 feature graphic).

Draws the same mark as the Android vector so the store listing matches the
launcher icon. Pure PIL, no network.
"""

import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join("store")
os.makedirs(OUT, exist_ok=True)

LIME = (182, 243, 106)
MINT = (123, 224, 195)
BG = (11, 13, 16)
TEXT = (243, 246, 241)
MUTED = (154, 165, 160)

SS = 3  # supersampling


def font(size, bold=True):
    candidates = (
        [r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf"]
        if bold
        else [r"C:\Windows\Fonts\segoeui.ttf", r"C:\Windows\Fonts\arial.ttf"]
    )
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def rounded_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def draw_mark(img, scale, cx=0.0, cy=0.0):
    """Paint the frame + two arrows, in 108-unit coordinates."""
    d = ImageDraw.Draw(img)
    u = scale
    ox, oy = cx, cy

    def p(x, y):
        return (ox + x * u, oy + y * u)

    d.rounded_rectangle(
        [p(24, 26), p(84, 82)],
        radius=8 * u,
        outline=(228, 255, 184),
        width=max(1, int(4.5 * u)),
    )
    w = max(1, int(8 * u))
    for coords, colour in (
        ([(39, 34), (54, 46), (69, 34)], LIME),
        ([(39, 74), (54, 62), (69, 74)], MINT),
    ):
        pts = [p(x, y) for x, y in coords]
        d.line(pts, fill=colour, width=w, joint="curve")
        for x, y in (pts[0], pts[-1]):
            r = w / 2
            d.ellipse([x - r, y - r, x + r, y + r], fill=colour)


def icon(size=512):
    s = size * SS
    img = Image.new("RGB", (s, s), BG)
    d = ImageDraw.Draw(img)

    for y in range(s):
        t = y / (s - 1)
        d.line(
            [(0, y), (s, y)],
            fill=(int(28 - 20 * t), int(37 - 27 * t), int(32 - 20 * t)),
        )

    glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gcx, gcy, gr = s * 0.5, s * 0.42, s * 0.55
    for i in range(26, 0, -1):
        rr = gr * (i / 26)
        gd.ellipse(
            [gcx - rr, gcy - rr, gcx + rr, gcy + rr],
            fill=LIME + (int(42 * (i / 26) ** 2),),
        )
    img = Image.alpha_composite(img.convert("RGBA"), glow)

    draw_mark(img, s / 108.0)
    img = img.convert("RGB").resize((size, size), Image.LANCZOS)

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), rounded_mask(size, int(size * 0.22)))
    out.save(os.path.join(OUT, "icon_512.png"))


def feature(width=1024, height=500):
    s_w, s_h = width * SS, height * SS
    img = Image.new("RGB", (s_w, s_h), BG)
    d = ImageDraw.Draw(img)

    for y in range(s_h):
        t = y / (s_h - 1)
        d.line([(0, y), (s_w, y)], fill=(int(23 - 12 * t), int(32 - 19 * t), int(27 - 11 * t)))

    glow = Image.new("RGBA", (s_w, s_h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gcx, gcy, gr = s_w * 0.80, s_h * 0.46, s_w * 0.40
    for i in range(28, 0, -1):
        rr = gr * (i / 28)
        gd.ellipse([gcx - rr, gcy - rr, gcx + rr, gcy + rr], fill=LIME + (int(38 * (i / 28) ** 2),))
    img = Image.alpha_composite(img.convert("RGBA"), glow)
    d = ImageDraw.Draw(img)

    pad = 72 * SS
    d.text((pad, 108 * SS), "VideoFit", font=font(86 * SS), fill=TEXT)
    d.text((pad, 208 * SS), "Compress to any target size", font=font(36 * SS, bold=False), fill=MUTED)
    d.text((pad, 258 * SS), "Offline · H.264 / H.265 · no uploads", font=font(28 * SS, bold=False), fill=LIME)

    chip_x, chip_y, chip_w, chip_h = pad, 336 * SS, 470 * SS, 78 * SS
    d.rounded_rectangle(
        [chip_x, chip_y, chip_x + chip_w, chip_y + chip_h],
        radius=20 * SS,
        fill=(28, 36, 30),
        outline=(42, 50, 59),
        width=2 * SS,
    )
    d.text((chip_x + 24 * SS, chip_y + 18 * SS), "23.2 MB", font=font(34 * SS), fill=MUTED)
    d.text((chip_x + 190 * SS, chip_y + 18 * SS), "→", font=font(34 * SS), fill=LIME)
    d.text((chip_x + 250 * SS, chip_y + 18 * SS), "1.99 MB", font=font(34 * SS), fill=LIME)

    # Badge with the mark
    badge = 210 * SS
    bx, by = int(s_w * 0.80 - badge / 2), int(s_h * 0.5 - badge / 2)
    d.rounded_rectangle(
        [bx, by, bx + badge, by + badge],
        radius=int(badge * 0.26),
        fill=(22, 28, 24),
        outline=(42, 50, 59),
        width=3 * SS,
    )
    draw_mark(img, badge / 132.0, cx=bx + badge * 0.5 - 54 * (badge / 132.0), cy=by + badge * 0.5 - 54 * (badge / 132.0))

    img.convert("RGB").resize((width, height), Image.LANCZOS).save(
        os.path.join(OUT, "feature_graphic_1024x500.png")
    )


if __name__ == "__main__":
    icon(512)
    feature()
    for name in ("icon_512.png", "feature_graphic_1024x500.png"):
        path = os.path.join(OUT, name)
        print(name, os.path.getsize(path), "bytes")
