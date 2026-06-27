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

    public static ItemStack buildGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(ColorUtil.parse(name));
        if (lore.length > 0) {
            List<Component> loreComponents = Arrays.stream(lore)
                    .map(ColorUtil::parse)
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
            meta.displayName(Component.empty());
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
            meta.displayName(ColorUtil.parse(name));
            clone.setItemMeta(meta);
        }
        return clone;
    }

    public static boolean isSafe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return switch (item.getType()) {
            case BEDROCK, COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
                 BARRIER, SPAWNER, STRUCTURE_BLOCK -> false;
            default -> true;
        };
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
