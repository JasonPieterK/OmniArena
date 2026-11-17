package com.omniarena.platform;

import com.omniarena.listener.ChatListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/** Spigot chat hook (AsyncPlayerChatEvent). */
final class SpigotChatListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (ChatListener.route(event.getPlayer(), event.getMessage())) event.setCancelled(true);
    }
}
