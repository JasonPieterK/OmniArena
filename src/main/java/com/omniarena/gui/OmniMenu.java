package com.omniarena.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/** Inventory holder for every OmniArena chest menu: a slot to click-handler map. Clicks are dispatched by {@link MenuListener}. */
public final class OmniMenu implements InventoryHolder {

    /** A click action. Runs on the main thread, one tick after the click. */
    @FunctionalInterface
    public interface Handler {
        void click(Player player, ClickType type);
    }

    private final Map<Integer, Handler> handlers = new HashMap<>();
    private final Inventory inventory;

    OmniMenu(int rows, Component title) {
        this.inventory = com.omniarena.platform.Platform.get().createInventory(this, rows * MenuLayout.WIDTH, title);
    }

    void bind(int slot, Handler handler) {
        if (handler == null) handlers.remove(slot); else handlers.put(slot, handler);
    }

    Handler handlerAt(int slot) {
        return handlers.get(slot);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
