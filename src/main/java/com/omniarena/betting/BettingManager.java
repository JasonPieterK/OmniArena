package com.omniarena.betting;

import com.omniarena.OmniArena;
import com.omniarena.match.Match;
import com.omniarena.util.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Betting system with Vault economy integration
 * Allows players to bet on match outcomes
 */
public class BettingManager {

    private static BettingManager instance;

    private final OmniArena plugin;
    private Economy economy;
    private final Map<UUID, Bet> activeBets = new ConcurrentHashMap<>();
    private final Map<String, List<Bet>> matchBets = new ConcurrentHashMap<>(); // arenaName -> List<Bet>
    private boolean enabled = false;

    public BettingManager(OmniArena plugin) {
        this.plugin = plugin;
        instance = this;
        setupEconomy();
    }

    public static BettingManager getInstance() { return instance; }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found - betting system disabled");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager()
                .getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No economy plugin found - betting system disabled");
            return false;
        }

        economy = rsp.getProvider();
        enabled = true;
        plugin.getLogger().info("Economy found: " + economy.getName());
        return true;
    }

    public boolean isEnabled() { return enabled; }
    public Economy getEconomy() { return economy; }

    /**
     * Place a bet on a match
     */
    public boolean placeBet(Player player, String arenaName, UUID predictedWinner, double amount) {
        if (!enabled) {
            MessageUtil.send(player, "<red>Betting is not available (no economy plugin).");
            return false;
        }

        if (amount <= 0) {
            MessageUtil.send(player, "<red>Bet amount must be positive.");
            return false;
        }

        double balance = economy.getBalance(player);
        if (balance < amount) {
            MessageUtil.send(player, "<red>You don't have enough money. Balance: <yellow>" + 
                    economy.format(balance));
            return false;
        }

        // Check minimum and maximum bets
        double minBet = plugin.getConfigManager().getConfig().getDouble("betting.min-bet", 100);
        double maxBet = plugin.getConfigManager().getConfig().getDouble("betting.max-bet", 10000);

        if (amount < minBet) {
            MessageUtil.send(player, "<red>Minimum bet is <yellow>" + economy.format(minBet));
            return false;
        }

        if (amount > maxBet) {
            MessageUtil.send(player, "<red>Maximum bet is <yellow>" + economy.format(maxBet));
            return false;
        }

        // Check if player already has a bet on this match
        if (hasBetOnMatch(player, arenaName)) {
            MessageUtil.send(player, "<red>You already have a bet on this match.");
            return false;
        }

        // Deduct money
        economy.withdrawPlayer(player, amount);

        // Create bet
        Bet bet = new Bet(player.getUniqueId(), player.getName(), arenaName, predictedWinner, amount);
        activeBets.put(player.getUniqueId(), bet);
        
        matchBets.computeIfAbsent(arenaName, k -> new ArrayList<>()).add(bet);

        String winnerName = Bukkit.getOfflinePlayer(predictedWinner).getName();
        MessageUtil.send(player, "<green>Bet placed! <yellow>" + economy.format(amount) + 
                "</yellow> on <yellow>" + winnerName + "</yellow> in arena <yellow>" + arenaName + "</yellow>.");

        return true;
    }

    /**
     * Cancel a bet (before match starts)
     */
    public boolean cancelBet(Player player) {
        Bet bet = activeBets.get(player.getUniqueId());
        if (bet == null) {
            MessageUtil.send(player, "<red>You have no active bets.");
            return false;
        }

        // Refund money
        economy.depositPlayer(player, bet.getAmount());

        // Remove bet
        activeBets.remove(player.getUniqueId());
        List<Bet> bets = matchBets.get(bet.getArenaName());
        if (bets != null) {
            bets.remove(bet);
            if (bets.isEmpty()) {
                matchBets.remove(bet.getArenaName());
            }
        }

        MessageUtil.send(player, "<green>Bet cancelled. <yellow>" + economy.format(bet.getAmount()) + 
                "</yellow> refunded.");
        return true;
    }

    /**
     * Check if player has a bet on a specific match
     */
    public boolean hasBetOnMatch(Player player, String arenaName) {
        Bet bet = activeBets.get(player.getUniqueId());
        return bet != null && bet.getArenaName().equals(arenaName);
    }

    /**
     * Get player's bet
     */
    public Bet getPlayerBet(Player player) {
        return activeBets.get(player.getUniqueId());
    }

    /**
     * Process bets when a match ends
     */
    public void processMatchResults(String arenaName, UUID winnerId, UUID loserId) {
        List<Bet> bets = matchBets.remove(arenaName);
        if (bets == null || bets.isEmpty()) return;

        double totalPool = 0;
        double winnerPool = 0;

        // Calculate pools
        for (Bet bet : bets) {
            totalPool += bet.getAmount();
            if (bet.getPredictedWinner().equals(winnerId)) {
                winnerPool += bet.getAmount();
            }
        }

        // Payout winners
        for (Bet bet : bets) {
            Player player = Bukkit.getPlayer(bet.getPlayerUuid());
            if (player == null || !player.isOnline()) {
                // Offline player - refund their bet
                economy.depositPlayer(bet.getPlayerUuid(), bet.getAmount());
                continue;
            }

            if (bet.getPredictedWinner().equals(winnerId)) {
                // Winner - calculate payout based on pool share
                double share = bet.getAmount() / winnerPool;
                double winnings = totalPool * share;
                double profit = winnings - bet.getAmount();

                economy.depositPlayer(player, winnings);
                MessageUtil.send(player, "<green>You won your bet! <yellow>" + economy.format(winnings) + 
                        "</yellow> (profit: <yellow>" + economy.format(profit) + "</yellow>)");
            } else {
                // Loser - already deducted, no refund
                MessageUtil.send(player, "<red>You lost your bet of <yellow>" + economy.format(bet.getAmount()) + "</yellow>.");
            }

            activeBets.remove(bet.getPlayerUuid());
        }

        plugin.getLogger().info("Processed " + bets.size() + " bets for arena " + arenaName + 
                ". Total pool: " + economy.format(totalPool));
    }

    /**
     * Get betting stats for a match
     */
    public BettingStats getMatchStats(String arenaName) {
        List<Bet> bets = matchBets.getOrDefault(arenaName, Collections.emptyList());
        double totalPool = bets.stream().mapToDouble(Bet::getAmount).sum();
        
        Map<UUID, Double> playerBets = new HashMap<>();
        for (Bet bet : bets) {
            playerBets.merge(bet.getPredictedWinner(), bet.getAmount(), Double::sum);
        }

        return new BettingStats(bets.size(), totalPool, playerBets);
    }

    /**
     * Get all active bets
     */
    public Collection<Bet> getAllBets() {
        return activeBets.values();
    }

    /**
     * Clear all bets (for admin use)
     */
    public void clearAllBets() {
        for (Bet bet : activeBets.values()) {
            economy.depositPlayer(bet.getPlayerUuid(), bet.getAmount());
        }
        activeBets.clear();
        matchBets.clear();
    }

    // Inner classes

    public static class Bet {
        private final UUID playerUuid;
        private final String playerName;
        private final String arenaName;
        private final UUID predictedWinner;
        private final double amount;
        private final long betTime;

        public Bet(UUID playerUuid, String playerName, String arenaName, UUID predictedWinner, double amount) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.arenaName = arenaName;
            this.predictedWinner = predictedWinner;
            this.amount = amount;
            this.betTime = System.currentTimeMillis();
        }

        public UUID getPlayerUuid() { return playerUuid; }
        public String getPlayerName() { return playerName; }
        public String getArenaName() { return arenaName; }
        public UUID getPredictedWinner() { return predictedWinner; }
        public double getAmount() { return amount; }
        public long getBetTime() { return betTime; }
    }

    public static class BettingStats {
        private final int totalBets;
        private final double totalPool;
        private final Map<UUID, Double> playerBets;

        public BettingStats(int totalBets, double totalPool, Map<UUID, Double> playerBets) {
            this.totalBets = totalBets;
            this.totalPool = totalPool;
            this.playerBets = playerBets;
        }

        public int getTotalBets() { return totalBets; }
        public double getTotalPool() { return totalPool; }
        public Map<UUID, Double> getPlayerBets() { return playerBets; }
    }
}
