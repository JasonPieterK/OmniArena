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

    /** Spigot has no QuitReason: remember kicks so combat-log detection can exempt them. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(org.bukkit.event.player.PlayerKickEvent event) {
        PlatformImpl.KICKED.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuitCleanup(org.bukkit.event.player.PlayerQuitEvent event) {
        // CombatListener (earlier priority) consumes the flag; drop leftovers one tick later
        org.bukkit.Bukkit.getScheduler().runTaskLater(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(PlatformImpl.class),
                () -> PlatformImpl.KICKED.remove(event.getPlayer().getUniqueId()), 1L);
    }
}
