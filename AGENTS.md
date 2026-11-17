# AGENTS.md: guide for coding agents working on OmniArena

OmniArena is a Paper PVP arena plugin (Java, Maven, Bukkit API + Adventure/MiniMessage), package
`com.omniarena`, main class `com.omniarena.OmniArena`. This file is self-sufficient for common
tasks; [CLAUDE.md](CLAUDE.md) has the long-form developer guide (architecture, algorithms,
persistence formats, pitfalls) and [DOCS.md](DOCS.md) / [README.md](README.md) are the user docs.
If code and docs disagree, the code wins: fix the docs in the same change.

## Commands

```bash
mvn -q package                 # compile + tests + shade -> target/OmniArena-1.0.0.jar
mvn -q test                    # unit tests only (src/test/java)
mvn -q -Dtest=TournamentBracketTest test
```

- Current POM: Java 21, Paper API 26.2 (`26.2.build.31-alpha`), shaded FastBoard/HikariCP/Gson/MySQL.
  An antrun step copies the jar to a Windows path; ignore it, do not depend on it.
- `target/` is tracked in git; do not edit it and do not mix its changes into a logical commit.
- Planned matrix: 4 platforms (`paper`, `spigot`, `bukkit`, `purpur`) x 6 versions (`26.3`, `26.2`,
  `26.1` on Java 25; `1.21.11`, `1.21.10`, `1.21.9` on Java 21) = **24 jars** named
  `OmniArena-<platform>-<mcversion>.jar`, built with `mvn -P<platform>,mc-<version> package`. Check
  `pom.xml` for `<id>mc-...</id>` profiles before using the form; they may not exist yet. Do not add
  Paper-only API calls to new code without noting the Spigot/Bukkit consequence.

## Project map

| Package | What lives there |
|---------|------------------|
| `OmniArena` | entry point, all managers, `isStopped()` flag, sign refresh task |
| `arena`, `arena.regen` | arena model/registry; snapshot + animated restore (10 styles, snapshot format v2) |
| `match`, `gamemode` | match engine (join/countdown/start/end/reset, forfeits, draws), 16 game types and loadouts |
| `commands.*` | `/oa` dispatcher (`CommandManager`: admin map + player map), subcommands, standalone forwarders |
| `kit` | preset kits (`admin-kits/`), personal kits, sharing, kit GUI |
| `queue`, `rating` | rating matchmaker (1 s tick), ELO (K=32, default 1000, floor 100) |
| `tournament` | single-elimination brackets with persistence |
| `betting`, `party`, `stats`, `combat`, `sign`, `module`, `visual`, `settings` | self-explanatory systems |
| `web` | embedded HTTP dashboard + schema/validation; UI in `src/main/resources/web/` |
| `integration` | Vault, PlaceholderAPI, WorldGuard, LuckPerms hooks (only place optional APIs may be imported) |
| `util` | `AsyncUtil`, `MessageUtil` (MiniMessage), `YamlUtil`, `LocationUtil`, ... |

## Rules you must follow

**Threading**
- Bukkit API: main thread only. Web handlers run on a 4-thread pool and must wrap every plugin/Bukkit
  access in `onMain(...)`.
- Async saves (stats, ELO, snapshots): snapshot state on the main thread, write async, and write
  **inline** when `!plugin.isEnabled()` (shutdown). `AsyncUtil.*` returns `null` when the plugin is
  disabled; handle it.
- Never block the main thread (no `Future.get()` without timeout, no heavy IO); time-slice large
  block operations; never force-load chunks.

**Lifecycle and `isStopped()`**
- `/oa stop` sets `stopped`, cancels *all* plugin scheduler tasks, removes players from matches,
  saves stats/arenas/tournaments, refunds bets, stops web/visuals/modules/queue/party. `/oa start`
  reloads data and restarts queue, party, tournaments, web, sign task and combat tasks.
  `/oa restart` = stop then start 20 ticks later. `/oa reload` reloads config/messages (arenas only
  when no match is active).
- Every new listener, command entry point and periodic task must check `plugin.isStopped()` (or be
  cancelled by stop and re-created by start). Periodic components expose idempotent `start()`/`stop()`.
- onDisable order: stopped=true, leave matches, regen `cancelAll`, stop queue/party, save
  tournaments, refund bets, save stats/arenas/ELO, visuals, modules, web, integrations; each step in
  its own try/catch.

**Matchmaking (`QueueManager`)**
- 1 s task; per-kit queues; allowed rating gap starts at 200, +50 per 10 s of waiting (longer waiter),
  capped at 1000; needs a free enabled WAITING arena; both players removed from the queue before
  match creation, re-queued (keeping wait time) on failure. Matches are flagged **ranked**; only
  ranked matches change ELO (win/loss, draw/timeout between two players, forfeit when the leaver
  quits a running two-player ranked match). There is no player command to join the queue or to view
  ELO yet; do not document them as available.

**Matches**
- Win condition = game type's goal (`KILLS` / `LAST_ALIVE`); arena `goal-value` overrides the default
  target only when > 0; arena `goal-type` is ignored. Teams (when `team-enabled`) score by team.
  Timeout in kill modes: strict leader wins, otherwise draw. Draws and timeouts are `isDraw()`.
- Player *i* spawns on battle spawn *i* (modulo). Players' full state is saved on join and restored
  on leave; always leave via `MatchManager.leaveArena/leaveSpectator`. Plugin teleports use
  `markInternalTeleport`.
- Regeneration is manual (`/oa regen`, `/oa override purge`, end commands); nothing auto-restores
  after a match. Snapshots: `snapshots/<arena>.yml`, `format: 2`, `palette` + `runs` (`"x,z,y0,len,idx"`).
  Styles: 10 enum values, old names mapped in `RegenAnimationType.fromConfig`.

**Tournaments**
- `/oa tournament create|join|leave|start|status|list|delete|setprize`. Auto-start when full; byes
  for non power-of-two fields (top seeds); one live match per arena; offline player = walkover after
  `tournament.walkover-grace-seconds`; a safety tick every 5 s relaunches stalled matches.
- Draw/timeout without a winner: player still in arena, else higher health, else random.
- Prizes (Vault): 1st = champion, 2nd = final loser, 3rd = split evenly among semifinal losers (no
  third-place match), each rounded down to cents. Skipped when 0 or no economy.
- `tournaments.yml` persists registration **and** in-progress brackets; finished/cancelled are not
  stored. Tournament matches are not ranked.

**Betting**
- One active bet per player. Window open until `betting.bet-window-seconds` after match start. No
  winner or nobody backed the winner: refund everyone. Otherwise winners split the whole pool pro rata
  (rounded down to cents). Aborted/cleaned-up matches refund pending bets; `/oa stop` and disable
  refund all.

**Config, messages, permissions**
- Add keys to `config.yml` and read them where used; many existing keys are unread (see DOCS.md
  section 18), so verify before assuming a key works. The web/GUI schema is derived from
  `config.yml` + `messages.yml` plus built-in extras, forced to exactly 250 entries.
- User-facing text goes through `messages.yml` (`ConfigManager.getMessage` adds the prefix) where
  practical; formatting is MiniMessage.
- Declare every permission in `plugin.yml`. Player subcommands are checked by the dispatcher against
  their constructor permission for non-OP players; admin subcommands via `PermissionChecker.hasAdmin`
  (console, OP, `omniarena.admin`, or the node). Known gap: `omniarena.bet` is required by
  `/oa bet` but undeclared.

**Web dashboard**
- Default bind `127.0.0.1`; token (`?token=`) on every `/api/*` call; sessions are fixed-lifetime and
  capped (`web.max-sessions`); body limit 1 MiB; all-or-nothing validation in `ConfigApplyService`;
  arena names `[A-Za-z0-9_-]{1,32}`. Static assets are public: never embed secrets. Do not add
  endpoints that run commands or touch arbitrary paths. Phosphor icon CSS is loaded from unpkg without
  SRI; hash command: `curl -s https://unpkg.com/@phosphor-icons/web@2.1.1/src/bold/style.css | openssl dgst -sha384 -binary | openssl base64 -A`.

**Storage** (YAML under `plugins/OmniArena/`): `config.yml`, `messages.yml`, `arenas/<name>.yml`,
`snapshots/<arena>.yml`, `sign-glowstone.yml`, `admin-kits/`, `player-kits/<uuid>.yml`,
`kit-cooldowns.yml`, `player-states/<uuid>.yml` (crash-safe pre-arena player state), `stats.yml`,
`elo.yml`, `tournaments.yml`. Do not rename persisted keys without
a migration or fallback read.

## Conventions

- Java 21 source level; 4-space indent; singletons via `private static X instance` set in the
  constructor; `Optional` for lookups; arena names are case-insensitive for lookup but keep their
  original case on disk (snapshots use lower case).
- Optional-plugin imports stay in `integration/` (or use reflection).
- Wrap fragile shutdown work in try/catch with a log; do not swallow exceptions elsewhere.
- Prefer pure-logic unit tests; the existing tests cover regen enum, bracket logic and the config
  schema.
- Do not add features without a user entry point (command, sign, GUI, web) unless you mark them
  "no entry point yet" in the docs.
- Commits: short imperative subject (history uses `Audit fixes: ...`, `Match flow: ...`).

## Adding things (checklist)

1. **Subcommand**: new `SubCommand` in `commands/admin` or `commands/player`; register in
   `CommandManager.registerAll`; declare permission in `plugin.yml`; add messages; update README
   command table, DOCS.md section 15 and the `HelpCommand` guides.
2. **Config key**: add to `config.yml`, read it, document in DOCS.md section 18, validate in
   `ConfigApplyService` if editable from the web.
3. **Regen style**: enum value + `orderEntries` + `buildPreview` (+ sound) + test + docs counts.
4. **Periodic task**: idempotent `start()`/`stop()`, wire into onEnable, onDisable, `/oa stop`,
   `/oa start`, check `isStopped()`.
5. **Docs**: README (features/commands), DOCS.md (matching section and limitations), CLAUDE.md
   (architecture/rules), and the notes at the bottom of `docs/superpowers/` plans/specs if the
   change deviates from them.

## Known pitfalls (short list)

- `/oa kit create|delete|equip|list` always resolve to the player kit command (admin routing only
  covers the metadata actions); admin kits are files in `admin-kits/`.
- `arena.region-protection`, `rewards.*`, `database.*`, global `visual.*`, `integrations.*` (except
  `tab.takeover`), `performance.*`, `anticheat.*` and several `pvp.*` keys are not read.
- `web.session-minutes` / `web.max-sessions` are read once at plugin enable.
- `RegenCommand.showSettings` has a stale fallback string (`WAVE_SHUFFLE`).
- The Docs tab serves `src/main/resources/web/docs.md`, a separate copy from `DOCS.md`.
- Delayed tasks must compare identity (`map.remove(key, value)`) so a stale callback cannot tear down
  a newer match or tournament.
- ELO: never both `recordWin()` and `setRating()` (double application bug, already fixed once).
