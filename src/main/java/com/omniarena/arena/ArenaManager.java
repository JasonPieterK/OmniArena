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
        arena.setMinPlayers(Math.max(1, c.getInt("arenas.min-players", arena.getMinPlayers())));
        arena.setMaxPlayers(Math.max(arena.getMinPlayers(), c.getInt("arenas.max-players", arena.getMaxPlayers())));
        arena.setCountdown(Math.max(0, c.getInt("arenas.countdown", arena.getCountdown())));
        arena.setMatchDuration(Math.max(1, c.getInt("arenas.match-duration", arena.getMatchDuration())));
        arena.setGameMode(com.omniarena.gamemode.GameModeType.fromString(
                c.getString("arenas.game-mode", arena.getGameMode().name())));
        arena.setSpectatorAllowed(c.getBoolean("arenas.spectator-allowed", arena.isSpectatorAllowed()));
        arena.setMidMatchJoin(c.getBoolean("arenas.mid-match-join", arena.isMidMatchJoin()));
        arena.setRespawnDelay(Math.max(0, c.getInt("arenas.respawn-delay", arena.getRespawnDelay())));
        arena.setSpawnProtection(Math.max(0, c.getInt("arenas.spawn-protection", arena.getSpawnProtection())));
        var v = arena.getVisual();
        v.setTitles(c.getBoolean("visuals.titles", v.isTitles()));
        v.setSounds(c.getBoolean("visuals.sounds", v.isSounds()));
        v.setParticles(c.getBoolean("visuals.particles", v.isParticles()));
        v.setScoreboard(c.getBoolean("visuals.scoreboard", v.isScoreboard()));
        v.setBossBar(c.getBoolean("visuals.boss-bar", v.isBossBar()));
        v.setActionBar(c.getBoolean("visuals.action-bar", v.isActionBar()));
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

    // ── Worlds that are not loaded (Multiverse, late-loading worlds) ──

    /**
     * Re-resolves the raw spawns and signs of this arena whose worlds are loaded now and recomputes which
     * worlds are still missing. Returns true when the arena just became available (it had a missing world before).
     */
    public boolean resolveWorlds(Arena arena) {
        boolean hadMissing = arena.hasMissingWorld();
        for (SpawnType type : SpawnType.values()) {
            var raws = arena.getUnresolvedSpawns(type);
            for (var it = raws.iterator(); it.hasNext(); ) {
                Location loc = com.omniarena.util.LocationUtil.deserialize(it.next());
                if (loc != null) {
                    arena.addSpawn(type, loc);
                    it.remove();
                }
            }
        }
        boolean signsChanged = false;
        for (var it = arena.getUnparsedSigns().iterator(); it.hasNext(); ) {
            Location loc = com.omniarena.util.LocationUtil.deserialize(it.next());
            if (loc != null) {
                arena.getSigns().add(new com.omniarena.sign.ArenaSign(loc, arena.getName()));
                it.remove();
                signsChanged = true;
            }
        }
        arena.recomputeMissingWorlds(w -> org.bukkit.Bukkit.getWorld(w) != null);
        var sm = com.omniarena.sign.SignManager.getInstance();
        if (sm != null && (signsChanged || (hadMissing && !arena.hasMissingWorld()))) sm.requestUpdate(arena);
        return hadMissing && !arena.hasMissingWorld();
    }

    /** WorldLoadEvent: every arena that was waiting for a world gets its locations back and becomes available again. */
    public void onWorldLoad(org.bukkit.World world) {
        for (Arena a : arenas.values()) {
            boolean relevant = a.hasMissingWorld() || !a.getUnparsedSigns().isEmpty();
            if (!relevant) continue;
            if (resolveWorlds(a)) {
                plugin.getLogger().info("Arena '" + a.getName() + "' is available again: world '" + world.getName() + "' loaded.");
            }
        }
    }

    /**
     * WorldUnloadEvent: spawns in that world go back to raw form (so no stale World object is ever teleported
     * to) and the arena is unavailable until the world loads again. Arenas with a running match are left alone.
     */
    public void onWorldUnload(org.bukkit.World world) {
        var mm = com.omniarena.match.MatchManager.getInstance();
        for (Arena a : arenas.values()) {
            if (mm != null && mm.getMatch(a).isPresent()) continue;
            boolean touched = false;
            for (SpawnType type : SpawnType.values()) {
                var live = a.getSpawns(type);
                for (var it = live.iterator(); it.hasNext(); ) {
                    Location l = it.next();
                    if (l.getWorld() != null && l.getWorld().equals(world)) {
                        String raw = com.omniarena.util.LocationUtil.serialize(l);
                        if (raw != null) a.getUnresolvedSpawns(type).add(raw);
                        it.remove();
                        touched = true;
                    }
                }
            }
            boolean regionHere = a.getRegion().isDefined() && world.getName().equals(a.getRegion().getWorldName());
            if (touched || regionHere) {
                a.recomputeMissingWorlds(w -> !w.equals(world.getName()) && org.bukkit.Bukkit.getWorld(w) != null);
                plugin.getLogger().warning("World '" + world.getName() + "' was unloaded: arena '" + a.getName() + "' is unavailable until it is loaded again.");
            }
        }
    }

    /**
     * Admin action (teleport, setup): tries to load the missing worlds of the arena, through Multiverse-Core
     * when it is installed. Returns true when the arena has no missing world afterwards.
     */
    public boolean ensureWorlds(Arena arena) {
        resolveWorlds(arena);
        if (!arena.hasMissingWorld()) return true;
        var im = plugin.getIntegrationManager();
        if (im != null && im.hasMultiverse()) {
            var mv = im.multiverse();
            for (String w : new ArrayList<>(arena.getMissingWorlds())) {
                im.safe("multiverse", () -> mv.loadWorld(w), false);
            }
            resolveWorlds(arena);
        }
        return !arena.hasMissingWorld();
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
