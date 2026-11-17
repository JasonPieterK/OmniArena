package com.omniarena.match;

import com.omniarena.arena.Arena;
import com.omniarena.arena.TeamColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class Match {

    private final Arena arena;
    private MatchState state = MatchState.WAITING;

    // Players
    private final Set<UUID> players     = new LinkedHashSet<>();
    private final Set<UUID> spectators  = new HashSet<>();
    private final Set<UUID> alivePlayers = new LinkedHashSet<>();

    // Per-player data
    private final Map<UUID, Integer>    kills      = new HashMap<>();
    private final Map<UUID, Integer>    deaths     = new HashMap<>();
    private final Map<UUID, Integer>    streak     = new HashMap<>();
    private final Map<UUID, Integer>    combo      = new HashMap<>();
    private final Map<UUID, Integer>    lives      = new HashMap<>();
    private final Map<UUID, Long>       joinedAt   = new HashMap<>();
    private final Map<UUID, UUID>       lastKilledBy = new HashMap<>(); // for revenge tracking
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor       = new HashMap<>();
    private final Map<UUID, String>     selectedKit  = new HashMap<>();
    private final Map<UUID, Long>       spawnProtection = new HashMap<>(); // expiry millis
    private final Map<UUID, MatchTeam>  teamMap     = new HashMap<>();
    private final Map<UUID, BukkitTask> spawnProtectionTasks = new HashMap<>();
    /** Pre-arranged side (0-based) per player, e.g. queued parties that must share a team. */
    private final Map<UUID, Integer> presetSides = new HashMap<>();

    // Teams
    private final List<MatchTeam> teams = new ArrayList<>();

    // Timing
    private long startTime;
    private long endTime;
    private int countdownSeconds;
    private int elapsedSeconds;

    // Scores
    private boolean firstBloodDealt = false;

    // Ranked (queue) matches record ELO; queueKit overrides the arena's default loadout
    private boolean ranked = false;
    private String queueKit;
    private String queueName;
    /** Number of players that were in the match when it started (0 before start). */
    private int startedWith;

    // Scheduled tasks
    private BukkitTask countdownTask;
    private BukkitTask matchTimerTask;

    public Match(Arena arena) {
        this.arena = arena;
    }

    public Arena getArena() { return arena; }
    public MatchState getState() { return state; }
    public void setState(MatchState state) { this.state = state; }
    public Set<UUID> getPlayers() { return players; }
    public Set<UUID> getSpectators() { return spectators; }
    public Set<UUID> getAlivePlayers() { return alivePlayers; }
    public Map<UUID, Integer> getKillsMap() { return kills; }
    public Map<UUID, Integer> getDeathsMap() { return deaths; }
    public Map<UUID, Integer> getStreakMap() { return streak; }
    public Map<UUID, Integer> getComboMap() { return combo; }
    public Map<UUID, Integer> getLivesMap() { return lives; }
    public Map<UUID, Integer> getPresetSides() { return presetSides; }
    public int getLives(UUID uuid) { return lives.getOrDefault(uuid, 0); }
    public Map<UUID, Long> getJoinedAt() { return joinedAt; }
    public Map<UUID, UUID> getLastKilledBy() { return lastKilledBy; }
    public Map<UUID, String> getSelectedKit() { return selectedKit; }
    public Map<UUID, MatchTeam> getTeamMap() { return teamMap; }
    public List<MatchTeam> getTeams() { return teams; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public int getCountdownSeconds() { return countdownSeconds; }
    public void setCountdownSeconds(int countdownSeconds) { this.countdownSeconds = countdownSeconds; }
    public int getElapsedSeconds() { return elapsedSeconds; }
    public void setElapsedSeconds(int elapsedSeconds) { this.elapsedSeconds = elapsedSeconds; }
    public boolean isRanked() { return ranked; }
    public void setRanked(boolean ranked) { this.ranked = ranked; }
    public String getQueueKit() { return queueKit; }
    public void setQueueKit(String queueKit) { this.queueKit = queueKit; }
    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }
    public int getStartedWith() { return startedWith; }
    public void setStartedWith(int startedWith) { this.startedWith = startedWith; }
    public boolean isFirstBloodDealt() { return firstBloodDealt; }
    public void setFirstBloodDealt(boolean firstBloodDealt) { this.firstBloodDealt = firstBloodDealt; }

    // ── Player helpers ────────────────────────────────────────────

    public void addPlayer(UUID uuid) {
        players.add(uuid);
        alivePlayers.add(uuid);
        kills.put(uuid, 0);
        deaths.put(uuid, 0);
        streak.put(uuid, 0);
        combo.put(uuid, 0);
        joinedAt.put(uuid, System.currentTimeMillis());
        var sm = com.omniarena.sign.SignManager.getInstance();
        if (sm != null) sm.requestUpdate(arena);
    }

    public void removePlayer(UUID uuid) {
        var sm = com.omniarena.sign.SignManager.getInstance();
        if (sm != null) sm.requestUpdate(arena);
        players.remove(uuid);
        alivePlayers.remove(uuid);
        spectators.remove(uuid);
        kills.remove(uuid);
        deaths.remove(uuid);
        streak.remove(uuid);
        combo.remove(uuid);
        joinedAt.remove(uuid);
        spawnProtection.remove(uuid);
        teamMap.remove(uuid);
        BukkitTask spTask = spawnProtectionTasks.remove(uuid);
        if (spTask != null) spTask.cancel();
        lives.remove(uuid);
        presetSides.remove(uuid);
        lastKilledBy.remove(uuid);
        lastKilledBy.values().removeIf(uuid::equals);
        savedInventories.remove(uuid);
        savedArmor.remove(uuid);
        selectedKit.remove(uuid);
        for (MatchTeam t : teams) t.getMembers().remove(uuid);
    }

    public boolean hasPlayer(UUID uuid) { return players.contains(uuid); }
    public boolean isSpectator(UUID uuid) { return spectators.contains(uuid); }
    public boolean isAlive(UUID uuid) { return alivePlayers.contains(uuid); }
    public int getPlayerCount() { return players.size(); }

    public void recordKill(UUID killer, UUID victim) {
        kills.merge(killer, 1, Integer::sum);
        deaths.merge(victim, 1, Integer::sum);
        streak.merge(killer, 1, Integer::sum);
        streak.put(victim, 0);
        combo.put(victim, 0);
        combo.merge(killer, 1, Integer::sum);
        lastKilledBy.put(victim, killer);
    }

    public void resetStreak(UUID uuid) { streak.put(uuid, 0); }
    public void resetCombo(UUID uuid)  { combo.computeIfPresent(uuid, (k, v) -> 0); }

    public int getKills(UUID uuid)  { return kills.getOrDefault(uuid, 0); }
    public int getDeaths(UUID uuid) { return deaths.getOrDefault(uuid, 0); }
    public int getStreak(UUID uuid) { return streak.getOrDefault(uuid, 0); }
    public int getCombo(UUID uuid)  { return combo.getOrDefault(uuid, 0); }

    public boolean isRevenge(UUID killer, UUID victim) {
        return victim.equals(lastKilledBy.get(killer));
    }

    public UUID getLeader() {
        return kills.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public List<UUID> getRankedPlayers() {
        List<UUID> ranked = new ArrayList<>(players);
        ranked.sort((a, b) -> Integer.compare(kills.getOrDefault(b, 0), kills.getOrDefault(a, 0)));
        return ranked;
    }

    public Map<UUID, Long> getSpawnProtection() { return spawnProtection; }

    /** Tracks the spawn-protection expiry task so it can be cancelled when the player leaves. */
    public void setSpawnProtectionTask(UUID uuid, BukkitTask task) {
        BukkitTask old = spawnProtectionTasks.put(uuid, task);
        if (old != null) old.cancel();
    }

    public void clearSpawnProtectionTask(UUID uuid) {
        spawnProtectionTasks.remove(uuid);
    }

    public void setSpawnProtection(UUID uuid, int seconds) {
        spawnProtection.put(uuid, System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean hasSpawnProtection(UUID uuid) {
        Long expiry = spawnProtection.get(uuid);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public void saveInventory(UUID uuid, ItemStack[] contents, ItemStack[] armor) {
        savedInventories.put(uuid, contents.clone());
        savedArmor.put(uuid, armor.clone());
    }

    public ItemStack[] getSavedContents(UUID uuid) { return savedInventories.get(uuid); }
    public ItemStack[] getSavedArmor(UUID uuid)    { return savedArmor.get(uuid); }

    // ── Team helpers ──────────────────────────────────────────────

    public MatchTeam createTeam(String name, TeamColor color) {
        MatchTeam team = new MatchTeam(name, color);
        teams.add(team);
        return team;
    }

    public MatchTeam getTeam(UUID uuid) { return teamMap.get(uuid); }

    public void assignTeam(UUID uuid, MatchTeam team) {
        team.addMember(uuid);
        teamMap.put(uuid, team);
    }

    public MatchTeam getLeadingTeam() {
        return teams.stream()
                .max(Comparator.comparingInt(MatchTeam::getScore))
                .orElse(null);
    }

    // ── Timing ────────────────────────────────────────────────────

    public long getDurationSeconds() {
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return (end - startTime) / 1000;
    }

    public int getTimeRemaining() {
        return Math.max(0, arena.getMatchDuration() - elapsedSeconds);
    }

    public void tick() { elapsedSeconds++; }

    public void setCountdownTask(BukkitTask task) {
        if (countdownTask != null) countdownTask.cancel();
        countdownTask = task;
    }

    public void setMatchTimerTask(BukkitTask task) {
        if (matchTimerTask != null) matchTimerTask.cancel();
        matchTimerTask = task;
    }

    public void cancelTasks() {
        spawnProtectionTasks.values().forEach(BukkitTask::cancel);
        spawnProtectionTasks.clear();
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (matchTimerTask != null) {
            matchTimerTask.cancel();
            matchTimerTask = null;
        }
    }
}
