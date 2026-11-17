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

    public BlockPlaceListener(OmniArena plugin) {}

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.isStopped()) return;
        Player player = event.getPlayer();
        Optional<Arena> arenaOpt = ArenaManager.getInstance().getArenaAt(event.getBlock().getLocation());
        if (arenaOpt.isEmpty()) return;

        Arena arena = arenaOpt.get();
        if (player.isOp()) return;
        if (arena.getState() == ArenaState.RUNNING && !arena.getRules().isBlockPlace()) {
            event.setCancelled(true);
        }
    }
}
