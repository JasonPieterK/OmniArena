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
import org.bukkit.event.inventory.InventoryDragEvent;
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

        // Cancel every click while a menu is open (shift-click from the player inventory,
        // number-key swaps, double-click collect, cursor placement) to prevent item theft/insertion.
        event.setCancelled(true);

        if (event.getClickedInventory() != top) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (holder.getType() == KitMenuHolder.MenuType.ADMIN) {
            handleAdminKitClick(player, holder, event.getRawSlot());
            return;
        }

        handlePlayerKitClick(player, holder, event.getRawSlot(), clicked);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (KitGUI.getKitMenuHolder(event.getView().getTopInventory()) == null) return;
        event.setCancelled(true);
    }

    private void handleAdminKitClick(Player player, KitMenuHolder holder, int slot) {
        if (slot >= holder.getKitNames().size()) return;

        String kitName = holder.getKitNames().get(slot);
        Optional<AdminKit> kitOpt = KitManager.getInstance().getAdminKit(kitName);
        if (kitOpt.isEmpty()) return;

        AdminKit kit = kitOpt.get();
        if (!KitManager.getInstance().tryEquip(player, kit)) return;
        player.closeInventory();
        MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-equipped")
                .replace("{kit}", kit.getName()));
    }

    private void handlePlayerKitClick(Player player, KitMenuHolder holder, int slot, ItemStack clicked) {
        Material type = clicked.getType();
        if (type == Material.BLACK_STAINED_GLASS_PANE || type == Material.CYAN_STAINED_GLASS_PANE) return;

        if (slot == KitGUI.PREV_SLOT && type == Material.ARROW) {
            KitGUI.reopenCombinedKitMenu(player, holder.getPage() - 1);
            return;
        }
        if (slot == KitGUI.NEXT_SLOT && type == Material.ARROW) {
            KitGUI.reopenCombinedKitMenu(player, holder.getPage() + 1);
            return;
        }

        if (slot == 45 || slot == 49) {
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
            if (!KitManager.getInstance().tryEquip(player, kit)) return;
            player.closeInventory();
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-equipped")
                    .replace("{kit}", kit.getName()));
            return;
        }

        // Personal kit
        Optional<PlayerKit> kitOpt = KitManager.getInstance().getPlayerKit(player.getUniqueId(), kitName);
        if (kitOpt.isEmpty()) return;
        if (!KitManager.getInstance().tryEquip(player, kitOpt.get())) return;
        player.closeInventory();
        MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-equipped")
                .replace("{kit}", kitOpt.get().getName()));
    }
}
