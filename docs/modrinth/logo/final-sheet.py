#!/usr/bin/env python3
"""Builds final-sheet.png: the final mark on paper/black/colour, one-colour versions, size ladder, lockups."""
import os, subprocess
here = os.path.dirname(os.path.abspath(__file__))
rd = lambda n, w=None: open(os.path.join(here, n)).read().replace('width="256" height="256"', f'width="{w or "100%"}" height="{w or "100%"}"') if w or n.endswith("logo.svg") else open(os.path.join(here, n)).read()
def inl(n, h):
    import re
    s = open(os.path.join(here, n)).read()
    return re.sub(r'width="\d+" height="\d+"', f'height="{h}"', s, 1)
html = f'''<!doctype html><meta charset="utf-8"><style>
body{{margin:0;width:1500px;background:#111;font-family:"Liberation Sans",Arial,sans-serif;font-weight:700}}
.r{{display:flex;gap:10px;padding:0 10px 10px}}.b{{padding:24px;display:flex;align-items:center;justify-content:center;gap:24px;flex-direction:column}}
.p{{background:#F4EFE6}}.k{{background:#111;outline:2px solid #444}}.y{{background:#F2C230}}.l{{font-size:14px;text-transform:uppercase;color:#111}}.k .l{{color:#F4EFE6}}
.h{{background:#D6352B;color:#F4EFE6;padding:14px 24px;font-size:28px;text-transform:uppercase;margin-bottom:10px}}</style>
<div class="h">OmniArena logo: final</div>
<div class="r"><div class="b p" style="flex:2">{inl("omniarena-logo.svg",300)}<div class="l">Colour mark</div></div>
<div class="b p" style="flex:1">{inl("omniarena-logo-black.svg",300)}<div class="l">Black</div></div>
<div class="b k" style="flex:1">{inl("omniarena-logo-white.svg",300)}<div class="l">White</div></div>
<div class="b p" style="flex:1">{inl("omniarena-icon.svg",300)}<div class="l">Modrinth icon</div></div></div>
<div class="r"><div class="b p" style="flex:1">{inl("omniarena-lockup.svg",110)}</div><div class="b k" style="flex:1">{inl("omniarena-lockup-white.svg",110)}</div></div>
<div class="r"><div class="b p" style="flex:1">{inl("omniarena-lockup-black.svg",80)}</div>
<div class="b y" style="flex:1">{inl("omniarena-lockup-black.svg",80)}</div>
<div class="b p" style="flex:1;flex-direction:row;align-items:flex-end">{inl("omniarena-logo.svg",128)}{inl("omniarena-logo.svg",64)}{inl("omniarena-logo-small.svg",32)}{inl("omniarena-logo-small.svg",16)}<div class="l">128 64 32 16 px</div></div></div>'''
open(os.path.join(here, "final-sheet.html"), "w").write(html)
subprocess.run(["node", "-e", """const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1500,height:800}});await p.goto('file://'+process.argv[1]);await p.screenshot({path:process.argv[2],fullPage:true});await b.close()})()""", os.path.join(here, "final-sheet.html"), os.path.join(here, "final-sheet.png")], check=True, env={**os.environ, "NODE_PATH": "/opt/node-tools/node_modules"})
os.remove(os.path.join(here, "final-sheet.html"))
