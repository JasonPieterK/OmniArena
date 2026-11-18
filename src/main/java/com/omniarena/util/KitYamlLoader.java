package com.omniarena.util;

import com.omniarena.kit.AdminKit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads admin kits from structured YAML (items/armor/effects sections).
 */
public final class KitYamlLoader {

    private KitYamlLoader() {}

    public static AdminKit loadAdminKit(FileConfiguration cfg, String fallbackName) {
        String name = cfg.getString("name", fallbackName);
        AdminKit kit = new AdminKit(name);
        kit.setDisplayName(name); // required for GUI display name matching
        kit.setPublic(cfg.getBoolean("public", false));
        kit.setDescription(cfg.getString("description", ""));
        kit.setCooldownSeconds(cfg.getInt("cooldown", 0));
        kit.setRequiredPermission(cfg.getString("permission", ""));
        kit.setArenaRestriction(cfg.getString("arena", ""));


        String iconStr = cfg.getString("icon", "CHEST");
        try {
            kit.setIcon(new ItemStack(Material.valueOf(iconStr.toUpperCase(Locale.ROOT))));
        } catch (Exception e) {
            kit.setIcon(new ItemStack(Material.CHEST));
        }

        if (cfg.contains("items") || cfg.isConfigurationSection("armor")) {
            kit.setContents(parseContents(cfg));
            kit.setArmor(parseArmor(cfg.getConfigurationSection("armor")));
            kit.setEffects(parseEffects(cfg.getList("effects")));
            kit.setOffHand(parseItemSection(cfg.getConfigurationSection("offhand")));
        } else {
            kit.setContents(parseLegacyContents(cfg.getList("contents")));
            kit.setArmor(parseLegacyArmor(cfg.getList("armor")));
            kit.setEffects(new ArrayList<>());
            Object off = cfg.get("offhand");
            if (off instanceof ItemStack o) kit.setOffHand(o);
            else {
                List<?> raw = cfg.getList("contents");
                if (raw != null && raw.size() > 40 && raw.get(40) instanceof ItemStack o2) kit.setOffHand(o2);
            }
        }

        if (kit.getContents() != null && kit.getIcon() != null
                && kit.getIcon().getType() == Material.CHEST) {
            kit.setIcon(findIcon(kit.getContents()));
        }

        return kit;
    }

    private static ItemStack[] parseContents(FileConfiguration cfg) {
        ItemStack[] contents = new ItemStack[36];
        List<Map<?, ?>> items = cfg.getMapList("items");
        for (Map<?, ?> raw : items) {
            int slot = toInt(raw.get("slot"), 0);
            if (slot < 0 || slot >= contents.length) continue;
            contents[slot] = parseItem(raw);
        }
        return contents;
    }

    private static ItemStack[] parseArmor(ConfigurationSection armorSection) {
        ItemStack[] armor = new ItemStack[4];
        if (armorSection == null) return armor;

        armor[0] = parseItemSection(armorSection.getConfigurationSection("helmet"));
        armor[1] = parseItemSection(armorSection.getConfigurationSection("chestplate"));
        armor[2] = parseItemSection(armorSection.getConfigurationSection("leggings"));
        armor[3] = parseItemSection(armorSection.getConfigurationSection("boots"));
        return armor;
    }

    private static ItemStack parseItemSection(ConfigurationSection section) {
        if (section == null) return null;
        return parseItem(section.getValues(false));
    }

    @SuppressWarnings("unchecked")
    private static ItemStack parseItem(Map<?, ?> raw) {
        String materialName = String.valueOf(raw.get("material"));
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }

        int amount = Math.max(1, toInt(raw.get("amount"), 1));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Object displayName = raw.get("name");
        if (displayName != null) {
            net.kyori.adventure.text.Component nm = ColorUtil.parse(String.valueOf(displayName));
            if (nm.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC)
                    == net.kyori.adventure.text.format.TextDecoration.State.NOT_SET) {
                nm = nm.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
            }
            com.omniarena.platform.Platform.get().setDisplayName(meta, nm);
        }

        Object enchants = raw.get("enchants");
        if (enchants instanceof Map<?, ?> enchantMap) {
            for (Map.Entry<?, ?> entry : enchantMap.entrySet()) {
                Enchantment enchantment = resolveEnchantment(String.valueOf(entry.getKey()));
                if (enchantment != null) {
                    meta.addEnchant(enchantment, toInt(entry.getValue(), 1), true);
                }
            }
        }

        if (toBool(raw.get("unbreakable"))) {
            meta.setUnbreakable(true);
        }

        if (meta instanceof PotionMeta potionMeta) {
            applyPotionData(potionMeta, raw);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static void applyPotionData(PotionMeta meta, Map<?, ?> raw) {
        Object potionType = raw.get("potion-type");
        if (potionType == null) return;

        PotionEffectType effectType = resolveEffectType(String.valueOf(potionType));
        if (effectType == null) return;

        int level = Math.max(1, toInt(raw.get("potion-level"), 1));
        int duration = toInt(raw.get("potion-duration"), 20);
        meta.addCustomEffect(new PotionEffect(effectType, duration, level - 1), true);
    }

    private static List<PotionEffect> parseEffects(List<?> rawEffects) {
        List<PotionEffect> effects = new ArrayList<>();
        if (rawEffects == null) return effects;

        for (Object entry : rawEffects) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            PotionEffectType type = resolveEffectType(String.valueOf(map.get("type")));
            if (type == null) continue;

            int level = Math.max(1, toInt(map.get("level"), 1));
            int duration = toInt(map.get("duration"), 200);
            if (duration < 0) duration = Integer.MAX_VALUE / 20;
            effects.add(new PotionEffect(type, duration * 20, level - 1, false, false));
        }
        return effects;
    }

    private static ItemStack[] parseLegacyContents(List<?> list) {
        if (list == null) return new ItemStack[36];
        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < Math.min(list.size(), contents.length); i++) {
            Object o = list.get(i);
            if (o instanceof ItemStack stack) contents[i] = stack;
        }
        return contents;
    }

    private static ItemStack[] parseLegacyArmor(List<?> list) {
        if (list == null) return new ItemStack[4];
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < Math.min(list.size(), armor.length); i++) {
            Object o = list.get(i);
            if (o instanceof ItemStack stack) armor[i] = stack;
        }
        return armor;
    }

    private static ItemStack findIcon(ItemStack[] contents) {
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) return item.clone();
        }
        return new ItemStack(Material.CHEST);
    }

    private static Enchantment resolveEnchantment(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Enchantment registry = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (registry != null) return registry;
        return Enchantment.getByName(name.toUpperCase(Locale.ROOT));
    }

    private static PotionEffectType resolveEffectType(String name) {
        String normalized = name.toUpperCase(Locale.ROOT);
        if ("HARMING".equals(normalized)) normalized = "INSTANT_DAMAGE";
        if ("HEAL".equals(normalized) || "HEALING".equals(normalized)) normalized = "INSTANT_HEALTH";
        if ("DAMAGE".equals(normalized)) normalized = "INSTANT_DAMAGE";

        PotionEffectType byName = PotionEffectType.getByName(normalized);
        if (byName != null) return byName;
        return PotionEffectType.getByKey(NamespacedKey.minecraft(normalized.toLowerCase(Locale.ROOT)));
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static boolean toBool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value != null) return Boolean.parseBoolean(String.valueOf(value));
        return false;
    }
}
