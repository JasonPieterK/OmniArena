package com.omniarena.settings;

import com.omniarena.OmniArena;
import com.omniarena.util.ColorUtil;
import com.omniarena.web.model.SettingDefinition;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class SettingsMenuListener implements Listener {

    private final OmniArena plugin;

    public SettingsMenuListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SettingsHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        org.bukkit.inventory.Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SettingsHolder holder)) return;
        // Cancel everything (shift-click, number keys, offhand swap, drop, double-click collect, ...)
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.isOnline()) return;
        // Only clicks inside the menu itself do anything
        if (event.getClickedInventory() != top) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;

        if (!com.omniarena.permission.PermissionChecker.hasAdminReload(player)) {
            runNextTick(player::closeInventory);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;

        try {
            // ── Category grid ──
            if (holder.isCategoryView()) {
                java.util.List<String> names = SettingsGUI.categoryNames();
                if (slot >= names.size()) return;
                String group = names.get(slot);
                runNextTick(() -> SettingsGUI.openGroup(player, group, 0));
                return;
            }

            // ── Group view nav ──
            final String group = holder.getGroup();
            final int page = holder.getPage();
            switch (slot) {
                case 45 -> { runNextTick(() -> SettingsGUI.openCategories(player)); return; }
                case 49 -> { runNextTick(player::closeInventory); return; }
                case 48 -> {
                    if (clicked.getType() == org.bukkit.Material.SPECTRAL_ARROW && page > 0)
                        runNextTick(() -> SettingsGUI.openGroup(player, group, page - 1));
                    return;
                }
                case 50 -> {
                    if (clicked.getType() == org.bukkit.Material.SPECTRAL_ARROW)
                        runNextTick(() -> SettingsGUI.openGroup(player, group, page + 1));
                    return;
                }
                default -> { /* fall through to setting click */ }
            }
            if (slot >= 45) return;

            SettingDefinition def = SettingsGUI.defAt(group, page, slot);
            if (def == null) return;

            switch (def.type()) {
                case BOOLEAN -> {
                    SettingsGUI.toggle(def);
                    runNextTick(() -> SettingsGUI.openGroup(player, group, page));
                }
                case INTEGER, DECIMAL -> {
                    double base = def.type() == SettingDefinition.SettingType.DECIMAL ? 0.5 : 1;
                    double step = event.isShiftClick() ? base * 10 : base;
                    if (event.isRightClick()) step = -step;
                    SettingsGUI.adjust(player, def, step);
                    runNextTick(() -> SettingsGUI.openGroup(player, group, page));
                }
                default -> {
                    runNextTick(player::closeInventory);
                    String prompt = "/oa settings set " + def.key() + " " + SettingsGUI.current(def);
                    net.kyori.adventure.text.Component msg = ColorUtil.parse("<gray>Set <yellow>" + def.key() + "</yellow>: <green>[click to type]")
                            .clickEvent(ClickEvent.suggestCommand(prompt))
                            .hoverEvent(HoverEvent.showText(ColorUtil.parse("<gray>" + (def.description() == null ? "" : def.description()))));
                    player.sendMessage(msg);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error in settings menu click: " + e.getMessage());
            e.printStackTrace();
            runNextTick(player::closeInventory);
            player.sendMessage("§cAn error occurred. Please try again.");
        }
    }

    /** Runs on the next tick (never open/close inventories from inside a click handler); no-op if the plugin is disabled. */
    private void runNextTick(Runnable r) {
        if (!plugin.isEnabled()) return;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            try { r.run(); } catch (Exception e) { plugin.getLogger().warning("Settings menu action failed: " + e.getMessage()); }
        });
    }

    private String legacyName(ItemStack item) {
        try {
            return com.omniarena.util.ColorUtil.toLegacy(item.getItemMeta().displayName());
        } catch (Throwable t) {
            return item.getItemMeta().getDisplayName();
        }
    }

    /** Remove §x color codes and bold/style markers, leaving the readable text. */
    private String strip(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    }
}
