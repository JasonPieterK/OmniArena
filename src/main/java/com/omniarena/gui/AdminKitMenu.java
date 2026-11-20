package com.omniarena.gui;

import com.omniarena.config.ConfigManager;
import com.omniarena.kit.AdminKit;
import com.omniarena.kit.KitManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.ItemUtil;
import com.omniarena.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Admin preset kits: equip, publish/hide, create from your inventory, delete with confirmation. */
public final class AdminKitMenu {

    private static final int PER_PAGE = 36;

    private AdminKitMenu() {}

    public static void open(Player player) {
        open(player, 0);
    }

    private static void open(Player player, int page) {
        if (!PermissionChecker.hasAdminKit(player)) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("no-permission"));
            return;
        }
        List<AdminKit> kits = new ArrayList<>(KitManager.getInstance().getAllAdminKits());
        kits.sort(Comparator.comparing(k -> k.getName().toLowerCase()));
        int pages = Math.max(1, (kits.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        List<AdminKit> slice = kits.subList(Math.min(pg * PER_PAGE, kits.size()), Math.min((pg + 1) * PER_PAGE, kits.size()));

        int rows = Math.min(6, MenuLayout.rowsFor(Math.max(1, slice.size())) + 2);
        MenuBuilder b = new MenuBuilder("<dark_gray>Admin kits" + (pages > 1 ? " <gray>(" + (pg + 1) + "/" + pages + ")" : ""), rows);

        List<MenuBuilder.Button> buttons = new ArrayList<>();
        for (AdminKit kit : slice) {
            buttons.add(MenuBuilder.button(kitItem(kit), (p, t) -> {
                if (t.isShiftClick() && t.isLeftClick()) {
                    KitManager.getInstance().setAdminKitPublic(kit.getName(), !kit.isPublic());
                    open(p, pg);
                } else if (t.isRightClick()) {
                    confirmDelete(p, kit, pg);
                } else {
                    p.closeInventory();
                    KitManager.getInstance().equipKit(p, kit);
                    MessageUtil.send(p, ConfigManager.getInstance().getMessage("kit-equipped").replace("{kit}", kit.getName()));
                }
            }));
        }
        b.flow(buttons);
        b.set((rows - 2) * 9 + 4, MenuBuilder.item(Material.ANVIL, "<green><bold>Create kit from inventory",
                "<gray>Saves what you carry right now.", "<gray>You will type the kit name in chat.", "",
                "<yellow>Click <gray>to type a name"),
                (p, t) -> ChatPrompt.suggest(p, "Create a kit from your inventory", "/oa kits create ", "/oa kits create <name>"));
        b.footer((p, t) -> AdminHubMenu.open(p),
                "<gray>Click: equip the kit.",
                "<gray>Shift-click: toggle public / private.",
                "<gray>Right-click: delete (asks to confirm).");
        if (pg > 0) b.set(MenuLayout.footerPrev(rows), MenuBuilder.item(Material.SPECTRAL_ARROW, "<yellow>Previous page"),
                (p, t) -> open(p, pg - 1));
        if (pg < pages - 1) b.set(MenuLayout.footerNext(rows), MenuBuilder.item(Material.SPECTRAL_ARROW, "<yellow>Next page"),
                (p, t) -> open(p, pg + 1));
        b.open(player);
    }

    private static ItemStack kitItem(AdminKit kit) {
        ItemStack icon = kit.getIcon() != null ? kit.getIcon().clone() : new ItemStack(Material.CHEST);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;
        List<String> lore = new ArrayList<>();
        if (kit.getDescription() != null && !kit.getDescription().isBlank()) lore.add("<gray>" + MenuBuilder.esc(kit.getDescription()));
        lore.add("<gray>Public: " + (kit.isPublic() ? "<green>ON" : "<red>OFF"));
        if (kit.getCooldownSeconds() > 0) lore.add("<gray>Cooldown: <white>" + kit.getCooldownSeconds() + "s");
        if (kit.getRequiredPermission() != null && !kit.getRequiredPermission().isBlank()) {
            lore.add("<gray>Permission: <white>" + MenuBuilder.esc(kit.getRequiredPermission()));
        }
        lore.add("");
        lore.add("<yellow>Click <gray>to equip");
        lore.add("<yellow>Shift-click <gray>to make " + (kit.isPublic() ? "private" : "public"));
        lore.add("<yellow>Right-click <gray>to delete");
        com.omniarena.platform.Platform.get().setDisplayName(meta,
                ItemUtil.noItalic(com.omniarena.util.ColorUtil.parse("<yellow><bold>" + MenuBuilder.esc(kit.getDisplayName()))));
        com.omniarena.platform.Platform.get().setLore(meta,
                lore.stream().map(l -> ItemUtil.noItalic(com.omniarena.util.ColorUtil.parse(l))).toList());
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES, org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        icon.setItemMeta(meta);
        return icon;
    }

    private static void confirmDelete(Player player, AdminKit kit, int page) {
        MenuBuilder b = new MenuBuilder("<dark_gray>Delete kit <red>" + MenuBuilder.esc(kit.getName()) + "<dark_gray>?", 3);
        b.row(1, List.of(
                MenuBuilder.button(MenuBuilder.item(Material.LIME_CONCRETE, "<green><bold>Yes, delete it",
                        "<gray>Permanently removes the kit", "<gray>" + MenuBuilder.esc(kit.getName()) + ".", "", "<yellow>Click <gray>to confirm"),
                        (p, t) -> {
                            KitManager.getInstance().deleteAdminKit(kit.getName());
                            MessageUtil.send(p, "<yellow>Deleted kit <white>" + MenuBuilder.esc(kit.getName()) + "</white>.");
                            open(p, page);
                        }),
                MenuBuilder.button(MenuBuilder.item(Material.RED_CONCRETE, "<red><bold>No, keep it",
                        "<gray>Go back without deleting.", "", "<yellow>Click <gray>to cancel"),
                        (p, t) -> open(p, page))));
        b.footer((p, t) -> open(p, page), "<gray>Deleting a kit cannot be undone.");
        b.open(player);
    }
}
