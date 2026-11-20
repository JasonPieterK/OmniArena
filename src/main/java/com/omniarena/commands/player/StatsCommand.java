package com.omniarena.commands.player;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.rating.EloManager;
import com.omniarena.rating.EloRating;
import com.omniarena.stats.PlayerStats;
import com.omniarena.stats.StatsManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class StatsCommand extends SubCommand {

    private static final int MAX_TOP = 25;
    private static final List<String> TOP_TYPES = List.of("kills", "wins", "kd", "elo");

    public StatsCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_USE, "/oa stats [player] | /oa stats top [kills|wins|kd|elo] [n] | /oa stats reset <player>", "View stats, ELO and leaderboards");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        OfflinePlayer target;

        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("top")) {
                top(sender, args);
                return;
            }

            // Check admin reset
            if (args[0].equalsIgnoreCase("reset")) {
                if (!PermissionChecker.hasAdminStats(sender)) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("no-permission"));
                    return;
                }
                if (args.length < 2) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("usage-line").replace("{usage}", "/oa stats reset <player>"));
                    return;
                }
                OfflinePlayer resetTarget = Bukkit.getPlayerExact(args[1]);
                if (resetTarget == null) {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(args[1]);
                    if (off.hasPlayedBefore()) resetTarget = off;
                }
                if (resetTarget == null) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-not-found").replace("{player}", args[1]));
                    return;
                }
                StatsManager.getInstance().resetStats(resetTarget.getUniqueId());
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("stats-reset").replace("{player}", resetTarget.getName() != null ? resetTarget.getName() : args[1]));
                return;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(args[0]);
                if (!off.hasPlayedBefore()) {
                    notFound(sender, args[0]);
                    return;
                }
                target = off;
            }
        } else {
            if (!(sender instanceof Player player)) {
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
                return;
            }
            target = player;
        }

        PlayerStats stats = StatsManager.getInstance().peekStats(target.getUniqueId());
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("stats-header").replace("{player}", target.getName() != null ? target.getName() : "Unknown"));
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("stats-kills").replace("{kills}", String.valueOf(stats.getKills())));
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("stats-deaths").replace("{deaths}", String.valueOf(stats.getDeaths())));
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("stats-wins").replace("{wins}", String.valueOf(stats.getWins())));
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("stats-losses").replace("{losses}", String.valueOf(stats.getLosses())));
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("stats-kd").replace("{kd}", String.valueOf(stats.getKDRatio())));
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("stats-time-played").replace("{time}", stats.getFormattedTimePlayed()));
        showElo(sender, target);
    }

    private void notFound(CommandSender sender, String input) {
        List<String> online = new java.util.ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) online.add(p.getName());
        String hint = com.omniarena.util.Suggest.closest(input, online)
                .map(n -> " <gray>Did you mean " + com.omniarena.util.Suggest.click("/oa stats " + n) + "<gray>?").orElse("");
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-not-found")
                .replace("{player}", com.omniarena.util.Suggest.escape(input)) + hint);
    }

    /** Read-only ELO lines (never creates a rating entry). */
    private void showElo(CommandSender sender, OfflinePlayer target) {
        EloManager em = EloManager.getInstance();
        if (em == null) return;
        EloRating r = em.peekRating(target.getUniqueId());
        int rank = em.getPlayerRank(target.getUniqueId());
        if (rank < 0 || r.getTotalMatches() == 0) {
            MessageUtil.send(sender, " <gray>ELO: <white>" + r.getRating() + " <dark_gray>(unranked: play a ranked match with " + com.omniarena.util.Suggest.click("/oa queue") + "<dark_gray>)");
            return;
        }
        MessageUtil.send(sender, " <gray>ELO: <white>" + r.getRating() + " <dark_gray>(rank #" + rank + " of " + em.getTotalPlayers() + ", peak " + r.getHighestRating() + ")");
        MessageUtil.send(sender, " <gray>Ranked: <green>" + r.getWins() + "W <red>" + r.getLosses() + "L <yellow>" + r.getDraws() + "D <gray>- win rate <white>"
                + String.format(java.util.Locale.ROOT, "%.1f%%", r.getWinRate()));
    }

    private void top(CommandSender sender, String[] args) {
        String type = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "kills";
        int n = 10;
        // "/oa stats top 5" is accepted as shorthand for kills
        if (args.length >= 2 && args[1].matches("\\d{1,3}")) { n = Integer.parseInt(args[1]); type = "kills"; }
        else if (args.length >= 3) {
            try { n = Integer.parseInt(args[2]); }
            catch (NumberFormatException e) {
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-number").replace("{input}", com.omniarena.util.Suggest.escape(args[2])));
                return;
            }
        }
        if (n < 1 || n > MAX_TOP) {
            MessageUtil.send(sender, "<red>Pick a number between <yellow>1</yellow> and <yellow>" + MAX_TOP + "</yellow>.");
            return;
        }
        if (!TOP_TYPES.contains(type)) {
            com.omniarena.commands.SmartErrors.unknownSubcommand(sender, "/oa stats top", type, TOP_TYPES);
            return;
        }
        if (type.equals("elo")) { showEloTop(sender, n); return; }
        showGlobalLeaderboard(sender, type, n);
    }

    private void showEloTop(CommandSender sender, int n) {
        EloManager em = EloManager.getInstance();
        if (em == null) { MessageUtil.send(sender, "<red>Ratings are not available right now. <gray>An admin may need to run " + com.omniarena.util.Suggest.click("/oa plugin start") + "<gray>."); return; }
        List<EloManager.LeaderboardEntry> top = em.getLeaderboard(n);
        MessageUtil.send(sender, "<gold>=== Top " + n + " ELO ===");
        if (top.isEmpty()) { MessageUtil.send(sender, "<gray>No ranked matches have been played yet."); return; }
        for (EloManager.LeaderboardEntry e : top) {
            // Names come from stored data: plain text so they can never inject tags
            MessageUtil.send(sender, net.kyori.adventure.text.Component.text("#" + e.getRank() + " ", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .append(net.kyori.adventure.text.Component.text(e.getPlayerName(), net.kyori.adventure.text.format.NamedTextColor.WHITE))
                    .append(net.kyori.adventure.text.Component.text(" - " + e.getElo() + " (" + e.getWins() + "W/" + e.getLosses() + "L)", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        }
    }

    private void showGlobalLeaderboard(CommandSender sender, String type, int n) {
        StatsManager statsManager = StatsManager.getInstance();
        List<PlayerStats> top;
        String label;

        switch (type.toLowerCase()) {
            case "wins" -> {
                top = statsManager.getTopWins(n);
                label = "Wins";
            }
            case "kd", "kdr" -> {
                top = statsManager.getTopKD(n);
                label = "K/D Ratio";
            }
            default -> {
                top = statsManager.getTopKills(n);
                label = "Kills";
            }
        }

        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("leaderboard-header").replace("{type}", label));
        for (int i = 0; i < top.size(); i++) {
            PlayerStats entry = top.get(i);
            String value = switch (type.toLowerCase()) {
                case "wins" -> String.valueOf(entry.getWins());
                case "kd", "kdr" -> String.valueOf(entry.getKDRatio());
                default -> String.valueOf(entry.getKills());
            };
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("leaderboard-entry")
                    .replace("{rank}", String.valueOf(i + 1))
                    .replace("{player}", entry.getPlayerName())
                    .replace("{value}", value));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        boolean admin = PermissionChecker.hasAdminStats(sender);
        if (args.length == 1) {
            List<String> opts = new java.util.ArrayList<>(TabUtil.players(sender, ""));
            opts.add("top");
            if (admin) opts.add("reset");
            return TabUtil.filter(opts, cur);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) return TabUtil.filter(TOP_TYPES, cur);
        if (args.length == 3 && args[0].equalsIgnoreCase("top")) return TabUtil.filter(List.of("5", "10", "25"), cur);
        if (args.length == 2 && args[0].equalsIgnoreCase("reset") && admin) return TabUtil.players(sender, cur);
        return List.of();
    }
}
