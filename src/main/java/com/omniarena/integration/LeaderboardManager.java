package com.omniarena.integration;

import com.omniarena.OmniArena;
import com.omniarena.util.AsyncUtil;
import com.omniarena.util.YamlUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Leaderboard holograms created through DecentHolograms or FancyHolograms ({@code /oa leaderboard}).
 * The hologram itself lives in that plugin; OmniArena persists only {@code leaderboards.yml}
 * (id, metric, size, hook, position). With PlaceholderAPI the hologram text is made of
 * {@code %omniarena_top_..%} placeholders and the hologram plugin refreshes it; without it OmniArena
 * writes static lines and re-pushes them every 60 s. {@link #start()}/{@link #stop()} are idempotent.
 */
public final class LeaderboardManager {

    public enum Status { OK, NO_HOOK, EXISTS, INVALID_ID, FAILED, NOT_FOUND }

    /** One persisted leaderboard. */
    public static final class Board {
        final String id;
        final LeaderboardMetric metric;
        final int size;
        final String hookKey;
        volatile boolean placeholders;
        final String world;
        final double x, y, z;

        Board(String id, LeaderboardMetric metric, int size, String hookKey, boolean placeholders, String world, double x, double y, double z) {
            this.id = id; this.metric = metric; this.size = size; this.hookKey = hookKey;
            this.placeholders = placeholders; this.world = world; this.x = x; this.y = y; this.z = z;
        }

        public String id() { return id; }
        public LeaderboardMetric metric() { return metric; }
        public int size() { return size; }
        public String hookKey() { return hookKey; }
        public String world() { return world; }
        public String position() { return world + " " + Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z); }
        public boolean placeholders() { return placeholders; }
    }

    public static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final long REFRESH_TICKS = 60L * 20L;

    private final OmniArena plugin;
    private final IntegrationManager im;
    private final File file;
    private final Map<String, Board> boards = new ConcurrentHashMap<>();
    private BukkitTask task;

    public LeaderboardManager(OmniArena plugin, IntegrationManager im) {
        this.plugin = plugin;
        this.im = im;
        this.file = new File(plugin.getDataFolder(), "leaderboards.yml");
    }

    // ── lifecycle ────────────────────────────────────────────────

    public void start() {
        stop();
        loadFile();
        task = AsyncUtil.runSyncRepeating(this::tick, REFRESH_TICKS, REFRESH_TICKS);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        boards.clear();
    }

    private void loadFile() {
        boards.clear();
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("boards");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            var metric = LeaderboardMetric.parse(s.getString("metric"));
            if (metric.isEmpty() || !ID.matcher(id).matches()) {
                plugin.getLogger().warning("leaderboards.yml: entry '" + id + "' is invalid (metric must be kills, wins, kd or elo) and was ignored. Fix: edit plugins/OmniArena/leaderboards.yml.");
                continue;
            }
            boards.put(id.toLowerCase(java.util.Locale.ROOT), new Board(id, metric.get(),
                    LeaderboardLines.clampSize(s.getInt("size", LeaderboardLines.DEFAULT_SIZE)),
                    s.getString("hook", ""), s.getBoolean("placeholders", true),
                    s.getString("world", ""), s.getDouble("x"), s.getDouble("y"), s.getDouble("z")));
        }
    }

    private void saveFile() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Board b : boards.values()) {
            String p = "boards." + b.id + ".";
            cfg.set(p + "metric", b.metric.id());
            cfg.set(p + "size", b.size);
            cfg.set(p + "hook", b.hookKey);
            cfg.set(p + "placeholders", b.placeholders);
            cfg.set(p + "world", b.world);
            cfg.set(p + "x", b.x);
            cfg.set(p + "y", b.y);
            cfg.set(p + "z", b.z);
        }
        YamlUtil.save(cfg, file, plugin.getLogger());
    }

    // ── operations ───────────────────────────────────────────────

    public static String hologramName(String id) { return "omniarena_lb_" + id; }

    /** Creates a leaderboard hologram at {@code where} in the installed hologram plugin. Main thread. */
    public Status create(Location where, String id, LeaderboardMetric metric, int size) {
        if (id == null || !ID.matcher(id).matches()) return Status.INVALID_ID;
        if (boards.containsKey(id.toLowerCase(java.util.Locale.ROOT))) return Status.EXISTS;
        HologramHook hook = im.hologramHook();
        if (hook == null) return Status.NO_HOOK;
        World w = where.getWorld();
        if (w == null) return Status.FAILED;

        int n = LeaderboardLines.clampSize(size);
        boolean placeholders = im.hasPlaceholderAPI();
        List<String> lines = lines(hook, metric, n, placeholders);
        boolean ok = im.safe(hook.key(), () -> hook.create(hologramName(id), where, lines), false);
        if (!ok) return Status.FAILED;

        boards.put(id.toLowerCase(java.util.Locale.ROOT), new Board(id, metric, n, hook.key(), placeholders,
                w.getName(), where.getX(), where.getY(), where.getZ()));
        saveFile();
        return Status.OK;
    }

    /** Removes the leaderboard and its hologram (the entry is dropped even when the hologram plugin is gone). */
    public Status remove(String id) {
        Board b = id == null ? null : boards.remove(id.toLowerCase(java.util.Locale.ROOT));
        if (b == null) return Status.NOT_FOUND;
        HologramHook hook = im.hologramHook(b.hookKey);
        if (hook != null) im.safe(hook.key(), () -> hook.remove(hologramName(b.id)), false);
        saveFile();
        return Status.OK;
    }

    public List<Board> list() {
        List<Board> l = new ArrayList<>(boards.values());
        l.sort(Comparator.comparing(Board::id, String.CASE_INSENSITIVE_ORDER));
        return l;
    }

    private List<String> lines(HologramHook hook, LeaderboardMetric metric, int size, boolean placeholders) {
        return LeaderboardLines.build(metric, size, hook.usesMiniMessage(),
                placeholders ? null : im.leaderboardCache().rows(metric, size));
    }

    // ── periodic static refresh ──────────────────────────────────

    /** Every 60 s: boards without PlaceholderAPI get fresh static lines; boards still on static lines switch to placeholders once PAPI appears. */
    private void tick() {
        if (plugin.isStopped() || boards.isEmpty()) return;
        boolean papi = im.hasPlaceholderAPI();
        for (Board b : boards.values()) {
            if (papi && b.placeholders) continue; // the hologram plugin refreshes placeholder lines itself
            HologramHook hook = im.hologramHook(b.hookKey);
            if (hook == null) continue;
            List<String> lines = lines(hook, b.metric, b.size, papi);
            im.safe(hook.key(), () -> hook.update(hologramName(b.id), lines), false);
            b.placeholders = papi;
        }
    }
}
