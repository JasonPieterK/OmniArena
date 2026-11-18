package com.omniarena.commands.player;

import java.util.Locale;
import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.betting.BettingManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class BetCommand extends SubCommand {

    private final OmniArena plugin;

    public BetCommand(OmniArena plugin) {
        super("omniarena.bet", "/bet <place|cancel|info> [arena] [player] [amount]", "Bet on arena match outcomes");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, com.omniarena.config.ConfigManager.getInstance().getMessage("player-only"));
            return;
        }

        BettingManager bm = plugin.getBettingManager();

        if (!bm.isEnabled()) {
            MessageUtil.send(player, com.omniarena.config.ConfigManager.getInstance().getMessage("economy-missing"));
            return;
        }

        if (args.length == 0) {
            sendUsage(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "place" -> handlePlace(player, bm, args);
            case "cancel" -> bm.cancelBet(player);
            case "info" -> handleInfo(player, bm, args);
            default -> sendUsage(player);
        }
    }

    private void handlePlace(Player player, BettingManager bm, String[] args) {
        if (args.length < 4) {
            MessageUtil.send(player, com.omniarena.config.ConfigManager.getInstance().getMessage("usage-line").replace("{usage}", "/bet place <arena> <player> <amount>  (e.g. /bet place duel1 Steve 100)"));
            return;
        }
        String arenaName = args[1];
        Arena arena = plugin.getArenaManager().get(arenaName).orElse(null);
        if (arena == null) {
            MessageUtil.send(player, com.omniarena.config.ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", arenaName));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            MessageUtil.send(player, com.omniarena.config.ConfigManager.getInstance().getMessage("player-not-found").replace("{player}", args[2]));
            return;
        }

        if (!plugin.getMatchManager().isInArena(target)) {
            MessageUtil.send(player, com.omniarena.config.ConfigManager.getInstance().getMessage("bet-player-not-in-arena").replace("{player}", target.getName()).replace("{arena}", arena.getName()));
            return;
        }
        // Target must be a participant (not spectator) of the arena being bet on
        boolean inThatArena = plugin.getMatchManager().getArenaName(target)
                .map(n -> n.equalsIgnoreCase(arena.getName())).orElse(false);
        if (!inThatArena || plugin.getMatchManager().isSpectating(target)) {
            MessageUtil.send(player, com.omniarena.config.ConfigManager.getInstance().getMessage("bet-player-not-in-arena").replace("{player}", target.getName()).replace("{arena}", arena.getName()));
            return;
        }

        // No betting on a match that has already ended (result settled / arena resetting)
        var liveMatch = plugin.getMatchManager().getMatch(arena);
        if (liveMatch.isEmpty() || liveMatch.get().getState() == com.omniarena.match.MatchState.ENDING
                || liveMatch.get().getState() == com.omniarena.match.MatchState.RESETTING) {
            MessageUtil.send(player, "<red>The match in <yellow>" + arena.getName() + "</yellow> is not open for betting (it is not running, or already ending/resetting). <gray>Check <white>/oa info " + arena.getName() + "</white> and bet on a live match.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
            if (!Double.isFinite(amount)) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            MessageUtil.send(player, com.omniarena.config.ConfigManager.getInstance().getMessage("invalid-number").replace("{input}", args[3]));
            return;
        }

        bm.placeBet(player, arena.getName(), target.getUniqueId(), amount);
    }

    private void handleInfo(Player player, BettingManager bm, String[] args) {
        BettingManager.Bet myBet = bm.getPlayerBet(player);
        if (myBet != null) {
            String winnerName = Bukkit.getOfflinePlayer(myBet.getPredictedWinner()).getName();
            MessageUtil.send(player, "<gold>=== Your Active Bet ===");
            MessageUtil.send(player, "<yellow>Arena: <white>" + myBet.getArenaName());
            MessageUtil.send(player, "<yellow>Bet on: <white>" + winnerName);
            MessageUtil.send(player, "<yellow>Amount: <white>" + bm.getEconomy().format(myBet.getAmount()));
        } else {
            MessageUtil.send(player, "<gray>You have no active bets.");
        }

        if (args.length >= 2) {
            String arenaName = args[1];
            BettingManager.BettingStats stats = bm.getMatchStats(arenaName);
            MessageUtil.send(player, "<gold>=== Bet Pool: " + arenaName + " ===");
            MessageUtil.send(player, "<yellow>Total bets: <white>" + stats.getTotalBets());
            MessageUtil.send(player, "<yellow>Pool size: <white>" + bm.getEconomy().format(stats.getTotalPool()));
        }
    }

    private void sendUsage(Player player) {
        MessageUtil.send(player, "<gold>=== /bet ===");
        MessageUtil.send(player, "<yellow>/bet place <arena> <player> <amount></yellow> <gray>- place a bet");
        MessageUtil.send(player, "<yellow>/bet cancel</yellow> <gray>- cancel your bet (refunded)");
        MessageUtil.send(player, "<yellow>/bet info [arena]</yellow> <gray>- view your bet and pool info");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.filter(List.of("place", "cancel", "info"), cur);
        String a = args[0].toLowerCase(java.util.Locale.ROOT);
        if (args.length == 2 && List.of("place", "info").contains(a)) return TabUtil.arenas(cur);
        if (args.length == 3 && a.equals("place")) return TabUtil.players(sender, cur);
        if (args.length == 4 && a.equals("place")) return TabUtil.hint("<amount>", cur);
        return List.of();
    }
}
