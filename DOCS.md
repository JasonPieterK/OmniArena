# OmniArena — Complete Documentation

OmniArena is a full PVP arena + server-wide combat plugin for Paper 26.2. It bundles arena
matches with 16 game types, 15 block-based regen animations, a kit system, a web dashboard,
an in-game settings GUI, a PvPManager-style combat system, ELO ratings, queue matchmaking,
party support, and betting — all driven by one `/oa` command.

---

## 1. Quick start

1. Drop `OmniArena-1.0.0-shaded.jar` into `plugins/` and restart.
2. Get the selection wand: `/oa wand` (a wooden hoe by default).
3. **Left-click** one corner of your arena, **right-click** the opposite corner.
4. Create the arena: `/oa create MyArena`.
5. Save the region: `/oa region set MyArena`.
6. Set spawns (stand where you want each one): `/oa spawn set MyArena BATTLE` — run it once per
   spawn pad. Player 1 takes spawn 1, player 2 takes spawn 2, and so on.
7. Set a lobby + spectator spawn: `/oa spawn set MyArena LOBBY`, `/oa spawn set MyArena SPECTATOR`.
8. Pick a game type: open `/oa settings` or the web editor and set the arena's game mode.
9. Snapshot the build so it can regenerate: `/oa regen snapshot MyArena`.
10. Enable it: `/oa enable MyArena`. Players join with `/oa join MyArena` or a join sign.

---

## 2. Arenas

An arena is a named region with spawns, rules, a game type, and visual settings.

- `/oa create <name>` — create a new arena.
- `/oa delete <name>` — delete it.
- `/oa enable <name>` / `/oa disable <name>` — toggle availability.
- `/oa list` — list all arenas and their state.
- `/oa info <name>` — show an arena's configuration.
- `/oa tp <name>` — teleport to an arena.

### Region selection
- `/oa wand` — get the selection tool (default **wooden hoe**, configurable via `tools.wand-material`).
- Left-click = position 1, right-click = position 2.
- `/oa region set <name>` — save the selected box as the arena region.
- Region protection (no block damage outside matches) is controlled by `arena.region-protection`.

### Spawns (numbered)
Battle spawns are numbered in the order you set them. At match start, **player _i_ spawns at
pad _i_** — six players with six pads each get their own spawn instead of stacking.

- `/oa spawn set <arena> BATTLE` — add the next battle spawn (reports the number, e.g. "spawn #3").
- `/oa spawn set <arena> LOBBY` — set a lobby/waiting spawn.
- `/oa spawn set <arena> SPECTATOR` — set the spectator spawn.
- `/oa spawn list <arena>` — list every spawn with its number.
- `/oa spawn clear <arena> BATTLE` — clear all battle spawns.
- `/oa spawn clear <arena> BATTLE <#>` — remove one specific spawn by number.

> Tip: set as many battle spawns as your max player count so nobody shares a pad.

---

## 3. Game types (16)

Each game type is a complete mode: its own loadout, win condition, and rules. Set it per arena
in `/oa settings` or the web editor (the "Game type" dropdown). The scoreboard shows the
friendly name; the win condition and kit are applied automatically.

| Game type | Loadout & rules | Win condition |
|-----------|-----------------|---------------|
| **Deathmatch** | Diamond kit + bow, FFA | First to kill target |
| **Team Deathmatch** | Iron kit + bow, two teams | Highest team kills |
| **Last One Standing** | Diamond kit, **one life** | Last player alive |
| **Sumo** | Knockback-II stick, no armor, **ring-out** | Knock everyone off |
| **Classic PvP** | Iron armor + iron sword + bow | First to kill target |
| **Crystal PvP** | Netherite + end crystals + obsidian + anchors + **totems** | First to kill target |
| **Mace** | Mace (Density/Wind Burst) + elytra + wind charges + rockets | First to kill target |
| **Sword Duel** | Sharpness-IV netherite sword + gapples, one life | Last alive |
| **Axe & Shield** | Netherite axe + shield + gapples, one life | Last alive |
| **UHC** | Diamond kit, **no natural regen**, gapples only | Last alive |
| **Potion PvP** | Diamond kit + splash health/speed/fire-res pots | First to kill target |
| **NoDebuff** | Diamond kit + gapple & buff-pot stacks | First to kill target |
| **Archer** | Infinity Power-III bow + light armor | First to kill target |
| **Netherite OP** | Fully enchanted netherite + totem, FFA | First to kill target |
| **Spleef** | Efficiency-V shovel, jump boost, no armor, **ring-out** | Last alive |
| **Gladiator** | No armor, stone sword + shield | First to kill target |

- **One-life modes** (Last One Standing, Sumo, Sword Duel, Axe & Shield, UHC, Spleef) drop you
  into spectator on death instead of respawning.
- **Ring-out modes** (Sumo, Spleef) kill you if you fall below the arena floor.
- **UHC** suppresses natural/food regen — only golden apples and potions heal.
- Kill-target modes use the arena's goal value if set, otherwise the mode's default.

---

## 4. Regen animations (15)

After a match, the arena rebuilds from a saved snapshot using real animated blocks (not just
particles). Take a snapshot first: `/oa regen snapshot <arena>`.

- `/oa regen <arena>` — rebuild now using the default style.
- `/oa regen style <type>` — set the default animation.
- `/oa regen batch <n>` — blocks restored per tick (higher = faster, lower = smoother).
- `/oa regen settings` — show current regen config.
- `/oa regen progress|sound <true|false>` — toggle the progress bar / sounds.

**Styles:** `SPIRAL`, `OUTSIDE_IN`, `CENTER_OUT`, `TOP_TO_BOTTOM`, `BOTTOM_TO_TOP`, `LASER_RAIN`,
`IRON_MAN`, `RANDOM_BURST`, `INSTANT`, `BEAM_SWEEP`, `SHOCKWAVE`, `CRYSTAL_GROWTH`,
`VORTEX_REBUILD`, `METEOR_DROP`, `PRISM_WAVE`.

Highlights:
- **IRON_MAN** — red-concrete suit pieces fall from the sky with arc-reactor jets; the suit
  assembles bottom-up.
- **METEOR_DROP** — flaming magma meteors crash down and explode on impact.
- **LASER_RAIN** — white-glass laser rounds streak down and vanish on hit.
- Falling-block entities are capped (90 concurrent) so big arenas stay performant.

---

## 5. Kits

Two kinds: **player kits** (personal, saved loadouts) and **admin/preset kits** (server-provided).

### Player commands — `/kit` or `/oa kit`
Both open the same combined menu (your kits on the left, public preset kits on the right).
- `/kit` — open the kit menu.
- `/kit create <name>` — save your current inventory as a kit.
- `/kit list` — list your kits.
- `/kit equip <name>` — load a kit.
- `/kit delete <name>` / `/kit rename <old> <new>` — manage kits.
- `/kit share <player> <name>` / `/kit accept` — share a kit with someone.
- `/kit preview <name>` — preview a kit's contents.

### Admin kit actions — `/oa kit ...`
- `/oa kit setpublic <name>` / `setprivate <name>` — control preset visibility.
- `/oa kit icon|description|cooldown|permission <name> ...` — configure a preset kit.

Slot limits: `kits.default-slot-limit`, plus permissions `omniarena.kit.slots.5` / `.10`.

---

## 6. Web dashboard & in-game settings

### Web editor — `/oa editor`
Opens a session link (default `http://127.0.0.1:8123/?token=...`). The dashboard (Mean-Median-Mode
styled, animated) has four tabs:
- **Dashboard** — live stats and arena cards (LIVE / STARTING / WAITING / IDLE).
- **Arenas** — list + full editor (game type, players, rules, visuals, region).
- **Config** — every setting grouped by category, each with a hover description and the right
  control (toggle / number / text). Searchable.
- **Docs** — this document.

Security: the number of concurrent editor sessions is capped by `web.max-sessions` (default **1**).
If the limit is reached, `/oa editor` tells you and sessions auto-expire after `web.session-minutes`.

### In-game settings GUI — `/oa settings`
Opens an interactive chest UI (no more chat spam):
- A category grid → click a category → a paginated list of its settings.
- **Booleans** toggle on click. **Numbers** adjust with left (+1) / right (−1) / shift (±10).
- **Text/list** settings open a chat prompt to type the value.
- Console still gets the text list; `/oa settings set <key> <value>` works for scripting.

Everything in `config.yml` (200+ keys) is editable from config.yml, the web Config tab, **and**
this GUI — they all read and write the same live config.

---

## 7. PvP / Combat system (server-wide)

PvPManager-style combat management that applies outside arena matches. The old `/pmr` prefix is now
`/oa`. Master switch: `pvp.enabled`.

### Features
- **Combat tagging** — hitting or being hit tags you for `pvp.combat.tag-duration` seconds.
- **Combat-log punishment** — logging out while tagged kills you (or runs configured commands) and
  broadcasts it. (`pvp.combat.combat-log-action`: KILL / COMMAND / NOTHING.)
- **PvP toggle** — per player (`/oa pvp`), per world (`/oa pmr worlds`), and server-wide (`/oa pvpglobal`).
- **On-combat lockdowns** — drops flight, forces survival, strips invisibility, and undisguises
  (LibsDisguises, if installed).
- **Newbie protection** — new players are protected for `pvp.newbie.duration` seconds; attacking
  gives it up.
- **Respawn protection** — brief invulnerability after respawn; attacking ends it early.
- **Anti kill-abuse** — repeatedly killing the same victim stops counting after a threshold.
- **Anti border-hopping (WorldGuard)** — tagged players can't flee into a `pvp deny` region.
- **Auto-soup** — right-click soup to instantly heal (toggle `pvp.auto-soup.enabled`).
- **Blood animation** — block-break particle burst on every valid hit.
- **In-combat blocks** — no commands (except an allow-list), enderpearls, teleports, or block
  placement while tagged.
- **Block lava/fire near protected players** — can't place lava buckets, fire, or campfires within
  `pvp.lava-fire-radius` of anyone with PvP disabled / newbie / respawn-protected.
- **Transfer drops to killer** — optionally send a victim's drops straight to their killer.
- **Per-world disable** — `pvp.disabled-worlds`.

### Player commands
- `/oa pvp [player]` — toggle your PvP (admins can target a player).
- `/oa pvpstatus [player]` — show PvP + combat state.
- `/oa pvptag` — check your combat timer.
- `/oa newbie [disable]` — check or give up newbie protection.

### Admin commands (`omniarena.pvp.admin`)
- `/oa pvptag <player>` — combat-tag a player.
- `/oa untag <player>` — clear a player's tag.
- `/oa pvpglobal <on|off>` — toggle server-wide PvP.
- `/oa pvpoverride` — let yourself attack anywhere (ignores toggles/protection).
- `/oa pvpinfo [player]` — full PvP report on a player.
- `/oa pvplist` — PvP state of all online players.
- `/oa newbie <add|remove|checktime> <player> [seconds]` — manage newbie protection.
- `/oa pmr <help|cleanup|reload|worlds <world>>` — combat admin: clean the database, reload config,
  or toggle a world's PvP.

---

## 8. ELO Rating System

Competitive matchmaking using the standard ELO algorithm (K-factor 32, 400-point scale). Ratings
update automatically after each match and persist to `elo.yml`.

### Features
- **Automatic rating updates** — winners gain ELO, losers lose ELO based on rating difference
- **Leaderboard** — top players ranked by ELO
- **Win/loss tracking** — with win rate and K/D ratio statistics
- **Abuse prevention** — tracks recent matches to prevent rating farming
- **Rating floor** — minimum rating of 100 to prevent negative ratings

### Commands
- `/oa elo [player]` — show your ELO rating (or another player's)
- `/oa elo leaderboard [top N]` — show top players (default 10)
- `/oa elo reset <player>` — reset a player's rating to 1000 (admin)

### Configuration
- `elo.enabled` — enable/disable ELO system
- `elo.default-rating` — starting rating (default 1000)
- `elo.min-rating` — rating floor (default 100)
- `elo.k-factor` — rating volatility (default 32)

---

## 9. Queue System with Matchmaking

Automatic matchmaking that pairs players based on ELO rating. Players join kit-specific queues
and are matched when compatible opponents are available.

### Features
- **Kit-specific queues** — separate queues for each game type/kit
- **ELO-based matchmaking** — matches players within 200 rating points
- **Automatic arena assignment** — finds available arenas automatically
- **Real-time queue status** — shows position and total in queue
- **Auto-requeue** — re-queues players if no arena is available

### Commands
- `/oa queue join <kit>` — join the queue for a specific kit
- `/oa queue leave` — leave the current queue
- `/oa queue status` — show your queue position
- `/oa queue list` — show all active queues and their sizes

### Configuration
- `queue.enabled` — enable/disable queue system
- `queue.elo-max-diff` — maximum ELO difference for matching (default 200)
- `queue.match-interval` — matchmaking check interval in ticks (default 20)

---

## 10. Party System

Team-based PvP with party creation, invitations, and management. Parties can queue together
and play in team modes.

### Features
- **Party creation** — create a party with yourself as leader
- **Invitations** — invite players with 60-second expiration
- **Leadership transfer** — pass leadership to another member
- **Member management** — kick members, leave party
- **Party size limit** — maximum 10 players per party
- **Party notifications** — all members get updates on joins/leaves

### Commands
- `/party create` — create a new party
- `/party disband` — disband your party (leader only)
- `/party invite <player>` — invite a player to your party
- `/party accept` — accept a pending party invite
- `/party decline` — decline a pending party invite
- `/party leave` — leave your current party
- `/party kick <player>` — kick a player (leader only)
- `/party leader <player>` — transfer leadership (leader only)
- `/party info` — show party information

### Configuration
- `party.enabled` — enable/disable party system
- `party.max-members` — maximum party size (default 10)
- `party.invite-timeout` — invite expiration in seconds (default 60)

---

## 11. Betting System with Vault Integration

Players can bet on match outcomes using server economy (requires Vault + economy plugin). Bets
are pooled and winners receive a share based on their contribution to the winning pool.

### Features
- **Match betting** — bet on who will win a match
- **Pool-based payouts** — winners split the total pool proportionally
- **Min/max bet limits** — configurable betting limits
- **Bet cancellation** — cancel bets before match starts
- **Offline refunds** — automatically refund offline players
- **Betting stats** — view total bets and pool size per match

### Commands
- `/oa bet <arena> <player> <amount>` — place a bet on a player
- `/oa bet cancel` — cancel your current bet
- `/oa bet info` — show your active bet
- `/oa bet stats <arena>` — show betting stats for a match

### Configuration
- `betting.enabled` — enable/disable betting system
- `betting.min-bet` — minimum bet amount (default 100)
- `betting.max-bet` — maximum bet amount (default 10000)

### Requirements
- **Vault** plugin must be installed
- **Economy plugin** (e.g., EssentialsX, CMI) must be installed

---

## 12. Integrations

Auto-detected on startup (all soft dependencies):
- **Vault** — economy rewards (`rewards.*`).
- **PlaceholderAPI** — exposes placeholders (see below); needed for TAB.
- **WorldGuard** — anti-border-hopping (reads the region `PVP` flag; `pvp deny` = safe zone).
- **LuckPerms** — permission lookups.
- **LibsDisguises** — undisguise on combat.
- **TAB** (tab-was-taken) — set `integrations.tab.takeover: true` to let TAB draw the sidebar +
  bossbar from OmniArena placeholders instead of the built-in ones.

### PlaceholderAPI placeholders (`%omniarena_<x>%`)
Lifetime: `kills`, `deaths`, `wins`, `losses`, `kd`, `winrate`, `time_played`.
In-match: `arena`, `gamemode`, `gamemode_name`, `gamemode_desc`, `state`, `arena_kills`,
`arena_deaths`, `arena_streak`, `alive`, `players`, `max_players`, `time_left`,
`time_left_seconds`, `team`, `team_color`, `in_arena`.

### TAB setup
1. Install TAB + PlaceholderAPI.
2. Set `integrations.tab.takeover: true` and `/oa reload`.
3. In TAB's config, build a scoreboard/bossbar from the `%omniarena_...%` placeholders above.

---

## 13. Command reference

### Player
`/oa join <arena>`, `/oa leave`, `/oa spectate <arena>`, `/oa list`, `/oa info <arena>`,
`/oa stats`, `/oa ready`, `/oa class`, `/kit` (and `/oa kit`), `/oa elo`, `/oa queue`, `/oa bet`,
plus the PvP player commands and party commands (`/party`).

### Admin
`/oa create|delete|enable|disable <arena>`, `/oa region`, `/oa spawn`, `/oa class`, `/oa goal`,
`/oa module`, `/oa match`, `/oa forcewin`, `/oa regen`, `/oa sign`, `/oa kit`, `/oa tp`,
`/oa debug`, `/oa announcements`, `/oa chat`, `/oa reload`, `/oa version`, `/oa wand`,
`/oa editor`, `/oa settings`, `/oa elo reset`, plus the PvP admin commands and `/oa pmr`.

Run `/oa <command> help` for per-command usage, or `/oa help` for the full list.

---

## 14. Permissions

- `omniarena.use` (default true) — basic player commands.
- `omniarena.kit.use` (default true) — kit commands.
- `omniarena.pvp` (default true) — PvP player commands.
- `omniarena.pvp.admin` (op) — PvP admin commands.
- `omniarena.admin` (op) — full admin, includes `omniarena.admin.arena/kit/match/sign/stats/reload`.
- `omniarena.spectate` (true), `omniarena.join.bypass` (op), `omniarena.kit.slots.5/.10`.

---

## 15. Configuration

All settings live in `config.yml` and messages in `messages.yml`. Every key is editable three ways:
edit the file and `/oa reload`, use the web **Config** tab, or use the in-game `/oa settings` GUI.

Major sections: `plugin`, `tools`, `database`, `arena`, `match`, `regen`, `pvp`, `rewards`, `kits`,
`signs`, `visual`, `killstreak`, `scoreboard`, `boss-bar`, `integrations`, `anticheat`,
`performance`, `web`, `elo`, `queue`, `party`, `betting`.

The `pvp` block alone covers tagging, combat-log, toggles, newbie protection, kill-abuse, respawn
protection, border-hopping, auto-soup, blood, lava/fire protection, drop transfer, and disabled
worlds. See the comments in `config.yml` for every key.

---

*OmniArena 1.0.0 · Paper 26.2*
