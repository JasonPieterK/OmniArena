package com.omniarena.kit;

import com.omniarena.util.ColorUtil;
import com.omniarena.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class KitGUI {

    public static void openAdminKitSelector(Player player, List<AdminKit> kits) {
        List<AdminKit> publicKits = kits.stream().filter(AdminKit::isPublic).toList();
        KitMenuHolder holder = new KitMenuHolder(
                KitMenuHolder.MenuType.ADMIN,
                publicKits.stream().map(Kit::getName).toList());

        int size = Math.min(54, Math.max(9, ((Math.max(publicKits.size(), 1) - 1) / 9 + 1) * 9));
        Inventory inv = createMenu(holder, size, ColorUtil.parse("<dark_aqua><bold>Arena Kits</bold></dark_aqua>"));

        int slot = 0;
        for (AdminKit kit : publicKits) {
            if (slot >= inv.getSize()) break;
            inv.setItem(slot++, buildKitItem(kit));
        }

        fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
    }

    /**
     * Combined menu: personal kits (left 4 cols) | divider | preset/admin kits (right 4 cols).
     * kitNames list is slot-indexed (null = filler). "admin:" prefix = admin kit.
     */
    public static void openCombinedKitMenu(Player player, List<PlayerKit> playerKits, List<AdminKit> adminKits) {
        // Build slot-indexed name list (size 54, null = not a kit)
        List<String> slotNames = new java.util.ArrayList<>(java.util.Collections.nCopies(54, null));

        // Player kit slots: cols 0-3 of rows 0-4 → slots 0,1,2,3, 9,10,11,12, 18,19,20,21, 27,28,29,30, 36,37,38,39
        int[] playerSlots = {0,1,2,3, 9,10,11,12, 18,19,20,21, 27,28,29,30, 36,37,38,39};
        // Admin kit slots: cols 5-8 of rows 0-4 → slots 5,6,7,8, 14,15,16,17, 23,24,25,26, 32,33,34,35, 41,42,43,44
        int[] adminSlots  = {5,6,7,8, 14,15,16,17, 23,24,25,26, 32,33,34,35, 41,42,43,44};

        for (int i = 0; i < Math.min(playerKits.size(), playerSlots.length); i++)
            slotNames.set(playerSlots[i], playerKits.get(i).getName());
        for (int i = 0; i < Math.min(adminKits.size(), adminSlots.length); i++)
            slotNames.set(adminSlots[i], "admin:" + adminKits.get(i).getName());

        KitMenuHolder holder = new KitMenuHolder(KitMenuHolder.MenuType.PLAYER, slotNames);
        Inventory inv = createMenu(holder, 54, ColorUtil.parse("<gold><bold>My Kits</bold> <gray>│ <aqua>Preset Kits</aqua></gold>"));

        // Place player kits
        for (int i = 0; i < Math.min(playerKits.size(), playerSlots.length); i++)
            inv.setItem(playerSlots[i], buildKitItem(playerKits.get(i)));

        // Divider column (col 4 = slots 4,13,22,31,40)
        ItemStack divider = ItemUtil.buildFillerItem(Material.CYAN_STAINED_GLASS_PANE);
        for (int row = 0; row < 5; row++) inv.setItem(row * 9 + 4, divider);

        // Place admin kits with PRESET label
        for (int i = 0; i < Math.min(adminKits.size(), adminSlots.length); i++) {
            ItemStack item = buildKitItem(adminKits.get(i));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<net.kyori.adventure.text.Component> lore = meta.lore() == null
                        ? new java.util.ArrayList<>() : new java.util.ArrayList<>(meta.lore());
                lore.add(0, ColorUtil.parse("<aqua><bold>[PRESET KIT]</bold></aqua>"));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(adminSlots[i], item);
        }

        // Bottom row: create button at 45, filler, info at 49
        inv.setItem(45, ItemUtil.buildGuiItem(Material.ANVIL, "<green><bold>+ Create Kit</bold>",
                "<gray>Save your current inventory as a kit."));
        inv.setItem(49, ItemUtil.buildGuiItem(Material.BOOK,
                "<yellow>Kit Help</yellow>",
                "<gray>/kit create <name> — save inventory",
                "<gray>/kit delete <name> — remove kit",
                "<gray>/kit rename <old> <new> — rename",
                "<gray>/kit share <name> <player> — share"));

        fillEmpty(inv, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inv);
    }

    public static void openPlayerKitMenu(Player player, List<PlayerKit> kits) {
        KitMenuHolder holder = new KitMenuHolder(
                KitMenuHolder.MenuType.PLAYER,
                kits.stream().map(Kit::getName).toList());

        Inventory inv = createMenu(holder, 54, ColorUtil.parse("<gold><bold>My Kits</bold></gold>"));

        int slot = 0;
        for (PlayerKit kit : kits) {
            if (slot >= 45) break;
            inv.setItem(slot++, buildKitItem(kit));
        }

        inv.setItem(49, ItemUtil.buildGuiItem(Material.ANVIL, "<green><bold>+ Create Kit</bold>",
                "<gray>Save your current inventory as a kit."));

        fillEmpty(inv, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inv);
    }

    public static void openKitPreview(Player player, Kit kit) {
        Inventory inv = Bukkit.createInventory(null, 54,
                ColorUtil.parse("<gray>Preview: <yellow>" + kit.getDisplayName()));

        if (kit.getContents() != null) {
            ItemStack[] contents = kit.getContents();
            for (int i = 0; i < Math.min(contents.length, 36); i++) {
                inv.setItem(i, contents[i]);
            }
        }

        if (kit.getArmor() != null) {
            ItemStack[] armor = kit.getArmor();
            for (int i = 0; i < Math.min(armor.length, 4); i++) {
                inv.setItem(36 + i, armor[i]);
            }
        }

        inv.setItem(49, ItemUtil.buildGuiItem(Material.BARRIER, "<red>Close", "<gray>Close preview."));
        player.openInventory(inv);
    }

    public static boolean isAdminKitMenu(Inventory inv) {
        return inv.getHolder() instanceof KitMenuHolder holder
                && holder.getType() == KitMenuHolder.MenuType.ADMIN;
    }

    public static boolean isPlayerKitMenu(Inventory inv) {
        return inv.getHolder() instanceof KitMenuHolder holder
                && holder.getType() == KitMenuHolder.MenuType.PLAYER;
    }

    public static KitMenuHolder getKitMenuHolder(Inventory inv) {
        return inv.getHolder() instanceof KitMenuHolder holder ? holder : null;
    }

    private static ItemStack buildKitItem(Kit kit) {
        ItemStack icon = kit.getIcon() != null ? kit.getIcon().clone() : new ItemStack(Material.CHEST);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        meta.displayName(ColorUtil.parse("<yellow><bold>" + kit.getDisplayName() + "</bold></yellow>"));

        List<Component> lore = new ArrayList<>();
        if (kit.getDescription() != null && !kit.getDescription().isBlank()) {
            lore.add(ColorUtil.parse("<gray>" + kit.getDescription()));
            lore.add(Component.empty());
        }

        if (kit.getContents() != null) {
            int itemCount = (int) java.util.Arrays.stream(kit.getContents())
                    .filter(i -> i != null && i.getType() != Material.AIR).count();
            lore.add(ColorUtil.parse("<dark_gray>Items: <white>" + itemCount));
        }

        if (kit.getCooldownSeconds() > 0) {
            lore.add(ColorUtil.parse("<dark_gray>Cooldown: <white>" + kit.getCooldownSeconds() + "s"));
        }

        if (kit.getRequiredPermission() != null && !kit.getRequiredPermission().isBlank()) {
            lore.add(ColorUtil.parse("<dark_red>Requires: <gray>" + kit.getRequiredPermission()));
        }

        lore.add(Component.empty());
        lore.add(ColorUtil.parse("<green>Click to equip"));

        meta.lore(lore);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        icon.setItemMeta(meta);
        return icon;
    }

    private static void fillEmpty(Inventory inv, Material filler) {
        ItemStack glass = ItemUtil.buildFillerItem(filler);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }

    private static Inventory createMenu(KitMenuHolder holder, int size, Component title) {
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);
        return inv;
    }
}
