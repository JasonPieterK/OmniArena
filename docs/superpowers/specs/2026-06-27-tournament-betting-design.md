# OmniArena: Tournament System + Betting Completion

Date: 2026-06-27

## Scope

Two deliverables:
1. **Tournament System** — full single-elimination bracket manager (new)
2. **Betting System Completion** — wire existing `BettingManager` to commands + match lifecycle

---

## 1. Tournament System

### Architecture

`TournamentManager` (standalone, mirrors `QueueManager`/`PartyManager` pattern) owns all tournament state. Registered in `OmniArena.java` alongside existing managers.

### Classes

**`com.omniarena.tournament.Tournament`**
- Fields: `name`, `arenaName`, `maxPlayers`, `state` (REGISTRATION / IN_PROGRESS / FINISHED), `participants` (List<UUID>), `bracket` (TournamentBracket), `creatorUUID`, `prizeFirst/prizeSecond/prizeThird` (double)
- State transitions: REGISTRATION → IN_PROGRESS (on start) → FINISHED (bracket complete)
- Auto-starts when `participants.size() == maxPlayers`

**`com.omniarena.tournament.TournamentBracket`**
- Generates rounds from participant list
- Handles byes: if participant count is not power-of-2, top seeds get byes in round 1
- `generateRound(List<UUID> remaining)` → list of `TournamentMatch` pairs
- `advanceWinner(UUID winner)` — removes loser, adds winner to next round pool
- `isRoundComplete()` — true when all current-round matches finished
- `getFinalWinner()` — returns UUID when bracket collapses to 1

**`com.omniarena.tournament.TournamentMatch`**
- Links a `Match` (existing) to its two tournament participants
- Tracks which participant won
- `matchId` ties back to `MatchManager` active match

**`com.omniarena.tournament.TournamentManager`**
- `Map<String, Tournament> tournaments` — name→Tournament
- `createTournament(Player creator, String name, String arenaName, int maxPlayers)` — any player can create; validates arena exists
- `joinTournament(Player player, String name)` — adds to participant list, rejects if IN_PROGRESS/FINISHED
- `leaveTournament(Player player, String name)` — only during REGISTRATION
- `startTournament(String name, Player requester)` — admin or creator; generates bracket round 1, launches first matches
- `onMatchEnd(MatchResult result)` — called by MatchManager; advances bracket, starts next matches or awards prizes
- `deleteTournament(String name, Player admin)` — admin-only; refunds any entry fees, removes tournament
- Persist to `plugins/OmniArena/tournaments.yml` (active tournaments survive reload; finished ones pruned)

### Bracket Logic (byes)

```
participants = 6
nextPow2 = 8
byes = 8 - 6 = 2  (top 2 seeds auto-advance round 1)
round1 matches = [p3 vs p6, p4 vs p5]  (p1, p2 have byes)
round2 matches = [p1 vs winner(p3/p6), p2 vs winner(p4/p5)]
```

Seeding order = join order (FIFO). No ELO-based seeding in v1.

### Commands

All under `/oa tournament` subcommand:

| Command | Who | Description |
|---|---|---|
| `create <name> <arena> <maxPlayers>` | any player | Create tournament in REGISTRATION state |
| `join <name>` | any player | Join during REGISTRATION |
| `leave <name>` | participant | Leave during REGISTRATION |
| `start <name>` | creator or admin | Force-start (min 2 players) |
| `status <name>` | any | Show bracket, current round, remaining players |
| `list` | any | List all active tournaments |
| `delete <name>` | admin | Delete and refund |
| `setprize <name> <1st/2nd/3rd> <amount>` | admin | Set Vault prize amounts |

Permission nodes:
- `omniarena.tournament.create` — default: true
- `omniarena.tournament.admin` — default: op

### Prize Payout

On bracket completion, `TournamentManager` calls `VaultIntegration.deposit()` for 1st/2nd/3rd place (if prize > 0 and Vault enabled). Falls back gracefully if Vault absent.

### Match Flow

1. `TournamentManager.startRound()` calls `MatchManager.joinArena()` for each pair
2. `MatchManager.endMatch()` fires `TournamentManager.onMatchEnd(result)` (new hook)
3. `onMatchEnd` advances bracket; if round complete → starts next round; if final done → awards prizes, sets FINISHED

### Persistence

`tournaments.yml` schema:
```yaml
tournaments:
  MyTournament:
    arena: NoDebuff
    maxPlayers: 8
    state: REGISTRATION
    creator: <uuid>
    participants:
      - <uuid>
      - <uuid>
    prizes:
      first: 5000.0
      second: 2000.0
      third: 1000.0
```

In-progress bracket state is NOT persisted (server restart during active tournament cancels it, refunds participants). REGISTRATION-state tournaments survive reload.

---

## 2. Betting System Completion

### What Exists

`BettingManager` is complete with: pool logic, Vault setup, `placeBet()`, `cancelBet()`, `processMatchResults()`, `BettingStats`. Already instantiated in `OmniArena.onEnable()`.

### What's Missing

**A) Commands** — new `BetCommand` under player commands:

| Command | Description |
|---|---|
| `/oa bet place <arena> <player> <amount>` | Place bet on player in active arena match |
| `/oa bet cancel` | Cancel current bet (refunds, match not yet started or still in countdown) |
| `/oa bet info [arena]` | Show pool size, current odds, your bet |

**B) Match-end hook** — `MatchManager.endMatch()` must call:
```java
plugin.getBettingManager().processMatchResults(arenaName, winnerId, loserId);
```

**C) Bet persistence** — on `OmniArena.onDisable()`, save active bets to `betting.yml` and refund all (bets cannot survive server restart since match state is lost). This prevents money loss on crash.

**D) Config entries** (add to `config.yml`):
```yaml
betting:
  enabled: true
  min-bet: 100.0
  max-bet: 10000.0
  allow-spectator-bet: true  # non-participants can bet
  bet-window-seconds: 30     # betting closes 30s after match starts
```

### Betting Window

After match starts, betting closes after `bet-window-seconds`. `BettingManager` tracks `matchStartTime` per arena. `placeBet()` rejects if window closed.

---

## Integration Points

- `MatchManager.endMatch()` → calls both `bettingManager.processMatchResults()` and `tournamentManager.onMatchEnd()`
- `OmniArena.onEnable()` — add `tournamentManager = new TournamentManager(this); tournamentManager.loadAll();`
- `OmniArena.onDisable()` — add `tournamentManager.saveAll(); bettingManager.refundAll();`
- `CommandManager` — register `TournamentCommand` and `BetCommand`
- `plugin.yml` — add new permission nodes and command aliases

---

## File List (new files)

```
src/main/java/com/omniarena/tournament/
  Tournament.java
  TournamentBracket.java
  TournamentMatch.java
  TournamentManager.java
  TournamentState.java

src/main/java/com/omniarena/commands/player/
  BetCommand.java

src/main/java/com/omniarena/commands/admin/
  TournamentCommand.java   (handles both player+admin subcommands)
```

## Files Modified

- `OmniArena.java` — add tournamentManager field/getter, wire onEnable/onDisable
- `MatchManager.java` — add tournament + betting hooks in endMatch()
- `CommandManager.java` — register BetCommand, TournamentCommand
- `config.yml` — add betting section defaults
- `plugin.yml` — add permissions + tournament command

---

## Status / Implementation notes

Implemented. The shipped behaviour differs from this design in the following ways (the code is
authoritative; see `DOCS.md` sections 12 and 13).

**Tournaments**
- **In-progress persistence is implemented.** The "In-progress bracket state is NOT persisted"
  decision above was reversed. `tournaments.yml` now stores participants, prizes, state and, for
  `IN_PROGRESS` tournaments, every bracket round (`bracket.rounds` as `{p1, p2?, winner?}` maps)
  and `bracket.currentRound`. On startup brackets are restored and resumed; an unusable bracket
  falls back to `REGISTRATION`. Finished and cancelled tournaments are removed, not stored. A
  `CANCELLED` state exists (set on delete). Active tournaments are saved on `/oa plugin stop` and
  `onDisable`, cleared from memory on stop, and reloaded by `/oa plugin start`.
- **Draw/timeout rule.** A tournament match that ends without a winner never stalls the bracket:
  the player still in the arena advances; if both are present the player with higher current
  health advances; otherwise a coin flip decides. (Kill-based game types additionally resolve a
  strict timeout leader to a normal win before this rule applies.)
- **Prize rule.** 1st = champion, 2nd = loser of the final, 3rd = `prizeThird` split evenly among the
  losers of the round before the final (there is no third-place match; shares are rounded down to
  cents). Prizes of 0 are skipped and, without Vault, a warning is logged and nothing is paid.
  Payment uses `VaultIntegration.depositOffline`.
- **Walkover grace and safety tick.** An offline participant gets
  `tournament.walkover-grace-seconds` (default 60) before the opponent advances by walkover. A
  100-tick task relaunches matches that had to wait or vanished without a result. A player who
  leaves the arena during a running tournament match loses.
- **Start rules.** `start` drops participants who are offline and needs at least 2 online players.
  Auto-start when the tournament is full is unchanged. While a tournament is in progress its arena
  is reserved for the current pending match.
- **Command wiring.** `TournamentCommand` lives in `commands/player/` (not `commands/admin/`) and is
  registered as the `/oa tournament` player subcommand; `delete` also accepts `cancel` and `status`
  also accepts `info`. A standalone `/tournament` command is registered in `plugin.yml` and
  forwarded to `/oa tournament`. No entry fees exist, so `delete` has nothing to refund. Tournament
  matches are not ranked (no ELO change).

**Betting**
- Active bets are not persisted to `betting.yml`; instead every bet is refunded on `/oa plugin stop` and on
  plugin disable, and pending bets are refunded when a match is cleaned up without being settled.
- A player may hold only one active bet in total (not one per arena). Cancelling is only possible
  while the betting window is open. `/oa bet info [arena]` shows the pool and bet count, not odds.
- Settlement: if there is no winner or nobody backed the winner, all bets are refunded; otherwise
  the whole pool is shared pro rata by stake (rounded down to cents). Bets cannot be placed on a
  match that is ENDING/RESETTING or not live.
- `betting.allow-spectator-bet: false` makes spectators of the arena unable to place bets
  (`BettingManager.placeBet`); `betting.enabled` and `integrations.vault` are also checked on every
  bet.
- `/oa bet` (and the standalone `/bet`) requires the permission node `omniarena.bet`, declared in
  `plugin.yml` with default true.
- With automatic arena regeneration a tournament arena is RESETTING until its rebuild finishes; the
  100-tick safety task retries launches that fail for that reason.
- QoL redesign update: tournaments are still `/oa tournament ...` and betting `/oa bet ...` (with the standalone `/tournament` and
  `/bet`); the config keys moved to `tournaments.walkover-grace-seconds` (was `tournament.walkover-grace-seconds`) and the rest of
  the betting keys are unchanged. Both are covered by the `tournament` and `betting` guide books (`/oa guide tournament`,
  `/oa guide betting`) and DOCS.md sections 2.6 and 2.7. The main menu has a Tournaments button.
