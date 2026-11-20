# OmniArena Documentation

OmniArena is a PvP arena plugin for Paper, Purpur, Spigot and Bukkit servers. Players join arenas from a menu, a
command or a sign, fight in 16 game types, climb a ranked ladder, bet on matches and play bracket tournaments.
Admins set an arena up in a few clicks, and the plugin rebuilds it after every match and repairs itself when
something goes wrong.

This file is the complete reference. The same guides are available in game as books: type `/oa guide`. For a short
overview see [README.md](README.md). Developer and AI-agent notes are in [CLAUDE.md](CLAUDE.md) and
[AGENTS.md](AGENTS.md).

Contents: 1 Overview, 2 Guides (quick start, arena setup, playing, kits, ranked, tournaments, betting,
regeneration, leaderboards, NPCs, PvP, self-healing, configuration), 3 Command reference, 4 Permissions,
5 Configuration reference, 6 Placeholders, 7 Integrations, 8 Web dashboard, 9 Event API for developers,
10 Persistence files, 11 Plugin lifecycle, 12 Building, 13 Limitations.

---

## 1. Overview

### What you get

- **Menus first.** `/oa` opens a main menu; setup and editing are button menus; every action also has a text command
  with tab completion. Mistyped commands get a did-you-mean with a clickable fix.
- **16 game types** with their own kits, goals and rules (section 2.2), optional lives, teams and respawn waves.
- **Automatic arena rebuild** after each match from a snapshot, with 10 animated styles (section 2.8).
- **Ranked matchmaking** with ELO, parties and per-kit queues (section 2.5), **tournaments** and **betting**.
- **Personal and preset kits**, sharing, cooldowns and a power cap (section 2.4).
- **Server-wide PvP management**: combat tag, newbie protection, anti kill-abuse, PvP toggle (section 2.11).
- **PlaceholderAPI placeholders** for stats and live match data, ready-made leaderboard holograms for DecentHolograms
  and FancyHolograms, Citizens NPCs, Multiverse world loading, TAB support (sections 6 and 7).
- **Self-healing**: config repair, a watchdog and `/oa doctor` (section 2.12).
- **Web dashboard** to edit settings and arenas in a browser (section 8) and an **event API** for other plugins
  (section 9).
- **In-game guide books** (`/oa guide`) generated from `guides.yml`, which you can edit.

### Requirements

- A server running Minecraft 1.21.9 or newer (up to 26.3). Pick the jar for your server type and version (section 12):
  `paper` and `purpur` jars need Paper or Purpur; `spigot` and `bukkit` jars run on Spigot, Bukkit and also Paper.
- Java 21 for 1.21.x servers, Java 25 for 26.x servers.
- Optional plugins, all detected automatically: Vault (money), PlaceholderAPI, WorldGuard, LuckPerms,
  DecentHolograms or FancyHolograms, Citizens, Multiverse-Core, TAB, LibsDisguises.

### Install

1. Put the jar into `plugins/` and restart the server.
2. Optional: install Vault plus an economy plugin for bets, prizes and money rewards.
3. Type `/oa guide start` (admins) for the five-minute setup, or follow section 2.1.

Everything lives in `plugins/OmniArena/` (section 10). Nothing needs a database; all data is YAML.

---

## 2. Guides

Each guide here is also an in-game book. `/oa guide` opens an index with clickable topics; `/oa guide <topic>` opens
one directly. Topics for admins (start, arena, regen, leaderboards, npc, doctor, config) are only listed to admins.
The text of the books is in `plugins/OmniArena/guides.yml` and can be edited; missing topics are filled in from the
jar (section 2.13).

### 2.1 Quick start: your first arena in 5 minutes

Permission: `omniarena.admin.arena` (operators have it). Command: `/oa guide start`.

1. **Create it.** Run `/oa setup duel_1` (any name of 1 to 32 letters, digits, `_` or `-`). You receive the **selection
   wand** (a wooden hoe by default) and the **setup menu** opens with a checklist. A new arena starts enabled but is
   not joinable until it has a battle spawn.
2. **Build the arena first.** The plugin takes a snapshot of the region later and rebuilds from it after every match,
   so finish the build before you save the region (or re-run `/oa regen snapshot duel_1` after changes).
3. **Select the area.** Hold the wand, **left-click** one corner (position 1) and **right-click** the opposite corner
   (position 2). Then run `/oa setup duel_1 region`. Both corners must be in the same world.
4. **Add spawns.** Stand on a pad and run `/oa setup duel_1 spawn`; repeat for every pad. Player 1 uses spawn 1, player
   2 uses spawn 2, and so on (extra players share pads).
5. **Finish.** Run `/oa setup duel_1 finish`. The plugin checks the arena and enables it.
6. **Play.** `/oa join duel_1`, or put up a sign with `/oa sign create duel_1` while looking at a placed sign.

*Tip:* `/oa doctor` finds arenas without a region, spawn or snapshot and offers fix buttons. The same steps are
buttons in `/oa menu`, then Admin, then Arenas.

### 2.2 Arena setup

Permission: `omniarena.admin.arena` (access lists: `omniarena.admin.access`). Command: `/oa guide arena`.

An arena is a named region with spawns, a game type, rules, team settings, visual toggles, chat mode and optional end
commands. It is saved to `arenas/<name>.yml`.

#### The setup wizard

`/oa setup <arena>` creates the arena when it does not exist (otherwise it continues setting it up), gives you the
wand, prints a checklist with the exact command for each missing step and opens the setup menu. The text shortcuts
never create an arena, so a typo cannot silently make a new one:

| Command | Effect |
|---------|--------|
| `/oa setup <arena> region` | Saves the wand selection as the region, clears the selection and takes the regeneration snapshot |
| `/oa setup <arena> spawn` | Adds a battle spawn at your position (numbered in order) |
| `/oa setup <arena> clearspawns` | Removes all battle spawns |
| `/oa setup <arena> lobby` | Sets the single lobby spawn (waiting room) to your position |
| `/oa setup <arena> spectator` | Sets the single spectator spawn to your position |
| `/oa setup <arena> finish` | Checks the requirements (at least one battle spawn) and enables the arena |

Without a lobby spawn the lobby is a random battle spawn; without a spectator spawn it is the lobby spawn. At match
start player *i* takes battle spawn *i*, wrapping around when there are more players than spawns. Respawns use a
random battle spawn. The region is optional but recommended: without it there is no block protection and no
regeneration.

#### The edit menu and text editing

`/oa edit <arena>` opens the edit menu: game mode, goal, lives, players, duration, countdown, respawn, chat, teams,
rules, visuals, modules and the regeneration style, as buttons (green concrete is on, red concrete is off). The same
keys work as text:

- `/oa edit <arena> set <key> <value>` and `/oa edit <arena> get <key>`; tab completion lists the keys and values.
- Keys that are true or false: `enabled`, `announcements`, `mid-match-join`, `spectator-allowed`, `whitelist-enabled`,
  `team-enabled`, rules `fall-damage`, `fire-damage`, `drowning`, `suffocation`, `friendly-fire`, `block-break`,
  `block-place`, `item-drop`, `hunger-drain`, visuals `visual-titles`, `visual-sounds`, `visual-particles`,
  `visual-scoreboard`, `visual-boss-bar`, `visual-action-bar`, and modules `module-killstreak`, `module-combo`,
  `module-power-up`.
- Whole numbers: `min-players`, `max-players`, `match-duration` (seconds), `countdown` (seconds), `goal-value`,
  `respawn-delay`, `respawn-wave-interval`, `spawn-protection`, `lives` (0 to 100, 0 = off), `team-count`, and the
  region bounds `region-min-x`, `region-min-y`, `region-min-z`, `region-max-x`, `region-max-y`, `region-max-z`.
- Names: `game-mode`, `goal-type`, `respawn-type`, `chat-mode`, `region-world`.
- `regen-auto` is `true`, `false` or `default` (follow the global `regen.auto`).

Values are validated: booleans accept true/false/yes/no/on/off, numbers must be whole and not negative, names must
match an existing value, `min-players` must not exceed `max-players`. Setting `enabled false` removes everyone from
the arena first.

#### Access lists

`/oa edit <arena> whitelist add|remove|list [player]`, `/oa edit <arena> blacklist add|remove|list [player]` and
`/oa edit <arena> set whitelist-enabled true` (permission `omniarena.admin.access`). The blacklist is always enforced;
a player blacklisted while inside is removed. The whitelist is only enforced when `whitelist-enabled` is true, and
operators or holders of `omniarena.join.bypass` skip it. Players are resolved from online players or players who
joined before. Every way into an arena (command, menu, sign, queue, tournament, NPC, API) goes through the same
checks.

#### Managing arenas

- `/oa arena tp <arena>`: teleport there (loads the arena's world first; with Multiverse-Core installed it is loaded
  through Multiverse).
- `/oa arena enable <arena>`: refuses when there is no battle spawn, warns when there is no region.
- `/oa arena disable <arena>`: removes every player and spectator first.
- `/oa arena delete <arena>`.
- `/oa list` shows all arenas; `/oa list <arena>` shows one arena's details to everybody.

#### Arena states

`DISABLED`, `WAITING`, `STARTING`, `RUNNING`, `ENDING`, `RESETTING`, `MAINTENANCE`. An arena is joinable in WAITING or
STARTING. After a played match it stays RESETTING until its automatic rebuild has finished.

#### Game types (16)

Set with the `game-mode` key. Each type has a loadout (from `GameModeKits`), a win condition, respawn behaviour and
special rules. Unknown names fall back to `DEATHMATCH`.

| Type | Display name | Win condition | Default target | Respawns | Natural regen | Special |
|------|--------------|---------------|:--------------:|:--------:|:-------------:|---------|
| `DEATHMATCH` | Deathmatch | KILLS | 15 | yes | yes | free for all |
| `TEAM_DEATHMATCH` | Team Deathmatch | KILLS | 30 | yes | yes | needs `team-enabled` |
| `LAST_ONE_STANDING` | Last One Standing | LAST_ALIVE | - | no | yes | |
| `SUMO` | Sumo | LAST_ALIVE | - | no | yes | ring-out |
| `CLASSIC_PVP` | Classic PvP | KILLS | 10 | yes | yes | |
| `CRYSTAL_PVP` | Crystal PvP | KILLS | 5 | yes | yes | |
| `MACE` | Mace | KILLS | 10 | yes | yes | |
| `SWORD_DUEL` | Sword Duel | LAST_ALIVE | - | no | yes | |
| `AXE_SHIELD` | Axe and Shield | LAST_ALIVE | - | no | yes | |
| `UHC` | UHC | LAST_ALIVE | - | no | no | no natural or food regeneration |
| `POTION_PVP` | Potion PvP | KILLS | 10 | yes | yes | |
| `NODEBUFF` | NoDebuff | KILLS | 10 | yes | yes | |
| `ARCHER` | Archer | KILLS | 12 | yes | yes | |
| `NETHERITE_OP` | Netherite OP | KILLS | 20 | yes | yes | |
| `SPLEEF` | Spleef | LAST_ALIVE | - | no | yes | ring-out |
| `GLADIATOR` | Gladiator | KILLS | 5 | yes | yes | |

One-life modes drop a dead player into spectator mode (eliminated). **Ring-out** (Sumo, Spleef): falling below the
platform counts as a death. Teams exist only when the arena's `team-enabled` flag is on (`team-count` is 2 by
default); choosing Team Deathmatch does not turn it on. In team arenas a KILLS goal ends when a team reaches the
target and a LAST_ALIVE goal ends when one team is left; without teams the individual leader decides. When a match was
created by the ranked queue with a named preset kit, that kit replaces the game type's loadout (also for respawns).

**Goal.** The win condition is the arena's goal type: by default the game type's (`KILLS` or `LAST_ALIVE`), or an
override set with the `goal-type` key. `OBJECTIVE` and `TIME_LIMIT` have no end rule of their own (the match then ends
by its timer or when one side is left). `goal-value` overrides the default kill target only when it is above 0; a new
arena is saved with 20, so set it to 0 to use the mode's default.

**Lives.** `lives` above 0 gives every player that many lives in any game type: each death costs one, the player
respawns while lives remain and is eliminated at 0. On KILLS goals the last player or team with lives wins. Lives show
on the scoreboard and action bar.

**Respawn types.** `INSTANT`, `DELAYED` (`respawn-delay` seconds) and `WAVE`: dead players wait in spectator mode and
return together every `respawn-wave-interval` seconds (default 10) with a countdown on the action bar.

**Chat mode.** `ARENA` (default) shows a player's chat only to the match, `TEAM` only to their team, `GLOBAL` leaves
chat alone. **Region protection:** outside RUNNING, non-admins cannot break or place blocks in an arena region
(`arenas.region-protection`); while RUNNING it follows the `block-break`/`block-place` rules. Water and lava cannot
flow out of the region during a match, and a player who leaves the region is returned to a battle spawn.

**Modules** (`module-killstreak`, `module-combo`, `module-power-up`): killstreak announcements, combo counters and
power-ups. Killstreak and combo are on by default.

**End commands.** Per-arena `end-commands` in the arena file, otherwise `matches.end-commands`. Placeholders:
`{winner}`, `{loser}`, `{arena}`, `{mode}`, `{duration}` (seconds) and `{winner_kills}`. Commands run as the console;
prefix `player:` runs one as the winner. No leading slash.

#### Join signs

Place a sign, write `[OmniArena]` (or `[oa]`) on line 1 and the arena name on line 2 (permission
`omniarena.admin.sign`). Or place any sign, look at it (within 5 blocks) and run `/oa sign create <arena>`. Also
`/oa sign remove` (look at the sign) and `/oa sign list <arena>`. The sign shows the arena name, a status line (Click
to Join, FULL, Starting, IN PROGRESS, Resetting, DISABLED) and players over max players. Right-click joins (for a
running arena without mid-match join the player gets a clickable spectate offer). With `arenas.sign-glow` a glowstone
block is placed behind wall signs; only glowstone the plugin placed is ever removed. Registered signs are protected
against breaking by non-admins, explosions and pistons.

### 2.3 Playing

Command: `/oa guide play`. Permission: `omniarena.use` (default for everybody).

- **Open the menu:** `/oa` or `/oa menu`. It has Join, Spectate, Ranked queue, Leave, My kits, My stats, Party,
  Tournaments, Guides and (for admins) Admin.
- **Join:** `/oa join <arena>` (or `/join`). With no name you get an arena picker. You can also right-click a sign or use
  an NPC. Joining refuses when you are in an arena already, queued, blacklisted, not on an enabled whitelist, the arena
  is full or disabled, or its world is not loaded; the message says what is wrong and, for admins, the fix.
- **In the lobby:** you are at the lobby spawn in adventure mode and frozen, with your state saved (location, game mode,
  health, food, XP, effects and, if `matches.inventory-restore` is on, your inventory). The countdown starts when
  `min-players` are in; below the minimum the lobby closes after `matches.lobby-timeout` seconds.
- **Leave:** `/oa leave` (or `/leave`). Your state and location are restored.
- **Spectate:** `/oa spectate <arena>` (or `/spectate`, permission `omniarena.spectate`). Spectators are invisible and
  can fly; leave with `/oa leave`.
- **Stats:** `/oa stats [player]` shows kills, deaths, K/D, wins, losses, time played, ELO and rank.
  `/oa stats top [kills/wins/kd/elo] [n]` shows leaderboards (default kills, n up to 25).
- **Party:** `/party create`, `invite <player>`, `accept`, `decline`, `leave`, `kick <player>`, `leader <player>`,
  `disband`, `info`. Invites last 60 seconds; a party holds up to `ranked.party-max-size` members (default 10).
- **Kits, ranked, tournaments, betting, PvP:** sections 2.4 to 2.11.

#### How a match runs

1. **Join and countdown.** The countdown (`countdown`, default 10 seconds) runs while enough players are present.
2. **Start.** Teams are assigned for team arenas, everyone teleports to a numbered spawn, gets the loadout and spawn
   protection (`spawn-protection` seconds). Scoreboard, boss bar and action bar appear.
3. **Running.** At time 0 in a KILLS mode the strict leader (or team) wins; ties and last-alive modes end as a draw
   (outcome TIMEOUT). Deaths clear drops unless the arena allows item drops and keep the inventory.
4. **End.** In order: ELO (ranked only), bets, tournament hook, titles, sounds and fireworks, end commands, rewards
   (`rewards.*`), stats, announcements. A failure in one step is logged and does not strand the arena.
5. **Reset.** After `matches.end-delay` seconds remaining players are returned, then the arena is rebuilt
   (section 2.8) and reopens.

Outcomes are WIN, LOSS, DRAW, FORFEIT and TIMEOUT; DRAW and TIMEOUT count as draws. Arena fighters cannot damage
outsiders and outsiders cannot damage them. If a player disconnects or crashes during a match, their saved state
(`player-states/<uuid>.yml`) is restored when they next join.

### 2.4 Kits

Command: `/oa guide kits`. Permission `omniarena.kit.use`.

#### Personal kits (`/kit`)

- `/kit` opens the kit menu (your kits and public preset kits).
- `/kit create <name>`: saves your inventory and armor (names: letters, digits, `_`, `-`, `.`; max 32).
- `/kit list`, `/kit equip <name>`, `/kit delete <name>`, `/kit rename <old> <new>`, `/kit preview <name>`.
- `/kit share <name> <player>`: sends a copy offer; the receiver runs `/kit accept <name>` within 60 seconds.

In an arena you can only equip in the lobby phase (before the match starts); preset kits enforce their permission and
cooldown. Slots: `kits.default-slot-limit` (3), 5 with `omniarena.kit.slots.5`, 10 with `omniarena.kit.slots.10`.
With `kits.power-cap.enabled`, enchant levels above `max-enchant-level` and the `banned-items` are removed when a kit
is saved. `/kit` inside an arena is intercepted by OmniArena even when another plugin owns it.

#### Preset kits (admins, `omniarena.admin.kit`)

`/oa kits` opens the admin kit menu. Text actions: `/oa kits create <name>` (from your inventory), `delete <name>`,
`rename <old> <new>`, `equip <name>`, `list`, `setpublic <name>`, `setprivate <name>`, `icon <name> <MATERIAL>`,
`description <name> <text>`, `cooldown <name> <seconds>`, `permission <name> <node>` (`none` clears). Kits are files in
`admin-kits/`; the ten defaults (warrior, archer, tank, assassin, mage, healer, pyro, ninja, juggernaut, gunner) are
installed when missing and never overwritten. The ranked queue can use a preset kit by name (section 2.5).

### 2.5 Ranked, queue and ELO

Command: `/oa guide ranked`. Permission `omniarena.queue` (default true).

- `/oa queue [kit]` joins the ranked queue (the default queue is `ranked`; a preset kit name queues for that kit and the
  match uses its loadout). `/oa queue status` shows position, wait time and your accepted ELO range.
  `/oa queue leave` leaves. The menu has a button for it.
- A matchmaking task runs every second. Players are sorted by ELO and paired with the next entry of the same size whose
  gap is within the allowed range: **200** at first, plus **50 for every 10 seconds** of waiting (the longer waiter
  counts), up to **1000**.
- A free arena is needed: enabled, waiting, joinable, not reserved by a tournament, big enough, and for parties
  team-enabled with 2 teams. With none free everyone stays queued. A player must leave the queue before joining an
  arena by hand.
- **Parties.** The leader queues the whole party as one entry rated by its average ELO. A party meets a party of the same
  size, or enough solo players to fill the other team. If a member leaves, quits or the party changes, the whole entry
  is removed.
- **Ranked matches** are closed to outsiders once underway. Leaving a running ranked duel is a forfeit (you lose ELO);
  in team matches the last member of a side leaving is a team loss. If a ranked match collapses before it starts, the
  rest return to the queue.
- **ELO:** standard ELO on a 400 point scale, K = 32, start 1000, floor 100. Computed from the ratings before the
  match; team members are rated against the opposing average. Only ranked matches change ELO. See it with
  `/oa stats`, `/oa stats top elo`, the main menu and the placeholders `%omniarena_elo%` and `%omniarena_rank%`.

### 2.6 Tournaments

Command: `/oa guide tournament`. Command `/oa tournament` (also `/tournament`).

| Command | Who | Notes |
|---------|-----|-------|
| `/oa tournament create <name> <arena> <max>` | `omniarena.tournament.create` (default true) | Arena must exist, max at least 2, unique name |
| `/oa tournament join <name>` | players | Registration only; starts automatically when full |
| `/oa tournament leave <name>` | players | Registration only |
| `/oa tournament start <name>` | creator or `omniarena.tournament.admin` | Offline players are dropped; needs 2 online players |
| `/oa tournament status <name>`, `list` | anyone | |
| `/oa tournament delete <name>` | admin | Also `cancel` |
| `/oa tournament setprize <name> <1st/2nd/3rd> <amount>` | admin | Needs Vault to pay |

Single elimination on one arena, one live match at a time. While a tournament runs its arena is reserved. Seeding is by
join order; for a field that is not a power of two the first seeds get byes. Each launch joins the two players through
the normal join path, and a safety task (every 5 seconds) relaunches matches that had to wait for the arena to finish
rebuilding. An offline player forfeits after `tournaments.walkover-grace-seconds` (60). A match with no winner is decided
by who is still in the arena, then by health, then a coin flip. Tournament matches are not ranked. Prizes: first for the
champion, second for the finalist, third split among the semi-final losers (rounded down to cents; zero prizes are
skipped). Tournaments are saved to `tournaments.yml` and resumed after a restart. Arenas used in tournaments need
`min-players` of 2 or less.

### 2.7 Betting

Command: `/oa guide betting`. Needs Vault with an economy, `betting.enabled` and `integrations.vault`.

- `/oa bet place <arena> <player> <amount>` (also `/bet`): bet on a participant of a live match. `/oa bet cancel`
  refunds while the window is open. `/oa bet info [arena]` shows your bet and an arena's pool.
- One active bet per player. Amount between `betting.min-bet` (100) and `betting.max-bet` (10000), and within your
  balance; the money is taken at once. The window closes `betting.bet-window-seconds` (30) after the match starts.
  `betting.allow-spectator-bet: false` stops spectators from betting.
- **Payout:** winners share the whole pool in proportion to their bets (rounded down to cents, paid even to offline
  players). With no winner (draw, timeout) or nobody on the winner, **every bet is refunded**. Pending bets of an aborted
  match and all bets on `/oa plugin stop` or shutdown are refunded too.

### 2.8 Regeneration

Command: `/oa guide regen`. Permission `omniarena.admin.arena`.

After every played match the arena is rebuilt from its snapshot, with no command needed; it stays RESETTING (not
joinable) until done. A match that was abandoned in the lobby is not rebuilt.

- **Snapshot:** taken by `/oa setup <arena> region` and re-taken with `/oa regen snapshot <arena>`. It stores blocks
  (palette plus run-length columns, scanned in slices of about 30,000 blocks per tick), containers, signs and banners.
- **Manual rebuild:** `/oa regen <arena>` (refused during a match or while already regenerating).
- **Settings** (`/oa settings`, category Regeneration): `regen.auto`, `regen.style`, `regen.particles`, `regen.sound`,
  `regen.progress-messages`; advanced: `advanced.regen.batch-size` (default 4000 blocks per tick).
  Per arena: `/oa edit <arena> set regen-auto true/false/default`.
- **No snapshot or failure:** a warning is logged, online admins are told, and the arena reopens with its current
  blocks, so it never stays stuck.
- **Styles (10):**

| Style | Order | Effect |
|-------|-------|--------|
| `INSTANT` | none | batched placement, one sound |
| `SPHERE` (default) | from the centre outwards | thin particle shell |
| `RISE` | bottom to top | rising particle plane |
| `FALL` | top to bottom | sinking particle plane |
| `SWEEP` | plane across the longest axis | sweeping spark plane |
| `TIME_REWIND` | reverse of the changes made during the match | particle clock, rising pitch |
| `GALAXY` | four spiral arms | portal and end-rod particles |
| `HOLOGRAM_SCAN` | bottom to top, two passes | glass previews, then real blocks |
| `METEOR_SHOWER` | random impacts, accelerating | flame and lava trails |
| `OVERGROWTH` | flood fill from 3 to 6 seeds | spore particles |

Old style names in an existing config are mapped to these ten when read. Animated styles place at most 1200 blocks per
tick. Containers are encoded per platform, so a snapshot with containers taken on a paper/purpur jar cannot be read by a
spigot/bukkit jar (re-take it after switching). Running rebuilds finish instantly on `/oa plugin stop` and shutdown.
During a match a change log records changed blocks (up to 200,000 positions) for `TIME_REWIND`.

### 2.9 Leaderboards (placeholders and holograms)

Command: `/oa guide leaderboards`. OmniArena has **no holograms of its own**. It feeds PlaceholderAPI and can place
ready-made holograms through DecentHolograms or FancyHolograms.

- **Easy way:** stand where the board should be and run `/oa leaderboard create <id> [kills/wins/kd/elo] [size]`
  (size 1 to 10, default 10, metric default kills). `/oa leaderboard list` and `/oa leaderboard remove <id>` manage them
  (permission `omniarena.admin.leaderboard`). DecentHolograms is used when installed, otherwise FancyHolograms
  (`integrations.decentholograms`, `integrations.fancyholograms`). With PlaceholderAPI the lines are placeholders that the
  hologram plugin refreshes; without it OmniArena refreshes static lines every 60 seconds. The definitions are saved in
  `leaderboards.yml`.
- **Any plugin:** write placeholders into any hologram, scoreboard or tab list:
  `%omniarena_top_kills_1_name%` and `%omniarena_top_kills_1_value%`. Types are `kills`, `wins`, `kd`, `elo`; positions 1
  to 10. The data is cached and refreshed about every 30 seconds without loading anything on the main thread.
- A leftover `holograms.yml` from older versions is ignored and logged once.

### 2.10 NPCs

Command: `/oa guide npc`. Needs Citizens (`integrations.citizens`). Permission `omniarena.admin.npc`.

Stand where the NPC should appear and run `/oa npc create <action>`. Right-clicking the NPC runs the action:

| Action | Effect |
|--------|--------|
| `menu` | Opens the main menu |
| `join <arena>` | Joins that arena |
| `queue [kit]` | Joins the ranked queue (default `ranked`) |
| `spectate [arena]` | Spectates that arena (no name opens the picker) |

`/oa npc list` shows the NPCs OmniArena manages, `/oa npc remove <id>` removes one (the id is the Citizens id).
Bindings are saved in `npcs.yml`. Rename or restyle the NPC with Citizens' own commands.

### 2.11 PvP and combat

Command: `/oa guide pvp`. Master switch `pvp.enabled`. This system is server-wide and independent of matches (players
inside an arena are handled by the match engine; entering an arena clears the combat tag).

- `/pvp` toggles your own PvP (cooldown `pvp.toggle.cooldown`, default state `pvp.toggle.default-state`). Players with
  PvP off are remembered across restarts. `/pvp status [player]` shows PvP state, combat tag, newbie protection and
  world state. `/pvp newbie` shows the time left, `/pvp newbie disable` ends your protection.
- **Combat tag:** hits tag victim and attacker for `pvp.combat.tag-duration` seconds (15). Kill credit also covers pets,
  TNT, potion clouds and end crystals. While tagged the plugin can drop flight, force survival, strip invisibility and
  disguises, and block commands (except `allowed-commands`), block placing, teleports by command, ender pearls and elytra
  (the firework-boost block needs the paper/purpur jar). Teleports caused by plugins are never blocked.
- **Combat log:** quitting while tagged is punished by `combat-log-action` (KILL, COMMAND or NOTHING); kicks and server
  shutdown are not punished.
- **Newbie protection** for first-time players (`pvp.newbie.*`), **respawn protection**, **anti kill-abuse**
  (`pvp.killabuse.*`: kills on the same victim stop counting for rewards), **border-hopping** prevention (needs
  WorldGuard), **auto-soup**, **blood** particles, **transfer drops** and a lava/fire guard near protected players.
- **Admins** (`omniarena.pvp.admin`): `/pvp tag <player> [seconds]` (1 to 3600), `/pvp untag <player>`,
  `/pvp global <on/off>`, `/pvp list`, `/pvp override` (attack regardless of toggles), `/pvp newbie add|remove <player>`,
  and `/pvp status <player>` for others.

### 2.12 Self-healing and /oa doctor

Command: `/oa guide doctor`. Permission `omniarena.admin.doctor`. Settings under `health` and `advanced.health`.

OmniArena fixes what it can and tells you when it did:

- **Config repair (startup and `/oa reload`).** Missing keys in `config.yml` and `messages.yml` are filled in from the jar,
  values of the wrong type get their default, and a file that no longer parses is replaced by a fresh default. The
  original is always copied to `backups/` first (the newest `health.backups-to-keep` are kept). Old-layout configs are
  migrated first (section 5.2). Corrupt data files are moved to `broken/` (arenas to `arenas/broken/`) and the plugin
  continues with empty data for that file.
- **Watchdog** (every `advanced.health.watchdog-seconds`, default 30, when `health.enabled`): resets arenas that are
  RUNNING or STARTING with no match, force-cleans arenas stuck in ENDING or RESETTING longer than
  `advanced.health.stuck-arena-seconds` (180; a long regeneration is fine), cleans matches whose arena was deleted,
  restores players mapped to a match that no longer exists, and restarts the matchmaking, party, tournament, sign and
  combat tasks if they stopped.
- **Error guard.** Repeating tasks and listeners that could throw run inside a guard: the error is logged once with a full
  stack trace, counted per component, and the rest of the plugin keeps running. A hook for an optional plugin switches
  itself off after three consecutive failures and back on when its plugin is re-enabled (or on `/oa reload`).
- **`/oa doctor`** prints a report with sections Config and data files, Arenas, Stuck state, Background tasks,
  Integrations and Recent errors. Problems have a clickable `[fix]`. **`/oa doctor fix`** runs every watchdog fix now.
- With `health.notify-admins` admins are told on join when something was repaired.

### 2.13 Configuration and guides

Command: `/oa guide config`.

- **Settings menu:** `/oa settings` (permission `omniarena.admin.reload`). Click a category, then a setting: booleans
  toggle, numbers go up (left click) or down (right click, shift for 10), text and lists open a chat prompt. The console
  gets a text list. `/oa settings set <key> <value>` works for scripts (lists accept comma separated values).
- **The file:** `plugins/OmniArena/config.yml` documents every key with its default. Edit it and run `/oa reload`
  (config, messages, kits and guides are reloaded; arenas only when no match is active; integrations are re-detected).
  Section 5 is the reference.
- **Web dashboard:** `/oa web` (section 8).
- **Plugin control:** `/oa plugin stop|start|restart|version` (section 11).
- **Messages:** `messages.yml` holds every player-facing text (MiniMessage). Missing keys fall back to the jar's version.
- **Guide books:** `plugins/OmniArena/guides.yml`. Each topic has `title`, `admin`, `summary` and `text`. Text is
  MiniMessage with real formatting (`<bold>`, `<italic>`, colours). The shorthand `{{/oa setup <arena>}}` makes a
  clickable command that puts the text in chat; `{{!/oa menu}}` runs it on click. A line containing only `<page>` forces a
  new page; long text is split automatically (about 14 lines of 19 characters). Edited topics override the jar, topics you
  delete or leave out are filled in from the jar, and topics you add appear after the built-in ones. Run `/oa reload`.

---

## 3. Command reference

Root command `/oa` (aliases `/arena`, `/omniarena`). `/oa` alone opens the main menu for players and the help index for
the console. `/oa help [command]` shows a clickable categorised index (admin commands only for admins); `/oa <command>
help` shows one command's usage. Unknown input gets a did-you-mean suggestion. While the plugin is stopped only
`/oa plugin start` works.

### 3.1 Player commands (permission `omniarena.use` unless noted)

| Command | What it does |
|---------|--------------|
| `/oa menu` | Open the main menu |
| `/oa join [arena]` | Join an arena (no name opens the picker); also `/join` |
| `/oa spectate [arena]` | Watch a match (`omniarena.spectate`); also `/spectate` |
| `/oa leave` | Leave your arena or stop spectating; also `/leave` |
| `/oa list [arena]` | List arenas, or show details of one |
| `/oa stats [player]` | Kills, deaths, wins, K/D, time, ELO and rank |
| `/oa stats top [kills/wins/kd/elo] [n]` | Leaderboards |
| `/oa queue [kit]` | Join the ranked queue (`omniarena.queue`); also `queue leave`, `queue status` |
| `/oa bet place/cancel/info ...` | Bets (`omniarena.bet`); also `/bet` |
| `/oa tournament ...` | Tournaments; also `/tournament` |
| `/oa guide [topic]` | In-game guide books (admin topics only for admins) |
| `/oa help [command]` | Command index |
| `/party ...` | Parties: create, invite, accept, decline, leave, kick, leader, disband, info |
| `/kit ...` | Personal kits (`omniarena.kit.use`); menu with no arguments |
| `/pvp` | Toggle your PvP (`omniarena.pvp`); also `status`, `newbie` |

### 3.2 Admin commands

| Command | Permission | What it does |
|---------|------------|--------------|
| `/oa setup <arena> [region/lobby/spectator/spawn/clearspawns/finish]` | `omniarena.admin.arena` | Create and set up an arena |
| `/oa edit <arena> [set <key> <value> / get <key>]` | `omniarena.admin.arena` | Edit menu or text edit |
| `/oa edit <arena> whitelist/blacklist add/remove/list [player]` | `omniarena.admin.access` | Access lists |
| `/oa arena tp/delete/enable/disable <arena>` | `omniarena.admin.arena` | Per-arena actions |
| `/oa regen <arena>`, `/oa regen snapshot <arena>` | `omniarena.admin.arena` | Rebuild, re-snapshot |
| `/oa kits [action ...]` | `omniarena.admin.kit` | Preset kits (menu or text) |
| `/oa sign create/remove/list [arena]` | `omniarena.admin.sign` | Arena join signs |
| `/oa leaderboard create/remove/list <id> ...` | `omniarena.admin.leaderboard` | Leaderboard holograms |
| `/oa npc create/remove/list ...` | `omniarena.admin.npc` | Citizens NPCs |
| `/oa stats reset <player>` | `omniarena.admin.stats` | Wipe a player's stats |
| `/oa settings [set <key> <value>]` | `omniarena.admin.reload` | Plugin settings |
| `/oa reload` | `omniarena.admin.reload` | Reload config, messages, kits, guides, arenas (when idle) |
| `/oa plugin start/stop/restart/version` | `omniarena.admin` | Lifecycle; the only command that works while stopped |
| `/oa web [kill]` | `omniarena.admin` | Dashboard link; `kill` revokes all sessions |
| `/oa doctor [fix]` | `omniarena.admin.doctor` | Health report and fixes |
| `/pvp tag/untag/global/list/override ...` | `omniarena.pvp.admin` | Admin PvP |

Console and operators pass every admin check. Per-command help is automatic.

### 3.3 Tab completion and errors

Every command tab-completes arenas, players, kits, tournaments, worlds and keys. An unknown subcommand answers
"Unknown command. Did you mean /oa X?" with a clickable suggestion; wrong arguments print the usage with a clickable
example; an unknown arena lists the real arenas; an arena that cannot be joined says exactly why. Unexpected errors in a
command are logged in full to the console and shown to the player as a short message with the usage.

---

## 4. Permissions

| Node | Default | Used by |
|------|---------|---------|
| `omniarena.use` | true | `/oa`, join, leave, list, stats, queue menu, guide, help, party, `/tournament` |
| `omniarena.kit.use` | true | `/kit` |
| `omniarena.queue` | true | `/oa queue` |
| `omniarena.bet` | true | `/oa bet`, `/bet` |
| `omniarena.spectate` | true | `/oa spectate`, `/spectate` |
| `omniarena.pvp` | true | `/pvp`, `/pvp status`, `/pvp newbie` |
| `omniarena.tournament.create` | true | create, join, leave and start own tournaments |
| `omniarena.kit.slots.5`, `omniarena.kit.slots.10` | false | 5 or 10 personal kit slots |
| `omniarena.admin` | op | Parent of all admin nodes; lifecycle, web |
| `omniarena.admin.arena` | op | setup, edit, arena, regen, wand, guides for admins |
| `omniarena.admin.access` | op | arena whitelist and blacklist |
| `omniarena.admin.kit` | op | preset kits |
| `omniarena.admin.sign` | op | arena signs |
| `omniarena.admin.leaderboard` | op | `/oa leaderboard` |
| `omniarena.admin.npc` | op | `/oa npc` |
| `omniarena.admin.stats` | op | `/oa stats reset` |
| `omniarena.admin.reload` | op | reload, settings |
| `omniarena.admin.doctor` | op | `/oa doctor` |
| `omniarena.pvp.admin` | op | admin PvP commands, targeting other players |
| `omniarena.tournament.admin` | op | manage any tournament |
| `omniarena.join.bypass` | op | skip the whitelist and the full-arena check |

`omniarena.admin` has as children all `omniarena.admin.*` nodes, `omniarena.pvp.admin`, `omniarena.tournament.admin`
and `omniarena.join.bypass`. Preset kits can require any permission node you choose.

---

## 5. Configuration reference

### 5.1 The config.yml layout (version 2)

`plugins/OmniArena/config.yml` is grouped into sections: `general`, `arenas`, `matches`, `regen`, `ranked`, `kits`,
`rewards`, `betting`, `tournaments`, `pvp`, `visuals`, `integrations`, `web`, `health` and `advanced`. Every key has its
default and allowed values in a comment. Edit the file and run `/oa reload`, or use `/oa settings` or the web dashboard.
Missing keys are filled in automatically. Only the `web` section and `advanced.web` need a plugin restart. Keys under
`arenas` that are marked as defaults apply to arenas created afterwards; existing arenas keep their own values (change
them with `/oa edit`).

#### `general`: General

| Key | Default | Meaning |
|-----|---------|---------|
| `general.debug` | `false` | verbose console diagnostics |
| `general.wand-material` | `WOODEN_HOE` | any item name: the region selection wand |

#### `arenas`: Arenas (defaults for new arenas)

| Key | Default | Meaning |
|-----|---------|---------|
| `arenas.min-players` | `2` | whole number >= 1 |
| `arenas.max-players` | `16` | whole number >= min-players |
| `arenas.countdown` | `10` | seconds, 0+: lobby countdown before a match |
| `arenas.match-duration` | `600` | seconds, 1+ |
| `arenas.game-mode` | `DEATHMATCH` | a game type name (section 2.2) |
| `arenas.spectator-allowed` | `true` | on or off |
| `arenas.mid-match-join` | `false` | allow joining a running match |
| `arenas.respawn-delay` | `3` | seconds, 0+ |
| `arenas.spawn-protection` | `3` | seconds of invulnerability after spawning, 0+ |
| `arenas.region-protection` | `true` | block break/place in arena regions between matches (admins bypass) |
| `arenas.sign-glow` | `true` | put glowstone behind arena join signs |

#### `matches`: Matches

| Key | Default | Meaning |
|-----|---------|---------|
| `matches.end-delay` | `5` | seconds, 0+: pause between match end and reset |
| `matches.lobby-timeout` | `120` | seconds, 0+: lobby closes after this long below the minimum players |
| `matches.inventory-restore` | `true` | give players their own gear back when they leave |
| `matches.announce-arena` | `true` | announce the result inside the arena |
| `matches.announce-global` | `false` | announce the result to the whole server |
| `matches.end-commands` | `[]` | Console commands run when a match ends. Placeholders: {winner} {loser} {arena} {mode} |

#### `regen`: Regeneration

| Key | Default | Meaning |
|-----|---------|---------|
| `regen.auto` | `true` | rebuild from the snapshot after every match |
| `regen.style` | `SPHERE` | INSTANT, SPHERE, RISE, FALL, SWEEP, TIME_REWIND, GALAXY, HOLOGRAM_SCAN, METEOR_SHOWER, OVERGROWTH |
| `regen.particles` | `true` | particle effects during the rebuild |
| `regen.sound` | `true` | sound effects during the rebuild |
| `regen.progress-messages` | `true` | chat progress updates during a manual /oa regen |

#### `ranked`: Ranked play

| Key | Default | Meaning |
|-----|---------|---------|
| `ranked.party-max-size` | `10` | whole number >= 2: players per party |

#### `kits`: Kits

| Key | Default | Meaning |
|-----|---------|---------|
| `kits.player-kits-enabled` | `true` | players may save personal kits |
| `kits.default-slot-limit` | `3` | whole number: personal kit slots per player |
| `kits.sharing-enabled` | `true` | players may share kits |
| `kits.power-cap.enabled` | `true` | sanitise kits (enchant cap, banned items) |
| `kits.power-cap.max-enchant-level` | `5` | whole number |

#### `rewards`: Rewards

| Key | Default | Meaning |
|-----|---------|---------|
| `rewards.enabled` | `false` | on or off |
| `rewards.kill-money` | `10.0` | number >= 0 |
| `rewards.win-money` | `50.0` | number >= 0 |
| `rewards.kill-xp` | `5` | whole number |
| `rewards.win-xp` | `20` | whole number |
| `rewards.win-commands` | `[]` | console commands per winner; {player} {arena} |
| `rewards.kill-commands` | `[]` | console commands per kill; {player} {victim} {arena} |

#### `betting`: Betting

| Key | Default | Meaning |
|-----|---------|---------|
| `betting.enabled` | `true` | on or off |
| `betting.min-bet` | `100.0` | number >= 0 |
| `betting.max-bet` | `10000.0` | number >= min-bet |
| `betting.allow-spectator-bet` | `true` | spectators of the match may bet |
| `betting.bet-window-seconds` | `30` | seconds: betting stays open this long after the match starts |

#### `tournaments`: Tournaments

| Key | Default | Meaning |
|-----|---------|---------|
| `tournaments.walkover-grace-seconds` | `60` | seconds: wait for an offline player before awarding a walkover |

#### `pvp`: PvP and combat

| Key | Default | Meaning |
|-----|---------|---------|
| `pvp.enabled` | `true` | master switch for everything under pvp |
| `pvp.server-pvp` | `true` | server-wide PvP on/off (also set by /pvp global) |
| `pvp.disabled-worlds` | `[]` | world names where PvP is fully off |
| `pvp.transfer-drops` | `false` | a victim's drops go straight to the killer |
| `pvp.combat.tag-duration` | `15` | seconds in combat after a hit |
| `pvp.combat.tag-on-damage` | `true` | taking damage tags you |
| `pvp.combat.tag-on-attack` | `true` | attacking tags you |
| `pvp.combat.block-teleport` | `true` | block /tpa, /tp, /home... while tagged (OmniArena's own are allowed) |
| `pvp.combat.block-elytra` | `true` | no elytra gliding or firework boost while tagged |
| `pvp.combat.block-ender-pearl` | `false` | block ender pearls while tagged |
| `pvp.combat.block-commands` | `true` | block commands while tagged (except allowed-commands) |
| `pvp.combat.block-block-place` | `true` | no block placing while tagged |
| `pvp.combat.disable-fly` | `true` | drop flight when combat starts |
| `pvp.combat.disable-gamemode` | `true` | force survival when combat starts |
| `pvp.combat.disable-invisibility` | `true` | strip invisibility when combat starts |
| `pvp.combat.disable-disguise` | `true` | undisguise (LibsDisguises) when combat starts |
| `pvp.combat.punish-combat-log` | `true` | punish quitting while tagged |
| `pvp.combat.combat-log-action` | `KILL` | KILL, COMMAND or NOTHING |
| `pvp.combat.combat-log-commands` | `[]` | run when combat-log-action is COMMAND; {player} is replaced |
| `pvp.combat.drop-on-combat-log` | `true` | the combat logger's items drop |
| `pvp.combat.broadcast-combat-log` | `true` | announce combat logging |
| `pvp.toggle.enabled` | `true` | players may use /pvp to switch PvP off for themselves |
| `pvp.toggle.cooldown` | `5` | seconds between /pvp toggles |
| `pvp.toggle.default-state` | `true` | PvP on for new players |
| `pvp.newbie.enabled` | `true` | protect first-time players |
| `pvp.newbie.duration` | `300` | seconds of protection |
| `pvp.newbie.disable-on-attack` | `true` | protection ends if the newbie attacks |
| `pvp.newbie.block-pickup` | `false` | protected players cannot pick up items |
| `pvp.killabuse.enabled` | `true` | stop farming the same victim |
| `pvp.killabuse.max-kills` | `3` | kills on one victim before they stop counting |
| `pvp.killabuse.time-window` | `300` | seconds the kill window spans |
| `pvp.respawn-protection.enabled` | `true` | on or off |
| `pvp.respawn-protection.duration` | `5` | seconds of invulnerability after respawn |
| `pvp.border-hopping.prevent` | `true` | tagged players cannot flee into safe zones |
| `pvp.auto-soup.enabled` | `false` | on or off |
| `pvp.auto-soup.heal-amount` | `3.5` | half-hearts healed per soup |
| `pvp.auto-soup.soup-material` | `MUSHROOM_STEW` | item name |
| `pvp.blood.enabled` | `true` | blood splash on hit |
| `pvp.blood.block` | `REDSTONE_BLOCK` | block name used for the particles |
| `pvp.block-lava-fire-near-disabled` | `true` | no lava/fire placement near players with PvP off |
| `pvp.lava-fire-radius` | `5.0` | blocks: radius for the option above |

#### `visuals`: Visuals

| Key | Default | Meaning |
|-----|---------|---------|
| `visuals.titles` | `true` | on or off |
| `visuals.sounds` | `true` | on or off |
| `visuals.particles` | `true` | on or off |
| `visuals.scoreboard` | `true` | sidebar |
| `visuals.boss-bar` | `true` | on or off |
| `visuals.action-bar` | `true` | on or off |
| `visuals.killstreak-titles` | `true` | on or off |
| `visuals.kill-announcements` | `true` | on or off |
| `visuals.victory-fireworks` | `true` | on or off |
| `visuals.countdown-sounds` | `true` | on or off |
| `visuals.spectator-invisibility` | `true` | on or off |
| `visuals.scoreboard-title` | `"<gradient:#ff6b35:#f7c59f>OmniArena</gradient>"` | MiniMessage text |
| `visuals.boss-bar-colors.timer` | `GREEN` | default GREEN: normal match timer |
| `visuals.boss-bar-colors.waiting` | `YELLOW` | default YELLOW: waiting/starting |
| `visuals.boss-bar-colors.ending` | `PURPLE` | default PURPLE: match ending |
| `visuals.boss-bar-colors.low-time` | `RED` | default RED: little time left |
| `visuals.boss-bar-colors.low-time-seconds` | `60` | seconds: "little time" threshold |
| `visuals.killstreak.sound` | `ENTITY_LIGHTNING_BOLT_THUNDER` | sound name |
| `visuals.killstreak.particle` | `FLAME` | particle name |

#### `integrations`: Integrations

| Key | Default | Meaning |
|-----|---------|---------|
| `integrations.vault` | `true` | economy for rewards, bets, tournament prizes |
| `integrations.placeholderapi` | `true` | %omniarena_...% placeholders (leaderboards for any hologram plugin: %omniarena_top_<kills/wins/kd/elo>_<1-10>_name% / _value%) |
| `integrations.worldguard` | `true` |  |
| `integrations.luckperms` | `true` |  |
| `integrations.decentholograms` | `true` | /oa leaderboard create <id> [kills/wins/kd/elo] [size] |
| `integrations.fancyholograms` | `true` | same command, used when DecentHolograms is not installed |
| `integrations.citizens` | `true` | /oa npc create <menu/join/queue/spectate> |
| `integrations.multiverse` | `true` | /oa arena tp and /oa setup can load an arena's world on demand |
| `integrations.tab.takeover` | `false` | on or off |

#### `web`: Web dashboard

| Key | Default | Meaning |
|-----|---------|---------|
| `web.enabled` | `true` | on or off |
| `web.bind-address` | `127.0.0.1` | 127.0.0.1 = this machine only (safe default). Use 0.0.0.0 for remote access, but put it behind an HTTPS reverse proxy. |
| `web.port` | `8123` | 1-65535 |
| `web.public-url` | `""` | optional base URL used in generated editor links |
| `web.title` | `OmniArena Control` | dashboard title text |

#### `health`: Self-healing

| Key | Default | Meaning |
|-----|---------|---------|
| `health.enabled` | `true` | runtime watchdog (config repair at startup always runs) |
| `health.notify-admins` | `true` | tell admins on join when something was repaired |
| `health.backups-to-keep` | `10` | whole number >= 1: config/messages backups kept in plugins/OmniArena/backups/ |

#### `advanced`: Advanced

| Key | Default | Meaning |
|-----|---------|---------|
| `advanced.persist-player-state` | `true` | write each player's pre-arena state to player-states/<uuid>.yml so a crash cannot lose inventories |
| `advanced.regen.batch-size` | `4000` | blocks per tick: higher = faster rebuild, lower = smoother |
| `advanced.regen.progress-interval-percent` | `10` | 1-100: chat progress step during manual regen |
| `advanced.signs.update-interval` | `20` | ticks: how often join signs refresh |
| `advanced.scoreboard.update-interval` | `20` | ticks: sidebar refresh (20 = 1 second is the effective minimum) |
| `advanced.health.watchdog-seconds` | `30` | seconds: how often the watchdog checks arenas and matches |
| `advanced.health.stuck-arena-seconds` | `180` | seconds: an arena stuck in ENDING/RESETTING this long is force-cleaned |
| `advanced.web.session-minutes` | `30` | minutes: dashboard login lifetime (needs a restart) |
| `advanced.web.max-sessions` | `1` | whole number: simultaneous dashboard logins (needs a restart) |

Lists not shown above: `kits.power-cap.banned-items` (default BEDROCK, COMMAND_BLOCK, BARRIER, SPAWNER,
CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK, STRUCTURE_BLOCK), `pvp.combat.allowed-commands` (default /oa, /pvp, /msg, /r,
/helpop) and `visuals.killstreak.milestones` (kills in a row to announcement text, default 5 KILLING SPREE, 10 DOMINATING,
15 UNSTOPPABLE, 20 GODLIKE). `regen.arenas.<arena-lowercase>.auto` is the per-arena rebuild override written by
`/oa edit <arena> set regen-auto`; it is not shown in the settings GUI.

### 5.2 Migration from the old layout

Files from versions before 2.0 have no `config-version` key. On startup and on `/oa reload` OmniArena detects this,
copies the file to `plugins/OmniArena/backups/`, moves every old key to its new path (a value that already exists at the
new path wins), writes the result on top of the new commented template and sets `config-version: 2`. If the file cannot
be rewritten, the old keys still work in memory. Old keys are only known to `ConfigMigration`; nothing else reads them.

| Old key | New key |
|---------|---------|
| `plugin.debug` | `general.debug` |
| `tools.wand-material` | `general.wand-material` |
| `arena.default-min-players` | `arenas.min-players` |
| `arena.default-max-players` | `arenas.max-players` |
| `arena.default-countdown` | `arenas.countdown` |
| `arena.default-match-duration` | `arenas.match-duration` |
| `arena.default-game-mode` | `arenas.game-mode` |
| `arena.spectator-allowed` | `arenas.spectator-allowed` |
| `arena.mid-match-join` | `arenas.mid-match-join` |
| `arena.respawn-delay` | `arenas.respawn-delay` |
| `arena.spawn-protection` | `arenas.spawn-protection` |
| `arena.region-protection` | `arenas.region-protection` |
| `signs.glow-effect` | `arenas.sign-glow` |
| `match.end-delay` | `matches.end-delay` |
| `arena.lobby-timeout` | `matches.lobby-timeout` |
| `arena.inventory-restore` | `matches.inventory-restore` |
| `match.announce-arena` | `matches.announce-arena` |
| `match.announce-global` | `matches.announce-global` |
| `match.end-commands` | `matches.end-commands` |
| `regen.animation.type` | `regen.style` |
| `regen.animation.particles` | `regen.particles` |
| `regen.animation.sound` | `regen.sound` |
| `regen.progress.enabled` | `regen.progress-messages` |
| `party.max-size` | `ranked.party-max-size` |
| `tournament.walkover-grace-seconds` | `tournaments.walkover-grace-seconds` |
| `pvp.global.pvp-enabled` | `pvp.server-pvp` |
| `pvp.combat.block-enderpearls` | `pvp.combat.block-ender-pearl` |
| `pvp.combat.cancel-teleport` | `pvp.combat.block-teleport` |
| `visual.titles` | `visuals.titles` |
| `visual.sounds` | `visuals.sounds` |
| `visual.particles` | `visuals.particles` |
| `visual.scoreboard` | `visuals.scoreboard` |
| `visual.boss-bar` | `visuals.boss-bar` |
| `visual.action-bar` | `visuals.action-bar` |
| `visual.killstreak-titles` | `visuals.killstreak-titles` |
| `visual.kill-announcements` | `visuals.kill-announcements` |
| `visual.victory-fireworks` | `visuals.victory-fireworks` |
| `visual.countdown-sounds` | `visuals.countdown-sounds` |
| `visual.spectator-invisibility` | `visuals.spectator-invisibility` |
| `scoreboard.title` | `visuals.scoreboard-title` |
| `boss-bar.timer-color` | `visuals.boss-bar-colors.timer` |
| `boss-bar.waiting-color` | `visuals.boss-bar-colors.waiting` |
| `boss-bar.ending-color` | `visuals.boss-bar-colors.ending` |
| `boss-bar.low-time-color` | `visuals.boss-bar-colors.low-time` |
| `boss-bar.low-time-threshold` | `visuals.boss-bar-colors.low-time-seconds` |
| `killstreak.sound` | `visuals.killstreak.sound` |
| `killstreak.particle` | `visuals.killstreak.particle` |
| `killstreak.milestones` | `visuals.killstreak.milestones` |
| `arena.persist-player-state` | `advanced.persist-player-state` |
| `regen.batch-size` | `advanced.regen.batch-size` |
| `regen.progress.interval-percent` | `advanced.regen.progress-interval-percent` |
| `signs.update-interval` | `advanced.signs.update-interval` |
| `scoreboard.update-interval` | `advanced.scoreboard.update-interval` |
| `health.watchdog-seconds` | `advanced.health.watchdog-seconds` |
| `health.stuck-arena-seconds` | `advanced.health.stuck-arena-seconds` |
| `web.session-minutes` | `advanced.web.session-minutes` |
| `web.max-sessions` | `advanced.web.max-sessions` |

Everything else (for example `regen.auto`, `regen.arenas.<name>.auto`, `rewards.*`, `betting.*`, `kits.*`, `integrations.*`,
`web.enabled`, `web.port`, `pvp.*` except the entries above) keeps its path. The old sections `plugin`, `tools`, `arena`,
`match`, `party`, `signs`, `tournament`, `visual`, `scoreboard`, `boss-bar`, `killstreak`, `regen.animation`,
`regen.progress` and `pvp.global` are removed once empty.

### 5.3 messages.yml

Every player-facing text is a MiniMessage string, with `{placeholders}` for values. `prefix` is added in front of most
messages. Keys missing from your file are taken from the jar, so upgrades never leave blanks; edit a text, then
`/oa reload`.

---

## 6. Placeholders

With PlaceholderAPI installed (`integrations.placeholderapi`), the expansion `omniarena` provides:

| Placeholder | Value |
|-------------|-------|
| `%omniarena_kills%`, `_deaths`, `_wins`, `_losses` | Lifetime stats (work anywhere) |
| `%omniarena_kd%`, `%omniarena_winrate%` | K/D ratio, win rate (with a percent sign) |
| `%omniarena_time_played%` | Time played |
| `%omniarena_elo%`, `%omniarena_rank%` | ELO (1000 default) and rank (`-` when unranked) |
| `%omniarena_in_arena%`, `%omniarena_in_match%` | true or false (match = actually running) |
| `%omniarena_arena%`, `%omniarena_arena_name%` | Arena name, `N/A` outside |
| `%omniarena_gamemode%`, `_gamemode_name`, `_gamemode_desc` | Game type id, display name, description |
| `%omniarena_goal%` | The arena's effective goal type |
| `%omniarena_state%` | Match state (WAITING, STARTING, RUNNING, ENDING ...) |
| `%omniarena_arena_kills%`, `_arena_deaths`, `_arena_streak` | Your numbers in this match |
| `%omniarena_alive%`, `_players`, `_max_players` | Alive players, players, capacity |
| `%omniarena_time_left%`, `_time_left_seconds` | Time left as `m:ss` or in seconds |
| `%omniarena_team%`, `%omniarena_team_color%` | Your team name and colour |
| `%omniarena_top_<kills/wins/kd/elo>_<1-10>_name%` | Leaderboard player name |
| `%omniarena_top_<kills/wins/kd/elo>_<1-10>_value%` | Leaderboard value |

Outside an arena the context placeholders return a sensible blank: text values are empty (`gamemode`, `state`, `team`),
numbers are `0` (`arena_kills`, `alive`, `players`, `time_left`), `arena` is `N/A`. For an offline player everything is
empty. Unknown names return nothing so PlaceholderAPI shows them as typed. Leaderboard values come from a cache refreshed
about every 30 seconds.

**TAB.** Set `integrations.tab.takeover: true` and run `/oa reload`. OmniArena then stops drawing its own scoreboard and
boss bar, and TAB can show them from the placeholders above (for example `%omniarena_gamemode_name%`,
`%omniarena_state%`, `%omniarena_alive%`).

---

## 7. Integrations

Every integration is detected automatically, can be switched off under `integrations.*`, and is re-detected on
`/oa reload`. All calls to another plugin are isolated: an error there is logged, and after three failures in a row the
hook switches itself off until the other plugin is re-enabled or `/oa reload` is run. `/oa doctor` shows each state.

| Plugin | Switch | What OmniArena does with it |
|--------|--------|-----------------------------|
| Vault | `integrations.vault` | Economy for bets, tournament prizes and money rewards |
| PlaceholderAPI | `integrations.placeholderapi` | The `omniarena` expansion (section 6) |
| WorldGuard | `integrations.worldguard` | Anti border-hopping (tagged players cannot enter `pvp deny` regions) |
| LuckPerms | `integrations.luckperms` | Hook object only; no shipped feature uses it |
| DecentHolograms | `integrations.decentholograms` | `/oa leaderboard` holograms |
| FancyHolograms | `integrations.fancyholograms` | Same, used when DecentHolograms is missing |
| Citizens | `integrations.citizens` | `/oa npc` NPCs |
| Multiverse-Core | `integrations.multiverse` | Loads an arena's world on demand (`/oa arena tp`, `/oa setup`, joining shows what to load) |
| TAB | `integrations.tab.takeover` | Hands scoreboard and boss bar over to TAB |
| LibsDisguises | (always, by reflection) | Undisguise when combat starts |

Rewards (`rewards.enabled`): money (`kill-money`, `win-money`, needs Vault), XP and console commands per kill or win
(`kill-commands` with `{player}`, `{victim}`, `{arena}`; `win-commands` with `{player}`, `{arena}`). Kill rewards are
suppressed for abusive kills.

---

## 8. Web dashboard

`/oa web` (permission `omniarena.admin`) prints a clickable login link. `/oa web kill` revokes every session. The server
is the JDK `HttpServer` bound to `web.bind-address` and `web.port` (default `127.0.0.1:8123`, this machine only). For
remote use bind to `127.0.0.1` behind an HTTPS reverse proxy and set `web.public-url`; do not expose it directly.

Tabs: **Dashboard** (live stats and arena cards), **Live Matches** (refreshed every 5 seconds), **Arenas** (list and
editor, including lives), **Players** (online players with ELO, wins and losses; search), **Config** (every
`config.yml` and `messages.yml` setting with controls and search) and **Docs** (this file, with a table of contents).
Edits are drafted in the browser and applied with the Apply button; everything is validated first (types, ranges, known
keys, enums, min players not above max, lives 0 to 100) and nothing is applied if one value is invalid.

Security: sessions use random 24-byte tokens with a sliding lifetime (`advanced.web.session-minutes`, 30) and a limit
(`advanced.web.max-sessions`, 1; 0 = unlimited), compared in constant time. The page keeps the token in `sessionStorage`
and removes it from the address bar; Log out revokes it. Request bodies are limited to 1 MiB; there is no CORS;
responses carry `Cache-Control: no-store`, a CSP, `X-Frame-Options: DENY`, `nosniff` and `Referrer-Policy: no-referrer`.
All plugin access happens on the main thread (10 second timeout). The session settings are read once at startup, so
changing them needs a restart. If the port is taken the console says so.

API (all `/api/*` calls need the token as `Authorization: Bearer <token>` or `X-OmniArena-Token`): `GET /api/state`,
`POST /api/apply`, `POST /api/create-arena`, `POST /api/delete-arena`, `GET /api/docs`, `GET /api/live-matches`,
`GET /api/players?search=`, `POST /api/logout`. The static page, `/styles.css` and `/app.js` are public and contain no
secrets.

---

## 9. Event API for developers

OmniArena fires events for other plugins and offers a small facade. Add OmniArena as a `softdepend` or `depend` in your
`plugin.yml` and compile against the jar. Everything is in `com.omniarena.api`; all calls and listeners run on the main
thread. A listener that throws is isolated: the match carries on and a warning is logged.

| Event | Cancellable | Data |
|-------|:-----------:|------|
| `ArenaPlayerJoinEvent` | yes | `getPlayer()`, `getArena()`, `getArenaName()`. Fired before the player is moved; cancel to refuse the join (tell the player why) |
| `ArenaPlayerLeaveEvent` | no | `getPlayer()`, `getArena()`, `getArenaName()`, `wasSpectator()` |
| `ArenaMatchStartEvent` | no | `getArena()`, `getPlayers()`, `getPlayerIds()`, `isRanked()` |
| `ArenaMatchEndEvent` | no | `getArena()`, `getResult()` (a `MatchResult`), `getWinners()` (UUIDs), `isDraw()` |
| `ArenaKillEvent` | no | `getKiller()`, `getVictim()`, `getArena()`, `getArenaName()` |

The events are in `com.omniarena.api.event`. Facade `OmniArenaAPI` (static methods, safe when the plugin is stopped or not
started, returning empty or false values):

- `getArenaNames()`: all arena names, sorted.
- `isInMatch(Player)`: playing in an arena (lobby or match), not spectating.
- `getArenaOf(Player)`: the arena name when playing or spectating.
- `getElo(UUID)`: current ELO, 1000 for unrated players; never creates an entry.
- `joinArena(Player, String)`: joins through the normal join path with every check applied; false when refused.

```java
@EventHandler
public void onWin(ArenaMatchEndEvent e) {
    if (e.isDraw()) return;
    for (UUID id : e.getWinners()) { /* reward the winners */ }
}
```

---

## 10. Persistence files

Everything is YAML under `plugins/OmniArena/`. Locations are `world:x:y:z:yaw:pitch`.

| Path | Content |
|------|---------|
| `config.yml`, `messages.yml`, `guides.yml` | Settings, texts, guide books |
| `arenas/<name>.yml` | One arena: `name`, `enabled`, `game-mode`, player limits, `match-duration`, `countdown`, `chat-mode`, `announcements`, `mid-match-join`, `spectator-allowed`, `spawn-protection`, `goal.type/value` (plus `goal.type-override: true`), `respawn.type/delay/wave-interval`, `lives`, `rules.*`, `team.enabled/count`, `modules.enabled`, `access.whitelist-enabled/whitelist/blacklist` (UUIDs), `region.*`, `spawns.battle/lobby/spectator`, `signs`, `end-commands`, `visual.*` |
| `arenas/broken/`, `broken/` | Corrupt files moved aside by the self-healing |
| `backups/` | Timestamped copies of `config.yml` and `messages.yml` before every repair or migration |
| `snapshots/<arena-lowercase>.yml` | Regeneration snapshot, format 2: `world`, `format`, `palette` (MATERIAL and block data), `runs` (`"x,z,y0,length,paletteIndex"` columns) and optional `tiles` (containers, signs, banners). Format 1 is still readable. Container bytes are platform specific |
| `sign-glowstone.yml` | Glowstone blocks the plugin placed behind signs |
| `leaderboards.yml` | Hologram leaderboards created with `/oa leaderboard` |
| `npcs.yml` | NPC bindings created with `/oa npc` |
| `admin-kits/<name>.yml`, `player-kits/<uuid>.yml`, `kit-cooldowns.yml` | Preset kits, personal kits, cooldown expiry per player |
| `stats.yml` | Per player: name, kills, deaths, wins, losses, assists, draws, time played |
| `elo.yml` | Per player: name, rating, wins, losses, draws, highest rating |
| `tournaments.yml` | Registration and running tournaments, brackets and current round |
| `pvp-toggles.yml`, `newbie.yml` | Players with PvP off, newbie protection expiry |
| `player-states/<uuid>.yml` | Crash-safe copy of a player's pre-arena state; written when joining, deleted on restore, applied on the next join after a crash (`advanced.persist-player-state`) |

Arenas, kits and tournaments are written synchronously; stats, ELO, PvP toggles and player states are written off the
main thread (inline during shutdown); snapshots are scanned in slices and written asynchronously and atomically. A leftover
`holograms.yml` from older versions is ignored.

---

## 11. Plugin lifecycle

`/oa plugin stop` ends all matches safely (players and spectators are returned, rebuilds finish instantly, bets are
refunded, ELO, stats and arenas are saved, tournaments are saved and cleared), cancels every scheduled task, revokes web
sessions and leaves the plugin idle. While stopped, listeners and other commands do nothing. `/oa plugin start` reloads
config, arenas, kits, stats and ELO and restarts matchmaking, parties, tournaments, the web server, signs, combat tasks,
integrations and the watchdog. `/oa plugin restart` does both. `/oa plugin version` prints the plugin version.
`/oa reload` reloads config, messages, kits and guides, reloads arenas only when no match is active, restarts the sign and
combat tasks and re-detects integrations. On server shutdown the same save order runs, each step guarded so one failure
cannot skip the rest.

---

## 12. Building

Maven 3.9 or newer. Every build needs one platform profile and one Minecraft-version profile, for example
`mvn -q -Ppaper,mc-26.2 package`. Passing any `-P` turns the default profiles off, so always pass both. Platform
profiles: `paper` and `purpur` (native Adventure, source folder `src/paper/java`), `spigot` and `bukkit` (shaded and
relocated Adventure, `src/spigot/java`). Version profiles: `mc-26.3`, `mc-26.2`, `mc-26.1` (Java 25) and `mc-1.21.11`,
`mc-1.21.10`, `mc-1.21.9` (Java 21). The jar is `target/OmniArena-<platform>-<version>.jar`, 24 combinations in total;
`scripts/build-all.sh` builds them all into `dist/` (set `JAVA25_HOME` and `JAVA21_HOME`). Differences between server
types go through the `PlatformAdapter` (messages, titles, boss bars, books, item names, inventory titles, sign lines,
chat, chunk loading, item serialization). A paper or purpur jar on a non-Paper server disables itself with a message; use
the spigot or bukkit jar there. Unit tests (`mvn -q -Ppaper,mc-26.2 test`) cover pure logic only.

---

## 13. Known limitations

- **Storage.** Everything is YAML; the shaded database libraries are unused.
- **Snapshots with containers** are not portable between paper/purpur and spigot/bukkit jars; the elytra firework-boost
  block while tagged exists only on paper and purpur.
- **LuckPerms** has a hook object but no shipped feature uses it.
- **Guide books** need a client that can open written books; very long edited topics are capped at 100 pages.
- **Builds.** All paper, spigot and bukkit jars compile; the six Purpur jars could not be verified because the Purpur
  repository was unreachable. There are no automated tests against a live server.
- **Web UI icons** load from a CDN without an integrity hash; if it is unreachable the buttons keep their text labels.
