package com.omniarena.sign;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaState;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.util.ColorUtil;
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

        Optional<Match> match = MatchManager.getInstance().getMatch(arena);
        int players = match.map(m -> m.getPlayerCount()).orElse(0);
        int max = arena.getMaxPlayers();
        ArenaState state = arena.getState();

        Component line1 = ColorUtil.parse("<dark_aqua><bold>[OmniArena]</bold></dark_aqua>");
        Component line2 = ColorUtil.parse("<yellow>" + arena.getName());
        Component line3 = buildStatusLine(state, players, max);
        Component line4 = ColorUtil.parse("<gray>" + players + "/" + max + " players");

        sign.getSide(Side.FRONT).line(0, line1);
        sign.getSide(Side.FRONT).line(1, line2);
        sign.getSide(Side.FRONT).line(2, line3);
        sign.getSide(Side.FRONT).line(3, line4);
        sign.update();
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
                // Block is no longer a sign — remove stale entry
                arena.getSigns().remove(as);
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
        } catch (Exception e) { plugin.getLogger().warning("Failed to save sign glowstone: " + e.getMessage()); }
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
        Block behind = block.getRelative(getOppositeDirection(block));
        if (behind.getType() == Material.AIR) {
            behind.setType(Material.GLOWSTONE);
            if (placedGlowstone.isEmpty()) loadGlow();
            placedGlowstone.add(behind.getLocation());
            saveGlow();
        }
    }

    private void removeGlowstone(Block block) {
        Block behind = block.getRelative(getOppositeDirection(block));
        if (placedGlowstone.isEmpty()) loadGlow();
        // Only remove glowstone the plugin placed itself
        if (placedGlowstone.remove(behind.getLocation())) {
            if (behind.getType() == Material.GLOWSTONE) behind.setType(Material.AIR);
            saveGlow();
        }
    }

    private org.bukkit.block.BlockFace getOppositeDirection(Block block) {
        // Simple fallback — north is behind
        if (block.getState() instanceof Sign sign) {
            if (block.getBlockData() instanceof org.bukkit.block.data.type.WallSign ws) {
                return ws.getFacing().getOppositeFace();
            }
        }
        return org.bukkit.block.BlockFace.NORTH;
    }
}
