package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import com.omniarena.kit.AdminKit;
import com.omniarena.kit.KitGUI;
import com.omniarena.kit.KitManager;
import com.omniarena.kit.KitMenuHolder;
import com.omniarena.kit.PlayerKit;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public class InventoryClickListener implements Listener {

    private final OmniArena plugin;

    public InventoryClickListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        KitMenuHolder holder = KitGUI.getKitMenuHolder(top);
        if (holder == null) return;

        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        event.setCancelled(true);

        if (holder.getType() == KitMenuHolder.MenuType.ADMIN) {
            handleAdminKitClick(player, holder, event.getRawSlot());
            return;
        }

        handlePlayerKitClick(player, holder, event.getRawSlot(), clicked);
    }

    private void handleAdminKitClick(Player player, KitMenuHolder holder, int slot) {
        if (slot >= holder.getKitNames().size()) return;

        String kitName = holder.getKitNames().get(slot);
        Optional<AdminKit> kitOpt = KitManager.getInstance().getAdminKit(kitName);
        if (kitOpt.isEmpty()) return;

        AdminKit kit = kitOpt.get();
        if (!PermissionChecker.canUseKit(player, kit.getRequiredPermission())) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-no-permission")
                    .replace("{kit}", kit.getName()));
            return;
        }

        KitManager.getInstance().equipKit(player, kit);
        player.closeInventory();
        MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-equipped")
                .replace("{kit}", kit.getName()));
    }

    private void handlePlayerKitClick(Player player, KitMenuHolder holder, int slot, ItemStack clicked) {
        Material type = clicked.getType();
        if (type == Material.BLACK_STAINED_GLASS_PANE || type == Material.CYAN_STAINED_GLASS_PANE) return;

        if (slot == 45 || (slot == 49 && type == Material.BOOK)) {
            player.closeInventory();
            MessageUtil.send(player, "<yellow>Use <white>/kit create <name></white> to save your current inventory.");
            return;
        }

        if (slot >= holder.getKitNames().size()) return;
        String kitName = holder.getKitNames().get(slot);
        if (kitName == null || kitName.isBlank()) return;

        // Preset/admin kit (stored as "admin:<name>")
        if (kitName.startsWith("admin:")) {
            String adminName = kitName.substring(6);
            Optional<AdminKit> adminKitOpt = KitManager.getInstance().getAdminKit(adminName);
            if (adminKitOpt.isEmpty()) return;
            AdminKit kit = adminKitOpt.get();
            if (!com.omniarena.permission.PermissionChecker.canUseKit(player, kit.getRequiredPermission())) {
                MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-no-permission")
                        .replace("{kit}", kit.getName()));
                return;
            }
            KitManager.getInstance().equipKit(player, kit);
            player.closeInventory();
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-equipped")
                    .replace("{kit}", kit.getName()));
            return;
        }

        // Personal kit
        Optional<PlayerKit> kitOpt = KitManager.getInstance().getPlayerKit(player.getUniqueId(), kitName);
        if (kitOpt.isEmpty()) return;
        KitManager.getInstance().equipKit(player, kitOpt.get());
        player.closeInventory();
        MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-equipped")
                .replace("{kit}", kitOpt.get().getName()));
    }
}
