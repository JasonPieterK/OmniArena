package com.omniarena.settings;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import com.omniarena.util.ItemUtil;
import com.omniarena.web.ConfigSchemaService;
import com.omniarena.web.model.SettingDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Interactive chest-based settings editor opened by {@code /oa settings}.
 * Two levels: a category grid, then a paginated list of that category's
 * settings. Booleans toggle on click; numbers adjust with left/right/shift;
 * lists & strings fall back to a chat prompt.
 */
public final class SettingsGUI {

    private SettingsGUI() {}

    private static final int PAGE_SIZE = 45; // rows 0-44, nav on bottom row

    private static final Map<String, Material> GROUP_ICON = Map.ofEntries(
            Map.entry("Config", Material.COMPARATOR), Map.entry("Arena", Material.IRON_SWORD),
            Map.entry("Match", Material.CLOCK), Map.entry("Regen", Material.CLOCK),
            Map.entry("Pvp", Material.DIAMOND_SWORD), Map.entry("Web", Material.COMPASS),
            Map.entry("Modules", Material.COMMAND_BLOCK), Map.entry("Integrations", Material.REDSTONE),
            Map.entry("Kits", Material.CHEST), Map.entry("Visual", Material.GLOWSTONE_DUST),
            Map.entry("Performance", Material.CLOCK), Map.entry("Database", Material.BARREL),
            Map.entry("Rewards", Material.GOLD_INGOT), Map.entry("Killstreak", Material.BLAZE_POWDER),
            Map.entry("Signs", Material.OAK_SIGN), Map.entry("Quality", Material.NETHER_STAR),
            Map.entry("Plugin", Material.BOOK));

    private static ConfigSchemaService schema() {
        return new ConfigSchemaService(OmniArena.getInstance());
    }

    /** Settings grouped by their category, message keys excluded. */
    public static LinkedHashMap<String, List<SettingDefinition>> grouped() {
        LinkedHashMap<String, List<SettingDefinition>> map = new LinkedHashMap<>();
        for (SettingDefinition def : schema().settings()) {
            if (def.key() != null && def.key().startsWith("messages.")) continue;
            map.computeIfAbsent(def.group(), k -> new ArrayList<>()).add(def);
        }
        return map;
    }

    /** Sorted category names, matching the slot order of the category grid. */
    public static List<String> categoryNames() {
        List<String> names = new ArrayList<>(grouped().keySet());
        Collections.sort(names);
        return names;
    }

    /** The setting shown at the given slot of a group page, or null if the slot is empty/out of range. */
    public static SettingDefinition defAt(String group, int page, int slot) {
        if (slot < 0 || slot >= PAGE_SIZE) return null;
        List<SettingDefinition> defs = grouped().getOrDefault(group, List.of());
        int pages = Math.max(1, (int) Math.ceil(defs.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));
        int idx = page * PAGE_SIZE + slot;
        return idx < defs.size() ? defs.get(idx) : null;
    }

    /** Closes every open settings menu (used on plugin disable). */
    public static void closeAll() {
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof SettingsHolder) p.closeInventory();
        }
    }

    // ── Category grid ──────────────────────────────────────────────────────────
    public static void openCategories(Player player) {
        try {
            LinkedHashMap<String, List<SettingDefinition>> groups = grouped();
            List<String> names = new ArrayList<>(groups.keySet());
            Collections.sort(names);
            if (names.size() > 54) names = names.subList(0, 54);

            int rows = Math.min(6, Math.max(1, (int) Math.ceil(names.size() / 9.0)));
            SettingsHolder holder = new SettingsHolder("", 0, true);
            Inventory inv = Bukkit.createInventory(holder, rows * 9, "§8OmniArena · Settings");
            holder.setInventory(inv);

            int slot = 0;
            for (String g : names) {
                Material icon = GROUP_ICON.getOrDefault(g, Material.PAPER);
                inv.setItem(slot++, ItemUtil.buildGuiItem(icon, "§e§l" + g,
                        "§7" + groups.get(g).size() + " setting(s)", "§8Click to open"));
            }
            player.openInventory(inv);
        } catch (Exception e) {
            OmniArena.getInstance().getLogger().severe("Failed to open settings GUI: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§cFailed to open settings menu. Check console for details.");
        }
    }

    // ── One category, paginated ────────────────────────────────────────────────
    public static void openGroup(Player player, String group, int page) {
        List<SettingDefinition> defs = grouped().getOrDefault(group, List.of());
        int pages = Math.max(1, (int) Math.ceil(defs.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));

        SettingsHolder holder = new SettingsHolder(group, page, false);
        Inventory inv = Bukkit.createInventory(holder,
                54, "§8Settings · §e" + group + " §8(" + (page + 1) + "/" + pages + ")");
        holder.setInventory(inv);

        int from = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && from + i < defs.size(); i++) {
            inv.setItem(i, renderSetting(defs.get(from + i)));
        }
        // Nav row
        inv.setItem(45, ItemUtil.buildGuiItem(Material.ARROW, "§e« Back", "§8To categories"));
        if (page > 0) inv.setItem(48, ItemUtil.buildGuiItem(Material.SPECTRAL_ARROW, "§e‹ Previous page"));
        if (page < pages - 1) inv.setItem(50, ItemUtil.buildGuiItem(Material.SPECTRAL_ARROW, "§eNext page ›"));
        inv.setItem(49, ItemUtil.buildGuiItem(Material.BARRIER, "§c§lClose"));
        player.openInventory(inv);
    }

    private static ItemStack renderSetting(SettingDefinition def) {
        Object val = current(def);
        String type = def.type().name();
        Material icon;
        List<String> lore = new ArrayList<>();
        lore.add("§8" + def.key());
        if (def.description() != null && !def.description().isBlank()) lore.add("§7" + def.description());
        lore.add("");
        lore.add("§fValue: §a" + stringify(val));

        switch (type) {
            case "BOOLEAN" -> {
                boolean b = toBool(val);
                icon = b ? Material.LIME_DYE : Material.GRAY_DYE;
                lore.add("§eClick §7to toggle " + (b ? "§cOFF" : "§aON"));
            }
            case "INTEGER", "DECIMAL" -> {
                icon = Material.COMPARATOR;
                lore.add("§eLeft §7+1   §eRight §7-1");
                lore.add("§eShift-Left §7+10   §eShift-Right §7-10");
            }
            default -> {
                icon = Material.NAME_TAG;
                lore.add("§eClick §7to edit in chat");
            }
        }
        // Encode the key in the display name (hidden lookup) using a leading marker.
        return ItemUtil.buildGuiItem(icon, "§b§l" + def.label(), lore.toArray(new String[0]));
    }

    // ── Click handling helpers (called by the listener) ────────────────────────
    public static SettingDefinition findByLabel(String group, String label) {
        for (SettingDefinition def : grouped().getOrDefault(group, List.of())) {
            if (def.label().equals(label)) return def;
        }
        return null;
    }

    public static void adjust(Player player, SettingDefinition def, double delta) {
        Object cur = current(def);
        if (def.type() == SettingDefinition.SettingType.INTEGER) {
            long v = (long) asDouble(cur) + (long) delta;
            v = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
            ConfigManager.getInstance().setValue(def.key(), (int) v);
        } else {
            double v = Math.round((asDouble(cur) + delta) * 100.0) / 100.0;
            ConfigManager.getInstance().setValue(def.key(), v);
        }
    }

    public static void toggle(SettingDefinition def) {
        ConfigManager.getInstance().setValue(def.key(), !toBool(current(def)));
    }

    public static Object current(SettingDefinition def) {
        Object v = ConfigManager.getInstance().getValue(def.key());
        return v != null ? v : def.defaultValue();
    }

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }
    private static double asDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
    private static String stringify(Object v) {
        if (v == null) return "";
        if (v instanceof List<?> l) return String.join(", ", l.stream().map(String::valueOf).toList());
        return String.valueOf(v);
    }
}
