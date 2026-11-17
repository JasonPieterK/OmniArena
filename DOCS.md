# OmniArena Documentation

Complete reference for OmniArena 1.0.0, derived from the current source. For a shorter overview
see [README.md](README.md). Developer and AI-agent guides: [CLAUDE.md](CLAUDE.md),
[AGENTS.md](AGENTS.md).

Contents: 1 Quick start, 2 Arenas, 3 Game types, 4 Matches, 5 Regeneration, 6 Kits, 7 Join signs
and leaderboard holograms, 8 Web dashboard and settings GUI, 9 Combat (PvP) system, 10 Ratings and
queue, 11 Party, 12 Betting, 13 Tournaments, 14 Integrations and placeholders, 15 Command
reference, 16 Permissions, 17 Building and the jar matrix, 18 Configuration reference, 19 Storage,
20 Lifecycle, 21 Known limitations.

---

## 1. Quick start

1. Install the jar for your platform and Minecraft version into `plugins/` (section 17), restart.
2. `/oa wand` (or `/oa region wand`) gives the selection wand (item set by `tools.wand-material`,
   default `WOODEN_HOE`). Left-click one corner (position 1), right-click the opposite corner
   (position 2). The wand needs `omniarena.admin.arena`.
3. `/oa create <name>`: names are 1-32 characters from `A-Z a-z 0-9 _ -`. A new arena starts
   enabled.
4. `/oa region set <name>` saves the selection as the arena region and takes the regen snapshot.
   The arena is rebuilt from that snapshot automatically after every match (section 5), so build
   the arena first and set the region last (or re-run `/oa regen snapshot <name>` after changes).
   (Enabling an arena later requires at least one battle spawn.)
5. `/oa spawn set <name> BATTLE`, once per pad. Also set `LOBBY` and `SPECTATOR` spawns.
6. `/oa config set <name> game-mode <TYPE>` (default `DEATHMATCH`), plus `min-players`,
   `max-players`, `match-duration`, `lives`, and so on (section 2).
7. Optional join sign: place a sign and write `[OmniArena]` on line 1 and the arena name on line 2
   (needs `omniarena.admin.sign`), or run `/oa sign create <name>` while looking at a placed sign
   (within 5 blocks).
8. Players join with `/oa join <name>` or `/join <name>`, by right-clicking the sign, or through
   the ranked queue (`/oa queue join`, section 10).

---

## 2. Arenas

An arena is a named region with spawns, a game type, rules, team settings, visual toggles, chat
mode, optional end commands, signs, and enabled modules. Each is saved to `arenas/<name>.yml`
(see section 19).

### Arena states

`DISABLED`, `WAITING`, `STARTING`, `RUNNING`, `ENDING`, `RESETTING`, `MAINTENANCE`. An arena is
*enabled* unless DISABLED or MAINTENANCE; it is *joinable* in WAITING or STARTING. The saved
`enabled` flag is true for any state except DISABLED. After a played match the arena stays
RESETTING until its automatic block regeneration has finished (section 5).

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
- `/oa module <enable|disable> <arena> <module>`: the valid modules are `killstreak`, `combo` and
  `power-up` (tab completion lists them; any other name is rejected with an error listing the
  valid ones). The default enabled set is `killstreak`, `combo`.
- `/oa access <arena> <whitelist|blacklist> <add|remove|list> [player]` and
  `/oa access <arena> whitelist-enabled <true|false>` (permission `omniarena.admin.access`): per-arena
  join control, saved in the arena file (`access.*`). The blacklist is always enforced (a player
  blacklisted while inside the arena is removed from it); the whitelist only while
  `whitelist-enabled` is true. OPs and holders of `omniarena.join.bypass` skip the whitelist, never
  the blacklist. Players are resolved from online players or players who have joined before (no
  network lookup). `/oa access <arena>` shows a summary. `/oa join`, signs, the queue and
  tournament launches all go through `MatchManager.joinArena`, which enforces both lists.
- `/oa chat set <arena> <ARENA|TEAM|GLOBAL>`: which players see in-arena chat.
- `/oa announcements set <arena> <enable|disable>`.

### Per-arena settings: `/oa config get|set <arena> <key> [value]`

Without an arena name, `/oa config set <key> <value>` edits the global `config.yml` (or
`messages.<key>` for messages); unknown keys are rejected (a key must be in the settings schema,
that is in `config.yml` or `messages.yml`, or already exist). Arena values are validated strictly:
booleans must be `true/false/yes/no/on/off`, numbers must be non-negative integers, enums must be
exact names, `min-players` must be >= 1 and not exceed `max-players` (and vice versa), `lives` is
0-100. Setting `enabled false` removes everyone from the arena. Valid arena keys:

`enabled`, `game-mode` (alias `gamemode`), `min-players`, `max-players`, `match-duration` (seconds),
`countdown` (seconds), `goal-type`, `goal-value`, `respawn-type` (`INSTANT|DELAYED|WAVE`),
`respawn-delay`, `respawn-wave-interval` (seconds, WAVE only, default 10), `spawn-protection`, `lives`, `chat-mode`, `announcements`, `mid-match-join`,
`spectator-allowed`, `whitelist-enabled`, `team-enabled`, `team-count`, rules `fall-damage`,
`fire-damage`, `drowning`, `suffocation`, `friendly-fire`, `block-break`, `block-place`,
`item-drop`, `hunger-drain`, visual `visual-titles|sounds|particles|scoreboard|boss-bar|action-bar|holograms`,
and region `region-world`, `region-min-x|y|z`, `region-max-x|y|z`.

Defaults of a newly created arena come from `config.yml`: the `arena.default-*` keys
(`default-game-mode` DEATHMATCH, `default-min-players` 2, `default-max-players` 16,
`default-countdown` 10, `default-match-duration` 600 s), `arena.spectator-allowed` (true),
`arena.mid-match-join` (false), `arena.respawn-delay` (3), `arena.spawn-protection` (3) and the
`visual.titles|sounds|particles|scoreboard|boss-bar|action-bar|holograms` toggles (all true).
Fixed defaults: goal value 20, respawn INSTANT, chat mode ARENA, announcements on, friendly fire
off, block break/place off, item drop off, hunger drain off, fall/fire/drowning/suffocation damage
on, modules `killstreak` + `combo`, lives 0. Changing the global defaults only affects arenas
created afterwards.

### Lives

`lives` (0-100, default 0 = off) is settable with `/oa config set <arena> lives <n>`, in the web
dashboard arena editor, or in the arena file. With `lives > 0` every player has that many lives in
any game type: each death costs one and tells the player how many remain, the player respawns
(timing per the arena respawn type) while lives remain and is eliminated (spectator) at 0,
overriding the game type's own respawn behaviour. On `KILLS`-goal arenas the last player or team
with lives left also wins. Lives are shown on the scoreboard (`Lives: n`) and the action bar.

### Spawns

Battle spawns are numbered in the order they were set. At match start **player *i* takes battle
spawn *i*** (modulo the number of spawns, so surplus players share pads). Respawns (modes with
respawn) use a random battle spawn. If there is no lobby spawn the lobby is a random battle spawn;
if there is no spectator spawn it is the lobby spawn.

### Goal note

The win condition is the arena's goal type. By default that is the game type's (`KILLS` or
`LAST_ALIVE`). `/oa goal set <arena> <type> [value]` (or `/oa config set <arena> goal-type <TYPE>`)
overrides it; the override is stored as `goal.type` plus `goal.type-override: true` in the arena
file, and setting the type back to the game type's own goal clears it. A `goal.type` without the
override flag (older files) is ignored. `OBJECTIVE` and `TIME_LIMIT` have no end rule of their own:
the match then ends only by its timer or when a single side is left. For team-enabled arenas the
condition is evaluated per team (team kills, or last team standing). The arena's `goal-value`
overrides the game type's default kill target only when it is greater than 0. A new arena is saved
with `goal-value` 20, so kill-target modes use 20 unless you set `goal-value` to 0 to fall back to
the mode default.

### Region protection and boundaries

- While an arena is not RUNNING, players who are not OP and lack the arena admin permission cannot
  break or place blocks inside its region when the arena's `block-break` / `block-place` rule is
  off and `arena.region-protection` is true (default).
- While RUNNING, break/place are blocked whenever the rule is off (OPs are exempt).
- During a match water and lava cannot flow out of the arena region (fluid containment), so the
  regeneration never has to clean up outside it.
- During a RUNNING match, a player who leaves the region is teleported to a random battle spawn.

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
removed and the arena returns to WAITING (after its automatic regeneration, section 5).

1. **Join.** `/oa join` first rejects: already in an arena, unknown/disabled arena, the player is
   in the matchmaking queue (leave it first with `/oa queue leave`), not whitelisted (when the
   arena whitelist is enabled and the player has no bypass), arena not joinable, or arena full (the whitelist and full checks are
   skipped with `omniarena.join.bypass`). Join signs apply the same rules. `joinArena` itself
   rejects when lockdown is on, the player is already in an arena or spectating, the arena is not
   joinable (WAITING/STARTING, or RUNNING when the arena allows mid-match join), the arena is
   reserved by a tournament, the player is on the arena blacklist, the arena whitelist is enabled and
   the player is not on it (and has no `omniarena.join.bypass`), or the arena holds a ranked
   (queue) match that is already underway. Joins done by the queue or by a tournament launch drop
   the player's queue entry. The player's complete state (location, game mode, health, food,
   saturation, XP, potion effects, flight, speeds, invisibility and, if `arena.inventory-restore`
   (default true), inventory and armor) is saved (and mirrored to `player-states/<uuid>.yml`
   unless `arena.persist-player-state` is false); they are put in ADVENTURE mode at the lobby
   spawn, healed, effects removed, and frozen (walk/fly speed 0) while waiting.
2. **Countdown.** A 1 s repeating task. With fewer than the minimum players the match stays
   WAITING and players see a "Waiting for players" title; the countdown resets. If
   `arena.lobby-timeout` seconds (default 120, 0 = never) pass below the minimum, the lobby is
   closed and everyone is removed. The minimum is the arena's `min-players`, except ranked (queue)
   matches where it is clamped to at most 2. Otherwise it counts down from the arena `countdown`,
   with chat/title/sound (`visual.countdown-sounds`) at 10 and 5 to 1 seconds. At 0 `startMatch`
   runs.
3. **Start.** The arena's TIME_REWIND change log is cleared. Teams are assigned for team-enabled
   arenas (shuffled, round-robin; queued parties keep their preset side). Each player is
   teleported to a numbered battle spawn, set to SURVIVAL, healed, given the loadout, and given
   spawn protection (`spawn-protection` seconds). Scoreboard and boss-bar timer are created
   unless TAB takeover is on. Betting is notified (`onMatchStart`).
4. **Running.** A 1 s task ticks the match timer and updates scoreboard/boss bar/action bar. At
   time 0 in a `KILLS` mode with more than one player, the strictly leading player (or team, for
   team arenas) wins; ties and `LAST_ALIVE` modes end with outcome `TIMEOUT` (treated as a draw).
5. **Deaths.** Death drops are cleared unless the arena allows item drop, keep-inventory is forced,
   kills are recorded (first blood, kill announcement `visual.kill-announcements`, revenge,
   killstreak and combo modules, team kills, `rewards.kill-*` unless the kill-abuse window
   suppresses it), then end conditions are checked. Respawn modes (or arenas with `lives`) respawn
   after the arena respawn delay and re-equip the loadout; one-life modes eliminate.
6. **End conditions.** `KILLS`: the leader (or team) reaching the target wins. `LAST_ALIVE`: one
   player (or team) left alive wins; none alive is a draw. Additionally, if a match that started
   with more than one side drops to a single player or team (others left), that side wins.
7. **End.** `endMatch` (idempotent) cancels tasks, sets ENDING, then in order: ELO (ranked
   matches only), betting settlement, tournament hook, titles/sounds/fireworks
   (`visual.victory-fireworks`), boss bars removed, end commands, `rewards.win-*`, stats, the arena
   announcement (`match.announce-arena` and the arena's `announcements`), optional global
   announcement (`match.announce-global`), and a delayed reset after `match.end-delay` seconds
   (default 5). A failure in one of these steps is logged and does not strand the arena.
8. **Reset.** Remaining players and spectators are removed (their saved state is restored,
   including the original location; without a saved state they go to bed or world spawn in
   SURVIVAL), boards removed, and the match is cleaned up. Cleanup refunds any still-pending bets,
   only releases the arena if this match still owns it, and then starts the automatic block
   regeneration (section 5); the arena is RESETTING (not joinable) until that finishes.

### Outcomes

`WIN`, `LOSS`, `DRAW`, `FORFEIT`, `TIMEOUT`. `DRAW` and `TIMEOUT` count as draws in stats and ELO.
Leaving a running **ranked** two-player match is a forfeit: the leaver loses and the opponent
wins (ELO applied). In a ranked team match the last member of a side leaving a running two-team
match counts as a loss for that side. Leaving an unranked match just removes the player, then end
conditions are re-checked; an empty match is cleaned up. If a ranked match collapses before it
starts, the remaining players are returned to the queue they came from (a party re-queues through
its leader).

### Spectating

`/oa spectate <arena>` (needs the match to exist and `spectator-allowed`). Spectators are in
SPECTATOR mode and invisible (`visual.spectator-invisibility`); `/oa leave` restores their saved
state and location.

### Presentation

Per match, the scoreboard (FastBoard; title `scoreboard.title`, refresh `scoreboard.update-interval`
ticks, minimum 1 s), boss-bar timer (colors `boss-bar.*`) and action bar are shown unless the
arena's `visual-scoreboard`, `visual-boss-bar` or `visual-action-bar` flag is off or
`integrations.tab.takeover` is on. The scoreboard and action bar show lives when the arena uses
them. Titles, sounds and particle effects (including victory fireworks) are on only when both the
global `visual.titles|sounds|particles` key and the arena's `visual-titles|sounds|particles` flag
are on (`VisualGate`); the finer global toggles (`killstreak-titles`, `kill-announcements`,
`victory-fireworks`, `countdown-sounds`, `spectator-invisibility`) still apply on top. Leaderboard
holograms need the global `visual.holograms`; a hologram placed inside an arena's region
additionally follows that arena's `visual-holograms` (hologram outside any arena: global only).

### End commands

Per-arena `end-commands` (if non-empty) otherwise `match.end-commands`. Placeholders: `{winner}`,
`{loser}`, `{arena}`, `{mode}`, `{duration}` (seconds), `{winner_kills}`. Default executor is the
console; prefix `console:` is accepted explicitly, and `player:` runs the command as the winner.
No leading slash. Regeneration no longer needs an end command: it runs automatically (section 5).

### Isolation and respawns

Arena fighters cannot damage players outside their match (including melee and projectiles), and
outsiders, spectators and other matches cannot damage them. A dead player in an arena respawns
inside it: lobby spawn outside the fighting phase, a battle spawn in a respawning mode while
fighting, the spectator spawn once eliminated.

Respawn types: `INSTANT` (1 tick), `DELAYED` (`respawn-delay` seconds on the death screen) and
`WAVE`. With `WAVE` the dead player is parked in spectator mode at the arena's spectator spot and
returns together with everyone else who died since the previous wave: waves fire every
`respawn-wave-interval` seconds of match time (arena key, default 10; `/oa config set <arena>
respawn-wave-interval <n>`, file key `respawn.wave-interval`). Waiting players see a countdown on
the action bar (and a title for the last 3 seconds), subject to the arena's `visual-action-bar` and
`visual-titles` flags. Waves are driven by the match timer, so a wave is released at most once per
second boundary; leaving the arena or the match ending clears the waiting list. Tournament-reserved arenas (see section 13) refuse
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

Arenas are rebuilt from a snapshot of their region. **Regeneration is automatic**: after every
played match the arena is restored without any command, and it is not joinable (RESETTING) until
the rebuild has finished. It can also be run by hand.

### Automatic regeneration

- `regen.auto` (default `true`) switches it on globally. Override it per arena with
  `/oa regen auto <arena> <true|false|default>` (`default` removes the override). The override is
  stored as `regen.arenas.<arena-name-lowercase>.auto` in `config.yml` (not part of the settings
  schema, so it does not appear in the GUI or dashboard).
- It starts when a match is cleaned up after its end reset (`match.end-delay`), and only for arenas
  with a defined region. A match that is still in the lobby (WAITING/STARTING) when its last player
  leaves is cleaned up without a regeneration.
- The style is `regen.animation.type`; `regen.batch-size` and the particle/sound toggles apply.
  Chat progress (`regen.progress.*`) is only sent for commands.
- No snapshot: a warning is logged, online admins (`omniarena.admin` / `.admin.arena`) are told to
  run `/oa regen snapshot <arena>`, and the arena reopens with its current blocks.
- Failure: the console and admins are told why and the arena is reopened with its current blocks, so
  it never stays stuck in RESETTING.
- On `/oa stop` and on plugin disable running regens are finished instantly (remaining blocks are
  placed, previews removed), including regens that the stop itself just started.
- While an arena regenerates, the TIME_REWIND change log is not recorded; stray items, arrows, XP
  orbs, primed TNT, falling blocks and effect clouds inside the region are removed.

### Snapshot

`/oa regen snapshot <arena>` (and `/oa region set`, `/oa override snapshotall`) scan the region
column by column, about 30,000 blocks per tick, into a palette plus vertical run-length encoding,
then write `snapshots/<arena>.yml` off the main thread (atomically). File format v2 is described
in section 19; legacy v1 files are still readable. The snapshot also records **tile entities**:
container contents (chests, trapped chests, barrels, shulker boxes, hoppers, dispensers, droppers,
furnaces, blast furnaces, smokers, brewing stands), sign text (both sides, dye color, glow, wax)
and banner patterns. They are written back after the block is placed and containers are cleared
first, so items players took or stored during the match are reset. Container items are encoded by
the platform layer (native NBT bytes on Paper/Purpur, Bukkit object streams on Spigot/Bukkit), so a
snapshot containing containers taken on one flavor cannot be read by the other; re-take it after
switching (blocks, signs and banners are unaffected).

### Restore

A restore loads the snapshot asynchronously, orders the blocks for the chosen style, then places
them in per-tick batches on the main thread without neighbour physics. A second restore of an arena
that is already restoring only queues its completion callback. `cancelAll` (on `/oa stop` and on
disable) instantly finishes running restores, removes preview blocks, and removes falling-block
entities.

### Commands

- `/oa regen <arena>`: restore now with `regen.animation.type`; refused while a match is active
  in that arena or while it is already regenerating.
- `/oa regen snapshot <arena>`.
- `/oa regen style [type]`: set (or list) the default style. The command and the settings GUI
  accept the 10 current names and the legacy names below (an unknown name is rejected).
- `/oa regen batch [n]`: set (or show) `regen.batch-size`.
- `/oa regen auto <arena> <true|false|default>`: per-arena automatic regeneration override.
- `/oa regen settings`: show current values (including the global `regen.auto`).
- `/oa regen progress|particles|sound <true|false>`: toggles `regen.progress.enabled`,
  `regen.animation.particles`, `regen.animation.sound`; any other value is rejected.
- `/oa override purge <arena>`: evacuate, then restore the snapshot.

### Fluid and change tracking

During a match, water and lava are kept inside the region (section 2). A per-arena change log
records, in order, the blocks changed by break/place, explosions, fluid flow, burning, fading and
block formation (max 200k positions); it is cleared at match start and after a regen and feeds
`TIME_REWIND`.

### Styles (10)

| Style | Order | Effect |
|-------|-------|--------|
| `INSTANT` | none | batched placement (`regen.batch-size`), one whoosh sound |
| `SPHERE` (default) | distance from region center, ascending | thin particle shell at the current radius |
| `RISE` | y ascending | particle plane rising |
| `FALL` | y descending | particle plane sinking |
| `SWEEP` | vertical plane along the longest horizontal axis | spark plane sweeping across |
| `TIME_REWIND` | exact reverse of the order blocks changed during the match (change log above); unlogged blocks follow in SPHERE order | counter-clockwise particle clock above the arena, rising pitch |
| `GALAXY` | 4 spiral arms (angle + k*radius), outward within each arm | PORTAL/END_ROD along the arm |
| `HOLOGRAM_SCAN` | y ascending, two passes | pass 1: light blue glass previews under a scan plane; pass 2: real blocks replace them (previews always cleaned on cancel/finish) |
| `METEOR_SHOWER` | random impact points (preferring blocks changed in the match), each rebuilds its surroundings; impacts accelerate | FLAME/LAVA trails and explosion particles, capped decorative falling magma (no damage, no explosion) |
| `OVERGROWTH` | flood fill from 3-6 seeds through adjacent differing blocks | spore particles, grass/azalea sounds |

Sort keys are computed off the main thread when the plan loads. Effects are capped per tick and sent only
to players within 64 blocks of the region.

Old style names and aliases are mapped when read from `config.yml` and also accepted by the commands:
`RADIATE_OUTWARD`/`COLLAPSE_INWARD`/`VOID_SHOCKWAVE`/`CENTER_OUT`/`OUTSIDE_IN`/`SHOCKWAVE`/`WAVE_SHUFFLE` -> `SPHERE`;
`UPWARD_SURGE`/`BOTTOM_UP`/`BOTTOM_TO_TOP` -> `RISE`;
`GRAVITY_DESCENT`/`RAIN_OF_LIGHT`/`ARMORED_DESCENT`/`TOP_DOWN`/`TOP_TO_BOTTOM`/`LASER_RAIN`/`IRON_MAN` -> `FALL`;
`BEACON_WAVE`/`TSUNAMI_SURGE`/`BEAM_SWEEP` -> `SWEEP`;
`SPIRAL_ASCENT`/`DARK_VORTEX`/`QUANTUM_MESH`/`SPIRAL`/`VORTEX_REBUILD` -> `GALAXY`;
`METEOR_STRIKE`/`CHAOTIC_ERUPTION`/`PHOENIX_REBIRTH`/`RANDOM_BURST`/`METEOR_DROP` -> `METEOR_SHOWER`;
`ICE_CRYSTAL_GROWTH`/`DIAMOND_FUSION`/`CRYSTAL_GROWTH` -> `OVERGROWTH`;
`BINARY_FILL`/`PRISM_CASCADE`/`PRISM_WAVE` -> `HOLOGRAM_SCAN`;
`INSTANT_RESTORE` -> `INSTANT`.
Blank values resolve to `SPHERE`; unknown values resolve to `SPHERE` with a console warning listing the valid
styles. Commands reject unknown names and list the 10 valid styles. Falling blocks are capped at 90
concurrent entities.

### Batch size

`INSTANT` places `regen.batch-size` blocks per tick (default 4000). Animated styles use
`min(regen.batch-size, 1200)`; `METEOR_SHOWER` ramps up from about 15% of that.

---

## 6. Kits

### Player kits: `/kit` or `/oa kit`

Both open the same combined menu (your kits and public preset kits) or run a subcommand:

- `/kit`: open the menu.
- `/kit create <name>`: save your inventory and armor as a personal kit. Names: letters, digits,
  `_`, `-`, `.` (max 32). Disabled when `kits.player-kits-enabled` is false.
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
(default 5) cap enchant levels when a kit is saved; unsafe items are dropped from saved kits, and
while the power cap is enabled the materials in `kits.power-cap.banned-items` (default bedrock,
command blocks, barrier, spawner, structure block) are also refused.

While you are in an arena, `/kit` is intercepted so OmniArena handles it even if another plugin
registered `/kit`. Outside arenas it only intercepts when OmniArena owns the command.

### Preset (admin) kits

Files in `admin-kits/<name>.yml`. On every load OmniArena installs any missing default among
`warrior`, `archer`, `tank`, `assassin`, `mage`, `healer`, `pyro`, `ninja`, `juggernaut`, `gunner`
(existing files are never overwritten). Fields: `name`, `public`, `description`, `icon`, `cooldown`,
`permission`, `arena`, `items` (slot, material, amount, enchants, unbreakable), `armor`.

Admin kit commands (permission `omniarena.admin.kit`) have an unambiguous form,
`/oa kit admin <action> ...` (alias `/oa adminkit <action> ...`), with the actions `create <name>`
(from your inventory), `delete <name>`, `rename <old> <new>`, `equip <name>`, `setpublic <name>`,
`setprivate <name>`, `icon <name> <MATERIAL>` (must be an item), `description <name> <text>`,
`cooldown <name> <seconds>` (enforced per player and persisted in `kit-cooldowns.yml`),
`permission <name> <node>` (`none` or `-` clears it) and `list`. Unknown kit names are reported.

Compatibility shortcut: for admins plain `/oa kit setpublic|setprivate|icon|description|cooldown|permission ...`
still reaches the admin command; every other plain `/oa kit <action>` (create, delete, equip, list,
rename, ...) is the player command, so use the `admin` form for those.

Queued ranked matches equip the admin kit named by the queue key (if one exists) instead of the
arena game type's loadout.

---

## 7. Join signs and leaderboard holograms

### Join signs

- Create by **placing a sign**: write `[OmniArena]` (or `[oa]`) on line 1 and the arena name on
  line 2 (needs `omniarena.admin.sign`; an unknown arena name is flagged on the sign and nothing is
  registered). Or place any sign and run `/oa sign create <arena>` while looking at it (within 5
  blocks). The text is written automatically, and (if `signs.glow-effect`) a glowstone block is
  placed behind a wall sign when that position is air. Only glowstone the plugin placed is ever
  removed; positions are tracked in `sign-glowstone.yml`.
- `/oa sign remove`: look at a registered sign (breaking it as an admin also removes it).
  `/oa sign list <arena>`.
- Registered signs are protected: only admins with `omniarena.admin.sign` can break them, and
  explosions and pistons cannot move or destroy them.
- Sign text: `[OmniArena]`, the arena name, a status line (Click to Join, FULL, Starting...,
  IN PROGRESS, Resetting..., DISABLED, MAINTENANCE) and `players/max players`.
- Right-click: joins the arena (750 ms click cooldown). Admins sneaking with an item in hand can
  edit/dye the sign instead. For a running arena without mid-match join the player is offered a
  clickable spectate message. Checks: `omniarena.use`, arena enabled, not already in an arena, not
  in a queue, blacklist, whitelist.
- Signs refresh every `signs.update-interval` ticks (default 20); unloaded chunks are skipped and a
  loading chunk refreshes its signs; a sign block that is no longer a sign is dropped from the
  arena.

### Leaderboard holograms (`/oa hologram`, permission `omniarena.admin.hologram`)

- `/oa hologram create <id> <kills|wins|kd|elo> [size]`: places a multi-line `TextDisplay` entity
  at your position showing the top `size` players (1-15, default 10) of that leaderboard.
  `/oa hologram move <id>` moves it to you, `/oa hologram remove <id>` deletes it,
  `/oa hologram list` lists them.
- Definitions persist in `holograms.yml` and are respawned on startup, `/oa start` and chunk load;
  text is refreshed in place every `holograms.refresh-seconds` (default 30) from cached stats/ELO
  data (no chunk is force-loaded and no stat entries are created).
- `visual.holograms: false` stops holograms from spawning (definitions are kept). Invalid entries in
  `holograms.yml` are left untouched with a console warning.
- The per-arena `visual-holograms` flag exists but is not consulted (section 21).

---

## 8. Web dashboard and settings GUI

### Dashboard

`/oa website` (permission `omniarena.admin`) prints a clickable session link. The server is a JDK
`HttpServer` bound to `web.bind-address:web.port` (default `127.0.0.1:8123`), started on plugin
enable and on `/oa start`, stopped on `/oa stop` and disable.

Tabs: **Dashboard** (live stats and arena cards), **Live Matches** (polled every 5 s), **Arenas**
(list and editor, including `lives`), **Players** (online players with ELO, wins, losses; search),
**Config** (every `config.yml` and `messages.yml` setting with controls and search), **Docs**
(bundled markdown). Edits are drafted in the browser and applied with the Apply button.

Endpoints (all `/api/*` calls need the session token, sent as `Authorization: Bearer <token>`, or
`X-OmniArena-Token`, or the legacy `?token=` query parameter): `GET /` (page), `/styles.css`,
`/app.js` (public); `POST /api/logout` (revokes the session); `GET /api/state` (snapshot of settings and arenas), `POST /api/apply` (validated
changes), `POST /api/create-arena`, `POST /api/delete-arena`, `GET /api/docs`,
`GET /api/live-matches`, `GET /api/players?search=`.

Behaviour and limits:
- Sessions: random 24-byte tokens; sliding lifetime `web.session-minutes` (default 30, minimum 1; each request renews it);
  tokens are compared in constant time; the page removes the token from the address bar and keeps it
  in `sessionStorage`; the Log out button revokes it; `/oa stop` revokes all sessions;
  at most `web.max-sessions` at once (default 1, `0` = unlimited). `/oa override killweb` revokes all.
- `POST` bodies are limited to 1 MiB. Wrong methods get `405`; errors are JSON `{"ok":false,"error":"..."}`.
- Same-origin only: no CORS headers are sent; responses carry `Cache-Control: no-store`, a CSP,
  `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` and `Referrer-Policy: no-referrer`.
- If the port is taken the console says so; change `web.port` and run `/oa restart`.
- `/api/apply` validates everything first (types, ranges, known keys, enums, min <= max players,
  lives 0-100, region and rule fields) and applies nothing if any value is invalid, then writes all
  config keys in one save per file. Arena names must match `[A-Za-z0-9_-]{1,32}`.
- All Bukkit API access happens on the main thread (10 s timeout).
- The sessions manager reads `web.session-minutes` and `web.max-sessions` once, when the plugin
  enables; changing them needs a plugin restart. `web.enabled`, bind address and port are re-read
  on `/oa start`. `web.title` (browser tab title) is read per page load.
- Remote use: see README, "Web dashboard": bind to `127.0.0.1` behind an HTTPS reverse proxy and set
  `web.public-url`.
- The page loads Phosphor icons from unpkg without a Subresource Integrity attribute (if unpkg is
  unreachable the icons are hidden and all buttons keep their text labels). To generate
  one: `curl -s https://unpkg.com/@phosphor-icons/web@2.1.1/src/bold/style.css | openssl dgst -sha384 -binary | openssl base64 -A`
  and add `integrity="sha384-<output>"` to the `<link>` in `web/index.html`.
- The Docs tab serves `src/main/resources/web/docs.md`, which is a copy of this file; after editing
  DOCS.md run `cp DOCS.md src/main/resources/web/docs.md`.

### Settings GUI

`/oa settings` (permission `omniarena.admin.reload`) opens a chest GUI for players: a category grid,
then paginated settings. Booleans toggle on click, numbers adjust (left +1, right -1, shift +/-10),
text and list values open a chat prompt. The console gets a text list.
`/oa settings set <key> <value>` works for scripting (booleans accept true/yes/on; lists accept
comma or newline separated values).

The same schema drives the GUI, the console list, and the web Config tab: exactly the keys present
in the live `config.yml` and `messages.yml` (no padding and no invented entries; every shipped
`config.yml` key is read by some code, section 18).

---

## 9. Combat (PvP) system

Server-wide, independent of arena matches (players inside an arena are handled by the match
engine; entering an arena clears the open-world combat tag). Master switch `pvp.enabled`. The old
`/pmr` prefix is now `/oa` (`/oa pmr` remains for admin actions).

- **Combat tag.** Hits tag the victim (`pvp.combat.tag-on-damage`) and the attacker
  (`tag-on-attack`) for `pvp.combat.tag-duration` seconds (default 15). Kill credit also covers
  pets, TNT, potion clouds and **end crystals** (the player who last hit a crystal is credited when
  it explodes, for 10 seconds).
- **Restrictions while tagged:** drop flight (`disable-fly`), force survival (`disable-gamemode`),
  strip invisibility, undisguise via LibsDisguises, block commands except the `allowed-commands`
  list (`block-commands`; matched by exact command label, namespaced forms such as `/minecraft:tp`
  are judged by their bare label and aliases are resolved), block placement (`block-block-place`),
  non-plugin teleports by command (`block-teleport`, default on; the older key `cancel-teleport` is
  still honoured when only it is set), ender pearl teleports (`block-ender-pearl`, default off;
  older key `block-enderpearls`) and elytra gliding (`block-elytra`, default on). Teleports caused
  by plugins (including OmniArena's own) are never blocked. Blocking the *firework boost* of an
  elytra needs Paper's `PlayerElytraBoostEvent`, so it works only on the paper/purpur jars; gliding
  itself is blocked on every platform.
- **Combat-log** (`punish-combat-log`, `combat-log-action` KILL | COMMAND | NOTHING,
  `combat-log-commands` with `{player}`, `broadcast-combat-log`, `drop-on-combat-log`: when false
  the combat-logger's inventory does not drop). Kicks and server shutdown are not punished.
- **PvP toggle:** per player (`/oa pvp`, cooldown `pvp.toggle.cooldown` seconds, can be disabled
  with `pvp.toggle.enabled`), default `pvp.toggle.default-state`, per world (`pvp.disabled-worlds`,
  `/oa pmr worlds <world>` toggles one), global (`/oa pvpglobal`, `pvp.global.pvp-enabled`). Players
  with PvP switched off are remembered across restarts in `pvp-toggles.yml`.
- **Newbie protection** (`pvp.newbie.enabled`, `duration` seconds, `disable-on-attack`,
  `block-pickup`) granted on a player's first join and persisted in `newbie.yml`; and
  **respawn protection** (`pvp.respawn-protection.*`).
- **Anti kill-abuse** (`pvp.killabuse.enabled`, `max-kills` on the same victim within
  `time-window` seconds); kill rewards are suppressed for abusive kills.
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

Every key under `pvp` in `config.yml` is read by the code.

---

## 10. Ratings and queue

### ELO (`rating/EloRating`, `EloManager`)

Standard ELO on a 400-point scale: expected = 1 / (1 + 10^((opp - own) / 400)), K = 32 (constant),
default rating 1000, floor 100, tracks wins, losses, draws and highest rating. Persisted to
`elo.yml`. Ratings update for **ranked matches** only (wins, losses, forfeits, and draws/timeouts).
Both players' new ratings are computed from the pre-match ratings. In ranked team matches (queued
parties) every player is rated against the average rating of the opposing side, for wins, draws and
forfeits. `/oa elo [player]` (permission `omniarena.use`) shows rating, rank, highest rating,
wins/losses/draws and win rate from a read-only lookup (it never creates a rating entry; a player
without ranked matches is shown as unranked at 1000); `/oa elo top [n]` (1-25, default 10) shows the
leaderboard. Ratings are also visible in `/oa queue status` (while queued), in the `elo` leaderboard
hologram (section 7) and in the web Players tab. The `elo.*` config keys described in older
documentation do not exist.

### Queue (`queue/QueueManager`, `/oa queue`)

- `/oa queue join [kit]` (permission `omniarena.queue`, default true): join the ranked queue. The
  queue name is `ranked` by default or the name of an admin kit (the match then uses that kit's
  loadout). `/oa queue leave` and `/oa queue status` (queue, position, waiting time, your rating and
  the rating range currently accepted). Refused while in an arena, while already queued, during a
  lockdown, for an unknown kit name, and while the plugin is stopped. A player must leave the queue
  before joining an arena by command or sign.
- **Parties.** When the leader of a party of 2 or more queues, the whole party is queued as one
  unit and is rated by its average rating; other party members cannot queue alone (they get a
  message) and everyone must be online and free. A party is matched against another party of the
  same size, or, when none is waiting, against enough solo players to fill the other team. Party
  matches need an arena with `team-enabled` true, `team-count` 2 and `max-players` of at least twice
  the party size; each side becomes one team. If a member leaves, quits, enters an arena or the
  party changes (leave, kick, disband, join), the whole party entry is removed and the others are
  told.
- A matchmaking task runs every 20 ticks (1 s) while the plugin runs (`/oa stop` cancels it,
  `/oa start` restarts it); disconnected players and players who entered an arena are dropped each
  tick.
- Pairing: entries in a queue are sorted by (average) rating; an entry is paired with the next entry
  of the same size whose rating gap is within the allowed range. The allowed range starts at
  **200** and grows by **50 for every 10 seconds** of waiting (the longer waiter of the two
  counts), capped at **1000**.
- A free arena is any enabled, WAITING, joinable arena with no running match that is not reserved by
  an in-progress tournament and has `max-players` of at least 2 (twice the party size for parties).
  If none is free nobody is paired in that tick and everyone stays queued. Matchmaking does nothing
  during a lockdown.
- The match is created by joining everyone; it is flagged **ranked**, remembers its queue name,
  and, if the queue key names an admin kit, `queueKit` is set. Ranked matches are closed to outsiders
  once underway. If joining fails everyone is re-queued with their original wait time. If a ranked
  match collapses before it starts, the remaining players are put back into the queue by
  `MatchManager` (a party re-queues through its leader).
- Joins done by the queue or a tournament launch drop the player's queue entry.

---

## 11. Party

`/party` (also `/oa party`, permission `omniarena.use`): `create`, `disband` (leader),
`invite <player>`, `accept`, `decline`, `leave`, `kick <player>` (leader), `leader <player>`
(transfer leadership), `info`. Invites expire after 60 seconds (cleanup task every 30 s) and are
tied to the party, so they survive a leadership change; a party holds at most `party.max-size`
members (default 10, minimum 2). Members are notified of joins and leaves. A quitting player is
removed from their party; when the leader leaves, leadership passes to the longest-standing online
member and the party disbands only if nobody else is online. Parties are used by the ranked queue
(section 10): the leader queues the whole party. They are otherwise not consumed by matches.

---

## 12. Betting

Requires Vault and an economy provider present when the plugin enables (checked once at startup),
`betting.enabled` and `integrations.vault`.

- `/oa bet place <arena> <player> <amount>` (also `/bet`): the arena must have a live match that is
  not ENDING/RESETTING, and the target must be online and a participant (not a spectator) of that
  arena. Non-OP players need the `omniarena.bet` node (declared in `plugin.yml`, default true).
- `/oa bet cancel`: refund your bet while the betting window is open.
- `/oa bet info [arena]`: your active bet and, with an arena, its bet count and pool.

Rules: **one active bet per player at a time** (cancel it before placing another); amount between `betting.min-bet` (default 100) and `betting.max-bet`
(default 10000) and within the player's balance; money is withdrawn on placement. The window is
open before the match starts and for `betting.bet-window-seconds` (default 30) after it starts;
afterwards you cannot place or cancel. With `betting.allow-spectator-bet: false` spectators of the
arena cannot place bets.

Settlement when a match ends: winner pool = bets on the winner. If there is no winner (draw,
timeout, forfeit without winner id) or nobody backed the winner, **every bet is refunded**.
Otherwise each winning bet receives `totalPool x (yourBet / winnerPool)` rounded down to cents
(paid even to offline players); losing bets are lost. Bets still pending at match cleanup (aborted match) are refunded;
all bets are refunded on `/oa stop` and on plugin disable.

---

## 13. Tournaments

Single elimination on one arena (one live match at a time). Command `/oa tournament` (also the
standalone `/tournament`).

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
(`TournamentManager` task, every 100 ticks) relaunches matches that had to wait (arena busy or still
regenerating, a player busy elsewhere) or that vanished without a result. If a player is offline, the opponent
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

Soft dependencies (auto-detected on enable and again on `/oa reload`): Vault (economy), PlaceholderAPI,
WorldGuard, LuckPerms. Each can be switched off with `integrations.vault`, `integrations.placeholderapi`,
`integrations.worldguard`, `integrations.luckperms` (default true). Vault is used for betting,
tournament prizes and `rewards.*` money; the LuckPerms hook object exists but no shipped feature
uses it. `plugin.yml` additionally lists WorldEdit, FastAsyncWorldEdit, PermissionsEx, EssentialsX,
DiscordSRV and HolographicDisplays for load order only. LibsDisguises is reached by reflection (soft
dependency) only for the combat undisguise.

### Placeholders `%omniarena_<name>%`

Lifetime (work anywhere): `kills`, `deaths`, `wins`, `losses`, `kd`, `winrate` (suffix `%`),
`time_played`.
Context: `in_arena` (`true`/`false`), `arena` / `arena_name`, `gamemode`, `gamemode_name`,
`gamemode_desc`, `goal` (the arena's effective goal type), `state`, `arena_kills`, `arena_deaths`,
`arena_streak`, `alive`, `players`, `max_players`, `time_left` (`m:ss`), `time_left_seconds`, `team`,
`team_color`.

Outside an arena: `arena` / `arena_name` return `N/A`; `gamemode`, `gamemode_name`, `gamemode_desc`,
`state`, `team`, `team_color` return an empty string; `arena_kills`, `arena_deaths`, `alive`,
`players`, `max_players`, `goal`, `time_left`, `time_left_seconds` return `0`; `arena_streak` and
unknown names return `null`. For an offline player every placeholder returns an empty string.

### TAB

Set `integrations.tab.takeover: true` and `/oa reload`. OmniArena then does not create its own
sidebar and boss bar; build them from the placeholders above in TAB's config.

---

## 15. Command reference

Run `/oa <subcommand> help` for usage of any subcommand (automatic, except `regen`, `kit` and
`adminkit`, which print their own help) and `/oa help [guide]` for guides (`makingarena`, `regenarena`,
`regenstyles`, `signjoin`, `kits`, `startmatch`, `stopmatch`, `settings`, `permissions`,
`troubleshooting`, `performance`). Tab completion exists for every subcommand and argument.

### Commands registered in `plugin.yml`

`/oa` (aliases `/omniarena`, `/arena`; `omniarena.use`), `/kit` (`omniarena.kit.use`), `/join`,
`/leave`, `/party`, `/stats`, `/list`, `/info`, `/ready`, `/help` (all `omniarena.use`),
`/spectate` (`omniarena.spectate`), `/bet` (`omniarena.bet`), `/tournament` (`omniarena.use`;
the command itself checks the tournament nodes), `/pvp`, `/pvpstatus`, `/newbie` (`omniarena.pvp`),
`/pvptag`, `/untag`, `/pvpglobal` (`omniarena.pvp.admin`). Standalone commands forward to the
matching `/oa` subcommand (`/kit`, `/join`, `/leave` and `/party` have their own executors). There
is no standalone `/queue`: use `/oa queue`.

### Dispatcher rules

- `/oa` requires `omniarena.use` to be run at all.
- While stopped, only `/oa start` runs.
- Admin subcommands require `PermissionChecker.hasAdmin`: console, OP, `omniarena.admin`, or the
  specific node. Player subcommands are checked against the subcommand's own required node for
  non-OP players (table below); the console and OPs always pass.
- The name `kit` exists in both maps. Admins get the admin command for
  `/oa kit admin <action> ...` and, for compatibility, for `setpublic`, `setprivate`, `icon`,
  `description`, `cooldown`, `permission`; everything else is the player command.
  `/oa adminkit <action> ...` is the same admin command.
- Unexpected exceptions in a subcommand are logged with a full stack trace and the player sees a
  short message with the usage.

### Player subcommands

| Subcommand | Permission |
|------------|-----------|
| `join <arena>`, `leave`, `list`, `info <arena>`, `stats ...`, `elo [player\|top [n]]`, `ready`, `party ...`, `help [guide]` | `omniarena.use` |
| `spectate <arena>` | `omniarena.spectate` |
| `kit ...` | `omniarena.kit.use` |
| `queue <join [kit]\|leave\|status>` | `omniarena.queue` |
| `bet <place\|cancel\|info>` | `omniarena.bet` |
| `tournament <create\|join\|leave\|start\|status\|list\|delete\|setprize>` | `omniarena.tournament.create` (admin actions check `omniarena.tournament.admin`) |
| `pvp [player]`, `pvpstatus [player]`, `newbie [disable]`, `pvpinfo [player]` | `omniarena.pvp` |
| `pvptag`, `untag`, `pvpglobal`, `pvplist`, `pvpoverride`, `newbie add\|remove\|checktime` | `omniarena.pvp.admin` |

### Admin subcommands

| Subcommand | Permission |
|------------|-----------|
| `create`, `delete`, `enable`, `disable`, `config`, `spawn`, `region`, `goal`, `module`, `regen`, `chat`, `announcements`, `tp`, `debug`, `wand` | `omniarena.admin.arena` |
| `access` | `omniarena.admin.access` |
| `match`, `force`, `forcewin` | `omniarena.admin.match` |
| `sign` | `omniarena.admin.sign` |
| `hologram` | `omniarena.admin.hologram` |
| `kit admin ...`, `adminkit ...` (and the metadata shortcuts) | `omniarena.admin.kit` |
| `override` | `omniarena.admin.override` |
| `reload`, `version`, `settings` | `omniarena.admin.reload` |
| `website`, `start`, `stop`, `restart`, `pmr` | `omniarena.admin` |

`/oa stats reset <player>` additionally needs `omniarena.admin.stats`.

### `/oa stats`

`/oa stats [player]` (unknown players are reported), `/oa stats global [kills|wins|kd]` (top 10),
`/oa stats reset <player>` (online or known offline player; needs `omniarena.admin.stats`).

### `/oa match <start|stop|reset> <arena>` (`omniarena.admin.match`)

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

Declared in `plugin.yml`:

| Node | Default | Used by |
|------|---------|---------|
| `omniarena.use` | true | `/oa`, join/leave/list/info/stats/ready/help/party, `/tournament` |
| `omniarena.kit.use` | true | `/kit` |
| `omniarena.queue` | true | `/oa queue` |
| `omniarena.bet` | true | `/oa bet`, `/bet` |
| `omniarena.spectate` | true | `/spectate` |
| `omniarena.pvp` | true | player PvP commands |
| `omniarena.tournament.create` | true | create, join, leave, start own |
| `omniarena.kit.slots.5`, `.10` | false | personal kit limits |
| `omniarena.admin` | op | parent of the nodes below it in this table; lifecycle, website, pmr |
| `omniarena.admin.arena` | op | arena, region, spawn, config, regen, wand, modules, chat, goal |
| `omniarena.admin.kit` | op | preset kit management |
| `omniarena.admin.match` | op | `/oa match`, `force`, `forcewin` |
| `omniarena.admin.sign` | op | `/oa sign`, creating and breaking arena signs |
| `omniarena.admin.hologram` | op | `/oa hologram` |
| `omniarena.admin.stats` | op | `/oa stats reset` |
| `omniarena.admin.reload` | op | reload, version, settings |
| `omniarena.admin.override` | op | `/oa override` |
| `omniarena.pvp.admin` | op | tag, untag, global, override, targeting others |
| `omniarena.tournament.admin` | op | start/delete/setprize any tournament |
| `omniarena.admin.access` | op | `/oa access` (arena whitelist/blacklist) |
| `omniarena.join.bypass` | op | skips the arena whitelist and the full-arena check (joins, signs, queue) |

`omniarena.admin` has as children: `.admin.arena`, `.admin.kit`, `.admin.match`, `.admin.sign`,
`.admin.hologram`, `.admin.stats`, `.admin.reload`, `.admin.override`, `.admin.access`, `omniarena.pvp.admin`,
`omniarena.tournament.admin` and `omniarena.join.bypass`.

Per-kit permission nodes (preset kit `permission` field) are free-form.

---

## 17. Building and the jar matrix

Maven 3.9+. Every build needs **one platform profile and one Minecraft-version profile**; always
pass both, for example:

```bash
mvn -q -Ppaper,mc-26.2 package        # target/OmniArena-paper-26.2.jar
mvn -q -Pspigot,mc-1.21.11 package    # target/OmniArena-spigot-1.21.11.jar
mvn -q test                           # JUnit 5 only (default profiles)
```

(`paper` and `mc-26.2` are active by default, but passing any `-P` switches the default profiles
off, so a lone `-Pmc-1.21.11` would have no server API on the classpath.)

| Platform profile | Server API | Source folder added | Adventure |
|------------------|------------|---------------------|-----------|
| `paper` | `paper-api` | `src/paper/java` | native |
| `purpur` | `purpur-api` (repo.purpurmc.org) | `src/paper/java` | native |
| `spigot` | `spigot-api` | `src/spigot/java` | shaded and relocated (`net.kyori` -> `com.omniarena.libs.kyori`) plus `adventure-platform-bukkit` |
| `bukkit` | `spigot-api` (the only published Bukkit-flavoured API) | `src/spigot/java` | same as `spigot`; only the jar name differs |

| Version profile | Java | `api-version` in `plugin.yml` | Paper API | Purpur API | Spigot API |
|-----------------|:----:|-------------------------------|-----------|------------|------------|
| `mc-26.3` | 25 | 26.3 | 26.3.build.253-stable | 26.3-R0.1-SNAPSHOT | 26.3-R0.1-SNAPSHOT |
| `mc-26.2` (default) | 25 | 26.2 | 26.2.build.135-stable | 26.2-R0.1-SNAPSHOT | 26.2-R0.1-SNAPSHOT |
| `mc-26.1` | 25 | 26.1 | 26.1.2.build.74-stable | 26.1.2-R0.1-SNAPSHOT | 26.1-R0.1-SNAPSHOT |
| `mc-1.21.11` | 21 | 1.21 | 1.21.11-R0.1-SNAPSHOT | 1.21.11-R0.1-SNAPSHOT | 1.21.11-R0.1-SNAPSHOT |
| `mc-1.21.10` | 21 | 1.21 | 1.21.10-R0.1-SNAPSHOT | 1.21.10-R0.1-SNAPSHOT | 1.21.10-R0.1-SNAPSHOT |
| `mc-1.21.9` | 21 | 1.21 | 1.21.9-R0.1-SNAPSHOT | 1.21.9-R0.1-SNAPSHOT | 1.21.9-R0.1-SNAPSHOT |

The output is `target/OmniArena-<platform>-<mcversion>.jar`: 4 platforms x 6 versions = **24 jars**.
`plugin.yml` is resource-filtered (`${project.version}`, `${api.version}`). The 26.x profiles
compile with `--release 25` and need a JDK 25; the 1.21.x profiles compile with `--release 21`
and need a JDK 21.

### Building every jar

`scripts/build-all.sh` loops over the matrix, runs `mvn -P<platform>,mc-<version> clean package`
and copies each jar to `dist/` (git-ignored):

```bash
JAVA25_HOME=/path/to/jdk25 JAVA21_HOME=/path/to/jdk21 scripts/build-all.sh
PLATFORMS="paper spigot" VERSIONS="26.2 1.21.11" scripts/build-all.sh     # a subset
```

`JAVA25_HOME` is used for 26.x and `JAVA21_HOME` for 1.21.x (without them the `java` on the PATH is
used, so only matching versions will compile). It prints the failed combinations and exits
non-zero. The Spigot snapshot repository (hub.spigotmc.org) and repo.purpurmc.org must be
reachable.

**Verification status.** Compilation was verified for all 18 paper, spigot and bukkit combinations
(6 Minecraft versions each; the 26.2 Paper profile also ran the unit tests, and spigot/1.21.9 and
paper/1.21.11 were compiled on Java 21). The 6 Purpur jars could not be verified because
repo.purpurmc.org was unreachable where this was checked, so the `purpur-api` coordinates remain
unconfirmed. Behaviour on live servers of each type is not covered by automated tests.

### Platform layer (`com.omniarena.platform`)

Common code talks only to `PlatformAdapter` (obtained from `Platform.get()`); exactly one
`PlatformImpl` is compiled per jar. `Platform.init` runs first in `onEnable` and disables the
plugin with an actionable message when a paper/purpur jar is installed on a non-Paper server (use
the spigot or bukkit jar there). The adapter covers text delivery (messages, titles, action bar,
boss bars), item names/lore, inventory titles, sign lines, `TextDisplay` text, chat (Paper
`AsyncChatEvent` vs Spigot `AsyncPlayerChatEvent`), kick detection, server-stopping detection,
async chunk loading and chunk tickets, `BlockState` access and item (container) serialization. The
spigot/bukkit jars also run on Paper, just without the Paper fast paths. Paper-only extras: the
elytra firework-boost block while combat-tagged (`PaperCombatListener`) and the native NBT item
bytes used in regen snapshots (section 5).

### Shading

FastBoard and HikariCP are relocated under `com.omniarena.shaded`; Gson and the MySQL connector
are bundled (the database layer is unused). On spigot/bukkit, Adventure (api, MiniMessage, legacy
and plain serializers) and `adventure-platform-bukkit` are bundled and relocated to
`com.omniarena.libs.kyori`. Other dependencies (provided): VaultAPI 1.7, PlaceholderAPI 2.11.6,
WorldGuard 7.0.12, LuckPerms API 5.4. The old Windows-specific `maven-antrun-plugin` copy step is
gone; `target/` is not tracked by git.

Tests (12 `@Test` methods): `RegenAnimationTypeTest` (5), `TournamentBracketTest` (6),
`ConfigSchemaServiceTest` (1).

---

## 18. Configuration reference

Every key of the bundled `config.yml`, grouped. All keys are read by code. Defaults are the values shipped in `config.yml`.

| Key | Default | Meaning |
|-----|---------|---------|
| `plugin.debug` | false | Verbose `[debug]` console logging: matchmaking decisions (pairings, no-opponent, no free arena), regen start/finish with timings, web request path and duration (never the token), wave respawns and refused joins |
| `tools.wand-material` | WOODEN_HOE | Selection wand item |
| `arena.default-min-players` | 2 | Seeds new arenas (section 2) |
| `arena.default-max-players` | 16 | Seeds new arenas |
| `arena.default-countdown` | 10 | Seeds new arenas |
| `arena.default-match-duration` | 600 | Seeds new arenas (seconds) |
| `arena.default-game-mode` | DEATHMATCH | Seeds new arenas |
| `arena.lobby-timeout` | 120 | Seconds below the minimum players before the lobby closes (0 = never) |
| `arena.spectator-allowed` | true | Seeds new arenas |
| `arena.mid-match-join` | false | Seeds new arenas |
| `arena.respawn-delay` | 3 | Seeds new arenas (seconds) |
| `arena.spawn-protection` | 3 | Seeds new arenas (seconds) |
| `arena.inventory-restore` | true | Save and restore inventory and armor |
| `arena.persist-player-state` | true | Also write each player's pre-arena state to `player-states/<uuid>.yml` |
| `arena.region-protection` | true | Block break/place in an arena region while it is not RUNNING and the rule is off (admins bypass) |
| `match.end-commands` | [] | Global end commands |
| `match.end-delay` | 5 | Seconds before reset after the end |
| `match.announce-global` | false | Broadcast results server-wide |
| `match.announce-arena` | true | Announce results to the arena (also needs the arena's `announcements`) |
| `regen.auto` | true | Rebuild arenas automatically after played matches; per arena `regen.arenas.<name>.auto` (not in the shipped file) overrides |
| `regen.batch-size` | 4000 | Blocks per tick (section 5) |
| `regen.progress.enabled` | true | Chat progress for command-driven restores |
| `regen.progress.interval-percent` | 10 | Progress step |
| `regen.animation.type` | SPHERE | Default style (10 styles, old names mapped) |
| `regen.animation.particles` | true | |
| `regen.animation.sound` | true | |
| `rewards.enabled` | false | Master switch (money needs Vault) |
| `rewards.kill-money` | 10.0 | Money per kill |
| `rewards.win-money` | 50.0 | Money per winner |
| `rewards.kill-xp` | 5 | XP per kill |
| `rewards.win-xp` | 20 | XP per winner |
| `rewards.win-commands` | [] | Console commands per winner (`{player}`, `{arena}`) |
| `rewards.kill-commands` | [] | Console commands per kill (`{player}`, `{victim}`, `{arena}`) |
| `kits.player-kits-enabled` | true | Allow `/kit create` |
| `kits.default-slot-limit` | 3 | Personal kit slots |
| `kits.power-cap.enabled` | true | Cap enchant levels and ban items in saved kits |
| `kits.power-cap.max-enchant-level` | 5 | |
| `kits.power-cap.banned-items` | BEDROCK, COMMAND_BLOCK, BARRIER, SPAWNER, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK, STRUCTURE_BLOCK | Refused while the power cap is enabled |
| `kits.sharing-enabled` | true | `/kit share` |
| `party.max-size` | 10 | Maximum party members (minimum 2) |
| `signs.glow-effect` | true | Glowstone behind wall signs |
| `signs.update-interval` | 20 | Ticks between sign refreshes |
| `holograms.refresh-seconds` | 30 | Leaderboard hologram refresh |
| `visual.titles` / `sounds` / `particles` / `scoreboard` / `boss-bar` / `action-bar` / `holograms` | true | Global switch for each channel and seed for the per-arena flags of new arenas; a channel is shown only when both the global key and the arena flag are on |
| `visual.killstreak-titles` | true | Killstreak titles |
| `visual.kill-announcements` | true | Kill announcements |
| `visual.victory-fireworks` | true | Fireworks for the winner |
| `visual.countdown-sounds` | true | Countdown sounds |
| `visual.spectator-invisibility` | true | Spectators are invisible |
| `killstreak.milestones` | 5 KILLING SPREE, 10 DOMINATING, 15 UNSTOPPABLE, 20 GODLIKE | Map of kills to title |
| `killstreak.sound` | ENTITY_LIGHTNING_BOLT_THUNDER | Milestone sound |
| `killstreak.particle` | FLAME | Milestone particle |
| `scoreboard.title` | gradient "OmniArena" | MiniMessage sidebar title |
| `scoreboard.update-interval` | 20 | Ticks (minimum effective 20) |
| `boss-bar.timer-color` | GREEN | |
| `boss-bar.waiting-color` | YELLOW | Waiting/starting |
| `boss-bar.ending-color` | PURPLE | |
| `boss-bar.low-time-color` | RED | Below the threshold |
| `boss-bar.low-time-threshold` | 60 | Seconds |
| `integrations.vault` / `placeholderapi` / `worldguard` / `luckperms` | true | Per-hook switch (section 14) |
| `integrations.tab.takeover` | false | TAB renders sidebar and boss bar |
| `web.enabled` | true | |
| `web.bind-address` | 127.0.0.1 | |
| `web.port` | 8123 | |
| `web.public-url` | "" | Base URL for generated links |
| `web.session-minutes` | 30 | Sliding session lifetime (read at plugin enable) |
| `web.max-sessions` | 1 | 0 = unlimited (read at plugin enable) |
| `web.title` | OmniArena Control | Browser tab title |
| `betting.enabled` | true | |
| `betting.min-bet` / `max-bet` | 100.0 / 10000.0 | |
| `betting.allow-spectator-bet` | true | Spectators may bet |
| `betting.bet-window-seconds` | 30 | Window after match start |
| `tournament.walkover-grace-seconds` | 60 | Wait for an offline player before a walkover |

`pvp.*`:

| Key | Default | Meaning |
|-----|---------|---------|
| `pvp.enabled` | true | Master switch |
| `pvp.combat.tag-duration` | 15 | Seconds in combat |
| `pvp.combat.tag-on-damage` / `tag-on-attack` | true | Tag the victim / the attacker |
| `pvp.combat.block-teleport` | true | Block non-plugin command teleports while tagged (legacy `cancel-teleport`) |
| `pvp.combat.block-elytra` | true | Block elytra gliding (and, on Paper, firework boost) while tagged |
| `pvp.combat.block-ender-pearl` | false | Block ender pearl teleports (legacy `block-enderpearls`) |
| `pvp.combat.block-commands` | true | Block commands except `allowed-commands` |
| `pvp.combat.allowed-commands` | /oa, /pvp, /pvpstatus, /pvptag, /msg, /r, /helpop | |
| `pvp.combat.block-block-place` | true | |
| `pvp.combat.disable-fly` / `disable-gamemode` / `disable-invisibility` / `disable-disguise` | true | Applied when the tag starts |
| `pvp.combat.punish-combat-log` | true | |
| `pvp.combat.combat-log-action` | KILL | KILL, COMMAND or NOTHING |
| `pvp.combat.combat-log-commands` | [] | For COMMAND (`{player}`) |
| `pvp.combat.drop-on-combat-log` | true | Combat-logger's inventory drops |
| `pvp.combat.broadcast-combat-log` | true | |
| `pvp.toggle.enabled` | true | Players may toggle their own PvP |
| `pvp.toggle.cooldown` | 5 | Seconds between toggles |
| `pvp.toggle.default-state` | true | PvP on by default |
| `pvp.global.pvp-enabled` | true | Server-wide switch |
| `pvp.newbie.enabled` | true | |
| `pvp.newbie.duration` | 300 | Seconds |
| `pvp.newbie.disable-on-attack` | true | |
| `pvp.newbie.block-pickup` | false | Newbies cannot pick up items |
| `pvp.killabuse.enabled` | true | |
| `pvp.killabuse.max-kills` | 3 | Kills on one victim before they stop counting |
| `pvp.killabuse.time-window` | 300 | Seconds |
| `pvp.respawn-protection.enabled` | true | |
| `pvp.respawn-protection.duration` | 5 | Seconds |
| `pvp.border-hopping.prevent` | true | Needs WorldGuard |
| `pvp.auto-soup.enabled` | false | |
| `pvp.auto-soup.heal-amount` | 3.5 | |
| `pvp.auto-soup.soup-material` | MUSHROOM_STEW | |
| `pvp.blood.enabled` | true | |
| `pvp.blood.block` | REDSTONE_BLOCK | |
| `pvp.transfer-drops` | false | Victim's drops go to the killer |
| `pvp.block-lava-fire-near-disabled` | true | |
| `pvp.lava-fire-radius` | 5.0 | Blocks |
| `pvp.disabled-worlds` | [] | Worlds with PvP off |

`messages.yml`: every player-facing string, MiniMessage format, with a `prefix` key prepended by
`ConfigManager.getMessage`. Missing keys fall back to the defaults bundled in the jar (so upgrades
do not show "Missing message"). Tournament messages: `tournament-usage`, `-no-permission`,
`-not-found`, `-none`, `-list-header`, `-list-entry`, `-prize-set`, `-prize-invalid`,
`-players-only`. Placeholders inside messages use `{name}` style.

Changing config: edit the file and `/oa reload`; or `/oa config set`, `/oa settings`, or the web
Config tab (all write the live config and save). Reload also reloads kits and the integration
hooks, and reloads arenas only when no match is running. Keys removed from older versions
(`plugin.language`, `plugin.metrics`, `database.*`, `arena.teleport-back-on-leave`, `anticheat.*`,
`performance.*`) no longer exist; a stale copy in your file is ignored.

---

## 19. Storage

All under `plugins/OmniArena/`:

- `arenas/<name>.yml`: `name`, `enabled`, `game-mode`, `min-players`, `max-players`,
  `match-duration`, `countdown`, `chat-mode`, `announcements`, `mid-match-join`, `spectator-allowed`,
  `spawn-protection`, `goal.type|value` (plus `goal.type-override: true` when the goal type differs
  from the game type's), `respawn.type|delay|wave-interval`, `lives`, `rules.*`, `team.enabled|count`,
  `modules.enabled`, `access.whitelist-enabled|whitelist|blacklist` (UUID lists), `region.world|min-x..max-z`, `spawns.battle|lobby|spectator` (each a list
  of `world:x:y:z:yaw:pitch`), `signs` (same format), `end-commands`, `visual.*`. The bundled
  `example.yml` also shows `team.colors` and `classes`, which the loader does not read.
- `snapshots/<arena-lowercase>.yml`: **format v2** (written atomically):
  ```yaml
  world: world
  format: 2
  palette:            # index = position; value = MATERIAL|blockdata-string
    - STONE|minecraft:stone
    - AIR|minecraft:air
  runs:               # "x,z,y0,length,paletteIndex" vertical run-length columns
    - "100,200,64,5,0"
  tiles:              # optional: tile entities, "x,y,z|C|<b64 items>", "x,y,z|S|<b64 front>|<b64 back>|<style>", "x,y,z|B|<banner patterns>"
    - "100,64,200|C|H4sIAAAA..."
  ```
  The legacy format 1 (`x,y,z|MATERIAL|blockdata` lines) is still loaded. Container item bytes are
  platform specific (section 5).
- `sign-glowstone.yml`: `locations: ["world,x,y,z"]`.
- `holograms.yml`: `holograms.<id>.{type,size,world,x,y,z}`.
- `admin-kits/<name>.yml`, `player-kits/<uuid>.yml` (section `kits.<name>`), `kit-cooldowns.yml`
  (preset-kit cooldown expiries per player).
- `stats.yml`: `stats.<uuid>.{name,kills,deaths,wins,losses,assists,draws,time-played}`.
- `elo.yml`: `ratings.<uuid>.{name,rating,wins,losses,draws,highestRating}`.
- `tournaments.yml`: `tournaments.<name>.{name,arena,maxPlayers,creator,state,participants,prizes.first|second|third,bracket.rounds,bracket.currentRound}`.
- `pvp-toggles.yml`: `pvp-off: [uuid, ...]` (players who switched PvP off).
- `newbie.yml`: `<uuid>: <expiry epoch millis>` (running newbie protection windows).
- `player-states/<uuid>.yml`: crash-safe copy of a player's pre-arena state (location, game mode,
  health, food, XP, effects, flight, speeds, inventory when enabled). Written asynchronously on a
  single writer thread (atomic temp file + move) when a player joins an arena or spectates; deleted
  when their state is restored; if it still exists when the player next joins the server (crash or
  kill during a match) `PlayerJoinListener` restores it and tells the player. Disabled by
  `arena.persist-player-state: false`.
- `config.yml`, `messages.yml`.

Save behaviour: arenas, kits and tournaments are written synchronously. Stats are written on an
async task when the plugin is enabled and inline during `onDisable`. ELO snapshots on the main
thread and writes on a single daemon executor (inline when the plugin is disabled), flushed with
`shutdown()` on disable. PvP toggles and newbie windows are saved on an async task (inline on
shutdown). Snapshots serialize on the main thread in slices and write async.

---

## 20. Lifecycle

### onEnable order

Platform layer (`Platform.init`; a wrong-flavor jar disables the plugin here); config load;
construct managers (visual, stats, kits, kit share, signs, modules, block restore, arenas, matches,
kit command, combat, ELO, queue, party, betting, tournaments); load data (stats, kits, arenas,
ELO); start queue, party, tournaments; integrations; web server; commands and tab completers;
listeners (including the platform's chat listener); sign update task; startup banner.
`VisualController` starts the leaderboard holograms.

### onDisable order

Set `stopped`; cancel sign task; close open kit/settings GUIs; leave all online players of active
matches (so results are included in saves); flush the player-state writer; `BlockRestoreModule.cancelAll`
(finishes regens, including ones started by the matches that just ended); shut down combat; stop
queue and party; save tournaments; refund all bets; save stats, arenas, ELO (flush ELO executor);
shut down visuals (removes holograms) and modules; stop the web server; release integrations; shut
down the platform layer. Each step is individually guarded so one failure does not skip the rest.

### `/oa stop`, `/oa start`, `/oa restart` (`omniarena.admin`)

- **stop:** sets `stopped = true`; finishes running regens; cancels **all** scheduler tasks of the
  plugin; removes all players and spectators from matches and finishes the regens that started;
  closes open GUIs; saves ELO, stats and arenas; refunds bets; saves and clears tournaments; stops
  the web server (revoking sessions); shuts down visuals and modules; stops queue and party.
  While stopped, listeners check `isStopped()` and do nothing, all commands except `/oa start`
  are refused, and `/kit` and `/party` report the stopped message.
- **start:** only when stopped. Clears `stopped`, reloads config, arenas, kits, stats, ELO; restarts
  queue, party, tournaments (restoring saved brackets), the web server, the sign task, combat
  tasks and visuals (holograms). It does not rebuild the Vault economy lookup (betting keeps its
  startup state) or listeners (they stay registered).
- **restart:** stop now, start 20 ticks later (the stop cancels all tasks, so use `/oa restart`
  rather than scheduling around it).
- **reload:** reloads config and messages and the kits; reloads arenas only if no match is active;
  reloads the killstreak module; restarts the sign task and combat tasks; re-runs the integration
  hooks (so `integrations.*` toggles and the PlaceholderAPI expansion are refreshed); refreshes all
  signs.

---

## 21. Known limitations

- **Database.** Everything is YAML; the shaded MySQL/Hikari libraries are unused.
- **Snapshots with containers are not portable** between paper/purpur and spigot/bukkit jars
  (re-take them after switching); the elytra firework-boost block while tagged exists only on
  paper/purpur.
- **Build verification.** All 18 paper, spigot and bukkit jars are compile-verified; the 6 Purpur
  jars are not (repo.purpurmc.org was unreachable; section 17). No automated runtime tests on live
  servers.
- **Wave respawn UI.** `respawn-wave-interval` is an arena file / `/oa config` key only; it is not
  in the web dashboard arena editor.
- The Docs tab serves a separate bundled copy of this documentation (`web/docs.md`, kept identical
  to this file).
