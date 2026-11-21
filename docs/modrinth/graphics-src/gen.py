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

PH_DIR = os.path.join(here, "phosphor")
def ph(name, color=K, size=96, extra="", weight="bold"):
    """Inline Phosphor Icons (Bold, MIT, vendored in ./phosphor): offline-safe SVG, one weight everywhere."""
    s = open(os.path.join(PH_DIR, f"{name}-{weight}.svg" if weight != "bold" else f"{name}-bold.svg")).read()
    inner = s[s.index(">") + 1:s.rindex("</svg>")]
    return (f'<svg class="ph" viewBox="0 0 256 256" width="{size}" height="{size}" fill="{color}" {extra}>{inner}</svg>')

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
.bar span{display:flex;align-items:center;gap:12px}.bar{justify-content:space-between}
""", f"""<div class="sq"></div><div class="tri trg"></div><div class="bigc">{ICON}</div>
<div class="title">OmniArena</div><div class="two">1.0</div>
<div class="tag">PVP arenas.<br><b>Zero hassle.</b><small>Menus, ranked, tournaments, auto-rebuild.</small></div>
<div class="bar"><span>{ph("flag-checkered",Y,40)}Arenas</span><span>{ph("ranking",Y,40)}Ranked</span><span>{ph("trophy",Y,40)}Tournaments</span><span>{ph("hard-drives",Y,40)}4 server types</span><span>{ph("stack",Y,40)}6 versions</span></div>""")

# ---------- 02 recap ----------
cards = [
 ("Setup wizard", "Wand, spawns, finish. Five minutes.", Y, "magic-wand"),
 ("16 game modes", "Duel, crystal, mace, sumo, spleef.", B, "game-controller"),
 ("Ranked ELO", "Per-kit queue. Parties welcome.", R, "ranking"),
 ("Tournaments", "Single-elimination brackets.", P, "trophy"),
 ("Betting", "Vault pools. Draws refund.", P, "coins"),
 ("Kits", "Personal, preset, shareable.", Y, "backpack"),
 ("Auto regen", "10 animated rebuild styles.", B, "arrows-clockwise"),
 ("Menus and books", "Chest menus. In-game guides.", R, "book-open"),
 ("Leaderboards", "Placeholders and holograms.", R, "chart-bar"),
 ("NPCs", "Citizens NPCs run commands.", Y, "user-focus"),
 ("Self-healing", "Watchdog and /oa doctor.", B, "first-aid"),
 ("Dashboard + API", "Web editor. Events for plugins.", P, "globe"),
]
fgof = lambda bg: P if bg in (B, R, K) else K
cells = "".join(
    f'<div class="card"><div class="ib" style="background:{bg}">{ph(ico_n, fgof(bg), 108)}<span class="num">{i+1:02d}</span></div>'
    f'<div class="ct">{t}</div><div class="cl">{l}</div></div>' for i, (t, l, bg, ico_n) in enumerate(cards))
page("02-recap.html", """
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;background:#111;border-bottom:0}
.card{background:#F4EFE6;padding:0 0 18px}
.ib{height:156px;border-bottom:8px solid #111;display:flex;align-items:center;justify-content:center;position:relative}
.num{position:absolute;left:14px;top:10px;font-size:22px;background:#111;color:#F4EFE6;padding:2px 8px}
.ct{font-size:31px;text-transform:uppercase;padding:14px 20px 4px;line-height:1.05}
.cl{font-size:21px;padding:0 20px;line-height:1.25}
""", head("Product recap", "Everything in one jar", "12", R) + f'<div class="grid">{cells}</div>'
 + '<div class="note">OmniArena 1.0 / Paper, Purpur, Spigot, Bukkit / Minecraft 1.21.9 to 26.3</div>')

# ---------- 03 setup ----------
def step(n, bg, fg, title, lines, tail, icon):
    ls = "".join(f'<div class="ln">{l}</div>' for l in lines)
    return (f'<div class="st" style="background:{bg};color:{fg}"><div class="bn">{n}</div><div class="sic">{ph(icon, fg, 120)}</div><div class="stt">{title}</div>{ls}'
            f'<div class="tl">{tail}</div></div>')
page("03-setup.html", """
.row{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;background:#111;padding:12px 0 0}
.st{padding:28px 32px 28px;border:0;min-height:520px;position:relative}
.sic{position:absolute;right:32px;top:28px}
.bn{width:92px;height:92px;border-radius:50%;background:#111;color:#F4EFE6;font-size:56px;display:flex;align-items:center;justify-content:center;margin-bottom:18px}
.stt{font-size:44px;text-transform:uppercase;margin-bottom:18px;line-height:1}
.ln{font-size:25px;line-height:1.3;margin:0 0 12px}
.ln .chip{font-size:24px;background:#111;color:#F4EFE6}
.tl{position:absolute;left:32px;right:32px;bottom:26px;font-size:20px;text-transform:uppercase;border-top:6px solid #111;padding-top:10px}
""", head("Setup in 3 steps", "About five minutes. No config files.", "3", B) + '<div class="row">' +
 step(1, Y, K, "Setup", ['<span class="chip">/oa setup duel_1</span>', "Creates the arena, gives you the wand, opens the setup menu.", "Left-click one corner, right-click the opposite one.", '<span class="chip">/oa setup duel_1 region</span>'], "Also takes the rebuild snapshot", "magic-wand") +
 step(2, R, P, "Spawns", ["Stand on each start pad:", '<span class="chip">/oa setup duel_1 spawn</span>', "Optional: lobby and spectator spawn.", '<span class="chip">/oa setup duel_1 lobby</span>'], "Players are spread over the pads", "map-pin") +
 step(3, B, P, "Enable", ['<span class="chip">/oa setup duel_1 finish</span>', "Checks the requirements and opens the arena.", "Players join with:", '<span class="chip">/oa join duel_1</span>'], "Or a menu, a sign, an NPC", "flag-checkered") + '</div>'
 + '<div class="note">Something missing? <span style="color:#F2C230">/oa doctor</span> lists every gap with a clickable fix. The same steps are a book: /oa guide start</div>')

# ---------- 04 match flow ----------
flow = [("Join", "Countdown starts when min players are in", Y, K, "sign-in"), ("Start", "Teams, spawns, loadout, spawn protection", B, P, "play"),
        ("Running", "Kills, lives, timer, bets open", R, P, "sword"), ("End", "ELO, bets, titles, rewards, stats", K, P, "trophy"), ("Rebuild", "Arena restored from snapshot, reopens", P, K, "arrows-clockwise")]
fl = ""
for i, (t, d, bg, fg, icn) in enumerate(flow):
    fl += f'<div class="fb" style="background:{bg};color:{fg}"><div class="fn">{i+1}</div><div class="fic">{ph(icn, fg, 76)}</div><div class="ft">{t}</div><div class="fd">{d}</div></div>'
    if i < 4: fl += f'<div class="ar">{ph("arrow-right", K, 40)}</div>'
outs = [("WIN", P, K), ("LOSS", P, K), ("DRAW", Y, K), ("FORFEIT", R, P), ("TIMEOUT", B, P)]
oc = "".join(f'<div class="oc" style="background:{b};color:{f}">{t}</div>' for t, b, f in outs)
page("04-match-flow.html", """
.fl{display:flex;align-items:center;padding:36px 28px;gap:6px}
.fb{flex:1;border:10px solid #111;padding:20px 18px;height:330px;position:relative}
.fn{font-size:70px;line-height:1}.ft{font-size:36px;text-transform:uppercase;margin:14px 0 10px}.fd{font-size:21px;line-height:1.3}
.ar{width:40px;flex:none}.fic{position:absolute;right:16px;top:16px}
.out{display:grid;grid-template-columns:260px repeat(5,1fr);border-top:12px solid #111}
.out .lb{background:#111;color:#F4EFE6;font-size:28px;text-transform:uppercase;display:flex;align-items:center;padding:0 24px;line-height:1.15}
.oc{height:110px;display:flex;align-items:center;justify-content:center;font-size:34px;border-left:12px solid #111}
""", head("How a match runs", "Every step is automatic", "5", B) + f'<div class="fl">{fl}</div><div class="out"><div class="lb">Possible outcomes</div>{oc}</div>'
 + '<div class="note">Crash or disconnect mid-match? Your saved state (inventory, location, health) is restored when you next join.</div>')

# ---------- 05 modes ----------
modes = [("Deathmatch","KILLS 15","skull"),("Team Deathmatch","KILLS 30","users-three"),("Last One Standing","LAST ALIVE","crown-simple"),("Sumo","LAST ALIVE / RING-OUT","arrows-out-cardinal"),
 ("Classic PvP","KILLS 10","sword"),("Crystal PvP","KILLS 5","diamond"),("Mace","KILLS 10","hammer"),("Sword Duel","LAST ALIVE","sword"),
 ("Axe and Shield","LAST ALIVE","shield"),("UHC","LAST ALIVE / NO REGEN","heartbeat"),("Potion PvP","KILLS 10","flask"),("NoDebuff","KILLS 10","prohibit"),
 ("Archer","KILLS 12","target"),("Netherite OP","KILLS 20","fire"),("Spleef","LAST ALIVE / RING-OUT","snowflake"),("Gladiator","KILLS 5","medal")]
mc = ""
for i, (n, g, icn) in enumerate(modes):
    last = g.startswith("LAST")
    bg, fg = (R, P) if last else (B, P)
    if n == "UHC": bg, fg = K, P
    if n in ("Sumo", "Spleef"): bg, fg = Y, K
    mc += f'<div class="mo" style="background:{bg};color:{fg}"><div class="mi">{i+1:02d}</div><div class="mic">{ph(icn, fg, 60)}</div><div class="mn">{n}</div><div class="mg">{g}</div></div>'
page("05-modes.html", """
.g{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;background:#111}
.mo{padding:18px 22px;height:156px;position:relative}
.mic{position:absolute;right:18px;bottom:14px}
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
<div class="bc"><div class="rl">Champion</div><div class="bcm"><div class="champ">{ph("trophy",P,64)}<div>1st</div></div></div></div></div>"""
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
.champ{background:#D6352B;color:#F4EFE6;border:6px solid #111;font-size:48px;text-align:center;padding:24px 0;display:flex;flex-direction:column;align-items:center;gap:6px}
""", head("Ranked and tournaments", "Queue it. Bracket it.", "<span style=\"font-size:54px\">ELO</span>", R) + f"""<div class="two"><div class="l"><h3>{ph("ranking",P,52,'style="vertical-align:-8px;margin-right:14px"')}Ranked queue</h3>
<div class="big">200</div><div class="sm">starting ELO gap, plus 50 every 10 seconds of waiting</div>
<div class="big">1000</div><div class="sm">maximum gap. Start rating 1000, floor 100, K = 32</div>
<div class="big">{ph("users-three",Y,70,'style="vertical-align:-8px;margin-right:16px"')}PARTY</div><div class="sm">The leader queues everyone as one entry, rated by the average ELO</div>
<div class="sm" style="margin:0"><span class="chip" style="color:#F4EFE6">/oa queue [kit]</span></div></div>
<div class="r"><h3>{ph("trophy",K,52,'style="vertical-align:-8px;margin-right:14px"')}Tournament</h3>{bracket}</div></div>
<div class="note">Seeding by join order. Byes for odd fields. Prizes for 1st, 2nd and 3rd via Vault. Saved and resumed after a restart.</div>""")

# ---------- 07 menus ----------
def slot(label="", bg="#9AA0A6", fg=K):
    ic = ph(ICO[label], fg, 44) if label in ICO else ""
    return f'<div class="sl" style="background:{bg};color:{fg}">{ic}<span>{label}</span></div>'
ICO = {"JOIN": "sign-in", "SPEC": "eye", "QUEUE": "hourglass", "LEAVE": "sign-out", "KITS": "backpack", "STATS": "chart-bar", "PARTY": "users-three",
       "TOUR": "trophy", "ON": "check", "OFF": "x", "BACK": "arrow-left", "HELP": "book-open", "CLOSE": "prohibit"}
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
.sl{width:92px;height:92px;display:flex;flex-direction:column;gap:4px;align-items:center;justify-content:center;font-size:15px;text-align:center}
.lgd{flex:1;padding:34px 40px;display:flex;flex-direction:column;justify-content:space-between}
.lr{display:flex;gap:20px;align-items:center}
.lc{width:64px;height:64px;border:6px solid #111;flex:none}
.lt{font-size:28px;text-transform:uppercase}.ld{font-size:20px;line-height:1.2;margin-top:2px}
""", head("Menu first", "Every button has a text-command twin", "/oa", R) + f'<div class="wrap"><div class="ch"><div class="gr">{gh}</div></div><div class="lgd">{lg}</div></div>'
 + '<div class="note">/oa opens the main menu. Setup, edit, kits and settings are menus. Typos get a clickable did-you-mean.</div>')

# ---------- 08 integrations ----------
ints = [("Vault", "Bets, prizes, money rewards", Y, K, "vault"), ("PlaceholderAPI", "Stats, ELO, live match, top 10", B, P, "brackets-curly"), ("WorldGuard", "Anti border-hopping while tagged", R, P, "shield-check"),
        ("DecentHolograms", "Leaderboard holograms", P, K, "projector-screen"), ("FancyHolograms", "Same, if Decent is missing", P, K, "sparkle"), ("Citizens", "NPCs: menu, join, queue, spectate", Y, K, "user-focus"),
        ("Multiverse", "Loads an arena world on demand", B, P, "planet"), ("TAB", "Takes over scoreboard and boss bar", R, P, "app-window"), ("LibsDisguises", "Undisguise when combat starts", K, P, "mask-happy")]
ic = "".join(f'<div class="ii" style="background:{b};color:{f}"><div class="iic">{ph(icn, f, 64)}</div><div class="in">{n}</div><div class="id">{d}</div></div>' for n, d, b, f, icn in ints)
page("08-integrations.html", """
.g{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;background:#111}
.ii{height:196px;padding:24px 28px;position:relative}.iic{position:absolute;right:24px;bottom:16px}
.in{font-size:38px;text-transform:uppercase;margin-bottom:10px;line-height:1}.id{font-size:24px;line-height:1.25}
""", head("Integrations", "All optional. All isolated.", "9", B) + f'<div class="g">{ic}</div>'
 + '<div class="note">Each hook has an integrations.* switch. An error is logged once; after 3 failures in a row the hook switches itself off. /oa doctor shows each state.</div>')

# ---------- 09 matrix ----------
plats = [("Paper", B, P, True), ("Purpur", R, P, False), ("Spigot", Y, K, True), ("Bukkit", K, P, True)]
vers = [("26.3", 25), ("26.2", 25), ("26.1", 25), ("1.21.11", 21), ("1.21.10", 21), ("1.21.9", 21)]
cir = lambda ok, col: ph("check-circle", K, 60, "", "fill")
mh = '<div class="c h" style="background:#111;color:#F4EFE6">Minecraft</div>' + "".join(f'<div class="c h" style="background:{b};color:{f}">{n}</div>' for n, b, f, _ in plats)
mr = ""
for v, j in vers:
    mr += f'<div class="c v"><span>{v}</span><small>Java {j}</small></div>' + "".join(f'<div class="c">{cir(n == "Paper" and v == "26.2", b)}</div>' for n, b, f, ok in plats)
page("09-matrix.html", """
.m{display:grid;grid-template-columns:330px repeat(4,1fr)}
.c{height:92px;border-right:12px solid #111;border-bottom:12px solid #111;display:flex;align-items:center;justify-content:center;font-size:28px;text-transform:uppercase;text-align:center;line-height:1.1}
.c:nth-child(5n){border-right:0}.c.h{height:100px}
.c.v{justify-content:flex-start;gap:20px;font-size:44px;padding-left:34px}.c.v small{white-space:nowrap;font-size:17px;background:#111;color:#F4EFE6;padding:4px 8px}
""", head("Platforms and versions", "24 jars: 4 server types times 6 versions", "24", R) + f'<div class="m">{mh}{mr}</div>'
 + f'<div class="note" style="border-top:0">File name: <span style="color:#F2C230;text-transform:none">OmniArena-paper-26.2.jar</span><br>Solid: compiles (checked with test-compile). Not run on a live server yet.</div>')

# ---------- 10 divider ----------
seq = [("sword", Y), ("trophy", "#E8564B"), ("flag-checkered", "#4C7FD6"), ("shield", Y), ("ranking", "#E8564B"), ("crown-simple", "#4C7FD6"), ("sword", Y)]
page("10-divider.html", """
.frame{height:84px;background:#111;display:flex;align-items:center;justify-content:center;gap:44px;border-width:0}
""", "".join(ph(n, c, 44) for n, c in seq))
