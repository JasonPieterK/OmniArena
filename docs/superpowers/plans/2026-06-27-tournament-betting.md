# Tournament + Betting Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a full single-elimination tournament system and wire the existing BettingManager to commands, config, and the match lifecycle.

**Architecture:** `TournamentManager` (standalone, mirrors `QueueManager`/`PartyManager`) owns bracket state. `MatchManager.endMatch()` fires both `bettingManager.processMatchResults()` and `tournamentManager.onMatchEnd()`. Betting commands are a new `BetCommand` SubCommand registered in `CommandManager`.

**Tech Stack:** Java 21, Paper 1.21, Vault economy API, existing `SubCommand` pattern, YAML persistence via `YamlUtil`/`ConfigManager`.

---

## File Map

### New Files
```
src/main/java/com/omniarena/tournament/TournamentState.java
src/main/java/com/omniarena/tournament/Tournament.java
src/main/java/com/omniarena/tournament/TournamentBracket.java
src/main/java/com/omniarena/tournament/TournamentMatch.java
src/main/java/com/omniarena/tournament/TournamentManager.java
src/main/java/com/omniarena/commands/player/BetCommand.java
src/main/java/com/omniarena/commands/admin/TournamentCommand.java
src/test/java/com/omniarena/tournament/TournamentBracketTest.java
src/test/java/com/omniarena/betting/BettingManagerTest.java
```

### Modified Files
```
src/main/java/com/omniarena/match/MatchResult.java          — add getLoserId()
src/main/java/com/omniarena/match/MatchManager.java         — hook endMatch() for betting + tournament
src/main/java/com/omniarena/OmniArena.java                  — add tournamentManager field, wire enable/disable
src/main/java/com/omniarena/commands/CommandManager.java    — register BetCommand, TournamentCommand
src/main/resources/config.yml                               — add betting section defaults
src/main/resources/plugin.yml                               — add tournament permission nodes
```

---

## Task 1: Add `getLoserId()` to MatchResult

`MatchManager.endMatch()` already calls `result.getLoserId()` (line 298) but `MatchResult` has no such method. Fix it.

**Files:**
- Modify: `src/main/java/com/omniarena/match/MatchResult.java`

- [ ] **Step 1: Open MatchResult and add the field + getter**

```java
// Add field after participants
private final UUID loserId;   // null if draw/team match/timeout

// Update constructor (add loserId param)
public MatchResult(String arenaName, UUID winnerId, UUID loserId, String winnerTeamName,
                   Outcome outcome, long durationSeconds, List<UUID> participants) {
    this.arenaName = arenaName;
    this.winnerId = winnerId;
    this.loserId = loserId;
    this.winnerTeamName = winnerTeamName;
    this.outcome = outcome;
    this.durationSeconds = durationSeconds;
    this.participants = participants;
}

// Add getter
public UUID getLoserId() { return loserId; }
```

- [ ] **Step 2: Fix all MatchResult constructor call sites in MatchManager.java**

Search for `new MatchResult(` — there are 5 call sites. Each currently passes 5 args; add `null` as the new `loserId` (3rd arg) for all except `LAST_ALIVE` and `KILLS` goals:

In `checkEndConditions` — `LAST_ALIVE` branch (alive.size() == 1), the loser is `alive.get(0)` ... wait, winner is `alive.get(0)`, so we need the other player. For team/FFA the loser isn't always one UUID. Pass `null` for now on team modes; for 1v1 solo derive it from participants:

```java
// LAST_ALIVE win — 1v1 case: find the single other participant
UUID winnerUuid = alive.get(0);
UUID loserUuid = match.getPlayers().stream()
        .filter(u -> !u.equals(winnerUuid))
        .findFirst().orElse(null);
MatchResult result = new MatchResult(arena.getName(), winnerUuid, loserUuid, null,
        MatchResult.Outcome.WIN, match.getDurationSeconds(),
        new ArrayList<>(match.getPlayers()));
endMatch(match, result);
```

```java
// KILLS win
UUID leaderUuid = leader; // already UUID
UUID loserUuid = match.getPlayers().stream()
        .filter(u -> !u.equals(leaderUuid))
        .findFirst().orElse(null);
MatchResult result = new MatchResult(arena.getName(), leaderUuid, loserUuid, null,
        MatchResult.Outcome.WIN, match.getDurationSeconds(),
        new ArrayList<>(match.getPlayers()));
endMatch(match, result);
```

```java
// forceWin
MatchResult result = new MatchResult(
        match.getArena().getName(), winnerId, null, null,
        MatchResult.Outcome.WIN, match.getDurationSeconds(),
        new ArrayList<>(match.getPlayers()));
```

```java
// forceWinTeam
MatchResult result = new MatchResult(
        match.getArena().getName(), null, null, teamName,
        MatchResult.Outcome.WIN, match.getDurationSeconds(),
        new ArrayList<>(match.getPlayers()));
```

```java
// buildTimeoutResult
return new MatchResult(match.getArena().getName(), null, null, null,
        MatchResult.Outcome.TIMEOUT, match.getDurationSeconds(),
        new ArrayList<>(match.getPlayers()));

// buildDrawResult
return new MatchResult(match.getArena().getName(), null, null, null,
        MatchResult.Outcome.DRAW, match.getDurationSeconds(),
        new ArrayList<>(match.getPlayers()));
```

- [ ] **Step 3: Verify project compiles**

```bash
cd "OmniArena - Complete PVP Arena Plugin (Paper 26.2)"
mvn compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/omniarena/match/MatchResult.java \
        src/main/java/com/omniarena/match/MatchManager.java
git commit -m "fix: add loserId to MatchResult, fix all constructor call sites"
```

---

## Task 2: Betting config defaults + BettingManager betting-window support

**Files:**
- Modify: `src/main/resources/config.yml`
- Modify: `src/main/java/com/omniarena/betting/BettingManager.java`

- [ ] **Step 1: Add betting section to config.yml**

Open `src/main/resources/config.yml` and add at the bottom:

```yaml
betting:
  enabled: true
  min-bet: 100.0
  max-bet: 10000.0
  allow-spectator-bet: true
  bet-window-seconds: 30
```

- [ ] **Step 2: Add match-start-time tracking + window check to BettingManager**

Add a field and two methods to `BettingManager`:

```java
// Field (add next to matchBets map)
private final Map<String, Long> matchStartTimes = new ConcurrentHashMap<>(); // arenaName -> start millis

// Call this from MatchManager.startMatch()
public void onMatchStart(String arenaName) {
    matchStartTimes.put(arenaName, System.currentTimeMillis());
}

// Call this from MatchManager.cleanupMatch() / resetMatch()
public void onMatchCleanup(String arenaName) {
    matchStartTimes.remove(arenaName);
}

private boolean isBettingWindowOpen(String arenaName) {
    Long startTime = matchStartTimes.get(arenaName);
    if (startTime == null) return true; // match not started yet — window open
    int windowSeconds = plugin.getConfigManager().getConfig()
            .getInt("betting.bet-window-seconds", 30);
    return System.currentTimeMillis() - startTime < windowSeconds * 1000L;
}
```

- [ ] **Step 3: Guard placeBet with window check and config-enabled check**

At the top of `placeBet()`, after the `!enabled` check, add:

```java
if (!plugin.getConfigManager().getConfig().getBoolean("betting.enabled", true)) {
    MessageUtil.send(player, "<red>Betting is disabled on this server.");
    return false;
}

if (!isBettingWindowOpen(arenaName)) {
    MessageUtil.send(player, "<red>Betting window closed for this match.");
    return false;
}
```

- [ ] **Step 4: Add refundAll() for server shutdown**

```java
public void refundAll() {
    if (economy == null) return;
    for (Bet bet : activeBets.values()) {
        economy.depositPlayer(bet.getPlayerUuid(), bet.getAmount());
        Player p = Bukkit.getPlayer(bet.getPlayerUuid());
        if (p != null) MessageUtil.send(p, "<yellow>Server shutting down — bet refunded: <green>" +
                economy.format(bet.getAmount()));
    }
    activeBets.clear();
    matchBets.clear();
    matchStartTimes.clear();
}
```

- [ ] **Step 5: Compile check**

```bash
mvn compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/config.yml \
        src/main/java/com/omniarena/betting/BettingManager.java
git commit -m "feat: betting config defaults, window enforcement, refundAll on shutdown"
```

---

## Task 3: Wire BettingManager into MatchManager lifecycle

**Files:**
- Modify: `src/main/java/com/omniarena/match/MatchManager.java`

- [ ] **Step 1: Wire `onMatchStart` — call after `match.setStartTime(...)` in `startMatch()`**

In `MatchManager.startMatch()`, after `match.setStartTime(System.currentTimeMillis());`:

```java
plugin.getBettingManager().onMatchStart(arena.getName());
```

- [ ] **Step 2: Wire `processMatchResults` — call in `endMatch()` after ELO update**

In `MatchManager.endMatch()`, after the ELO block (after line ~303), add:

```java
// Betting payout
plugin.getBettingManager().processMatchResults(
        match.getArena().getName(), result.getWinnerId(), result.getLoserId());
```

- [ ] **Step 3: Wire `onMatchCleanup` — call in `cleanupMatch()`**

In `MatchManager.cleanupMatch()`:

```java
plugin.getBettingManager().onMatchCleanup(match.getArena().getName());
```

- [ ] **Step 4: Compile check**

```bash
mvn compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/omniarena/match/MatchManager.java
git commit -m "feat: wire betting lifecycle hooks into MatchManager (start/end/cleanup)"
```

---

## Task 4: BetCommand player command

**Files:**
- Create: `src/main/java/com/omniarena/commands/player/BetCommand.java`

- [ ] **Step 1: Write BetCommand**

```java
package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.betting.BettingManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class BetCommand extends SubCommand {

    private final OmniArena plugin;

    public BetCommand(OmniArena plugin) {
        super("omniarena.bet", "/oa bet <place|cancel|info> [arena] [player] [amount]", "Bet on arena match outcomes");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "<red>Players only.");
            return;
        }

        BettingManager bm = plugin.getBettingManager();

        if (!bm.isEnabled()) {
            MessageUtil.send(player, "<red>Betting is not available (no economy plugin).");
            return;
        }

        if (args.length == 0) {
            sendUsage(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "place" -> handlePlace(player, bm, args);
            case "cancel" -> bm.cancelBet(player);
            case "info" -> handleInfo(player, bm, args);
            default -> sendUsage(player);
        }
    }

    private void handlePlace(Player player, BettingManager bm, String[] args) {
        // /oa bet place <arena> <targetPlayer> <amount>
        if (args.length < 4) {
            MessageUtil.send(player, "<red>Usage: /oa bet place <arena> <player> <amount>");
            return;
        }
        String arenaName = args[1];
        Arena arena = plugin.getArenaManager().get(arenaName);
        if (arena == null) {
            MessageUtil.send(player, "<red>Arena not found: <yellow>" + arenaName);
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            MessageUtil.send(player, "<red>Player not found or offline: <yellow>" + args[2]);
            return;
        }

        if (!plugin.getMatchManager().isInArena(target)) {
            MessageUtil.send(player, "<red>" + target.getName() + " is not in an arena.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            MessageUtil.send(player, "<red>Invalid amount: <yellow>" + args[3]);
            return;
        }

        bm.placeBet(player, arenaName, target.getUniqueId(), amount);
    }

    private void handleInfo(Player player, BettingManager bm, String[] args) {
        // /oa bet info [arena]
        BettingManager.Bet myBet = bm.getPlayerBet(player);
        if (myBet != null) {
            String winnerName = Bukkit.getOfflinePlayer(myBet.getPredictedWinner()).getName();
            MessageUtil.send(player, "<gold>=== Your Active Bet ===");
            MessageUtil.send(player, "<yellow>Arena: <white>" + myBet.getArenaName());
            MessageUtil.send(player, "<yellow>Bet on: <white>" + winnerName);
            MessageUtil.send(player, "<yellow>Amount: <white>" + bm.getEconomy().format(myBet.getAmount()));
        } else {
            MessageUtil.send(player, "<gray>You have no active bets.");
        }

        if (args.length >= 2) {
            String arenaName = args[1];
            BettingManager.BettingStats stats = bm.getMatchStats(arenaName);
            MessageUtil.send(player, "<gold>=== Bet Pool: " + arenaName + " ===");
            MessageUtil.send(player, "<yellow>Total bets: <white>" + stats.getTotalBets());
            MessageUtil.send(player, "<yellow>Pool size: <white>" + bm.getEconomy().format(stats.getTotalPool()));
        }
    }

    private void sendUsage(Player player) {
        MessageUtil.send(player, "<gold>=== /oa bet ===");
        MessageUtil.send(player, "<yellow>/oa bet place <arena> <player> <amount></yellow> <gray>- place a bet");
        MessageUtil.send(player, "<yellow>/oa bet cancel</yellow> <gray>- cancel your bet (refunded)");
        MessageUtil.send(player, "<yellow>/oa bet info [arena]</yellow> <gray>- view your bet and pool info");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("place", "cancel", "info").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("place") || args[0].equalsIgnoreCase("info")))
            return plugin.getArenaManager().getAll().stream()
                    .map(Arena::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("place"))
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase())).toList();
        return List.of();
    }
}
```

- [ ] **Step 2: Register BetCommand in CommandManager.java**

In `CommandManager.registerAll()`, add after the `"help"` registration:

```java
reg(playerCommands, "bet", new BetCommand(plugin));
```

- [ ] **Step 3: Compile check**

```bash
mvn compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/omniarena/commands/player/BetCommand.java \
        src/main/java/com/omniarena/commands/CommandManager.java
git commit -m "feat: add /oa bet command (place/cancel/info)"
```

---

## Task 5: Wire BettingManager shutdown into OmniArena.onDisable()

**Files:**
- Modify: `src/main/java/com/omniarena/OmniArena.java`

- [ ] **Step 1: Add refundAll call in onDisable()**

In `OmniArena.onDisable()`, after `statsManager.saveAll()` and `arenaManager.saveAll()`:

```java
if (bettingManager != null) bettingManager.refundAll();
```

- [ ] **Step 2: Compile check**

```bash
mvn compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/omniarena/OmniArena.java
git commit -m "feat: refund all active bets on server shutdown"
```

---

## Task 6: Tournament core data classes

**Files:**
- Create: `src/main/java/com/omniarena/tournament/TournamentState.java`
- Create: `src/main/java/com/omniarena/tournament/TournamentMatch.java`
- Create: `src/main/java/com/omniarena/tournament/Tournament.java`

- [ ] **Step 1: Write TournamentState enum**

```java
package com.omniarena.tournament;

public enum TournamentState {
    REGISTRATION,
    IN_PROGRESS,
    FINISHED,
    CANCELLED
}
```

- [ ] **Step 2: Write TournamentMatch**

```java
package com.omniarena.tournament;

import java.util.UUID;

public class TournamentMatch {

    private final UUID player1;
    private final UUID player2;   // null = bye (player1 auto-advances)
    private final String arenaName;
    private final int round;
    private UUID winner;
    private boolean completed;

    public TournamentMatch(UUID player1, UUID player2, String arenaName, int round) {
        this.player1 = player1;
        this.player2 = player2;
        this.arenaName = arenaName;
        this.round = round;
        this.completed = (player2 == null); // bye auto-completes
        this.winner = (player2 == null) ? player1 : null;
    }

    public UUID getPlayer1()  { return player1; }
    public UUID getPlayer2()  { return player2; }
    public String getArenaName() { return arenaName; }
    public int getRound()     { return round; }
    public UUID getWinner()   { return winner; }
    public boolean isBye()    { return player2 == null; }
    public boolean isCompleted() { return completed; }

    public void setWinner(UUID winner) {
        this.winner = winner;
        this.completed = true;
    }

    /** True if this match involves the given player (either slot). */
    public boolean involves(UUID uuid) {
        return uuid.equals(player1) || uuid.equals(player2);
    }
}
```

- [ ] **Step 3: Write Tournament**

```java
package com.omniarena.tournament;

import java.util.*;

public class Tournament {

    private final String name;
    private final String arenaName;
    private final int maxPlayers;
    private final UUID creatorUuid;
    private final List<UUID> participants = new ArrayList<>();
    private TournamentState state = TournamentState.REGISTRATION;
    private TournamentBracket bracket;
    private double prizeFirst  = 0;
    private double prizeSecond = 0;
    private double prizeThird  = 0;
    private final long createdAt = System.currentTimeMillis();

    public Tournament(String name, String arenaName, int maxPlayers, UUID creatorUuid) {
        this.name = name;
        this.arenaName = arenaName;
        this.maxPlayers = maxPlayers;
        this.creatorUuid = creatorUuid;
    }

    public String getName()       { return name; }
    public String getArenaName()  { return arenaName; }
    public int getMaxPlayers()    { return maxPlayers; }
    public UUID getCreatorUuid()  { return creatorUuid; }
    public List<UUID> getParticipants() { return participants; }
    public TournamentState getState()   { return state; }
    public void setState(TournamentState state) { this.state = state; }
    public TournamentBracket getBracket() { return bracket; }
    public void setBracket(TournamentBracket bracket) { this.bracket = bracket; }
    public double getPrizeFirst()  { return prizeFirst; }
    public double getPrizeSecond() { return prizeSecond; }
    public double getPrizeThird()  { return prizeThird; }
    public void setPrizeFirst(double v)  { prizeFirst = v; }
    public void setPrizeSecond(double v) { prizeSecond = v; }
    public void setPrizeThird(double v)  { prizeThird = v; }
    public long getCreatedAt()     { return createdAt; }

    public boolean isFull()   { return participants.size() >= maxPlayers; }
    public boolean hasPlayer(UUID uuid) { return participants.contains(uuid); }

    public boolean addParticipant(UUID uuid) {
        if (state != TournamentState.REGISTRATION) return false;
        if (isFull()) return false;
        if (hasPlayer(uuid)) return false;
        participants.add(uuid);
        return true;
    }

    public boolean removeParticipant(UUID uuid) {
        if (state != TournamentState.REGISTRATION) return false;
        return participants.remove(uuid);
    }
}
```

- [ ] **Step 4: Compile check**

```bash
mvn compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/omniarena/tournament/
git commit -m "feat: tournament core data classes (TournamentState, TournamentMatch, Tournament)"
```

---

## Task 7: TournamentBracket with bye support

**Files:**
- Create: `src/main/java/com/omniarena/tournament/TournamentBracket.java`
- Create: `src/test/java/com/omniarena/tournament/TournamentBracketTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.omniarena.tournament;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TournamentBracketTest {

    private static List<UUID> uuids(int n) {
        List<UUID> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(UUID.randomUUID());
        return list;
    }

    @Test
    void powerOfTwo_noByesGenerated() {
        List<UUID> players = uuids(4);
        TournamentBracket bracket = new TournamentBracket(players, "arena");
        List<TournamentMatch> round1 = bracket.getCurrentRound();
        // 4 players → 2 matches, 0 byes
        assertEquals(2, round1.size());
        assertTrue(round1.stream().noneMatch(TournamentMatch::isBye));
    }

    @Test
    void sixPlayers_twoByes() {
        List<UUID> players = uuids(6);
        TournamentBracket bracket = new TournamentBracket(players, "arena");
        List<TournamentMatch> round1 = bracket.getCurrentRound();
        // next power of 2 above 6 is 8 → 2 byes, so 4 "matches" in round1: 2 byes + 2 real
        assertEquals(4, round1.size());
        long byeCount = round1.stream().filter(TournamentMatch::isBye).count();
        assertEquals(2, byeCount);
    }

    @Test
    void advanceWinner_roundCompletes() {
        List<UUID> players = uuids(4);
        TournamentBracket bracket = new TournamentBracket(players, "arena");
        List<TournamentMatch> round1 = bracket.getCurrentRound();

        UUID w1 = round1.get(0).getPlayer1();
        UUID w2 = round1.get(1).getPlayer1();
        bracket.recordWinner(round1.get(0), w1);
        bracket.recordWinner(round1.get(1), w2);

        assertTrue(bracket.isRoundComplete());
        assertFalse(bracket.isFinished());

        bracket.advanceRound();
        List<TournamentMatch> round2 = bracket.getCurrentRound();
        assertEquals(1, round2.size());
        assertTrue(round2.get(0).involves(w1));
        assertTrue(round2.get(0).involves(w2));
    }

    @Test
    void twoPlayers_finishesInOneRound() {
        List<UUID> players = uuids(2);
        TournamentBracket bracket = new TournamentBracket(players, "arena");
        TournamentMatch final_ = bracket.getCurrentRound().get(0);
        UUID winner = final_.getPlayer1();
        bracket.recordWinner(final_, winner);
        assertTrue(bracket.isRoundComplete());
        assertTrue(bracket.isFinished());
        assertEquals(winner, bracket.getFinalWinner());
    }

    @Test
    void getMatchForArena_returnsCorrectMatch() {
        List<UUID> players = uuids(4);
        TournamentBracket bracket = new TournamentBracket(players, "arena");
        Optional<TournamentMatch> match = bracket.getActiveMatchForArena("arena");
        // There are 2 matches for same arena name in round1 (they queue sequentially)
        // First one is returned
        assertTrue(match.isPresent());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -pl . -Dtest=TournamentBracketTest -q 2>&1 | tail -20
```
Expected: compilation error or `TournamentBracket` not found.

- [ ] **Step 3: Write TournamentBracket**

```java
package com.omniarena.tournament;

import java.util.*;

public class TournamentBracket {

    private final String arenaName;
    private final List<List<TournamentMatch>> rounds = new ArrayList<>();
    private int currentRoundIndex = 0;

    public TournamentBracket(List<UUID> participants, String arenaName) {
        this.arenaName = arenaName;
        List<UUID> seeded = new ArrayList<>(participants);
        generateRound1(seeded);
    }

    private void generateRound1(List<UUID> seeded) {
        int n = seeded.size();
        int nextPow2 = nextPowerOfTwo(n);
        int byes = nextPow2 - n;

        // First 'byes' seeds get bye matches; remainder are paired
        List<TournamentMatch> round = new ArrayList<>();
        int idx = 0;
        for (int i = 0; i < byes; i++) {
            round.add(new TournamentMatch(seeded.get(idx++), null, arenaName, 1));
        }
        while (idx < seeded.size()) {
            UUID p1 = seeded.get(idx++);
            UUID p2 = seeded.get(idx++);
            round.add(new TournamentMatch(p1, p2, arenaName, 1));
        }
        rounds.add(round);
    }

    private static int nextPowerOfTwo(int n) {
        if (n <= 1) return 1;
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    public List<TournamentMatch> getCurrentRound() {
        if (currentRoundIndex >= rounds.size()) return List.of();
        return Collections.unmodifiableList(rounds.get(currentRoundIndex));
    }

    public int getCurrentRoundNumber() { return currentRoundIndex + 1; }

    public boolean isRoundComplete() {
        return getCurrentRound().stream().allMatch(TournamentMatch::isCompleted);
    }

    public boolean isFinished() {
        return isRoundComplete() && getWinners().size() == 1;
    }

    public UUID getFinalWinner() {
        if (!isFinished()) return null;
        return getWinners().get(0);
    }

    public void recordWinner(TournamentMatch match, UUID winner) {
        match.setWinner(winner);
    }

    /** Call after isRoundComplete() is true to generate next round. */
    public void advanceRound() {
        List<UUID> winners = getWinners();
        if (winners.size() <= 1) return; // finished — no next round
        currentRoundIndex++;
        int roundNum = currentRoundIndex + 1;
        List<TournamentMatch> next = new ArrayList<>();
        for (int i = 0; i < winners.size() - 1; i += 2) {
            next.add(new TournamentMatch(winners.get(i), winners.get(i + 1), arenaName, roundNum));
        }
        // Odd winner out gets a bye (shouldn't happen if power-of-2 seeding correct, but safety)
        if (winners.size() % 2 == 1) {
            next.add(new TournamentMatch(winners.get(winners.size() - 1), null, arenaName, roundNum));
        }
        rounds.add(next);
    }

    private List<UUID> getWinners() {
        List<TournamentMatch> round = getCurrentRound();
        List<UUID> winners = new ArrayList<>();
        for (TournamentMatch m : round) {
            if (m.getWinner() != null) winners.add(m.getWinner());
        }
        return winners;
    }

    /** Returns the first incomplete, non-bye match in current round for given arena. */
    public Optional<TournamentMatch> getActiveMatchForArena(String arena) {
        return getCurrentRound().stream()
                .filter(m -> !m.isBye() && !m.isCompleted() && m.getArenaName().equals(arena))
                .findFirst();
    }

    /** Returns incomplete non-bye matches ready to be started. */
    public List<TournamentMatch> getPendingMatches() {
        return getCurrentRound().stream()
                .filter(m -> !m.isBye() && !m.isCompleted())
                .toList();
    }

    public List<List<TournamentMatch>> getAllRounds() {
        return Collections.unmodifiableList(rounds);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -pl . -Dtest=TournamentBracketTest -q 2>&1 | tail -10
```
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/omniarena/tournament/TournamentBracket.java \
        src/test/java/com/omniarena/tournament/TournamentBracketTest.java
git commit -m "feat: TournamentBracket with single-elimination + bye support (tested)"
```

---

## Task 8: TournamentManager

**Files:**
- Create: `src/main/java/com/omniarena/tournament/TournamentManager.java`

- [ ] **Step 1: Write TournamentManager**

```java
package com.omniarena.tournament;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.match.Match;
import com.omniarena.match.MatchResult;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TournamentManager {

    private final OmniArena plugin;
    private final Map<String, Tournament> tournaments = new ConcurrentHashMap<>();
    private final File dataFile;

    public TournamentManager(OmniArena plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tournaments.yml");
    }

    public void start() {
        loadAll();
    }

    // ── Create / Join / Leave / Delete ────────────────────────────

    public boolean createTournament(Player creator, String name, String arenaName, int maxPlayers) {
        if (tournaments.containsKey(name.toLowerCase())) {
            MessageUtil.send(creator, "<red>Tournament '<yellow>" + name + "</yellow>' already exists.");
            return false;
        }
        Arena arena = plugin.getArenaManager().get(arenaName);
        if (arena == null) {
            MessageUtil.send(creator, "<red>Arena not found: <yellow>" + arenaName);
            return false;
        }
        if (maxPlayers < 2) {
            MessageUtil.send(creator, "<red>Tournament needs at least 2 players.");
            return false;
        }

        Tournament t = new Tournament(name, arenaName, maxPlayers, creator.getUniqueId());
        tournaments.put(name.toLowerCase(), t);
        saveAll();

        Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                "<gold>[Tournament] <yellow>" + creator.getName() +
                " created tournament <white>" + name +
                " <gray>(" + arenaName + ", max " + maxPlayers + "). Use /oa tournament join " + name));
        return true;
    }

    public boolean joinTournament(Player player, String name) {
        Tournament t = getTournament(name);
        if (t == null) {
            MessageUtil.send(player, "<red>Tournament not found: <yellow>" + name);
            return false;
        }
        if (t.getState() != TournamentState.REGISTRATION) {
            MessageUtil.send(player, "<red>Tournament is not accepting registrations.");
            return false;
        }
        if (!t.addParticipant(player.getUniqueId())) {
            if (t.hasPlayer(player.getUniqueId()))
                MessageUtil.send(player, "<red>Already registered.");
            else
                MessageUtil.send(player, "<red>Tournament is full.");
            return false;
        }

        MessageUtil.send(player, "<green>Joined tournament <yellow>" + t.getName() +
                "</yellow>. Players: " + t.getParticipants().size() + "/" + t.getMaxPlayers());

        if (t.isFull()) {
            startTournament(t, null);
        } else {
            saveAll();
        }
        return true;
    }

    public boolean leaveTournament(Player player, String name) {
        Tournament t = getTournament(name);
        if (t == null) {
            MessageUtil.send(player, "<red>Tournament not found: <yellow>" + name);
            return false;
        }
        if (!t.removeParticipant(player.getUniqueId())) {
            MessageUtil.send(player, "<red>Not registered or tournament already started.");
            return false;
        }
        MessageUtil.send(player, "<yellow>Left tournament <white>" + t.getName() + "</white>.");
        saveAll();
        return true;
    }

    public void startTournament(Tournament t, Player requester) {
        if (t.getState() != TournamentState.REGISTRATION) {
            if (requester != null) MessageUtil.send(requester, "<red>Tournament already started.");
            return;
        }
        if (t.getParticipants().size() < 2) {
            if (requester != null) MessageUtil.send(requester, "<red>Need at least 2 players.");
            return;
        }

        t.setState(TournamentState.IN_PROGRESS);
        t.setBracket(new TournamentBracket(t.getParticipants(), t.getArenaName()));

        Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                "<gold>[Tournament] <white>" + t.getName() + " <yellow>has started! " +
                t.getParticipants().size() + " players competing."));

        launchPendingMatches(t);
    }

    public boolean deleteTournament(String name, Player admin) {
        Tournament t = getTournament(name);
        if (t == null) {
            if (admin != null) MessageUtil.send(admin, "<red>Tournament not found: <yellow>" + name);
            return false;
        }
        t.setState(TournamentState.CANCELLED);
        tournaments.remove(name.toLowerCase());
        saveAll();
        if (admin != null) MessageUtil.send(admin, "<green>Tournament <yellow>" + name + "</yellow> deleted.");
        return true;
    }

    // ── Bracket progression ───────────────────────────────────────

    /**
     * Called by MatchManager.endMatch() for every match result.
     * Checks if this match belongs to any active tournament and advances the bracket.
     */
    public void onMatchEnd(MatchResult result) {
        if (result.getWinnerId() == null) return; // draw/timeout — skip

        for (Tournament t : tournaments.values()) {
            if (t.getState() != TournamentState.IN_PROGRESS) continue;
            TournamentBracket bracket = t.getBracket();

            Optional<TournamentMatch> matchOpt = bracket.getActiveMatchForArena(result.getArenaName());
            if (matchOpt.isEmpty()) continue;

            TournamentMatch tm = matchOpt.get();
            if (!tm.involves(result.getWinnerId()) && !tm.involves(result.getLoserId())) continue;

            bracket.recordWinner(tm, result.getWinnerId());
            String winnerName = Bukkit.getOfflinePlayer(result.getWinnerId()).getName();
            Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                    "<gold>[Tournament] <white>" + t.getName() +
                    " R" + bracket.getCurrentRoundNumber() +
                    ": <yellow>" + winnerName + " <gray>advances!"));

            if (bracket.isRoundComplete()) {
                if (bracket.isFinished()) {
                    finishTournament(t);
                } else {
                    bracket.advanceRound();
                    Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                            "<gold>[Tournament] <white>" + t.getName() +
                            " <yellow>Round " + bracket.getCurrentRoundNumber() + " begins!"));
                    launchPendingMatches(t);
                }
            } else {
                launchPendingMatches(t);
            }
            return;
        }
    }

    private void launchPendingMatches(Tournament t) {
        Arena arena = plugin.getArenaManager().get(t.getArenaName());
        if (arena == null) return;

        for (TournamentMatch tm : t.getBracket().getPendingMatches()) {
            Player p1 = Bukkit.getPlayer(tm.getPlayer1());
            Player p2 = Bukkit.getPlayer(tm.getPlayer2());

            if (p1 == null || p2 == null) {
                // Offline player — award win to present player
                UUID winner = p1 != null ? tm.getPlayer1() : tm.getPlayer2();
                t.getBracket().recordWinner(tm, winner);
                String wName = Bukkit.getOfflinePlayer(winner).getName();
                Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                        "<gold>[Tournament] <yellow>" + wName + " <gray>wins by walkover (opponent offline)."));
                continue;
            }

            // Teleport both into the arena
            plugin.getMatchManager().joinArena(p1, arena);
            plugin.getMatchManager().joinArena(p2, arena);

            MessageUtil.send(p1, "<gold>[Tournament] <yellow>Your match vs <white>" + p2.getName() + " <yellow>begins!");
            MessageUtil.send(p2, "<gold>[Tournament] <yellow>Your match vs <white>" + p1.getName() + " <yellow>begins!");
        }
    }

    private void finishTournament(Tournament t) {
        UUID winner = t.getBracket().getFinalWinner();
        t.setState(TournamentState.FINISHED);

        String winnerName = winner != null ? Bukkit.getOfflinePlayer(winner).getName() : "Unknown";
        Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                "<gold>★ [Tournament] <white>" + t.getName() +
                " <yellow>finished! Champion: <green>" + winnerName + " <yellow>★"));

        // Pay prizes via Vault
        if (winner != null && t.getPrizeFirst() > 0) {
            plugin.getIntegrationManager().getVault().deposit(
                    Bukkit.getOfflinePlayer(winner), t.getPrizeFirst());
            Player winnerOnline = Bukkit.getPlayer(winner);
            if (winnerOnline != null) MessageUtil.send(winnerOnline,
                    "<green>You won <yellow>" + plugin.getIntegrationManager().getVault()
                            .format(t.getPrizeFirst()) + " <green>as tournament champion!");
        }

        tournaments.remove(t.getName().toLowerCase());
        saveAll();
    }

    // ── Status ────────────────────────────────────────────────────

    public String getStatusText(Tournament t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<gold>=== Tournament: ").append(t.getName()).append(" ===\n");
        sb.append("<yellow>State: <white>").append(t.getState()).append("\n");
        sb.append("<yellow>Arena: <white>").append(t.getArenaName()).append("\n");
        sb.append("<yellow>Players: <white>").append(t.getParticipants().size())
                .append("/").append(t.getMaxPlayers()).append("\n");

        if (t.getState() == TournamentState.IN_PROGRESS && t.getBracket() != null) {
            sb.append("<yellow>Round: <white>").append(t.getBracket().getCurrentRoundNumber()).append("\n");
            sb.append("<yellow>Pending matches: <white>")
                    .append(t.getBracket().getPendingMatches().size()).append("\n");
        }
        if (t.getPrizeFirst() > 0) {
            sb.append("<yellow>Prizes: <green>1st ").append(t.getPrizeFirst())
                    .append(" <yellow>2nd ").append(t.getPrizeSecond())
                    .append(" <yellow>3rd ").append(t.getPrizeThird()).append("\n");
        }
        return sb.toString().trim();
    }

    // ── Persistence ───────────────────────────────────────────────

    public void loadAll() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        var section = cfg.getConfigurationSection("tournaments");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String arenaName  = section.getString(key + ".arena");
            int maxPlayers    = section.getInt(key + ".maxPlayers");
            UUID creatorUuid  = UUID.fromString(section.getString(key + ".creator"));
            String stateStr   = section.getString(key + ".state", "REGISTRATION");

            Tournament t = new Tournament(key, arenaName, maxPlayers, creatorUuid);
            try { t.setState(TournamentState.valueOf(stateStr)); }
            catch (IllegalArgumentException ignored) {}

            // Only restore REGISTRATION-state tournaments
            if (t.getState() != TournamentState.REGISTRATION) continue;

            for (String uuidStr : section.getStringList(key + ".participants")) {
                try { t.getParticipants().add(UUID.fromString(uuidStr)); }
                catch (IllegalArgumentException ignored) {}
            }

            t.setPrizeFirst(section.getDouble(key + ".prizes.first"));
            t.setPrizeSecond(section.getDouble(key + ".prizes.second"));
            t.setPrizeThird(section.getDouble(key + ".prizes.third"));

            tournaments.put(key.toLowerCase(), t);
        }
        plugin.getLogger().info("Loaded " + tournaments.size() + " tournament(s).");
    }

    public void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Tournament t : tournaments.values()) {
            if (t.getState() != TournamentState.REGISTRATION) continue; // only persist open ones
            String key = "tournaments." + t.getName();
            cfg.set(key + ".arena", t.getArenaName());
            cfg.set(key + ".maxPlayers", t.getMaxPlayers());
            cfg.set(key + ".creator", t.getCreatorUuid().toString());
            cfg.set(key + ".state", t.getState().name());
            cfg.set(key + ".participants", t.getParticipants().stream()
                    .map(UUID::toString).toList());
            cfg.set(key + ".prizes.first",  t.getPrizeFirst());
            cfg.set(key + ".prizes.second", t.getPrizeSecond());
            cfg.set(key + ".prizes.third",  t.getPrizeThird());
        }
        try { cfg.save(dataFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Failed to save tournaments.yml", e); }
    }

    // ── Lookup ────────────────────────────────────────────────────

    public Tournament getTournament(String name) {
        return tournaments.get(name.toLowerCase());
    }

    public Collection<Tournament> getAll() {
        return Collections.unmodifiableCollection(tournaments.values());
    }

    public Optional<Tournament> getTournamentForPlayer(UUID uuid) {
        return tournaments.values().stream()
                .filter(t -> t.hasPlayer(uuid))
                .findFirst();
    }
}
```

- [ ] **Step 2: Compile check**

```bash
mvn compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/omniarena/tournament/TournamentManager.java
git commit -m "feat: TournamentManager with bracket progression, prizes, persistence"
```

---

## Task 9: TournamentCommand

**Files:**
- Create: `src/main/java/com/omniarena/commands/admin/TournamentCommand.java`

- [ ] **Step 1: Write TournamentCommand**

```java
package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.tournament.Tournament;
import com.omniarena.tournament.TournamentManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.StreamSupport;

public class TournamentCommand extends SubCommand {

    private final OmniArena plugin;

    public TournamentCommand(OmniArena plugin) {
        super("omniarena.tournament", "/oa tournament <create|join|leave|start|status|list|delete|setprize>", "Tournament management");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        TournamentManager tm = plugin.getTournamentManager();

        if (args.length == 0) { sendUsage(sender); return; }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                // /oa tournament create <name> <arena> <maxPlayers>
                if (!(sender instanceof Player player)) { MessageUtil.send(sender, "<red>Players only."); return; }
                if (args.length < 4) { MessageUtil.send(sender, "<red>Usage: /oa tournament create <name> <arena> <maxPlayers>"); return; }
                int max;
                try { max = Integer.parseInt(args[3]); } catch (NumberFormatException e) {
                    MessageUtil.send(sender, "<red>Invalid number: " + args[3]); return;
                }
                tm.createTournament(player, args[1], args[2], max);
            }
            case "join" -> {
                if (!(sender instanceof Player player)) { MessageUtil.send(sender, "<red>Players only."); return; }
                if (args.length < 2) { MessageUtil.send(sender, "<red>Usage: /oa tournament join <name>"); return; }
                tm.joinTournament(player, args[1]);
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) { MessageUtil.send(sender, "<red>Players only."); return; }
                if (args.length < 2) { MessageUtil.send(sender, "<red>Usage: /oa tournament leave <name>"); return; }
                tm.leaveTournament(player, args[1]);
            }
            case "start" -> {
                if (args.length < 2) { MessageUtil.send(sender, "<red>Usage: /oa tournament start <name>"); return; }
                Tournament t = tm.getTournament(args[1]);
                if (t == null) { MessageUtil.send(sender, "<red>Tournament not found."); return; }
                boolean isCreator = sender instanceof Player p && p.getUniqueId().equals(t.getCreatorUuid());
                boolean isAdmin   = PermissionChecker.hasAdmin(sender, "omniarena.tournament.admin");
                if (!isCreator && !isAdmin) {
                    MessageUtil.send(sender, "<red>Only the creator or an admin can force-start.");
                    return;
                }
                tm.startTournament(t, sender instanceof Player p2 ? p2 : null);
            }
            case "status" -> {
                if (args.length < 2) { MessageUtil.send(sender, "<red>Usage: /oa tournament status <name>"); return; }
                Tournament t = tm.getTournament(args[1]);
                if (t == null) { MessageUtil.send(sender, "<red>Tournament not found."); return; }
                MessageUtil.send(sender, tm.getStatusText(t));
            }
            case "list" -> {
                var all = tm.getAll();
                if (all.isEmpty()) { MessageUtil.send(sender, "<gray>No active tournaments."); return; }
                MessageUtil.send(sender, "<gold>=== Tournaments ===");
                for (Tournament t : all) {
                    MessageUtil.send(sender, "<yellow>" + t.getName() + " <gray>(" + t.getArenaName() +
                            ", " + t.getParticipants().size() + "/" + t.getMaxPlayers() +
                            ", " + t.getState() + ")");
                }
            }
            case "delete" -> {
                if (!PermissionChecker.hasAdmin(sender, "omniarena.tournament.admin")) {
                    MessageUtil.send(sender, "<red>Admin only.");
                    return;
                }
                if (args.length < 2) { MessageUtil.send(sender, "<red>Usage: /oa tournament delete <name>"); return; }
                tm.deleteTournament(args[1], sender instanceof Player p ? p : null);
            }
            case "setprize" -> {
                // /oa tournament setprize <name> <1st|2nd|3rd> <amount>
                if (!PermissionChecker.hasAdmin(sender, "omniarena.tournament.admin")) {
                    MessageUtil.send(sender, "<red>Admin only."); return;
                }
                if (args.length < 4) {
                    MessageUtil.send(sender, "<red>Usage: /oa tournament setprize <name> <1st|2nd|3rd> <amount>"); return;
                }
                Tournament t = tm.getTournament(args[1]);
                if (t == null) { MessageUtil.send(sender, "<red>Tournament not found."); return; }
                double amount;
                try { amount = Double.parseDouble(args[3]); } catch (NumberFormatException e) {
                    MessageUtil.send(sender, "<red>Invalid amount."); return;
                }
                switch (args[2].toLowerCase()) {
                    case "1st" -> t.setPrizeFirst(amount);
                    case "2nd" -> t.setPrizeSecond(amount);
                    case "3rd" -> t.setPrizeThird(amount);
                    default -> { MessageUtil.send(sender, "<red>Use 1st, 2nd, or 3rd."); return; }
                }
                tm.saveAll();
                MessageUtil.send(sender, "<green>Set " + args[2] + " prize for <yellow>" + t.getName() +
                        " <green>to <yellow>" + amount);
            }
            default -> sendUsage(sender);
        }
    }

    private void sendUsage(CommandSender sender) {
        MessageUtil.send(sender, "<gold>=== /oa tournament ===");
        MessageUtil.send(sender, "<yellow>/oa tournament create <name> <arena> <max></yellow> <gray>- create");
        MessageUtil.send(sender, "<yellow>/oa tournament join <name></yellow> <gray>- join registration");
        MessageUtil.send(sender, "<yellow>/oa tournament leave <name></yellow> <gray>- leave registration");
        MessageUtil.send(sender, "<yellow>/oa tournament start <name></yellow> <gray>- force start");
        MessageUtil.send(sender, "<yellow>/oa tournament status <name></yellow> <gray>- bracket status");
        MessageUtil.send(sender, "<yellow>/oa tournament list</yellow> <gray>- list all");
        MessageUtil.send(sender, "<yellow>/oa tournament delete <name></yellow> <gray>- admin: delete");
        MessageUtil.send(sender, "<yellow>/oa tournament setprize <name> <1st|2nd|3rd> <amount></yellow> <gray>- admin: set prize");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("create","join","leave","start","status","list","delete","setprize")
                .stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && !args[0].equalsIgnoreCase("create") && !args[0].equalsIgnoreCase("list"))
            return plugin.getTournamentManager().getAll().stream()
                    .map(Tournament::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("create"))
            return List.of(); // name — free text
        if (args.length == 3 && args[0].equalsIgnoreCase("create"))
            return plugin.getArenaManager().getAll().stream()
                    .map(a -> a.getName())
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase())).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("setprize"))
            return List.of("1st","2nd","3rd").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase())).toList();
        return List.of();
    }
}
```

- [ ] **Step 2: Compile check**

```bash
mvn compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/omniarena/commands/admin/TournamentCommand.java
git commit -m "feat: /oa tournament command (create/join/leave/start/status/list/delete/setprize)"
```

---

## Task 9b: Add offline player deposit to VaultIntegration

**Files:**
- Modify: `src/main/java/com/omniarena/integration/VaultIntegration.java`

- [ ] **Step 1: Add `deposit(OfflinePlayer, double)` overload**

```java
public void deposit(org.bukkit.OfflinePlayer player, double amount) {
    if (economy == null || amount <= 0) return;
    economy.depositPlayer(player, amount);
}
```

Add this after the existing `deposit(Player player, double amount)` method.

- [ ] **Step 2: Compile + commit**

```bash
mvn compile -q
git add src/main/java/com/omniarena/integration/VaultIntegration.java
git commit -m "feat: add offline player deposit to VaultIntegration for prize payouts"
```

---

## Task 10: Wire TournamentManager into OmniArena + MatchManager + CommandManager

**Files:**
- Modify: `src/main/java/com/omniarena/OmniArena.java`
- Modify: `src/main/java/com/omniarena/match/MatchManager.java`
- Modify: `src/main/java/com/omniarena/commands/CommandManager.java`
- Modify: `src/main/resources/plugin.yml`

- [ ] **Step 1: Add tournamentManager to OmniArena.java**

Add field after `bettingManager`:
```java
private com.omniarena.tournament.TournamentManager tournamentManager;
```

Add getter:
```java
public com.omniarena.tournament.TournamentManager getTournamentManager() { return tournamentManager; }
```

In `onEnable()`, after `bettingManager = new com.omniarena.betting.BettingManager(this);`:
```java
tournamentManager = new com.omniarena.tournament.TournamentManager(this);
```

After `partyManager.start();`:
```java
tournamentManager.start();
```

In `onDisable()`, after `bettingManager.refundAll();`:
```java
if (tournamentManager != null) tournamentManager.saveAll();
```

- [ ] **Step 2: Add tournament hook to MatchManager.endMatch()**

In `MatchManager.endMatch()`, after the betting `processMatchResults` call (added in Task 3):

```java
// Tournament bracket advancement
plugin.getTournamentManager().onMatchEnd(result);
```

- [ ] **Step 3: Register TournamentCommand in CommandManager**

In `CommandManager.registerAll()`, after the betting registration:

```java
reg(adminCommands,  "tournament", new TournamentCommand(plugin));
reg(playerCommands, "tournament", new TournamentCommand(plugin));
```

Note: registering in both maps so non-admin players can use create/join/leave/status/list (the command itself enforces admin-only for delete/setprize).

- [ ] **Step 4: Add permissions to plugin.yml**

Open `src/main/resources/plugin.yml` and add inside the `permissions:` section:

```yaml
  omniarena.bet:
    description: Place bets on arena matches
    default: true
  omniarena.tournament:
    description: Create and join tournaments
    default: true
  omniarena.tournament.admin:
    description: Admin tournament management (delete, setprize)
    default: op
```

- [ ] **Step 5: Final compile check**

```bash
mvn compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Full test suite**

```bash
mvn test -q 2>&1 | tail -15
```
Expected: no failures.

- [ ] **Step 7: Build JAR**

```bash
mvn package -DskipTests -q
```
Expected: `BUILD SUCCESS`, JAR created in `target/`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/omniarena/OmniArena.java \
        src/main/java/com/omniarena/match/MatchManager.java \
        src/main/java/com/omniarena/commands/CommandManager.java \
        src/main/resources/plugin.yml
git commit -m "feat: wire TournamentManager into OmniArena lifecycle, MatchManager, CommandManager"
```

---

## Self-Review

**Spec coverage:**
- ✅ Single-elimination bracket — `TournamentBracket`
- ✅ Any size with byes — `generateRound1` computes `nextPowerOfTwo`
- ✅ Admin + player create — `createTournament` open to all; `delete/setprize` guard with `omniarena.tournament.admin`
- ✅ Auto-start when full — `joinTournament` calls `startTournament` when `isFull()`
- ✅ Force-start — `start` subcommand checks creator or admin
- ✅ Vault prize payout — `finishTournament` calls `VaultIntegration.deposit()`
- ✅ Persist REGISTRATION tournaments — `saveAll()`/`loadAll()` with `tournaments.yml`
- ✅ Betting commands — `BetCommand` (place/cancel/info)
- ✅ Match-end betting hook — Task 3 wires `processMatchResults`
- ✅ Betting window — `isBettingWindowOpen()` + `onMatchStart()`
- ✅ Refund on shutdown — `refundAll()` called in `onDisable()`
- ✅ Config defaults — `config.yml` betting section

**Vault deposit for offline players:** `VaultIntegration.deposit(Player, double)` requires an online `Player`. For prizes, `getBracket().getFinalWinner()` could be offline by payout time. Need to check — `VaultIntegration` currently only has `deposit(Player player, double)`. Add an offline overload.

**Fix:** In `VaultIntegration.java`, add:
```java
public void deposit(org.bukkit.OfflinePlayer player, double amount) {
    if (economy == null || amount <= 0) return;
    economy.depositPlayer(player, amount);
}
```
This uses Vault's `Economy.depositPlayer(OfflinePlayer, double)` which works offline. Add this step before Task 10 commit or inline into Task 8 commit.

**Type consistency:** `TournamentMatch.involves()` used in `TournamentBracket.getActiveMatchForArena()` — consistent. `TournamentBracket.recordWinner(TournamentMatch, UUID)` — consistent across Task 7/8. `VaultIntegration.deposit(OfflinePlayer, double)` used in `TournamentManager.finishTournament()` — needs the fix above.
