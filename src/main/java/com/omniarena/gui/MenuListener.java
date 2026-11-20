package com.omniarena.gui;

import com.omniarena.OmniArena;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;

/** Single listener for every {@link OmniMenu}: cancels all clicks/drags and dispatches slot handlers one tick later. */
public final class MenuListener implements Listener {

    private final OmniArena plugin;

    public MenuListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof OmniMenu menu)) return;
        // Cancel everything (shift-click from the player inventory, number keys, offhand swap, drop, double-click collect)
        event.setCancelled(true);
        if (plugin.isStopped()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != top) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;
        OmniMenu.Handler handler = menu.handlerAt(slot);
        if (handler == null) return;
        var type = event.getClick();
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.isStopped() || !player.isOnline()) return;
            // The menu may have been replaced or closed before this tick runs
            if (player.getOpenInventory().getTopInventory() != top) return;
            try {
                handler.click(player, type);
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "A menu action failed (" + e + "). The menu was closed; retry or use the equivalent /oa command.", e);
                player.closeInventory();
                com.omniarena.util.MessageUtil.send(player, "<red>That menu action failed and the menu was closed. <gray>An admin can check the console for details.");
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof OmniMenu) event.setCancelled(true);
    }

    /** Closes every open OmniArena menu (plugin disable, /oa plugin stop). */
    public static void closeAll() {
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof OmniMenu) p.closeInventory();
        }
    }
}
