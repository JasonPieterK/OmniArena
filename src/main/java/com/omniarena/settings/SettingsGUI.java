package com.omniarena.settings;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import com.omniarena.gui.AdminHubMenu;
import com.omniarena.gui.ChatPrompt;
import com.omniarena.gui.MenuBuilder;
import com.omniarena.gui.MenuLayout;
import com.omniarena.gui.MenuListener;
import com.omniarena.web.ConfigSchemaService;
import com.omniarena.web.model.SettingDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Interactive chest-based settings editor opened by {@code /oa settings}, built on the OmniMenu framework.
 * Two levels: a category grid, then a paginated list of that category's
 * settings. Booleans are lime/red concrete toggles; numbers adjust with left/right/shift;
 * lists & strings fall back to a chat prompt.
 */
public final class SettingsGUI {

    private SettingsGUI() {}

    private static final int PAGE_SIZE = 45; // rows 0-44, nav on bottom row

    /** One entry per top-level section of config.yml (the category name is the section name, title-cased). */
    private static final Map<String, Material> GROUP_ICON = Map.ofEntries(
            Map.entry("General", Material.COMPARATOR), Map.entry("Arenas", Material.IRON_SWORD),
            Map.entry("Matches", Material.CLOCK), Map.entry("Regen", Material.BRICKS),
            Map.entry("Ranked", Material.NETHER_STAR), Map.entry("Kits", Material.CHEST),
            Map.entry("Rewards", Material.GOLD_INGOT), Map.entry("Betting", Material.EMERALD),
            Map.entry("Tournaments", Material.GOLDEN_HELMET), Map.entry("Pvp", Material.DIAMOND_SWORD),
            Map.entry("Visuals", Material.GLOWSTONE_DUST), Map.entry("Integrations", Material.REDSTONE),
            Map.entry("Web", Material.COMPASS), Map.entry("Health", Material.GOLDEN_APPLE),
            Map.entry("Advanced", Material.REPEATER));

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

    /** Category names in config.yml order, matching the slot order of the category grid. */
    public static List<String> categoryNames() {
        return new ArrayList<>(grouped().keySet()); // config.yml order
    }

    /** The setting shown at the given slot of a group page, or null if the slot is empty/out of range. */
    public static SettingDefinition defAt(String group, int page, int slot) {
        List<SettingDefinition> defs = grouped().getOrDefault(group, List.of());
        int pages = Math.max(1, (int) Math.ceil(defs.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * PAGE_SIZE;
        int count = Math.max(0, Math.min(PAGE_SIZE, defs.size() - from));
        int[] slots = MenuLayout.flowSlots(count, 5);
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) return defs.get(from + i);
        return null;
    }

    /** Closes every open OmniArena menu (used on plugin disable). */
    public static void closeAll() {
        MenuListener.closeAll();
    }

    private static boolean allowed(Player player) {
        if (com.omniarena.permission.PermissionChecker.hasAdminReload(player)) return true;
        player.closeInventory();
        return false;
    }

    // ── Category grid ──────────────────────────────────────────────────────────
    public static void openCategories(Player player) {
        try {
            if (!allowed(player)) return;
            LinkedHashMap<String, List<SettingDefinition>> groups = grouped();
            List<String> names = new ArrayList<>(groups.keySet()); // config.yml order
            if (names.size() > 45) names = names.subList(0, 45);

            int rows = Math.min(6, MenuLayout.rowsFor(names.size()) + 1);
            MenuBuilder b = new MenuBuilder("<dark_gray>OmniArena <gray>Settings", rows);
            List<MenuBuilder.Button> buttons = new ArrayList<>();
            for (String g : names) {
                Material icon = GROUP_ICON.getOrDefault(g, Material.PAPER);
                buttons.add(MenuBuilder.button(MenuBuilder.item(icon, "<yellow><bold>" + MenuBuilder.esc(g),
                        "<gray>" + groups.get(g).size() + " setting(s)", "", "<yellow>Click <gray>to open"),
                        (p, t) -> openGroup(p, g, 0)));
            }
            b.flow(buttons);
            b.footer((p, t) -> AdminHubMenu.open(p), "<gray>Global plugin settings, grouped by topic.", "<gray>Changes save immediately.");
            b.open(player);
        } catch (Exception e) {
            OmniArena.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Failed to open the settings GUI (" + e + "). Full trace below. Fix: use the chat version /oa settings, and report the trace.", e);
            player.sendMessage("\u00a7cCould not open the settings menu. \u00a77Use \u00a7f/oa settings\u00a77 (chat version) instead; an admin can check the console for the cause.");
        }
    }

    // ── One category, paginated ────────────────────────────────────────────────
    public static void openGroup(Player player, String group, int page) {
        if (!allowed(player)) return;
        List<SettingDefinition> defs = grouped().getOrDefault(group, List.of());
        int pages = Math.max(1, (int) Math.ceil(defs.size() / (double) PAGE_SIZE));
        final int pg = Math.max(0, Math.min(page, pages - 1));

        int from = pg * PAGE_SIZE;
        int count = Math.max(0, Math.min(PAGE_SIZE, defs.size() - from));
        int rows = Math.min(6, MenuLayout.rowsFor(Math.max(1, count)) + 1);
        MenuBuilder b = new MenuBuilder("<dark_gray>Settings <gray>\u00b7 <yellow>" + MenuBuilder.esc(group)
                + " <dark_gray>(" + (pg + 1) + "/" + pages + ")", rows);

        int[] slots = MenuLayout.flowSlots(count, rows - 1);
        for (int i = 0; i < slots.length; i++) {
            SettingDefinition def = defs.get(from + i);
            b.set(slots[i], renderSetting(def), (p, t) -> click(p, def, t, group, pg));
        }
        b.footer((p, t) -> openCategories(p), "<gray>Click a setting to change it.",
                "<gray>Green = on, red = off. Numbers: left +, right -, shift x10.",
                "<gray>Text and lists are typed in chat.");
        if (pg > 0) b.set(MenuLayout.footerPrev(rows), MenuBuilder.item(Material.SPECTRAL_ARROW, "<yellow>Previous page"),
                (p, t) -> openGroup(p, group, pg - 1));
        if (pg < pages - 1) b.set(MenuLayout.footerNext(rows), MenuBuilder.item(Material.SPECTRAL_ARROW, "<yellow>Next page"),
                (p, t) -> openGroup(p, group, pg + 1));
        b.open(player);
    }

    private static void click(Player player, SettingDefinition def, org.bukkit.event.inventory.ClickType type, String group, int page) {
        if (!allowed(player)) return;
        switch (def.type()) {
            case BOOLEAN -> {
                toggle(def);
                openGroup(player, group, page);
            }
            case INTEGER, DECIMAL -> {
                double base = def.type() == SettingDefinition.SettingType.DECIMAL ? 0.5 : 1;
                double step = type.isShiftClick() ? base * 10 : base;
                if (type.isRightClick()) step = -step;
                adjust(player, def, step);
                openGroup(player, group, page);
            }
            default -> {
                Object curVal = current(def);
                String prompt = "/oa settings set " + def.key() + " " + stringify(curVal);
                ChatPrompt.suggest(player, "Set " + def.key(), prompt, def.description());
            }
        }
    }

    private static ItemStack renderSetting(SettingDefinition def) {
        Object val = current(def);
        String name = "<aqua><bold>" + MenuBuilder.esc(def.label());
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>" + MenuBuilder.esc(def.key()));
        if (def.description() != null && !def.description().isBlank()) lore.add("<gray>" + MenuBuilder.esc(def.description()));
        lore.add("");
        switch (def.type()) {
            case BOOLEAN -> {
                return MenuBuilder.toggle(toBool(val), name, lore.toArray(new String[0]));
            }
            case INTEGER, DECIMAL -> {
                lore.add("<white>Value: <green>" + MenuBuilder.esc(stringify(val)));
                lore.add("<yellow>Left <gray>+1   <yellow>Right <gray>-1");
                lore.add("<yellow>Shift <gray>for 10");
                return MenuBuilder.item(Material.COMPARATOR, name, lore.toArray(new String[0]));
            }
            case LIST -> {
                lore.add("<white>Value: <green>" + MenuBuilder.esc(stringify(val)));
                lore.add("<yellow>Click <gray>to edit in chat");
                return MenuBuilder.item(Material.WRITABLE_BOOK, name, lore.toArray(new String[0]));
            }
            default -> {
                lore.add("<white>Value: <green>" + MenuBuilder.esc(stringify(val)));
                lore.add("<yellow>Click <gray>to edit in chat");
                return MenuBuilder.item(Material.NAME_TAG, name, lore.toArray(new String[0]));
            }
        }
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
