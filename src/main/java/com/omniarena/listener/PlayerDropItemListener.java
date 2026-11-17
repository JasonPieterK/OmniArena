package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.match.MatchManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.Optional;

/** Cancels manual item drops in arenas whose item-drop rule is off. */
public class PlayerDropItemListener implements Listener {

    private final OmniArena plugin;

    public PlayerDropItemListener(OmniArena plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.isStopped()) return;
        var player = event.getPlayer();
        if (!MatchManager.getInstance().isInArena(player)) return;
        Optional<String> arenaName = MatchManager.getInstance().getArenaName(player);
        if (arenaName.isEmpty()) return;
        Optional<Arena> arena = ArenaManager.getInstance().get(arenaName.get());
        if (arena.isEmpty()) return;
        if (!arena.get().getRules().isItemDrop()) event.setCancelled(true);
    }
}
