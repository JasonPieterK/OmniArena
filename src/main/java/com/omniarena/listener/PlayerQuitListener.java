package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.match.MatchManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final OmniArena plugin;

    public PlayerQuitListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getVisual() != null) {
            plugin.getVisual().getBossBarManager().removePlayer(player);
            plugin.getVisual().getScoreboardManager().removeBoard(player);
        }
        MatchManager mm = MatchManager.getInstance();

        if (mm.isSpectating(player)) {
            mm.leaveSpectator(player);
        } else if (mm.isInArena(player)) {
            mm.leaveArena(player);
        }
    }
}
