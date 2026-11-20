package com.omniarena.listener;

import com.omniarena.OmniArena;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Arenas whose world is not loaded are kept (never dropped) and become available again when that world
 * loads, e.g. a Multiverse world that finishes loading after OmniArena started.
 */
public class WorldListener implements Listener {

    private final OmniArena plugin;

    public WorldListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        if (plugin.isStopped() || plugin.getArenaManager() == null) return;
        try {
            plugin.getArenaManager().onWorldLoad(event.getWorld());
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not re-enable arenas for world '" + event.getWorld().getName() + "' (" + t + "). Run /oa reload when no match is active.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        if (plugin.isStopped() || plugin.getArenaManager() == null || event.isCancelled()) return;
        try {
            plugin.getArenaManager().onWorldUnload(event.getWorld());
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not mark arenas of unloaded world '" + event.getWorld().getName() + "' (" + t + ").");
        }
    }
}
