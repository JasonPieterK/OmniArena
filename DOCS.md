# OmniArena Documentation

Complete reference for OmniArena 1.0.0, derived from the current source. For a shorter overview
see [README.md](README.md). Developer and AI-agent guides: [CLAUDE.md](CLAUDE.md),
[AGENTS.md](AGENTS.md).

Contents: 1 Quick start, 2 Arenas, 3 Game types, 4 Matches, 5 Regeneration, 6 Kits, 7 Join signs,
8 Web dashboard and settings GUI, 9 Combat (PvP) system, 10 Ratings and queue, 11 Party,
12 Betting, 13 Tournaments, 14 Integrations and placeholders, 15 Command reference,
16 Permissions, 17 Building and the jar matrix, 18 Configuration reference, 19 Storage,
20 Lifecycle, 21 Known limitations.

---

## 1. Quick start

1. Install the jar for your platform and Minecraft version into `plugins/`, restart.
2. `/oa wand` (or `/oa region wand`) gives the selection wand (item set by `tools.wand-material`,
   default `WOODEN_HOE`). Left-click one corner (position 1), right-click the opposite corner
   (position 2). The wand needs `omniarena.admin.arena`.
3. `/oa create <name>`: names are 1-32 characters from `A-Z a-z 0-9 _ -`. A new arena starts
   enabled.
4. `/oa region set <name>` saves the selection as the arena region and takes the regen snapshot.
   (Enabling an arena later requires at least one battle spawn.)
5. `/oa spawn set <name> BATTLE`, once per pad. Also set `LOBBY` and `SPECTATOR` spawns.
6. `/oa config set <name> game-mode <TYPE>` (default `DEATHMATCH`), plus `min-players`,
   `max-players`, `match-duration`, and so on (section 2).
7. Optional: `/oa sign create <name>` while looking at a sign (within 5 blocks).
8. Players join with `/oa join <name>` or `/join <name>`, or by right-clicking the sign.

---

## 2. Arenas

An arena is a named region with spawns, a game type, rules, team settings, visual toggles, chat
mode, optional end commands, signs, and enabled modules. Each is saved to `arenas/<name>.yml`
(see section 19).

### Arena states

`DISABLED`, `WAITING`, `STARTING`, `RUNNING`, `ENDING`, `RESETTING`, `MAINTENANCE`. An arena is
*enabled* unless DISABLED or MAINTENANCE; it is *joinable* in WAITING or STARTING. The saved
`enabled` flag is true for any state except DISABLED.

### Commands (admin, `omniarena.admin.arena` unless noted)

- `/oa create <name>`, `/oa delete <arena>`
- `/oa enable <arena>`: refuses when the arena has no battle spawns (warns when it has no region);
  `/oa disable <arena>` removes every player and spectator from the arena first.
- `/oa list`, `/oa info <arena>` (player commands)
- `/oa tp <arena>`: teleport to the arena
- `/oa debug <arena>`: print match debug state
- `/oa wand` and `/oa region wand`: the selection wand (`tools.wand-material`).
- `/oa region set <arena>`: needs both wand points in the same world; replaces the region, clears
  the selection, takes a snapshot. `/oa region info <arena>`.
- `/oa spawn set <arena> [BATTLE|LOBBY|SPECTATOR]` (default BATTLE; an unknown type is rejected) adds a spawn at your position
  and reports its number. `/oa spawn list <arena>`. `/oa spawn clear <arena> [type] [#]`: clear all
  of a type, or remove one by 1-based number.
- `/oa goal set <arena> <type> [value]`: type is validated (`KILLS`, `LAST_ALIVE`, `OBJECTIVE`,
  `TIME_LIMIT`), value must be >= 1; see the goal note below.
- `/oa module <enable|disable> <arena> <module>`: tab-complete suggests `killstreak`, `combo`,
  `power-up`, `item-spawner`, `environmental`, `leveling` (other names are rejected). Only
  `killstreak`, `combo` and `power-up` have code behind them; the default enabled set is
  `killstreak`, `combo`.
- `/oa chat set <arena> <ARENA|TEAM|GLOBAL>`: which players see in-arena chat.
- `/oa announcements set <arena> <enable|disable>`.

### Per-arena settings: `/oa config get|set <arena> <key> [value]`

Without an arena name, `/oa config set <key> <value>` edits the global `config.yml` (or
`messages.<key>` for messages); unknown keys are rejected (a key must be in the settings schema
or already exist). Arena values are validated strictly: booleans must be `true/false/yes/no/on/off`,
numbers must be non-negative integers, enums must be exact names, `min-players` must be >= 1 and
not exceed `max-players` (and vice versa). Setting `enabled false` removes everyone from the arena.
Valid arena keys:

`enabled`, `game-mode` (alias `gamemode`), `min-players`, `max-players`, `match-duration` (seconds),
`countdown` (seconds), `goal-type`, `goal-value`, `respawn-type` (`INSTANT|DELAYED|WAVE`),
`respawn-delay`, `spawn-protection`, `chat-mode`, `announcements`, `mid-match-join`,
`spectator-allowed`, `whitelist-enabled`, `team-enabled`, `team-count`, rules `fall-damage`,
`fire-damage`, `drowning`, `suffocation`, `friendly-fire`, `block-break`, `block-place`,
`item-drop`, `hunger-drain`, visual `visual-titles|sounds|particles|scoreboard|boss-bar|action-bar|holograms`,
and region `region-world`, `region-min-x|y|z`, `region-max-x|y|z`.

Defaults of a newly created arena: DEATHMATCH, min 2, max 16, duration 300 s, countdown 10 s,
goal KILLS 20, respawn INSTANT with delay 3, spawn protection 3 s, chat mode ARENA, announcements
on, mid-match join off, spectators allowed, friendly fire off, block break/place off, item drop off,
hunger drain off, modules `killstreak` + `combo`. (The global `arena.default-*` keys in `config.yml`
are not read; see section 18.)

### Lives

The arena file accepts `lives: <n>` (default 0). With `lives > 0` every player has that many lives
in any game type: each death costs one and shows the remaining count, the player respawns while
lives remain and is eliminated (spectator) at 0, overriding the game type's respawn behaviour.
There is no `/oa config` key or dashboard field for it yet; edit `arenas/<name>.yml` and reload.

### Spawns

Battle spawns are numbered in the order they were set. At match start **player *i* takes battle
spawn *i*** (modulo the number of spawns, so surplus players share pads). Respawns (modes with
respawn) use a random battle spawn. If there is no lobby spawn the lobby is a random battle spawn;
if there is no spectator spawn it is the lobby spawn.

### Goal note

The game type fixes the win condition (`KILLS` or `LAST_ALIVE`). For team-enabled arenas the
condition is evaluated per team (team kills, or last team standing). The arena's `goal-value`
overrides the game type's default kill target only when it is greater than 0. The arena's
`goal-type` is stored and shown in the dashboard but the match engine does not read it. The
saved default `goal-value` of a new arena is 20, so kill-target modes use 20 unless you set
`goal-value` to 0 to fall back to the mode default.

### Region protection and boundaries

`RegionHandler.isLocationProtected` treats a region as protected only while its arena is
RUNNING or STARTING. During a RUNNING match, a player who leaves the region is teleported to a
random battle spawn. The `arena.region-protection` config key is not read.

---

## 3. Game types (16)

Set per arena with `/oa config set <arena> game-mode <TYPE>`, the web editor, or `/oa settings`.
Each type defines a loadout (applied by `GameModeKits`), a win condition, respawn behaviour and
special rules.

| Type (`TYPE`) | Display name | Win condition | Default target | Respawns | Natural regen | Special |
|---------------|--------------|---------------|:--------------:|:--------:|:-------------:|---------|
| `DEATHMATCH` | Deathmatch | KILLS | 15 | yes | yes | FFA |
| `TEAM_DEATHMATCH` | Team Deathmatch | KILLS | 30 | yes | yes | two teams (see note below) |
| `LAST_ONE_STANDING` | Last One Standing | LAST_ALIVE | - | no | yes | |
| `SUMO` | Sumo | LAST_ALIVE | - | no | yes | ring-out |
| `CLASSIC_PVP` | Classic PvP | KILLS | 10 | yes | yes | |
| `CRYSTAL_PVP` | Crystal PvP | KILLS | 5 | yes | yes | |
| `MACE` | Mace | KILLS | 10 | yes | yes | |
| `SWORD_DUEL` | Sword Duel | LAST_ALIVE | - | no | yes | |
| `AXE_SHIELD` | Axe & Shield | LAST_ALIVE | - | no | yes | |
| `UHC` | UHC | LAST_ALIVE | - | no | **no** | no natural/food regen |
| `POTION_PVP` | Potion PvP | KILLS | 10 | yes | yes | |
| `NODEBUFF` | NoDebuff | KILLS | 10 | yes | yes | |
| `ARCHER` | Archer | KILLS | 12 | yes | yes | |
| `NETHERITE_OP` | Netherite OP | KILLS | 20 | yes | yes | |
| `SPLEEF` | Spleef | LAST_ALIVE | - | no | yes | ring-out |
| `GLADIATOR` | Gladiator | KILLS | 5 | yes | yes | |

Descriptions (also exposed as `%omniarena_gamemode_desc%`):
Deathmatch: free-for-all, first to the kill target. Team Deathmatch: two teams, shared kill count.
Last One Standing: one life each. Sumo: knockback stick, no armor. Classic PvP: iron armor, iron
sword, a bow. Crystal PvP: netherite, end crystals, obsidian and totems. Mace: mace, elytra and wind
charges. Sword Duel: netherite sword, gapples, one life. Axe & Shield: axe and shield, one life.
UHC: no natural regen, golden apples to heal. Potion PvP: splash potions. NoDebuff: gapples and
buff potions. Archer: power bow, light armor. Netherite OP: fully enchanted netherite. Spleef:
efficiency shovel, no armor. Gladiator: no armor, stone sword, shield.

- **One-life modes** (`respawns = false`): a dead player is dropped into spectator mode and counts
  as eliminated.
- **Ring-out** (Sumo, Spleef): dropping below the platform counts as a death.
- Unknown game type names fall back to `DEATHMATCH`.
- **Team Deathmatch note:** teams exist only when the arena's `team-enabled` flag is on
  (`/oa config set <arena> team-enabled true`, `team-count` 2 by default); choosing the game type
  does not turn it on. In team arenas `KILLS` modes end when a team's kill count reaches the target
  and `LAST_ALIVE` modes end when one team has players alive. Without teams the individual top
  killer decides.
- When a match was created by the queue with a named admin kit, that kit replaces the game type's
  loadout (for the match and for respawns).

---

## 4. Matches

State machine: `WAITING -> STARTING -> RUNNING -> ENDING -> RESETTING`, then the match object is
removed and the arena returns to WAITING.

1. **Join.** `/oa join` first rejects: already in an arena, unknown/disabled arena, the player is
   queued, not whitelisted (when the arena whitelist is on), arena not joinable, or arena full (the
   whitelist and full checks are skipped with `omniarena.join.bypass`). `joinArena` itself rejects
   when lockdown is on, the player is already in an arena or spectating, the arena is not joinable
   (WAITING/STARTING, or RUNNING when the arena allows mid-match join), or the player is on the
   arena blacklist. A manual join cancels any queue entry. The player's complete state (location,
   game mode, health, food, saturation, XP, potion effects, flight, speeds, invisibility and, if
   `arena.inventory-restore` (default true), inventory and armor) is saved; they are put in
   ADVENTURE mode at the lobby spawn, healed, effects removed, and frozen (walk/fly speed 0) while
   waiting.
2. **Countdown.** A 1 s repeating task. With fewer than the minimum players the match stays
   WAITING and players see a "Waiting for players" title; the countdown resets. The minimum is the
   arena's `min-players`, except ranked (queue) matches where it is clamped to at most 2. Otherwise it counts down from
   the arena `countdown`, with chat/title/sound at 10 and 5 to 1 seconds. At 0 `startMatch` runs.
3. **Start.** Teams are assigned for team-enabled arenas (shuffled, round-robin). Each player is
   teleported to a numbered battle spawn, set to SURVIVAL, healed, given the loadout, and given
   spawn protection (`spawn-protection` seconds). Scoreboard and boss-bar timer are created
   unless TAB takeover is on. Betting is notified (`onMatchStart`).
4. **Running.** A 1 s task ticks the match timer and updates scoreboard/boss bar/action bar. At
   time 0 in a `KILLS` mode with more than one player, the strictly leading player (or team, for
   team arenas) wins; ties and `LAST_ALIVE` modes end with outcome `TIMEOUT` (treated as a draw).
5. **Deaths.** Death drops are cleared unless the arena allows item drop, keep-inventory is forced,
   kills are recorded (first blood, kill announcement, revenge, killstreak module, team kills),
   then end conditions are checked. Respawn modes respawn after the arena respawn delay and
   re-equip the loadout; one-life modes eliminate.
6. **End conditions.** `KILLS`: the leader (or team) reaching the target wins. `LAST_ALIVE`: one
   player (or team) left alive wins; none alive is a draw. Additionally, if a match that started
   with more than one side drops to a single player or team (others left), that side wins.
7. **End.** `endMatch` (idempotent) cancels tasks, sets ENDING, then in order: ELO (ranked
   matches only), betting settlement, tournament hook, titles/sounds/fireworks, boss bars
   removed, end commands, stats, optional global announcement (`match.announce-global`), and a
   delayed reset after `match.end-delay` seconds (default 5).
8. **Reset.** Remaining players and spectators are removed (their saved state is restored,
   including the original location; without a saved state they go to bed or world spawn in
   SURVIVAL), boards removed, and the match is cleaned up. Cleanup refunds any still-pending bets
   and only releases the arena if this match still owns it.

### Outcomes

`WIN`, `LOSS`, `DRAW`, `FORFEIT`, `TIMEOUT`. `DRAW` and `TIMEOUT` count as draws in stats and ELO.
Leaving a running **ranked** two-player match is a forfeit: the leaver loses and the opponent
wins (ELO applied). Leaving an unranked match just removes the player, then end conditions are
re-checked; an empty match is cleaned up. If a ranked match collapses before it starts, the
remaining player is returned to the queue they came from.

### Spectating

`/oa spectate <arena>` (needs the match to exist and `spectator-allowed`). Spectators are in
SPECTATOR mode and invisible; `/oa leave` restores their saved state and location.

### End commands

Per-arena `end-commands` (if non-empty) otherwise `match.end-commands`. Placeholders: `{winner}`,
`{loser}`, `{arena}`, `{mode}`, `{duration}` (seconds), `{winner_kills}`. Default executor is the
console; prefix `console:` is accepted explicitly, and `player:` runs the command as the winner.
No leading slash. Example: `match.end-commands: ["oa regen {arena}"]` rebuilds the arena after
every match.

### Isolation and respawns

Arena fighters cannot damage players outside their match (including melee and projectiles), and
outsiders, spectators and other matches cannot damage them. A dead player in an arena respawns
inside it: lobby spawn outside the fighting phase, a battle spawn in a respawning mode while
fighting, the spectator spawn once eliminated. Tournament-reserved arenas (see section 13) refuse
joins from anyone who is not in the current pending tournament match.

### Item drops

Manual item drops (the drop key) are cancelled in arenas whose `item-drop` rule is off
(`PlayerDropItemListener`); death drops are cleared under the same rule.

### Chat

`chat-mode` ARENA sends a player's chat only to the match's players and spectators, TEAM sends it
only to team members (or to the arena if the player has no team), GLOBAL leaves chat untouched.
Chat from spectators is not intercepted.

---

## 5. Regeneration

After matches an arena can be rebuilt from a snapshot. **Regeneration is manual**: nothing in the
match flow calls it. Run `/oa regen <arena>`, `/oa override purge <arena>`, or an end command.

### Snapshot

`/oa regen snapshot <arena>` (and `/oa region set`, `/oa override snapshotall`) scan the region
column by column, about 30,000 blocks per tick, into a palette plus vertical run-length encoding,
then write `snapshots/<arena>.yml` off the main thread. File format v2 is described in section 19;
legacy v1 files are still readable.

### Restore

`/oa regen <arena>` loads the snapshot asynchronously, orders the blocks for the chosen style,
then places them in per-tick batches on the main thread. A second restore of an arena that is
already restoring only queues its completion callback. `cancelAll` (on `/oa stop` and on disable)
instantly finishes running restores, removes preview blocks, and removes falling-block entities.

### Commands

- `/oa regen <arena>`: restore now with `regen.animation.type`; refused while a match is active
  in that arena.
- `/oa regen snapshot <arena>`.
- `/oa regen style [type]`: set (or list) the default style. The command and the settings GUI
  accept only the 20 current names (an unknown name is rejected); the legacy aliases below are
  still honoured when found in `config.yml`.
- `/oa regen batch [n]`: set (or show) `regen.batch-size`.
- `/oa regen settings`: show current values. (Its fallback text for an unset style shows a stale
  name, `WAVE_SHUFFLE`; the effective default is `CENTER_OUT` which maps to `RADIATE_OUTWARD`.)
- `/oa regen progress|particles|sound <true|false>`: toggles `regen.progress.enabled`,
  `regen.animation.particles`, `regen.animation.sound`; any other value is rejected.

### Styles (20)

`SPIRAL_ASCENT`, `COLLAPSE_INWARD`, `RADIATE_OUTWARD`, `GRAVITY_DESCENT`, `UPWARD_SURGE`,
`RAIN_OF_LIGHT`, `ARMORED_DESCENT`, `CHAOTIC_ERUPTION`, `INSTANT_RESTORE`, `BEACON_WAVE`,
`VOID_SHOCKWAVE`, `ICE_CRYSTAL_GROWTH`, `DARK_VORTEX`, `METEOR_STRIKE`, `PRISM_CASCADE`,
`BINARY_FILL`, `PHOENIX_REBIRTH`, `QUANTUM_MESH`, `DIAMOND_FUSION`, `TSUNAMI_SURGE`.

Legacy names still accepted when read from `config.yml` and what they map to:

| Legacy | Current | Legacy | Current |
|--------|---------|--------|---------|
| `SPIRAL` | `SPIRAL_ASCENT` | `BEAM_SWEEP` | `BEACON_WAVE` |
| `OUTSIDE_IN` | `COLLAPSE_INWARD` | `SHOCKWAVE` | `VOID_SHOCKWAVE` |
| `CENTER_OUT` | `RADIATE_OUTWARD` | `CRYSTAL_GROWTH` | `ICE_CRYSTAL_GROWTH` |
| `TOP_TO_BOTTOM`, `TOP_DOWN` | `GRAVITY_DESCENT` | `VORTEX_REBUILD` | `DARK_VORTEX` |
| `BOTTOM_TO_TOP`, `BOTTOM_UP` | `UPWARD_SURGE` | `METEOR_DROP` | `METEOR_STRIKE` |
| `LASER_RAIN` | `RAIN_OF_LIGHT` | `PRISM_WAVE` | `PRISM_CASCADE` |
| `IRON_MAN` | `ARMORED_DESCENT` | `INSTANT` | `INSTANT_RESTORE` |
| `RANDOM_BURST` | `CHAOTIC_ERUPTION` | | |

Blank or unknown values resolve to `RADIATE_OUTWARD`.

Highlights: `ARMORED_DESCENT` drops red suit pieces as falling blocks; `METEOR_STRIKE` drops flaming
meteors that explode on impact; `RAIN_OF_LIGHT` streaks glowing blocks that vanish on impact. Falling
blocks are capped at 90 concurrent entities.

### Batch size

`INSTANT_RESTORE` places `regen.batch-size` blocks per tick (default 4000). Animated styles use
`min(regen.batch-size, 400)`; `ARMORED_DESCENT`, `RAIN_OF_LIGHT`, `METEOR_STRIKE`, `BINARY_FILL` and
`QUANTUM_MESH` use `min(regen.batch-size, 1200)`.

---

## 6. Kits

### Player kits: `/kit` or `/oa kit`

Both open the same combined menu (your kits and public preset kits) or run a subcommand:

- `/kit`: open the menu.
- `/kit create <name>`: save your inventory and armor as a personal kit. Names: letters, digits,
  `_`, `-`, `.` (max 32).
- `/kit list`: list your kits and slot usage.
- `/kit equip <name>`: equip a personal kit, or a public preset kit (admins can equip any). While
  in an arena this works only in the lobby phase (match WAITING/STARTING); preset kits enforce
  their permission node and cooldown (holders of `omniarena.admin` bypass the cooldown).
- `/kit delete <name>`, `/kit rename <old> <new>`, `/kit preview <name>` (personal kits and public
  preset kits).
- `/kit share <name> <player>`: send a copy offer; the recipient runs `/kit accept <name>` within
  1 minute (`kits.sharing-enabled`).
- `/kit help [sub]`.

Personal kit slots: `kits.default-slot-limit` (default 3), 5 with `omniarena.kit.slots.5`, 10 with
`omniarena.kit.slots.10` or OP. `kits.power-cap.enabled` and `kits.power-cap.max-enchant-level`
(default 5) cap enchant levels when a kit is saved, and unsafe items are dropped from saved kits. `kits.player-kits-enabled` and `kits.power-cap.banned-items`
exist in `config.yml` but are not read.

While you are in an arena, `/kit` is intercepted so OmniArena handles it even if another plugin
registered `/kit`. Outside arenas it only intercepts when OmniArena owns the command.

### Preset (admin) kits

Files in `admin-kits/<name>.yml`. On every load OmniArena installs any missing default among
`warrior`, `archer`, `tank`, `assassin`, `mage`, `healer`, `pyro`, `ninja`, `juggernaut`, `gunner`
(existing files are never overwritten). Fields: `name`, `public`, `description`, `icon`, `cooldown`,
`permission`, `arena`, `items` (slot, material, amount, enchants, unbreakable), `armor`.

Admin metadata commands (permission `omniarena.admin.kit`): `/oa kit setpublic <name>`,
`setprivate <name>`, `icon <name> <MATERIAL>` (must be an item), `description <name> <text>`,
`cooldown <name> <seconds>` (enforced per player and persisted in `kit-cooldowns.yml`),
`permission <name> <node>` (`none` or `-` clears it). `AdminKitCommand` also implements
`rename <old> <new>`, but `CommandManager.shouldUsePlayerCommand` does not list `rename`, so for an
admin `/oa kit rename` currently reaches the *player* rename (your own kits). Unknown kit names are
reported. Note that `/oa kit create`, `delete`, `equip` and `list` are routed to the *player* kit
command even for admins; the admin versions are unreachable through `/oa`. Create preset kits by placing a YAML file in `admin-kits/` and
restarting or running `/oa start`.

Queued ranked matches equip the admin kit named by the queue key (if one exists) instead of the
arena game type's loadout.

---

## 7. Join signs

- `/oa sign create <arena>`: look at an existing sign (within 5 blocks). It is registered, its text
  is written, and (if `signs.glow-effect`) a glowstone block is placed behind it when that
  position is air. Only glowstone the plugin placed is ever removed; positions are tracked in
  `sign-glowstone.yml`.
- `/oa sign remove`: look at a registered sign. `/oa sign list <arena>`.
- Sign text: `[OmniArena]`, the arena name, a status line (Click to Join, FULL, Starting...,
  IN PROGRESS, Resetting..., DISABLED, MAINTENANCE) and `players/max players`.
- Right-click: joins the arena. For a running arena without mid-match join the player is offered a
  clickable spectate message. Checks: arena enabled, not already in an arena, blacklist, whitelist.
- Signs refresh every `signs.update-interval` ticks (default 20); unloaded chunks are skipped; a
  sign block that is no longer a sign is dropped from the arena.

---

## 8. Web dashboard and settings GUI

### Dashboard

`/oa website` (permission `omniarena.admin`) prints a clickable session link. The server is a JDK
`HttpServer` bound to `web.bind-address:web.port` (default `127.0.0.1:8123`), started on plugin
enable and on `/oa start`, stopped on `/oa stop` and disable.

Tabs: **Dashboard** (live stats and arena cards), **Live Matches** (polled every 5 s), **Arenas**
(list and editor), **Players** (online players with ELO, wins, losses; search), **Config** (every
setting with controls and search), **Docs** (bundled markdown). Edits are drafted in the browser
and applied with the Apply button.

Endpoints (all but the static shell require `?token=`): `GET /` (page), `/styles.css`, `/app.js`
(public); `GET /api/state` (snapshot of settings and arenas), `POST /api/apply` (validated
changes), `POST /api/create-arena`, `POST /api/delete-arena`, `GET /api/docs`,
`GET /api/live-matches`, `GET /api/players?search=`.

Behaviour and limits:
- Sessions: random 24-byte tokens; fixed lifetime `web.session-minutes` (default 30, minimum 1);
  at most `web.max-sessions` at once (default 1, `0` = unlimited). `/oa override killweb` revokes all.
- `POST` bodies are limited to 1 MiB.
- `/api/apply` validates everything first (types, ranges, known keys, enums, min <= max players,
  region and rule fields) and applies nothing if any value is invalid, then writes all config keys
  in one save per file. Arena names must match `[A-Za-z0-9_-]{1,32}`.
- All Bukkit API access happens on the main thread (10 s timeout).
- The sessions manager reads `web.session-minutes` and `web.max-sessions` once, when the plugin
  enables; changing them needs a plugin restart. `web.enabled`, bind address and port are re-read
  on `/oa start`.
- Remote use: see README, "Web dashboard": bind to `127.0.0.1` behind an HTTPS reverse proxy and set
  `web.public-url`.
- The page loads Phosphor icons from unpkg without a Subresource Integrity attribute. To generate
  one: `curl -s https://unpkg.com/@phosphor-icons/web@2.1.1/src/bold/style.css | openssl dgst -sha384 -binary | openssl base64 -A`
  and add `integrity="sha384-<output>"` to the `<link>` in `web/index.html`.
- The Docs tab serves `src/main/resources/web/docs.md`, which is a separate copy from this file and
  can drift; keep them in sync when editing.

### Settings GUI

`/oa settings` (permission `omniarena.admin.reload`) opens a chest GUI for players: a category grid,
then paginated settings. Booleans toggle on click, numbers adjust (left +1, right -1, shift +/-10),
text and list values open a chat prompt. The console gets a text list.
`/oa settings set <key> <value>` works for scripting (booleans accept true/yes/on; lists accept
comma or newline separated values).

The same schema (about 250 entries) drives the GUI, the console list, and the web Config tab: all
`config.yml` and `messages.yml` keys plus extra descriptive entries, trimmed or padded to exactly
250. Entries that no code reads (for example `web.remote.*`, `modules.*`, `qol.*`, `regen.laser.*`,
`extras.auto-setting-N`) can be written but have no effect.

---

## 9. Combat (PvP) system

Server-wide, independent of arena matches. Master switch `pvp.enabled`. The old `/pmr` prefix is
now `/oa` (`/oa pmr` remains for admin actions).

- **Combat tag.** Hits tag both players for `pvp.combat.tag-duration` seconds (default 15).
- **Restrictions while tagged:** drop flight (`disable-fly`), force survival (`disable-gamemode`),
  strip invisibility, undisguise via LibsDisguises, block commands except the
  `allowed-commands` list (`block-commands`), enderpearls, teleports (`cancel-teleport`) and block
  placement (`block-block-place`).
- **Combat-log** (`punish-combat-log`, `combat-log-action` KILL | COMMAND | NOTHING,
  `combat-log-commands` with `{player}`, `broadcast-combat-log`).
- **PvP toggle:** per player (`/oa pvp`), default `pvp.toggle.default-state`, per world
  (`pvp.disabled-worlds`, `/oa pmr worlds <world>` toggles one), global (`/oa pvpglobal`,
  `pvp.global.pvp-enabled`).
- **Newbie protection** (`pvp.newbie.enabled`, `duration` seconds, `disable-on-attack`) and
  **respawn protection** (`pvp.respawn-protection.*`).
- **Anti kill-abuse** (`pvp.killabuse.enabled`, `max-kills` on the same victim within
  `time-window` seconds).
- **Anti border-hopping** (`pvp.border-hopping.prevent`): needs WorldGuard; blocks a tagged player
  from entering a region where the `pvp` flag is `deny`.
- **Auto-soup** (`pvp.auto-soup.enabled` default false, `heal-amount`, `soup-material`).
- **Blood** (`pvp.blood.enabled`, `block`), **transfer drops** (`pvp.transfer-drops`),
  **lava/fire guard** near protected players (`pvp.block-lava-fire-near-disabled`,
  `lava-fire-radius`).
- **Override:** `/oa pvpoverride` lets an admin attack regardless of toggles and protections.

Commands: `/oa pvp [player]`, `pvpstatus [player]`, `pvptag [player] [seconds]` (admin; seconds 1-3600, default
`pvp.combat.tag-duration`), `untag [player]`,
`pvpglobal <on|off>`, `newbie [disable]` (players) and `newbie add|remove|checktime <player>
[seconds]` (admin), `pvpinfo [player]`, `pvplist`, `pvpoverride`, `pmr <help|cleanup|reload|worlds>`.
Targeting another player requires `omniarena.pvp.admin`.

Config keys under `pvp` that exist but are not read: `combat.tag-on-damage`, `combat.tag-on-attack`,
`combat.drop-on-combat-log`, `toggle.enabled`, `toggle.cooldown`, `newbie.block-pickup`,
`killabuse.cooldown`.

---

## 10. Ratings and queue

### ELO (`rating/EloRating`, `EloManager`)

Standard ELO on a 400-point scale: expected = 1 / (1 + 10^((opp - own) / 400)), K = 32 (constant),
default rating 1000, floor 100, tracks wins, losses, draws and highest rating. Persisted to
`elo.yml`. Ratings update for **ranked matches** only (wins, losses, forfeits, and two-player
draws/timeouts). Both players' new ratings are computed from the pre-match ratings. The `elo.*`
config keys described in older documentation do not exist. There is currently no command that
displays ratings; the web Players tab shows them.

### Queue (`queue/QueueManager`)

- A matchmaking task runs every 20 ticks (1 s) while the plugin runs (`/oa stop` cancels it,
  `/oa start` restarts it).
- Players queue per kit name (case-insensitive key). A player cannot queue while in an arena or
  already queued; disconnected players or players who entered an arena are dropped each tick.
- Pairing: players in a queue are sorted by rating; adjacent-first search pairs the first player
  with the next player whose rating gap is within the allowed range. The allowed range starts at
  **200** and grows by **50 for every 10 seconds** of waiting (the longer waiter of the two
  counts), capped at **1000**.
- A free arena is any enabled, WAITING, joinable arena with no running match. If none is free
  matchmaking stops for that tick and everyone stays queued.
- The match is created by joining both players; it is flagged **ranked**, remembers its queue name,
  and, if the queue key names an admin kit, `queueKit` is set. If joining fails both players are
  re-queued with their original wait time. If a ranked match collapses before it starts, the
  remaining player is put back into the queue by `MatchManager`.
- A manual `/oa join` or a tournament launch removes the player from any queue. Queue matchmaking
skips arenas that are reserved by an in-progress tournament, arenas with `max-players` below 2,
and does nothing while lockdown is on.
- Outside that re-queue path, `joinQueue(Player, kitName)` has no caller in this build, see
  section 21.

---

## 11. Party

`/party` (also `/oa party`, permission `omniarena.use`): `create`, `disband` (leader),
`invite <player>`, `accept`, `decline`, `leave`, `kick <player>` (leader), `leader <player>`,
`info`. Invites expire after 60 seconds (cleanup task every 30 s); a party holds at most 10
members. Members are notified of joins and leaves. A quitting player is removed from their party.
The `party.*` config keys described in older documentation do not exist; the limits are
constants. Parties are not yet consumed by matches or queues.

---

## 12. Betting

Requires Vault and an economy provider present when the plugin enables (checked once at startup).

- `/oa bet place <arena> <player> <amount>`: the arena must have a live match that is not
  ENDING/RESETTING, and the target must be online and a participant (not a spectator) of that arena.
  Non-OP players need the `omniarena.bet` node (see section 16).
- `/oa bet cancel`: refund your bet while the betting window is open.
- `/oa bet info [arena]`: your active bet and, with an arena, its bet count and pool.

Rules: **one active bet per player at a time** (cancel it before placing another); amount between `betting.min-bet` (default 100) and `betting.max-bet`
(default 10000) and within the player's balance; money is withdrawn on placement. The window is
open before the match starts and for `betting.bet-window-seconds` (default 30) after it starts;
afterwards you cannot place or cancel.

Settlement when a match ends: winner pool = bets on the winner. If there is no winner (draw,
timeout, forfeit without winner id) or nobody backed the winner, **every bet is refunded**.
Otherwise each winning bet receives `totalPool x (yourBet / winnerPool)` rounded down to cents
(paid even to offline players); losing bets are lost. Bets still pending at match cleanup (aborted match) are refunded;
all bets are refunded on `/oa stop` and on plugin disable. `betting.allow-spectator-bet` exists in
`config.yml` but is not read.

---

## 13. Tournaments

Single elimination on one arena (one live match at a time). Command `/oa tournament`.

| Command | Who | Notes |
|---------|-----|-------|
| `create <name> <arena> <maxPlayers>` | players with `omniarena.tournament.create` (default true) or admins | Arena must exist, max >= 2, name unique (case-insensitive). Broadcast on create. |
| `join <name>` | players | Registration state only. Starts automatically when the tournament is full. |
| `leave <name>` | players | Registration state only. |
| `start <name>` | creator or `omniarena.tournament.admin` | Drops participants who are offline at that moment; needs >= 2 online players. |
| `status <name>` (alias `info`), `list` | anyone | |
| `delete <name>` (alias `cancel`) | admin | Removes the tournament (no entry fees exist to refund). |
| `setprize <name> <1st\|2nd\|3rd> <amount>` | admin | Non-negative number. |

### Bracket

While a tournament is IN_PROGRESS its arena is **reserved**: `MatchManager.joinArena` refuses anyone
who is not in the current round's pending match. Seed order is join order. For a non power-of-two field the top seeds receive byes in round 1
(for 6 players: 2 byes, 2 real matches). Each launch picks the first pending match, removes both
players from any queue, joins them to the arena via the normal join path, and waits for the match
to end before launching the next (`match.end-delay + 2` seconds later). A 5-second safety tick
(`TournamentManager` task, every 100 ticks) relaunches matches that had to wait (arena busy, a
player busy elsewhere) or that vanished without a result. If a player is offline, the opponent
gets a walkover after `tournament.walkover-grace-seconds` (default 60); if a player leaves the arena
during a running tournament match the other player is declared the winner. Tournament matches are
**not ranked** (no ELO).

### Draw and timeout rule

If a match ends without a winner (draw or timeout), the winner is decided by: the only player
still in the arena (or online); else the player with higher current health; else a coin flip. The
bracket never stalls. (Kill-based modes already resolve a strict timeout leader to a winner, so this
rule mainly covers ties and last-alive draws.)

### Prizes (Vault)

When the final ends, `prizeFirst` goes to the champion, `prizeSecond` to the final's loser, and
`prizeThird` is **split evenly among the losers of the round before the final** (the semifinal
losers; there is no third-place match), each share rounded down to cents. Prizes <= 0 are skipped;
with no economy a warning is logged. Finished tournaments are removed from memory and from `tournaments.yml`.

### Persistence

Registration and in-progress tournaments are saved to `tournaments.yml` (participants, prizes, all
bracket rounds with winners, current round index) on every state change, and on `/oa stop` and
plugin disable. On the next start in-progress brackets are restored and resumed one tick after
startup; an unusable bracket falls back to registration. `/oa stop` clears the in-memory
tournaments, `/oa start` reloads them.

---

## 14. Integrations and placeholders

Soft dependencies (auto-detected on enable): Vault (economy), PlaceholderAPI, WorldGuard, LuckPerms.
`plugin.yml` additionally lists WorldEdit, FastAsyncWorldEdit, PermissionsEx, EssentialsX,
DiscordSRV and HolographicDisplays for load order only. The `integrations.*` toggles (other than
`integrations.tab.takeover`) are not read; LibsDisguises is reached by reflection (soft dependency) only for
the combat undisguise.

### Placeholders `%omniarena_<name>%`

Lifetime: `kills`, `deaths`, `wins`, `losses`, `kd`, `winrate` (suffix `%`), `time_played`.
Context: `in_arena`, `arena` / `arena_name`, `gamemode`, `gamemode_name`, `gamemode_desc`, `goal`,
`state`, `arena_kills`, `arena_deaths`, `arena_streak`, `alive`, `players`, `max_players`,
`time_left` (`m:ss`), `time_left_seconds`, `team`, `team_color`.

Outside an arena: text placeholders return an empty string, numeric ones `0`, `arena` returns
`N/A`. Unknown names return `null`.

### TAB

Set `integrations.tab.takeover: true` and `/oa reload`. OmniArena then does not create its own
sidebar and boss bar; build them from the placeholders above in TAB's config.

---

## 15. Command reference

Run `/oa <subcommand> help` for usage of any subcommand and `/oa help [guide]` for guides
(`makingarena`, `regenarena`, `regenstyles`, `signjoin`, `kits`, `startmatch`, `stopmatch`,
`settings`, `permissions`, `troubleshooting`, `performance`).

### Dispatcher rules

- `/oa` (aliases `omniarena`, `arena`) requires `omniarena.use` to be run at all.
- While stopped, only `/oa start` runs.
- Admin subcommands require `PermissionChecker.hasAdmin`: console, OP, `omniarena.admin`, or the
  specific node. Player subcommands are checked against the subcommand's own required node for
  non-OP players (`omniarena.use`, `omniarena.spectate`, `omniarena.kit.use`, `omniarena.pvp`,
  `omniarena.bet`, `omniarena.tournament.create`); the console and OPs always pass.
- When a name exists as both a player and admin command (`kit`), admins get the admin version only
  for `setpublic`, `setprivate`, `icon`, `description`, `cooldown`, `permission`.

### Player subcommands

`join`, `leave`, `spectate`, `list`, `info`, `stats`, `ready`, `kit`, `party`, `bet`,
`tournament`, `help`, `pvp`, `pvpstatus`, `pvptag`, `untag`, `pvpglobal`, `newbie`, `pvpinfo`,
`pvplist`, `pvpoverride` (the last group enforces `omniarena.pvp.admin` inside for admin actions).

### Admin subcommands

`create`, `delete`, `enable`, `disable`, `config`, `spawn`, `region`, `goal`, `module`, `match`,
`force`, `forcewin`, `regen`, `sign`, `kit` (metadata actions), `tp`, `debug`, `announcements`,
`chat`, `override`, `reload`, `version`, `wand`, `website`, `settings`, `start`, `stop`, `restart`,
`pmr`.

### `/oa stats`

`/oa stats [player]` (unknown players are reported), `/oa stats global [kills|wins|kd]` (top 10),
`/oa stats reset <player>` (online or known offline player; needs `omniarena.admin.stats`).

### `/oa match <start|stop> <arena>` (`omniarena.admin.match`)

`start` begins the arena's existing match immediately (needs at least one player; refused when
already running/ending); `stop` ends it as a forfeit with no winner; `reset` removes every player
and spectator from the arena (the match is then cleaned up).

### `/oa force start <arena>` and `/oa forcewin <arena> <player|team>` (`omniarena.admin.match`)

Force start the arena's existing match; declare a winner. `forcewin` requires a RUNNING match and
an exact online player in it, or an exact team name (for example `Team RED`).

### `/oa override <action>` (`omniarena.admin.override`)

`killweb`, `forceend <arena>` (alias `endarena`), `evacuate <arena>`, `freeze <arena>` (toggle),
`lockdown`, `unlockdown`, `purge <arena>` (evacuate, then restore snapshot), `god <arena>`
(toggle invulnerability), `broadcast <message>`, `snapshotall` (arenas with a region only),
`resetstats <player>`, `forcewin <arena> <player|team>`, `forcestart <arena>`,
`startarena <arena>` (arena must be enabled and already have players in its match).

### Lifecycle: `/oa stop`, `/oa start`, `/oa restart`, `/oa reload`

See section 20.

---

## 16. Permissions

| Node | Default | Used by |
|------|---------|---------|
| `omniarena.use` | true | `/oa`, join/leave/list/info/stats/ready/help/party |
| `omniarena.kit.use` | true | `/kit` |
| `omniarena.spectate` | true | `/spectate` |
| `omniarena.pvp` | true | player PvP commands |
| `omniarena.pvp.admin` | op | tag, untag, global, override, targeting others |
| `omniarena.tournament.create` | true | create, join, leave, start own |
| `omniarena.tournament.admin` | op | start/delete/setprize any |
| `omniarena.admin` | op | parent of the admin nodes; lifecycle, website, pmr |
| `omniarena.admin.arena` | op | arena, region, spawn, config, regen, wand, modules |
| `omniarena.admin.kit` | op | preset kit metadata |
| `omniarena.admin.match` | op | `/oa match` |
| `omniarena.admin.sign` | op | `/oa sign` |
| `omniarena.admin.stats` | op | `/oa stats reset` |
| `omniarena.admin.reload` | op | reload, version, settings |
| `omniarena.admin.override` | op | `/oa override` (child of `omniarena.admin`) |
| `omniarena.bet` | **not declared** | required by `/oa bet` for non-OP players; grant it explicitly |
| `omniarena.join.bypass` | op | skips the whitelist and full-arena checks on `/oa join` |
| `omniarena.kit.slots.5`, `.10` | false | personal kit limits |

Per-kit permission nodes (preset kit `permission` field) are free-form.

---

## 17. Building and the jar matrix

### Current build

Java 21, Maven. `mvn package` runs the compiler, the surefire tests (JUnit 5), and the shade
plugin, producing `target/OmniArena-1.0.0.jar` (FastBoard and HikariCP relocated under
`com.omniarena.shaded`; Gson and MySQL connector bundled). A `maven-antrun-plugin` step copies the
jar to a Windows desktop path; that step is developer-machine specific and should be removed or
made conditional for CI. The `target/` directory is committed to the repository.

Dependencies (provided): Paper API `26.2.build.31-alpha`, VaultAPI 1.7, PlaceholderAPI 2.11.6,
WorldGuard 7.0.12, LuckPerms API 5.4.

### Planned matrix (24 jars)

Platforms: `paper`, `spigot`, `bukkit`, `purpur`. Versions: `26.3`, `26.2`, `26.1` (Java 25) and
`1.21.11`, `1.21.10`, `1.21.9` (Java 21). Artifact name `OmniArena-<platform>-<mcversion>.jar`.
Planned invocation:

```bash
mvn -P<platform>,mc-<version> package     # e.g. mvn -Ppaper,mc-26.2 package
```

The profiles are being added; until they exist use plain `mvn package`. Build commands for the
whole matrix are tracked in [CLAUDE.md](CLAUDE.md) (Build section) so they stay next to the
developer rules. Java 25 profiles need a JDK 25 toolchain.

---

## 18. Configuration reference

`config.yml` keys, grouped. "Read" means some code reads the key; "-" means it exists in the file
and the dashboard but nothing reads it.

| Key | Default | Read | Meaning |
|-----|---------|:----:|---------|
| `plugin.debug` | false | yes | Verbose logging flag |
| `plugin.language`, `plugin.metrics` | en, true | - | |
| `tools.wand-material` | WOODEN_HOE | yes | Selection wand item |
| `database.*` | disabled | - | Placeholder for a MySQL backend |
| `arena.inventory-restore` | true | yes | Save/restore inventories |
| `arena.default-*`, `lobby-timeout`, `spectator-allowed`, `mid-match-join`, `respawn-delay`, `spawn-protection`, `teleport-back-on-leave`, `region-protection` | | - | Per-arena values are used instead |
| `match.end-commands` | [] | yes | Global end commands |
| `match.end-delay` | 5 | yes | Seconds before reset |
| `match.announce-global` | false | yes | Broadcast results server-wide |
| `match.announce-arena` | true | - | |
| `regen.batch-size` | 4000 | yes | Blocks per tick (see section 5) |
| `regen.progress.enabled`, `interval-percent` | true, 10 | yes | Chat progress for command-driven restores |
| `regen.animation.type` | CENTER_OUT | yes | Default style |
| `regen.animation.particles`, `sound` | true | yes | |
| `pvp.*` | see section 9 | mostly | |
| `rewards.*` | | - | Not implemented |
| `kits.default-slot-limit` | 3 | yes | |
| `kits.sharing-enabled` | true | yes | |
| `kits.power-cap.enabled`, `max-enchant-level` | true, 5 | yes | |
| `kits.player-kits-enabled`, `power-cap.banned-items` | | - | |
| `signs.glow-effect`, `update-interval` | true, 20 | yes | |
| `visual.*` | | - | Use per-arena visual flags |
| `killstreak.milestones` | 5/10/15/20 | yes | Map of kills to title |
| `killstreak.sound`, `particle` | | - | |
| `scoreboard.*`, `boss-bar.timer-color`, `low-time-color` | | - | |
| `boss-bar.low-time-threshold` | 60 | yes | |
| `integrations.tab.takeover` | false | yes | |
| `integrations.*` (others) | | - | |
| `anticheat.*`, `performance.*` | | - | |
| `web.enabled`, `bind-address`, `port`, `public-url`, `session-minutes`, `max-sessions` | true, 127.0.0.1, 8123, "", 30, 1 | yes | |
| `web.title` | | - | |
| `betting.enabled`, `min-bet`, `max-bet`, `bet-window-seconds` | true, 100, 10000, 30 | yes | |
| `betting.allow-spectator-bet` | true | - | |
| `tournament.walkover-grace-seconds` | 60 | yes | Wait for an offline player before a walkover |

`messages.yml`: every player-facing string, MiniMessage format, with a `prefix` key prepended by
`ConfigManager.getMessage`. Missing keys fall back to the defaults bundled in the jar (so upgrades
do not show "Missing message"). Tournament messages: `tournament-usage`, `-no-permission`,
`-not-found`, `-none`, `-list-header`, `-list-entry`, `-prize-set`, `-prize-invalid`,
`-players-only`. Placeholders inside messages use `{name}` style.

Changing config: edit the file and `/oa reload`; or `/oa config set`, `/oa settings`, or the web
Config tab (all write the live config and save). Reload also reloads arenas, but only when no
match is running.

---

## 19. Storage

All under `plugins/OmniArena/`:

- `arenas/<name>.yml`: `name`, `enabled`, `game-mode`, `min-players`, `max-players`,
  `match-duration`, `countdown`, `chat-mode`, `announcements`, `mid-match-join`, `spectator-allowed`,
  `spawn-protection`, `goal.type|value`, `respawn.type|delay`, `rules.*`, `team.enabled|count`,
  `modules.enabled`, `lives`, `region.world|min-x..max-z`, `spawns.battle|lobby|spectator` (each a list
  of `world:x:y:z:yaw:pitch`), `signs` (same format), `end-commands`, `visual.*`. The bundled
  `example.yml` also shows `team.colors` and `classes`, which the loader does not read.
- `snapshots/<arena-lowercase>.yml`: **format v2**:
  ```yaml
  world: world
  format: 2
  palette:            # index = position; value = MATERIAL|blockdata-string
    - STONE|minecraft:stone
    - AIR|minecraft:air
  runs:               # "x,z,y0,length,paletteIndex" vertical run-length columns
    - "100,200,64,5,0"
  ```
  The legacy format 1 (`x,y,z|MATERIAL|blockdata` lines) is still loaded.
- `sign-glowstone.yml`: `locations: ["world,x,y,z"]`.
- `admin-kits/<name>.yml`, `player-kits/<uuid>.yml` (section `kits.<name>`), `kit-cooldowns.yml`
  (preset-kit cooldown expiries per player).
- `stats.yml`: `stats.<uuid>.{name,kills,deaths,wins,losses,assists,draws,time-played}`.
- `elo.yml`: `ratings.<uuid>.{name,rating,wins,losses,draws,highestRating}`.
- `tournaments.yml`: `tournaments.<name>.{name,arena,maxPlayers,creator,state,participants,prizes.first|second|third,bracket.rounds,bracket.currentRound}`.
- `config.yml`, `messages.yml`.

Save behaviour: arenas, kits and tournaments are written synchronously. Stats are written on an
async task when the plugin is enabled and inline during `onDisable`. ELO snapshots on the main
thread and writes on a single daemon executor (inline when the plugin is disabled), flushed with
`shutdown()` on disable. Snapshots serialize on the main thread in slices and write async.

---

## 20. Lifecycle

### onEnable order

Config load; construct managers (visual, stats, kits, kit share, signs, modules, block restore,
arenas, matches, kit command, combat, ELO, queue, party, betting, tournaments); load data
(stats, kits, arenas, ELO); start queue, party, tournaments; integrations; web server; commands
and tab completers; listeners; sign update task; startup banner.

### onDisable order

Set `stopped`; cancel sign task; leave all online players of active matches (so results are
included in saves); `BlockRestoreModule.cancelAll`; stop queue and party; save tournaments;
refund all bets; save stats, arenas, ELO (flush ELO executor); shut down visuals and modules; stop
the web server; release integrations. Each step is individually guarded so one failure does not
skip the rest.

### `/oa stop`, `/oa start`, `/oa restart` (`omniarena.admin`)

- **stop:** sets `stopped = true`; finishes running regens; cancels **all** scheduler tasks of the
  plugin; removes all players and spectators from matches; saves stats and arenas; refunds bets; saves and clears
  tournaments; stops the web server; shuts down visuals and modules; stops queue and party.
  While stopped, listeners check `isStopped()` and do nothing, all commands except `/oa start`
  are refused, and `/kit` and `/party` report the stopped message.
- **start:** only when stopped. Clears `stopped`, reloads config, arenas, kits, stats, ELO; restarts
  queue, party, tournaments (restoring saved brackets), the web server, the sign task, and combat
  tasks. It does not rebuild the PlaceholderAPI hook, Vault economy lookup (betting keeps its
  startup state) or listeners (they stay registered).
- **restart:** stop now, start 20 ticks later (the stop cancels all tasks, so use `/oa restart`
  rather than scheduling around it).
- **reload:** reloads config and messages; reloads arenas only if no match is active; reloads the
  killstreak module; restarts the sign task and combat tasks; refreshes all signs.

---

## 21. Known limitations

- No command or sign enters the queue for the first time (`QueueManager.joinQueue` is only called
  by `MatchManager` to re-queue a player whose ranked match fell apart) and no command shows ELO.
  Ranked matches, and therefore ELO changes, only come from the queue.
- Older documentation described `/oa elo ...`, `/oa queue ...`, `/oa class`, `/oa editor`, `elo.*`,
  `queue.*`, `party.*` config keys, 16/15 regen counts and `rewards`; those are not present. The
  dashboard command is `/oa website`.
- Arena `goal-type` (stored but not read by the engine), `respawn-type` WAVE timing (flat 1 s),
  arena whitelist/blacklist population (no command stores them and they are not persisted),
  global `visual.*`, `rewards.*`, `database.*` are unimplemented or inert, as described in the
  sections above.
- Admin preset kits cannot be created, deleted or equipped through `/oa kit` (player version
  wins); manage them as files (the metadata actions work through `/oa kit`; `/oa kit rename` for admins is routed to the player command).
- `omniarena.bet` is required by `/oa bet` but not declared in `plugin.yml`, so non-OP players are
  denied unless a permissions plugin grants it.
- The Docs tab serves a separate bundled copy of this documentation.
- The Spigot/Bukkit/Purpur and non-26.2 builds are pending the build-matrix work (section 17).
