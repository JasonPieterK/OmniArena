package com.omniarena.arena;

import com.omniarena.OmniArena;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class RegionHandler {

    private final OmniArena plugin;

    public RegionHandler(OmniArena plugin) {
        this.plugin = plugin;
    }

    public boolean isInRegion(Player player, Arena arena) {
        ArenaRegion region = arena.getRegion();
        return region.isDefined() && region.contains(player.getLocation());
    }

    public boolean isLocationProtected(Location location) {
        return ArenaManager.getInstance().getArenaAt(location)
                .map(a -> a.isRunning() || a.getState() == ArenaState.STARTING)
                .orElse(false);
    }

    public void setRegion(Arena arena, Location point1, Location point2) {
        ArenaRegion region = new ArenaRegion(point1, point2);
        arena.setRegion(region);
        ArenaManager.getInstance().save(arena);
    }

    /** Teleport player back into arena if they wander outside during a match. */
    public void enforceRegion(Player player, Arena arena) {
        if (!arena.isRunning()) return;
        if (arena.getRegion().isDefined() && !arena.getRegion().contains(player.getLocation())) {
            Location safe = arena.getRandomBattleSpawn();
            if (safe != null) {
                player.teleport(safe);
                player.sendMessage(com.omniarena.util.ColorUtil.parse(
                        "<red>You left the arena boundary and were teleported back."));
            }
        }
    }
}
