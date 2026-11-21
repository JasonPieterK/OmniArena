#!/usr/bin/env python3
"""Generates the NN-*.html sources next to this file. Then run render.js."""
import os
here = os.path.dirname(os.path.abspath(__file__))
R, B, Y, K, P = "#D6352B", "#1E4FA3", "#F2C230", "#111111", "#F4EFE6"
ICON = open(os.path.join(here, "icon.svg")).read().replace('width="512" height="512"', 'width="100%" height="100%"')

def page(name, css, body):
    open(os.path.join(here, name), "w").write(
        f'<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="common.css"><style>{css}</style></head>'
        f'<body><div class="frame">{body}</div></body></html>')

def el(k, c, cx, cy, r):
    s = f'fill="{c}" stroke="{K}" stroke-width="6" stroke-linejoin="miter"'
    if k == "c": return f'<circle cx="{cx}" cy="{cy}" r="{r}" {s}/>'
    if k == "s": return f'<rect x="{cx-r}" y="{cy-r}" width="{2*r}" height="{2*r}" {s}/>'
    if k == "t": return f'<polygon points="{cx},{cy-r} {cx+r},{cy+r} {cx-r},{cy+r}" {s}/>'
    if k == "d": return f'<polygon points="{cx},{cy-r} {cx+r},{cy} {cx},{cy+r} {cx-r},{cy}" {s}/>'
    if k == "h": return f'<path d="M{cx-r} {cy+r/2} A{r} {r} 0 0 1 {cx+r} {cy+r/2} Z" {s}/>'
    if k == "r": return f'<rect x="{cx-r}" y="{cy-r/3}" width="{2*r}" height="{2*r/1.5}" {s}/>'
def ico(*els, size=110):
    return f'<svg class="shape" viewBox="0 0 100 100" width="{size}" height="{size}">{"".join(els)}</svg>'

def head(title, sub, num, numbg=R):
    return (f'<div class="head"><div class="t">{title}</div><div class="s">{sub}</div>'
            f'<div class="n" style="background:{numbg}">{num}</div></div>')

# ---------- 01 hero ----------
page("01-hero.html", """
.frame{height:640px}
.title{position:absolute;left:56px;top:50px;font-size:128px;line-height:.95;text-transform:uppercase;letter-spacing:-.01em}
.two{position:absolute;left:56px;top:330px;width:150px;height:150px;border-radius:50%;background:#D6352B;color:#F4EFE6;font-size:58px;display:flex;align-items:center;justify-content:center;border:10px solid #111}
.tag{position:absolute;left:236px;top:340px;font-size:40px;line-height:1.3;text-transform:uppercase;width:760px}
.tag b{background:#F2C230;padding:0 8px}
.tag small{display:block;font-size:24px;margin-top:10px;font-weight:700}
.bigc{position:absolute;right:70px;top:36px;width:480px;height:480px;overflow:hidden;background:#F4EFE6}
.sq{position:absolute;right:480px;top:330px;width:190px;height:190px;background:#1E4FA3;border:12px solid #111}
.trg{position:absolute;right:560px;top:50px;--s:60px}
.bar{position:absolute;left:0;right:0;bottom:0;height:100px;background:#111;color:#F4EFE6;display:flex;align-items:center;padding:0 56px;font-size:29px;text-transform:uppercase;gap:24px;white-space:nowrap}
.bar i{display:inline-block;width:20px;height:20px;background:#F2C230;border-radius:50%}
.bar i.s{border-radius:0;background:#D6352B}.bar i.t{background:#4C7FD6;border-radius:0}
""", f"""<div class="sq"></div><div class="tri trg"></div><div class="bigc">{ICON}</div>
<div class="title">OmniArena</div><div class="two">1.0</div>
<div class="tag">PVP arenas.<br><b>Zero hassle.</b><small>Menus, ranked, tournaments, auto-rebuild.</small></div>
<div class="bar"><span>Arenas</span><i></i><span>Ranked</span><i class="s"></i><span>Tournaments</span><i class="t"></i><span>4 server types</span><i></i><span>6 versions</span></div>""")

# ---------- 02 recap ----------
cards = [
 ("Setup wizard", "Wand, spawns, finish. Five minutes.", Y, [el("s",B,42,56,28), el("c",R,66,36,18)]),
 ("16 game modes", "Duel, crystal, mace, sumo, spleef.", B, [el("c",Y,50,50,34), el("t",R,50,54,18)]),
 ("Ranked ELO", "Per-kit queue. Parties welcome.", R, [el("d",Y,50,50,38), el("c",P,50,50,15)]),
 ("Tournaments", "Single-elimination brackets.", P, [el("s",B,32,34,16), el("s",R,32,68,16), el("s",Y,70,51,20)]),
 ("Betting", "Vault pools. Draws refund.", P, [el("c",R,50,50,36), el("s",K,50,50,14)]),
 ("Kits", "Personal, preset, shareable.", Y, [el("s",R,35,50,22), el("t",B,68,52,24)]),
 ("Auto regen", "10 animated rebuild styles.", B, [el("c",P,50,50,36), el("h",Y,50,46,30)]),
 ("Menus and books", "Chest menus. In-game guides.", R, [el("s",Y,34,34,18), el("s",B,66,34,18), el("s",P,34,68,18), el("s",K,66,68,18)]),
 ("Leaderboards", "Placeholders and holograms.", R, [el("s",Y,28,66,14), el("s",P,50,50,14), el("s",K,72,34,14)]),
 ("NPCs", "Citizens NPCs run commands.", Y, [el("c",B,50,34,18), el("h",R,50,64,32)]),
 ("Self-healing", "Watchdog and /oa doctor.", B, [el("c",R,50,50,36), el("s",P,50,50,14)]),
 ("Dashboard + API", "Web editor. Events for plugins.", P, [el("t",R,50,50,38), el("c",Y,50,62,14)]),
]
cells = "".join(
    f'<div class="card"><div class="ib" style="background:{bg}">{ico(*els, size=96)}<span class="num">{i+1:02d}</span></div>'
    f'<div class="ct">{t}</div><div class="cl">{l}</div></div>' for i, (t, l, bg, els) in enumerate(cards))
page("02-recap.html", """
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;background:#111;border-bottom:0}
.card{background:#F4EFE6;padding:0 0 18px}
.ib{height:130px;border-bottom:8px solid #111;display:flex;align-items:center;justify-content:center;position:relative}
.num{position:absolute;left:14px;top:10px;font-size:22px;background:#111;color:#F4EFE6;padding:2px 8px}
.ct{font-size:31px;text-transform:uppercase;padding:14px 20px 4px;line-height:1.05}
.cl{font-size:21px;padding:0 20px;line-height:1.25}
""", head("Product recap", "Everything in one jar", "12", R) + f'<div class="grid">{cells}</div>'
 + '<div class="note">OmniArena 1.0 / Paper, Purpur, Spigot, Bukkit / Minecraft 1.21.9 to 26.3</div>')

# ---------- 03 setup ----------
def step(n, bg, fg, title, lines, tail):
    ls = "".join(f'<div class="ln">{l}</div>' for l in lines)
    return (f'<div class="st" style="background:{bg};color:{fg}"><div class="bn">{n}</div><div class="stt">{title}</div>{ls}'
            f'<div class="tl">{tail}</div></div>')
page("03-setup.html", """
.row{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;background:#111;padding:12px 0 0}
.st{padding:28px 32px 28px;border:0;min-height:520px;position:relative}
.bn{width:92px;height:92px;border-radius:50%;background:#111;color:#F4EFE6;font-size:56px;display:flex;align-items:center;justify-content:center;margin-bottom:18px}
.stt{font-size:44px;text-transform:uppercase;margin-bottom:18px;line-height:1}
.ln{font-size:25px;line-height:1.3;margin:0 0 12px}
.ln .chip{font-size:24px;background:#111;color:#F4EFE6}
.tl{position:absolute;left:32px;right:32px;bottom:26px;font-size:20px;text-transform:uppercase;border-top:6px solid #111;padding-top:10px}
""", head("Setup in 3 steps", "About five minutes. No config files.", "3", B) + '<div class="row">' +
 step(1, Y, K, "Setup", ['<span class="chip">/oa setup duel_1</span>', "Creates the arena, gives you the wand, opens the setup menu.", "Left-click one corner, right-click the opposite one.", '<span class="chip">/oa setup duel_1 region</span>'], "Also takes the rebuild snapshot") +
 step(2, R, P, "Spawns", ["Stand on each start pad:", '<span class="chip">/oa setup duel_1 spawn</span>', "Optional: lobby and spectator spawn.", '<span class="chip">/oa setup duel_1 lobby</span>'], "Players are spread over the pads") +
 step(3, B, P, "Enable", ['<span class="chip">/oa setup duel_1 finish</span>', "Checks the requirements and opens the arena.", "Players join with:", '<span class="chip">/oa join duel_1</span>'], "Or a menu, a sign, an NPC") + '</div>'
 + '<div class="note">Something missing? <span style="color:#F2C230">/oa doctor</span> lists every gap with a clickable fix. The same steps are a book: /oa guide start</div>')

# ---------- 04 match flow ----------
flow = [("Join", "Countdown starts when min players are in", Y, K), ("Start", "Teams, spawns, loadout, spawn protection", B, P),
        ("Running", "Kills, lives, timer, bets open", R, P), ("End", "ELO, bets, titles, rewards, stats", K, P), ("Rebuild", "Arena restored from snapshot, reopens", P, K)]
fl = ""
for i, (t, d, bg, fg) in enumerate(flow):
    fl += f'<div class="fb" style="background:{bg};color:{fg}"><div class="fn">{i+1}</div><div class="ft">{t}</div><div class="fd">{d}</div></div>'
    if i < 4: fl += '<div class="ar"><svg viewBox="0 0 40 60" width="40" height="60"><polygon points="0,18 22,18 22,2 40,30 22,58 22,42 0,42" fill="#111"/></svg></div>'
outs = [("WIN", P, K), ("LOSS", P, K), ("DRAW", Y, K), ("FORFEIT", R, P), ("TIMEOUT", B, P)]
oc = "".join(f'<div class="oc" style="background:{b};color:{f}">{t}</div>' for t, b, f in outs)
page("04-match-flow.html", """
.fl{display:flex;align-items:center;padding:36px 28px;gap:6px}
.fb{flex:1;border:10px solid #111;padding:20px 18px;height:330px;position:relative}
.fn{font-size:70px;line-height:1}.ft{font-size:36px;text-transform:uppercase;margin:14px 0 10px}.fd{font-size:21px;line-height:1.3}
.ar{width:40px;flex:none}
.out{display:grid;grid-template-columns:260px repeat(5,1fr);border-top:12px solid #111}
.out .lb{background:#111;color:#F4EFE6;font-size:28px;text-transform:uppercase;display:flex;align-items:center;padding:0 24px;line-height:1.15}
.oc{height:110px;display:flex;align-items:center;justify-content:center;font-size:34px;border-left:12px solid #111}
""", head("How a match runs", "Every step is automatic", "5", B) + f'<div class="fl">{fl}</div><div class="out"><div class="lb">Possible outcomes</div>{oc}</div>'
 + '<div class="note">Crash or disconnect mid-match? Your saved state (inventory, location, health) is restored when you next join.</div>')

# ---------- 05 modes ----------
modes = [("Deathmatch","KILLS 15"),("Team Deathmatch","KILLS 30"),("Last One Standing","LAST ALIVE"),("Sumo","LAST ALIVE / RING-OUT"),
 ("Classic PvP","KILLS 10"),("Crystal PvP","KILLS 5"),("Mace","KILLS 10"),("Sword Duel","LAST ALIVE"),
 ("Axe and Shield","LAST ALIVE"),("UHC","LAST ALIVE / NO REGEN"),("Potion PvP","KILLS 10"),("NoDebuff","KILLS 10"),
 ("Archer","KILLS 12"),("Netherite OP","KILLS 20"),("Spleef","LAST ALIVE / RING-OUT"),("Gladiator","KILLS 5")]
mc = ""
for i, (n, g) in enumerate(modes):
    last = g.startswith("LAST")
    bg, fg = (R, P) if last else (B, P)
    if n == "UHC": bg, fg = K, P
    if n in ("Sumo", "Spleef"): bg, fg = Y, K
    mc += f'<div class="mo" style="background:{bg};color:{fg}"><div class="mi">{i+1:02d}</div><div class="mn">{n}</div><div class="mg">{g}</div></div>'
page("05-modes.html", """
.g{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;background:#111}
.mo{padding:18px 22px;height:150px;position:relative}
.mi{font-size:20px;opacity:.85}.mn{font-size:32px;text-transform:uppercase;line-height:1.05;margin:6px 0 10px}.mg{font-size:19px;letter-spacing:.04em}
.lg{display:flex;gap:30px;align-items:center;padding:0 40px;height:84px;background:#F4EFE6;font-size:22px;text-transform:uppercase;border-top:12px solid #111}
.lg i{display:inline-block;width:26px;height:26px;margin-right:10px;vertical-align:middle;border:4px solid #111}
""", head("16 game modes", "Each has its own loadout and rules", "16", B) + f'<div class="g">{mc}</div>'
 + f'<div class="lg"><span><i style="background:{B}"></i>First to N kills</span><span><i style="background:{R}"></i>Last one alive</span><span><i style="background:{Y}"></i>Ring-out falls</span><span><i style="background:{K}"></i>No regeneration</span></div>'
 + '<div class="note">Plus: lives, teams, respawn waves, per-arena rules, killstreak and combo modules</div>')

# ---------- 06 ranked + tournament ----------
def bm(a, b, w=None):
    return f'<div class="bm"><div class="bp">{a}</div><div class="bp">{b}</div></div>'
bracket = f"""<div class="br"><div class="bc"><div class="rl">Round 1</div><div class="bcm">{bm("P1","P2")}{bm("P3","P4")}{bm("P5","P6")}{bm("P7","P8")}</div></div>
<div class="bc"><div class="rl">Semi-finals</div><div class="bcm">{bm("W1","W2")}{bm("W3","W4")}</div></div>
<div class="bc"><div class="rl">Final</div><div class="bcm">{bm("W5","W6")}</div></div>
<div class="bc"><div class="rl">Champion</div><div class="bcm"><div class="champ">1st</div></div></div></div>"""
page("06-ranked.html", """
.two{display:grid;grid-template-columns:640px 1fr}
.l{background:#1E4FA3;color:#F4EFE6;padding:30px 36px;border-right:12px solid #111}
.l h3{font-size:42px;margin-bottom:16px}
.big{font-size:74px;line-height:1;color:#F2C230}.sm{font-size:22px;margin:4px 0 18px;line-height:1.25}
.r{background:#F4EFE6;padding:30px 30px}
.r h3{font-size:42px;margin-bottom:14px}
.br{display:flex;gap:26px;height:470px}
.bc{flex:1;display:flex;flex-direction:column}.bcm{flex:1;display:flex;flex-direction:column;justify-content:space-around}
.rl{font-size:19px;text-transform:uppercase;text-align:center;background:#111;color:#F4EFE6;padding:4px}
.bm{border:6px solid #111;background:#fff0}
.bp{padding:8px 12px;font-size:22px;background:#F4EFE6}.bp+.bp{border-top:6px solid #111;background:#F2C230}
.champ{background:#D6352B;color:#F4EFE6;border:6px solid #111;font-size:48px;text-align:center;padding:30px 0}
""", head("Ranked and tournaments", "Queue it. Bracket it.", "<span style=\"font-size:54px\">ELO</span>", R) + f"""<div class="two"><div class="l"><h3>Ranked queue</h3>
<div class="big">200</div><div class="sm">starting ELO gap, plus 50 every 10 seconds of waiting</div>
<div class="big">1000</div><div class="sm">maximum gap. Start rating 1000, floor 100, K = 32</div>
<div class="big">PARTY</div><div class="sm">The leader queues everyone as one entry, rated by the average ELO</div>
<div class="sm" style="margin:0"><span class="chip" style="color:#F4EFE6">/oa queue [kit]</span></div></div>
<div class="r"><h3>Tournament</h3>{bracket}</div></div>
<div class="note">Seeding by join order. Byes for odd fields. Prizes for 1st, 2nd and 3rd via Vault. Saved and resumed after a restart.</div>""")

# ---------- 07 menus ----------
def slot(label="", bg="#9AA0A6", fg=K):
    return f'<div class="sl" style="background:{bg};color:{fg}">{label}</div>'
grid = [["", "", "", "", "", "", "", "", ""],
        ["", "JOIN|"+B, "SPEC|"+B, "QUEUE|"+Y, "LEAVE|"+R, "KITS|"+Y, "STATS|"+B, "PARTY|"+Y, ""],
        ["", "", "TOUR|"+Y, "", "ON|#58B947", "", "OFF|"+R, "", ""],
        ["BACK|"+P, "", "", "", "HELP|"+P, "", "", "", "CLOSE|"+K]]
gh = ""
for row in grid:
    for c in row:
        if not c: gh += slot()
        else:
            l, bg = c.split("|") if "|" in c else (c, "#9AA0A6")
            fg = P if bg in (B, R, K) else K
            if l in ("ON", "OFF"): fg = K if l == "ON" else P
            gh += slot(l, bg, fg)
leg = [("#9AA0A6", K, "Gray glass", "Every empty slot"), ("#58B947", K, "Green concrete", "A setting that is on"), (R, P, "Red concrete", "A setting that is off"),
       (Y, K, "Any other item", "A distinct button. Lore ends in a yellow Click line"), (P, K, "Footer", "Back left, Help centre, Close right")]
lg = "".join(f'<div class="lr"><div class="lc" style="background:{b};color:{f}"></div><div><div class="lt">{t}</div><div class="ld">{d}</div></div></div>' for b, f, t, d in leg)
page("07-menus.html", """
.wrap{display:flex}
.ch{padding:34px;background:#F4EFE6;border-right:12px solid #111}
.gr{display:grid;grid-template-columns:repeat(9,92px);gap:6px;background:#111;padding:6px;border:6px solid #111;width:max-content}
.sl{width:92px;height:92px;display:flex;align-items:center;justify-content:center;font-size:17px;text-align:center}
.lgd{flex:1;padding:34px 40px;display:flex;flex-direction:column;justify-content:space-between}
.lr{display:flex;gap:20px;align-items:center}
.lc{width:64px;height:64px;border:6px solid #111;flex:none}
.lt{font-size:28px;text-transform:uppercase}.ld{font-size:20px;line-height:1.2;margin-top:2px}
""", head("Menu first", "Every button has a text-command twin", "/oa", R) + f'<div class="wrap"><div class="ch"><div class="gr">{gh}</div></div><div class="lgd">{lg}</div></div>'
 + '<div class="note">/oa opens the main menu. Setup, edit, kits and settings are menus. Typos get a clickable did-you-mean.</div>')

# ---------- 08 integrations ----------
ints = [("Vault", "Bets, prizes, money rewards", Y, K), ("PlaceholderAPI", "Stats, ELO, live match, top 10", B, P), ("WorldGuard", "Anti border-hopping while tagged", R, P),
        ("DecentHolograms", "Leaderboard holograms", P, K), ("FancyHolograms", "Same, if Decent is missing", P, K), ("Citizens", "NPCs: menu, join, queue, spectate", Y, K),
        ("Multiverse", "Loads an arena world on demand", B, P), ("TAB", "Takes over scoreboard and boss bar", R, P), ("LibsDisguises", "Undisguise when combat starts", K, P)]
ic = "".join(f'<div class="ii" style="background:{b};color:{f}"><div class="in">{n}</div><div class="id">{d}</div></div>' for n, d, b, f in ints)
page("08-integrations.html", """
.g{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;background:#111}
.ii{height:170px;padding:24px 28px}
.in{font-size:38px;text-transform:uppercase;margin-bottom:10px;line-height:1}.id{font-size:24px;line-height:1.25}
""", head("Integrations", "All optional. All isolated.", "9", B) + f'<div class="g">{ic}</div>'
 + '<div class="note">Each hook has an integrations.* switch. An error is logged once; after 3 failures in a row the hook switches itself off. /oa doctor shows each state.</div>')

# ---------- 09 matrix ----------
plats = [("Paper", B, P, True), ("Purpur", R, P, False), ("Spigot", Y, K, True), ("Bukkit", K, P, True)]
vers = [("26.3", 25), ("26.2", 25), ("26.1", 25), ("1.21.11", 21), ("1.21.10", 21), ("1.21.9", 21)]
cir = lambda ok, col: (f'<svg viewBox="0 0 100 100" width="58" height="58"><circle cx="50" cy="50" r="40" fill="{col if ok else "none"}" stroke="{K}" stroke-width="8" {"" if ok else "stroke-dasharray=\"14 10\""}/></svg>')
mh = '<div class="c h" style="background:#111;color:#F4EFE6">Minecraft</div>' + "".join(f'<div class="c h" style="background:{b};color:{f}">{n}</div>' for n, b, f, _ in plats)
mr = ""
for v, j in vers:
    mr += f'<div class="c v"><span>{v}</span><small>Java {j}</small></div>' + "".join(f'<div class="c">{cir(ok, b if b != K else K)}</div>' for n, b, f, ok in plats)
page("09-matrix.html", """
.m{display:grid;grid-template-columns:330px repeat(4,1fr)}
.c{height:92px;border-right:12px solid #111;border-bottom:12px solid #111;display:flex;align-items:center;justify-content:center;font-size:28px;text-transform:uppercase;text-align:center;line-height:1.1}
.c:nth-child(5n){border-right:0}.c.h{height:100px}
.c.v{justify-content:flex-start;gap:20px;font-size:44px;padding-left:34px}.c.v small{white-space:nowrap;font-size:17px;background:#111;color:#F4EFE6;padding:4px 8px}
""", head("Platforms and versions", "24 jars: 4 server types times 6 versions", "24", R) + f'<div class="m">{mh}{mr}</div>'
 + f'<div class="note" style="border-top:0">File name: <span style="color:#F2C230;text-transform:none">OmniArena-paper-26.2.jar</span><br>Solid circle: compiled. Dashed circle: Purpur not verified yet (its repository was unreachable). Not run on a live server yet.</div>')

# ---------- 10 divider ----------
page("10-divider.html", """
.frame{height:84px;background:#111;display:flex;align-items:center;justify-content:center;gap:36px;border-width:0}
.d{width:28px;height:28px}
""", f'<div class="d" style="background:{Y};border-radius:50%"></div><div class="d" style="background:{R}"></div><div class="tri" style="--s:16px;border-bottom-color:#4C7FD6"></div><div class="d" style="background:{Y};border-radius:50%"></div><div class="d" style="background:{R}"></div><div class="tri" style="--s:16px;border-bottom-color:#4C7FD6"></div><div class="d" style="background:{Y};border-radius:50%"></div>')
