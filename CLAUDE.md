# CLAUDE.md: OmniArena developer guide for AI agents

OmniArena is a PVP arena plugin for Paper, Purpur, Spigot and Bukkit (Java, Maven, Bukkit API +
Adventure/MiniMessage). Package root `com.omniarena`, main class `com.omniarena.OmniArena`.
User-facing docs: [README.md](README.md), [DOCS.md](DOCS.md). Generic agent summary:
[AGENTS.md](AGENTS.md). Specs and plans: `docs/superpowers/`.

Read this file fully before changing code. Everything here was derived from the source; if the code
and this file disagree, the code wins: fix this file in the same change.

---

## 1. Build, test, run

Every build needs one platform profile and one Minecraft-version profile; always pass both:

```bash
mvn -q -Ppaper,mc-26.2 package       # -> target/OmniArena-paper-26.2.jar
mvn -q -Ppaper,mc-26.2 test          # JUnit 5 only
mvn -q -Ppaper,mc-26.2 -Dtest=TournamentBracketTest test
scripts/build-all.sh                 # whole 24-jar matrix into dist/ (JAVA25_HOME / JAVA21_HOME)
```

- Tests live in `src/test/java` (`RegenAnimationTypeTest`, `TournamentBracketTest`,
  `ConfigSchemaServiceTest`, `SuggestTest`, `MenuLayoutTest`, `FailureTrackerTest`, `LeaderboardPlaceholderTest`,
  `NpcActionTest`, `ArenaWorldsTest`; 39 `@Test` methods: check with `grep -r "@Test" src/test | wc -l`).
  Only pure logic is unit tested; code that needs a live Bukkit server is not.
- `target/` and `dist/` are git-ignored build output; never edit them. The old antrun step that
  copied the jar to a Windows path has been removed.
- Repositories needed: PaperMC, Spigot snapshots, PurpurMC, EssentialsX, PlaceholderAPI, EngineHub,
  Sonatype, JitPack, Vault. Outbound network may be restricted in sandboxes; builds may need a
  pre-populated `~/.m2`. Verification status: compilation was verified for all 18 paper, spigot and
  bukkit combinations (and the unit tests on paper/mc-26.2); the 6 purpur jars could not be
  verified because repo.purpurmc.org was unreachable. No live-server runtime tests exist.

### The 24-jar matrix

Platform profiles `paper`, `purpur`, `spigot`, `bukkit` x version profiles `mc-26.3`, `mc-26.2`,
`mc-26.1` (Java 25) and `mc-1.21.11`, `mc-1.21.10`, `mc-1.21.9` (Java 21). Artifact name
`OmniArena-<platform>-<mcversion>.jar`. `paper` and `mc-26.2` are `activeByDefault`, which Maven
turns off as soon as any other profile is passed with `-P`: hence "always pass both".

| Profile | Effect |
|---------|--------|
| `paper` | `paper-api`; adds `src/paper/java` (native Adventure, Paper events) |
| `purpur` | `purpur-api` (repo.purpurmc.org); adds `src/paper/java` (coordinates unverified: repo unreachable when checked) |
| `spigot` | `spigot-api`; adds `src/spigot/java`; shades and relocates Adventure (`net.kyori` -> `com.omniarena.libs.kyori`) + `adventure-platform-bukkit` |
| `bukkit` | identical to `spigot` (only the jar name differs) |
| `mc-*` | sets `mc.version`, `java.version`/`maven.compiler.release` (25 or 21), `api.version` (the `plugin.yml` `api-version`, `1.21` for all 1.21.x) and the three API versions |

`finalName` is `OmniArena-${platform}-${mc.version}`; only `plugin.yml` is resource-filtered.

Platform layer rules (`com.omniarena.platform`):
- Common code (`src/main/java`) must not use Paper-only API. Anything that differs goes through
  `PlatformAdapter` (`Platform.get()`), implemented by exactly one `PlatformImpl` per jar
  (`src/paper/java` or `src/spigot/java`). Current adapter surface: message/title/action bar/boss bar
  delivery, item name/lore, inventory title, sign lines (`SignSide` and `SignChangeEvent`),
  `TextDisplay` text, chat listener, `wasKicked`, `isServerStopping`, `commandMap`, async chunk load
  and chunk tickets, `getBlockState`, `serializeItems`/`deserializeItems` (container snapshots).
- Paper-only listeners live in `src/paper/java` (`PaperChatListener`, `PaperCombatListener` for
  `PlayerElytraBoostEvent`); Spigot has `SpigotChatListener` (`AsyncPlayerChatEvent`, kick tracking).
- `Platform.init` is the first thing in `onEnable`; a paper/purpur jar on a non-Paper server
  disables the plugin with a message. Adding an adapter method means implementing it in both
  `PlatformImpl` classes.
- Java 25 profiles need a JDK 25 toolchain; Java 21 profiles must not use language features above 21
  (write code at Java 21 level everywhere).
- Run the full test suite per profile; prefer a CI matrix over a local loop.
- Container items in `snapshots/*.yml` are encoded per flavor (native NBT bytes vs Bukkit object
  streams) and are not interchangeable.
- When profiles or versions change, update README, DOCS.md section 17, `scripts/build-all.sh` and
  this section.
- Shade plugin: FastBoard and HikariCP relocated under `com.omniarena.shaded`, Gson and MySQL
  connector bundled; the database layer is unused.

---

## 2. Architecture map

Singletons are reachable via `OmniArena.getInstance().getXxx()` and (most of them) their own
`static getInstance()`. All are constructed in `OmniArena.onEnable`.

| Package | Key classes | Responsibility |
|---------|-------------|----------------|
| `com.omniarena` | `OmniArena` | Plugin entry. Owns every manager, `stopped` flag (`isStopped()`), command/listener registration, sign refresh task, startup/shutdown banners. |
| `platform` | `Platform`, `PlatformAdapter` | Per-flavor adapter (Paper vs Spigot), see section 1. `PlatformImpl` classes are in `src/paper/java` and `src/spigot/java`. |
| `arena` | `Arena`, `ArenaManager`, `ArenaRegion`, `ArenaRules`, `ArenaTeamConfig`, `ArenaVisualConfig`, `ArenaState`, `SpawnType`, `ChatMode`, `TeamColor`, `RegionHandler` | Arena model and registry (case-insensitive name map), wand selection points, spawn lists (`getBattleSpawn(i)` wraps modulo), region containment, boundary enforcement. `ArenaManager.applyDefaults` seeds new arenas from `arena.*` / `visual.*`. |
| `arena.regen` | `BlockRestoreModule`, `RegenAnimationType` | Snapshot (palette + vertical RLE + tile entities, file format v2) and animated restore, change log for TIME_REWIND. Active-regen map, queued callbacks, descender cap (90), `cancelAll()`. |
| `config` | `ConfigManager`, `ArenaConfig` | `config.yml`/`messages.yml` access (`getMessage` adds `prefix`; bundled defaults back-fill missing message keys), batched writes (`setValues`), per-arena YAML load/save (`arenas/<name>.yml`). |
| `match` | `MatchManager`, `Match`, `MatchResult`, `MatchState`, `MatchTeam`, `GoalType`, `RespawnType`, `EndCommandHandler`, `RewardHandler`, `PlayerStateStore` | Match engine: join/leave, countdown, start, timers, end conditions, forfeit, draws, reset + auto regen, inventory/state save/restore (crash-safe store), rewards, god/freeze/lockdown admin state. |
| `gamemode` | `GameModeType`, `GameModeKits` | 16 game types: goal type, default target, respawn/regen/ring-out flags, loadouts. |
| `kit` | `KitManager`, `Kit`/`AdminKit`/`PlayerKit`, `KitGUI`, `KitShareHandler`, `KitMenuHolder` | Preset kits (`admin-kits/`) and personal kits (`player-kits/`), sanitising and power cap, sharing with 60 s expiry. |
| `queue` | `QueueManager` | Per-kit matchmaking queues of solo players and parties; 1 s task. |
| `rating` | `EloManager`, `EloRating` | ELO storage (`elo.yml`) and math (1v1 and team). |
| `party` | `PartyManager` | Parties (invites keyed by party id, leader transfer, kick, size limit `party.max-size`). Feeds the queue. |
| `betting` | `BettingManager` | Vault-backed bet pools and settlement. |
| `tournament` | `TournamentManager`, `Tournament`, `TournamentBracket`, `TournamentMatch`, `TournamentState` | Single-elimination tournaments with persistence. |
| `stats` | `StatsManager`, `PlayerStats` | Lifetime stats (`stats.yml`), top lists, `peekStats` (read-only) vs `getStats` (creates). |
| `combat` | `CombatManager`, `CombatListener`, `PvpCommandHandler` | Server-wide PvP management (tags, persistent toggles `pvp-toggles.yml`, newbie `newbie.yml`, abuse, border hopping, crystal attribution). |
| `listener` | 18 listeners (incl. `WorldListener`) | Bukkit event glue (death, damage, interact/wand/signs, sign creation/protection, movement/region, teleport, chat routing, quit/join, blocks and fluid containment, regen change log, food, drops, `/kit` interception, game-mode rules, inventory clicks, world load/unload). |
| `commands` | `CommandManager`, `SubCommand`, `KitCommandExecutor`, `SmartErrors`, `commands.admin.*` (`SetupCommand`, `EditCommand`, `ArenaCommand`, `RegenCommand`, `AdminKitCommand` = `/oa kits`, `SignCommand`, `LeaderboardCommand` = `/oa leaderboard`, `NpcCommand` = `/oa npc`, `SettingsCommand`, `ReloadCommand`, `LifecycleCommand` = `/oa plugin`, `WebEditorCommand`; `ArenaSettings`/`ArenaAccess` are the `/oa edit` helpers), `commands.player.*`, `commands.standalone.*`; `util.Suggest` | `/oa` dispatcher with two maps (admin, player); `/oa` alone opens `gui.MainMenu`; unknown input gets a did-you-mean (`SmartErrors`, `Suggest`); standalone commands forward to `/oa` subcommands. |
| `visual` | `VisualController`, `Title/Sound/Particle/Board/BossBar/ActionBar/EffectManager`, `VisualGate` | Per-match presentation (FastBoard sidebars). The built-in hologram system was removed: leaderboards are PlaceholderAPI placeholders plus `/oa leaderboard` (see `integration`). An old `holograms.yml` is ignored and logged once. |
| `module` | `ModuleManager`, `KillstreakModule`, `ComboModule`, `PowerUpModule` | Optional per-arena modules (names in `Arena.getEnabledModules()`). |
| `settings` | `SettingsGUI`, `SettingsMenuListener`, `SettingsHolder` | Chest-GUI editor over `ConfigSchemaService`. |
| `sign` | `SignManager`, `ArenaSign` | Join signs, glowstone tracking (`sign-glowstone.yml`). |
| `integration` | `IntegrationManager` (+ `IntegrationStatus`, `FailureTracker`), `VaultIntegration`, `PlaceholderAPIIntegration`, `WorldGuardIntegration`, `LuckPermsIntegration`, reflection-only `DecentHologramsHook`/`FancyHologramsHook` (`HologramHook`), `CitizensIntegration`, `MultiverseIntegration` (`Reflect` helper), services `LeaderboardCache`/`LeaderboardManager`/`NpcManager` | Soft-dependency hooks (each behind an `integrations.<name>` switch, all isolated by `IntegrationManager.safe`, see section 16c). Code that touches an optional API must stay inside these classes; hooks without an API jar on the build path (holograms, Citizens, Multiverse) are reflection only. |
| `api` | `OmniArenaAPI`, `api.event.*` (`ArenaMatchStartEvent`, `ArenaMatchEndEvent`, `ArenaPlayerJoinEvent` (cancellable), `ArenaPlayerLeaveEvent`, `ArenaKillEvent`, `ApiEvents`) | Public facade and events for other plugins. Events are fired on the main thread through `ApiEvents.fire` (never lets a listener problem break a match): start in `startMatch`, end in `endMatch` after the effects, join in `joinArena` after all checks and before state changes, leave in `leaveArena`/`leaveSpectator`, kill in `PlayerDeathListener` right after `recordKill`. |
| `permission` | `PermissionChecker` | Permission node constants and `hasAdmin`. |
| `web` | `WebDashboardServer`, `WebSessionManager`, `ConfigSchemaService`, `ConfigApplyService`, `DocsCatalog`, `web.model.*` | Embedded HTTP dashboard, schema (exactly the keys of `config.yml` + `messages.yml`) and validated apply. Static UI in `src/main/resources/web/`. |
| `util` | `AsyncUtil`, `YamlUtil`, `MessageUtil`, `ColorUtil`, `ItemUtil`, `LocationUtil`, `KitYamlLoader` | Scheduling helpers, YAML IO, MiniMessage helpers. |

Resources: `plugin.yml` (filtered), `config.yml`, `messages.yml`, `arenas/example.yml` (template,
ignored by loader), `kits/*.yml` (10 default preset kits copied to `admin-kits/`),
`web/{index.html,app.js,styles.css,docs.md}`.

Data flow of a match (read these in order): `JoinCommand`/sign/tournament/queue ->
`MatchManager.joinArena` -> countdown task -> `startMatch` -> listeners (`PlayerDeathListener`,
`EntityDamageListener`, `PlayerMoveListener`) -> `checkEndConditions` / timer -> `endMatch` ->
delayed `resetMatch` -> `cleanupMatch` -> `startAutoRegen` (arena RESETTING until the restore
completes).

---

## 3. Lifecycle

### onEnable (order matters)

1. `Platform.init(this)` (a wrong-flavor jar disables the plugin and returns).
2. `ConfigManager.load()`.
3. Construct: `VisualController`, `StatsManager`, `KitManager`,
   `KitShareHandler`, `SignManager`, `ModuleManager`, `BlockRestoreModule`, `ArenaManager`,
   `MatchManager`, `KitCommandExecutor`, `CombatManager` (its constructor calls `start()`),
   `EloManager`, `QueueManager`, `PartyManager`, `BettingManager` (looks up Vault economy once),
   `TournamentManager`.
4. Load: `statsManager.loadAll()`, `kitManager.loadAll()`, `arenaManager.loadAll()`,
   `eloManager.loadAll()`, `queueManager.start()`, `partyManager.start()`,
   `tournamentManager.start()` (loads and schedules resume of in-progress brackets).
5. `IntegrationManager.load()` then `WebDashboardServer.start()`.
6. Commands (`oa`, `kit`, `join`, `leave`, `party`, forwarders for the rest including `bet` and
   `tournament`), then listeners (plus `Platform.get().registerListeners`), then
   `startSignUpdateTask()`.

### onDisable (order matters; each step is wrapped so one failure cannot skip the rest)

`stopped = true`; cancel sign task; `KitGUI.closeAll()`, `SettingsGUI.closeAll()`; `leaveArena` for
every online player in every active match (so results land in the saves below);
`matchManager.shutdownStateStore()`; `BlockRestoreModule.cancelAll()` (finishes regens, including
those just started by the matches that ended); `combat.shutdown()`; `queue.stop()`, `party.stop()`;
`tournament.stop()` (save + clear); `betting.refundAll()`; `stats.saveAll()`; `arena.saveAll()`;
`elo.shutdown(); elo.saveAll()`; `visual.shutdown()`; `module.shutdown()`;
`web.stop()`; `integration.shutdown()`; `Platform.shutdown()`.

### `/oa plugin stop|start|restart|version` (`LifecycleCommand`)

- **stop**: `setStopped(true)`; `cancelAll()` on regen; `Bukkit.getScheduler().cancelTasks(plugin)`
  (cancels *everything*: matchmaking, sign refresh, combat tasks, tournament tick, countdowns,
  timers); leave all active matches (players and spectators); `cancelAll()` again so regens started
  by those matches finish instantly; close GUIs; save ELO, stats, arenas; refund bets;
  `tournament.stop()`; stop web; shut down visuals, modules, queue, party.
- **start** (only if stopped): `setStopped(false)`; reload config; reload arenas, kits, stats, ELO;
  restart queue, party, tournaments (which also restarts the tournament tick task), web server,
  sign task (`resetPending`), combat tasks, `visual.start()`, `IntegrationManager.start()` (leaderboard cache refresh,
  leaderboard holograms, NPC click handler). Listeners and command
  executors stay registered; `BettingManager` is not re-initialised.
- **restart**: stop on the next tick, start 20 ticks later.
- **reload** (`ReloadCommand`): reload config/messages and kits; reload arenas only when no match is
  active; `ModuleManager.reload`; restart sign task; re-run `IntegrationManager.shutdown()/load()`
  (so `integrations.*` switches apply and tripped hooks are retried; `load()` calls `start()`); restart combat tasks; refresh signs.
  `/oa plugin stop` also calls `IntegrationManager.stop()` (tasks are cancelled anyway, bindings are cleared).

### `isStopped()` gating

When stopped, `CommandManager.onCommand` allows only `/oa plugin ...`; `KitCommandExecutor`,
`PartyCommand` and `/oa queue` print the stopped message; listeners and repeating tasks return early
on `plugin.isStopped()`. **Every new listener, command entry point and repeating task must check
`plugin.isStopped()`** (or be cancelled by `stop`). Do not start schedulers outside code paths that
`/oa stop` can cancel or that `/oa start` re-creates.

Anything you add that owns a periodic task must expose `start()`/`stop()` that is idempotent
(call `stop()` first inside `start()`, like `QueueManager`/`PartyManager`) and must be wired into
onEnable, onDisable, `/oa stop` and `/oa start`.

---

## 4. Threading rules

1. **Bukkit API is main-thread only.** World access, entities, inventories, scoreboards, messaging,
   scheduler calls that touch the world, economy calls: main thread.
2. **Web handlers run on the `OmniArena-Web` pool (4 threads).** All plugin/Bukkit access inside a
   handler must go through `onMain(callable)` (`callSyncMethod`, 10 s timeout). Parse and validate
   JSON off-thread, apply on the main thread. `ConfigApplyService.apply` itself is invoked via
   `onMain`.
3. **Async saves with inline fallback.** Pattern used by `StatsManager`, `EloManager`,
   `BlockRestoreModule`: if `!plugin.isEnabled()` (shutdown) write inline, because scheduling is
   unreliable then. `AsyncUtil.*` returns `null` when the plugin is not enabled; callers must
   handle that. `EloManager.saveAll()` snapshots the data on the main thread, then writes on a
   single-thread executor guarded by a lock; `shutdown()` drains it. Do not iterate live mutable
   collections from async tasks unless the collection is concurrent and you accept a fuzzy view.
4. Shared state uses `ConcurrentHashMap`/concurrent sets where it is read from web threads or async
   writers. Preserve that when adding fields.
5. **No blocking on the main thread**: no network, no large file reads, no `Future.get()` without a
   timeout. Large block operations must be time-sliced (see `BlockRestoreModule`:
   `SNAPSHOT_BLOCKS_PER_TICK = 30000`, per-tick batches for placement).
6. Do not force-load chunks (sign refresh checks `isChunkLoaded`; leaderboard placeholders read the `LeaderboardCache` snapshot).
   Other async writers follow the same snapshot-then-write pattern: `PlayerStateStore` (single
   writer thread), `CombatManager` (`pvp-toggles.yml`, `newbie.yml`).
7. Player-facing text: build with MiniMessage via `MessageUtil.send` and `ConfigManager.getMessage`
   (the latter prepends `prefix`). Player chat is built with `Component.text` in `ChatListener.safeText`
   (never parse it as MiniMessage). Never concatenate untrusted user text into MiniMessage tags without
   considering tag injection. Component-typed Bukkit APIs (item names, sign lines, inventory titles,
   boss bars, titles) must go through `PlatformAdapter`, never straight to Paper-only methods.

---

## 5. Matchmaking, ratings and ranked flag

`QueueManager` (entry point `/oa queue join [kit]|leave|status`, `QueueCommand`, permission
`omniarena.queue`):

- Task: `runTaskTimer(plugin, processMatchmaking, 20L, 20L)` (1 s).
- Queues keyed by `kitName.toLowerCase()` (`ranked` by default, or an admin kit name; the command
  rejects unknown kit names). An entry (`QueuedPlayer`) is one solo player or a whole party unit.
- A party leader queuing a party of 2+ queues all members as one entry (everyone must be online,
  free and unqueued; non-leaders cannot queue; `canHost` requires an enabled arena with
  `team-enabled`, `team-count == 2` and `max-players >= 2 * size`). `PartyManager` calls
  `removePartyFromQueue` when the party changes; `removeOnQuit` drops the unit when a member quits
  or enters an arena.
- Entries are sorted by average `peekElo`. Pairing: the next same-size entry within the allowed
  gap; a party with no equal-size party may instead take enough solo players to fill the other
  team. Allowed gap: `BASE_RANGE = 200`, `+ RANGE_STEP = 50` for every `RANGE_STEP_SECONDS = 10`
  seconds of the *longer* waiter's wait, capped at `MAX_RANGE = 1000`; scan breaks early beyond the
  cap.
- Needs a free arena (`findFreeArena(perSide)`): enabled, `WAITING`, `isJoinable()`, not reserved by
  a tournament, no active match, and `arenaFits` (max-players >= 2 * perSide; teams required beyond
  1v1). No arena: keep everyone queued.
- `createMatch`: everyone is removed from the queue first; join side 1 (the first join marks the
  match `setRanked(true)`, `setQueueName(kit)` and `setQueueKit(kit)` if an admin kit by that name
  exists), then side 2; on failure everyone leaves and is `requeue`d (keeps `queueTime`). Team sides
  are stored in `Match.getPresetSides()` so `assignTeams` keeps parties together.
- `MatchManager.leaveArena` sends the remaining players of a collapsed ranked match (before it
  starts) back to the queue (parties through their leader). `JoinCommand` and the join sign refuse
  queued players; `MatchManager.joinArena` itself calls `forceRemove` (queue and tournament launches
  rely on it).
- Ranked matches use `effectiveMin(match)` = `clamp(arena.minPlayers, 1..2)` for the countdown and
  are closed to outsiders once underway.

Ranked flag and ELO (`MatchManager.endMatchEffects`):

- `EloManager` is touched only if `match.isRanked()`.
- 1v1 draw/timeout with exactly 2 participants: `recordDraw`. 1v1 win/loss with both ids:
  `recordMatch`. Team matches: `recordTeamMatch(winners, losers)` / `recordTeamDraw` (each player
  against the opposing side's average).
- Leaving a RUNNING ranked match while not stopped and with exactly one opponent produces a
  `FORFEIT` `MatchResult` (opponent winner, leaver loser) and `endMatch` is invoked (the leaver
  loses ELO). In a two-team ranked match the last member of a side leaving records a team loss
  directly (only when no 1v1 forfeit result was built, so ELO is never applied twice).
- `EloRating`: default 1000, floor 100, `K_FACTOR = 32`, expected score on a 400 scale. Ratings
  compute from pre-match values; `recordWin/recordLoss` apply the new rating themselves (do not
  also call `setRating`: this double-applied before, commit 79f37fe).
- `MatchResult.isDraw()` is true for `DRAW` and `TIMEOUT`.
- `/oa stats [player]` (ELO and rank are part of it) and `/oa stats top elo [n]` (`StatsCommand`, `omniarena.use`) show ratings through the
  read-only `peekRating`/`getPlayerRank`/`getLeaderboard` paths (never `getRating`, which creates an
  entry). Ratings also appear in `/oa queue status`, the `%omniarena_elo%`/`%omniarena_rank%`/`top_elo` placeholders and the web Players tab.

---

## 6. Match engine details worth knowing

- Win condition comes from `arena.getGoalType()`: the arena's `goal.type` override (only stored and
  loaded when `goal.type-override: true`; `Arena.setGoalType` clears it when it equals the game
  type's goal), otherwise `gameMode.getGoalType()`. `arena.getGoalValue()` overrides the mode's
  default target only when > 0. `OBJECTIVE`/`TIME_LIMIT` have no end rule in `checkEndConditions`.
  If the arena has teams (`team-enabled`, so `match.getTeams()` is non-empty) `KILLS` uses team
  kills and `LAST_ALIVE` uses alive teams; otherwise the individual leader / last alive player. A
  match that started with `startedWith > 1` and drops to one player/team ends in that side's favour
  (`checkEndConditions`). `GameModeType.isTeamBased()` is unused.
- Timeout: `buildTimeoutResult` awards a strict kill leader (or leading team) in `KILLS` modes;
  ties and `LAST_ALIVE` modes yield `Outcome.TIMEOUT` (a draw).
- Player state: `MatchManager.saveState/restoreState` store and restore location, game mode, health,
  food, saturation, XP, effects, flight, speeds, invisibility and (if `arena.inventory-restore`)
  inventory/armor for players *and* spectators. `PlayerStateStore` mirrors it to
  `player-states/<uuid>.yml` when `arena.persist-player-state` (default true) (snapshot on main
  thread, atomic write on a single writer thread) so `PlayerJoinListener` can restore a player
  after a crash; `MatchManager.shutdownStateStore()` flushes it in `onDisable`. Always leave
  through `leaveArena`/`leaveSpectator` so state is restored (and the file deleted). A stale
  mapping (match gone) still restores state.
- `joinArena` accepts RUNNING arenas only when `arena.isMidMatchJoin()`, refuses arenas reserved by
  an in-progress tournament (`isArenaReserved`) for anyone not in the current pending match, and
  refuses outsiders in a ranked match that is underway.
- Countdown task: below `effectiveMin` players it stays WAITING and counts `arena.lobby-timeout`
  (config, default 120, 0 = never) seconds before closing the lobby.
- `Arena.lives` (arena file key `lives`, 0 = off, max 100): settable via `/oa edit <arena> set
  lives <n>` and the dashboard. `PlayerDeathListener` decrements `Match.getLivesMap()`, respawns
  while > 0, eliminates at 0 (also in modes that normally never respawn); on KILLS goals
  `checkEndConditions` also ends the match when one side is left with lives. `BoardManager` shows
  `Lives:` and `ActionBarManager` shows hearts.
- `EntityDamageListener` isolates arenas: damage between a player in a match and an outsider/other
  match/spectator is cancelled. `PlayerDeathListener.onRespawn` forces respawns inside the arena.
- `PlayerDropItemListener` cancels manual drops when the arena rule `item-drop` is off.
- Player *i* spawns at `arena.getBattleSpawn(i)` (index modulo size). Respawns use a random spawn.
- `cleanupMatch` uses `activeMatches.remove(arenaName, match)`: the delayed `resetMatch` of an old
  match must never tear down a newer one on the same arena (fixed in 79f37fe). After removing the
  match it calls `startAutoRegen` (section 7); `cleanupMatch` only starts it when the match state is RUNNING/ENDING/RESETTING (a lobby-only match whose last player left directly is not regenerated).
- `endMatch` is guarded by state `ENDING`/`RESETTING` and by identity
  (`activeMatches.get(arena) == match`), so it is idempotent. `endMatchEffects` (wrapped so an
  exception cannot strand the arena in ENDING) runs in order: ELO, betting settlement,
  `TournamentManager.onMatchEnd`, titles/sounds/fireworks, boss bars, end commands, rewards
  (`RewardHandler.onWin`), stats, arena and global announcements; then the delayed reset. Keep
  betting/tournament hooks before cleanup.
- Death handling is in `PlayerDeathListener`; it forces keep-inventory, clears drops unless the arena
  allows item drops, awards `rewards.kill-*` (suppressed by the kill-abuse window), schedules respawn
  or eliminates. Ring-out is in `GameModeRulesListener`.
- Internal teleports must go through `MatchManager.markInternalTeleport` so
  `PlayerTeleportListener` does not eject the player.
- There is no admin match control any more (no force start/win, freeze, god, lockdown); `MatchManager.forceWin` stays for tournaments.

---

## 7. Regeneration

- Regeneration is **automatic** and can also be run by hand. `MatchManager.startAutoRegen` (called
  from `cleanupMatch`) restores the arena after a match when the region is defined and
  `regen.arenas.<arena-lowercase>.auto` (if set) or `regen.auto` (default true) is true. It is not
  gated on `isStopped()` (matches ended by `/oa stop` / `onDisable` must still restore; `cancelAll`
  then finalizes them synchronously). The arena is `RESETTING` until the restore callback, which
  sets it back to `WAITING` unless it was disabled or a new match exists; failure and a missing
  snapshot (warning + `notifyAdmins`) also reopen the arena. Manual entry points: `/oa regen
  <arena>` (refused while a match is active or a regen is running).
- Snapshot: `createSnapshot` scans in slices on the main thread into a palette (`MATERIAL|blockdata`)
  plus runs `{x, z, y0, len, paletteIdx}` and a `tiles` list (containers, signs, banners captured
  via `Platform.getBlockState`/`serializeItems`), then `AsyncUtil.runAsyncThenSync` writes
  `snapshots/<arena-lowercase>.yml` atomically with `format: 2`, `palette`, `runs` (strings
  `"x,z,y0,len,idx"`) and optional `tiles`. `loadEntries` also reads legacy format 1.
- Restore: async load + ordering (`orderEntries`), then a repeating sync task places batches without
  physics (tile data applied after the block, containers cleared first); preview blocks one tick
  ahead for animated styles; `clearTransientEntities` removes items/projectiles/XP/TNT/falling
  blocks/clouds; `activeRegens` maps arena -> finalizer so `cancelAll()` can complete instantly; a
  second restore on the same arena queues only its callback.
- Change log: `RegenChangeListener` records block changes (break, place, explosions, fluid, burn,
  fade, form) of RUNNING arenas through `BlockRestoreModule.recordChange` (max 200k positions,
  ignored while regenerating, cleared by `clearChangeLog` at match start and after a regen). Only
  `TIME_REWIND` consumes it. `BlockPlaceListener.onFluidFlow` cancels water/lava flowing out of a
  region during a match.
- Styles: 10 enum values in `RegenAnimationType` (`INSTANT`, `SPHERE` default, `RISE`, `FALL`,
  `SWEEP`, `TIME_REWIND`, `GALAXY`, `HOLOGRAM_SCAN`, `METEOR_SHOWER`, `OVERGROWTH`), plus the
  legacy-name map in `fromConfig`/`parse`. Unknown or blank -> `SPHERE`. Batch: `INSTANT` uses
  `regen.batch-size`; animated types use `min(batch-size, 1200)`. `MAX_DESCENDERS = 90`.
- When adding a style: add the enum value, `orderEntries` case, `buildPreview` case, optional
  sound case, extend `RegenAnimationTypeTest`, and update docs (counts appear in README, DOCS.md and
  the help guides).
- `regen.arenas.<name>.auto` is not in `config.yml`, hence not in the settings schema (no GUI or
  dashboard entry). Set it with `/oa regen auto <arena> <true|false|default>` (`default` unsets it)
  or by editing the file.

---

## 8. Tournaments

- `TournamentManager` (`tournaments.yml`), command `TournamentCommand` registered as `/oa tournament`
  (in `commands/player`, permissions `omniarena.tournament.create` / `.admin`).
- States: `REGISTRATION -> IN_PROGRESS -> FINISHED` (or `CANCELLED` on delete). Auto-start when full.
- Bracket: join-order seeding; byes go to the first `nextPow2 - n` seeds; each round pairs winners in
  order; odd leftover gets a safety bye. One live match at a time per arena: `launchPendingMatches`
  waits while a started match is live (`checkLive`), launches the first pending match whose players
  are free and the arena is idle, and requeues nothing itself (it calls `leaveQueue` for both
  players). A 100-tick task (`tickTask`, cancelled in `stop()`, gated by `isStopped()`) retries
  launches and recovers matches that vanished without a result (`tm.setStarted(false)`).
- Offline player: `waitingSince` map; walkover only after `tournament.walkover-grace-seconds`
  (default 60). A player who leaves the arena while the match runs forfeits via `forceWin`.
- `startTournament` drops offline participants and needs >= 2 online players.
- `onMatchEnd(MatchResult)`: finds the first started, non-completed, non-bye match of the current
  round that involves the winner/loser or both participants. **Draw/timeout rule**: if the result has
  no winner in the match, `decideDraw`: the player still in the arena (or only the online one) wins;
  else higher current health; else random.
- Prizes in `finishTournament`: champion = `prizeFirst`; final loser = `prizeSecond`; `prizeThird`
  is divided equally (rounded down to cents) among `getSemifinalLosers()` (losers of the round before
  the final; no 3rd place match). Zero prizes skipped; no Vault -> warning logged. Payment via
  `VaultIntegration.depositOffline`.
- Persistence: `saveAll` after every transition; stores participants, prizes, state, and for
  `IN_PROGRESS` the full bracket (`bracket.rounds` as lists of `{p1, p2?, winner?}` maps and
  `bracket.currentRound`). `loadAll` skips FINISHED/CANCELLED, restores brackets (falling back to
  `REGISTRATION` if unusable), and schedules `resume` on the next tick. `stop()` saves then clears
  memory; `start()` reloads. Tournament matches are not ranked.
- Delay between launches: `match.end-delay + 2` seconds (arena must finish resetting first). With
  automatic regeneration the arena stays `RESETTING` until the rebuild is done, so an early launch
  fails `joinArena` and the 100-tick safety task retries it.
- A standalone `/tournament` command exists (plugin.yml, forwarded to `/oa tournament`).
- Arenas used by tournaments need `min-players <= 2`.

---

## 9. Betting settlement rules

`BettingManager.processMatchResults(arena, winnerId, loserId)` (called from `endMatch`):

- Removes the arena's bet list. `winnerPool` = sum of bets whose predicted winner == `winnerId`.
- If `winnerId == null` (draw/timeout/no winner) **or** `winnerPool <= 0`: refund every bet
  (never destroy money).
- Else each winning bet gets `floor(totalPool * (bet / winnerPool) * 100) / 100` (offline players
  included); losing bets are forfeited.
- `onMatchCleanup` refunds still-pending bets (aborted match); `refundAll()` on `/oa stop` and
  disable; `cancelBet` only while the window is open and only if the bet is still in `activeBets`
  (`remove(key, value)` prevents double refunds).
- Window: open until `betting.bet-window-seconds` after `onMatchStart`. **One active bet per player
  across all arenas** (`activeBets` keyed by player; a second bet is rejected until cancelled).
  `BetCommand` also requires a live match that is not ENDING/RESETTING.
- Economy is resolved once in the constructor; `enabled` stays false without Vault + provider.
  `placeBet` additionally checks `betting.enabled` and `integrations.vault` on every call.
- `betting.allow-spectator-bet: false` rejects bets from spectators of that arena's match.
- `/bet` is a standalone command forwarded to `/oa bet`; `omniarena.bet` is declared (default true).

---

## 10. Persistence formats

All YAML under `plugins/OmniArena/`. See DOCS.md section 19 for field-level layouts. Key rules:

- Arena files: `arenas/<name>.yml`, loaded by `ArenaConfig` (skips `example.yml`). Locations are
  `world:x:y:z:yaw:pitch` (`LocationUtil`). `enabled` is derived from state != DISABLED. `lives` and
  `goal.type` (+ `goal.type-override`), `respawn.wave-interval` and `access.whitelist-enabled|
  whitelist|blacklist` (UUID strings) are persisted. `team.colors` and `classes` in the example file are not read.
- Snapshots: format v2 palette/runs plus optional `tiles` (section 7).
- `sign-glowstone.yml`: `locations: ["world,x,y,z"]`; only positions listed are ever cleared.
- `leaderboards.yml` (`LeaderboardManager`: `boards.<id>` metric/size/hook/placeholders/position), `npcs.yml`
  (`NpcManager`: `npcs.<citizensId>` action/arg). A pre-existing `holograms.yml` is ignored, never deleted.
  `pvp-toggles.yml` (`pvp-off` list), `newbie.yml` (uuid -> expiry millis).
- `stats.yml`, `elo.yml`, `tournaments.yml`, `admin-kits/`, `player-kits/<uuid>.yml`,
  `kit-cooldowns.yml`, `player-states/<uuid>.yml`.
- `YamlUtil.save` swallows `IOException` and logs. Prefer it for new files.
- Never change a persisted key name without a migration or a fallback read of the old key (see the
  `cancel-teleport` / `block-enderpearls` fallbacks in `CombatListener.legacyBool`, which use
  `isSet` so the jar defaults do not mask an old key).

---

## 11. Commands, permissions, config: how to add things

### Adding a `/oa` subcommand

1. Create a `SubCommand` subclass in `commands/admin` or `commands/player` (constructor passes the
   required permission node, usage, description).
2. Register it in `CommandManager.registerAll` in the right map. Admin map entries are gated by
   `PermissionChecker.hasAdmin` (console and OP pass). Player-map entries are gated for non-OP players by
   `p.hasPermission(subcommand.getRequiredPermission())`, so the permission passed to `super(...)`
   must be a node that regular players actually have (declared in `plugin.yml` with `default: true`),
   otherwise non-OP players are denied.
3. If it should also exist as a standalone command, add it to `plugin.yml` `commands:` and register a
   `PlayerCommandForwarder` in `OmniArena.onEnable` (and add a permission entry). `bet` and
   `tournament` are registered through the small loop at the end of that block.
4. Declare every permission node you check in `plugin.yml` (all current nodes are declared,
   including `omniarena.bet`, `omniarena.queue`, `omniarena.admin.leaderboard` and `omniarena.admin.npc`).
5. Tab completion goes in `tabComplete`. `/oa <sub> help` is automatic (except `regen`, `kit`,
   `adminkit`).
6. Add messages to `messages.yml` and read via `ConfigManager.getMessage(key)`.
7. Update README command table, DOCS.md sections 15 and 16, and the help guides in `HelpCommand`.

Final tree: see `docs/superpowers/qol-commands-final.md`. Use `SmartErrors` (wrongArgs, arena,
unknownSubcommand, explainNotJoinable) for user mistakes so errors stay clickable and specific.
`/oa kits` (admin presets) is the only admin-kit entry; `/kit` is the player kit command.

### Adding a config key

1. Add it to `src/main/resources/config.yml` with a comment.
2. Read it with `ConfigManager.getInstance().getConfig().getXxx(key, default)` at the point of use.
3. The web/GUI schema derives from the live `config.yml` and `messages.yml` automatically
   (`ConfigSchemaService.settings()`; type is inferred from the value). There is no padding and no
   `extra(...)` list any more, so only keys that exist in the files appear. Check
   `ConfigSchemaServiceTest` and the web Config tab after changes.
4. If the key must be validated in the web UI add rules in `ConfigApplyService` (types, ranges, enums).
5. Document it in DOCS.md section 18 and, if user-facing, the README.
6. Do not add a key nobody reads. Today every key in `config.yml` is read. `plugin.debug` is read via
   `ConfigManager.debugLog(() -> "...")` (lazy supplier, no cost when off); use it for verbose
   diagnostics (matchmaking, regen timings, web requests) and never log tokens.

### Messages

All user text goes through `messages.yml` where practical; many commands still hard-code MiniMessage
strings (`TournamentManager`, `QueueManager`, `QueueCommand`, `BettingManager`,
`CommandManager`). New code should prefer message keys. `ConfigManager.applyMessageDefaults()` falls
back to the jar's bundled `messages.yml`.

---

## 12. Web dashboard rules

- `WebDashboardServer` registers contexts through `ctx(path, handler)` (wraps errors into JSON
  `{"ok":false,"error":...}` responses: `HttpError` status, 400 for malformed JSON, 503 on a main
  thread timeout, 500 otherwise). Add new API handlers there, authorise with `authorize(exchange)`
  (token from `Authorization: Bearer`, `X-OmniArena-Token`, or legacy `?token=`), and use `onMain`
  for Bukkit access.
- Static assets (`/`, `/styles.css`, `/app.js`) are public; **no secret may be embedded in them**.
  Responses carry `Cache-Control: no-store`, a CSP, `X-Frame-Options: DENY`, `nosniff` and
  `Referrer-Policy: no-referrer`; keep them when adding handlers (the shared header helper does it).
- Bind default is `127.0.0.1`. Do not change the default. Do not add endpoints that execute commands
  or read files by user-supplied path.
- Body limit `MAX_BODY_BYTES = 1 MiB`; keep validation all-or-nothing (`ConfigApplyService`) and
  validate length, charset and ranges for every new field (e.g. `lives` 0-100). Arena names match
  `[A-Za-z0-9_-]{1,32}` (same rule in `CreateArenaCommand`).
- Sessions: `WebSessionManager` tokens (24 random bytes), **sliding** expiry (renewed by each valid
  request), `max-sessions` cap, `POST /api/logout` revokes one, `stop()` revokes all; the manager is
  created once in the server constructor (config changes to TTL/cap need a restart).
- The UI loads Phosphor icons from unpkg without SRI. To compute the hash:
  `curl -s https://unpkg.com/@phosphor-icons/web@2.1.1/src/bold/style.css | openssl dgst -sha384 -binary | openssl base64 -A`
  then add `integrity="sha384-..."` to the `<link>` in `index.html` (keep `crossorigin`).
- `web/docs.md` is a copy of DOCS.md shown in the Docs tab and must stay byte-identical:
  `cp DOCS.md src/main/resources/web/docs.md` after every DOCS.md edit.
- The frontend polls `/api/live-matches` every 5 s while that tab is open.

---

## 13. Coding conventions

- Java 21 language level everywhere (the 1.21.x profiles compile with `--release 21`; no preview
  features). 4-space indent, braces on the same line.
- Package-private singletons: follow the existing `private static X instance; getInstance()` pattern
  and set `instance = this` in the constructor.
- Prefer `Optional` returns for lookups (`ArenaManager.get`).
- Arena names are looked up case-insensitively (lowercased keys) but saved with original case in the
  file name and `name:` field. Snapshot files use the lowercase name.
- Use `plugin.getLogger()` for diagnostics; wrap fragile shutdown steps in try/catch with a log.
- Never swallow exceptions silently except where the existing code deliberately does so (optional
  integrations).
- Match state mutations (kills, alive set) happen on the main thread only.
- Do not use `Thread.sleep`, blocking IO or `.join()` on the main thread.
- Do not call Paper-only API from `src/main/java`; put it behind `PlatformAdapter` (section 1).
- Keep optional-API imports (Vault, PlaceholderAPI, WorldGuard, LuckPerms) inside `integration/`
  classes so the plugin loads when they are absent. DecentHolograms, FancyHolograms, Citizens and
  Multiverse have no Maven dependency: use `Reflect` (no new API jars). Call every hook through
  `IntegrationManager.safe` (section 16c). `BettingManager` imports Vault types directly:
  keep Vault registration guarded by the `Vault` plugin presence check.
- Do not add dependencies without discussing shading/relocation.
- Avoid adding features that are not wired to a user entry point; if infrastructure is added first,
  document it as "no entry point yet".
- Tests: prefer pure-logic unit tests (bracket, schema, enums); avoid MockBukkit unless it is added to
  the POM.
- Commit style: short imperative subject; the repo history uses "Audit fixes: ..." / "Match flow: ..."
  style prefixes.

---

## 14. Pitfalls (learned from the history and from reading the code)

1. `AsyncUtil.*` returns `null` when the plugin is disabled: guard before storing the `BukkitTask`.
2. Calling `Bukkit.getScheduler().cancelTasks(plugin)` (done by `/oa stop`) kills every periodic task;
   anything started elsewhere will not return unless `/oa start` restarts it.
3. Double application of ELO deltas (fixed): never call both `recordWin()` and `setRating()`.
4. Stale delayed callbacks: always compare identity (`remove(key, value)`, `tournaments.get(name) == t`)
   in delayed tasks.
5. Regen is automatic after played matches (`MatchManager.startAutoRegen`): the arena is `RESETTING`
   until it finishes, so code that waits for `WAITING` (tournament launches, queue) must tolerate
   that and retry. Arenas without region/snapshot, and lobby matches abandoned before they start, are not restored.
6. The engine reads `arena.getGoalType()` (override or game type), not the raw `goalType` field;
   `goal.type` in a file only counts with `goal.type-override: true`.
7. `LifecycleCommand.start` does not recreate `BettingManager` or listeners; `stop` calls
   `visual.shutdown()` (clears boards/boss bars) and `IntegrationManager.stop()`; `start` calls
   `visual.start()` and `IntegrationManager.start()`.
8. `PlayerCommandPreprocessListener` intercepts `/kit` for players in arenas even when another plugin
   owns `/kit`.
9. Admin preset kits are managed only through `/oa kits` (`AdminKitCommand`); `/oa` has no player `kit`
   subcommand. Player-initiated equips go through
   `KitManager.tryEquip` (lobby-only inside arenas, permission, persisted cooldown): do not call
   `equipKit` directly for player actions.
10. Standalone `/join`, `/leave`, `/spectate` are registered in `plugin.yml` and can clash
    with other plugins' commands.
11. Player-map commands are permission-checked by the dispatcher using their constructor node; keep
    that node declared and default-true for anything regular players should use.
12. `ConfigSchemaService.settings()` instantiates a lot on each call (`SettingsGUI.grouped()` constructs a
    new service per call): avoid calling it in hot paths.
13. `WebSessionManager` is created once; `web.session-minutes`/`web.max-sessions` do not live-reload.
14. Maven profiles: `paper` and `mc-26.2` are `activeByDefault`, which is switched off by any
    explicit `-P`; always pass `-P<platform>,mc-<version>`.
15. `target/` and `dist/` are git-ignored; do not force-add build output.
16. Arena whitelist/blacklist/`whitelist-enabled` are managed by `/oa edit <arena> whitelist|blacklist ...` (`ArenaAccess`,
    `omniarena.admin.access`), saved in the arena file and enforced in `MatchManager.joinArena`
    (blacklist always; whitelist when enabled unless `omniarena.join.bypass`/OP). Do not add join
    paths that skip `joinArena`.
16a. Visual channels: `VisualGate` combines the global `visual.titles|sounds|particles`
    key with the arena flag. New titles/sounds/particles must go through `TitleManager`,
    `SoundManager`, `ParticleManager`/`EffectManager` (which gate) or call `VisualGate` themselves.
16c. Integration isolation (`IntegrationManager`): hooks are `vault`, `placeholderapi`, `worldguard`, `luckperms`,
    `decentholograms`, `fancyholograms`, `citizens`, `multiverse` (switch `integrations.<name>`, default true;
    softdepends in `plugin.yml`). `safe(name, Runnable)` / `safe(name, Supplier, fallback)` catch Throwable
    (incl. `NoClassDefFoundError`/`LinkageError`), log the first failure once, record `lastError` and switch the hook
    off after 3 consecutive failures (`FailureTracker`, pure and unit tested); a success resets the counter.
    A hook is also released on `PluginDisableEvent` of its plugin and set up again on `PluginEnableEvent`;
    `/oa reload` retries everything. `statuses()` returns `List<IntegrationStatus>(name, present, enabled, lastError)`
    for `/oa doctor`. Convenience wrappers exist for the old call sites (`deposit`, `depositOffline`, `isPvpDenied`);
    `getVault()`/`getWorldGuard()` return null while the hook is off. New hook code never runs outside `safe`.
16d. Leaderboards: `LeaderboardCache` (top 10 of kills/wins/kd/elo + ELO rank map) is rebuilt on the main thread about
    every 30 s by `IntegrationManager` and read lock-free by `PlaceholderAPIIntegration` (never creates stats/ELO
    entries). Placeholders: `%omniarena_top_<kills|wins|kd|elo>_<1-10>_<name|value>%`, `%omniarena_elo%`,
    `%omniarena_rank%`, `%omniarena_arena%`, `%omniarena_in_match%` (+ the older lifetime/match ones).
    `/oa leaderboard create|remove|list` (`omniarena.admin.leaderboard`) builds the hologram in DecentHolograms
    (preferred) or FancyHolograms with `%` placeholder lines when PlaceholderAPI is live, else static lines pushed
    every 60 s. Hologram names are `omniarena_lb_<id>`; no hook -> a message pointing to the plugins/placeholders.
16e. NPCs: `/oa npc create|remove|list` (`omniarena.admin.npc`, needs Citizens, `integrations.citizens`) binds a
    Citizens NPC id to `NpcAction` (menu, join <arena>, queue [kit], spectate [arena]); a right-click runs the
    matching `/oa ...` command as the player (all normal checks, 750 ms debounce). Citizens persists the NPC
    entity itself; `npcs.yml` only keeps the binding.
16f. Unloaded worlds: arenas whose region/spawn world is not loaded are kept (spawns stay as raw strings in
    `Arena.getUnresolvedSpawns`, written back on save), `Arena.getMissingWorlds()` is non-empty and `isJoinable()` is
    false; `joinArena` answers "Arena world 'x' is not loaded". `WorldListener` re-resolves on `WorldLoadEvent`
    (`ArenaManager.onWorldLoad`) and demotes spawns on `WorldUnloadEvent` (arenas with a running match excepted).
    `/oa arena tp` and `/oa setup` try `ArenaManager.ensureWorlds`, which loads the world through Multiverse
    (v5 API, v4 API, then `mv load`) when present.
16b. A paper/purpur jar and a spigot/bukkit jar write container snapshots differently; do not mix
    snapshot files between them.
17. `RespawnType.WAVE`: dead players are parked in spectator mode (`MatchManager.queueWaveRespawn`,
    queue in `Match.getWaveQueue()`) and released together by `tickWave` from the match timer every
    `Arena.respawnWaveInterval` seconds (`respawn.wave-interval`, default 10); the shared respawn
    body is `MatchManager.completeRespawn`. `respawn.delay` only applies to `DELAYED`.
18. `BlockRestoreModule` keeps `shuttingDown = true` after `cancelAll()` until the next snapshot or
    restore resets it; `/oa start` therefore works for regen again on the next call.
19. When a match has `getQueueKit()` set, loadouts come from that admin kit, otherwise from
    `GameModeKits`; respawns call the same `applyLoadout`.
20. `isModuleEnabled` (`modules.*`) is unused. Check DOCS.md section 18 before assuming a key works.
    The `/oa edit` keys `module-killstreak|module-combo|module-power-up` are the only module switches.
21. `index.html` is a resource under `src/main/resources/web`: changes require a rebuild to be served
    (served from the jar via `plugin.getResource`).

---

## 15. Documentation upkeep

Update, in the same change as code: README.md (features, commands, config), DOCS.md (the matching
section, plus section 21 if you add/remove a limitation) and then `cp DOCS.md
src/main/resources/web/docs.md` (the two files must be identical), this file (architecture, rules),
AGENTS.md (if a key rule changes), and for tournament/betting/web work the notes at the bottom of the
matching file in `docs/superpowers/`. Never describe a feature that is not reachable from a command,
sign, GUI or the web UI without saying so.
