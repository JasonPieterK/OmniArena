package com.omniarena.health;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaState;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.util.AsyncUtil;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime self-healing. Every {@code advanced.health.watchdog-seconds} it looks for arenas stuck in ENDING/RESETTING,
 * matches whose arena vanished, players mapped to a vanished match and core periodic tasks that stopped, and fixes
 * them. Every fix is recorded in the {@link HealthReport}. Start/stop are idempotent; it is gated by isStopped().
 */
public final class Watchdog {

    private static Watchdog instance;

    /** First time an arena was seen in a transitional state (state + timestamp). */
    private record Seen(ArenaState state, long since) {}

    private final OmniArena plugin;
    private final Map<String, Seen> transitional = new ConcurrentHashMap<>();
    private BukkitTask task;

    public Watchdog(OmniArena plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static Watchdog getInstance() { return instance; }

    private boolean enabled() {
        return ConfigManager.getInstance().getConfig().getBoolean("health.enabled", true);
    }

    /** (Re)starts the periodic check; idempotent. */
    public synchronized void start() {
        stop();
        if (!enabled()) return;
        long ticks = Math.max(5, ConfigManager.getInstance().getConfig().getInt("advanced.health.watchdog-seconds", 30)) * 20L;
        task = AsyncUtil.runSyncRepeating(ErrorGuard.wrap("watchdog", this::tick), ticks, ticks);
    }

    public synchronized void stop() {
        if (task != null) { task.cancel(); task = null; }
        transitional.clear();
    }

    public boolean isRunning() {
        return task != null && !task.isCancelled();
    }

    private void tick() {
        if (plugin.isStopped()) return;
        runChecks();
    }

    /** Seconds the arena has been seen in ENDING/RESETTING, or -1 if it is in a normal state. */
    public long transitionalSeconds(Arena arena) {
        Seen s = transitional.get(arena.getName().toLowerCase(Locale.ROOT));
        if (s == null || s.state() != arena.getState()) return -1;
        return (System.currentTimeMillis() - s.since()) / 1000L;
    }

    public int stuckThresholdSeconds() {
        return Math.max(10, ConfigManager.getInstance().getConfig().getInt("advanced.health.stuck-arena-seconds", 180));
    }

    /** Arenas currently in ENDING/RESETTING longer than the threshold without an active regen. */
    public List<Arena> stuckArenas() {
        List<Arena> out = new ArrayList<>();
        var restore = plugin.getBlockRestoreModule();
        for (Arena a : plugin.getArenaManager().getAll()) {
            if (!isTransitional(a.getState())) continue;
            if (a.getState() == ArenaState.RESETTING && restore != null && restore.isRegenerating(a.getName())) continue;
            if (transitionalSeconds(a) >= stuckThresholdSeconds()) out.add(a);
        }
        return out;
    }

    private static boolean isTransitional(ArenaState s) {
        return s == ArenaState.ENDING || s == ArenaState.RESETTING;
    }

    /**
     * Runs all checks now and applies every fix.
     * @return human-readable descriptions of the fixes performed (also recorded in the health report)
     */
    public List<String> runChecks() {
        List<String> fixes = new ArrayList<>();
        ErrorGuard.run("watchdog-matches", () -> fixVanishedArenaMatches(fixes));
        ErrorGuard.run("watchdog-arenas", () -> fixStuckArenas(fixes));
        ErrorGuard.run("watchdog-players", () -> fixOrphans(fixes));
        ErrorGuard.run("watchdog-tasks", () -> fixTasks(fixes));
        for (String f : fixes) {
            HealthReport.get().recordRepair("watchdog", f);
            plugin.getLogger().warning("[Watchdog] " + f);
        }
        return fixes;
    }

    private void fixVanishedArenaMatches(List<String> fixes) {
        MatchManager mm = plugin.getMatchManager();
        for (Match m : new ArrayList<>(mm.getActiveMatches())) {
            String name = m.getArena().getName();
            Arena current = plugin.getArenaManager().get(name).orElse(null);
            if (current == m.getArena()) continue;
            mm.forceCleanup(m);
            fixes.add("Cleaned up the match of deleted arena '" + name + "' and restored its players.");
        }
    }

    private void fixStuckArenas(List<String> fixes) {
        MatchManager mm = plugin.getMatchManager();
        var restore = plugin.getBlockRestoreModule();
        long now = System.currentTimeMillis();
        long limitMs = stuckThresholdSeconds() * 1000L;

        for (Arena arena : plugin.getArenaManager().getAll()) {
            String key = arena.getName().toLowerCase(Locale.ROOT);
            ArenaState st = arena.getState();
            Match match = mm.getMatch(arena).orElse(null);

            // RUNNING/STARTING with no match behind it can never end by itself
            if ((st == ArenaState.RUNNING || st == ArenaState.STARTING) && match == null) {
                arena.setState(ArenaState.WAITING);
                transitional.remove(key);
                fixes.add("Arena '" + arena.getName() + "' was " + st + " with no match; reset to WAITING.");
                continue;
            }

            if (!isTransitional(st)) { transitional.remove(key); continue; }

            Seen seen = transitional.get(key);
            if (seen == null || seen.state() != st) {
                transitional.put(key, new Seen(st, now));
                continue;
            }
            if (now - seen.since() < limitMs) continue;

            boolean regen = restore != null && restore.isRegenerating(arena.getName());
            if (st == ArenaState.RESETTING && regen) {
                transitional.put(key, new Seen(st, now)); // a long regen is fine
                continue;
            }

            long secs = (now - seen.since()) / 1000L;
            if (match != null) mm.forceCleanup(match);
            // forceCleanup may have started a fresh regen (arena legitimately RESETTING again)
            boolean regenNow = restore != null && restore.isRegenerating(arena.getName());
            if (!regenNow && isTransitional(arena.getState())) arena.setState(ArenaState.WAITING);
            transitional.remove(key);
            fixes.add("Arena '" + arena.getName() + "' was stuck in " + st + " for " + secs + "s; "
                    + (match != null ? "force-cleaned its match and " : "") + (regenNow ? "started its regeneration." : "reset it to WAITING."));
        }
        transitional.keySet().removeIf(k -> plugin.getArenaManager().get(k).isEmpty());
    }

    private void fixOrphans(List<String> fixes) {
        int n = plugin.getMatchManager().healOrphanedPlayers();
        if (n > 0) fixes.add("Restored " + n + " player(s) mapped to a match that no longer exists.");
    }

    private void fixTasks(List<String> fixes) {
        var queue = plugin.getQueueManager();
        if (queue != null && !queue.isRunning()) { queue.start(); fixes.add("Restarted the matchmaking queue task."); }
        var party = plugin.getPartyManager();
        if (party != null && !party.isRunning()) { party.start(); fixes.add("Restarted the party invite task."); }
        var tournament = plugin.getTournamentManager();
        if (tournament != null && !tournament.isRunning()) { tournament.scheduleTick(); fixes.add("Restarted the tournament tick task."); }
        if (!plugin.isSignTaskRunning()) { plugin.startSignUpdateTask(); fixes.add("Restarted the sign refresh task."); }
        var combat = plugin.getCombatManager();
        if (combat != null && !combat.isRunning()) { combat.start(); fixes.add("Restarted the combat tag task."); }
    }
}
