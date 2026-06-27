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
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory() == null || event.getInventory().getHolder() == null) return;
        if (!(event.getInventory().getHolder() instanceof SettingsHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.isOnline()) return;
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;

        try {
            String name = strip(legacyName(clicked));
            int slot = event.getRawSlot();

            // ── Category grid ──
            if (holder.isCategoryView()) {
                if (name.isBlank()) return;
                SettingsGUI.openGroup(player, name, 0);
                return;
            }

            // ── Group view nav ──
            switch (slot) {
                case 45 -> { SettingsGUI.openCategories(player); return; }
                case 49 -> { player.closeInventory(); return; }
                case 48 -> { SettingsGUI.openGroup(player, holder.getGroup(), Math.max(0, holder.getPage() - 1)); return; }
                case 50 -> { SettingsGUI.openGroup(player, holder.getGroup(), holder.getPage() + 1); return; }
                default -> { /* fall through to setting click */ }
            }
            if (slot >= 45) return;

            SettingDefinition def = SettingsGUI.findByLabel(holder.getGroup(), name);
            if (def == null) return;

            switch (def.type()) {
                case BOOLEAN -> { 
                    SettingsGUI.toggle(def); 
                    SettingsGUI.openGroup(player, holder.getGroup(), holder.getPage()); 
                }
                case INTEGER, DECIMAL -> {
                    double base = def.type() == SettingDefinition.SettingType.DECIMAL ? 0.5 : 1;
                    double step = event.isShiftClick() ? base * 10 : base;
                    if (event.isRightClick()) step = -step;
                    SettingsGUI.adjust(player, def, step);
                    SettingsGUI.openGroup(player, holder.getGroup(), holder.getPage());
                }
                default -> {
                    // String / select / list — edit in chat via the existing setter.
                    player.closeInventory();
                    player.sendMessage(ColorUtil.parse("<gray>Set <yellow>" + def.key() + "</yellow>:")
                            .append(ColorUtil.parse(" <green>[click to type a value]"))
                            .clickEvent(ClickEvent.suggestCommand("/oa settings set " + def.key() + " " + SettingsGUI.current(def)))
                            .hoverEvent(HoverEvent.showText(ColorUtil.parse("<gray>" + def.description()))));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error in settings menu click: " + e.getMessage());
            e.printStackTrace();
            player.closeInventory();
            player.sendMessage("§cAn error occurred. Please try again.");
        }
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
