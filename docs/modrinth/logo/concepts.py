#!/usr/bin/env python3
"""Writes concepts/*.svg (grid 256, Bauhaus palette). Exploration only; the final mark is built by build-logo.py."""
import math, os
here = os.path.dirname(os.path.abspath(__file__))
R, B, Y, K, P = "#D6352B", "#1E4FA3", "#F2C230", "#111111", "#F4EFE6"
def svg(body, title):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256" role="img">'
            f'<title>{title}</title>{body}</svg>\n')
def f(v): return f"{v:.2f}".rstrip("0").rstrip(".")

def quartered(cols, rad=120, w=14):
    cx = cy = 128; c = w * math.sqrt(2)
    # intersection of line x-y=c (relative) with circle: y = (-c + sqrt(2R^2-c^2))/2 style
    d = math.sqrt(2 * rad * rad - c * c)
    a = (c + d) / 2   # x of point with y = a - c
    b = (d - c) / 2   # |y| of that point
    def pt(x, y): return f"{f(cx + x)} {f(cy + y)}"
    # sectors defined by apex and two circle points (clockwise)
    right = f"M{pt(c,0)} L{pt(a,-b)} A{rad} {rad} 0 0 1 {pt(a,b)} Z"
    bottom = f"M{pt(0,c)} L{pt(b,a)} A{rad} {rad} 0 0 1 {pt(-b,a)} Z"
    left = f"M{pt(-c,0)} L{pt(-a,b)} A{rad} {rad} 0 0 1 {pt(-a,-b)} Z"
    top = f"M{pt(0,-c)} L{pt(-b,-a)} A{rad} {rad} 0 0 1 {pt(b,-a)} Z"
    return top, right, bottom, left
t, r, b, l = quartered(None)
A = svg(f'<path fill="{Y}" d="{t}"/><path fill="{B}" d="{r}"/><path fill="{K}" d="{b}"/><path fill="{R}" d="{l}"/>', "Concept A Quartered")
Bc = svg(f'<circle cx="128" cy="128" r="120" fill="{K}"/><polygon points="44,74 44,182 112,128" fill="{R}"/>'
         f'<polygon points="212,74 212,182 144,128" fill="{B}"/><circle cx="128" cy="128" r="10" fill="{Y}"/>', "Concept B Face-off")
Cc = svg(f'<circle cx="128" cy="128" r="120" fill="{B}"/><g transform="translate(128 128) scale(.66) translate(-128 -128)">'
         f'<path fill="{P}" fill-rule="evenodd" d="M104 32 H152 L248 224 H200 L184 192 H72 L56 224 H8 Z M96 144 H160 L128 80 Z"/></g>'
         f'<circle cx="128" cy="172" r="12" fill="{Y}"/>', "Concept C Arena A")
D = svg(f'<rect x="16" y="112" width="112" height="112" fill="{Y}"/><circle cx="176" cy="88" r="64" fill="{R}"/>'
        f'<polygon points="128,224 240,224 184,128" fill="{B}"/>', "Concept D Trinity")
E = svg(f'<path fill="{K}" fill-rule="evenodd" d="M128 8 A120 120 0 1 1 127.9 8 Z M128 36 A92 92 0 1 0 128.1 36 Z"/>'
        f'<g transform="rotate(45 128 128)"><rect x="112" y="40" width="32" height="176" fill="{R}"/><rect x="40" y="112" width="176" height="32" fill="{B}"/></g>'
        f'<rect x="108" y="108" width="40" height="40" fill="{Y}" transform="rotate(45 128 128)"/>', "Concept E X marks")
for n, s in [("a-quartered", A), ("b-faceoff", Bc), ("c-arena-a", Cc), ("d-trinity", D), ("e-x-marks", E)]:
    open(os.path.join(here, "concepts", n + ".svg"), "w").write(s)
print("ok")
