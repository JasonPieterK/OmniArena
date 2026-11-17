package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.RegionHandler;
import com.omniarena.match.MatchManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Optional;

public class PlayerMoveListener implements Listener {

    private final OmniArena plugin;
    private final RegionHandler regionHandler;

    public PlayerMoveListener(OmniArena plugin) {
        this.plugin = plugin;
        this.regionHandler = new RegionHandler(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (plugin.isStopped()) return;
        // Only act on full block movements (skip look-only events)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        MatchManager mm = MatchManager.getInstance();

        if (!mm.isInArena(player) || mm.isSpectating(player)) return;

        Optional<String> arenaName = mm.getArenaName(player);
        if (arenaName.isEmpty()) return;

        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(arenaName.get());
        if (arenaOpt.isEmpty()) return;

        Arena arena = arenaOpt.get();
        if (!arena.isRunning() || !arena.getRegion().isDefined()) return;

        if (mm.isArenaFrozen(arena.getName())) {
            event.setTo(event.getFrom());
            return;
        }

        if (!arena.getRegion().contains(event.getTo())) {
            regionHandler.enforceRegion(player, arena);
        }
    }
}
