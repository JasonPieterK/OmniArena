package com.omniarena.sign;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaState;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.util.ColorUtil;
import com.omniarena.platform.Platform;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SignManager {

    private static SignManager instance;

    private final OmniArena plugin;

    public SignManager(OmniArena plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static SignManager getInstance() { return instance; }

    public boolean isArenaSign(Block block) {
        if (!isSign(block)) return false;
        return ArenaManager.getInstance().getAll().stream()
                .anyMatch(a -> a.getSigns().stream()
                        .anyMatch(s -> s.getLocation().equals(block.getLocation())));
    }

    public Optional<ArenaSign> getSign(Block block) {
        return ArenaManager.getInstance().getAll().stream()
                .flatMap(a -> a.getSigns().stream())
                .filter(s -> s.getLocation().equals(block.getLocation()))
                .findFirst();
    }

    public boolean createSign(Arena arena, Block block) {
        if (!isSign(block)) return false;
        // A block can only belong to one arena: drop any earlier registration first
        if (getSign(block).isPresent()) removeSign(block);
        ArenaSign sign = new ArenaSign(block.getLocation(), arena.getName());
        arena.getSigns().add(sign);
        ArenaManager.getInstance().save(arena);
        updateSign(block, arena);
        placeGlowstone(block);
        return true;
    }

    public boolean removeSign(Block block) {
        return ArenaManager.getInstance().getAll().stream()
                .anyMatch(arena -> {
                    boolean removed = arena.getSigns().removeIf(
                            s -> s.getLocation().equals(block.getLocation()));
                    if (removed) {
                        ArenaManager.getInstance().save(arena);
                        removeGlowstone(block);
                    }
                    return removed;
                });
    }

    public void updateSign(Block block, Arena arena) {
        if (!isSign(block)) return;
        Sign sign = (Sign) block.getState();

        int players = MatchManager.getInstance().getMatch(arena).map(m -> m.getPlayerCount()).orElse(0);
        int max = arena.getMaxPlayers();

        Component[] lines = {
                ColorUtil.parse("<dark_aqua><bold>[OmniArena]</bold></dark_aqua>"),
                // Plain text component: arena names are never parsed as MiniMessage/legacy codes
                Component.text(arena.getName(), net.kyori.adventure.text.format.NamedTextColor.YELLOW),
                buildStatusLine(arena.getState(), players, max),
                ColorUtil.parse("<gray>" + players + "/" + max + " players")
        };
        boolean changed = false;
        for (Side side : Side.values()) {
            for (int i = 0; i < 4; i++) {
                if (!ColorUtil.toLegacy(lines[i]).equals(ColorUtil.toLegacy(Platform.get().getSignLine(sign.getSide(side), i)))) {
                    Platform.get().setSignLine(sign.getSide(side), i, lines[i]);
                    changed = true;
                }
            }
        }
        if (changed) sign.update();
    }

    private final java.util.Set<String> pendingUpdates = new java.util.HashSet<>();

    /** Forgets coalesced updates whose scheduled tasks were cancelled (e.g. by /oa stop), so requestUpdate works again. */
    public void resetPending() { pendingUpdates.clear(); }

    /** Refreshes an arena's signs next tick (coalesced); call on state/player-count changes. */
    public void requestUpdate(Arena arena) {
        if (arena == null || plugin.isStopped() || !plugin.isEnabled() || arena.getSigns().isEmpty()) return;
        if (!pendingUpdates.add(arena.getName())) return;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            pendingUpdates.remove(arena.getName());
            if (!plugin.isStopped()) updateAllSigns(arena);
        });
    }

    /** Refreshes signs in a freshly loaded chunk (signs in unloaded chunks are skipped by the timer). */
    public void updateChunk(org.bukkit.Chunk chunk) {
        for (Arena arena : ArenaManager.getInstance().getAll()) {
            if (arena.getSigns().isEmpty()) continue;
            boolean any = false;
            for (ArenaSign as : arena.getSigns()) {
                Location l = as.getLocation();
                if (l.getWorld() == chunk.getWorld() && (l.getBlockX() >> 4) == chunk.getX() && (l.getBlockZ() >> 4) == chunk.getZ()) { any = true; break; }
            }
            if (any) updateAllSigns(arena);
        }
    }

    /** Arena deleted: blank its signs, remove tracked glowstone. Registrations vanish with the arena. */
    public void clearSigns(Arena arena) {
        for (ArenaSign as : new ArrayList<>(arena.getSigns())) {
            Location loc = as.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            Block block = loc.getBlock();
            if (block.getState() instanceof Sign sign) {
                for (Side side : Side.values()) {
                    Platform.get().setSignLine(sign.getSide(side), 0, ColorUtil.parse("<dark_red>[OmniArena]"));
                    Platform.get().setSignLine(sign.getSide(side), 1, Component.text("Arena removed"));
                    Platform.get().setSignLine(sign.getSide(side), 2, Component.empty());
                    Platform.get().setSignLine(sign.getSide(side), 3, Component.empty());
                }
                sign.update();
            }
            removeGlowstone(block);
        }
        arena.getSigns().clear();
    }

    public void updateAllSigns(Arena arena) {
        List<ArenaSign> signs = new ArrayList<>(arena.getSigns());
        for (ArenaSign as : signs) {
            Location loc = as.getLocation();
            if (loc.getWorld() == null) continue;
            // Never force-load chunks just to refresh a sign
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            Block block = loc.getBlock();
            if (isSign(block)) {
                updateSign(block, arena);
            } else {
                // Block is no longer a sign (broken by explosion/physics/etc.) — drop entry and our glowstone
                arena.getSigns().remove(as);
                removeGlowstoneNear(block);
                ArenaManager.getInstance().save(arena);
            }
        }
    }

    private Component buildStatusLine(ArenaState state, int players, int max) {
        return switch (state) {
            case WAITING   -> players >= max
                    ? ColorUtil.parse("<red>FULL")
                    : ColorUtil.parse("<green>Click to Join");
            case STARTING  -> ColorUtil.parse("<yellow>Starting...");
            case RUNNING   -> ColorUtil.parse("<yellow>IN PROGRESS");
            case ENDING, RESETTING -> ColorUtil.parse("<gray>Resetting...");
            case DISABLED  -> ColorUtil.parse("<red>DISABLED");
            case MAINTENANCE -> ColorUtil.parse("<dark_red>MAINTENANCE");
        };
    }

    private boolean isSign(Block block) {
        return block.getState() instanceof Sign;
    }

    // Glowstone blocks this plugin placed behind signs (never remove foreign glowstone)
    private final java.util.Set<org.bukkit.Location> placedGlowstone = new java.util.HashSet<>();
    private java.io.File glowFile() { return new java.io.File(plugin.getDataFolder(), "sign-glowstone.yml"); }

    private void saveGlow() {
        try {
            org.bukkit.configuration.file.YamlConfiguration cfg = new org.bukkit.configuration.file.YamlConfiguration();
            java.util.List<String> out = new java.util.ArrayList<>();
            for (org.bukkit.Location l : placedGlowstone)
                if (l.getWorld() != null) out.add(l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
            cfg.set("locations", out);
            cfg.save(glowFile());
        } catch (Exception e) { plugin.getLogger().warning("Failed to save sign glow data (" + e + "). Sign highlights may be lost on restart. Fix: check that plugins/OmniArena is writable and the disk is not full."); }
    }

    private void loadGlow() {
        if (!glowFile().exists()) return;
        for (String s : org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(glowFile()).getStringList("locations")) {
            String[] p = s.split(",");
            if (p.length != 4) continue;
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(p[0]);
            if (w == null) continue;
            try { placedGlowstone.add(new org.bukkit.Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]))); }
            catch (NumberFormatException ignored) {}
        }
    }

    private void placeGlowstone(Block block) {
        if (!plugin.getConfig().getBoolean("signs.glow-effect", true)) return;
        org.bukkit.block.BlockFace face = getOppositeDirection(block);
        if (face == null) return; // only wall signs get a backing glowstone
        Block behind = block.getRelative(face);
        if (behind.getType() == Material.AIR) {
            behind.setType(Material.GLOWSTONE);
            if (placedGlowstone.isEmpty()) loadGlow();
            placedGlowstone.add(behind.getLocation());
            saveGlow();
        }
    }

    /** The sign block is already gone, so check every horizontal neighbour for glowstone we placed. */
    private void removeGlowstoneNear(Block signBlock) {
        if (placedGlowstone.isEmpty()) loadGlow();
        boolean changed = false;
        for (org.bukkit.block.BlockFace f : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
            Block n = signBlock.getRelative(f);
            if (placedGlowstone.remove(n.getLocation())) {
                if (n.getType() == Material.GLOWSTONE) n.setType(Material.AIR);
                changed = true;
            }
        }
        if (changed) saveGlow();
    }

    private void removeGlowstone(Block block) {
        org.bukkit.block.BlockFace face = getOppositeDirection(block);
        if (face == null) { removeGlowstoneNear(block); return; }
        Block behind = block.getRelative(face);
        if (placedGlowstone.isEmpty()) loadGlow();
        // Only remove glowstone the plugin placed itself
        if (placedGlowstone.remove(behind.getLocation())) {
            if (behind.getType() == Material.GLOWSTONE) behind.setType(Material.AIR);
            saveGlow();
        }
    }

    private org.bukkit.block.BlockFace getOppositeDirection(Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.type.WallSign ws) {
            return ws.getFacing().getOppositeFace();
        }
        return null; // standing and hanging signs have no "behind" block
    }
}
