package com.omniarena.kit;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public final class KitMenuHolder implements InventoryHolder {

    public enum MenuType {
        ADMIN,
        PLAYER,
        PREVIEW
    }

    private final MenuType type;
    private final List<String> kitNames;
    private Inventory inventory;
    private int page;
    private int totalPages = 1;

    public KitMenuHolder(MenuType type, List<String> kitNames) {
        this.type = type;
        this.kitNames = List.copyOf(kitNames);
    }

    public KitMenuHolder(MenuType type, List<String> kitNames, int page, int totalPages) {
        this(type, kitNames);
        this.page = page;
        this.totalPages = Math.max(1, totalPages);
    }

    public int getPage() { return page; }

    public int getTotalPages() { return totalPages; }

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
