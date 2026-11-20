package com.omniarena.gui;

import com.omniarena.OmniArena;
import com.omniarena.match.MatchManager;
import com.omniarena.party.PartyManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.queue.QueueManager;
import com.omniarena.rating.EloManager;
import com.omniarena.stats.PlayerStats;
import com.omniarena.stats.StatsManager;
import com.omniarena.tournament.Tournament;
import com.omniarena.tournament.TournamentManager;
import com.omniarena.tournament.TournamentState;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The player hub opened by {@code /oa} and {@code /oa menu}. */
public final class MainMenu {

    private static final String QUEUE = "ranked";

    private MainMenu() {}

    public static void open(Player player) {
        OmniArena plugin = OmniArena.getInstance();
        if (plugin.isStopped()) {
            MessageUtil.send(player, com.omniarena.config.ConfigManager.getInstance().getMessage("plugin-stopped"));
            return;
        }
        boolean admin = PermissionChecker.hasAdmin(player, PermissionChecker.ADMIN_ALL);
        MatchManager mm = MatchManager.getInstance();
        QueueManager qm = QueueManager.getInstance();
        boolean inArena = mm.isInArena(player);
        boolean queued = qm != null && qm.isQueued(player);

        MenuBuilder b = new MenuBuilder("<dark_gray>OmniArena", 5);

        // Row 1: play
        List<MenuBuilder.Button> play = new ArrayList<>();
        play.add(MenuBuilder.button(MenuBuilder.item(Material.IRON_SWORD, "<green><bold>Join an arena",
                "<gray>Pick an arena and fight.", "", "<yellow>Click <gray>to choose an arena"),
                (p, t) -> ArenaPickerMenu.open(p, ArenaPickerMenu.Mode.JOIN)));
        play.add(MenuBuilder.button(MenuBuilder.item(Material.ENDER_EYE, "<aqua><bold>Spectate",
                "<gray>Watch a match without playing.", "", "<yellow>Click <gray>to choose an arena"),
                (p, t) -> ArenaPickerMenu.open(p, ArenaPickerMenu.Mode.SPECTATE)));
        play.add(MenuBuilder.button(queued
                ? MenuBuilder.item(Material.REDSTONE_TORCH, "<red><bold>Leave ranked queue",
                        "<gray>You are waiting for a ranked match.", "", "<yellow>Click <gray>to leave the queue")
                : MenuBuilder.item(Material.DIAMOND_SWORD, "<gold><bold>Ranked queue",
                        "<gray>Get matched against a player of", "<gray>similar ELO.", "", "<yellow>Click <gray>to join the queue"),
                (p, t) -> toggleQueue(p)));
        if (inArena) {
            play.add(MenuBuilder.button(MenuBuilder.item(Material.OAK_DOOR, "<red><bold>Leave arena",
                    "<gray>Leave your current match or spectating.", "", "<yellow>Click <gray>to leave"),
                    (p, t) -> { p.closeInventory(); MatchManager.getInstance().leaveArena(p); }));
        }
        b.row(1, play);

        // Row 2: profile
        List<MenuBuilder.Button> profile = new ArrayList<>();
        profile.add(MenuBuilder.button(MenuBuilder.item(Material.CHEST, "<yellow><bold>My kits",
                "<gray>Your personal kits and the preset kits.", "", "<yellow>Click <gray>to open"),
                (p, t) -> com.omniarena.kit.KitGUI.reopenCombinedKitMenu(p, 0)));
        profile.add(MenuBuilder.button(statsItem(player), null));
        profile.add(MenuBuilder.button(partyItem(player), (p, t) -> {
            p.closeInventory();
            MessageUtil.send(p, "<gray>Party commands: <white>/party</white> (create, invite, accept, leave ...)");
        }));
        profile.add(MenuBuilder.button(MenuBuilder.item(Material.TOTEM_OF_UNDYING, "<light_purple><bold>Tournaments",
                "<gray>See open and running tournaments.", "", "<yellow>Click <gray>to open"),
                (p, t) -> openTournaments(p)));
        b.row(2, profile);

        // Row 3: admin
        if (admin) {
            b.row(3, List.of(MenuBuilder.button(MenuBuilder.item(Material.COMMAND_BLOCK, "<red><bold>Admin",
                    "<gray>Arenas, kits, settings and reload.", "", "<yellow>Click <gray>to open the admin hub"),
                    (p, t) -> AdminHubMenu.open(p))));
        }
        b.footer(null, "<gray>OmniArena main menu.",
                "<gray>Same actions are available as /oa commands.");
        b.open(player);
    }

    private static void toggleQueue(Player player) {
        QueueManager qm = QueueManager.getInstance();
        OmniArena plugin = OmniArena.getInstance();
        player.closeInventory();
        if (qm == null || plugin.isStopped()) return;
        if (qm.isQueued(player)) {
            qm.leaveQueue(player);
            return;
        }
        if (!player.hasPermission(PermissionChecker.PLAYER_QUEUE)) {
            MessageUtil.send(player, com.omniarena.config.ConfigManager.getInstance().getMessage("no-permission"));
            return;
        }
        qm.joinQueue(player, QUEUE);
    }

    private static ItemStack statsItem(Player player) {
        StatsManager sm = StatsManager.getInstance();
        EloManager em = EloManager.getInstance();
        UUID id = player.getUniqueId();
        List<String> lore = new ArrayList<>();
        if (sm != null) {
            PlayerStats s = sm.peekStats(id);
            lore.add("<gray>Kills: <white>" + s.getKills() + "  <gray>Deaths: <white>" + s.getDeaths());
            lore.add("<gray>K/D: <white>" + String.format(Locale.ROOT, "%.2f", s.getKDRatio()));
            lore.add("<gray>Wins: <white>" + s.getWins() + "  <gray>Losses: <white>" + s.getLosses());
            lore.add("<gray>Win rate: <white>" + String.format(Locale.ROOT, "%.0f%%", s.getWinRate() * 100));
            lore.add("<gray>Time played: <white>" + s.getFormattedTimePlayed());
        }
        if (em != null) {
            int rank = em.getPlayerRank(id);
            lore.add("<gray>ELO: <gold>" + em.peekElo(id) + (rank > 0 ? " <gray>(rank #" + rank + ")" : " <gray>(unranked)"));
        }
        return MenuBuilder.item(Material.PLAYER_HEAD, "<gold><bold>My stats", lore.toArray(new String[0]));
    }

    private static ItemStack partyItem(Player player) {
        PartyManager pm = PartyManager.getInstance();
        List<String> lore = new ArrayList<>();
        PartyManager.Party party = pm == null ? null : pm.getParty(player);
        if (party == null) {
            lore.add("<gray>You are not in a party.");
            lore.add("<gray>Parties queue and fight together.");
        } else {
            lore.add("<gray>Leader: <white>" + MenuBuilder.esc(party.getLeaderName()));
            lore.add("<gray>Members (" + party.getSize() + "/" + PartyManager.maxSize() + "):");
            for (UUID m : party.getMembers()) {
                var op = Bukkit.getOfflinePlayer(m);
                lore.add("<dark_gray> - <white>" + MenuBuilder.esc(op.getName() == null ? "?" : op.getName()));
            }
        }
        lore.add("");
        lore.add("<yellow>Click <gray>for party commands");
        return MenuBuilder.item(Material.LEAD, "<green><bold>Party", lore.toArray(new String[0]));
    }

    private static void openTournaments(Player player) {
        TournamentManager tm = OmniArena.getInstance().getTournamentManager();
        List<Tournament> list = new ArrayList<>();
        if (tm != null) {
            for (Tournament t : tm.getAll()) {
                if (t.getState() == TournamentState.REGISTRATION || t.getState() == TournamentState.IN_PROGRESS) list.add(t);
            }
        }
        int rows = Math.min(6, MenuLayout.rowsFor(Math.max(1, Math.min(list.size(), 45))) + 1);
        MenuBuilder b = new MenuBuilder("<dark_gray>Tournaments", rows);
        if (list.isEmpty()) {
            b.set(MenuLayout.centeredSlots(0, 1)[0], MenuBuilder.item(Material.COBWEB, "<gray><bold>No tournaments",
                    "<gray>Nothing is open right now."), null);
        } else {
            List<MenuBuilder.Button> buttons = new ArrayList<>();
            for (Tournament t : list.subList(0, Math.min(list.size(), 45))) {
                boolean joined = t.hasPlayer(player.getUniqueId());
                boolean open = t.getState() == TournamentState.REGISTRATION;
                ItemStack item = MenuBuilder.item(open ? Material.NETHER_STAR : Material.GOLDEN_SWORD,
                        "<gold><bold>" + MenuBuilder.esc(t.getName()),
                        "<gray>Arena: <white>" + MenuBuilder.esc(t.getArenaName()),
                        "<gray>State: <white>" + t.getState().name().toLowerCase(Locale.ROOT),
                        "<gray>Players: <white>" + t.getParticipants().size() + "/" + t.getMaxPlayers(),
                        joined ? "<green>You are registered." : "",
                        "",
                        open && !joined ? "<yellow>Click <gray>to join" : "<yellow>Click <gray>for status");
                buttons.add(MenuBuilder.button(item, (p, c) -> {
                    p.closeInventory();
                    if (open && !t.hasPlayer(p.getUniqueId())) tm.joinTournament(p, t.getName());
                    else MessageUtil.send(p, tm.getStatusText(t));
                }));
            }
            b.flow(buttons);
        }
        b.footer((p, t) -> open(p), "<gray>Open and running tournaments.", "<gray>Click one to join or see its status.");
        b.open(player);
    }
}
