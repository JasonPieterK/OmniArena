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
    /** Players mid plugin-initiated teleport; PlayerTeleportListener ignores them. */
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();

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

    public boolean isInternalTeleport(UUID uuid) { return internalTeleports.contains(uuid); }

    /** Teleports a player on behalf of the plugin without triggering out-of-arena handling. */
    public void markInternalTeleport(Player player, Location to) {
        if (to == null) return;
        internalTeleports.add(player.getUniqueId());
        try { player.teleport(to); }
        finally { internalTeleports.remove(player.getUniqueId()); }
    }

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

    public Match getOrCreateMatch(Arena arena) {
        return activeMatches.computeIfAbsent(arena.getName(), k -> {
            Match m = new Match(arena);
            startCountdown(m);
            return m;
        });
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
        if (spawn != null) markInternalTeleport(player, spawn);

        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        
        // Full health, saturation, remove effects, lock in place
        player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        for (org.bukkit.potion.PotionEffect fx : player.getActivePotionEffects()) {
            player.removePotionEffect(fx.getType()); // getActivePotionEffects() is a copy
        }
        player.setWalkSpeed(0);
        player.setFlySpeed(0);

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

        // Leaving a running ranked (queue) match counts as a forfeit: the leaver loses ELO.
        MatchResult forfeit = null;
        if (match.getState() == MatchState.RUNNING && match.isRanked() && !plugin.isStopped()
                && match.hasPlayer(player.getUniqueId())) {
            List<UUID> others = match.getPlayers().stream()
                    .filter(u -> !u.equals(player.getUniqueId())).toList();
            if (others.size() == 1) {
                forfeit = new MatchResult(arenaName, others.get(0), player.getUniqueId(), null,
                        MatchResult.Outcome.FORFEIT, match.getDurationSeconds(),
                        new ArrayList<>(match.getPlayers()));
            }
        }

        // Restore before removePlayer(), which clears the saved inventory
        restoreInventory(player, match);
        match.removePlayer(player.getUniqueId());
        plugin.getVisual().getScoreboardManager().removeBoard(player);

        Location back = player.getBedSpawnLocation() != null
                ? player.getBedSpawnLocation()
                : player.getWorld().getSpawnLocation();
        player.teleport(back);
        player.setGameMode(GameMode.SURVIVAL);
        // Undo the lobby freeze / spectator state so leaving early doesn't leave the player stuck
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setInvisible(false);

        MessageUtil.send(player, ConfigManager.getInstance().getMessage("leave-arena")
                .replace("{arena}", arenaName));

        if (forfeit != null) {
            endMatch(match, forfeit);
        } else if (match.getState() == MatchState.RUNNING) {
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
                
                // Show title instead of chat spam
                for (UUID uuid : match.getPlayers()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        com.omniarena.util.MessageUtil.sendTitle(player,
                            "<yellow>Waiting for players</yellow>",
                            "<gray>" + match.getPlayerCount() + "/" + match.getArena().getMinPlayers() + "</gray>",
                            0, 20, 0
                        );
                        // Lock player in place during waiting
                        player.setWalkSpeed(0);
                        player.setFlySpeed(0);
                        player.setAllowFlight(false);
                    }
                }
                
                secondsLeft[0] = match.getArena().getCountdown();
                return;
            }

            match.setState(MatchState.STARTING);
            
            // Unlock players when starting
            for (UUID uuid : match.getPlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                }
            }
            
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
        plugin.getBettingManager().onMatchStart(arena.getName());

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
        if (spawn != null) markInternalTeleport(player, spawn);

        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getAttribute(
                org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);

        // Hand the player the queued kit if any, else this game type's full loadout.
        applyLoadout(player, match);

        if (arena.getSpawnProtection() > 0) {
            match.setSpawnProtection(player.getUniqueId(), arena.getSpawnProtection());
        }
    }

    /** Equips the queued kit when the match has one, otherwise the arena game mode's loadout. */
    public void applyLoadout(Player player, Match match) {
        String kit = match.getQueueKit();
        if (kit != null) {
            var adminKit = com.omniarena.kit.KitManager.getInstance().getAdminKit(kit);
            if (adminKit.isPresent()) {
                com.omniarena.kit.KitManager.getInstance().equipKit(player, adminKit.get());
                return;
            }
        }
        match.getArena().getGameMode().applyLoadout(player);
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

        // Update ELO ratings for ranked (queue) matches only
        if (match.isRanked() && result.isDraw() && result.getParticipants() != null
                && result.getParticipants().size() == 2) {
            UUID a = result.getParticipants().get(0), b = result.getParticipants().get(1);
            String an = Bukkit.getOfflinePlayer(a).getName();
            String bn = Bukkit.getOfflinePlayer(b).getName();
            plugin.getEloManager().recordDraw(a, b, an != null ? an : "Unknown", bn != null ? bn : "Unknown");
        } else if (match.isRanked() && result.getWinnerId() != null && result.getLoserId() != null) {
            String winnerName = Bukkit.getOfflinePlayer(result.getWinnerId()).getName();
            String loserName = Bukkit.getOfflinePlayer(result.getLoserId()).getName();
            plugin.getEloManager().recordMatch(result.getWinnerId(), result.getLoserId(), 
                    winnerName != null ? winnerName : "Unknown", 
                    loserName != null ? loserName : "Unknown");
        }

        // Betting payout
        plugin.getBettingManager().processMatchResults(
                match.getArena().getName(), result.getWinnerId(), result.getLoserId());
        plugin.getTournamentManager().onMatchEnd(result);

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
                match.getArena().getName(), winnerId, null, null,
                MatchResult.Outcome.WIN, match.getDurationSeconds(),
                new ArrayList<>(match.getPlayers()));
        endMatch(match, result);
    }

    public void forceWinTeam(Match match, String teamName) {
        MatchResult result = new MatchResult(
                match.getArena().getName(), null, null, teamName,
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
                    UUID loserUuid = match.getPlayers().stream()
                            .filter(u -> !u.equals(leader)).findFirst().orElse(null);
                    MatchResult result = new MatchResult(arena.getName(), leader, loserUuid, null,
                            MatchResult.Outcome.WIN, match.getDurationSeconds(),
                            new ArrayList<>(match.getPlayers()));
                    endMatch(match, result);
                }
            }
            case LAST_ALIVE -> {
                if (alive.size() == 1) {
                    UUID winnerUuid = alive.get(0);
                    UUID loserUuid = match.getPlayers().stream()
                            .filter(u -> !u.equals(winnerUuid)).findFirst().orElse(null);
                    MatchResult result = new MatchResult(arena.getName(), winnerUuid, loserUuid, null,
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
        // Only release the arena if this match still owns it: the delayed resetMatch()
        // of an already-cleaned-up match must not tear down a newer match on the same arena.
        if (!activeMatches.remove(match.getArena().getName(), match)) return;
        match.getArena().setState(ArenaState.WAITING);
        plugin.getBettingManager().onMatchCleanup(match.getArena().getName());
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
        return new MatchResult(match.getArena().getName(), null, null, null,
                MatchResult.Outcome.TIMEOUT, match.getDurationSeconds(),
                new ArrayList<>(match.getPlayers()));
    }

    private MatchResult buildDrawResult(Match match) {
        return new MatchResult(match.getArena().getName(), null, null, null,
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
