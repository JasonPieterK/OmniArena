package com.omniarena.kit;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public final class KitMenuHolder implements InventoryHolder {

    public enum MenuType {
        ADMIN,
        PLAYER
    }

    private final MenuType type;
    private final List<String> kitNames;
    private Inventory inventory;

    public KitMenuHolder(MenuType type, List<String> kitNames) {
        this.type = type;
        this.kitNames = List.copyOf(kitNames);
    }

    public MenuType getType() {
        return type;
    }

    public List<String> getKitNames() {
        return kitNames;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
