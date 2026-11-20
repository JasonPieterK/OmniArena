package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Optional;

public class BlockPlaceListener implements Listener {

    private final OmniArena plugin;

    public BlockPlaceListener(OmniArena plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.isStopped()) return;
        Player player = event.getPlayer();
        Optional<Arena> arenaOpt = ArenaManager.getInstance().getArenaAt(event.getBlock().getLocation());
        if (arenaOpt.isEmpty()) return;

        Arena arena = arenaOpt.get();
        if (player.isOp()) return;
        if (!arena.getRules().isBlockPlace() && arena.getState() != ArenaState.RUNNING
                && com.omniarena.config.ConfigManager.getInstance().getConfig().getBoolean("arenas.region-protection", true)
                && !com.omniarena.permission.PermissionChecker.hasAdminArena(player)) {
            event.setCancelled(true);
            return;
        }
        if (arena.getState() == ArenaState.RUNNING && !arena.getRules().isBlockPlace()) {
            event.setCancelled(true);
        }
    }

    /** During a match, stop water/lava from leaking out of the arena region (regen would never clean it up). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluidFlow(org.bukkit.event.block.BlockFromToEvent event) {
        if (plugin.isStopped()) return;
        org.bukkit.Material type = event.getBlock().getType();
        if (type != org.bukkit.Material.WATER && type != org.bukkit.Material.LAVA) return;
        org.bukkit.Location from = event.getBlock().getLocation();
        org.bukkit.Location to = event.getToBlock().getLocation();
        Optional<Arena> src = ArenaManager.getInstance().getArenaAt(from);
        if (src.isEmpty() || !src.get().getRegion().isDefined()) return;
        Arena arena = src.get();
        if (arena.getRegion().contains(to)) return;
        if (arena.getState() == ArenaState.RUNNING
                || com.omniarena.match.MatchManager.getInstance().getMatch(arena).isPresent()) {
            event.setCancelled(true);
        }
    }
}
