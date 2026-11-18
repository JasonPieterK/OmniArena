# OmniArena

OmniArena is a PVP arena plugin for Paper, Purpur, Spigot and Bukkit servers (Minecraft 1.21.9 to
26.3). It combines match-based arenas (16 game types, kits, spectating, join signs, automatic
animated block regeneration), server-wide combat management (combat tagging, PvP toggle, newbie
protection), an ELO rating engine with a party-aware ranked queue, bracket tournaments, match
betting, leaderboard holograms and an embedded web dashboard, all driven by one `/oa` command.

> This README describes what the code in this repository does today. Full per-command and per-key
> reference lives in [DOCS.md](DOCS.md). Developer and AI-agent guides: [CLAUDE.md](CLAUDE.md),
> [AGENTS.md](AGENTS.md).

---

## Contents

1. [Features](#features)
2. [Supported platforms and versions](#supported-platforms-and-versions)
3. [Building](#building)
4. [Installation](#installation)
5. [Quick start](#quick-start)
6. [Commands and permissions](#commands-and-permissions)
7. [Placeholders (PlaceholderAPI)](#placeholders-placeholderapi)
8. [Integrations](#integrations)
9. [Web dashboard](#web-dashboard)
10. [Configuration overview](#configuration-overview)
11. [Storage](#storage)
12. [Performance notes](#performance-notes)
13. [Troubleshooting](#troubleshooting)
14. [Known limitations](#known-limitations)

---

## Features

**Arenas and matches**
- Named arenas stored one-per-file (`arenas/<name>.yml`) with a cuboid region, numbered battle
  spawns, lobby and spectator spawns, per-arena rules, per-arena visual toggles, teams, chat mode,
  and optional per-arena end commands. New arenas are seeded from the `arena.*` and `visual.*` keys
  of `config.yml`.
- 16 game types (Deathmatch, Team Deathmatch, Last One Standing, Sumo, Classic PvP, Crystal PvP,
  Mace, Sword Duel, Axe & Shield, UHC, Potion PvP, NoDebuff, Archer, Netherite OP, Spleef,
  Gladiator). Each defines its loadout, win condition, respawn behaviour, and special rules
  (no natural regen, ring-out). The goal type can be overridden per arena (`/oa goal set`).
- Match lifecycle: lobby wait (with `arena.lobby-timeout`), countdown, numbered spawn assignment
  (player *i* gets spawn *i*), match timer, end conditions (kill target or last alive; team arenas
  score by team), timeout resolution (strict kill leader wins, otherwise a draw), end commands,
  rewards, delayed reset.
- Per-arena `lives` (`/oa config set <arena> lives <n>`, dashboard, arena file): respawn until
  lives run out, then eliminated; shown on the scoreboard and action bar.
- Per-arena access control (`/oa access`): a blacklist that always applies and an optional whitelist
  (`whitelist-enabled`), both saved in the arena file; `omniarena.join.bypass` skips the whitelist.
- Per-arena visual switches (`visual-titles|sounds|particles|holograms|scoreboard|boss-bar|action-bar`)
  work together with the global `visual.*` keys: a channel is on only when both are on. Leaderboard
  holograms placed inside an arena region follow that arena's `visual-holograms`.
- Arena fighters are isolated from outsiders (no damage in or out of the match).
- Full player-state save and restore around a match: location, game mode, health, food, XP, potion
  effects, flight, speeds, invisibility, and (if `arena.inventory-restore`) inventory and armor.
  The pre-arena state is also written to `player-states/<uuid>.yml`, so players are restored on their
  next join if the server crashed mid-match.
- Spectating (`/oa spectate`), mid-match join (per arena), spawn protection, respawn types
  (instant, delayed, and wave: dead players return together every `respawn.wave-interval` seconds,
  default 10, with a countdown on the action bar/title), first blood, revenge, killstreak, combo and power-up modules.
- Configurable rewards (`rewards.*`): Vault money, XP and console commands per kill and per win.
- Join signs (place a sign with `[OmniArena]` on line 1 and the arena name on line 2, or use
  `/oa sign create`), with live state and player count and an optional glowstone block tracked in
  `sign-glowstone.yml` so only plugin-placed glowstone is ever removed.
- Leaderboard holograms (`/oa hologram`): `TextDisplay` top lists for kills, wins, K/D or ELO,
  persisted in `holograms.yml` and refreshed in place.
- Admin overrides: force end, evacuate, freeze, lockdown, god mode, purge-and-regen, force win,
  force start (`/oa override ...`).

**Block regeneration**
- **Automatic**: after every played match the arena is rebuilt from its snapshot (`regen.auto`,
  per-arena override via `/oa regen auto <arena> <true|false|default>`); the arena is not
  joinable until the rebuild has finished. `/oa regen <arena>` and `/oa override purge <arena>` do it by hand.
- Snapshots in a compact palette + vertical run-length format (format v2), captured in time slices
  on the main thread and written off-thread. They include tile entities: container contents, sign
  text and banner patterns. A snapshot is taken when you run `/oa region set`.
- 10 animated restore styles built from real temporary blocks and capped falling-block entities:
  `INSTANT`, `SPHERE` (default), `RISE`, `FALL`, `SWEEP`, `TIME_REWIND` (replays the match's block
  changes backwards), `GALAXY`, `HOLOGRAM_SCAN`, `METEOR_SHOWER`, `OVERGROWTH`. Legacy style names
  from older versions are mapped.
- Water and lava are kept inside the arena region during a match.

**Kits**
- Player kits (`/kit`): save, equip, rename, delete, preview, and share with another player (60 s
  accept window). Slot limits by config and `omniarena.kit.slots.5` / `.10`. Kit names: letters,
  digits, `_`, `-`, `.`, up to 32 characters. Inside an arena kits can only be equipped while the
  match is waiting or starting (lobby).
- Admin (preset) kits loaded from `admin-kits/*.yml`; ten defaults are installed on first run
  (warrior, archer, tank, assassin, mage, healer, pyro, ninja, juggernaut, gunner). Managed with
  `/oa kit admin <create|delete|rename|equip|setpublic|setprivate|icon|description|cooldown|permission|list>`
  (alias `/oa adminkit`): public/private, icon, description, per-player cooldown (persisted in
  `kit-cooldowns.yml`) and a permission node.

**Server-wide combat (PvPManager-style)**
- Combat tagging (with end-crystal, pet, TNT and cloud kill attribution), combat-log punishment
  (KILL / COMMAND / NOTHING), per-player (persistent, `pvp-toggles.yml`) and global PvP toggles,
  per-world disable, newbie protection (persistent, `newbie.yml`), respawn protection, anti
  kill-abuse, anti border-hopping into WorldGuard `pvp deny` regions, auto-soup, blood effect,
  blocks on commands, teleports, ender pearls, elytra and block placement while tagged,
  lava/fire placement guard, optional drop transfer to the killer, attack override for admins.

**Competitive systems**
- ELO ratings (K = 32, default 1000, floor 100) persisted to `elo.yml`; updated for ranked matches
  (wins, losses, draws, forfeits), per player against the opposing side's average in team matches.
- Ranked matchmaking queue (`/oa queue join [kit]|leave|status`): rating window that expands while
  you wait, optional admin-kit queues, and **party queuing** (a party leader queues the whole
  party; parties meet parties or enough solo players on a 2-team arena). `/oa elo [player]` and
  `/oa elo top [n]` show ratings.
- Parties (`/party`): create, invite (60 s expiry), accept, decline, leave, kick, transfer
  leadership (`leader <player>`, automatic when the leader leaves), disband, info. Size limit
  `party.max-size` (default 10).
- Single-elimination tournaments (`/oa tournament` or `/tournament`) with byes, walkover after a
  grace period for offline players (`tournament.walkover-grace-seconds`), automatic draw
  resolution, Vault prizes for 1st/2nd/3rd, a 5-second safety tick that relaunches stalled matches,
  and persistence of in-progress brackets across restarts.
- Match betting through Vault (`/oa bet` or `/bet`): pooled payouts, betting window, refunds for
  aborted or unbacked matches.
- Lifetime stats (`stats.yml`) with `/oa stats` and global leaderboards (kills, wins, K/D).

**Tooling**
- Embedded web dashboard: live state, arena editor, searchable config editor, live matches,
  players, docs.
- In-game settings GUI (`/oa settings`) and scriptable `/oa settings set <key> <value>`.
- PlaceholderAPI expansion `omniarena` and TAB takeover mode.
- Lifecycle control: `/oa stop`, `/oa start`, `/oa restart`, `/oa reload`.

---

## Supported platforms and versions

OmniArena is built as separate jars per server platform and Minecraft version (4 platforms x 6
versions = **24 jars**):

| Minecraft | Java | Paper | Spigot | Bukkit | Purpur |
|-----------|------|:-----:|:------:|:------:|:------:|
| 26.3      | 25   | yes   | yes    | yes    | yes    |
| 26.2      | 25   | yes   | yes    | yes    | yes    |
| 26.1      | 25   | yes   | yes    | yes    | yes    |
| 1.21.11   | 21   | yes   | yes    | yes    | yes    |
| 1.21.10   | 21   | yes   | yes    | yes    | yes    |
| 1.21.9    | 21   | yes   | yes    | yes    | yes    |

Jar names: `OmniArena-<platform>-<mcversion>.jar`, e.g. `OmniArena-paper-26.2.jar`,
`OmniArena-purpur-1.21.11.jar`.

Notes:
- Use the jar whose name matches your platform and Minecraft version. Java 25 is required for
  26.x servers and Java 21 for 1.21.x servers.
- **paper** and **purpur** jars use Paper's native Adventure and need a Paper-family server (they
  refuse to start on plain Spigot with a message telling you to use the spigot jar).
  **spigot** and **bukkit** jars bundle and relocate Adventure plus `adventure-platform-bukkit`
  (`com.omniarena.platform` adapts the differences) and also run on Paper. The **bukkit** jar is
  built from the same code as **spigot** against `spigot-api` (the only published Bukkit-flavoured
  API); only the file name differs.
- Differences: the elytra firework-boost block while combat-tagged exists only on paper/purpur, and
  regen snapshots that contain containers are encoded differently on Paper-family and Spigot-family
  jars (re-take them with `/oa regen snapshot <arena>` after switching; blocks, signs and banners
  are unaffected).
- Build verification: compilation (and, for the 26.2 Paper profile, the unit tests) was verified
  for all 18 paper, spigot and bukkit combinations. The 6 Purpur jars compile against `purpur-api`
  from repo.purpurmc.org, which was unreachable where this was checked, so those 6 are the only
  jars not compile-verified. Runtime behaviour on live servers is not covered by automated tests.

---

## Building

Requirements: Maven 3.9+, and a JDK matching the target (25 for 26.x, 21 for 1.21.x). Every build
needs one platform profile and one Minecraft-version profile; **always pass both**:

```bash
mvn -q -Ppaper,mc-26.2 package          # -> target/OmniArena-paper-26.2.jar
mvn -q -Pspigot,mc-1.21.11 package      # -> target/OmniArena-spigot-1.21.11.jar
# platform: paper | purpur | spigot | bukkit
# version : mc-26.3 | mc-26.2 | mc-26.1 | mc-1.21.11 | mc-1.21.10 | mc-1.21.9
mvn -q test                             # unit tests only
```

All 24 jars (copied to `dist/`):

```bash
JAVA25_HOME=/path/to/jdk25 JAVA21_HOME=/path/to/jdk21 scripts/build-all.sh
PLATFORMS="paper spigot" VERSIONS="26.2 1.21.11" scripts/build-all.sh   # a subset
```

Details (profile table, API versions, shading, platform layer) are in
[DOCS.md](DOCS.md#17-building-and-the-jar-matrix). `target/` and `dist/` are not tracked by git.

---

## Installation

1. Pick the jar for your platform and Minecraft version (see the matrix above).
2. Stop the server, drop the jar into `plugins/`, start the server.
3. On first start OmniArena creates `plugins/OmniArena/` with `config.yml`, `messages.yml`,
   `arenas/`, `admin-kits/` (10 default kits), `player-kits/`, `snapshots/` and `player-states/`
   as needed.
4. Optional soft dependencies are detected automatically: Vault (+ an economy plugin),
   PlaceholderAPI, WorldGuard, LuckPerms. See [Integrations](#integrations).

Web dashboard defaults: it starts on `127.0.0.1:8123` (local only). Read
[Web dashboard](#web-dashboard) before exposing it.

---

## Quick start

```text
/oa wand                       get the selection wand (default: wooden hoe, tools.wand-material)
                               (/oa region wand gives the same item)
(left-click one corner, right-click the opposite corner)
/oa create Arena1              new arena (letters, digits, _ and -; 1-32 chars)
/oa spawn set Arena1 BATTLE    stand on each battle pad; repeat once per player slot
/oa spawn set Arena1 LOBBY     waiting area
/oa spawn set Arena1 SPECTATOR spectator spawn
/oa region set Arena1          save the selection; also takes the regen snapshot (do this last)
/oa config set Arena1 game-mode SUMO      pick a game type (default DEATHMATCH)
/oa config set Arena1 max-players 8
/oa config set Arena1 lives 3             optional: 3 lives per player
/oa sign create Arena1         look at a placed sign within 5 blocks (or write [OmniArena] / Arena1 on a new sign)
```

A new arena starts enabled (state WAITING). `/oa enable` refuses an arena that has no battle
spawns, and `/oa disable` removes everyone from the arena. Players join with `/oa join Arena1` (or
`/join`), by right-clicking a join sign, through the ranked queue, or via tournaments. Matches
start when the arena's minimum players (default 2) are present and the countdown finishes.

**Kits.** Players use `/kit` (opens a GUI with their own kits and public presets) or
`/kit create <name>` to save their current inventory. Admins manage preset kits with
`/oa kit admin <action>` (create from their inventory, setpublic, icon, cooldown, permission, ...)
or by dropping YAML into `admin-kits/`.

**Regenerating the arena after matches.** Nothing to do: arenas rebuild themselves after every
played match from the snapshot taken by `/oa region set` (`regen.auto: true`). Re-run
`/oa regen snapshot <arena>` after you change the arena's blocks, and `/oa regen <arena>` to
rebuild by hand. Turn it off globally with `regen.auto: false` or per arena with
`regen.arenas.<arena-lowercase>.auto: false`.

**Ranked queue.**

```text
/oa queue join            join the "ranked" queue (or /oa queue join <admin-kit> for a kit queue)
/oa queue status          position, wait time, your rating and the accepted rating range
/oa queue leave
```

Party leaders queue the whole party (`/party create`, `/party invite <player>`, then
`/oa queue join`); party matches need an arena with `team-enabled true`, `team-count 2` and
`max-players` of at least twice the party size.

**Tournaments.**

```text
/oa tournament create Cup Arena1 8     create (registration state); /tournament works too
/oa tournament join Cup                players join; auto-starts when full
/oa tournament start Cup               creator or admin, min 2 players
/oa tournament setprize Cup 1st 5000   admin, Vault prize
/oa tournament status Cup
```

---

## Commands and permissions

`/oa` is the main command (aliases `omniarena`, `arena`). Standalone commands registered in
`plugin.yml`: `/kit`, `/join`, `/leave`, `/party`, `/spectate`, `/stats`, `/list`, `/info`,
`/ready`, `/help`, `/bet`, `/tournament`, `/pvp`, `/pvpstatus`, `/pvptag`, `/untag`, `/pvpglobal`,
`/newbie`. Most are forwarders to the matching `/oa` subcommand. There is no standalone `/queue`.

While the plugin is stopped (`/oa stop`) every command except `/oa start` replies with the
"plugin stopped" message.

### Player commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/oa join <arena>` | `omniarena.use` | Join an arena (also `/join`) |
| `/oa leave` | `omniarena.use` | Leave arena or stop spectating (also `/leave`) |
| `/oa spectate <arena>` | `omniarena.spectate` | Spectate a running match |
| `/oa list` | `omniarena.use` | List arenas with state and players |
| `/oa info <arena>` | `omniarena.use` | Arena details |
| `/oa stats [player]`, `/oa stats global [kills\|wins\|kd]` | `omniarena.use` | Lifetime stats and leaderboards |
| `/oa elo [player]`, `/oa elo top [n]` | `omniarena.use` | Ranked ELO rating (rating, rank, highest, wins/losses) and leaderboard; read-only |
| `/oa ready` | `omniarena.use` | Status message only: matches start automatically when the minimum players are in |
| `/oa help [guide]` | `omniarena.use` | Help and guides |
| `/kit` and `/oa kit` | `omniarena.kit.use` | Kit menu and subcommands |
| `/party <action>` | `omniarena.use` | Party management |
| `/oa queue <join [kit]\|leave\|status>` | `omniarena.queue` | Ranked matchmaking queue |
| `/oa bet place <arena> <player> <amount>`, `cancel`, `info [arena]` (also `/bet`) | `omniarena.bet` | Betting |
| `/oa tournament <action>` (also `/tournament`) | `omniarena.tournament.create` / `.admin` | Tournaments |
| `/oa pvp [player]`, `pvpstatus`, `pvpinfo`, `newbie [disable]` | `omniarena.pvp` | Combat toggles and status |

### Admin commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/oa create <name>` / `delete <arena>` | `omniarena.admin.arena` | Create or delete an arena |
| `/oa enable <arena>` / `disable <arena>` | `omniarena.admin.arena` | Toggle availability |
| `/oa wand` | `omniarena.admin.arena` | Selection wand |
| `/oa region <set\|info\|wand> <arena>` | `omniarena.admin.arena` | Region (`set` also snapshots) |
| `/oa spawn <set\|list\|clear> <arena> [BATTLE\|LOBBY\|SPECTATOR] [#]` | `omniarena.admin.arena` | Spawns |
| `/oa config <get\|set> [arena] <key> [value]` | `omniarena.admin.arena` | Edit global or per-arena config live (arena keys include `lives`) |
| `/oa goal set <arena> <type> [value]` | `omniarena.admin.arena` | Arena goal type/value |
| `/oa module <enable\|disable> <arena> <module>` | `omniarena.admin.arena` | Arena modules (`killstreak`, `combo`, `power-up`) |
| `/oa chat set <arena> <ARENA\|TEAM\|GLOBAL>` | `omniarena.admin.arena` | Chat mode |
| `/oa announcements set <arena> <enable\|disable>` | `omniarena.admin.arena` | Arena announcements |
| `/oa tp <arena>` | `omniarena.admin.arena` | Teleport to arena |
| `/oa debug <arena>` | `omniarena.admin.arena` | Match debug state |
| `/oa regen ...` | `omniarena.admin.arena` | Snapshot, restore, style, batch, toggles, `auto <arena> <true\|false\|default>` |
| `/oa access <arena> <whitelist\|blacklist> <add\|remove\|list> [player]`, `/oa access <arena> whitelist-enabled <true\|false>` | `omniarena.admin.access` | Per-arena join whitelist and blacklist (saved in the arena file) |
| `/oa sign <create\|remove\|list>` | `omniarena.admin.sign` | Join signs |
| `/oa hologram <create\|remove\|move\|list> ...` | `omniarena.admin.hologram` | Leaderboard holograms |
| `/oa kit admin <action> ...` / `/oa adminkit <action> ...` | `omniarena.admin.kit` | Preset kits: create, delete, rename, equip, setpublic, setprivate, icon, description, cooldown, permission, list |
| `/oa match <start\|stop\|reset> <arena>` | `omniarena.admin.match` | Start, end (no winner), or reset (remove everyone from) the arena's match |
| `/oa force start <arena>`, `/oa forcewin <arena> <player\|team>` | `omniarena.admin.match` | Force start / force a winner |
| `/oa override <action>` | `omniarena.admin.override` | Force end/win/start, evacuate, freeze, lockdown, purge, god, broadcast, snapshotall, resetstats, killweb |
| `/oa website` | `omniarena.admin` | Create a dashboard session link |
| `/oa settings [set <key> <value>]` | `omniarena.admin.reload` | Settings GUI / scripting |
| `/oa reload` | `omniarena.admin.reload` | Reload config, messages, kits, integrations |
| `/oa version` | `omniarena.admin.reload` | Plugin version |
| `/oa start` / `stop` / `restart` | `omniarena.admin` | Lifecycle control |
| `/oa pmr <help\|cleanup\|reload\|worlds <world>>` | `omniarena.admin` | Combat admin |
| `/oa pvptag`, `untag`, `pvpglobal <on\|off>`, `pvplist`, `pvpoverride`, `newbie add\|remove\|checktime` | `omniarena.pvp.admin` | Combat admin |

Admin checks pass for the console, for operators, and for holders of `omniarena.admin` or the
specific node. `/oa stats reset <player>` needs `omniarena.admin.stats`.

### Permission nodes

| Node | Default | Purpose |
|------|---------|---------|
| `omniarena.use` | true | Basic player commands |
| `omniarena.kit.use` | true | `/kit` |
| `omniarena.queue` | true | `/oa queue` |
| `omniarena.bet` | true | `/oa bet`, `/bet` |
| `omniarena.spectate` | true | `/spectate` |
| `omniarena.pvp` | true | Player PvP commands |
| `omniarena.tournament.create` | true | Create, join, leave and start own tournaments |
| `omniarena.kit.slots.5` / `.10` | false | Larger personal kit limit |
| `omniarena.admin` | op | Parent of all admin nodes below |
| `omniarena.admin.arena` / `.kit` / `.match` / `.sign` / `.hologram` / `.stats` / `.reload` / `.override` / `.access` | op | Admin areas |
| `omniarena.pvp.admin` | op | Combat admin commands |
| `omniarena.tournament.admin` | op | Manage any tournament, delete, setprize |
| `omniarena.join.bypass` | op | Bypass the arena whitelist and the full-arena check on `/oa join` |

Individual preset kits may require their own configured permission node.

---

## Placeholders (PlaceholderAPI)

Identifier: `omniarena` (use as `%omniarena_<name>%`). Requires PlaceholderAPI.

| Placeholder | Value |
|-------------|-------|
| `kills`, `deaths`, `wins`, `losses`, `kd` | Lifetime stats |
| `winrate` | Win rate with a `%` suffix |
| `time_played` | Formatted total play time |
| `in_arena` | `true` / `false` |
| `arena`, `arena_name` | Current arena name, or `N/A` |
| `gamemode`, `gamemode_name`, `gamemode_desc` | Current game type (enum, display name, description) |
| `goal` | Effective goal type of the arena (`KILLS` / `LAST_ALIVE` / ...) |
| `state` | Match state (WAITING, STARTING, RUNNING, ENDING, RESETTING) |
| `arena_kills`, `arena_deaths`, `arena_streak` | Per-match counters of the player |
| `alive`, `players`, `max_players` | Alive count, player count, arena max |
| `time_left`, `time_left_seconds` | Remaining time (`m:ss` / seconds) |
| `team`, `team_color` | Team name / color enum |

Outside an arena: `gamemode*`, `state`, `team`, `team_color` return an empty string, `arena_kills`,
`arena_deaths`, `alive`, `players`, `max_players`, `goal`, `time_left`, `time_left_seconds` return
`0`, and `arena_streak` and unknown placeholders return `null`.

TAB: set `integrations.tab.takeover: true` and run `/oa reload` to stop OmniArena drawing its own
sidebar and boss bar; build them from the placeholders in TAB instead.

---

## Integrations

All integrations are soft: OmniArena runs without them. Each hook has a switch under
`integrations.*` (`vault`, `placeholderapi`, `worldguard`, `luckperms`, default true), re-read on
`/oa reload`.

| Plugin | Used for |
|--------|----------|
| Vault + an economy plugin | Betting, tournament prizes and `rewards.*` money. Without it betting is disabled and prizes are logged as unpaid. |
| PlaceholderAPI | The `omniarena` expansion (registered on enable, unregistered on disable) |
| WorldGuard | Anti border-hopping: a combat-tagged player cannot enter a region where the `pvp` flag is `deny` |
| LuckPerms | Hook object exists (primary group and permission lookups); not used by shipped features |
| LibsDisguises | Undisguise on combat tag (`pvp.combat.disable-disguise`) |
| TAB | Takeover mode (see above) |

`plugin.yml` also lists `WorldEdit`, `FastAsyncWorldEdit`, `PermissionsEx`, `EssentialsX`,
`DiscordSRV`, `HolographicDisplays` as soft dependencies (load-order only). No code path uses them.

---

## Web dashboard

The dashboard is a small JDK `HttpServer` serving a single-page app (tabs: Dashboard, Live Matches,
Arenas, Players, Config, Docs). Settings:

```yaml
web:
  enabled: true
  bind-address: 127.0.0.1   # local only by default
  port: 8123
  public-url: ""            # base URL used in generated links (e.g. https://arena.example.com)
  session-minutes: 30       # sliding lifetime of a session token (renewed on each request)
  max-sessions: 1           # 0 = unlimited
  title: OmniArena Control  # browser tab title
```

**Access.** Run `/oa website` in game (or from the console) to create a session. You get a link of
the form `<public-url or http://127.0.0.1:<port>>/?token=<token>`. The page moves the token out of
the address bar into `sessionStorage`; every `/api/*` request carries it as `Authorization: Bearer
<token>` (also accepted: the `X-OmniArena-Token` header and, for old clients, `?token=`); the
HTML/JS/CSS shell is served without it. The Log out button (`POST /api/logout`) revokes the
session. If the session limit is reached, free a slot with `/oa override killweb`. Tokens are 24
random bytes, URL-safe base64, compared in constant time.

**Security guidance.**
- The default bind is `127.0.0.1`. Keep it unless you need remote access.
- The server speaks plain HTTP and the first link carries the token in the URL. For remote access
  bind to `127.0.0.1` and put a reverse proxy with HTTPS in front (nginx, Caddy, Traefik), set
  `web.public-url` to the public HTTPS URL, and restrict the proxy (IP allow-list or VPN). Binding
  `0.0.0.0` exposes an HTTP endpoint that can change plugin config.
- Treat the link as a password: it grants config editing, arena creation and deletion. Do not
  paste it in public channels; revoke with the Log out button or `/oa override killweb`.
- Responses are JSON errors (`{"ok":false,"error":...}`) and carry `Cache-Control: no-store`, a
  Content-Security-Policy, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` and
  `Referrer-Policy: no-referrer`; no CORS headers are sent.
- Request bodies are capped at 1 MiB; all values are validated before anything is applied
  (all-or-nothing); handlers run Bukkit API calls on the main thread.
- `web.session-minutes` and `web.max-sessions` are read when the dashboard object is created at
  plugin enable; changing them needs a plugin restart (not just `/oa reload`).

**Phosphor icon CSS and SRI.** `index.html` loads the Phosphor icon stylesheet from unpkg
(`@phosphor-icons/web@2.1.1`). It is currently included with `crossorigin="anonymous"` but without
an `integrity` attribute. To compute a Subresource Integrity hash for that exact file:

```bash
curl -s https://unpkg.com/@phosphor-icons/web@2.1.1/src/bold/style.css | openssl dgst -sha384 -binary | openssl base64 -A
```

Prefix the output with `sha384-` and add it as `integrity="sha384-..."` on the `<link>` in
`src/main/resources/web/index.html`. Re-run the command whenever the pinned version changes.

---

## Configuration overview

Files: `config.yml` (behaviour), `messages.yml` (all player-facing text, MiniMessage format,
`prefix` is prepended to every message), per-arena files in `arenas/`. Edit by file and
`/oa reload`, by `/oa config set <key> <value>`, by the `/oa settings` GUI, or by the web Config
tab; all of them write the same live config. The settings GUI and web Config tab list exactly the
keys of `config.yml` and `messages.yml`.

Every key shipped in `config.yml` is read by the code. `plugin.debug: true` adds `[debug]` console
lines for matchmaking decisions, regen timings and web requests (paths only, never tokens).
Sections:

| Section | Keys |
|---------|------|
| `plugin` | `debug` (no effect) |
| `tools` | `wand-material` |
| `arena` | `default-min-players`, `default-max-players`, `default-countdown`, `default-match-duration`, `default-game-mode`, `spectator-allowed`, `mid-match-join`, `respawn-delay`, `spawn-protection` (seed new arenas), `lobby-timeout`, `inventory-restore`, `persist-player-state`, `region-protection` |
| `match` | `end-commands`, `end-delay`, `announce-global`, `announce-arena` |
| `regen` | `auto`, `batch-size`, `progress.enabled`, `progress.interval-percent`, `animation.type`, `animation.particles`, `animation.sound` (and the optional `arenas.<name>.auto`) |
| `pvp` | the whole block: `combat.*` (tag, `block-teleport`, `block-elytra`, `block-ender-pearl`, commands, combat-log), `toggle.*`, `global.*`, `newbie.*`, `killabuse.*`, `respawn-protection.*`, `border-hopping.*`, `auto-soup.*`, `blood.*`, `transfer-drops`, lava/fire guard, `disabled-worlds` |
| `rewards` | `enabled`, `kill-money`, `win-money`, `kill-xp`, `win-xp`, `win-commands`, `kill-commands` |
| `kits` | `player-kits-enabled`, `default-slot-limit`, `power-cap.*` (incl. `banned-items`), `sharing-enabled` |
| `party` | `max-size` |
| `signs`, `holograms` | `glow-effect`, `update-interval`; `refresh-seconds` |
| `visual` | `titles`, `sounds`, `particles`, `scoreboard`, `boss-bar`, `action-bar`, `holograms` (seed new arenas; `holograms` also gates leaderboard holograms), `killstreak-titles`, `kill-announcements`, `victory-fireworks`, `countdown-sounds`, `spectator-invisibility` |
| `killstreak`, `scoreboard`, `boss-bar` | milestones, sound, particle; title, update interval; colors and low-time threshold |
| `integrations` | `vault`, `placeholderapi`, `worldguard`, `luckperms`, `tab.takeover` |
| `web` | `enabled`, `bind-address`, `port`, `public-url`, `session-minutes`, `max-sessions`, `title` |
| `betting` | `enabled`, `min-bet`, `max-bet`, `allow-spectator-bet`, `bet-window-seconds` |
| `tournament` | `walkover-grace-seconds` (default 60) |

Old keys removed from the shipped file (`plugin.language`, `plugin.metrics`, `database.*`,
`arena.teleport-back-on-leave`, `anticheat.*`, `performance.*`) are no longer read. Combat keys
renamed earlier (`pvp.combat.cancel-teleport`, `pvp.combat.block-enderpearls`) are still honoured
when only the old name is present in your file. The exact defaults of every key are in
[DOCS.md](DOCS.md#18-configuration-reference).

---

## Storage

All data is flat YAML under `plugins/OmniArena/`. No database is used (the shaded MySQL/Hikari
libraries are unused).

| Path | Contents |
|------|----------|
| `config.yml`, `messages.yml` | Settings and text |
| `arenas/<name>.yml` | One arena (region, spawns as `world:x:y:z:yaw:pitch`, rules, team, visual, lives, goal, signs, end commands). `example.yml` in the jar is a template and is ignored by the loader |
| `snapshots/<arena-lowercase>.yml` | Regen snapshot. **Format v2**: `world`, `format: 2`, `palette` (list of `MATERIAL\|blockdata` strings), `runs` (strings `x,z,y0,length,paletteIndex`, vertical run-length encoded columns) and optional `tiles` (container contents, sign text, banner patterns). Legacy format 1 (`x,y,z\|MATERIAL\|blockdata` lines) is still read |
| `sign-glowstone.yml` | `locations: ["world,x,y,z", ...]`: glowstone blocks placed behind join signs by the plugin |
| `holograms.yml` | Leaderboard holograms (`type`, `size`, `world`, `x`, `y`, `z`) |
| `admin-kits/<name>.yml` | Preset kits (structured `items` / `armor` sections) |
| `player-kits/<uuid>.yml` | Personal kits |
| `stats.yml` | Lifetime stats per UUID (kills, deaths, wins, losses, assists, draws, time-played) |
| `elo.yml` | `ratings.<uuid>`: name, rating, wins, losses, draws, highestRating |
| `pvp-toggles.yml`, `newbie.yml` | Players with PvP switched off; running newbie-protection windows |
| `player-states/<uuid>.yml` | Crash-safe copy of a player's pre-arena state (location, game mode, health, food, XP, effects, inventory); written while they are in an arena, deleted when they leave, and applied on their next join if the server died mid-match |
| `kit-cooldowns.yml` | Per-player preset-kit cooldown expiries |
| `tournaments.yml` | Active tournaments (registration and in-progress, including bracket rounds and winners). Finished and cancelled tournaments are removed |

Writes: stats, PvP toggles and newbie windows are saved asynchronously when the plugin is enabled and
inline during shutdown. ELO uses a single writer thread with a main-thread snapshot, flushed on
disable. Snapshots are encoded on the main thread in slices and the file is written asynchronously.
Arenas, tournaments and kits are saved synchronously.

---

## Performance notes

- Snapshot capture is time-sliced: about 30,000 blocks of scanning per tick, then an async write.
- Regeneration places `regen.batch-size` blocks per tick for `INSTANT`; animated styles use
  `min(batch-size, 1200)` per tick (`METEOR_SHOWER` ramps up from about 15% of that). Decorative
  falling blocks are capped at 90 concurrent entities and effects are only sent to players within
  64 blocks. Lower `regen.batch-size` for smoother ticks on weak hardware.
- The sign refresh task runs every `signs.update-interval` ticks (default 20) and never loads
  chunks to refresh a sign. Leaderboard holograms refresh every `holograms.refresh-seconds` from
  cached data.
- Matchmaking runs once per second only when queues exist.
- Match timers run once per second per active match; scoreboards, boss bars and action bars update
  with it.
- The web server uses a 4-thread pool; every Bukkit API call from a handler is hopped to the
  main thread with a 10 s timeout.
- Region enforcement runs on player block movement for players in running arenas only.
- Large regions: take the snapshot once, set `regen.animation.type` to `INSTANT` or a light style
  (`RISE`, `FALL`) if restores cause lag, or lower `regen.batch-size`.

---

## Troubleshooting

| Symptom | Likely cause and fix |
|---------|----------------------|
| `/oa ...` says OmniArena is stopped | Someone ran `/oa stop`. Run `/oa start` (the only command that works while stopped). |
| Plugin disables itself on start ("This is the Paper/Purpur build of OmniArena but the server is not Paper", or "built for another server software") | Wrong jar flavor: paper/purpur jars need a Paper-family server; use the spigot or bukkit jar on Spigot. Also match the Minecraft version and Java version (25 for 26.x, 21 for 1.21.x). |
| Cannot join an arena | Arena disabled, lockdown active (`/oa override unlockdown`), arena not in WAITING/STARTING state (RUNNING only with mid-match join; an arena that is regenerating after a match is RESETTING), arena full, you are in a queue (`/oa queue leave`), or you are not whitelisted (`omniarena.join.bypass` skips whitelist and full checks). |
| Match never starts | Fewer players than `min-players` (default 2); the lobby closes after `arena.lobby-timeout` seconds. Tournaments need the arena's min players <= 2. Check with `/oa info <arena>` and `/oa debug <arena>`. |
| Players spawn on top of each other | Fewer battle spawns than players: add spawns (`/oa spawn set <arena> BATTLE`). Spawn *i* goes to player *i*; with fewer spawns than players the list wraps around (players share pads). |
| `/oa region set` says select two points | Use the wand (needs `omniarena.admin.arena`; item from `tools.wand-material`). Both points must be in the same world. |
| Arena does not rebuild after a match | Check `regen.auto` and `regen.arenas.<name>.auto`, that the arena has a region, and that a snapshot exists (`/oa region set` or `/oa regen snapshot <arena>`); the console and admins get a "was not regenerated" message otherwise. A lobby-only match whose last player simply left is not regenerated. |
| "No snapshot" on regen | `snapshots/<arena>.yml` missing; re-run the snapshot. |
| Chests/signs not restored after switching jar type | Container snapshots are platform specific; re-take the snapshot (`/oa regen snapshot <arena>`). |
| `/oa queue join` says no arena is available | No enabled arena can host the match: solo queues need `max-players` >= 2; parties need team mode (2 teams) and `max-players` of at least twice the party size. |
| Betting says unavailable | Vault or an economy provider is missing at plugin start, or `betting.enabled` / `integrations.vault` is off. Install both and restart. |
| Dashboard link refused | `web.enabled: false`, port in use (see log `[Web] Cannot start dashboard`), or session limit reached (`/oa override killweb`). |
| Dashboard "Invalid or expired session" | Token idle longer than `web.session-minutes` or revoked: run `/oa website` again. |
| Config change has no effect | The key needs `/oa reload` / restart (`web.session-minutes`, `web.max-sessions`). |
| `/kit` opens another plugin's kit | OmniArena only intercepts `/kit` if it owns the command or the player is in an arena. |
| Placeholders blank | PlaceholderAPI missing, or the player is not in an arena (in-match values are blank/0 outside one). |
| Standalone `/list`, `/info`, `/help` clash with other plugins | These are registered by OmniArena; use `/oa list` etc. and resolve the clash in your command-alias setup. |

Server logs name the failing step for most problems. Every `onDisable` step is individually
guarded, so a failure in one save does not skip the others.

---

## Known limitations

These are accurate as of the current code and are tracked so users are not surprised:

- **Database mode** is not implemented; everything is YAML.
- **Platform differences**: container snapshots are not portable between Paper-family and
  Spigot-family jars, and the elytra firework-boost block while tagged is Paper-only.
- **Build verification**: the Purpur API coordinates and the 26.x / Spigot snapshot API versions in
  the POM have not been verified against the live repositories by the authors of this
  documentation.
