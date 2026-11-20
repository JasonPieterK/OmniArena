package com.omniarena.commands.player;

import java.util.Locale;
import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.commands.SmartErrors;
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

    private static final List<String> ACTIONS = List.of("create", "join", "leave", "start", "status", "list", "delete", "setprize");

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
            SmartErrors.wrongArgs(sender, "/tournament <create|join|leave|start|status|list|delete|setprize>", "/tournament list");
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
                if (args.length < 4) { SmartErrors.wrongArgs(sender, "/tournament create <name> <arena> <maxPlayers>", "/tournament create cup duel_1 8"); return; }
                if (SmartErrors.arena(sender, args[2], "/tournament create " + args[1] + " ").isEmpty()) return;
                int max;
                try { max = Integer.parseInt(args[3]); }
                catch (NumberFormatException e) { MessageUtil.send(sender, msg("invalid-number", "input", args[3])); return; }
                tm.createTournament((Player) sender, args[1], args[2], max);
            }
            case "join" -> {
                if (!isPlayer) { MessageUtil.send(sender, msg("tournament-players-only")); return; }
                if (args.length < 2) { SmartErrors.wrongArgs(sender, "/tournament join <name>", "/tournament join <name>"); return; }
                tm.joinTournament((Player) sender, args[1]);
            }
            case "leave" -> {
                if (!isPlayer) { MessageUtil.send(sender, msg("tournament-players-only")); return; }
                if (args.length < 2) { SmartErrors.wrongArgs(sender, "/tournament leave <name>", "/tournament leave <name>"); return; }
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
                if (args.length < 4) { SmartErrors.wrongArgs(sender, "/tournament setprize <name> <1st|2nd|3rd> <amount>", "/tournament setprize <name> 1st 500"); return; }
                Tournament t = tm.getTournament(args[1]);
                if (t == null) { tournamentNotFound(sender, tm, args[1], "setprize"); return; }
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
            default -> SmartErrors.unknownSubcommand(sender, "/tournament", args[0], ACTIONS);
        }
    }

    private Tournament need(CommandSender sender, TournamentManager tm, String[] args) {
        if (args.length < 2) { SmartErrors.wrongArgs(sender, "/tournament " + args[0] + " <name>", "/tournament " + args[0] + " <name>"); return null; }
        Tournament t = tm.getTournament(args[1]);
        if (t == null) tournamentNotFound(sender, tm, args[1], args[0]);
        return t;
    }

    private void tournamentNotFound(CommandSender sender, TournamentManager tm, String input, String action) {
        List<String> names = new java.util.ArrayList<>();
        for (Tournament x : tm.getAll()) names.add(x.getName());
        String hint = com.omniarena.util.Suggest.closest(input, names)
                .map(n -> " <gray>Did you mean " + com.omniarena.util.Suggest.click("/tournament " + action + " " + n) + "<gray>?").orElse("");
        MessageUtil.send(sender, msg("tournament-not-found", "name", com.omniarena.util.Suggest.escape(input)) + hint);
    }

    private boolean canManage(CommandSender sender, Tournament t) {
        return isAdmin(sender) || (sender instanceof Player p && p.getUniqueId().equals(t.getCreatorUuid()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        boolean admin = isAdmin(sender);
        if (args.length == 1) {
            List<String> opts = new java.util.ArrayList<>(List.of("join", "leave", "start", "status", "list"));
            if (admin || sender.hasPermission(PERM_CREATE)) opts.add("create");
            if (admin) opts.addAll(List.of("delete", "setprize"));
            return TabUtil.filter(opts, cur);
        }
        String a = args[0].toLowerCase(java.util.Locale.ROOT);
        if (args.length == 2) {
            if (a.equals("create")) return TabUtil.hint("<name>", cur);
            if (List.of("join", "leave", "start", "status", "info", "delete", "cancel", "setprize").contains(a)) return TabUtil.tournaments(cur);
        }
        if (args.length == 3) {
            if (a.equals("create")) return TabUtil.arenas(cur);
            if (a.equals("setprize")) return TabUtil.filter(List.of("1st", "2nd", "3rd"), cur);
        }
        if (args.length == 4) {
            if (a.equals("create")) return TabUtil.hint("<maxPlayers>", cur);
            if (a.equals("setprize")) return TabUtil.hint("<amount>", cur);
        }
        return List.of();
    }
}
