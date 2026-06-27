package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.stats.StatsManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final OmniArena plugin;

    public PlayerJoinListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Pre-load stats cache for this player
        StatsManager.getInstance().getStats(event.getPlayer().getUniqueId());
    }
}
