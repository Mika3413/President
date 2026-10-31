"""Génère l'icône du jeu (bille de verre et trou doré) dans res/mipmap-*."""
import math, os, sys
from PIL import Image, ImageDraw, ImageFilter

S = 1024
def radial(size, cx, cy, r, stops):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            d = min(1.0, math.hypot(x - cx, y - cy) / r)
            for i in range(len(stops) - 1):
                a, ca = stops[i]; b, cb = stops[i + 1]
                if a <= d <= b:
                    k = (d - a) / (b - a) if b > a else 0
                    px[x, y] = tuple(int(ca[j] + (cb[j] - ca[j]) * k) for j in range(4))
                    break
    return img

base = Image.new("RGBA", (S, S), (0, 0, 0, 0))
bg = radial(S, S * 0.35, S * 0.3, S * 0.95, [(0, (52, 140, 110, 255)), (0.6, (16, 60, 48, 255)), (1, (4, 20, 16, 255))])
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
base.paste(bg, (0, 0), mask)

# trou doré
d = ImageDraw.Draw(base)
hx, hy, hr = S * 0.68, S * 0.7, S * 0.17
glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse([hx - hr * 1.8, hy - hr * 1.8, hx + hr * 1.8, hy + hr * 1.8], fill=(255, 210, 90, 120))
glow = glow.filter(ImageFilter.GaussianBlur(S * 0.05))
base = Image.alpha_composite(base, glow)
d = ImageDraw.Draw(base)
d.ellipse([hx - hr, hy - hr, hx + hr, hy + hr], fill=(255, 200, 70, 255))
d.ellipse([hx - hr * 0.95, hy - hr * 0.95, hx + hr * 0.9, hy + hr * 0.9], fill=(255, 236, 160, 255))
d.ellipse([hx - hr * 0.76, hy - hr * 0.76, hx + hr * 0.76, hy + hr * 0.76], fill=(5, 5, 5, 255))

# ombre de la bille
bx, by, br = S * 0.42, S * 0.42, S * 0.25
sh = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(sh).ellipse([bx - br + S * 0.06, by - br + S * 0.1, bx + br + S * 0.08, by + br + S * 0.1], fill=(0, 0, 0, 150))
sh = sh.filter(ImageFilter.GaussianBlur(S * 0.03))
base = Image.alpha_composite(base, sh)

# corps de la bille
body = radial(S, bx - br * 0.3, by - br * 0.35, br * 1.3,
              [(0, (160, 220, 255, 255)), (0.3, (44, 123, 224, 255)), (0.72, (20, 60, 148, 255)), (1, (7, 21, 61, 255))])
bm = Image.new("L", (S, S), 0)
ImageDraw.Draw(bm).ellipse([bx - br, by - br, bx + br, by + br], fill=255)
layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
layer.paste(body, (0, 0), bm)
# bande dorée
band = Image.new("RGBA", (S, S), (0, 0, 0, 0))
bd = ImageDraw.Draw(band)
for i in range(120):
    a = i / 120 * 2 * math.pi
    # cercle incliné projeté
    x3, y3, z3 = math.cos(a), math.sin(a) * 0.45, math.sin(a) * 0.89
    if z3 < -0.05: continue
    px_, py_ = bx + (x3 * 0.94) * br, by + (y3 * 0.94 + 0.12) * br
    rr = br * (0.07 + 0.08 * max(0, z3))
    lum = 0.45 + 0.55 * max(0, z3)
    col = (int(122 + (255 - 122) * lum), int(46 + (179 - 46) * lum), int(0 + 71 * lum), 255)
    bd.ellipse([px_ - rr, py_ - rr, px_ + rr, py_ + rr], fill=col)
bandm = Image.new("L", (S, S), 0)
ImageDraw.Draw(bandm).ellipse([bx - br * 0.97, by - br * 0.97, bx + br * 0.97, by + br * 0.97], fill=255)
layer.paste(band, (0, 0), Image.composite(band.getchannel("A"), Image.new("L", (S, S), 0), bandm))
# ombrage du bord
shade = radial(S, bx - br * 0.25, by - br * 0.28, br * 1.3, [(0, (0, 0, 0, 0)), (0.45, (0, 0, 0, 0)), (0.78, (0, 8, 32, 85)), (1, (0, 5, 16, 200))])
sl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
sl.paste(shade, (0, 0), bm)
layer = Image.alpha_composite(layer, sl)
# reflet
hl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(hl).ellipse([bx - br * 0.62, by - br * 0.66, bx - br * 0.05, by - br * 0.25], fill=(255, 255, 255, 200))
hl = hl.filter(ImageFilter.GaussianBlur(S * 0.012))
layer = Image.alpha_composite(layer, hl)
ImageDraw.Draw(layer).ellipse([bx - br * 0.5, by - br * 0.55, bx - br * 0.36, by - br * 0.41], fill=(255, 255, 255, 255))
base = Image.alpha_composite(base, layer)

# étincelles
d = ImageDraw.Draw(base)
for (sx, sy, sr) in [(0.82, 0.22, 0.03), (0.2, 0.8, 0.022), (0.88, 0.46, 0.015)]:
    cx, cy, r = S * sx, S * sy, S * sr
    d.polygon([(cx, cy - r * 2), (cx + r * 0.4, cy - r * 0.4), (cx + r * 2, cy), (cx + r * 0.4, cy + r * 0.4),
               (cx, cy + r * 2), (cx - r * 0.4, cy + r * 0.4), (cx - r * 2, cy), (cx - r * 0.4, cy - r * 0.4)], fill=(255, 240, 190, 230))

out = sys.argv[1]
for name, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    dd = os.path.join(out, "mipmap-" + name)
    os.makedirs(dd, exist_ok=True)
    base.resize((size, size), Image.LANCZOS).save(os.path.join(dd, "ic_launcher.png"))
base.resize((512, 512), Image.LANCZOS).save(os.path.join(out, "..", "icon-512.png"))
