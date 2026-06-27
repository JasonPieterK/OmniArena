package com.omniarena.settings;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Marks a settings inventory and remembers which view it is showing. */
public class SettingsHolder implements InventoryHolder {

    private final String group;
    private final int page;
    private final boolean categoryView;

    public SettingsHolder(String group, int page, boolean categoryView) {
        this.group = group;
        this.page = page;
        this.categoryView = categoryView;
    }

    public String getGroup()        { return group; }
    public int getPage()            { return page; }
    public boolean isCategoryView() { return categoryView; }

    @Override public @NotNull Inventory getInventory() { return null; }
}
