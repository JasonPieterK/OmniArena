#!/usr/bin/env python3
"""Builds the final OmniArena logo kit (SVG + PNG) into this folder, the Modrinth icon and graphics-src/icon.svg.
Mark: a disc split by an X into four fields (grid 256, disc r=120, X gap 28 wide). Wordmark: custom constructed caps (no fonts).
Run:  python3 build-logo.py     (needs Pillow and playwright via NODE_PATH for PNG export)"""
import math, os, json, subprocess
from PIL import Image
here = os.path.dirname(os.path.abspath(__file__)); root = os.path.dirname(here)
R, B, Y, K, P = "#D6352B", "#1E4FA3", "#F2C230", "#111111", "#F4EFE6"
def f(v): return f"{v:.2f}".rstrip("0").rstrip(".")

def sectors(cx, cy, rad, w):
    c = w * math.sqrt(2); d = math.sqrt(2 * rad * rad - c * c); a = (c + d) / 2; b = (d - c) / 2
    pt = lambda x, y: f"{f(cx + x)} {f(cy + y)}"
    return {
     "top":    f"M{pt(0,-c)} L{pt(-b,-a)} A{rad} {rad} 0 0 1 {pt(b,-a)} Z",
     "right":  f"M{pt(c,0)} L{pt(a,-b)} A{rad} {rad} 0 0 1 {pt(a,b)} Z",
     "bottom": f"M{pt(0,c)} L{pt(b,a)} A{rad} {rad} 0 0 1 {pt(-b,a)} Z",
     "left":   f"M{pt(-c,0)} L{pt(-a,b)} A{rad} {rad} 0 0 1 {pt(-a,-b)} Z"}
def mark(cx=128, cy=128, rad=120, w=14, cols=(Y, B, K, R)):
    s = sectors(cx, cy, rad, w)
    return "".join(f'<path fill="{c}" d="{s[k]}"/>' for k, c in zip(("top", "right", "bottom", "left"), cols))
def svg(vb, body, title, extra=""):
    x, y, w, h = vb
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x} {y} {w} {h}" width="{w}" height="{h}" role="img" aria-labelledby="t">'
            f'<title id="t">{title}</title>{extra}{body}</svg>\n')
def put(path, s): open(os.path.join(here, path), "w").write(s)

# ---- wordmark: custom caps, cap height H=120, stem S=28, built from primitives ----
H, S = 120, 28
def poly(pts, dx): return '<polygon points="' + " ".join(f"{f(x+dx)},{f(y)}" for x, y in pts) + '"/>'
def rect(x, y, w, h, dx): return f'<rect x="{f(x+dx)}" y="{f(y)}" width="{f(w)}" height="{f(h)}"/>'
def L_I(dx): return rect(0, 0, S, H, dx), S
def L_E(dx):
    w = 100; t = 24
    return rect(0, 0, S, H, dx) + rect(0, 0, w, t, dx) + rect(0, (H - t) / 2, w - 12, t, dx) + rect(0, H - t, w, t, dx), w
def L_N(dx):
    w = 118; k = 1.2 * S
    return rect(0, 0, S, H, dx) + rect(w - S, 0, S, H, dx) + poly([(0, 0), (k, 0), (w, H), (w - k, H)], dx), w
def L_M(dx):
    w = 150; k = 1.2 * S
    return (rect(0, 0, S, H, dx) + rect(w - S, 0, S, H, dx) + poly([(0, 0), (k, 0), (w / 2 + k / 2, H), (w / 2 - k / 2, H)], dx)
            + poly([(w, 0), (w - k, 0), (w / 2 - k / 2, H), (w / 2 + k / 2, H)], dx)), w
def L_A(dx):
    w = 140; k = 1.2 * S; a = 0.6 * S
    return (poly([(0, H), (k, H), (w / 2 + a, 0), (w / 2 - a, 0)], dx) + poly([(w, H), (w - k, H), (w / 2 - a, 0), (w / 2 + a, 0)], dx)
            + rect(w * 0.22, H * 0.66, w * 0.56, 22, dx)), w
def L_O(dx):
    r = H / 2 + 2; ri = r - S * 0.95; cx = r; cy = H / 2
    p = (f'<path fill-rule="evenodd" d="M{f(dx)} {f(cy)} A{f(r)} {f(r)} 0 1 1 {f(dx+2*r)} {f(cy)} A{f(r)} {f(r)} 0 1 1 {f(dx)} {f(cy)} Z '
         f'M{f(dx+r-ri)} {f(cy)} A{f(ri)} {f(ri)} 0 1 0 {f(dx+r+ri)} {f(cy)} A{f(ri)} {f(ri)} 0 1 0 {f(dx+r-ri)} {f(cy)} Z"/>')
    return p, 2 * r
def L_R(dx):
    w = 112; hb = 0.6 * H; r = hb / 2; t = 24
    ri = r - t
    bowl = (f'<path fill-rule="evenodd" d="M{f(dx)} 0 H{f(dx+w-r)} A{f(r)} {f(r)} 0 0 1 {f(dx+w-r)} {f(hb)} H{f(dx)} Z '
            f'M{f(dx+S)} {f(t)} H{f(dx+w-r)} A{f(ri)} {f(ri)} 0 0 1 {f(dx+w-r)} {f(hb-t)} H{f(dx+S)} Z"/>')
    return rect(0, 0, S, H, dx) + bowl + poly([(w * 0.34, hb - 6), (w * 0.34 + 1.25 * S, hb - 6), (w, H), (w - 1.25 * S, H)], dx), w
def wordmark(letters, gaps):
    out = []; x = 0
    for i, (L, g) in enumerate(zip(letters, gaps + [0])):
        s, w = L(x); out.append(s); x += w + g
    return "".join(out), x - gaps[-1] if False else x
word, W = wordmark([L_O, L_M, L_N, L_I, L_A, L_R, L_E, L_N, L_A], [14, 18, 18, 16, 6, 18, 18, 10, 0])

# ---- files ----
put("omniarena-logo.svg", svg((0, 0, 256, 256), mark(), "OmniArena logo mark"))
put("omniarena-logo-black.svg", svg((0, 0, 256, 256), mark(cols=(K, K, K, K)), "OmniArena logo mark, black"))
put("omniarena-logo-white.svg", svg((0, 0, 256, 256), mark(cols=(P, P, P, P)), "OmniArena logo mark, white"))
LW = 256 + 44 + W
def lockup(c_mark, c_word, title):
    wy = (256 - H) / 2
    return svg((0, 0, round(LW), 256), mark(cols=c_mark) + f'<g fill="{c_word}" transform="translate({256+44} {f(wy)})">{word}</g>', title)
put("omniarena-lockup.svg", lockup((Y, B, K, R), K, "OmniArena logo"))
put("omniarena-lockup-black.svg", lockup((K,) * 4, K, "OmniArena logo, black"))
put("omniarena-lockup-white.svg", lockup((P,) * 4, P, "OmniArena logo, white"))
# icon tile: paper, black frame, mark inside (Modrinth icon, README, hero)
icon = svg((0, 0, 256, 256), f'<rect width="256" height="256" fill="{P}"/>' + mark(rad=96, w=11.5) +
           f'<rect x="6" y="6" width="244" height="244" fill="none" stroke="{K}" stroke-width="12"/>', "OmniArena icon")
put("omniarena-icon.svg", icon)
# small-size cut: wider X gap for 16/32 px
put("omniarena-logo-small.svg", svg((0, 0, 256, 256), mark(rad=124, w=17), "OmniArena logo mark, small-size cut"))
put(os.path.join("..", "images", "00-icon.svg"), icon.replace('width="256" height="256"', 'width="512" height="512"'))
put(os.path.join("..", "graphics-src", "icon.svg"), icon.replace('width="256" height="256"', 'width="512" height="512"'))
print("lockup width", round(LW), "wordmark width", round(W))

# ---- PNG exports ----
png = os.path.join(here, "png"); os.makedirs(png, exist_ok=True)
jobs = []
for s in (512, 256, 128, 64, 32, 16):
    src = "omniarena-logo-small.svg" if s <= 32 else "omniarena-logo.svg"
    jobs.append([os.path.join(here, src), os.path.join(png, f"omniarena-logo-{s}.png"), s, s, None])
    jobs.append([os.path.join(here, "omniarena-icon.svg"), os.path.join(png, f"omniarena-icon-{s}.png"), s, s, None])
jobs.append([os.path.join(here, "omniarena-lockup.svg"), os.path.join(png, "omniarena-lockup-1200.png"), 1200, round(1200 * 256 / LW), None])
subprocess.run(["node", os.path.join(here, "render-svg.js"), json.dumps(jobs)], check=True, env={**os.environ, "NODE_PATH": "/opt/node-tools/node_modules"})
def optimise(path, colors=16):
    im = Image.open(path).convert("RGBA")
    q = im.quantize(colors=colors, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE) if im.getextrema()[3][0] == 255 else im
    q.save(path, optimize=True)  # no metadata written
for fn in os.listdir(png):
    p = os.path.join(png, fn)
    if "lockup" in fn: Image.open(p).convert("RGBA").save(p, optimize=True)
    else: Image.open(p).convert("RGBA").save(p, optimize=True)
images = os.path.join(root, "images")
for s, name in ((512, "00-icon.png"), (256, "00-icon-256.png"), (64, "00-icon-64.png")):
    im = Image.open(os.path.join(png, f"omniarena-icon-{s}.png")).convert("RGB")
    im = im.quantize(colors=16, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    im.save(os.path.join(images, name), optimize=True)
    print(name, os.path.getsize(os.path.join(images, name)), "bytes")
