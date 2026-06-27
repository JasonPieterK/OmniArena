package com.omniarena.match;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaState;
import com.omniarena.arena.TeamColor;
import com.omniarena.config.ConfigManager;
import com.omniarena.stats.StatsManager;
import com.omniarena.util.AsyncUtil;
import com.omniarena.util.MessageUtil;
import com.omniarena.visual.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MatchManager {

    private static MatchManager instance;

    private final OmniArena plugin;
    private final Map<String, Match> activeMatches = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerArena    = new ConcurrentHashMap<>();
    private final EndCommandHandler endCommandHandler;
    private final Set<String> frozenArenas = ConcurrentHashMap.newKeySet();
    private final Set<UUID> godModePlayers = ConcurrentHashMap.newKeySet();
    private volatile boolean lockdown = false;

    public MatchManager(OmniArena plugin) {
        this.plugin = plugin;
        this.endCommandHandler = new EndCommandHandler(plugin);
        instance = this;
    }

    public static MatchManager getInstance() { return instance; }

    /**
     * When TAB takeover is on, OmniArena leaves the sidebar + bossbar alone so
     * the TAB plugin can drive them from our PlaceholderAPI placeholders instead.
     */
    private boolean tabTakeover() {
        return ConfigManager.getInstance().getConfig().getBoolean("integrations.tab.takeover", false);
    }

    // ── Join / Leave ──────────────────────────────────────────────

    public void setLockdown(boolean lockdown) { this.lockdown = lockdown; }
    public boolean isLockdown() { return lockdown; }

    public void setArenaFrozen(String arenaName, boolean frozen) {
        if (arenaName == null || arenaName.isBlank()) return;
        String key = arenaName.toLowerCase(Locale.ROOT);
        if (frozen) frozenArenas.add(key);
        else frozenArenas.remove(key);
    }

    public boolean isArenaFrozen(String arenaName) {
        return arenaName != null && frozenArenas.contains(arenaName.toLowerCase(Locale.ROOT));
    }

    public void setGodMode(UUID uuid, boolean enabled) {
        if (uuid == null) return;
        if (enabled) godModePlayers.add(uuid);
        else godModePlayers.remove(uuid);
    }

    public boolean isGodMode(UUID uuid) {
        return uuid != null && godModePlayers.contains(uuid);
    }

    public void clearGodMode(UUID uuid) {
        if (uuid != null) godModePlayers.remove(uuid);
    }

    public boolean joinArena(Player player, Arena arena) {
        if (lockdown) {
            MessageUtil.send(player, "<red>Arenas are currently locked down by an administrator.");
            return false;
        }
        if (playerArena.containsKey(player.getUniqueId())) return false;
        if (!arena.isJoinable()) return false;
        if (arena.isBanned(player.getUniqueId())) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("arena-banned"));
            return false;
        }

        Match match = activeMatches.computeIfAbsent(arena.getName(), k -> {
            Match m = new Match(arena);
            startCountdown(m);
            return m;
        });

        saveInventory(player, match);
        match.addPlayer(player.getUniqueId());
        playerArena.put(player.getUniqueId(), arena.getName());

        Location spawn = arena.getLobbySpawn();
        if (spawn != null) player.teleport(spawn);

        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();

        broadcastArena(match, ConfigManager.getInstance().getMessage("join-arena")
                .replace("{arena}", arena.getName()));

        if (!tabTakeover()) plugin.getVisual().getScoreboardManager().createBoard(player, match);

        if (match.getState() == MatchState.RUNNING && arena.isMidMatchJoin()) {
            spawnPlayer(player, match);
        }

        return true;
    }

    public void leaveArena(Player player) {
        String arenaName = playerArena.remove(player.getUniqueId());
        if (arenaName == null) return;

        Match match = activeMatches.get(arenaName);
        if (match == null) return;

        match.removePlayer(player.getUniqueId());
        plugin.getVisual().getScoreboardManager().removeBoard(player);
        restoreInventory(player, match);

        Location back = player.getBedSpawnLocation() != null
                ? player.getBedSpawnLocation()
                : player.getWorld().getSpawnLocation();
        player.teleport(back);
        player.setGameMode(GameMode.SURVIVAL);

        MessageUtil.send(player, ConfigManager.getInstance().getMessage("leave-arena")
                .replace("{arena}", arenaName));

        if (match.getState() == MatchState.RUNNING) {
            checkEndConditions(match);
        }

        if (match.getPlayerCount() == 0) {
            cleanupMatch(match);
        }
    }

    public boolean joinSpectator(Player player, Arena arena) {
        if (!arena.isSpectatorAllowed()) return false;
        if (playerArena.containsKey(player.getUniqueId())) return false;

        Match match = activeMatches.get(arena.getName());
        if (match == null) return false;

        match.getSpectators().add(player.getUniqueId());
        playerArena.put(player.getUniqueId(), arena.getName() + ":spectator");

        Location spec = arena.getSpectatorSpawn();
        if (spec != null) player.teleport(spec);

        player.setGameMode(GameMode.SPECTATOR);
        player.setInvisible(true);

        MessageUtil.send(player, ConfigManager.getInstance().getMessage("spectate-joined")
                .replace("{arena}", arena.getName()));
        return true;
    }

    public void leaveSpectator(Player player) {
        String key = playerArena.remove(player.getUniqueId());
        if (key == null) return;

        String arenaName = key.replace(":spectator", "");
        Match match = activeMatches.get(arenaName);
        if (match != null) match.getSpectators().remove(player.getUniqueId());

        player.setGameMode(GameMode.SURVIVAL);
        player.setInvisible(false);

        Location back = player.getWorld().getSpawnLocation();
        player.teleport(back);
    }

    // ── Countdown ────────────────────────────────────────────────

    private void startCountdown(Match match) {
        match.setState(MatchState.WAITING);
        final int[] secondsLeft = {match.getArena().getCountdown()};
        match.setCountdownSeconds(secondsLeft[0]);

        BukkitTask task = AsyncUtil.runSyncRepeating(() -> {
            if (match.getState() != MatchState.WAITING && match.getState() != MatchState.STARTING) return;
            if (match.getPlayerCount() < match.getArena().getMinPlayers()) {
                match.setState(MatchState.WAITING);
                broadcastArena(match, ConfigManager.getInstance().getMessage("match-min-players")
                        .replace("{current}", String.valueOf(match.getPlayerCount()))
                        .replace("{min}", String.valueOf(match.getArena().getMinPlayers())));
                secondsLeft[0] = match.getArena().getCountdown();
                return;
            }

            match.setState(MatchState.STARTING);
            if (secondsLeft[0] <= 0) {
                startMatch(match);
                return;
            }

            if (secondsLeft[0] <= 5 || secondsLeft[0] == 10) {
                broadcastArena(match, ConfigManager.getInstance().getMessage("match-starting")
                        .replace("{seconds}", String.valueOf(secondsLeft[0])));
                plugin.getVisual().getTitleManager().sendCountdownTitle(match, secondsLeft[0]);
                plugin.getVisual().getSoundManager().playCountdown(match, secondsLeft[0]);
            }
            secondsLeft[0]--;
        }, 20L, 20L);
        match.setCountdownTask(task);
    }

    // ── Start Match ──────────────────────────────────────────────

    public void startMatch(Match match) {
        match.cancelTasks();

        Arena arena = match.getArena();
        arena.setState(ArenaState.RUNNING);
        match.setState(MatchState.RUNNING);
        match.setStartTime(System.currentTimeMillis());

        // Assign teams
        if (arena.getTeamConfig().isEnabled()) assignTeams(match);

        // Teleport all players to DISTINCT numbered battle spawns (spawn 1,2,3…)
        int idx = 0;
        for (UUID uuid : match.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            Location spawn = arena.getBattleSpawn(idx++);
            spawnPlayer(player, match, spawn);
        }

        broadcastArena(match, ConfigManager.getInstance().getMessage("match-started"));
        plugin.getVisual().getTitleManager().sendMatchStartTitle(match);
        plugin.getVisual().getSoundManager().playMatchStart(match);
        if (!tabTakeover()) plugin.getVisual().getBossBarManager().createTimer(match);

        startMatchTimer(match);
    }

    private void spawnPlayer(Player player, Match match) {
        spawnPlayer(player, match, match.getArena().getRandomBattleSpawn());
    }

    private void spawnPlayer(Player player, Match match, Location spawn) {
        Arena arena = match.getArena();
        if (spawn == null) spawn = arena.getRandomBattleSpawn();
        if (spawn != null) player.teleport(spawn);

        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getAttribute(
                org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);

        // Hand the player this game type's full loadout.
        arena.getGameMode().applyLoadout(player);

        if (arena.getSpawnProtection() > 0) {
            match.setSpawnProtection(player.getUniqueId(), arena.getSpawnProtection());
        }
    }

    private void startMatchTimer(Match match) {
        BukkitTask task = AsyncUtil.runSyncRepeating(() -> {
            if (match.getState() != MatchState.RUNNING) return;

            match.tick();
            plugin.getVisual().getScoreboardManager().updateAll(match);
            plugin.getVisual().getBossBarManager().updateTimer(match);
            plugin.getVisual().getActionBarManager().updateAll(match);

            if (match.getTimeRemaining() <= 0) {
                endMatch(match, buildTimeoutResult(match));
            }
        }, 20L, 20L);
        match.setMatchTimerTask(task);
    }

    // ── End Match ────────────────────────────────────────────────

    public void endMatch(Match match, MatchResult result) {
        if (match.getState() == MatchState.ENDING) return;
        match.cancelTasks();
        match.setState(MatchState.ENDING);
        match.setEndTime(System.currentTimeMillis());
        match.getArena().setState(ArenaState.ENDING);

        // Update ELO ratings for competitive matches
        if (result.getWinnerId() != null && result.getLoserId() != null) {
            String winnerName = Bukkit.getOfflinePlayer(result.getWinnerId()).getName();
            String loserName = Bukkit.getOfflinePlayer(result.getLoserId()).getName();
            plugin.getEloManager().recordMatch(result.getWinnerId(), result.getLoserId(), 
                    winnerName != null ? winnerName : "Unknown", 
                    loserName != null ? loserName : "Unknown");
        }

        plugin.getVisual().getTitleManager().sendMatchEndTitle(match, result);
        plugin.getVisual().getSoundManager().playMatchEnd(match, result);
        plugin.getVisual().getEffectManager().playVictoryFireworks(match, result);
        plugin.getVisual().getBossBarManager().removeAll(match);

        endCommandHandler.execute(match, result);
        StatsManager.getInstance().recordResult(match, result);

        if (ConfigManager.getInstance().getConfig().getBoolean("match.announce-global")) {
            String msg = ConfigManager.getInstance().getMessage("match-ended")
                    .replace("{result}", result.isDraw() ? "Draw" : "Winner: " +
                            (result.getWinnerId() != null ? Bukkit.getOfflinePlayer(result.getWinnerId()).getName() : result.getWinnerTeamName()));
            Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(msg));
        }

        int endDelay = ConfigManager.getInstance().getConfig().getInt("match.end-delay", 5) * 20;
        AsyncUtil.runSyncLater(() -> resetMatch(match), endDelay);
    }

    public void forceWin(Match match, UUID winnerId) {
        MatchResult result = new MatchResult(
                match.getArena().getName(), winnerId, null,
                MatchResult.Outcome.WIN, match.getDurationSeconds(),
                new ArrayList<>(match.getPlayers()));
        endMatch(match, result);
    }

    public void forceWinTeam(Match match, String teamName) {
        MatchResult result = new MatchResult(
                match.getArena().getName(), null, teamName,
                MatchResult.Outcome.WIN, match.getDurationSeconds(),
                new ArrayList<>(match.getPlayers()));
        endMatch(match, result);
    }

    public void checkEndConditions(Match match) {
        Arena arena = match.getArena();
        List<UUID> alive = new ArrayList<>(match.getAlivePlayers());

        // The game type drives the win condition; arena goal value overrides the
        // mode default only when an admin set one (> 0).
        var mode = arena.getGameMode();
        GoalType goal = mode.getGoalType();
        int goalValue = arena.getGoalValue() > 0 ? arena.getGoalValue() : mode.getDefaultGoalValue();

        switch (goal) {
            case KILLS -> {
                UUID leader = match.getLeader();
                if (leader != null && match.getKills(leader) >= goalValue) {
                    MatchResult result = new MatchResult(arena.getName(), leader, null,
                            MatchResult.Outcome.WIN, match.getDurationSeconds(),
                            new ArrayList<>(match.getPlayers()));
                    endMatch(match, result);
                }
            }
            case LAST_ALIVE -> {
                if (alive.size() == 1) {
                    MatchResult result = new MatchResult(arena.getName(), alive.get(0), null,
                            MatchResult.Outcome.WIN, match.getDurationSeconds(),
                            new ArrayList<>(match.getPlayers()));
                    endMatch(match, result);
                } else if (alive.isEmpty()) {
                    endMatch(match, buildDrawResult(match));
                }
            }
        }
    }

    // ── Reset ────────────────────────────────────────────────────

    private void resetMatch(Match match) {
        match.setState(MatchState.RESETTING);

        // Remove all players
        new ArrayList<>(match.getPlayers()).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) leaveArena(player);
        });
        new ArrayList<>(match.getSpectators()).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) leaveSpectator(player);
        });

        plugin.getVisual().getScoreboardManager().removeAllBoards(match);
        plugin.getVisual().getBossBarManager().removeAll(match);
        cleanupMatch(match);
    }

    private void cleanupMatch(Match match) {
        match.cancelTasks();
        activeMatches.remove(match.getArena().getName());
        match.getArena().setState(ArenaState.WAITING);
    }

    // ── Inventory save / restore ──────────────────────────────────

    private void saveInventory(Player player, Match match) {
        if (!ConfigManager.getInstance().getConfig().getBoolean("arena.inventory-restore", true)) return;
        match.saveInventory(player.getUniqueId(),
                player.getInventory().getContents(),
                player.getInventory().getArmorContents());
        player.getInventory().clear();
    }

    private void restoreInventory(Player player, Match match) {
        if (!ConfigManager.getInstance().getConfig().getBoolean("arena.inventory-restore", true)) return;
        org.bukkit.inventory.ItemStack[] contents = match.getSavedContents(player.getUniqueId());
        org.bukkit.inventory.ItemStack[] armor    = match.getSavedArmor(player.getUniqueId());
        if (contents != null) player.getInventory().setContents(contents);
        if (armor != null)    player.getInventory().setArmorContents(armor);
    }

    // ── Team assignment ───────────────────────────────────────────

    private void assignTeams(Match match) {
        Arena arena = match.getArena();
        List<TeamColor> colors = arena.getTeamConfig().getColors();
        int teamCount = arena.getTeamConfig().getTeamCount();

        List<MatchTeam> teams = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            TeamColor color = colors.size() > i ? colors.get(i) : TeamColor.RED;
            teams.add(match.createTeam("Team " + color.name(), color));
        }

        List<UUID> playerList = new ArrayList<>(match.getPlayers());
        Collections.shuffle(playerList);
        for (int i = 0; i < playerList.size(); i++) {
            match.assignTeam(playerList.get(i), teams.get(i % teamCount));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    public void broadcastArena(Match match, String miniMessage) {
        for (UUID uuid : match.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) MessageUtil.send(player, miniMessage);
        }
    }

    private MatchResult buildTimeoutResult(Match match) {
        return new MatchResult(match.getArena().getName(), null, null,
                MatchResult.Outcome.TIMEOUT, match.getDurationSeconds(),
                new ArrayList<>(match.getPlayers()));
    }

    private MatchResult buildDrawResult(Match match) {
        return new MatchResult(match.getArena().getName(), null, null,
                MatchResult.Outcome.DRAW, match.getDurationSeconds(),
                new ArrayList<>(match.getPlayers()));
    }

    // ── Lookup ───────────────────────────────────────────────────

    public Optional<Match> getMatch(Arena arena) {
        return Optional.ofNullable(activeMatches.get(arena.getName()));
    }

    public Optional<String> getArenaName(Player player) {
        return Optional.ofNullable(playerArena.get(player.getUniqueId()))
                .map(s -> s.replace(":spectator", ""));
    }

    public boolean isInArena(Player player) {
        return playerArena.containsKey(player.getUniqueId());
    }

    public boolean isSpectating(Player player) {
        String key = playerArena.get(player.getUniqueId());
        return key != null && key.endsWith(":spectator");
    }

    public Collection<Match> getActiveMatches() {
        return Collections.unmodifiableCollection(activeMatches.values());
    }
}
