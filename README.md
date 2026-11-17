# OmniArena

OmniArena is a PVP arena plugin for Paper-family Minecraft servers. It combines match-based arenas
(16 game types, kits, spectating, join signs, animated block regeneration), server-wide combat
management (combat tagging, PvP toggle, newbie protection), an ELO rating engine, bracket
tournaments, match betting, and an embedded web dashboard, all driven by one `/oa` command.

> This README describes what the code in this repository does today. Items that exist only as
> infrastructure (no player-facing entry point yet) are called out explicitly in
> [Known limitations](#known-limitations). Full per-command and per-key reference lives in
> [DOCS.md](DOCS.md). Developer and AI-agent guides: [CLAUDE.md](CLAUDE.md), [AGENTS.md](AGENTS.md).

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
  and optional per-arena end commands.
- 16 game types (Deathmatch, Team Deathmatch, Last One Standing, Sumo, Classic PvP, Crystal PvP,
  Mace, Sword Duel, Axe & Shield, UHC, Potion PvP, NoDebuff, Archer, Netherite OP, Spleef,
  Gladiator). Each defines its loadout, win condition, respawn behaviour, and special rules
  (no natural regen, ring-out).
- Match lifecycle: lobby wait, countdown, numbered spawn assignment (player *i* gets spawn *i*),
  match timer, end conditions (kill target or last alive), draw/timeout handling, end commands,
  delayed reset.
- Spectating (`/oa spectate`), mid-match join (per arena), inventory save and restore, spawn
  protection, respawn types (instant, delayed, wave setting stored), first blood, revenge,
  killstreaks and combo modules, power-up module.
- Join signs (right-click to join, live state and player count), with an optional glowstone
  block tracked in `sign-glowstone.yml` so only plugin-placed glowstone is ever removed.
- Admin overrides: force end, evacuate, freeze, lockdown, god mode, purge-and-regen, force win,
  force start (`/oa override ...`).

**Block regeneration**
- Snapshots stored in a compact palette + vertical run-length format (format v2), captured in
  time slices on the main thread and written off-thread.
- 20 animated restore styles built from real temporary blocks and capped falling-block entities;
  legacy style names from older versions are still accepted.
- Regeneration is manual (`/oa regen <arena>`, `/oa override purge <arena>`); a snapshot is taken
  automatically when you run `/oa region set`.

**Kits**
- Player kits (`/kit`): save, equip, rename, delete, preview, and share with another player (60 s
  accept window). Slot limits by config and `omniarena.kit.slots.5` / `.10`.
- Admin (preset) kits loaded from `admin-kits/*.yml`; ten defaults are installed on first run
  (warrior, archer, tank, assassin, mage, healer, pyro, ninja, juggernaut, gunner). Preset kits can
  be made public and given an icon, description, cooldown field, and permission node.

**Server-wide combat (PvPManager-style)**
- Combat tagging, combat-log punishment (KILL / COMMAND / NOTHING), per-player and global PvP
  toggles, per-world disable, newbie protection, respawn protection, anti kill-abuse, anti
  border-hopping into WorldGuard `pvp deny` regions, auto-soup, blood effect, blocks on commands,
  enderpearls, teleports and block placement while tagged, lava/fire placement guard, optional
  drop transfer to the killer, attack override for admins.

**Competitive systems**
- ELO ratings (K = 32, default 1000, floor 100) persisted to `elo.yml`; updated for ranked matches
  (wins, losses, draws, forfeits).
- Rating-based queue matchmaker (1 s tick, expanding rating window). See
  [Known limitations](#known-limitations) regarding player entry points.
- Single-elimination tournaments with byes, walkover on offline players, automatic draw
  resolution, Vault prizes for 1st/2nd/3rd, and persistence of in-progress brackets across restarts.
- Match betting through Vault: pooled payouts, betting window, refunds for aborted or unbacked
  matches.
- Party system (`/party`): create, invite (60 s expiry), accept, decline, leave, kick, transfer
  leadership, disband, info. Max 10 members.
- Lifetime stats (`stats.yml`) with `/oa stats` and global leaderboards (kills, wins, K/D).

**Tooling**
- Embedded web dashboard: live state, arena editor, searchable config editor, live matches,
  players, docs.
- In-game settings GUI (`/oa settings`) and scriptable `/oa settings set <key> <value>`.
- PlaceholderAPI expansion `omniarena` and TAB takeover mode.
- Lifecycle control: `/oa stop`, `/oa start`, `/oa restart`, `/oa reload`.

---

## Supported platforms and versions

OmniArena is built as separate jars per server platform and Minecraft version. The build matrix
is being set up; the table below is the target. See [Building](#building).

| Minecraft | Java | Paper | Spigot | Bukkit | Purpur |
|-----------|------|:-----:|:------:|:------:|:------:|
| 26.3      | 25   | yes   | yes    | yes    | yes    |
| 26.2      | 25   | yes   | yes    | yes    | yes    |
| 26.1      | 25   | yes   | yes    | yes    | yes    |
| 1.21.11   | 21   | yes   | yes    | yes    | yes    |
| 1.21.10   | 21   | yes   | yes    | yes    | yes    |
| 1.21.9    | 21   | yes   | yes    | yes    | yes    |

That is 4 platforms x 6 versions = **24 jars**, named:

```
OmniArena-<platform>-<mcversion>.jar
e.g. OmniArena-paper-26.2.jar, OmniArena-purpur-1.21.11.jar
```

Notes:
- The code base currently compiles against the Paper API (`paper-api 26.2.build.31-alpha`) and
  declares `api-version: '26.2'` in `plugin.yml`. It uses Paper/Adventure APIs (MiniMessage text,
  `Component` click events, `player.spigot().respawn()`), so the Spigot and Bukkit variants depend
  on the per-platform build work finishing. Until the matrix lands, only the single Paper 26.2
  jar produced by a plain `mvn package` is a tested configuration.
- Use the jar whose name matches your platform and Minecraft version. Java 25 is required for
  26.x servers and Java 21 for 1.21.x servers.

---

## Building

Requirements: JDK matching the target (21 for the current POM), Maven 3.9+.

Current state (works today):

```bash
mvn -q package            # compiles, runs tests, shades -> target/OmniArena-1.0.0.jar
mvn -q test               # tests only
```

Planned matrix form (see Building notes in [CLAUDE.md](CLAUDE.md) for the status of the
profiles; **not all profiles exist yet**):

```bash
mvn -P<platform>,mc-<version> package
# platform: paper | spigot | bukkit | purpur
# version : 26.3 | 26.2 | 26.1 | 1.21.11 | 1.21.10 | 1.21.9
# e.g.   mvn -Ppaper,mc-26.2 package
```

Build commands and the matrix plan are maintained in [CLAUDE.md](CLAUDE.md) and
[DOCS.md](DOCS.md#17-building-and-the-jar-matrix).

Caveat: the current `pom.xml` contains an `maven-antrun-plugin` step that copies the finished jar to
a Windows-specific path. On other machines it has no effect on the jar in `target/`; remove or
override it for CI.

---

## Installation

1. Pick the jar for your platform and Minecraft version (see the matrix above).
2. Stop the server, drop the jar into `plugins/`, start the server.
3. On first start OmniArena creates `plugins/OmniArena/` with `config.yml`, `messages.yml`,
   `arenas/`, `admin-kits/` (10 default kits), `player-kits/`, and `snapshots/` as needed.
4. Optional soft dependencies are detected automatically: Vault (+ an economy plugin),
   PlaceholderAPI, WorldGuard, LuckPerms. See [Integrations](#integrations).

Web dashboard defaults: it starts on `127.0.0.1:8123` (local only). Read
[Web dashboard](#web-dashboard) before exposing it.

---

## Quick start

```text
/oa wand                       get the selection wand (default: wooden hoe, tools.wand-material)
(left-click one corner, right-click the opposite corner)
/oa create Arena1              new arena (letters, digits, _ and -; 1-32 chars)
/oa region set Arena1          save the selection; also takes the regen snapshot
/oa spawn set Arena1 BATTLE    stand on each battle pad; repeat once per player slot
/oa spawn set Arena1 LOBBY     waiting area
/oa spawn set Arena1 SPECTATOR spectator spawn
/oa config set Arena1 game-mode SUMO      pick a game type (default DEATHMATCH)
/oa config set Arena1 max-players 8
/oa sign create Arena1         look at a placed sign within 5 blocks
```

A new arena starts enabled (state WAITING). Players join with `/oa join Arena1` (or `/join`), by
right-clicking a join sign, or via tournaments. Matches start when the arena's minimum players
(default 2) are present and the countdown finishes.

**Kits.** Players use `/kit` (opens a GUI with their own kits and public presets) or
`/kit create <name>` to save their current inventory. Admins manage preset kits by dropping YAML
into `admin-kits/` and using `/oa kit setpublic|setprivate|icon|description|cooldown|permission`.

**Regenerating the arena after matches.** Block regeneration is not triggered automatically at the
end of a match. Use `/oa regen <arena>`, or run it from match end commands (for example
`match.end-commands: ["oa regen {arena}"]` as console), or `/oa override purge <arena>`.

**Tournaments.**

```text
/oa tournament create Cup Arena1 8     create (registration state)
/oa tournament join Cup                players join; auto-starts when full
/oa tournament start Cup               creator or admin, min 2 players
/oa tournament setprize Cup 1st 5000   admin, Vault prize
/oa tournament status Cup
```

**Queue.** See [Known limitations](#known-limitations): the queue and ELO engines are implemented
in code, but this build exposes no `/oa queue` or `/oa elo` command.

---

## Commands and permissions

`/oa` is the main command (aliases `omniarena`, `arena`). Standalone commands registered in
`plugin.yml`: `/kit`, `/join`, `/leave`, `/party`, `/spectate`, `/stats`, `/list`, `/info`,
`/ready`, `/help`, `/pvp`, `/pvpstatus`, `/pvptag`, `/untag`, `/pvpglobal`, `/newbie`. Most are
forwarders to the matching `/oa` subcommand.

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
| `/oa ready` | `omniarena.use` | Prints a "ready" message only (no readiness logic) |
| `/oa help [guide]` | `omniarena.use` | Help and guides |
| `/kit` and `/oa kit` | `omniarena.kit.use` | Kit menu and subcommands |
| `/party <action>` | `omniarena.use` | Party management |
| `/oa bet place <arena> <player> <amount>`, `cancel`, `info [arena]` | `omniarena.bet` (see note) | Betting |
| `/oa tournament <action>` | `omniarena.tournament.create` / `.admin` | Tournaments |
| `/oa pvp [player]`, `pvpstatus`, `newbie [disable]` | `omniarena.pvp` | Combat toggles and status |

### Admin commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/oa create <name>` / `delete <arena>` | `omniarena.admin.arena` | Create or delete an arena |
| `/oa enable <arena>` / `disable <arena>` | `omniarena.admin.arena` | Toggle availability |
| `/oa wand` | `omniarena.admin.arena` | Selection wand |
| `/oa region <set\|info\|wand> <arena>` | `omniarena.admin.arena` | Region (`set` also snapshots) |
| `/oa spawn <set\|list\|clear> <arena> [BATTLE\|LOBBY\|SPECTATOR] [#]` | `omniarena.admin.arena` | Spawns |
| `/oa config <get\|set> [arena] <key> [value]` | `omniarena.admin.arena` | Edit global or per-arena config live |
| `/oa goal set <arena> <type> [value]` | `omniarena.admin.arena` | Arena goal type/value |
| `/oa module <enable\|disable> <arena> <module>` | `omniarena.admin.arena` | Arena modules |
| `/oa chat set <arena> <ARENA\|TEAM\|GLOBAL>` | `omniarena.admin.arena` | Chat mode |
| `/oa announcements set <arena> <enable\|disable>` | `omniarena.admin.arena` | Arena announcements |
| `/oa tp <arena>` | `omniarena.admin.arena` | Teleport to arena |
| `/oa debug <arena>` | `omniarena.admin.arena` | Match debug state |
| `/oa regen ...` | `omniarena.admin.arena` | Snapshot, restore, style, batch, toggles |
| `/oa sign <create\|remove\|list>` | `omniarena.admin.sign` | Join signs |
| `/oa kit <setpublic\|setprivate\|icon\|description\|cooldown\|permission> ...` | `omniarena.admin.kit` | Preset kit metadata |
| `/oa match <start\|stop> <arena>` | `omniarena.admin.match` | Start or stop a running match (`reset` is listed in tab-complete but does nothing) |
| `/oa override <action>` | `omniarena.admin.override` (see note) | Force end/win/start, evacuate, freeze, lockdown, purge, god, broadcast, snapshotall, resetstats, killweb |
| `/oa website` | `omniarena.admin` | Create a dashboard session link |
| `/oa settings [set <key> <value>]` | `omniarena.admin.reload` | Settings GUI / scripting |
| `/oa reload` | `omniarena.admin.reload` | Reload config and messages |
| `/oa version` | `omniarena.admin.reload` | Plugin version |
| `/oa start` / `stop` / `restart` | `omniarena.admin` | Lifecycle control |
| `/oa pmr <help\|cleanup\|reload\|worlds <world>>` | `omniarena.admin` | Combat admin |
| `/oa pvptag`, `untag`, `pvpglobal <on\|off>`, `pvpinfo`, `pvplist`, `pvpoverride`, `newbie add\|remove\|checktime` | `omniarena.pvp.admin` | Combat admin |

Admin checks pass for the console, for operators, and for holders of `omniarena.admin` or the
specific node.

### Permission nodes

| Node | Default | Purpose |
|------|---------|---------|
| `omniarena.use` | true | Basic player commands |
| `omniarena.kit.use` | true | `/kit` |
| `omniarena.spectate` | true | `/spectate` |
| `omniarena.pvp` | true | Player PvP commands |
| `omniarena.pvp.admin` | op | Combat admin commands |
| `omniarena.tournament.create` | true | Create, join, leave and start own tournaments |
| `omniarena.tournament.admin` | op | Manage any tournament, delete, setprize |
| `omniarena.admin` | op | Everything below |
| `omniarena.admin.arena` / `.kit` / `.match` / `.sign` / `.stats` / `.reload` | op | Admin areas |
| `omniarena.join.bypass` | op | Declared in `plugin.yml`; the join path does not currently check it |
| `omniarena.kit.slots.5` / `.10` | false | Larger personal kit limit |

Note: two nodes are checked in code but are not declared in `plugin.yml`: `omniarena.bet`
(declared as the required node of `/oa bet`) and `omniarena.admin.override` (required by
`/oa override`). Operators and holders of `omniarena.admin` always pass the override check. Grant
`omniarena.bet` explicitly if your permissions plugin treats undeclared nodes as false, but note
that `/oa bet` is not enforced centrally by the dispatcher beyond the command's own checks.
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
| `goal` | Goal type of the arena's game type (`KILLS` / `LAST_ALIVE`) |
| `state` | Match state (WAITING, STARTING, RUNNING, ENDING, RESETTING) |
| `arena_kills`, `arena_deaths`, `arena_streak` | Per-match counters of the player |
| `alive`, `players`, `max_players` | Alive count, player count, arena max |
| `time_left`, `time_left_seconds` | Remaining time (`m:ss` / seconds) |
| `team`, `team_color` | Team name / color enum |

Outside an arena, in-match placeholders return an empty string (text) or `0` (numbers) so TAB
lines collapse cleanly. Unknown placeholders return `null`.

TAB: set `integrations.tab.takeover: true` and run `/oa reload` to stop OmniArena drawing its own
sidebar and boss bar; build them from the placeholders in TAB instead.

---

## Integrations

All integrations are soft: OmniArena runs without them.

| Plugin | Used for |
|--------|----------|
| Vault + an economy plugin | Betting and tournament prizes. Without it betting is disabled and prizes are logged as unpaid. |
| PlaceholderAPI | The `omniarena` expansion (registered on enable, unregistered on disable) |
| WorldGuard | Anti border-hopping: a combat-tagged player cannot enter a region where the `pvp` flag is `deny` |
| LuckPerms | Hook object exists (primary group and permission lookups); not used by shipped features |
| LibsDisguises | Undisguise on combat tag (`pvp.combat.disable-disguise`) |
| TAB | Takeover mode (see above) |

`plugin.yml` also lists `WorldEdit`, `FastAsyncWorldEdit`, `PermissionsEx`, `EssentialsX`,
`DiscordSRV`, `HolographicDisplays` as soft dependencies (load-order only). No code path uses them.
The `integrations.*` toggles in `config.yml` are not read by the plugin (only
`integrations.tab.takeover` is).

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
  session-minutes: 30       # fixed lifetime of a session token
  max-sessions: 1           # 0 = unlimited
```

**Access.** Run `/oa website` in game (or from the console) to create a session. You get a link of
the form `<public-url or http://127.0.0.1:<port>>/?token=<token>`. Every `/api/*` request must carry
the token (`?token=`); the HTML/JS/CSS shell is served without it. If the session limit is
reached, free a slot with `/oa override killweb`. Tokens are 24 random bytes, URL-safe base64.

**Security guidance.**
- The default bind is `127.0.0.1`. Keep it unless you need remote access.
- The server speaks plain HTTP and the token travels in the URL. For remote access bind to
  `127.0.0.1` and put a reverse proxy with HTTPS in front (nginx, Caddy, Traefik), set
  `web.public-url` to the public HTTPS URL, and restrict the proxy (IP allow-list or VPN). Binding
  `0.0.0.0` exposes an HTTP endpoint that can change plugin config.
- Treat the link as a password: it grants config editing, arena creation and deletion. Do not
  paste it in public channels; revoke with `/oa override killweb`.
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
tab; all of them write the same live config.

Sections that the code actually reads:

| Section | Keys in use |
|---------|-------------|
| `plugin` | `debug` |
| `tools` | `wand-material` |
| `arena` | `inventory-restore` |
| `match` | `end-commands`, `end-delay`, `announce-global` |
| `regen` | `batch-size`, `progress.enabled`, `progress.interval-percent`, `animation.type`, `animation.particles`, `animation.sound` |
| `pvp` | the whole block except `combat.tag-on-damage`, `combat.tag-on-attack`, `combat.drop-on-combat-log`, `toggle.enabled`, `toggle.cooldown`, `newbie.block-pickup`, `killabuse.cooldown` |
| `kits` | `default-slot-limit`, `sharing-enabled`, `power-cap.enabled`, `power-cap.max-enchant-level` |
| `signs` | `glow-effect`, `update-interval` |
| `killstreak` | `milestones` |
| `boss-bar` | `low-time-threshold` |
| `integrations` | `tab.takeover` |
| `web` | `enabled`, `bind-address`, `port`, `public-url`, `session-minutes`, `max-sessions` |
| `betting` | `enabled`, `min-bet`, `max-bet`, `bet-window-seconds` |

Keys present in `config.yml` but **not currently read** by any code: `plugin.language`,
`plugin.metrics`, `database.*`, `arena.default-*`, `arena.lobby-timeout`, `arena.spectator-allowed`,
`arena.mid-match-join`, `arena.respawn-delay`, `arena.spawn-protection`,
`arena.teleport-back-on-leave`, `arena.region-protection`, `match.announce-arena`, `rewards.*`,
`kits.player-kits-enabled`, `kits.power-cap.banned-items`, global `visual.*`, `killstreak.sound`,
`killstreak.particle`, `scoreboard.*`, `boss-bar.timer-color`, `boss-bar.low-time-color`,
`integrations.vault|placeholderapi|worldguard|luckperms|discordsrv*`, `anticheat.*`,
`performance.*`, `web.title`, `betting.allow-spectator-bet`. Arena defaults and visual toggles are
per arena (stored in the arena file), not global. Changing the unread keys has no effect.

The settings GUI and web Config tab list roughly 250 entries: every key in `config.yml` and
`messages.yml`, plus a built-in list of extra descriptions, trimmed or padded to exactly 250. Some
of those extra or padded entries (for example `web.remote.*`, `modules.*`, `qol.*`,
`regen.laser.*`, `extras.auto-setting-N`) have no consumer in the code; setting them writes the
key to `config.yml` but changes no behaviour.

---

## Storage

All data is flat YAML under `plugins/OmniArena/`. No database is used (the `database.*` keys and
the shaded MySQL/Hikari libraries are placeholders).

| Path | Contents |
|------|----------|
| `config.yml`, `messages.yml` | Settings and text |
| `arenas/<name>.yml` | One arena (region, spawns as `world:x:y:z:yaw:pitch`, rules, team, visual, signs, end commands). `example.yml` in the jar is a template and is ignored by the loader |
| `snapshots/<arena-lowercase>.yml` | Regen snapshot. **Format v2**: `world`, `format: 2`, `palette` (list of `MATERIAL\|blockdata` strings), `runs` (strings `x,z,y0,length,paletteIndex`, vertical run-length encoded columns). Legacy format 1 (`x,y,z\|MATERIAL\|blockdata` lines) is still read |
| `sign-glowstone.yml` | `locations: ["world,x,y,z", ...]`: glowstone blocks placed behind join signs by the plugin |
| `admin-kits/<name>.yml` | Preset kits (structured `items` / `armor` sections) |
| `player-kits/<uuid>.yml` | Personal kits |
| `stats.yml` | Lifetime stats per UUID (kills, deaths, wins, losses, assists, draws, time-played) |
| `elo.yml` | `ratings.<uuid>`: name, rating, wins, losses, draws, highestRating |
| `tournaments.yml` | Active tournaments (registration and in-progress, including bracket rounds and winners). Finished and cancelled tournaments are removed |

Writes: stats are saved asynchronously when the plugin is enabled and inline during shutdown. ELO
uses a single writer thread with a main-thread snapshot, flushed on disable. Snapshots are encoded
on the main thread in slices and the file is written asynchronously. Arenas, tournaments and
kits are saved synchronously.

---

## Performance notes

- Snapshot capture is time-sliced: about 30,000 blocks of scanning per tick, then an async write.
- Regeneration places `regen.batch-size` blocks per tick for `INSTANT_RESTORE`; animated styles use
  `min(batch-size, 400)` per tick (1200 for ARMORED_DESCENT, RAIN_OF_LIGHT, METEOR_STRIKE,
  BINARY_FILL, QUANTUM_MESH). Decorative falling blocks are capped at 90 concurrent entities.
  Lower `regen.batch-size` for smoother ticks on weak hardware.
- The sign refresh task runs every `signs.update-interval` ticks (default 20) and never loads
  chunks to refresh a sign.
- Matchmaking runs once per second only when queues exist.
- Match timers run once per second per active match; scoreboards, boss bars and action bars update
  with it.
- The web server uses a 4-thread pool; every Bukkit API call from a handler is hopped to the
  main thread with a 10 s timeout.
- Region enforcement runs on player block movement for players in running arenas only.
- Large regions: take the snapshot once, keep `regen.animation.type` on `INSTANT_RESTORE` or a
  light style if restores cause lag.

---

## Troubleshooting

| Symptom | Likely cause and fix |
|---------|----------------------|
| `/oa ...` says OmniArena is stopped | Someone ran `/oa stop`. Run `/oa start` (the only command that works while stopped). |
| Cannot join an arena | Arena disabled, lockdown active (`/oa override unlockdown`), arena not in WAITING/STARTING state, or you are banned/not whitelisted. |
| Match never starts | Fewer players than `min-players` (default 2). Tournaments need the arena's min players <= 2. Check with `/oa info <arena>` and `/oa debug <arena>`. |
| Players spawn on top of each other | Fewer battle spawns than players: add spawns (`/oa spawn set <arena> BATTLE`). Spawn *i* goes to player *i*; with fewer spawns than players the list wraps around (players share pads). |
| `/oa region set` says select two points | Use the wand (needs `omniarena.admin.arena`; item from `tools.wand-material`). Both points must be in the same world. |
| Arena does not rebuild after a match | Regen is manual. Take a snapshot (`/oa region set` does it, or `/oa regen snapshot <arena>`) and run `/oa regen <arena>`. |
| "No snapshot" on regen | `snapshots/<arena>.yml` missing; re-run the snapshot. |
| Betting says unavailable | Vault or an economy provider is missing at plugin start. Install both and restart. |
| Dashboard link refused | `web.enabled: false`, port in use (see log `[Web] Failed to start dashboard`), or session limit reached (`/oa override killweb`). |
| Dashboard "Invalid or expired session" | Token older than `web.session-minutes` or revoked: run `/oa website` again. |
| Config change has no effect | The key is not read by the code (see list above), or it needs `/oa reload` / restart (`web.session-minutes`, `web.max-sessions`). |
| `/kit` opens another plugin's kit | OmniArena only intercepts `/kit` if it owns the command or the player is in an arena. |
| Placeholders blank | PlaceholderAPI missing, or the player is not in an arena (in-match values are blank/0 outside one). |
| Standalone `/list`, `/info`, `/help` clash with other plugins | These are registered by OmniArena; use `/oa list` etc. and resolve the clash in your command-alias setup. |

For deeper issues set `plugin.debug: true` and read the server log. Every `onDisable` step is
individually guarded, so a failure in one save does not skip the others.

---

## Known limitations

These are accurate as of the current code and are tracked so users are not surprised:

- **No player entry point to the queue or ELO views.** `QueueManager` (rating matchmaking, ranked
  matches) and `EloManager` (ratings, leaderboard) are fully implemented and wired into the match
  lifecycle, but no command, GUI or sign calls `QueueManager.joinQueue`, and no command displays
  ELO. ELO therefore only changes for matches flagged ranked, which only the queue creates.
  The web Players tab shows ELO, wins and losses from `elo.yml`.
- **Per-arena `goal.type` is ignored by the engine.** The win condition comes from the arena's
  game type; only `goal.value` (when > 0) overrides the game type's default kill target.
- **Respawn type WAVE** behaves like a 1 second respawn; `respawn.delay` applies to DELAYED only.
- **`/oa ready`** only prints a confirmation. **`/oa match reset`** is a no-op.
- **Party system** stands alone (create/invite/manage); parties do not join matches or queues as
  a group.
- **Rewards** (`rewards.*`) are not implemented; Vault is used only for betting and tournament
  prizes.
- **Database mode** (`database.*`) is not implemented; everything is YAML.
- **Admin kit creation by command:** `/oa kit create|delete|equip|list` resolve to the player kit
  commands even for admins. Admin preset kits are created by placing YAML files in `admin-kits/`
  and managed with the metadata subcommands.
- The matrix builds (Spigot, Bukkit, Purpur, 26.3/26.1/1.21.x jars) are being set up.
