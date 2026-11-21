#!/usr/bin/env python3
"""Builds concepts/concept-sheet.html from concepts/*.svg (needs render-svg.js for the 16/32 px true-size PNGs)."""
import os, subprocess, json, base64
here = os.path.dirname(os.path.abspath(__file__)); cd = os.path.join(here, "concepts"); tmp = os.path.join(cd, ".t"); os.makedirs(tmp, exist_ok=True)
items = [("a-quartered","A  Quartered","Disc split by an X into four colour fields: all modes, one arena, red vs blue."),
 ("b-faceoff","B  Face-off","Two spawn triangles aim at each other in the ring."),
 ("c-arena-a","C  Arena A","Letter A as a peak inside the arena disc."),
 ("d-trinity","D  Trinity","Bauhaus square, circle, triangle: everything in one."),
 ("e-x-marks","E  X marks","Crossed blades abstracted to an X in a ring.")]
jobs=[]
for k,_,_ in items:
    for s in (16,32,64):
        jobs.append([f"{cd}/{k}.svg", f"{tmp}/{k}-{s}.png", s, s, "#F4EFE6"])
    jobs.append([f"{cd}/{k}.svg", f"{tmp}/{k}-mono.png", 64, 64, "#F4EFE6"])
open(f"{tmp}/mono.txt","w")
# mono versions: recolour all fills to black
import re
for k,_,_ in items:
    s=open(f"{cd}/{k}.svg").read(); s=re.sub(r'fill="#(?!111111|F4EFE6)[0-9A-Fa-f]{6}"','fill="#111111"',s)
    open(f"{tmp}/{k}-mono.svg","w").write(s)
    jobs[[j[1] for j in jobs].index(f"{tmp}/{k}-mono.png")][0]=f"{tmp}/{k}-mono.svg"
subprocess.run(["node", os.path.join(here,"render-svg.js"), json.dumps(jobs)], check=True, env={**os.environ,"NODE_PATH":"/opt/node-tools/node_modules"})
def b64(p): return "data:image/png;base64,"+base64.b64encode(open(p,"rb").read()).decode()
cards=""
for k,n,idea in items:
    big=open(f"{cd}/{k}.svg").read().replace('width="256" height="256"','width="100%" height="100%"')
    cards+=f'''<div class="c"><div class="big">{big}</div><div class="n">{n}</div><div class="i">{idea}</div>
<div class="sz"><img src="{b64(f"{tmp}/{k}-64.png")}"><img src="{b64(f"{tmp}/{k}-32.png")}"><img src="{b64(f"{tmp}/{k}-16.png")}">
<img class="z" src="{b64(f"{tmp}/{k}-16.png")}"><img src="{b64(f"{tmp}/{k}-mono.png")}"></div></div>'''
html=f'''<!doctype html><meta charset="utf-8"><style>
body{{margin:0;background:#111;font-family:"Liberation Sans",Arial,sans-serif;font-weight:700;width:1700px}}
.w{{display:grid;grid-template-columns:repeat(5,1fr);gap:10px;padding:10px;background:#111}}
.h{{background:#F2C230;padding:16px 24px;font-size:30px;text-transform:uppercase;border-bottom:10px solid #111}}
.c{{background:#F4EFE6;padding:16px}}.big{{width:100%;aspect-ratio:1;background:#F4EFE6}}
.n{{font-size:24px;text-transform:uppercase;margin-top:10px}}.i{{font-size:15px;min-height:56px;margin:4px 0 10px;line-height:1.25}}
.sz{{display:flex;gap:10px;align-items:flex-end}}.z{{width:64px;height:64px;image-rendering:pixelated}}
.f{{background:#111;color:#F4EFE6;padding:10px 24px;font-size:15px;text-transform:uppercase}}</style>
<div class="h">OmniArena logo concepts (Bauhaus palette). Row under each: 64, 32, 16 px true size, 16 px x4 zoom, one-colour</div><div class="w">{cards}</div>
<div class="f">Pick: A (Quartered). See brief.md for the critique. One-colour = every colour field set to black, paper stays paper.</div>'''
open(f"{cd}/concept-sheet.html","w").write(html)
subprocess.run(["node","-e","""const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1700,height:900}});await p.goto('file://'+process.argv[1]);await p.screenshot({path:process.argv[2],fullPage:true});await b.close()})()""",f"{cd}/concept-sheet.html",os.path.join(here,"concepts-sheet.png")],check=True,env={**os.environ,"NODE_PATH":"/opt/node-tools/node_modules"})
