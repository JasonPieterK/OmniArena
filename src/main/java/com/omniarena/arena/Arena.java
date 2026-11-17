package com.omniarena.arena;

import com.omniarena.match.GoalType;
import com.omniarena.match.RespawnType;
import com.omniarena.gamemode.GameModeType;
import com.omniarena.sign.ArenaSign;
import org.bukkit.Location;

import java.util.*;

public class Arena {

    private final String name;
    private ArenaState state = ArenaState.WAITING;

    // Match settings
    private GameModeType gameMode = GameModeType.DEATHMATCH;
    private int minPlayers = 2;
    private int maxPlayers = 16;
    private int matchDuration = 300;  // seconds
    private int countdown = 10;

    // Goal
    /** Explicit per-arena goal type; null = use the game mode's goal type. */
    private GoalType goalTypeOverride = null;
    private int goalValue = 20;

    // Respawn
    private RespawnType respawnType = RespawnType.INSTANT;
    private int respawnDelay = 3;
    private int spawnProtection = 3;
    /** Lives per player; 0 = not configured (mode default respawn semantics apply). */
    private int lives = 0;

    // Spawns
    private final List<Location> battleSpawns = new ArrayList<>();
    private final List<Location> lobbySpawns  = new ArrayList<>();
    private final List<Location> spectatorSpawns = new ArrayList<>();

    // Region
    private ArenaRegion region = new ArenaRegion();

    // Rules
    private ArenaRules rules = new ArenaRules();

    // Teams
    private ArenaTeamConfig teamConfig = new ArenaTeamConfig();

    // Visual
    private ArenaVisualConfig visual = new ArenaVisualConfig();

    // Chat
    private ChatMode chatMode = ChatMode.ARENA;
    private boolean announcements = true;

    // Modules
    private final Set<String> enabledModules = new HashSet<>(Set.of("killstreak", "combo"));

    // Signs
    private final List<ArenaSign> signs = new ArrayList<>();

    // Options
    private boolean midMatchJoin = false;
    private boolean spectatorAllowed = true;

    // Whitelist/blacklist
    private final Set<UUID> whitelist = new HashSet<>();
    private final Set<UUID> blacklist = new HashSet<>();
    private boolean whitelistEnabled = false;

    // End commands (per-arena override, empty = use global)
    private final List<String> endCommands = new ArrayList<>();

    public Arena(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public ArenaState getState() { return state; }
    public void setState(ArenaState state) {
        this.state = state;
        var sm = com.omniarena.sign.SignManager.getInstance();
        if (sm != null) sm.requestUpdate(this);
    }
    public GameModeType getGameMode() { return gameMode; }
    public void setGameMode(GameModeType gameMode) { this.gameMode = gameMode; }
    public int getMinPlayers() { return minPlayers; }
    public void setMinPlayers(int minPlayers) { this.minPlayers = minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public int getMatchDuration() { return matchDuration; }
    public void setMatchDuration(int matchDuration) { this.matchDuration = matchDuration; }
    public int getCountdown() { return countdown; }
    public void setCountdown(int countdown) { this.countdown = countdown; }
    /** Effective goal type: the arena override if one was set, else the game mode's own goal. */
    public GoalType getGoalType() { return goalTypeOverride != null ? goalTypeOverride : gameMode.getGoalType(); }
    /** Sets the goal type; choosing the game mode's own goal clears the override. */
    public void setGoalType(GoalType goalType) {
        this.goalTypeOverride = (goalType == null || goalType == gameMode.getGoalType()) ? null : goalType;
    }
    public boolean hasGoalTypeOverride() { return goalTypeOverride != null; }
    public int getGoalValue() { return goalValue; }
    public void setGoalValue(int goalValue) { this.goalValue = goalValue; }
    public RespawnType getRespawnType() { return respawnType; }
    public void setRespawnType(RespawnType respawnType) { this.respawnType = respawnType; }
    public int getRespawnDelay() { return respawnDelay; }
    public void setRespawnDelay(int respawnDelay) { this.respawnDelay = respawnDelay; }
    public int getLives() { return lives; }
    public void setLives(int lives) { this.lives = Math.max(0, lives); }
    public int getSpawnProtection() { return spawnProtection; }
    public void setSpawnProtection(int spawnProtection) { this.spawnProtection = spawnProtection; }
    public List<Location> getBattleSpawns() { return battleSpawns; }
    public List<Location> getLobbySpawns() { return lobbySpawns; }
    public List<Location> getSpectatorSpawns() { return spectatorSpawns; }
    public ArenaRegion getRegion() { return region; }
    public void setRegion(ArenaRegion region) { this.region = region; }
    public ArenaRules getRules() { return rules; }
    public void setRules(ArenaRules rules) { this.rules = rules; }
    public ArenaTeamConfig getTeamConfig() { return teamConfig; }
    public void setTeamConfig(ArenaTeamConfig teamConfig) { this.teamConfig = teamConfig; }
    public ArenaVisualConfig getVisual() { return visual; }
    public void setVisual(ArenaVisualConfig visual) { this.visual = visual; }
    public ChatMode getChatMode() { return chatMode; }
    public void setChatMode(ChatMode chatMode) { this.chatMode = chatMode; }
    public boolean isAnnouncements() { return announcements; }
    public void setAnnouncements(boolean announcements) { this.announcements = announcements; }
    public Set<String> getEnabledModules() { return enabledModules; }
    public List<ArenaSign> getSigns() { return signs; }
    public boolean isMidMatchJoin() { return midMatchJoin; }
    public void setMidMatchJoin(boolean midMatchJoin) { this.midMatchJoin = midMatchJoin; }
    public boolean isSpectatorAllowed() { return spectatorAllowed; }
    public void setSpectatorAllowed(boolean spectatorAllowed) { this.spectatorAllowed = spectatorAllowed; }
    public Set<UUID> getWhitelist() { return whitelist; }
    public Set<UUID> getBlacklist() { return blacklist; }
    public boolean isWhitelistEnabled() { return whitelistEnabled; }
    public void setWhitelistEnabled(boolean whitelistEnabled) { this.whitelistEnabled = whitelistEnabled; }
    public List<String> getEndCommands() { return endCommands; }

    // ── State helpers ──────────────────────────────────────────

    public boolean isEnabled() {
        return state != ArenaState.DISABLED && state != ArenaState.MAINTENANCE;
    }

    public boolean isJoinable() {
        return state == ArenaState.WAITING || state == ArenaState.STARTING;
    }

    public boolean isRunning() {
        return state == ArenaState.RUNNING;
    }

    public boolean hasModule(String module) {
        return enabledModules.contains(module.toLowerCase());
    }

    public void enableModule(String module) {
        enabledModules.add(module.toLowerCase());
    }

    public void disableModule(String module) {
        enabledModules.remove(module.toLowerCase());
    }

    public boolean isWhitelisted(UUID playerId) {
        if (!whitelistEnabled) return true;
        return whitelist.contains(playerId);
    }

    public boolean isBanned(UUID playerId) {
        return blacklist.contains(playerId);
    }

    public Location getRandomBattleSpawn() {
        if (battleSpawns.isEmpty()) return null;
        return battleSpawns.get(new Random().nextInt(battleSpawns.size()));
    }

    /**
     * Distinct numbered battle spawn. Player {@code index} gets battle spawn
     * {@code index % count}, so a 6-player match with 6 spawns puts everyone on
     * their own pad (spawn 1, 2, 3 …) instead of stacking on a random one.
     */
    public Location getBattleSpawn(int index) {
        if (battleSpawns.isEmpty()) return null;
        return battleSpawns.get(index % battleSpawns.size());
    }

    public Location getLobbySpawn() {
        if (lobbySpawns.isEmpty()) return getRandomBattleSpawn();
        return lobbySpawns.get(0);
    }

    public Location getSpectatorSpawn() {
        if (spectatorSpawns.isEmpty()) return getLobbySpawn();
        return spectatorSpawns.get(0);
    }

    public void addSpawn(SpawnType type, Location location) {
        switch (type) {
            case BATTLE   -> battleSpawns.add(location);
            case LOBBY    -> lobbySpawns.add(location);
            case SPECTATOR -> spectatorSpawns.add(location);
        }
    }

    public List<Location> getSpawns(SpawnType type) {
        return switch (type) {
            case BATTLE   -> battleSpawns;
            case LOBBY    -> lobbySpawns;
            case SPECTATOR -> spectatorSpawns;
        };
    }

    public void clearSpawns(SpawnType type) {
        getSpawns(type).clear();
    }
}
