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
