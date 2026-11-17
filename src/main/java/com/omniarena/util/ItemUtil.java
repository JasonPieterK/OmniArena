package com.omniarena.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ItemUtil {

    private ItemUtil() {}

    /** Disables the default italic on item names/lore unless the component set it explicitly. */
    public static Component noItalic(Component c) {
        return c.decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    public static ItemStack buildGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(noItalic(ColorUtil.parse(name)));
        if (lore.length > 0) {
            List<Component> loreComponents = Arrays.stream(lore)
                    .map(l -> noItalic(ColorUtil.parse(l)))
                    .toList();
            meta.lore(loreComponents);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack buildFillerItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty().decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack cloneWithName(ItemStack original, String name) {
        if (original == null) return null;
        ItemStack clone = original.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.displayName(noItalic(ColorUtil.parse(name)));
            clone.setItemMeta(meta);
        }
        return clone;
    }

    public static boolean isSafe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return switch (item.getType()) {
            case BEDROCK, COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
                 BARRIER, SPAWNER, STRUCTURE_BLOCK -> false;
            default -> !isConfigBanned(item.getType());
        };
    }

    /** Extra bans from kits.power-cap.banned-items (only applied while the power cap is enabled). */
    private static boolean isConfigBanned(Material type) {
        var cm = com.omniarena.config.ConfigManager.getInstance();
        if (cm == null || cm.getConfig() == null) return false;
        if (!cm.getConfig().getBoolean("kits.power-cap.enabled", true)) return false;
        for (String s : cm.getConfig().getStringList("kits.power-cap.banned-items")) {
            if (s != null && s.trim().equalsIgnoreCase(type.name())) return true;
        }
        return false;
    }

    public static int getMaxEnchantLevel(ItemStack item) {
        if (item == null) return 0;
        return item.getEnchantments().values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public static void capEnchants(ItemStack item, int maxLevel) {
        if (item == null) return;
        Map<Enchantment, Integer> enchants = new java.util.HashMap<>(item.getEnchantments());
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getValue() > maxLevel) {
                item.removeEnchantment(entry.getKey());
                item.addUnsafeEnchantment(entry.getKey(), maxLevel);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> java.util.Map.Entry<K, V>[] entries(java.util.Map<K, V> map) {
        return map.entrySet().toArray(new java.util.Map.Entry[0]);
    }
}
