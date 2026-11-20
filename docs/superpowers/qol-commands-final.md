# OmniArena: final command list (QoL redesign)

Root `/oa` (aliases `/arena`, `/omniarena`). `/oa` alone opens the main menu (players) or the help index (console).
Old commands are removed with no aliases. Unknown input gets a did-you-mean with a clickable suggestion.

## Player (`omniarena.use` unless noted)

| Command | What it does |
|---------|--------------|
| `/oa menu` | Open the main menu |
| `/oa join [arena]` (`/join`) | Join an arena; no name opens the arena picker |
| `/oa spectate [arena]` (`/spectate`, `omniarena.spectate`) | Watch a match; no name opens the picker |
| `/oa leave` (`/leave`) | Leave your arena or stop spectating |
| `/oa list [arena]` | List arenas; with an arena, its details (replaces `info`) |
| `/oa stats [player]` | Kills/deaths/wins/K-D/time plus ELO and rank |
| `/oa stats top [kills\|wins\|kd\|elo] [n]` | Leaderboards (replaces `elo top`, `stats global`) |
| `/oa stats reset <player>` | Admin (`omniarena.admin.stats`): wipe a player's stats |
| `/oa queue [kit]` | Join the ranked queue (default `ranked`, or an admin kit name) |
| `/oa queue leave`, `/oa queue status` | Leave the queue, show position/ELO range |
| `/oa bet place\|cancel\|info ...` (`/bet`) | Bet on a running match |
| `/oa tournament ...` (`/tournament`) | Bracket tournaments |
| `/oa help [command]` | Clickable categorised index (admin section only for admins) |
| `/oa guide [topic]` | In-game guide books; no topic opens the index (admin topics only for admins) |
| `/party ...` | Parties |
| `/kit ...` | Personal kits (menu with no args) |
| `/pvp` | Toggle your PvP |
| `/pvp status [player]` | PvP state, combat tag, newbie protection (others: admin) |
| `/pvp newbie [checktime\|disable]` | Own newbie protection |

## Admin

| Command | What it does |
|---------|--------------|
| `/oa setup <arena>` | Create the arena if missing, give the wand, show the checklist, open the setup menu |
| `/oa setup <arena> region` | Save the wand selection as the region and take the regen snapshot |
| `/oa setup <arena> lobby\|spectator` | Set that (single) spawn to your position |
| `/oa setup <arena> spawn` / `clearspawns` | Add a battle spawn / remove all battle spawns |
| `/oa setup <arena> finish` | Check requirements and enable the arena |
| `/oa edit <arena>` | Open the edit menu |
| `/oa edit <arena> set <key> <value>` | Set any arena key (tab-complete): game-mode, players, goal-type/value, lives, chat-mode, announcements, respawn, rules, visuals, team, whitelist-enabled, region-*, `regen-auto`, `module-killstreak\|combo\|power-up`, ... |
| `/oa edit <arena> get <key>` | Read a key |
| `/oa edit <arena> whitelist\|blacklist add\|remove\|list [player]` | Access lists (`omniarena.admin.access`) |
| `/oa arena <tp\|delete\|enable\|disable> <arena>` | Per-arena actions |
| `/oa regen <arena>` / `/oa regen snapshot <arena>` | Rebuild / re-snapshot (global regen options are in settings) |
| `/oa kits` | Admin kit menu |
| `/oa kits <create\|delete\|rename\|equip\|list\|setpublic\|setprivate\|icon\|description\|cooldown\|permission> ...` | Admin preset kit actions |
| `/oa sign <create\|remove\|list>` | Arena join signs |
| `/oa leaderboard <create\|remove\|list> <id> [kills\|wins\|kd\|elo] [size]` | Leaderboard holograms in DecentHolograms or FancyHolograms (`omniarena.admin.leaderboard`) |
| `/oa npc <create\|remove\|list> <menu\|join <arena>\|queue [kit]\|spectate [arena]>` | Citizens NPCs (`omniarena.admin.npc`) |
| `/oa doctor [fix]` | Health report and fixes (`omniarena.admin.doctor`) |
| `/oa settings [set <key> <value>]` | Plugin settings |
| `/oa reload` | Reload config, messages, kits, arenas (when idle) |
| `/oa plugin <start\|stop\|restart\|version>` | Lifecycle; the only command that works while stopped |
| `/oa web [kill]` | Web dashboard link; `kill` revokes all sessions |
| `/pvp tag <p> [s]`, `untag <p>`, `global <on\|off>`, `list`, `override`, `newbie add\|remove\|checktime <p>` | Admin PvP (`omniarena.pvp.admin`) |

## Removed
`ready`, `info`, `elo`, `force`, `forcewin`, `match`, `debug`, `override` (all actions), top-level `create`, `delete`,
`enable`, `disable`, `tp`, `wand`, `region`, `spawn`, `goal`, `chat`, `announcements`, `module`, `config`, `access`,
`adminkit`, `kit`, `party`, `version`, `website`, `start`, `stop`, `restart`, `pmr`, and the standalone `/list`, `/info`,
`/help`, `/ready`, `/stats`, `/pvpstatus`, `/pvptag`, `/untag`, `/pvpglobal`, `/newbie`. Permissions `omniarena.admin.match`
and `omniarena.admin.override` are gone.
