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
                && com.omniarena.config.ConfigManager.getInstance().getConfig().getBoolean("arena.region-protection", true)
                && !com.omniarena.permission.PermissionChecker.hasAdminArena(player)) {
            event.setCancelled(true);
            return;
        }
        if (arena.getState() == ArenaState.RUNNING && !arena.getRules().isBlockPlace()) {
            event.setCancelled(true);
        }
    }
}
