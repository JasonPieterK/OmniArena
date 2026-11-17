package com.omniarena.commands.player;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.stats.PlayerStats;
import com.omniarena.stats.StatsManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class StatsCommand extends SubCommand {

    public StatsCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_USE, "/oa stats [player]", "View arena stats");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        OfflinePlayer target;

        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("global")) {
                showGlobalLeaderboard(sender, args.length >= 2 ? args[1] : "kills");
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
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-not-found").replace("{player}", args[0]));
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
    }

    private void showGlobalLeaderboard(CommandSender sender, String type) {
        StatsManager statsManager = StatsManager.getInstance();
        List<PlayerStats> top;
        String label;

        switch (type.toLowerCase()) {
            case "wins" -> {
                top = statsManager.getTopWins(10);
                label = "Wins";
            }
            case "kd", "kdr" -> {
                top = statsManager.getTopKD(10);
                label = "K/D Ratio";
            }
            default -> {
                top = statsManager.getTopKills(10);
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
            opts.add("global");
            if (admin) opts.add("reset");
            return TabUtil.filter(opts, cur);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("global")) return TabUtil.filter(List.of("kills", "wins", "kd"), cur);
        if (args.length == 2 && args[0].equalsIgnoreCase("reset") && admin) return TabUtil.players(sender, cur);
        return List.of();
    }
}
