# QoL redesign: shared contract (orchestrator spec)

Approved by the project owner. Old commands are REMOVED (no aliases). Everything in one go.

## Build
JDK 25 is installed: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn -q -Ppaper,mc-26.2 test`
Also verify the spigot flavor compiles: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn -q -Pspigot,mc-26.2 -DskipTests package`
Write Java 21 level code. Follow CLAUDE.md (platform adapter, isStopped gating, main-thread rules).

## Final command tree (root `/oa`, aliases `/arena`, `/omniarena`)

`/oa` with no args -> opens `MainMenu` for players (help text for console).

Player (permission `omniarena.use` unless noted):
- `/oa menu` -> MainMenu
- `/oa join [arena]` (no arena -> ArenaPickerMenu JOIN), `/oa leave`, `/oa spectate [arena]` (picker SPECTATE)
- `/oa list [arena]` -> list; with arena = old `info` output (info merged here)
- `/oa stats [player]` (now also shows ELO + rank), `/oa stats top [kills|wins|kd|elo] [n]` (elo top merged here)
- `/oa queue [kit]` joins, `/oa queue leave`, `/oa queue status`
- `/oa help` (clickable text index), `/oa bet ...`, `/oa tournament ...` (unchanged)
- Standalone kept: `/join`, `/leave`, `/spectate`, `/party`, `/kit`, `/bet`, `/tournament`, `/pvp`.
- REMOVED: `ready`, `info`, `elo`, standalone `/list`, `/info`, `/help`, `/ready`, `/stats`.

Admin:
- `/oa setup <arena>`: creates the arena if missing, gives the wand, opens `ArenaSetupMenu`.
  Text shortcuts: `/oa setup <arena> region|lobby|spectator|spawn|clearspawns|finish`.
- `/oa edit <arena>`: opens `ArenaEditMenu`. Text: `/oa edit <arena> set <key> <value>`,
  `/oa edit <arena> whitelist|blacklist add|remove|list [player]`.
- `/oa arena <tp|delete|enable|disable> <arena>`
- `/oa regen <arena>` (global regen options live in settings / edit menu)
- `/oa kits` -> AdminKitMenu; `/oa kits <create|delete|rename|equip|list|...> ...` (old AdminKitCommand actions)
- `/oa sign <create|remove|list>`, `/oa holo <create|list|move|remove>`
- `/oa settings`, `/oa reload`, `/oa plugin <start|stop|restart|version>`, `/oa web`
- REMOVED entirely (owner: "debug only"): force, forcewin, match, debug, override (all 15 actions),
  plus old top-level create, delete, enable, disable, tp, wand, region, spawn, goal, chat,
  announcements, module, config, access, adminkit, version, website, start, stop, restart.
  Delete unreachable state that only those commands used where it is safe (e.g. freeze/god/lockdown);
  keep internals used by tournaments/queue (e.g. forceWin used by TournamentManager).
- PvP: `/pvp` toggle, `/pvp status [player]`, `/pvp newbie [checktime|disable]`; admin
  `/pvp tag <p> [s]`, `/pvp untag <p>`, `/pvp global <on|off>`, `/pvp list`, `/pvp newbie add|remove <p>`.
  Standalone `/pvpstatus`, `/pvptag`, `/untag`, `/pvpglobal`, `/newbie` removed.

## Smart errors
`com.omniarena.util.Suggest` (pure, unit tested): `closest(String input, Collection<String> options)` (Levenshtein,
case-insensitive, max distance ~ len/3+1). Unknown subcommand -> "Unknown command. Did you mean /oa X?" with
`<click:suggest_command>`; wrong args -> usage + clickable example; unknown arena -> did-you-mean + available list;
missing setup -> say exactly what to do. Messages in messages.yml.

## GUI contract (package `com.omniarena.gui`, owned by the GUI agent)
- `MenuBuilder`: rows, title, gray stained glass filler on every empty slot, helper to place N buttons centered
  in a row, standard footer (bottom row: ARROW back slot rows*9-9+3? -> use: back = slot (last row)+0... keep
  consistent: Back bottom-left, Help (BOOK) center, Close (BARRIER) bottom-right).
- `OmniMenu implements InventoryHolder` with slot -> click handler; one `MenuListener` (cancels clicks/drags,
  checks isStopped).
- Toggles: LIME_CONCRETE = true/on, RED_CONCRETE = false/off. Every other button a distinct, meaningful item.
- Public entry points (exact signatures, other agents call these):
  - `MainMenu.open(Player)`
  - `ArenaPickerMenu.open(Player, ArenaPickerMenu.Mode)` with `enum Mode { JOIN, SPECTATE }`
  - `ArenaSetupMenu.open(Player, Arena)`
  - `ArenaEditMenu.open(Player, Arena)`
  - `AdminKitMenu.open(Player)`
  - `SettingsGUI` / `KitGUI` keep their existing public open methods but are redesigned onto MenuBuilder.
- Text through PlatformAdapter (item name/lore, inventory title).

## Final phase (guide books and docs)
- `/oa guide [topic]` (`omniarena.use`): in-game written books built from MiniMessage pages. Topics: start, arena, play, kits,
  ranked, tournament, betting, regen, leaderboards, npc, pvp, doctor, config; the admin ones (start, arena, regen, leaderboards,
  npc, doctor, config) are only listed to admins. No topic opens an index book with clickable topic links. A "Guides" button
  exists in the main menu and the admin hub, and `/oa help` links to it.
- `PlatformAdapter.openBook(Player, String title, String author, List<Component> pages)` is implemented in both
  `PlatformImpl` classes. Book text lives in the bundled `guides.yml` (copied to the data folder, missing topics back-filled
  from the jar); `GuidePaginator` (pure, tested) splits long text into 14 x 19 pages.
- `DOCS.md` was rewritten for the new plugin and is mirrored byte-identically to `src/main/resources/web/docs.md`; README,
  AGENTS.md and CLAUDE.md were updated and CLAUDE.md was deduplicated. All references to removed commands and old config paths
  were fixed (the old forms appear only in `ConfigMigration`, its test and the migration table in DOCS.md section 5.2).
