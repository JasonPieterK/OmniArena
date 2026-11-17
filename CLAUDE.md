# CLAUDE.md: OmniArena developer guide for AI agents

OmniArena is a Paper PVP arena plugin (Java, Maven, Bukkit API + Adventure/MiniMessage). Package
root `com.omniarena`, main class `com.omniarena.OmniArena`. User-facing docs: [README.md](README.md),
[DOCS.md](DOCS.md). Generic agent summary: [AGENTS.md](AGENTS.md). Specs and plans:
`docs/superpowers/`.

Read this file fully before changing code. Everything here was derived from the source; if the code
and this file disagree, the code wins: fix this file in the same change.

---

## 1. Build, test, run

Current (single Paper 26.2, Java 21):

```bash
mvn -q package       # compile + tests + shade  -> target/OmniArena-1.0.0.jar
mvn -q test          # JUnit 5 only
mvn -q -Dtest=TournamentBracketTest test
```

- Tests live in `src/test/java` (`RegenAnimationTypeTest`, `TournamentBracketTest`,
  `ConfigSchemaServiceTest`). Only pure logic is unit tested; code that needs a live Bukkit server
  is not.
- Shade plugin bundles FastBoard (relocated `com.omniarena.shaded.fastboard`), HikariCP
  (relocated), Gson and the MySQL connector. The database layer is unused but the libraries are
  shaded.
- `pom.xml` has a `maven-antrun-plugin` execution that copies the jar to a hard-coded Windows path
  (`C:/Users/.../plugins/OmniArena.jar`). It is irrelevant on other machines; do not "fix" the
  path, and do not depend on it. Consider making it a profile when touching the POM.
- `target/` is tracked in git. Never hand-edit files in it; expect it to show as modified after a
  build and do not include it in logical changes unless asked.
- Repositories needed: PaperMC, EssentialsX, PlaceholderAPI, EngineHub, Sonatype, JitPack,
  Vault. Outbound network may be restricted in sandboxes; builds may need a pre-populated
  `~/.m2`.

### The 24-jar matrix (plan)

Four platforms x six Minecraft versions, each its own jar:

| Platform | Versions | Java |
|----------|----------|------|
| `paper`, `spigot`, `bukkit`, `purpur` | `26.3`, `26.2`, `26.1` | 25 |
| `paper`, `spigot`, `bukkit`, `purpur` | `1.21.11`, `1.21.10`, `1.21.9` | 21 |

Artifact name: `OmniArena-<platform>-<mcversion>.jar` (24 total). Planned invocation:

```bash
mvn -P<platform>,mc-<version> package          # e.g. mvn -Ppaper,mc-26.2 package
# whole matrix (once profiles exist):
for p in paper spigot bukkit purpur; do for v in 26.3 26.2 26.1 1.21.11 1.21.10 1.21.9; do
  mvn -q -P$p,mc-$v package || exit 1; done; done
```

Status: the profiles are being set up by someone else; the checked-in POM has none and hardcodes
Paper API `26.2.build.31-alpha`, `java.version 21`, and `api-version: '26.2'` in `plugin.yml`.
Before relying on a profile, grep `pom.xml` for `<id>mc-` / `<id>paper`. Rules for matrix work:

- Do not call Paper-only API from code paths that must run on Spigot/Bukkit builds without an
  abstraction. Today the code uses Paper API widely: `AsyncChatEvent`, `Component`/MiniMessage,
  `player.spigot().respawn()`, `Attribute.MAX_HEALTH`, `Bukkit.broadcast(Component)`,
  `getMinecraftVersion()`. A Spigot/Bukkit variant needs shims (or relocated Adventure) and cannot
  be assumed to work.
- Java 25 profiles need a JDK 25 toolchain; Java 21 profiles must not use language features above 21.
- Keep `api-version` and the jar name filtered per profile (resource filtering), not hand-edited.
- Run the full test suite per profile; add a CI matrix rather than a local loop where possible.
- Update README, DOCS.md section 17, and this section when the profiles land.

---

## 2. Architecture map

Singletons are reachable via `OmniArena.getInstance().getXxx()` and (most of them) their own
`static getInstance()`. All are constructed in `OmniArena.onEnable`.

| Package | Key classes | Responsibility |
|---------|-------------|----------------|
| `com.omniarena` | `OmniArena` | Plugin entry. Owns every manager, `stopped` flag (`isStopped()`), command/listener registration, sign refresh task, startup/shutdown banners. |
| `arena` | `Arena`, `ArenaManager`, `ArenaRegion`, `ArenaRules`, `ArenaTeamConfig`, `ArenaVisualConfig`, `ArenaState`, `SpawnType`, `ChatMode`, `TeamColor`, `RegionHandler` | Arena model and registry (case-insensitive name map), wand selection points, spawn lists (`getBattleSpawn(i)` wraps modulo), region containment, boundary enforcement. |
| `arena.regen` | `BlockRestoreModule`, `RegenAnimationType` | Snapshot (palette + vertical RLE, file format v2) and animated restore. Active-regen map, queued callbacks, descender cap (90), `cancelAll()`. |
| `config` | `ConfigManager`, `ArenaConfig` | `config.yml`/`messages.yml` access (`getMessage` adds `prefix`; bundled defaults back-fill missing message keys), batched writes (`setValues`), per-arena YAML load/save (`arenas/<name>.yml`). |
| `match` | `MatchManager`, `Match`, `MatchResult`, `MatchState`, `MatchTeam`, `GoalType`, `RespawnType`, `EndCommandHandler` | Match engine: join/leave, countdown, start, timers, end conditions, forfeit, draws, reset, inventory save/restore, god/freeze/lockdown admin state. |
| `gamemode` | `GameModeType`, `GameModeKits` | 16 game types: goal type, default target, respawn/regen/ring-out flags, loadouts. |
| `kit` | `KitManager`, `Kit`/`AdminKit`/`PlayerKit`, `KitGUI`, `KitShareHandler`, `KitMenuHolder` | Preset kits (`admin-kits/`) and personal kits (`player-kits/`), sanitising and power cap, sharing with 60 s expiry. |
| `queue` | `QueueManager` | Per-kit matchmaking queues; 1 s task. |
| `rating` | `EloManager`, `EloRating` | ELO storage (`elo.yml`) and math. |
| `party` | `PartyManager` | Parties (invites, leader, kick). Not connected to matches. |
| `betting` | `BettingManager` | Vault-backed bet pools and settlement. |
| `tournament` | `TournamentManager`, `Tournament`, `TournamentBracket`, `TournamentMatch`, `TournamentState` | Single-elimination tournaments with persistence. |
| `stats` | `StatsManager`, `PlayerStats` | Lifetime stats (`stats.yml`), top lists, `peekStats` (read-only) vs `getStats` (creates). |
| `combat` | `CombatManager`, `CombatListener`, `PvpCommandHandler` | Server-wide PvP management (tags, toggles, newbie, abuse, border hopping). |
| `listener` | 14 listeners | Bukkit event glue (death, damage, interact/wand/signs, movement/region, teleport, chat, quit, blocks, food, `/kit` interception, game-mode rules). |
| `commands` | `CommandManager`, `SubCommand`, `KitCommandExecutor`, `commands.admin.*` (26), `commands.player.*` (10), `commands.standalone.*` | `/oa` dispatcher with two maps (admin, player); standalone commands forward to `/oa` subcommands. |
| `visual` | `VisualController`, `Title/Sound/Particle/Board/BossBar/ActionBar/Hologram/EffectManager` | Per-match presentation. FastBoard for sidebars. |
| `module` | `ModuleManager`, `KillstreakModule`, `ComboModule`, `PowerUpModule` | Optional per-arena modules (names in `Arena.getEnabledModules()`). |
| `settings` | `SettingsGUI`, `SettingsMenuListener`, `SettingsHolder` | Chest-GUI editor over `ConfigSchemaService`. |
| `sign` | `SignManager`, `ArenaSign` | Join signs, glowstone tracking (`sign-glowstone.yml`). |
| `integration` | `IntegrationManager`, `VaultIntegration`, `PlaceholderAPIIntegration`, `WorldGuardIntegration`, `LuckPermsIntegration` | Soft-dependency hooks. Code that touches an optional API must stay inside these classes (or use reflection like `CombatManager` does for LibsDisguises). |
| `permission` | `PermissionChecker` | Permission node constants and `hasAdmin`. |
| `web` | `WebDashboardServer`, `WebSessionManager`, `ConfigSchemaService`, `ConfigApplyService`, `DocsCatalog`, `web.model.*` | Embedded HTTP dashboard, schema (about 250 settings) and validated apply. Static UI in `src/main/resources/web/`. |
| `util` | `AsyncUtil`, `YamlUtil`, `MessageUtil`, `ColorUtil`, `ItemUtil`, `LocationUtil`, `KitYamlLoader` | Scheduling helpers, YAML IO, MiniMessage helpers. |

Resources: `plugin.yml`, `config.yml`, `messages.yml`, `arenas/example.yml` (template, ignored by
loader), `kits/*.yml` (10 default preset kits copied to `admin-kits/`), `web/{index.html,app.js,styles.css,docs.md}`.

Data flow of a match (read these in order): `JoinCommand`/sign/tournament/queue ->
`MatchManager.joinArena` -> countdown task -> `startMatch` -> listeners (`PlayerDeathListener`,
`EntityDamageListener`, `PlayerMoveListener`) -> `checkEndConditions` / timer -> `endMatch` ->
delayed `resetMatch` -> `cleanupMatch`.

---

## 3. Lifecycle

### onEnable (order matters)

1. `ConfigManager.load()`.
2. Construct: `VisualController`, `StatsManager`, `KitManager`, `KitShareHandler`, `SignManager`,
   `ModuleManager`, `BlockRestoreModule`, `ArenaManager`, `MatchManager`, `KitCommandExecutor`,
   `CombatManager` (its constructor calls `start()`), `EloManager`, `QueueManager`, `PartyManager`,
   `BettingManager` (looks up Vault economy once), `TournamentManager`.
3. Load: `statsManager.loadAll()`, `kitManager.loadAll()`, `arenaManager.loadAll()`,
   `eloManager.loadAll()`, `queueManager.start()`, `partyManager.start()`,
   `tournamentManager.start()` (loads and schedules resume of in-progress brackets).
4. `IntegrationManager.load()` then `WebDashboardServer.start()`.
5. Commands (`oa`, `kit`, standalone, forwarders), then listeners, then `startSignUpdateTask()`.

### onDisable (order matters; each step is wrapped so one failure cannot skip the rest)

`stopped = true`; cancel sign task; `leaveArena` for every online player in every active match
(so results land in the saves below); `BlockRestoreModule.cancelAll()`; `queue.stop()`,
`party.stop()`; `tournament.stop()` (save + clear); `betting.refundAll()`; `stats.saveAll()`;
`arena.saveAll()`; `elo.shutdown(); elo.saveAll()`; `visual.shutdown()`; `module.shutdown()`;
`web.stop()`; `integration.shutdown()`; also `KitGUI.closeAll()`, `SettingsGUI.closeAll()` and
`matchManager.shutdownStateStore()` run in `onDisable`.

### `/oa stop`, `/oa start`, `/oa restart` (`LifecycleCommand`)

- **stop**: `setStopped(true)`; `cancelAll()` on regen; `Bukkit.getScheduler().cancelTasks(plugin)`
  (cancels *everything*: matchmaking, sign refresh, combat tasks, tournament tick, countdowns,
  timers); leave all active matches (players and spectators); save stats, arenas; refund bets; `tournament.stop()`; stop web; shut down visuals,
  modules, queue, party.
- **start** (only if stopped): `setStopped(false)`; reload config; reload arenas, kits, stats, ELO;
  restart queue, party, tournaments (which also restarts the tournament tick task), web server,
  sign task, combat tasks. Listeners and command
  executors stay registered; `BettingManager` is not re-initialised.
- **restart**: stop on the next tick, start 20 ticks later.
- **reload** (`ReloadCommand`): reload config/messages; reload arenas only when no match is active;
  `ModuleManager.reload`; restart sign task and combat tasks; refresh signs.

### `isStopped()` gating

When stopped, `CommandManager.onCommand` allows only `/oa start`; `KitCommandExecutor` and
`PartyCommand` print the stopped message; listeners and repeating tasks return early on
`plugin.isStopped()`. **Every new listener, command entry point and repeating task must check
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
6. Do not force-load chunks (sign refresh checks `isChunkLoaded`).
7. Player-facing text: build with MiniMessage via `MessageUtil.send` and `ConfigManager.getMessage`
   (the latter prepends `prefix`). Never concatenate untrusted user text into MiniMessage tags without
   considering tag injection.

---

## 5. Matchmaking, ratings and ranked flag

`QueueManager`:

- Task: `runTaskTimer(plugin, processMatchmaking, 20L, 20L)` (1 s).
- Queues keyed by `kitName.toLowerCase()`.
- Allowed rating gap: `BASE_RANGE = 200`, `+ RANGE_STEP = 50` for every `RANGE_STEP_SECONDS = 10`
  seconds of the *longer* waiter's wait, capped at `MAX_RANGE = 1000`. Players sorted ascending by
  `peekElo` and scanned with early break when the gap exceeds the cap.
- Needs a free arena: enabled, `WAITING`, `isJoinable()`, no active match. No arena: return and keep
  everyone queued.
- `createMatch`: both players are removed from the queue first; join player 1, mark the match
  `setRanked(true)`, `setQueueName(kit)` and `setQueueKit(kit)` (if an admin kit by that name
  exists), join player 2; on failure undo and `requeue` both (keeps `queueTime`).
- The only caller of `joinQueue` outside `QueueManager` is `MatchManager.leaveArena`, which sends the
  remaining player of a collapsed ranked match back to its queue. There is **no command, GUI or sign
  for a first join**, and no ELO display command. Wiring those up is a known gap; do not document
  them as available. `MatchManager.joinArena` and tournament launches call `removeOnQuit` / `leaveQueue`
  so a manual join cancels a queue entry.
- Ranked matches use `effectiveMin(match)` = `clamp(arena.minPlayers, 1..2)` for the countdown.

Ranked flag and ELO (`MatchManager.endMatch`):

- `EloManager` is touched only if `match.isRanked()`.
- Draw/timeout with exactly 2 participants: `recordDraw`. Win/loss with both ids: `recordMatch`.
- Leaving a RUNNING ranked match while not stopped and with exactly one opponent produces a
  `FORFEIT` `MatchResult` (opponent winner, leaver loser) and `endMatch` is invoked (the leaver
  loses ELO).
- `EloRating`: default 1000, floor 100, `K_FACTOR = 32`, expected score on a 400 scale. Ratings
  compute from pre-match values; `recordWin/recordLoss` apply the new rating themselves (do not
  also call `setRating`: this double-applied before, commit 79f37fe).
- `MatchResult.isDraw()` is true for `DRAW` and `TIMEOUT`.

---

## 6. Match engine details worth knowing

- Win condition comes from `arena.getGameMode().getGoalType()`; `arena.getGoalValue()` overrides the
  mode's default target only when > 0. `Arena.goalType` is not consulted. If the arena has teams
  (`team-enabled`, so `match.getTeams()` is non-empty) `KILLS` uses team kills and `LAST_ALIVE` uses
  alive teams; otherwise the individual leader / last alive player. A match that started with
  `startedWith > 1` and drops to one player/team ends in that side's favour
  (`checkEndConditions`). `GameModeType.isTeamBased()` is unused.
- Timeout: `buildTimeoutResult` awards a strict kill leader (or leading team) in `KILLS` modes;
  ties and `LAST_ALIVE` modes yield `Outcome.TIMEOUT` (a draw).
- Player state: `MatchManager.saveState/restoreState` store and restore location, game mode, health,
  food, saturation, XP, effects, flight, speeds, invisibility and (if `arena.inventory-restore`)
  inventory/armor for players *and* spectators. `PlayerStateStore` mirrors it to
  `player-states/<uuid>.yml` (snapshot on main thread, atomic write on a single writer thread) so
  `PlayerJoinListener` can restore a player after a crash; `MatchManager.shutdownStateStore()` flushes it
  in `onDisable`. Always leave through `leaveArena`/`leaveSpectator` so state is restored (and the file
  deleted). A stale mapping (match gone) still restores state.
- `joinArena` accepts RUNNING arenas only when `arena.isMidMatchJoin()`, and refuses arenas reserved by
  an in-progress tournament (`isArenaReserved`) for anyone not in the current pending match.
- `Arena.lives` (arena file key `lives`, 0 = off): `PlayerDeathListener` decrements
  `Match.getLivesMap()`, respawns while > 0, eliminates at 0; `BoardManager` shows lives. No command
  or dashboard setting writes it yet.
- `EntityDamageListener` isolates arenas: damage between a player in a match and an outsider/other
  match/spectator is cancelled. `PlayerDeathListener.onRespawn` forces respawns inside the arena.
- `PlayerDropItemListener` cancels manual drops when the arena rule `item-drop` is off.
- Player *i* spawns at `arena.getBattleSpawn(i)` (index modulo size). Respawns use a random spawn.
- `cleanupMatch` uses `activeMatches.remove(arenaName, match)`: the delayed `resetMatch` of an old
  match must never tear down a newer one on the same arena (fixed in 79f37fe).
- `endMatch` is guarded by state `ENDING`/`RESETTING` and by identity
  (`activeMatches.get(arena) == match`), so it is idempotent. Order inside it: ELO, betting settlement,
  `TournamentManager.onMatchEnd`, visuals, end commands, stats, announcement, delayed reset.
  Keep betting/tournament hooks before cleanup.
- Death handling is in `PlayerDeathListener`; it forces keep-inventory, clears drops unless the arena
  allows item drops, schedules respawn or eliminates. Ring-out is in `GameModeRulesListener`.
- Internal teleports must go through `MatchManager.markInternalTeleport` so
  `PlayerTeleportListener` does not eject the player.
- `/oa override` state (freeze, god, lockdown) lives in `MatchManager`.

---

## 7. Regeneration

- Regeneration is **manual**: `/oa regen <arena>`, `/oa override purge <arena>`, or a console
  command in end commands. Nothing in the match flow calls `BlockRestoreModule.restore`.
- Snapshot: `createSnapshot` scans in slices on the main thread into a palette (`MATERIAL|blockdata`)
  plus runs `{x, z, y0, len, paletteIdx}`, then `AsyncUtil.runAsyncThenSync` writes
  `snapshots/<arena-lowercase>.yml` with `format: 2`, `palette`, `runs` (strings
  `"x,z,y0,len,idx"`). `loadEntries` also reads legacy format 1.
- Restore: async load + ordering (`orderEntries`), then a repeating sync task places batches; preview
  blocks one tick ahead for animated styles; `activeRegens` maps arena -> finalizer so
  `cancelAll()` can complete instantly; a second restore on the same arena queues only its callback.
- Styles: 10 enum values in `RegenAnimationType` (INSTANT, SPHERE default, RISE, FALL, SWEEP, TIME_REWIND,
  GALAXY, HOLOGRAM_SCAN, METEOR_SHOWER, OVERGROWTH); `fromConfig` maps all old 20 names and aliases to the
  closest style, unknown -> SPHERE with a console warning, `parse` is the strict variant for commands.
  Sort order is computed in `computeOrder` (off-main, in `loadPlan`); per-tick particles/sounds in
  `playEffects` (budget-capped, players within 64 blocks only). TIME_REWIND reads the per-arena change log
  fed by `RegenChangeListener` (`recordChange`, max 200k, cleared at match start and after regen).
  Batch: `INSTANT` uses `regen.batch-size`; animated styles use `min(batch-size, 1200)`. `MAX_DESCENDERS = 90`.
- When adding a style: add the enum value, a `computeKeys` (or dedicated key) case, a `playEffects` case,
  extend `RegenAnimationTypeTest`, and update docs (counts appear in README, DOCS.md and the help guides).
- The default style in `config.yml` is `SPHERE`.
  (`config.yml`) is `CENTER_OUT`.

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
- Delay between launches: `match.end-delay + 2` seconds (arena must finish resetting first).
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
- `betting.allow-spectator-bet` is not read.

---

## 10. Persistence formats

All YAML under `plugins/OmniArena/`. See DOCS.md section 19 for field-level layouts. Key rules:

- Arena files: `arenas/<name>.yml`, loaded by `ArenaConfig` (skips `example.yml`). Locations are
  `world:x:y:z:yaw:pitch` (`LocationUtil`). `enabled` is derived from state != DISABLED. Whitelist/
  blacklist are not persisted. `team.colors` and `classes` in the example file are not read.
- Snapshots: format v2 palette/runs (section 7).
- `sign-glowstone.yml`: `locations: ["world,x,y,z"]`; only positions listed are ever cleared.
- `stats.yml`, `elo.yml`, `tournaments.yml`, `admin-kits/`, `player-kits/<uuid>.yml`,
  `kit-cooldowns.yml`, `player-states/<uuid>.yml`.
- `YamlUtil.save` swallows `IOException` and logs. Prefer it for new files.
- Never change a persisted key name without a migration or a fallback read of the old key.

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
   `PlayerCommandForwarder` in `OmniArena.onEnable` (and add a permission entry).
4. Declare every permission node you check in `plugin.yml`. (`omniarena.bet` is currently required by
   `BetCommand` but undeclared, which denies non-OP players; fix when touching it.)
5. Tab completion goes in `tabComplete`. `/oa <sub> help` is automatic.
6. Add messages to `messages.yml` and read via `ConfigManager.getMessage(key)`.
7. Update README command table, DOCS.md section 15, and the help guides in `HelpCommand`.

`/oa force start <arena>` (`ForceStartCommand`), `/oa forcewin <arena> <player|team>`
(`ForceWinCommand`) and the `/oa override forcestart|forcewin|startarena` variants all exist.

### Adding a config key

1. Add it to `src/main/resources/config.yml` with a comment.
2. Read it with `ConfigManager.getInstance().getConfig().getXxx(key, default)` at the point of use.
3. The web/GUI schema derives from `config.yml` automatically (`ConfigSchemaService`); type is inferred
   from the value. Add an `extra(...)` entry only for keys that are not in `config.yml`. The schema is
   trimmed/padded to `TARGET_SETTING_COUNT = 250`; adding keys may push earlier extras out. Check
   `ConfigSchemaServiceTest` and the web Config tab after changes.
4. If the key must be validated in the web UI add rules in `ConfigApplyService` (types, ranges, enums).
5. Document it in DOCS.md section 18 and, if user-facing, the README.
6. Do not add a key nobody reads. (Many existing keys are unread: see DOCS.md section 18.)

### Messages

All user text goes through `messages.yml` where practical; many commands still hard-code MiniMessage
strings (`TournamentManager`, `QueueManager`, `BettingManager`, `OverrideCommand`, `CommandManager`).
New code should prefer message keys. `ConfigManager.applyMessageDefaults()` falls back to the jar's
bundled `messages.yml`.

---

## 12. Web dashboard rules

- `WebDashboardServer` registers contexts through `ctx(path, handler)` (wraps errors into a 400 JSON
  response). Add new API handlers there, authorise with `authorize(exchange)` (`?token=`), and use
  `onMain` for Bukkit access.
- Static assets (`/`, `/styles.css`, `/app.js`) are public; **no secret may be embedded in them**.
- Bind default is `127.0.0.1`. Do not change the default. Do not add endpoints that execute commands
  or read files by user-supplied path.
- Body limit `MAX_BODY_BYTES = 1 MiB`; keep validation all-or-nothing (`ConfigApplyService`) and
  validate length, charset and ranges for every new field. Arena names match
  `[A-Za-z0-9_-]{1,32}` (same rule in `CreateArenaCommand`).
- Sessions: `WebSessionManager` tokens (24 random bytes), fixed expiry, `max-sessions` cap, created at
  plugin enable (config changes to TTL/cap need a restart).
- The UI loads Phosphor icons from unpkg without SRI. To compute the hash:
  `curl -s https://unpkg.com/@phosphor-icons/web@2.1.1/src/bold/style.css | openssl dgst -sha384 -binary | openssl base64 -A`
  then add `integrity="sha384-..."` to the `<link>` in `index.html` (keep `crossorigin`).
- `web/docs.md` is a separate copy of the docs shown in the Docs tab; keep it in sync with DOCS.md
  (it is a resource file: coordinate before editing).
- The frontend polls `/api/live-matches` every 5 s while that tab is open.

---

## 13. Coding conventions

- Java 21 language level (no preview features). 4-space indent, braces on the same line.
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
- Keep optional-API imports (Vault, PlaceholderAPI, WorldGuard, LuckPerms) inside `integration/`
  classes so the plugin loads when they are absent. `BettingManager` imports Vault types directly:
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
5. Regen is not triggered automatically; do not assume arenas are restored after matches.
6. `arena.getGoalType()` is not read by the engine; use the game type.
7. `LifecycleCommand.start` does not recreate `BettingManager`, `VisualController` or listeners;
   `stop` calls `visual.shutdown()` which only clears boards/boss bars/holograms, so they remain usable.
8. `PlayerCommandPreprocessListener` intercepts `/kit` for players in arenas even when another plugin
   owns `/kit`.
9. `CommandManager.shouldUsePlayerCommand` routes `kit` for admins to the *player* command unless the
   first argument is one of `setpublic|setprivate|icon|description|cooldown|permission` (check the set
   when adding actions; the dispatcher's set is the authority: `AdminKitCommand` also implements
   `rename`, but the dispatcher does not route it, so `/oa kit rename` always hits the player command); `AdminKitCommand`'s `create`,
   `delete`, `equip`, `list` are therefore unreachable. Player-initiated equips go through
   `KitManager.tryEquip` (lobby-only inside arenas, permission, persisted cooldown): do not call
   `equipKit` directly for player actions.
10. Standalone `/list`, `/info`, `/help`, `/join`, `/leave` are registered in `plugin.yml` and can clash
    with other plugins' commands.
11. Player-map commands are permission-checked by the dispatcher using their constructor node; keep
    that node declared and default-true for anything regular players should use.
12. `ConfigSchemaService.settings()` instantiates a lot on each call (`SettingsGUI.grouped()` constructs a
    new service per call): avoid calling it in hot paths.
13. `WebSessionManager` is created once; `web.session-minutes`/`web.max-sessions` do not live-reload.
14. The `pom.xml` antrun copy step targets a Windows path.
15. `target/` is tracked: do not commit build output unintentionally; avoid editing it.
16. Whitelist/blacklist on arenas are never populated or saved (`JoinCommand` consults the whitelist
    and `omniarena.join.bypass`).
17. `RespawnType.WAVE` is a flat 1 s respawn; `respawn.delay` only applies to `DELAYED`.
18. `BlockRestoreModule` keeps `shuttingDown = true` after `cancelAll()` until the next snapshot or
    restore resets it; `/oa start` therefore works for regen again on the next call.
19. When a match has `getQueueKit()` set, loadouts come from that admin kit, otherwise from
    `GameModeKits`; respawns call the same `applyLoadout`.
20. Unread config keys exist (DOCS.md section 18): do not assume a key works because it is in
    `config.yml` or the dashboard.
21. `index.html` is a resource under `src/main/resources/web`: changes require a rebuild to be served
    (served from the jar via `plugin.getResource`).

---

## 15. Documentation upkeep

Update, in the same change as code: README.md (features, commands, config), DOCS.md (the matching
section, plus section 21 if you add/remove a limitation), this file (architecture, rules),
AGENTS.md (if a key rule changes), and for tournament/betting/web work the notes at the bottom of the
matching file in `docs/superpowers/`. Never describe a feature that is not reachable from a command,
sign, GUI or the web UI without saying so.
