package com.omniarena.arena;

import com.omniarena.OmniArena;
import com.omniarena.config.ArenaConfig;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaManager {

    private static ArenaManager instance;

    private final OmniArena plugin;
    private final ArenaConfig arenaConfig;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();

    // Selection points for region setup (admin tool)
    private final Map<UUID, Location> selectionPoint1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> selectionPoint2 = new ConcurrentHashMap<>();

    public ArenaManager(OmniArena plugin) {
        this.plugin = plugin;
        this.arenaConfig = new ArenaConfig(plugin);
        instance = this;
    }

    public static ArenaManager getInstance() { return instance; }

    public void loadAll() {
        arenas.clear();
        List<Arena> loaded = arenaConfig.loadAll();
        loaded.forEach(a -> arenas.put(a.getName().toLowerCase(), a));
        plugin.getLogger().info("Loaded " + arenas.size() + " arena(s).");
    }

    public void saveAll() {
        arenas.values().forEach(arenaConfig::save);
    }

    // ── CRUD ──────────────────────────────────────────────────────

    public Optional<Arena> get(String name) {
        return Optional.ofNullable(arenas.get(name.toLowerCase()));
    }

    public boolean exists(String name) {
        return arenas.containsKey(name.toLowerCase());
    }

    public Arena create(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Arena name cannot be null or blank");
        }
        if (exists(name)) {
            throw new IllegalArgumentException("Arena already exists: " + name);
        }
        Arena arena = new Arena(name);
        applyDefaults(arena);
        arenas.put(name.toLowerCase(), arena);
        arenaConfig.save(arena);
        return arena;
    }

    /** Seeds a newly created arena from the arena.* defaults in config.yml. */
    private void applyDefaults(Arena arena) {
        var cm = com.omniarena.config.ConfigManager.getInstance();
        if (cm == null || cm.getConfig() == null) return;
        var c = cm.getConfig();
        arena.setMinPlayers(Math.max(1, c.getInt("arena.default-min-players", arena.getMinPlayers())));
        arena.setMaxPlayers(Math.max(arena.getMinPlayers(), c.getInt("arena.default-max-players", arena.getMaxPlayers())));
        arena.setCountdown(Math.max(0, c.getInt("arena.default-countdown", arena.getCountdown())));
        arena.setMatchDuration(Math.max(1, c.getInt("arena.default-match-duration", arena.getMatchDuration())));
        arena.setGameMode(com.omniarena.gamemode.GameModeType.fromString(
                c.getString("arena.default-game-mode", arena.getGameMode().name())));
        arena.setSpectatorAllowed(c.getBoolean("arena.spectator-allowed", arena.isSpectatorAllowed()));
        arena.setMidMatchJoin(c.getBoolean("arena.mid-match-join", arena.isMidMatchJoin()));
        arena.setRespawnDelay(Math.max(0, c.getInt("arena.respawn-delay", arena.getRespawnDelay())));
        arena.setSpawnProtection(Math.max(0, c.getInt("arena.spawn-protection", arena.getSpawnProtection())));
        var v = arena.getVisual();
        v.setTitles(c.getBoolean("visual.titles", v.isTitles()));
        v.setSounds(c.getBoolean("visual.sounds", v.isSounds()));
        v.setParticles(c.getBoolean("visual.particles", v.isParticles()));
        v.setScoreboard(c.getBoolean("visual.scoreboard", v.isScoreboard()));
        v.setBossBar(c.getBoolean("visual.boss-bar", v.isBossBar()));
        v.setActionBar(c.getBoolean("visual.action-bar", v.isActionBar()));
        v.setHolograms(c.getBoolean("visual.holograms", v.isHolograms()));
    }

    public boolean delete(String name) {
        if (name == null || name.isBlank()) return false;
        Arena removed = arenas.remove(name.toLowerCase());
        if (removed != null) {
            var sm = com.omniarena.sign.SignManager.getInstance();
            if (sm != null) sm.clearSigns(removed);
            arenaConfig.delete(name);
            return true;
        }
        return false;
    }

    public void save(Arena arena) {
        arenaConfig.save(arena);
    }

    public Collection<Arena> getAll() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public List<Arena> getEnabled() {
        return arenas.values().stream().filter(Arena::isEnabled).toList();
    }

    // ── Region selection ──────────────────────────────────────────

    public void setPoint1(UUID player, Location loc) {
        selectionPoint1.put(player, loc);
    }

    public void setPoint2(UUID player, Location loc) {
        selectionPoint2.put(player, loc);
    }

    public Optional<Location> getPoint1(UUID player) {
        return Optional.ofNullable(selectionPoint1.get(player));
    }

    public Optional<Location> getPoint2(UUID player) {
        return Optional.ofNullable(selectionPoint2.get(player));
    }

    public void clearSelection(UUID player) {
        selectionPoint1.remove(player);
        selectionPoint2.remove(player);
    }

    public boolean hasFullSelection(UUID player) {
        return selectionPoint1.containsKey(player) && selectionPoint2.containsKey(player);
    }

    // ── Lookup helpers ────────────────────────────────────────────

    /** Returns the arena that contains this location, or empty. */
    public Optional<Arena> getArenaAt(Location loc) {
        return arenas.values().stream()
                .filter(a -> a.getRegion().isDefined() && a.getRegion().contains(loc))
                .findFirst();
    }

    public void enable(Arena arena) {
        arena.setState(ArenaState.WAITING);
        save(arena);
    }

    public void disable(Arena arena) {
        arena.setState(ArenaState.DISABLED);
        save(arena);
    }
}
