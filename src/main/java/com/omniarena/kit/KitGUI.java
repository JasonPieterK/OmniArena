package com.omniarena.kit;

import com.omniarena.config.ConfigManager;
import com.omniarena.gui.ChatPrompt;
import com.omniarena.gui.MainMenu;
import com.omniarena.gui.MenuBuilder;
import com.omniarena.gui.MenuLayout;
import com.omniarena.gui.MenuListener;
import com.omniarena.util.ColorUtil;
import com.omniarena.util.ItemUtil;
import com.omniarena.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Kit menus (built on the OmniMenu framework): personal kits, preset kits and kit previews. */
public class KitGUI {

    /** Footer slots of the 6-row combined menu. */
    public static final int PREV_SLOT = MenuLayout.footerPrev(6);
    public static final int NEXT_SLOT = MenuLayout.footerNext(6);

    private static final int PER_PAGE = 16;

    /** Public preset kits only, one click to equip. */
    public static void openAdminKitSelector(Player player, List<AdminKit> kits) {
        List<AdminKit> publicKits = kits.stream().filter(AdminKit::isPublic).toList();
        int shown = Math.min(publicKits.size(), 45);
        int rows = Math.min(6, MenuLayout.rowsFor(Math.max(1, shown)) + 1);
        MenuBuilder b = new MenuBuilder("<dark_aqua><bold>Arena Kits</bold>", rows);
        List<MenuBuilder.Button> buttons = new ArrayList<>();
        for (AdminKit kit : publicKits.subList(0, shown)) {
            buttons.add(MenuBuilder.button(buildKitItem(kit, true), (p, t) -> equip(p, kit)));
        }
        b.flow(buttons);
        b.footer((p, t) -> MainMenu.open(p), "<gray>Click a kit to equip it.");
        b.open(player);
    }

    /**
     * Combined menu: personal kits (left four columns) and preset/admin kits (right four columns).
     */
    public static void openCombinedKitMenu(Player player, List<PlayerKit> playerKits, List<AdminKit> adminKits) {
        openCombinedKitMenu(player, playerKits, adminKits, 0);
    }

    /** Re-opens the combined menu at the given page using the player's currently visible kits. */
    public static void reopenCombinedKitMenu(Player player, int page) {
        List<PlayerKit> playerKits = KitManager.getInstance().getPlayerKits(player.getUniqueId());
        List<AdminKit> adminKits = KitManager.getInstance().getAllAdminKits().stream()
                .filter(k -> k.isPublic() || player.hasPermission("omniarena.admin")).toList();
        openCombinedKitMenu(player, playerKits, adminKits, page);
    }

    public static void openCombinedKitMenu(Player player, List<PlayerKit> allPlayerKits, List<AdminKit> allAdminKits, int page) {
        int totalPages = Math.max(1, Math.max(
                (allPlayerKits.size() + PER_PAGE - 1) / PER_PAGE,
                (allAdminKits.size() + PER_PAGE - 1) / PER_PAGE));
        final int pg = Math.max(0, Math.min(page, totalPages - 1));
        List<PlayerKit> playerKits = allPlayerKits.subList(Math.min(pg * PER_PAGE, allPlayerKits.size()),
                Math.min((pg + 1) * PER_PAGE, allPlayerKits.size()));
        List<AdminKit> adminKits = allAdminKits.subList(Math.min(pg * PER_PAGE, allAdminKits.size()),
                Math.min((pg + 1) * PER_PAGE, allAdminKits.size()));

        MenuBuilder b = new MenuBuilder("<gold><bold>My Kits</bold> <gray>| <aqua>Preset Kits"
                + (totalPages > 1 ? " <dark_gray>(" + (pg + 1) + "/" + totalPages + ")" : ""), 6);

        // Personal kits: columns 0-3 of rows 0-3. Preset kits: columns 5-8 of rows 0-3.
        for (int i = 0; i < playerKits.size(); i++) {
            PlayerKit kit = playerKits.get(i);
            b.set((i / 4) * 9 + (i % 4), buildKitItem(kit, false), (p, t) -> equipPersonal(p, kit));
        }
        for (int i = 0; i < adminKits.size(); i++) {
            AdminKit kit = adminKits.get(i);
            b.set((i / 4) * 9 + 5 + (i % 4), buildKitItem(kit, true), (p, t) -> equip(p, kit));
        }
        if (playerKits.isEmpty() && pg == 0) {
            b.set(10, MenuBuilder.item(Material.COBWEB, "<gray><bold>No personal kits",
                    "<gray>Save your inventory with", "<gray>the button below."), null);
        }

        b.row(4, List.of(MenuBuilder.button(MenuBuilder.item(Material.ANVIL, "<green><bold>+ Create Kit",
                "<gray>Save your current inventory as a kit.", "<gray>You will type the name in chat.", "",
                "<yellow>Click <gray>to type a name"),
                (p, t) -> ChatPrompt.suggest(p, "Save your inventory as a kit", "/kit create ", "/kit create <name>"))));

        b.footer((p, t) -> MainMenu.open(p),
                "<gray>Left side: your kits. Right side: preset kits.",
                "<gray>Click a kit to equip it.",
                "<gray>/kit create <name>  - save inventory",
                "<gray>/kit delete <name>  - remove a kit",
                "<gray>/kit rename <old> <new>",
                "<gray>/kit share <name> <player>");
        if (pg > 0) b.set(PREV_SLOT, MenuBuilder.item(Material.SPECTRAL_ARROW, "<yellow>Previous page",
                "<gray>Page " + pg + "/" + totalPages), (p, t) -> reopenCombinedKitMenu(p, pg - 1));
        if (pg < totalPages - 1) b.set(NEXT_SLOT, MenuBuilder.item(Material.SPECTRAL_ARROW, "<yellow>Next page",
                "<gray>Page " + (pg + 2) + "/" + totalPages), (p, t) -> reopenCombinedKitMenu(p, pg + 1));
        b.open(player);
    }

    public static void openPlayerKitMenu(Player player, List<PlayerKit> kits) {
        int shown = Math.min(kits.size(), 36);
        int rows = Math.min(6, MenuLayout.rowsFor(Math.max(1, shown)) + 2);
        MenuBuilder b = new MenuBuilder("<gold><bold>My Kits</bold>", rows);
        List<MenuBuilder.Button> buttons = new ArrayList<>();
        for (PlayerKit kit : kits.subList(0, shown)) {
            buttons.add(MenuBuilder.button(buildKitItem(kit, false), (p, t) -> equipPersonal(p, kit)));
        }
        b.flow(buttons);
        b.set((rows - 2) * 9 + 4, MenuBuilder.item(Material.ANVIL, "<green><bold>+ Create Kit",
                "<gray>Save your current inventory as a kit.", "", "<yellow>Click <gray>to type a name"),
                (p, t) -> ChatPrompt.suggest(p, "Save your inventory as a kit", "/kit create ", "/kit create <name>"));
        b.footer((p, t) -> MainMenu.open(p), "<gray>Click a kit to equip it.");
        b.open(player);
    }

    public static void openKitPreview(Player player, Kit kit) {
        MenuBuilder b = new MenuBuilder("<gray>Preview: <yellow>" + MenuBuilder.esc(kit.getDisplayName()), 6);
        if (kit.getContents() != null) {
            ItemStack[] contents = kit.getContents();
            for (int i = 0; i < Math.min(contents.length, 36); i++) {
                if (contents[i] != null && contents[i].getType() != Material.AIR) b.set(i, contents[i], null);
            }
        }
        if (kit.getArmor() != null) {
            ItemStack[] armor = kit.getArmor();
            int[] slots = MenuLayout.centeredSlots(4, Math.min(armor.length, 4));
            for (int i = 0; i < slots.length; i++) {
                if (armor[i] != null && armor[i].getType() != Material.AIR) b.set(slots[i], armor[i], null);
            }
        }
        b.footer((p, t) -> reopenCombinedKitMenu(p, 0), "<gray>Preview of the kit contents.",
                "<gray>Hotbar and inventory on top, armor below.");
        b.open(player);
    }

    private static void equip(Player player, AdminKit kit) {
        if (!kit.isPublic() && !player.hasPermission("omniarena.admin")) return;
        if (!KitManager.getInstance().tryEquip(player, kit)) return;
        player.closeInventory();
        MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-equipped").replace("{kit}", kit.getName()));
    }

    private static void equipPersonal(Player player, PlayerKit kit) {
        // Re-resolve: the kit may have been deleted or renamed since the menu opened
        var current = KitManager.getInstance().getPlayerKit(player.getUniqueId(), kit.getName());
        if (current.isEmpty()) return;
        if (!KitManager.getInstance().tryEquip(player, current.get())) return;
        player.closeInventory();
        MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-equipped").replace("{kit}", current.get().getName()));
    }

    private static ItemStack buildKitItem(Kit kit, boolean preset) {
        ItemStack icon = kit.getIcon() != null ? kit.getIcon().clone() : new ItemStack(Material.CHEST);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        com.omniarena.platform.Platform.get().setDisplayName(meta, ItemUtil.noItalic(ColorUtil.parse("<yellow><bold>" + esc(kit.getDisplayName()) + "</bold></yellow>")));

        List<Component> lore = new ArrayList<>();
        if (preset) lore.add(ColorUtil.parse("<aqua><bold>[PRESET KIT]</bold></aqua>"));
        if (kit.getDescription() != null && !kit.getDescription().isBlank()) {
            lore.add(ColorUtil.parse("<gray>" + esc(kit.getDescription())));
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
            lore.add(ColorUtil.parse("<dark_red>Requires: <gray>" + esc(kit.getRequiredPermission())));
        }
        lore.add(Component.empty());
        lore.add(ColorUtil.parse("<green>Click to equip"));

        com.omniarena.platform.Platform.get().setLore(meta, lore.stream().map(ItemUtil::noItalic).toList());
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        icon.setItemMeta(meta);
        return icon;
    }

    private static String esc(String s) {
        return MenuBuilder.esc(s);
    }

    /** Closes every open OmniArena menu (used on plugin disable). */
    public static void closeAll() {
        MenuListener.closeAll();
    }
}
