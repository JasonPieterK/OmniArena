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

        // Crash recovery: a leftover pre-arena state file means the server died mid-match
        final org.bukkit.entity.Player player = event.getPlayer();
        com.omniarena.match.MatchManager mm = com.omniarena.match.MatchManager.getInstance();
        if (mm == null || !mm.getStateStore().exists(player.getUniqueId())) return;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || plugin.isStopped()) return;
            if (mm.isInArena(player)) return;
            if (mm.getStateStore().restore(player)) {
                com.omniarena.util.MessageUtil.send(player,
                        "<yellow>The server restarted while you were in an arena. <green>Your previous inventory and location were restored.");
            }
        });
    }
}
