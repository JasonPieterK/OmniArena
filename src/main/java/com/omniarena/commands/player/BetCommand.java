package com.omniarena.commands.player;

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
        super("omniarena.bet", "/oa bet <place|cancel|info> [arena] [player] [amount]", "Bet on arena match outcomes");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "<red>Players only.");
            return;
        }

        BettingManager bm = plugin.getBettingManager();

        if (!bm.isEnabled()) {
            MessageUtil.send(player, "<red>Betting is not available (no economy plugin).");
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
            MessageUtil.send(player, "<red>Usage: /oa bet place <arena> <player> <amount>");
            return;
        }
        String arenaName = args[1];
        Arena arena = plugin.getArenaManager().get(arenaName).orElse(null);
        if (arena == null) {
            MessageUtil.send(player, "<red>Arena not found: <yellow>" + arenaName);
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            MessageUtil.send(player, "<red>Player not found or offline: <yellow>" + args[2]);
            return;
        }

        if (!plugin.getMatchManager().isInArena(target)) {
            MessageUtil.send(player, "<red>" + target.getName() + " is not in an arena.");
            return;
        }
        // Target must be a participant (not spectator) of the arena being bet on
        boolean inThatArena = plugin.getMatchManager().getArenaName(target)
                .map(n -> n.equalsIgnoreCase(arena.getName())).orElse(false);
        if (!inThatArena || plugin.getMatchManager().isSpectating(target)) {
            MessageUtil.send(player, "<red>" + target.getName() + " is not playing in arena <yellow>" + arena.getName() + "</yellow>.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
            if (!Double.isFinite(amount)) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            MessageUtil.send(player, "<red>Invalid amount: <yellow>" + args[3]);
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
        MessageUtil.send(player, "<gold>=== /oa bet ===");
        MessageUtil.send(player, "<yellow>/oa bet place <arena> <player> <amount></yellow> <gray>- place a bet");
        MessageUtil.send(player, "<yellow>/oa bet cancel</yellow> <gray>- cancel your bet (refunded)");
        MessageUtil.send(player, "<yellow>/oa bet info [arena]</yellow> <gray>- view your bet and pool info");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("place", "cancel", "info").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("place") || args[0].equalsIgnoreCase("info")))
            return plugin.getArenaManager().getAll().stream()
                    .map(Arena::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("place"))
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase())).toList();
        return List.of();
    }
}
