package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.rating.EloManager;
import com.omniarena.rating.EloRating;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /oa elo [player] shows a ranked rating (read-only: never creates a rating entry);
 * /oa elo top [n] shows the leaderboard.
 */
public class EloCommand extends SubCommand {

    private static final int DEFAULT_TOP = 10;
    private static final int MAX_TOP = 25;

    public EloCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_USE, "/oa elo [player] | /oa elo top [n]", "View ranked ELO ratings");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        EloManager em = EloManager.getInstance();
        if (em == null) {
            MessageUtil.send(sender, "<red>Ratings are not available right now. <gray>An admin may need to run <white>/oa start</white>.");
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            int n = DEFAULT_TOP;
            if (args.length >= 2) {
                try { n = Integer.parseInt(args[1]); }
                catch (NumberFormatException e) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-number").replace("{input}", args[1]));
                    return;
                }
                if (n < 1 || n > MAX_TOP) {
                    MessageUtil.send(sender, "<red>Pick a number between <yellow>1</yellow> and <yellow>" + MAX_TOP + "</yellow>.");
                    return;
                }
            }
            showTop(sender, em, n);
            return;
        }

        OfflinePlayer target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                    if (op.getName() != null && op.getName().equalsIgnoreCase(args[0])) { target = op; break; }
                }
            }
            if (target == null) {
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-not-found").replace("{player}", args[0]));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }
        showPlayer(sender, em, target);
    }

    private void showPlayer(CommandSender sender, EloManager em, OfflinePlayer target) {
        String name = target.getName() != null ? target.getName() : "Unknown";
        EloRating r = em.peekRating(target.getUniqueId());   // read-only: no entry is created
        int rank = em.getPlayerRank(target.getUniqueId());   // -1 when unranked
        MessageUtil.send(sender, "<gold>=== ELO: <yellow>" + name + "</yellow> ===");
        if (rank < 0 || r.getTotalMatches() == 0) {
            MessageUtil.send(sender, "<gray>Rating: <white>" + r.getRating() + " <dark_gray>(unranked, no ranked matches yet)");
            MessageUtil.send(sender, "<gray>Ranked matches use <white>/oa queue join</white>.");
            return;
        }
        MessageUtil.send(sender, "<gray>Rating: <white>" + r.getRating() + " <dark_gray>(rank #" + rank + " of " + em.getTotalPlayers() + ")");
        MessageUtil.send(sender, "<gray>Highest: <white>" + r.getHighestRating());
        MessageUtil.send(sender, "<gray>Wins: <green>" + r.getWins() + "<gray>  Losses: <red>" + r.getLosses()
                + "<gray>  Draws: <yellow>" + r.getDraws());
        MessageUtil.send(sender, "<gray>Win rate: <white>" + String.format(Locale.ROOT, "%.1f%%", r.getWinRate()));
    }

    private void showTop(CommandSender sender, EloManager em, int n) {
        List<EloManager.LeaderboardEntry> top = em.getLeaderboard(n);
        MessageUtil.send(sender, "<gold>=== Top " + n + " ELO ===");
        if (top.isEmpty()) {
            MessageUtil.send(sender, "<gray>No ranked matches have been played yet.");
            return;
        }
        for (EloManager.LeaderboardEntry e : top) {
            // Names come from stored data: send as plain text so they can never inject tags
            MessageUtil.send(sender, net.kyori.adventure.text.Component.text("#" + e.getRank() + " ", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .append(net.kyori.adventure.text.Component.text(e.getPlayerName(), net.kyori.adventure.text.format.NamedTextColor.WHITE))
                    .append(net.kyori.adventure.text.Component.text(" - " + e.getElo() + " (" + e.getWins() + "W/" + e.getLosses() + "L)", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) {
            List<String> opts = new java.util.ArrayList<>(TabUtil.players(sender, ""));
            opts.add("top");
            return TabUtil.filter(opts, cur);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) return TabUtil.filter(List.of("5", "10", "25"), cur);
        return List.of();
    }
}
