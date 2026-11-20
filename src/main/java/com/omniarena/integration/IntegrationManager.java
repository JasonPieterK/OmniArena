package com.omniarena.integration;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import com.omniarena.util.AsyncUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Owner of every optional-plugin hook (Vault, PlaceholderAPI, WorldGuard, LuckPerms, DecentHolograms,
 * FancyHolograms, Citizens, Multiverse-Core) and of the services built on them (leaderboard cache and
 * holograms, NPCs).
 *
 * <p>Isolation rules (see CLAUDE.md): a hook is only called through {@link #safe}, which catches Throwable
 * (including NoClassDefFoundError/LinkageError from a changed or missing API), logs the first failure,
 * records {@code lastError} and switches the hook off after 3 consecutive failures
 * ({@link FailureTracker}). A hook is also switched off when its plugin is disabled and switched back on
 * when that plugin is enabled again. {@code /oa reload} re-runs {@link #shutdown()} + {@link #load()}, which
 * also clears tripped hooks.
 *
 * <p>{@link #start()}/{@link #stop()} own the periodic tasks (leaderboard cache refresh every ~30 s,
 * static leaderboard refresh, NPC click handler); both are idempotent and wired into onEnable, onDisable,
 * {@code /oa plugin stop} and {@code /oa plugin start}.
 */
public class IntegrationManager implements Listener {

    private static final long CACHE_REFRESH_TICKS = 30L * 20L;

    /** Mutable per-hook state. */
    private static final class Hook {
        final String key;
        final String pluginName;
        volatile boolean active;
        volatile boolean dependencyDown;
        volatile String lastError;

        Hook(String key, String pluginName) { this.key = key; this.pluginName = pluginName; }
    }

    private final OmniArena plugin;
    private final Map<String, Hook> hooks = new LinkedHashMap<>();
    private final FailureTracker tracker = new FailureTracker();
    private final Set<String> logged = ConcurrentHashMap.newKeySet();

    private volatile VaultIntegration vault;
    private volatile PlaceholderAPIIntegration placeholderAPI;
    private volatile WorldGuardIntegration worldGuard;
    private volatile LuckPermsIntegration luckPerms;
    private volatile HologramHook decentHolograms;
    private volatile HologramHook fancyHolograms;
    private volatile CitizensIntegration citizens;
    private volatile MultiverseIntegration multiverse;

    private final LeaderboardCache leaderboardCache = new LeaderboardCache();
    private final LeaderboardManager leaderboards;
    private final NpcManager npcs;
    private BukkitTask cacheTask;
    private boolean listenerRegistered;

    public IntegrationManager(OmniArena plugin) {
        this.plugin = plugin;
        addHook("vault", "Vault");
        addHook("placeholderapi", "PlaceholderAPI");
        addHook("worldguard", "WorldGuard");
        addHook("luckperms", "LuckPerms");
        addHook("decentholograms", "DecentHolograms");
        addHook("fancyholograms", "FancyHolograms");
        addHook("citizens", "Citizens");
        addHook("multiverse", "Multiverse-Core");
        this.leaderboards = new LeaderboardManager(plugin, this);
        this.npcs = new NpcManager(plugin, this);
    }

    private void addHook(String key, String pluginName) { hooks.put(key, new Hook(key, pluginName)); }

    // ── accessors (null / false when the hook is off) ────────────

    public VaultIntegration getVault() { return vault; }
    public PlaceholderAPIIntegration getPlaceholderAPI() { return placeholderAPI; }
    public WorldGuardIntegration getWorldGuard() { return worldGuard; }
    public LuckPermsIntegration getLuckPerms() { return luckPerms; }
    public CitizensIntegration citizens() { return citizens; }
    public MultiverseIntegration multiverse() { return multiverse; }
    public LeaderboardCache leaderboardCache() { return leaderboardCache; }
    public LeaderboardManager leaderboards() { return leaderboards; }
    public NpcManager npcs() { return npcs; }

    public boolean hasVault()          { return vault != null; }
    public boolean hasPlaceholderAPI() { return placeholderAPI != null; }
    public boolean hasWorldGuard()     { return worldGuard != null; }
    public boolean hasLuckPerms()      { return luckPerms != null; }
    public boolean hasCitizens()       { return citizens != null; }
    public boolean hasMultiverse()     { return multiverse != null; }

    /** The hologram plugin to use for leaderboards: DecentHolograms first, then FancyHolograms; null when neither is live. */
    public HologramHook hologramHook() {
        HologramHook d = decentHolograms;
        return d != null ? d : fancyHolograms;
    }

    /** A specific live hologram hook by key ("decentholograms" / "fancyholograms"), or null. */
    public HologramHook hologramHook(String key) {
        if ("decentholograms".equals(key)) return decentHolograms;
        if ("fancyholograms".equals(key)) return fancyHolograms;
        return null;
    }

    // ── load / shutdown ──────────────────────────────────────────

    /** Sets up every enabled hook whose plugin is present, then starts the periodic services. Safe to call again after {@link #shutdown()}. */
    public synchronized void load() {
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }
        for (Hook h : hooks.values()) {
            tracker.reset(h.key);
            logged.remove(h.key);
            h.lastError = null;
            h.dependencyDown = false;
            setUp(h);
        }
        start();
    }

    /** Releases all hooks and stops periodic services. Call from onDisable (and before re-running load() on reload). */
    public synchronized void shutdown() {
        stop();
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
        for (Hook h : hooks.values()) release(h);
    }

    /** (Re)starts the periodic services: leaderboard cache refresh, leaderboard holograms, NPC click handling. Idempotent. */
    public synchronized void start() {
        stop();
        refreshCache();
        cacheTask = AsyncUtil.runSyncRepeating(this::refreshCache, CACHE_REFRESH_TICKS, CACHE_REFRESH_TICKS);
        leaderboards.start();
        npcs.start();
    }

    /** Stops the periodic services (used by /oa plugin stop and shutdown). Hooks stay registered. Idempotent. */
    public synchronized void stop() {
        if (cacheTask != null) { cacheTask.cancel(); cacheTask = null; }
        leaderboards.stop();
        npcs.stop();
    }

    private void refreshCache() {
        if (plugin.isStopped()) return;
        try {
            leaderboardCache.refresh();
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError vme) throw vme;
            ConfigManager.debugLog(() -> "Leaderboard cache refresh failed: " + t);
        }
    }

    // ── per-hook setup / release ─────────────────────────────────

    private void setUp(Hook h) {
        h.active = false;
        if (!enabled(h.key)) return;
        Plugin dep = Bukkit.getPluginManager().getPlugin(h.pluginName);
        if (dep == null || !dep.isEnabled()) return;
        try {
            boolean ok = switch (h.key) {
                case "vault" -> setUpVault();
                case "placeholderapi" -> setUpPlaceholderApi();
                case "worldguard" -> { worldGuard = new WorldGuardIntegration(); info("WorldGuard hooked."); yield true; }
                case "luckperms" -> { luckPerms = new LuckPermsIntegration(); info("LuckPerms hooked."); yield true; }
                case "decentholograms" -> { decentHolograms = new DecentHologramsHook(dep); info("DecentHolograms hooked (/oa leaderboard)."); yield true; }
                case "fancyholograms" -> { fancyHolograms = new FancyHologramsHook(dep); info("FancyHolograms hooked (/oa leaderboard)."); yield true; }
                case "citizens" -> { citizens = new CitizensIntegration(plugin, dep); info("Citizens hooked (/oa npc)."); yield true; }
                case "multiverse" -> { multiverse = new MultiverseIntegration(dep); info("Multiverse-Core hooked (worlds are loaded on demand)."); yield true; }
                default -> false;
            };
            h.active = ok;
            if (!ok) release(h);
            else if (h.key.equals("citizens")) npcs.attach();
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError vme) throw vme;
            release(h);
            String msg = describe(t);
            h.lastError = msg;
            tracker.recordFailure(h.key, msg);
            plugin.getLogger().warning("[Integrations] " + h.pluginName + " is installed but could not be hooked (" + msg
                    + "). It stays off; the rest of OmniArena works. Fix: update " + h.pluginName + " (or OmniArena), or set integrations." + h.key + ": false in config.yml.");
        }
    }

    private boolean setUpVault() {
        VaultIntegration v = new VaultIntegration(plugin);
        if (v.setup()) {
            vault = v;
            info("Vault hooked.");
            return true;
        }
        plugin.getLogger().warning("[Integrations] Vault is installed but no economy provider is registered, so betting/rewards are disabled. Fix: install an economy plugin that supports Vault (e.g. EssentialsX) and restart.");
        return false;
    }

    private boolean setUpPlaceholderApi() {
        PlaceholderAPIIntegration p = new PlaceholderAPIIntegration(plugin);
        p.tryRegister();
        placeholderAPI = p;
        info("PlaceholderAPI hooked.");
        return true;
    }

    private void release(Hook h) {
        h.active = false;
        try {
            switch (h.key) {
                case "placeholderapi" -> {
                    PlaceholderAPIIntegration p = placeholderAPI;
                    placeholderAPI = null;
                    if (p != null) p.unregisterExpansion();
                }
                case "vault" -> vault = null;
                case "worldguard" -> worldGuard = null;
                case "luckperms" -> luckPerms = null;
                case "decentholograms" -> decentHolograms = null;
                case "fancyholograms" -> fancyHolograms = null;
                case "citizens" -> {
                    CitizensIntegration c = citizens;
                    citizens = null;
                    if (c != null) c.unregister();
                }
                case "multiverse" -> multiverse = null;
                default -> { }
            }
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError vme) throw vme;
            ConfigManager.debugLog(() -> "Releasing hook " + h.key + " failed: " + t);
        }
    }

    // ── isolation wrapper ────────────────────────────────────────

    /**
     * Runs {@code action} against a hook. Does nothing when the hook is off. Any Throwable (also
     * LinkageError/NoClassDefFoundError) is caught, recorded and counted; at 3 consecutive failures the hook is disabled.
     *
     * @return true when the action ran without error
     */
    public boolean safe(String hook, Runnable action) {
        Hook h = hooks.get(hook);
        if (h == null || !h.active) return false;
        try {
            action.run();
            tracker.recordSuccess(hook);
            return true;
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError vme) throw vme;
            onFailure(h, t);
            return false;
        }
    }

    /** Like {@link #safe(String, Runnable)} but returns the supplier's value, or {@code fallback} when the hook is off or failed. */
    public <T> T safe(String hook, Supplier<T> action, T fallback) {
        Hook h = hooks.get(hook);
        if (h == null || !h.active) return fallback;
        try {
            T r = action.get();
            tracker.recordSuccess(hook);
            return r;
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError vme) throw vme;
            onFailure(h, t);
            return fallback;
        }
    }

    private void onFailure(Hook h, Throwable t) {
        String msg = describe(t);
        h.lastError = msg;
        boolean trip = tracker.recordFailure(h.key, msg);
        if (logged.add(h.key)) {
            plugin.getLogger().warning("[Integrations] " + h.pluginName + " hook error: " + msg
                    + ". The call was skipped; the hook is switched off after " + FailureTracker.DEFAULT_LIMIT + " failures in a row.");
        }
        if (trip) {
            h.active = false; // short-circuits further calls immediately
            plugin.getLogger().warning("[Integrations] " + h.pluginName + " hook disabled after " + FailureTracker.DEFAULT_LIMIT
                    + " failures (last: " + msg + "). Fix: update " + h.pluginName + " or OmniArena, then /oa reload (or restart) to retry.");
            Runnable r = () -> release(h);
            try {
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, r); else r.run();
            } catch (RuntimeException e) {
                r.run();
            }
        }
    }

    /** Root cause as "ClassName: message", capped so it stays readable in chat and logs. */
    static String describe(Throwable t) {
        Throwable r = t;
        for (int i = 0; i < 8 && r.getCause() != null && r.getCause() != r; i++) r = r.getCause();
        String m = r.getMessage();
        String s = r.getClass().getSimpleName() + (m != null && !m.isBlank() ? ": " + m : "");
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    // ── status ───────────────────────────────────────────────────

    /** One entry per known hook (name, plugin installed, hook live, last error), for /oa doctor and diagnostics. */
    public List<IntegrationStatus> statuses() {
        List<IntegrationStatus> out = new ArrayList<>();
        for (Hook h : hooks.values()) {
            Plugin dep = Bukkit.getPluginManager().getPlugin(h.pluginName);
            boolean present = dep != null && dep.isEnabled() && !h.dependencyDown;
            String err = h.lastError != null ? h.lastError : tracker.lastError(h.key);
            out.add(new IntegrationStatus(h.key, present, h.active, err));
        }
        return Collections.unmodifiableList(out);
    }

    // ── plugin enable/disable tracking ───────────────────────────

    private Hook byPlugin(String pluginName) {
        for (Hook h : hooks.values()) if (h.pluginName.equalsIgnoreCase(pluginName)) return h;
        return null;
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        Hook h = byPlugin(event.getPlugin().getName());
        if (h == null) return;
        h.dependencyDown = true;
        if (h.active) {
            release(h);
            info(h.pluginName + " was disabled: its hook is off until it is enabled again.");
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        Hook h = byPlugin(event.getPlugin().getName());
        if (h == null) return;
        h.dependencyDown = false;
        if (h.active || plugin.isStopped()) return;
        tracker.reset(h.key);
        logged.remove(h.key);
        h.lastError = null;
        setUp(h);
        if (h.active) info(h.pluginName + " was enabled: its hook is back on.");
    }

    // ── convenience wrappers around hook calls ───────────────────

    /** Deposits through Vault. Returns the formatted amount, or null when there is no live economy or the call failed. */
    public String deposit(Player player, double amount) {
        VaultIntegration v = vault;
        if (v == null) return null;
        return safe("vault", () -> { v.deposit(player, amount); return v.format(amount); }, null);
    }

    /** Offline variant of {@link #deposit}. */
    public String depositOffline(OfflinePlayer player, double amount) {
        VaultIntegration v = vault;
        if (v == null) return null;
        return safe("vault", () -> { v.depositOffline(player, amount); return v.format(amount); }, null);
    }

    /** True when WorldGuard is live and its PVP flag denies PvP at this location. */
    public boolean isPvpDenied(Location loc) {
        WorldGuardIntegration wg = worldGuard;
        if (wg == null) return false;
        return safe("worldguard", () -> wg.isPvpDenied(loc), false);
    }

    // ── helpers ──────────────────────────────────────────────────

    private void info(String msg) { plugin.getLogger().info("[Integrations] " + msg); }

    /** Honours the integrations.<name> toggle in config.yml (default true). */
    private boolean enabled(String key) {
        var cm = ConfigManager.getInstance();
        return cm == null || cm.isIntegrationEnabled(key);
    }
}
