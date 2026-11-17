package com.omniarena.visual;

import com.omniarena.OmniArena;
import com.omniarena.rating.EloManager;
import com.omniarena.rating.EloRating;
import com.omniarena.stats.PlayerStats;
import com.omniarena.stats.StatsManager;
import com.omniarena.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Leaderboard holograms: one multi-line TextDisplay entity per hologram, persisted in holograms.yml,
 * refreshed in place from the cached stats/elo data (never creates stat entries, never force-loads chunks).
 */
public class HologramManager implements Listener {

    public enum Type {
        KILLS("TOP KILLS", "#ff6b35:#f7c59f"),
        WINS("TOP WINS", "#35a7ff:#9fd8f7"),
        KD("TOP K/D", "#7bff35:#d8f79f"),
        ELO("TOP ELO", "#ff35c9:#f79fe6");

        final String title;
        final String gradient;
        Type(String title, String gradient) { this.title = title; this.gradient = gradient; }

        public static Type parse(String s) {
            if (s == null) return null;
            try { return valueOf(s.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
        }
    }

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 15;
    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

    private static final class Holo {
        final String id;
        Type type;
        int size;
        String world;
        double x, y, z;
        TextDisplay entity;
        Holo(String id, Type type, int size, Location l) {
            this.id = id; this.type = type; this.size = size;
            set(l);
        }
        void set(Location l) { world = l.getWorld().getName(); x = l.getX(); y = l.getY(); z = l.getZ(); }
        Location loc() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
        boolean inChunk(org.bukkit.Chunk c) {
            return c.getWorld().getName().equals(world) && ((int) Math.floor(x) >> 4) == c.getX() && ((int) Math.floor(z) >> 4) == c.getZ();
        }
    }

    private final OmniArena plugin;
    private final NamespacedKey tagKey;
    private final Map<String, Holo> holograms = new LinkedHashMap<>();
    /** Entries in holograms.yml that could not be parsed; written back verbatim on save. */
    private final Map<String, Object> unparsed = new LinkedHashMap<>();
    private BukkitTask refreshTask;

    public HologramManager(OmniArena plugin) {
        this.plugin = plugin;
        this.tagKey = new NamespacedKey(plugin, "hologram");
    }

    private File file() { return new File(plugin.getDataFolder(), "holograms.yml"); }

    /** Loads holograms.yml, sweeps stale entities, spawns holograms in loaded chunks and starts the refresh timer. */
    public void start() {
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World w : plugin.getServer().getWorlds()) {
            for (org.bukkit.Chunk c : w.getLoadedChunks()) removeStale(c);
        }
        refreshAll();
        long ticks = Math.max(1, plugin.getConfig().getInt("holograms.refresh-seconds", 30)) * 20L;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, ticks, ticks);
    }

    /** Despawns every hologram entity and stops the timer. Definitions stay in holograms.yml. */
    public void removeAll() {
        if (refreshTask != null) { refreshTask.cancel(); refreshTask = null; }
        for (Holo h : holograms.values()) despawn(h);
    }

    // ---- persistence ----

    private void load() {
        holograms.clear();
        unparsed.clear();
        if (!file().exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
        ConfigurationSection root = cfg.getConfigurationSection("holograms");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            Type type = s == null ? null : Type.parse(s.getString("type"));
            if (s == null || type == null || s.getString("world") == null || !s.contains("x") || !s.contains("y") || !s.contains("z")) {
                plugin.getLogger().warning("holograms.yml: entry '" + id + "' is invalid (needs type kills|wins|kd|elo plus world, x, y, z). It is kept in the file untouched. Fix: edit holograms.yml or remove it.");
                unparsed.put(id, root.get(id));
                continue;
            }
            Holo h = new Holo(id, type, clamp(s.getInt("size", DEFAULT_SIZE)), new Location(
                    Bukkit.getWorlds().get(0), s.getDouble("x"), s.getDouble("y"), s.getDouble("z")));
            h.world = s.getString("world"); // may be a world that is not loaded (yet): never dropped
            holograms.put(id, h);
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, Object> e : unparsed.entrySet()) cfg.set("holograms." + e.getKey(), e.getValue());
        for (Holo h : holograms.values()) {
            String p = "holograms." + h.id + ".";
            cfg.set(p + "type", h.type.name().toLowerCase(Locale.ROOT));
            cfg.set(p + "size", h.size);
            cfg.set(p + "world", h.world);
            cfg.set(p + "x", h.x);
            cfg.set(p + "y", h.y);
            cfg.set(p + "z", h.z);
        }
        try { cfg.save(file()); }
        catch (Exception e) { plugin.getLogger().warning("Failed to save holograms.yml (" + e + "). Holograms may be lost on restart. Fix: check that plugins/OmniArena is writable and the disk is not full."); }
    }

    private static int clamp(int size) { return Math.max(1, Math.min(MAX_SIZE, size)); }

    // ---- admin API (each returns null on success or a detailed error) ----

    public String create(String rawId, Type type, int size, Location at) {
        String id = rawId == null ? "" : rawId.toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(id).matches())
            return "Invalid hologram id '" + rawId + "'. Use 1-32 characters: lowercase letters, digits, '_' or '-'.";
        if (holograms.containsKey(id) || unparsed.containsKey(id))
            return "A hologram named '" + id + "' already exists. Pick another id, or run /oa hologram remove " + id + " first.";
        if (at == null || at.getWorld() == null) return "Your location has no world; try again.";
        Holo h = new Holo(id, type, clamp(size), at);
        holograms.put(id, h);
        save();
        refresh(h);
        return null;
    }

    public String remove(String id) {
        Holo h = holograms.remove(id == null ? "" : id.toLowerCase(Locale.ROOT));
        if (h == null) return "No hologram named '" + id + "'. Run /oa hologram list to see the existing ones.";
        despawn(h);
        save();
        return null;
    }

    public String move(String id, Location at) {
        Holo h = holograms.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
        if (h == null) return "No hologram named '" + id + "'. Run /oa hologram list to see the existing ones.";
        if (at == null || at.getWorld() == null) return "Your location has no world; try again.";
        despawn(h);
        h.set(at);
        save();
        refresh(h);
        return null;
    }

    public List<String> ids() { return new ArrayList<>(holograms.keySet()); }

    /** One human-readable line per hologram for /oa hologram list. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Holo h : holograms.values()) {
            boolean loaded = Bukkit.getWorld(h.world) != null;
            out.add("<yellow>" + h.id + "</yellow> <gray>" + h.type.name().toLowerCase(Locale.ROOT) + " top " + h.size + " @ "
                    + h.world + " " + (int) h.x + ", " + (int) h.y + ", " + (int) h.z
                    + (loaded ? (h.entity != null && h.entity.isValid() ? "" : " <dark_gray>(chunk not loaded)") : " <red>(world not loaded)"));
        }
        return out;
    }

    // ---- entities ----

    private void despawn(Holo h) {
        if (h.entity != null && h.entity.isValid()) h.entity.remove();
        h.entity = null;
    }

    private void refreshAll() {
        for (Holo h : holograms.values()) refresh(h);
    }

    /** Spawns the entity if missing (loaded chunks only) and updates its text in place. */
    private void refresh(Holo h) {
        Location loc = h.loc();
        if (loc == null) return;
        // Never force-load a chunk just to spawn a hologram
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
        if (h.entity == null || !h.entity.isValid()) {
            h.entity = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(false);
                td.setSeeThrough(false);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setBackgroundColor(org.bukkit.Color.fromARGB(96, 0, 0, 0));
                td.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
            });
        }
        h.entity.text(buildText(h));
    }

    private Component buildText(Holo h) {
        Component text = ColorUtil.parse("<gradient:" + h.type.gradient + "><bold>" + h.type.title + "</bold></gradient>");
        int rank = 0;
        for (Object[] row : rows(h)) {
            rank++;
            NamedTextColor rc = rank == 1 ? NamedTextColor.GOLD : rank == 2 ? NamedTextColor.GRAY
                    : rank == 3 ? NamedTextColor.DARK_RED : NamedTextColor.DARK_GRAY;
            text = text.append(Component.newline())
                    .append(Component.text("#" + rank + " ", rc))
                    .append(Component.text(String.valueOf(row[0]), NamedTextColor.WHITE)) // plain text: names never parsed
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(row[1]), NamedTextColor.YELLOW));
        }
        if (rank == 0) text = text.append(Component.newline()).append(Component.text("No data yet", NamedTextColor.GRAY));
        return text;
    }

    /** {name, value-label} rows from cached leaderboards only. */
    private List<Object[]> rows(Holo h) {
        List<Object[]> rows = new ArrayList<>();
        switch (h.type) {
            case KILLS -> { StatsManager sm = StatsManager.getInstance(); if (sm != null)
                for (PlayerStats s : sm.getTopKills(h.size)) rows.add(new Object[]{s.getPlayerName(), s.getKills() + " kills"}); }
            case WINS -> { StatsManager sm = StatsManager.getInstance(); if (sm != null)
                for (PlayerStats s : sm.getTopWins(h.size)) rows.add(new Object[]{s.getPlayerName(), s.getWins() + " wins"}); }
            case KD -> { StatsManager sm = StatsManager.getInstance(); if (sm != null)
                for (PlayerStats s : sm.getTopKD(h.size)) rows.add(new Object[]{s.getPlayerName(), String.format(Locale.ROOT, "%.2f K/D", s.getKDRatio())}); }
            case ELO -> { EloManager em = EloManager.getInstance(); if (em != null)
                for (EloRating r : em.getTopPlayers(h.size)) rows.add(new Object[]{r.getPlayerName(), r.getRating() + " elo"}); }
        }
        return rows;
    }

    // ---- stale sweep / chunk load ----

    private boolean isTracked(Entity e) {
        UUID id = e.getUniqueId();
        for (Holo h : holograms.values()) if (h.entity != null && h.entity.getUniqueId().equals(id)) return true;
        return false;
    }

    /** Removes OmniArena-tagged entities this manager does not track (left over after a crash/reload). */
    private void removeStale(org.bukkit.Chunk chunk) {
        for (Entity e : chunk.getEntities()) {
            if (e.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE) && !isTracked(e)) e.remove();
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        org.bukkit.Chunk chunk = event.getChunk();
        if (!event.isNewChunk()) removeStale(chunk);
        for (Holo h : holograms.values()) {
            if (h.inChunk(chunk)) {
                Bukkit.getScheduler().runTask(plugin, () -> { if (plugin.isEnabled()) refresh(h); });
            }
        }
    }
}
