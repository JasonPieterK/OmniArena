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

    /** Everything about a player that arena play overwrites; restored when they leave. */
    private record PlayerState(Location location, GameMode gameMode, double health, int food, float saturation,
                               int level, float exp, java.util.Collection<org.bukkit.potion.PotionEffect> effects,
                               boolean allowFlight, boolean flying, float walkSpeed, float flySpeed,
                               boolean invisible, org.bukkit.inventory.ItemStack[] contents,
                               org.bukkit.inventory.ItemStack[] armor) {}

    private final Map<UUID, PlayerState> savedStates = new ConcurrentHashMap<>();
    private PlayerStateStore stateStore;

    /** Crash-safe on-disk copy of saved pre-arena states (lazy: needs the plugin data folder). */
    public PlayerStateStore getStateStore() {
        if (stateStore == null) stateStore = new PlayerStateStore(plugin);
        return stateStore;
    }

    /** Flushes pending state-file writes/deletes; call on plugin disable after players were restored. */
    public void shutdownStateStore() {
        if (stateStore != null) stateStore.shutdown();
    }

    private void saveState(Player p) {
        boolean inv = ConfigManager.getInstance().getConfig().getBoolean("arena.inventory-restore", true);
        if (ConfigManager.getInstance().getConfig().getBoolean("arena.persist-player-state", true)) {
            getStateStore().save(p, inv);
        }
        savedStates.put(p.getUniqueId(), new PlayerState(p.getLocation().clone(), p.getGameMode(), p.getHealth(),
                p.getFoodLevel(), p.getSaturation(), p.getLevel(), p.getExp(),
                new ArrayList<>(p.getActivePotionEffects()), p.getAllowFlight(), p.isFlying(),
                p.getWalkSpeed(), p.getFlySpeed(), p.isInvisible(),
                inv ? p.getInventory().getContents().clone() : null,
                inv ? p.getInventory().getArmorContents().clone() : null));
    }

    /** Puts the player back exactly as they were before joining (or sane defaults if unknown). */
    private void restoreState(Player p) {
        PlayerState s = savedStates.remove(p.getUniqueId());
        if (stateStore != null) stateStore.delete(p.getUniqueId());
        if (p.isDead()) {
            try { p.spigot().respawn(); } catch (Throwable ignored) {}
        }
        for (org.bukkit.potion.PotionEffect fx : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(fx.getType());
        }
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

        Location back = s != null && s.location().getWorld() != null ? s.location()
                : (p.getBedSpawnLocation() != null ? p.getBedSpawnLocation() : p.getWorld().getSpawnLocation());
        markInternalTeleport(p, back);

        if (s == null) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setWalkSpeed(0.2f);
            p.setFlySpeed(0.1f);
            p.setInvisible(false);
            return;
        }
        if (s.contents() != null) {
            p.getInventory().setContents(s.contents());
            p.getInventory().setArmorContents(s.armor());
        }
        p.setGameMode(s.gameMode());
        p.setAllowFlight(s.allowFlight());
        if (s.allowFlight() && s.flying()) p.setFlying(true);
        double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.max(0.5, Math.min(max, s.health())));
        p.setFoodLevel(s.food());
        p.setSaturation(s.saturation());
        p.setLevel(s.level());
        p.setExp(s.exp());
        for (org.bukkit.potion.PotionEffect fx : s.effects()) p.addPotionEffect(fx);
        p.setWalkSpeed(s.walkSpeed());
        p.setFlySpeed(s.flySpeed());
        p.setInvisible(s.invisible());
    }

    private int effectiveMin(Match match) {
        int min = match.getArena().getMinPlayers();
        return match.isRanked() ? Math.max(1, Math.min(2, min)) : min;
    }

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

    /** True when an in-progress tournament uses this arena and the player is not in one of its pending matches. */
    public boolean isArenaReserved(String arenaName, UUID uuid) {
        var tm = plugin.getTournamentManager();
        if (tm == null) return false;
        for (var t : tm.getAll()) {
            if (t.getState() != com.omniarena.tournament.TournamentState.IN_PROGRESS || t.getBracket() == null) continue;
            if (!arenaName.equalsIgnoreCase(t.getArenaName())) continue;
            boolean allowed = t.getBracket().getCurrentRound().stream()
                    .anyMatch(m -> !m.isCompleted() && !m.isBye() && m.involves(uuid));
            if (!allowed) return true;
        }
        return false;
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
        if (!arena.isJoinable() && !(arena.isRunning() && arena.isMidMatchJoin())) return false;
        if (isArenaReserved(arena.getName(), player.getUniqueId())) {
            MessageUtil.send(player, "<red>That arena is reserved for a tournament match.");
            return false;
        }
        if (arena.isBanned(player.getUniqueId())) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("arena-banned"));
            return false;
        }

        Match match = activeMatches.computeIfAbsent(arena.getName(), k -> {
            Match m = new Match(arena);
            startCountdown(m);
            return m;
        });

        // Joining an arena manually cancels any queue entry
        var qm = com.omniarena.queue.QueueManager.getInstance();
        if (qm != null) qm.removeOnQuit(player.getUniqueId());

        saveState(player);
        player.getInventory().clear();
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
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setInvisible(false);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setLevel(0);
        player.setExp(0f);

        broadcastArena(match, ConfigManager.getInstance().getMessage("join-arena")
                .replace("{arena}", arena.getName()));

        if (!tabTakeover()) plugin.getVisual().getScoreboardManager().createBoard(player, match);

        if (match.getState() == MatchState.RUNNING && arena.isMidMatchJoin()) {
            spawnPlayer(player, match);
        }

        return true;
    }

    public void leaveArena(Player player) {
        String key = playerArena.get(player.getUniqueId());
        if (key == null) return;
        if (key.endsWith(":spectator")) { leaveSpectator(player); return; }
        String arenaName = playerArena.remove(player.getUniqueId());

        Match match = activeMatches.get(arenaName);
        if (match == null || !match.hasPlayer(player.getUniqueId())) {
            // Stale mapping (match already cleaned up): still give the player their state back
            restoreState(player);
            plugin.getVisual().getScoreboardManager().removeBoard(player);
            plugin.getVisual().getBossBarManager().removePlayer(player);
            return;
        }

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

        match.removePlayer(player.getUniqueId());
        plugin.getVisual().getScoreboardManager().removeBoard(player);
        plugin.getVisual().getBossBarManager().removePlayer(player);

        // Restores inventory, armor, xp, health, food, effects, gamemode, flight, speeds and location
        restoreState(player);

        MessageUtil.send(player, ConfigManager.getInstance().getMessage("leave-arena")
                .replace("{arena}", arenaName));

        if (forfeit != null) {
            endMatch(match, forfeit);
        } else if (match.getState() == MatchState.RUNNING && !plugin.isStopped()) {
            checkEndConditions(match);
        }

        // A queued (ranked) match that fell apart before it started: send the other player back to the queue
        if (match.isRanked() && !plugin.isStopped() && match.getPlayerCount() > 0
                && (match.getState() == MatchState.WAITING || match.getState() == MatchState.STARTING)
                && match.getPlayerCount() < effectiveMin(match)) {
            String queue = match.getQueueName();
            for (UUID uuid : new ArrayList<>(match.getPlayers())) {
                Player other = Bukkit.getPlayer(uuid);
                if (other == null) continue;
                leaveArena(other);
                if (queue != null && com.omniarena.queue.QueueManager.getInstance() != null) {
                    MessageUtil.send(other, "<yellow>Your opponent left. Returning you to the queue.");
                    com.omniarena.queue.QueueManager.getInstance().joinQueue(other, queue);
                }
            }
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

        saveState(player);
        match.getSpectators().add(player.getUniqueId());
        playerArena.put(player.getUniqueId(), arena.getName() + ":spectator");

        Location spec = arena.getSpectatorSpawn();
        if (spec != null) player.teleport(spec);

        player.setGameMode(GameMode.SPECTATOR);
        player.setInvisible(true);
        player.setFireTicks(0);

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

        plugin.getVisual().getScoreboardManager().removeBoard(player);
        plugin.getVisual().getBossBarManager().removePlayer(player);
        restoreState(player);
    }

    // ── Countdown ────────────────────────────────────────────────

    private void startCountdown(Match match) {
        match.setState(MatchState.WAITING);
        final int[] secondsLeft = {match.getArena().getCountdown()};
        match.setCountdownSeconds(secondsLeft[0]);

        BukkitTask task = AsyncUtil.runSyncRepeating(() -> {
            if (match.getState() != MatchState.WAITING && match.getState() != MatchState.STARTING) return;
            if (match.getPlayerCount() < effectiveMin(match)) {
                match.setState(MatchState.WAITING);
                
                // Show title instead of chat spam
                for (UUID uuid : match.getPlayers()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        com.omniarena.util.MessageUtil.sendTitle(player,
                            "<yellow>Waiting for players</yellow>",
                            "<gray>" + match.getPlayerCount() + "/" + effectiveMin(match) + "</gray>",
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

        match.setStartedWith(match.getPlayerCount());

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

        if (arena.getLives() > 0) match.getLivesMap().putIfAbsent(player.getUniqueId(), arena.getLives());
        player.setGameMode(GameMode.SURVIVAL);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
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
        if (match.getState() == MatchState.ENDING || match.getState() == MatchState.RESETTING) return;
        if (activeMatches.get(match.getArena().getName()) != match) return;
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

        // Ranked team matches (e.g. queued parties): per-player ELO against the opposing side's average
        if (match.isRanked() && !result.isDraw() && result.getWinnerTeamName() != null && !match.getTeams().isEmpty()) {
            List<UUID> winners = new ArrayList<>(), losers = new ArrayList<>();
            for (MatchTeam t : match.getTeams()) {
                (t.getName().equals(result.getWinnerTeamName()) ? winners : losers).addAll(t.getMembers());
            }
            if (!winners.isEmpty() && !losers.isEmpty()) plugin.getEloManager().recordTeamMatch(winners, losers);
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
        if (match.getState() != MatchState.RUNNING) return;
        Arena arena = match.getArena();
        List<UUID> alive = new ArrayList<>(match.getAlivePlayers());
        boolean teamed = !match.getTeams().isEmpty();

        // Everyone (or every other team) left a match that started with several sides
        if (match.getStartedWith() > 1) {
            if (teamed) {
                Set<MatchTeam> present = new LinkedHashSet<>();
                for (UUID u : match.getPlayers()) {
                    MatchTeam t = match.getTeam(u);
                    if (t != null) present.add(t);
                }
                if (present.size() == 1) {
                    endMatch(match, teamWin(match, present.iterator().next()));
                    return;
                }
            } else if (match.getPlayerCount() == 1) {
                UUID w = match.getPlayers().iterator().next();
                endMatch(match, new MatchResult(arena.getName(), w, null, null, MatchResult.Outcome.WIN,
                        match.getDurationSeconds(), new ArrayList<>(match.getPlayers())));
                return;
            }
        }

        // The game type drives the win condition; arena goal value overrides the
        // mode default only when an admin set one (> 0).
        var mode = arena.getGameMode();
        GoalType goal = mode.getGoalType();
        int goalValue = arena.getGoalValue() > 0 ? arena.getGoalValue() : mode.getDefaultGoalValue();

        // Per-player lives on a kills-goal arena: last player/team with lives left also wins
        if (arena.getLives() > 0 && goal == GoalType.KILLS && match.getStartedWith() > 1) {
            boolean teamWon = false;
            if (teamed) {
                for (MatchTeam t : match.getTeams()) {
                    if (t.getKills() >= goalValue) { teamWon = true; break; }
                }
            } else {
                UUID l = match.getLeader();
                teamWon = l != null && match.getKills(l) >= goalValue;
            }
            if (!teamWon) {
                checkLastAlive(match, alive, teamed);
                if (match.getState() != MatchState.RUNNING) return;
            }
        }

        switch (goal) {
            case KILLS -> {
                if (teamed) {
                    for (MatchTeam t : match.getTeams()) {
                        if (t.getKills() >= goalValue) {
                            endMatch(match, teamWin(match, t));
                            return;
                        }
                    }
                    return;
                }
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
            case LAST_ALIVE -> checkLastAlive(match, alive, teamed);
            default -> {}
        }
    }

    /** Last-one/last-team-standing resolution (also used for KILLS modes with per-player lives). */
    private void checkLastAlive(Match match, List<UUID> alive, boolean teamed) {
        Arena arena = match.getArena();
        if (teamed) {
            Set<MatchTeam> aliveTeams = new LinkedHashSet<>();
            for (UUID u : alive) {
                MatchTeam t = match.getTeam(u);
                if (t != null) aliveTeams.add(t);
            }
            if (aliveTeams.size() == 1) endMatch(match, teamWin(match, aliveTeams.iterator().next()));
            else if (aliveTeams.isEmpty()) endMatch(match, buildDrawResult(match));
        } else if (alive.size() == 1) {
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

    private MatchResult teamWin(Match match, MatchTeam team) {
        return new MatchResult(match.getArena().getName(), null, null, team.getName(),
                MatchResult.Outcome.WIN, match.getDurationSeconds(), new ArrayList<>(match.getPlayers()));
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
        // Don't strand spectators on a match that no longer exists
        for (UUID uuid : new ArrayList<>(match.getSpectators())) {
            Player sp = Bukkit.getPlayer(uuid);
            if (sp != null) leaveSpectator(sp);
            else playerArena.remove(uuid);
        }
        match.getSpectators().clear();
        match.getArena().setState(ArenaState.WAITING);
        plugin.getBettingManager().onMatchCleanup(match.getArena().getName());
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
        // Queued parties arrive with a preset side so they stay together
        List<UUID> unassigned = new ArrayList<>();
        for (UUID u : playerList) {
            Integer side = match.getPresetSides().get(u);
            if (side != null && teamCount > 0) match.assignTeam(u, teams.get(side % teamCount));
            else unassigned.add(u);
        }
        for (UUID u : unassigned) {
            MatchTeam smallest = teams.get(0);
            for (MatchTeam t : teams) if (t.getMembers().size() < smallest.getMembers().size()) smallest = t;
            match.assignTeam(u, smallest);
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
        // Kill-based modes: whoever is strictly ahead when time runs out wins
        if (match.getArena().getGameMode().getGoalType() == GoalType.KILLS && match.getPlayerCount() > 1) {
            if (!match.getTeams().isEmpty()) {
                List<MatchTeam> sorted = new ArrayList<>(match.getTeams());
                sorted.sort((a, b) -> Integer.compare(b.getKills(), a.getKills()));
                if (sorted.size() >= 2 && sorted.get(0).getKills() > sorted.get(1).getKills()) {
                    return teamWin(match, sorted.get(0));
                }
            } else {
                List<UUID> sorted = match.getRankedPlayers();
                if (sorted.size() >= 2 && match.getKills(sorted.get(0)) > match.getKills(sorted.get(1))) {
                    UUID loser = sorted.size() == 2 ? sorted.get(1) : null;
                    return new MatchResult(match.getArena().getName(), sorted.get(0), loser, null,
                            MatchResult.Outcome.WIN, match.getDurationSeconds(), new ArrayList<>(match.getPlayers()));
                }
            }
        }
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
