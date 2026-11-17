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
    private final Map<String, Long> matchStartTimes = new ConcurrentHashMap<>();
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

    public void onMatchStart(String arenaName) {
        matchStartTimes.put(arenaName, System.currentTimeMillis());
    }

    public void onMatchCleanup(String arenaName) {
        matchStartTimes.remove(arenaName);
        // Any bets still pending here were never settled (match aborted) - refund them
        List<Bet> leftover = matchBets.remove(arenaName);
        if (leftover != null && economy != null) {
            for (Bet bet : leftover) {
                if (!activeBets.remove(bet.getPlayerUuid(), bet)) continue;
                economy.depositPlayer(Bukkit.getOfflinePlayer(bet.getPlayerUuid()), bet.getAmount());
                Player p = Bukkit.getPlayer(bet.getPlayerUuid());
                if (p != null) MessageUtil.send(p, "<yellow>Match aborted - bet refunded: <green>" +
                        economy.format(bet.getAmount()));
            }
        }
    }

    private boolean isBettingWindowOpen(String arenaName) {
        Long startTime = matchStartTimes.get(arenaName);
        if (startTime == null) return true; // match not started yet
        int windowSeconds = plugin.getConfigManager().getConfig()
                .getInt("betting.bet-window-seconds", 30);
        return System.currentTimeMillis() - startTime < windowSeconds * 1000L;
    }

    /**
     * Place a bet on a match
     */
    public boolean placeBet(Player player, String arenaName, UUID predictedWinner, double amount) {
        if (!enabled) {
            MessageUtil.send(player, "<red>Betting is not available (no economy plugin).");
            return false;
        }

        if (!plugin.getConfigManager().getConfig().getBoolean("betting.enabled", true)) {
            MessageUtil.send(player, "<red>Betting is disabled on this server.");
            return false;
        }

        // Normalise to the canonical arena name so payout/refund lookups match
        arenaName = plugin.getArenaManager().get(arenaName)
                .map(com.omniarena.arena.Arena::getName).orElse(arenaName);

        if (!isBettingWindowOpen(arenaName)) {
            MessageUtil.send(player, "<red>Betting window closed for this match.");
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

        // One active bet per player (a second bet would orphan the first one's bookkeeping)
        if (activeBets.containsKey(player.getUniqueId())) {
            MessageUtil.send(player, "<red>You already have an active bet. Cancel it first.");
            return false;
        }

        // Deduct money
        if (!economy.withdrawPlayer(player, amount).transactionSuccess()) {
            MessageUtil.send(player, "<red>Could not withdraw the bet amount.");
            return false;
        }

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

        // Bets are locked once the betting window closes (prevents free-rolling)
        if (!isBettingWindowOpen(bet.getArenaName())) {
            MessageUtil.send(player, "<red>Betting window closed - you can no longer cancel your bet.");
            return false;
        }

        // Remove bet first (only refund if it was actually still active -> no double refund)
        if (!activeBets.remove(player.getUniqueId(), bet)) return false;

        // Refund money
        economy.depositPlayer(player, bet.getAmount());
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

        // Nobody backed the winner (draw / no winner): refund everyone, never destroy money
        boolean refundAll = winnerId == null || winnerPool <= 0;

        for (Bet bet : bets) {
            org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(bet.getPlayerUuid());
            Player player = Bukkit.getPlayer(bet.getPlayerUuid());

            if (refundAll) {
                economy.depositPlayer(off, bet.getAmount());
                if (player != null) MessageUtil.send(player, "<yellow>No winning bets - your bet of <green>" +
                        economy.format(bet.getAmount()) + "</green> was refunded.");
            } else if (bet.getPredictedWinner().equals(winnerId)) {
                // Winner - payout based on pool share (paid even if offline)
                double winnings = Math.floor(totalPool * (bet.getAmount() / winnerPool) * 100) / 100; // never pay out more than the pool
                economy.depositPlayer(off, winnings);
                if (player != null) MessageUtil.send(player, "<green>You won your bet! <yellow>" + economy.format(winnings) +
                        "</yellow> (profit: <yellow>" + economy.format(winnings - bet.getAmount()) + "</yellow>)");
            } else if (player != null) {
                MessageUtil.send(player, "<red>You lost your bet of <yellow>" + economy.format(bet.getAmount()) + "</yellow>.");
            }

            activeBets.remove(bet.getPlayerUuid(), bet);
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
     * Refund all active bets on server shutdown
     */
    public void refundAll() {
        if (economy == null) return;
        for (Bet bet : activeBets.values()) {
            economy.depositPlayer(Bukkit.getOfflinePlayer(bet.getPlayerUuid()), bet.getAmount());
            Player p = Bukkit.getPlayer(bet.getPlayerUuid());
            if (p != null) MessageUtil.send(p, "<yellow>Server shutting down — bet refunded: <green>" +
                    economy.format(bet.getAmount()));
        }
        activeBets.clear();
        matchBets.clear();
        matchStartTimes.clear();
    }

    /**
     * Clear all bets (for admin use)
     */
    public void clearAllBets() {
        for (Bet bet : activeBets.values()) {
            economy.depositPlayer(Bukkit.getOfflinePlayer(bet.getPlayerUuid()), bet.getAmount());
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
