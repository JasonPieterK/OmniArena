package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.tournament.Tournament;
import com.omniarena.tournament.TournamentManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TournamentCommand extends SubCommand {

    private static final String PERM_CREATE = "omniarena.tournament.create";
    private static final String PERM_ADMIN = "omniarena.tournament.admin";
    private static final String USAGE =
            "/oa tournament <create <name> <arena> <maxPlayers>|join|leave|start|status|list|delete|setprize <name> <1st|2nd|3rd> <amount>>";

    private final OmniArena plugin;

    public TournamentCommand(OmniArena plugin) {
        super(PERM_CREATE, USAGE, "Create and play bracket tournaments");
        this.plugin = plugin;
    }

    private String msg(String key, String... kv) {
        String m = ConfigManager.getInstance().getMessage(key);
        for (int i = 0; i + 1 < kv.length; i += 2) m = m.replace("{" + kv[i] + "}", kv[i + 1]);
        return m;
    }

    private boolean isAdmin(CommandSender s) {
        return PermissionChecker.hasAdmin(s, PERM_ADMIN);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        TournamentManager tm = plugin.getTournamentManager();
        if (args.length == 0) {
            MessageUtil.send(sender, msg("tournament-usage", "usage", USAGE));
            return;
        }
        String action = args[0].toLowerCase();
        boolean isPlayer = sender instanceof Player;

        switch (action) {
            case "list" -> {
                if (tm.getAll().isEmpty()) { MessageUtil.send(sender, msg("tournament-none")); return; }
                MessageUtil.send(sender, msg("tournament-list-header"));
                for (Tournament t : tm.getAll()) {
                    MessageUtil.send(sender, msg("tournament-list-entry", "name", t.getName(),
                            "state", t.getState().name(), "players", String.valueOf(t.getParticipants().size()),
                            "max", String.valueOf(t.getMaxPlayers()), "arena", t.getArenaName()));
                }
            }
            case "status", "info" -> {
                Tournament t = need(sender, tm, args);
                if (t != null) MessageUtil.send(sender, tm.getStatusText(t));
            }
            case "create" -> {
                if (!isPlayer) { MessageUtil.send(sender, msg("tournament-players-only")); return; }
                if (!sender.hasPermission(PERM_CREATE) && !isAdmin(sender)) {
                    MessageUtil.send(sender, msg("no-permission")); return;
                }
                if (args.length < 4) { MessageUtil.send(sender, msg("tournament-usage", "usage", "/oa tournament create <name> <arena> <maxPlayers>")); return; }
                int max;
                try { max = Integer.parseInt(args[3]); }
                catch (NumberFormatException e) { MessageUtil.send(sender, msg("invalid-number", "input", args[3])); return; }
                tm.createTournament((Player) sender, args[1], args[2], max);
            }
            case "join" -> {
                if (!isPlayer) { MessageUtil.send(sender, msg("tournament-players-only")); return; }
                if (args.length < 2) { MessageUtil.send(sender, msg("tournament-usage", "usage", "/oa tournament join <name>")); return; }
                tm.joinTournament((Player) sender, args[1]);
            }
            case "leave" -> {
                if (!isPlayer) { MessageUtil.send(sender, msg("tournament-players-only")); return; }
                if (args.length < 2) { MessageUtil.send(sender, msg("tournament-usage", "usage", "/oa tournament leave <name>")); return; }
                tm.leaveTournament((Player) sender, args[1]);
            }
            case "start" -> {
                Tournament t = need(sender, tm, args);
                if (t == null) return;
                if (!canManage(sender, t)) { MessageUtil.send(sender, msg("tournament-no-permission")); return; }
                tm.startTournament(t, sender instanceof Player p ? p : null);
            }
            case "delete", "cancel" -> {
                Tournament t = need(sender, tm, args);
                if (t == null) return;
                if (!isAdmin(sender)) { MessageUtil.send(sender, msg("no-permission")); return; }
                tm.deleteTournament(t.getName(), sender instanceof Player p ? p : null);
            }
            case "setprize" -> {
                if (!isAdmin(sender)) { MessageUtil.send(sender, msg("no-permission")); return; }
                if (args.length < 4) { MessageUtil.send(sender, msg("tournament-usage", "usage", "/oa tournament setprize <name> <1st|2nd|3rd> <amount>")); return; }
                Tournament t = tm.getTournament(args[1]);
                if (t == null) { MessageUtil.send(sender, msg("tournament-not-found", "name", args[1])); return; }
                double amount;
                try { amount = Double.parseDouble(args[3]); }
                catch (NumberFormatException e) { amount = -1; }
                if (!Double.isFinite(amount) || amount < 0) { MessageUtil.send(sender, msg("tournament-prize-invalid")); return; }
                switch (args[2].toLowerCase()) {
                    case "1st", "1", "first" -> t.setPrizeFirst(amount);
                    case "2nd", "2", "second" -> t.setPrizeSecond(amount);
                    case "3rd", "3", "third" -> t.setPrizeThird(amount);
                    default -> { MessageUtil.send(sender, msg("tournament-prize-invalid")); return; }
                }
                tm.saveAll();
                MessageUtil.send(sender, msg("tournament-prize-set", "place", args[2], "amount", String.valueOf(amount), "name", t.getName()));
            }
            default -> MessageUtil.send(sender, msg("tournament-usage", "usage", USAGE));
        }
    }

    private Tournament need(CommandSender sender, TournamentManager tm, String[] args) {
        if (args.length < 2) { MessageUtil.send(sender, msg("tournament-usage", "usage", "/oa tournament " + args[0] + " <name>")); return null; }
        Tournament t = tm.getTournament(args[1]);
        if (t == null) MessageUtil.send(sender, msg("tournament-not-found", "name", args[1]));
        return t;
    }

    private boolean canManage(CommandSender sender, Tournament t) {
        return isAdmin(sender) || (sender instanceof Player p && p.getUniqueId().equals(t.getCreatorUuid()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        if (args.length == 1)
            return List.of("create", "join", "leave", "start", "status", "list", "delete", "setprize").stream()
                    .filter(s -> s.startsWith(last)).toList();
        String a = args[0].toLowerCase();
        if (args.length == 2 && List.of("join", "leave", "start", "status", "info", "delete", "cancel", "setprize").contains(a))
            return plugin.getTournamentManager().getAll().stream().map(Tournament::getName)
                    .filter(n -> n.toLowerCase().startsWith(last)).toList();
        if (args.length == 3 && a.equals("create"))
            return plugin.getArenaManager().getAll().stream().map(Arena::getName)
                    .filter(n -> n.toLowerCase().startsWith(last)).toList();
        if (args.length == 3 && a.equals("setprize"))
            return List.of("1st", "2nd", "3rd").stream().filter(s -> s.startsWith(last)).toList();
        return List.of();
    }
}
