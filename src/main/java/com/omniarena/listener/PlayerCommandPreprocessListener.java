package com.omniarena.listener;

import com.omniarena.OmniArena;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

public class PlayerCommandPreprocessListener implements Listener {

    private final OmniArena plugin;

    public PlayerCommandPreprocessListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.length() < 2 || message.charAt(0) != '/') return;

        String raw = message.substring(1).trim();
        if (raw.isEmpty()) return;

        String label = raw.split("\\s+", 2)[0];
        if (!label.toLowerCase(Locale.ROOT).equals("kit")) return;

        event.setCancelled(true);
        if (!event.getPlayer().hasPermission(com.omniarena.permission.PermissionChecker.PLAYER_KIT)) {
            com.omniarena.util.MessageUtil.send(event.getPlayer(),
                    com.omniarena.config.ConfigManager.getInstance().getMessage("no-permission"));
            return;
        }
        String[] args = raw.length() == label.length()
                ? new String[0]
                : raw.substring(label.length()).trim().split("\\s+");
        if (args.length == 1 && args[0].isBlank()) {
            args = new String[0];
        }

        Player player = event.getPlayer();
        plugin.getKitCommandExecutor().handle(player, args);
    }
}
