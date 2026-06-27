package com.omniarena.kit;

import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;

public abstract class Kit {
    protected String name;
    protected String displayName;
    protected String description;
    protected ItemStack icon;
    protected ItemStack[] contents;   // 36-slot inventory
    protected ItemStack[] armor;      // [helmet, chestplate, leggings, boots]
    protected ItemStack offHand;
    protected List<PotionEffect> effects;
    protected int cooldownSeconds;
    protected String requiredPermission;
    protected String arenaRestriction; // empty = all arenas

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ItemStack getIcon() { return icon; }
    public void setIcon(ItemStack icon) { this.icon = icon; }
    public ItemStack[] getContents() { return contents; }
    public void setContents(ItemStack[] contents) { this.contents = contents; }
    public ItemStack[] getArmor() { return armor; }
    public void setArmor(ItemStack[] armor) { this.armor = armor; }
    public ItemStack getOffHand() { return offHand; }
    public void setOffHand(ItemStack offHand) { this.offHand = offHand; }
    public List<PotionEffect> getEffects() { return effects; }
    public void setEffects(List<PotionEffect> effects) { this.effects = effects; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(int cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    public String getRequiredPermission() { return requiredPermission; }
    public void setRequiredPermission(String requiredPermission) { this.requiredPermission = requiredPermission; }
    public String getArenaRestriction() { return arenaRestriction; }
    public void setArenaRestriction(String arenaRestriction) { this.arenaRestriction = arenaRestriction; }

    public boolean hasPermission() {
        return requiredPermission == null || requiredPermission.isBlank();
    }

    public boolean isArenaRestricted() {
        return arenaRestriction != null && !arenaRestriction.isBlank();
    }
}
