package com.omniarena.gui;

import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaSetupService;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Small admin hub reached from the main menu: arenas, admin kits, settings, reload. */
public final class AdminHubMenu {

    private static final int PER_PAGE = 36;

    private AdminHubMenu() {}

    static boolean allowed(Player player, boolean arenaScope) {
        boolean ok = arenaScope ? PermissionChecker.hasAdminArena(player) : PermissionChecker.hasAdmin(player, PermissionChecker.ADMIN_ALL);
        if (!ok) MessageUtil.send(player, ConfigManager.getInstance().getMessage("no-permission"));
        return ok;
    }

    public static void open(Player player) {
        if (!allowed(player, false)) return;
        MenuBuilder b = new MenuBuilder("<dark_gray>OmniArena <red>Admin", 4);
        List<MenuBuilder.Button> row = new ArrayList<>();
        row.add(MenuBuilder.button(MenuBuilder.item(Material.GRASS_BLOCK, "<green><bold>Arenas",
                "<gray>All arenas.", "<gray>Click an arena to edit it,", "<gray>shift-click to run its setup.", "",
                "<yellow>Click <gray>to open"), (p, t) -> openArenas(p, 0)));
        row.add(MenuBuilder.button(MenuBuilder.item(Material.ENCHANTED_BOOK, "<gold><bold>Admin kits",
                "<gray>Preset kits: equip, publish, create.", "", "<yellow>Click <gray>to open"),
                (p, t) -> AdminKitMenu.open(p)));
        row.add(MenuBuilder.button(MenuBuilder.item(Material.COMPARATOR, "<aqua><bold>Settings",
                "<gray>Global plugin settings.", "", "<yellow>Click <gray>to open"),
                (p, t) -> com.omniarena.settings.SettingsGUI.openCategories(p)));
        row.add(MenuBuilder.button(MenuBuilder.item(Material.REDSTONE, "<red><bold>Reload",
                "<gray>Reload config, messages, kits", "<gray>and arenas (when idle).", "", "<yellow>Click <gray>to reload"),
                (p, t) -> { p.closeInventory(); Bukkit.dispatchCommand(p, "oa reload"); }));
        row.add(MenuBuilder.button(MenuBuilder.item(Material.WRITABLE_BOOK, "<aqua><bold>Guides",
                "<gray>Admin guides: quick start, arena setup,", "<gray>regen, leaderboards, NPCs, doctor, config.", "", "<yellow>Click <gray>to open the guide index"),
                (p, t) -> { p.closeInventory(); com.omniarena.guide.GuideBooks.openIndex(p); }));
        b.row(1, row);
        b.footer((p, t) -> MainMenu.open(p), "<gray>Admin tools.", "<gray>Everything here also has a /oa command.");
        b.open(player);
    }

    public static void openArenas(Player player, int page) {
        if (!allowed(player, true)) return;
        List<Arena> arenas = new ArrayList<>(ArenaManager.getInstance().getAll());
        arenas.sort(Comparator.comparing(a -> a.getName().toLowerCase()));
        int pages = Math.max(1, (arenas.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        List<Arena> slice = arenas.subList(Math.min(pg * PER_PAGE, arenas.size()), Math.min((pg + 1) * PER_PAGE, arenas.size()));

        // Content rows for arenas + one row for the "create" button, then footer
        int rows = Math.min(6, MenuLayout.rowsFor(Math.max(1, slice.size())) + 2);
        MenuBuilder b = new MenuBuilder("<dark_gray>Arenas" + (pages > 1 ? " <gray>(" + (pg + 1) + "/" + pages + ")" : ""), rows);
        List<MenuBuilder.Button> buttons = new ArrayList<>();
        for (Arena a : slice) {
            boolean ready = ArenaSetupService.missingRequirements(a).isEmpty();
            Material icon = !a.isEnabled() ? Material.BARRIER : (ready ? Material.GRASS_BLOCK : Material.DIRT);
            var item = MenuBuilder.item(icon, (a.isEnabled() ? "<green><bold>" : "<red><bold>") + MenuBuilder.esc(a.getName()),
                    "<gray>State: <white>" + a.getState().name().toLowerCase(),
                    "<gray>Mode: <white>" + a.getGameMode().getDisplayName(),
                    ready ? "<green>Setup complete" : "<red>Setup incomplete",
                    "",
                    "<yellow>Click <gray>to edit settings",
                    "<yellow>Shift-click <gray>to run the setup wizard");
            buttons.add(MenuBuilder.button(item, (p, t) -> {
                if (t.isShiftClick()) ArenaSetupMenu.open(p, a); else ArenaEditMenu.open(p, a);
            }));
        }
        b.flow(buttons);
        int createRow = rows - 2;
        b.set(createRow * 9 + 4, MenuBuilder.item(Material.WRITABLE_BOOK, "<green><bold>Create arena",
                "<gray>Type a name in chat to create", "<gray>a new arena and open its setup.", "",
                "<yellow>Click <gray>to type a name"),
                (p, t) -> ChatPrompt.suggest(p, "Create an arena", "/oa setup ", "/oa setup <name>"));
        b.footer((p, t) -> open(p), "<gray>Click an arena to edit it.", "<gray>Shift-click runs the setup wizard.");
        if (pg > 0) b.set(MenuLayout.footerPrev(rows), MenuBuilder.item(Material.SPECTRAL_ARROW, "<yellow>Previous page"),
                (p, t) -> openArenas(p, pg - 1));
        if (pg < pages - 1) b.set(MenuLayout.footerNext(rows), MenuBuilder.item(Material.SPECTRAL_ARROW, "<yellow>Next page"),
                (p, t) -> openArenas(p, pg + 1));
        b.open(player);
    }
}
