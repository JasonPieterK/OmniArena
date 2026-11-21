# AGENTS.md: guide for coding agents working on OmniArena

OmniArena is a PVP arena plugin for Paper, Purpur, Spigot and Bukkit (Java, Maven, Bukkit API +
Adventure/MiniMessage), package `com.omniarena`, main class `com.omniarena.OmniArena`. This file is self-sufficient for common
tasks; [CLAUDE.md](CLAUDE.md) has the long-form developer guide (architecture, algorithms,
persistence formats, pitfalls) and [DOCS.md](DOCS.md) / [README.md](README.md) are the user docs.
If code and docs disagree, the code wins: fix the docs in the same change.

## Commands

```bash
mvn -q -Ppaper,mc-26.2 package          # compile + tests + shade -> target/OmniArena-paper-26.2.jar
mvn -q -Ppaper,mc-26.2 test             # unit tests only (src/test/java)
mvn -q -Ppaper,mc-26.2 -Dtest=TournamentBracketTest test
scripts/build-all.sh                    # all 24 jars into dist/ (JAVA25_HOME, JAVA21_HOME)
```

- Always pass **both** a platform profile (`paper`, `purpur`, `spigot`, `bukkit`) and a version profile
  (`mc-26.3`, `mc-26.2`, `mc-26.1` on Java 25; `mc-1.21.11`, `mc-1.21.10`, `mc-1.21.9` on Java 21):
  `mvn -P<platform>,mc-<version> package` -> `target/OmniArena-<platform>-<mcversion>.jar`
  (4 platforms x 6 versions = 24 jars). `paper` and `mc-26.2` are active by default but any explicit
  `-P` switches them off.
- Platform layer: `com.omniarena.platform` (`Platform`, `PlatformAdapter`) in `src/main/java`; the one
  `PlatformImpl` per jar is in `src/paper/java` (paper, purpur) or `src/spigot/java` (spigot, bukkit,
  with shaded + relocated Adventure and `adventure-platform-bukkit`). Never call Paper-only API from
  common code: add a method to `PlatformAdapter` and implement it in both `PlatformImpl` classes
  (e.g. `openBook` for the guide books). Code stays at Java 21 level. All 24 profile combinations compile (checked with `mvn test-compile`, 0 errors; no jars built); the unit tests run on paper/mc-26.2. Nothing has run on a live server.
- `target/` and `dist/` are git-ignored.
- Tests: 79 `@Test` methods (`grep -r "@Test" src/test | wc -l`): regen enum, bracket logic, config schema and
  migration, YAML repair and backups, error tracking, integration helpers, menu layout, did-you-mean, guide paginator and
  bundled guide books.

## Project map

| Package | What lives there |
|---------|------------------|
| `OmniArena` | entry point, all managers, `isStopped()` flag, sign refresh task, watchdog |
| `arena`, `arena.regen` | arena model/registry, setup/edit services; snapshot (tile entities included) + animated automatic/manual restore (10 styles, snapshot format v2) |
| `match`, `gamemode` | match engine (join/countdown/start/end/reset + auto regen, forfeits, draws, rewards, crash-safe player state), 16 game types and loadouts |
| `platform` | `Platform`/`PlatformAdapter`: Paper vs Spigot differences (text delivery, books, signs, chat, item bytes, chunks) |
| `commands.*` | `/oa` dispatcher (`CommandManager`: admin map + player map), `SmartErrors`, `TabUtil`, subcommands, standalone forwarders |
| `gui` | chest menus (`MenuBuilder`, `OmniMenu`, `MenuListener`, `MainMenu`, `AdminHubMenu`, `ArenaPickerMenu`, `ArenaSetupMenu`, `ArenaEditMenu`, `AdminKitMenu`, `ChatPrompt`) |
| `guide` | in-game guide books: `GuideBooks` (loads `guides.yml`), `GuidePaginator`, `GuideMarkup` |
| `health` | self-healing: config repair, tolerant YAML, watchdog, error guard, `/oa doctor` |
| `integration` | Vault, PlaceholderAPI, WorldGuard, LuckPerms, DecentHolograms, FancyHolograms, Citizens, Multiverse hooks (only place optional APIs may be used), leaderboard cache/manager, NPC manager |
| `api` | `OmniArenaAPI` facade and `api.event.*` events for other plugins |
| `kit` | preset kits (`admin-kits/`), personal kits, sharing, kit GUI |
| `queue`, `rating` | rating matchmaker (1 s tick, solo and party entries, `/oa queue`), ELO (K=32, default 1000, floor 100) |
| `tournament` | single-elimination brackets with persistence |
| `betting`, `party`, `stats`, `combat`, `sign`, `module`, `visual`, `settings` | self-explanatory systems (`/pvp` lives in `combat`) |
| `config` | `ConfigManager`, `ArenaConfig`, `ConfigMigration` (config.yml layout v2, pure and tested) |
| `web` | embedded HTTP dashboard + schema/validation; UI in `src/main/resources/web/` |
| `util` | `AsyncUtil`, `MessageUtil` (MiniMessage), `Suggest` (did-you-mean), `YamlUtil`, `LocationUtil`, ... |

The command tree is in `docs/superpowers/qol-commands-final.md`; the redesign rules in `docs/superpowers/qol-redesign-contract.md`.

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
- `/oa plugin stop` sets `stopped`, cancels *all* plugin scheduler tasks, removes players from matches,
  saves stats/arenas/tournaments, refunds bets, stops web/visuals/modules/queue/party/integrations. `/oa plugin start`
  reloads data and restarts queue, party, tournaments, web, sign task, combat tasks, integrations and the watchdog.
  `/oa plugin restart` = stop then start 20 ticks later. `/oa reload` reloads config/messages/kits/guides (arenas only
  when no match is active). Only `/oa plugin ...` works while stopped.
- Every new listener, command entry point and periodic task must check `plugin.isStopped()` (or be
  cancelled by stop and re-created by start). Periodic components expose idempotent `start()`/`stop()`; wrap their bodies in
  `ErrorGuard`.
- onDisable order: stopped=true, watchdog stop, leave matches, regen `cancelAll`, stop queue/party, save
  tournaments, refund bets, save stats/arenas/ELO, visuals, modules, web, integrations; each step in
  its own try/catch.

**Optional plugins**
- Everything touching another plugin runs through `IntegrationManager.safe(...)` (catches Throwable, switches the hook off after
  3 consecutive failures). DecentHolograms, FancyHolograms, Citizens and Multiverse are reflection only.
- OmniArena has no holograms of its own: leaderboards are PlaceholderAPI placeholders and `/oa leaderboard`.

**Matchmaking (`QueueManager`)**
- Entry point `/oa queue [kit]|leave|status` (`omniarena.queue`). 1 s task; per-kit queues
  (`ranked` or an admin kit); a party leader queues the whole party as one entry (parties need a
  2-team arena with `max-players >= 2 * size`; they meet an equal-size party or enough solo
  players). Allowed rating gap starts at 200, +50 per 10 s of waiting (longer waiter), capped at
  1000; needs a free enabled WAITING arena not reserved by a tournament; everyone is removed from the
  queue before match creation and re-queued (keeping wait time) on failure. Matches are flagged
  **ranked**; only ranked matches change ELO (win/loss, draws, forfeits; per player against the
  opposing side's average in team matches). `/oa stats` shows ratings (read-only `peekRating`, never creates entries).

**Matches**
- Win condition = `arena.getGoalType()`: the game type's goal (`KILLS` / `LAST_ALIVE`) unless the
  arena file has `goal.type` with `goal.type-override: true` (`/oa edit <arena> set goal-type`); arena `goal-value`
  overrides the default target only when > 0. Teams (when `team-enabled`) score by team. Arena
  `lives` (0-100) overrides respawn semantics. Timeout in kill modes: strict leader wins, otherwise draw. Draws and
  timeouts are `isDraw()`.
- Player *i* spawns on battle spawn *i* (modulo). Players' full state is saved on join and restored
  on leave; always leave via `MatchManager.leaveArena/leaveSpectator`. Plugin teleports use
  `markInternalTeleport`. Every way into an arena goes through `MatchManager.joinArena`.
- Regeneration is **automatic** after a match (`MatchManager.startAutoRegen`; `regen.auto`, per arena
  `regen.arenas.<name>.auto`, set with `/oa edit <arena> set regen-auto <true|false|default>`); the arena is
  `RESETTING` until it finishes. Manual: `/oa regen <arena>`, `/oa regen snapshot <arena>`. Snapshots:
  `snapshots/<arena>.yml`, `format: 2`, `palette` + `runs` (`"x,z,y0,len,idx"`) + optional `tiles` (containers, signs,
  banners; container bytes are platform specific). Styles: 10 enum values (`INSTANT`, `SPHERE` default, `RISE`, `FALL`,
  `SWEEP`, `TIME_REWIND`, `GALAXY`, `HOLOGRAM_SCAN`, `METEOR_SHOWER`, `OVERGROWTH`), old names mapped in
  `RegenAnimationType.fromConfig`.
- There is no admin match control (no force start/win, freeze, god, lockdown, debug); `MatchManager.forceWin` stays for tournaments.

**Tournaments**
- `/oa tournament create|join|leave|start|status|list|delete|setprize`. Auto-start when full; byes
  for non power-of-two fields (top seeds); one live match per arena; offline player = walkover after
  `tournaments.walkover-grace-seconds`; a safety tick every 5 s relaunches stalled matches.
- Draw/timeout without a winner: player still in arena, else higher health, else random.
- Prizes (Vault): 1st = champion, 2nd = final loser, 3rd = split evenly among semifinal losers (no
  third-place match), each rounded down to cents. Skipped when 0 or no economy.
- `tournaments.yml` persists registration **and** in-progress brackets; finished/cancelled are not
  stored. Tournament matches are not ranked.

**Betting**
- One active bet per player. Window open until `betting.bet-window-seconds` after match start. No
  winner or nobody backed the winner: refund everyone. Otherwise winners split the whole pool pro rata
  (rounded down to cents). Aborted/cleaned-up matches refund pending bets; `/oa plugin stop` and disable
  refund all.

**Config, messages, permissions**
- `config.yml` is layout version 2 (sections `general`, `arenas`, `matches`, `regen`, `ranked`, `kits`, `rewards`, `betting`,
  `tournaments`, `pvp`, `visuals`, `integrations`, `web`, `health`, `advanced`). Add keys there and read them where used; today
  every shipped key is read. The web/GUI schema is exactly the keys of the live `config.yml` + `messages.yml`.
  Renaming a key: add it to `ConfigMigration.MOVES` (the only place old names may live) and the test.
- User-facing text goes through `messages.yml` (`ConfigManager.getMessage` adds the prefix) where
  practical; formatting is MiniMessage. Never put player-typed text into a MiniMessage string:
  build `Component.text(...)` (see `ChatListener.safeText`) or escape it (`Suggest.escape`, `MenuBuilder.esc`).
- User mistakes go through `SmartErrors` (did-you-mean, usage + clickable example, unknown arena, why an arena is not joinable).
- `general.debug` gates verbose logging through `ConfigManager.debugLog(() -> ...)`; never log tokens.
- Per-arena access lists (`/oa edit <arena> whitelist|blacklist ...`, `omniarena.admin.access`) are enforced in
  `MatchManager.joinArena`; per-arena `visual-titles|sounds|particles` are combined with the global `visuals.*` keys by
  `visual/VisualGate`.
- Declare every permission in `plugin.yml`. Player subcommands are checked by the dispatcher against
  their constructor permission for non-OP players; admin subcommands via `PermissionChecker.hasAdmin`
  (console, OP, `omniarena.admin`, or the node).

**GUI and guide books**
- Menus use `MenuBuilder` (gray glass filler, Back/Help/Close footer, green/red concrete toggles); one `MenuListener` cancels clicks
  and dispatches handlers. Every menu action needs a text command twin.
- Guide books: `/oa guide [topic]`; text in `src/main/resources/guides.yml` (copied to the data folder, missing topics back-filled
  from the jar), pages by `GuidePaginator`, shorthand `{{/oa cmd <arg>}}` / `{{!/oa cmd}}` by `GuideMarkup`. `GuideBooksTest`
  checks page size, MiniMessage validity, required topics and stale commands.

**Web dashboard**
- Default bind `127.0.0.1`; token (`Authorization: Bearer`, `X-OmniArena-Token`, legacy `?token=`) on
  every `/api/*` call; sessions slide on use, are capped (`advanced.web.max-sessions`) and revocable
  (`POST /api/logout`); JSON errors and security headers on every response; body limit 1 MiB; all-or-nothing validation in `ConfigApplyService`;
  arena names `[A-Za-z0-9_-]{1,32}`. Static assets are public: never embed secrets. Do not add
  endpoints that run commands or touch arbitrary paths. Phosphor icon CSS (Bold weight only) is loaded from unpkg with an SRI hash in `index.html`; recompute it on a version bump: `curl -s https://unpkg.com/@phosphor-icons/web@2.1.1/src/bold/style.css | openssl dgst -sha384 -binary | openssl base64 -A`.

**Storage** (YAML under `plugins/OmniArena/`): `config.yml`, `messages.yml`, `guides.yml`, `arenas/<name>.yml`,
`snapshots/<arena>.yml`, `sign-glowstone.yml`, `leaderboards.yml`, `npcs.yml`, `admin-kits/`, `player-kits/<uuid>.yml`,
`kit-cooldowns.yml`, `player-states/<uuid>.yml` (crash-safe pre-arena player state), `stats.yml`,
`elo.yml`, `tournaments.yml`, `pvp-toggles.yml`, `newbie.yml`, plus `backups/` and `broken/` from the self-healing. Do not rename
persisted keys without a migration or fallback read. Load persistent data with `SafeYaml.loadData`.

## Conventions

- Java 21 source level everywhere (also for the Java 25 profiles); 4-space indent; singletons via `private static X instance` set in the
  constructor; `Optional` for lookups; arena names are case-insensitive for lookup but keep their
  original case on disk (snapshots use lower case).
- Optional-plugin imports stay in `integration/` (or use reflection); Paper-only API stays behind the
  platform layer.
- Wrap fragile shutdown work in try/catch with a log; do not swallow exceptions elsewhere.
- Prefer pure-logic unit tests; avoid MockBukkit.
- Do not add features without a user entry point (command, menu, sign, GUI, web) unless you mark them
  "no entry point yet" in the docs.
- Commits: short imperative subject (history uses `Audit fixes: ...`, `Match flow: ...`, `QoL: ...`).

## Adding things (checklist)

1. **Subcommand**: new `SubCommand` in `commands/admin` or `commands/player`; register in
   `CommandManager.registerAll`; declare permission in `plugin.yml`; add messages; update README
   command table, DOCS.md section 3 and the matching guide, the `HelpCommand` index, `qol-commands-final.md`, `guides.yml`.
2. **Config key**: add to `config.yml`, read it, document in DOCS.md section 5, validate in
   `ConfigApplyService` if editable from the web.
3. **Regen style**: enum value + `orderEntries` + `buildPreview` (+ sound) + test + docs counts.
4. **Periodic task**: idempotent `start()`/`stop()`, wire into onEnable, onDisable, `/oa plugin stop`,
   `/oa plugin start`, check `isStopped()`, wrap in `ErrorGuard`, let the watchdog restart it if it can die.
5. **Guide topic**: entry in `guides.yml`, id in `GuideBooksTest`, DOCS.md guide section.
6. **Docs**: README (features/commands), DOCS.md (matching section and limitations; then copy it to
   `src/main/resources/web/docs.md`), CLAUDE.md (architecture/rules), and the notes at the bottom of `docs/superpowers/`
   if the change deviates from them.

## Known pitfalls (short list)

- Admin preset kits are managed only through `/oa kits` (menu or text); `/kit` is the player command. Player equips go
  through `KitManager.tryEquip`.
- `regen.arenas.<name>.auto` is not in the settings schema (no GUI/dashboard entry); use the edit menu, `regen-auto`
  key, or edit the file. `RespawnType.WAVE` waves are driven by the match timer
  (`MatchManager.tickWave`) and `respawn.wave-interval` is editable via `/oa edit` and the dashboard.
- `advanced.web.session-minutes` / `advanced.web.max-sessions` are read once at plugin enable.
- The Docs tab serves `src/main/resources/web/docs.md`, which must be byte-identical to `DOCS.md`
  (`cp DOCS.md src/main/resources/web/docs.md`); keep DOCS.md free of `|` inside table cells and of stray `*`.
- `/oa plugin stop` cancels every scheduler task; auto regens that the stop starts are finished
  synchronously by `BlockRestoreModule.cancelAll()`.
- Delayed tasks must compare identity (`map.remove(key, value)`) so a stale callback cannot tear down
  a newer match or tournament.
- ELO: never both `recordWin()` and `setRating()` (double application bug, already fixed once).
- Guide book text must not use `&` colour codes (`ColorUtil.parse` translates them).
