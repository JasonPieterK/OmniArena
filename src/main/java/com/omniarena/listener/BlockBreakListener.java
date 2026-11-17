package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Optional;

public class BlockBreakListener implements Listener {

    private final OmniArena plugin;

    public BlockBreakListener(OmniArena plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.isStopped()) return;
        Player player = event.getPlayer();

        // Prevent block break if holding selection wand and has permission
        if (com.omniarena.permission.PermissionChecker.hasAdminArena(player)) {
            org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
            if (item != null && item.getType() == PlayerInteractListener.wandMaterial()) {
                event.setCancelled(true);
                return;
            }
        }

        Optional<Arena> arenaOpt = ArenaManager.getInstance().getArenaAt(event.getBlock().getLocation());
        if (arenaOpt.isEmpty()) return;

        Arena arena = arenaOpt.get();
        // Allow if player is OP or arena allows breaking
        if (player.isOp()) return;
        if (!arena.getRules().isBlockBreak() && arena.getState() != ArenaState.RUNNING
                && com.omniarena.config.ConfigManager.getInstance().getConfig().getBoolean("arena.region-protection", true)
                && !com.omniarena.permission.PermissionChecker.hasAdminArena(player)) {
            event.setCancelled(true);
            return;
        }
        if (arena.getState() == ArenaState.RUNNING && !arena.getRules().isBlockBreak()) {
            event.setCancelled(true);
        }
    }
}
