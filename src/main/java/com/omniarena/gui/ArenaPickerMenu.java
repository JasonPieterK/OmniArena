package com.omniarena.gui;

import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.queue.QueueManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Lists arenas so the player can join (or spectate) with one click. */
public final class ArenaPickerMenu {

    public enum Mode { JOIN, SPECTATE }

    private static final int PER_PAGE = 45; // five content rows, footer on the sixth

    private ArenaPickerMenu() {}

    public static void open(Player player, Mode mode) {
        open(player, mode, 0);
    }

    private static void open(Player player, Mode mode, int page) {
        List<Arena> arenas = new ArrayList<>();
        for (Arena a : ArenaManager.getInstance().getAll()) {
            if (!a.isEnabled()) continue;
            if (mode == Mode.SPECTATE && !a.isSpectatorAllowed()) continue;
            arenas.add(a);
        }
        arenas.sort(Comparator.comparing(a -> a.getName().toLowerCase()));

        int pages = Math.max(1, (arenas.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        List<Arena> slice = arenas.subList(Math.min(pg * PER_PAGE, arenas.size()), Math.min((pg + 1) * PER_PAGE, arenas.size()));

        int contentRows = Math.max(1, MenuLayout.rowsFor(slice.size()));
        int rows = Math.min(6, contentRows + 1);
        String title = (mode == Mode.JOIN ? "<dark_gray>Join an arena" : "<dark_gray>Spectate an arena")
                + (pages > 1 ? " <gray>(" + (pg + 1) + "/" + pages + ")" : "");
        MenuBuilder b = new MenuBuilder(title, rows);

        if (slice.isEmpty()) {
            b.set(MenuLayout.centeredSlots(0, 1)[0], MenuBuilder.item(Material.COBWEB, "<gray><bold>No arenas available",
                    "<gray>" + (mode == Mode.JOIN ? "No arena is enabled right now." : "No arena allows spectators right now."),
                    "<gray>Ask an admin to set one up."), null);
        } else {
            List<MenuBuilder.Button> buttons = new ArrayList<>();
            for (Arena a : slice) buttons.add(MenuBuilder.button(arenaItem(a, mode), (p, t) -> {
                p.closeInventory();
                if (mode == Mode.JOIN) tryJoin(p, a); else trySpectate(p, a);
            }));
            b.flow(buttons);
        }
        b.footer((p, t) -> MainMenu.open(p),
                "<gray>Pick an arena to " + (mode == Mode.JOIN ? "join" : "spectate") + ".",
                "<gray>Item shows the arena state and player count.");
        if (pg > 0) b.set(MenuLayout.footerPrev(rows), MenuBuilder.item(Material.SPECTRAL_ARROW, "<yellow>Previous page"),
                (p, t) -> open(p, mode, pg - 1));
        if (pg < pages - 1) b.set(MenuLayout.footerNext(rows), MenuBuilder.item(Material.SPECTRAL_ARROW, "<yellow>Next page"),
                (p, t) -> open(p, mode, pg + 1));
        b.open(player);
    }

    private static org.bukkit.inventory.ItemStack arenaItem(Arena a, Mode mode) {
        int players = MatchManager.getInstance().getMatch(a).map(Match::getPlayerCount).orElse(0);
        Material icon = switch (a.getState()) {
            case WAITING -> Material.IRON_SWORD;
            case STARTING -> Material.CLOCK;
            case RUNNING -> Material.TNT;
            default -> Material.ANVIL;
        };
        String state = a.getState().name().toLowerCase();
        return MenuBuilder.item(icon, "<green><bold>" + MenuBuilder.esc(a.getName()),
                "<gray>Mode: <white>" + a.getGameMode().getDisplayName(),
                "<gray>State: <white>" + state,
                "<gray>Players: <white>" + players + (a.getMaxPlayers() > 0 ? "/" + a.getMaxPlayers() : ""),
                "",
                "<yellow>Click <gray>to " + (mode == Mode.JOIN ? "join" : "spectate"));
    }

    /** The checks of the old /oa join command, with chat feedback. */
    static void tryJoin(Player player, Arena arena) {
        MatchManager mm = MatchManager.getInstance();
        if (mm.isInArena(player)) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("already-in-arena"));
            return;
        }
        if (!arena.isEnabled()) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("arena-disabled-join").replace("{arena}", arena.getName()));
            return;
        }
        if (QueueManager.getInstance().isQueued(player)) {
            MessageUtil.send(player, "<red>You are in a queue, so you can't join an arena directly. <gray>Leave the queue first (main menu).");
            return;
        }
        boolean bypass = PermissionChecker.canBypassJoin(player);
        if (!bypass && !arena.isWhitelisted(player.getUniqueId())) {
            MessageUtil.send(player, "<red>This arena is whitelist-only and you are not on its whitelist. <gray>Ask an admin to add you, or pick another arena.");
            return;
        }
        if (!arena.isJoinable()) {
            MessageUtil.send(player, "<red>Arena <yellow>" + arena.getName() + "</yellow> is not joinable right now (state: "
                    + arena.getState().name().toLowerCase() + "). <gray>Wait for it to reopen, pick another arena, or spectate it.");
            return;
        }
        int current = mm.getMatch(arena).map(Match::getPlayerCount).orElse(0);
        if (!bypass && arena.getMaxPlayers() > 0 && current >= arena.getMaxPlayers()) {
            MessageUtil.send(player, "<red>Arena <yellow>" + arena.getName() + "</yellow> is full. <gray>Try another arena or spectate it.");
            return;
        }
        mm.joinArena(player, arena);
    }

    /** The checks of the old /oa spectate command, with chat feedback. */
    static void trySpectate(Player player, Arena arena) {
        if (!PermissionChecker.canSpectate(player)) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("no-permission"));
            return;
        }
        if (!MatchManager.getInstance().joinSpectator(player, arena)) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("spectate-not-allowed").replace("{arena}", arena.getName()));
        }
    }
}
