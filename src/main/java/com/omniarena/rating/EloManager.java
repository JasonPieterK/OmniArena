package com.omniarena.rating;

import com.omniarena.OmniArena;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages ELO ratings for all players
 * Handles persistence, retrieval, and ranking calculations
 */
public class EloManager {

    private static EloManager instance;

    private final OmniArena plugin;
    private final File eloFile;
    private final Map<UUID, EloRating> ratings = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> matchHistory = new ConcurrentHashMap<>(); // Tracks recent matches for abuse prevention

    public EloManager(OmniArena plugin) {
        this.plugin = plugin;
        this.eloFile = new File(plugin.getDataFolder(), "elo.yml");
        instance = this;
    }

    public static EloManager getInstance() { return instance; }

    public void loadAll() {
        if (!eloFile.exists()) return;
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(eloFile);
            if (!cfg.contains("ratings")) return;
            
            for (String uuidStr : cfg.getConfigurationSection("ratings").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String path = "ratings." + uuidStr;
                    String name = cfg.getString(path + ".name", "Unknown");
                    EloRating rating = new EloRating(uuid, name);
                    rating.setRating(cfg.getInt(path + ".rating"));
                    rating.setWins(cfg.getInt(path + ".wins"));
                    rating.setLosses(cfg.getInt(path + ".losses"));
                    rating.setDraws(cfg.getInt(path + ".draws"));
                    // Note: highestRating not persisted, recalculated on load
                    rating.setHighestRating(Math.max(rating.getRating(),
                            cfg.getInt(path + ".highestRating", rating.getRating())));
                    ratings.put(uuid, rating);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load ELO for " + uuidStr + ": " + e.getMessage());
                }
            }
            plugin.getLogger().info("Loaded ELO ratings for " + ratings.size() + " player(s).");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load ELO file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveAll() {
        com.omniarena.util.AsyncUtil.runAsync(() -> {
            FileConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<UUID, EloRating> entry : ratings.entrySet()) {
                String path = "ratings." + entry.getKey();
                EloRating r = entry.getValue();
                cfg.set(path + ".name", r.getPlayerName());
                cfg.set(path + ".rating", r.getRating());
                cfg.set(path + ".wins", r.getWins());
                cfg.set(path + ".losses", r.getLosses());
                cfg.set(path + ".draws", r.getDraws());
                cfg.set(path + ".highestRating", r.getHighestRating());
            }
            com.omniarena.util.YamlUtil.save(cfg, eloFile, plugin.getLogger());
        });
    }

    public EloRating getRating(UUID uuid) {
        return ratings.computeIfAbsent(uuid, id -> {
            String name = org.bukkit.Bukkit.getOfflinePlayer(id).getName();
            return new EloRating(id, name != null ? name : "Unknown");
        });
    }

    public EloRating getRating(UUID uuid, String playerName) {
        EloRating rating = getRating(uuid);
        rating.setPlayerName(playerName);
        return rating;
    }

    /**
     * Record a match result and update ratings
     */
    public void recordMatch(UUID winner, UUID loser, String winnerName, String loserName) {
        EloRating winnerRating = getRating(winner, winnerName);
        EloRating loserRating = getRating(loser, loserName);

        int oldWinnerRating = winnerRating.getRating();
        int oldLoserRating = loserRating.getRating();

        // recordWin/recordLoss compute and apply the new rating themselves from the
        // pre-match ratings (also applying it here double-counted the change).
        winnerRating.recordWin(oldLoserRating);
        loserRating.recordLoss(oldWinnerRating);

        // Track match history for abuse prevention
        long now = System.currentTimeMillis();
        matchHistory.put(winner, (int) (now / 1000));
        matchHistory.put(loser, (int) (now / 1000));

        saveAll();
    }

    /**
     * Check if two players have fought recently (for abuse prevention)
     */
    public boolean foughtRecently(UUID player1, UUID player2, long seconds) {
        Integer time1 = matchHistory.get(player1);
        Integer time2 = matchHistory.get(player2);
        if (time1 == null || time2 == null) return false;
        return Math.abs(time1 - time2) < seconds;
    }

    /**
     * Get top players by rating
     */
    public List<EloRating> getTopPlayers(int limit) {
        return ratings.values().stream()
                .sorted(Comparator.comparingInt(EloRating::getRating).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Get leaderboard with ranks
     */
    public List<LeaderboardEntry> getLeaderboard(int limit) {
        List<EloRating> sorted = ratings.values().stream()
                .sorted(Comparator.comparingInt(EloRating::getRating).reversed())
                .limit(limit)
                .toList();
        
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            entries.add(new LeaderboardEntry(i + 1, sorted.get(i)));
        }
        return entries;
    }

    /**
     * Get player's rank
     */
    public int getPlayerRank(UUID uuid) {
        EloRating rating = ratings.get(uuid);
        if (rating == null) return -1;
        
        int rank = 1;
        for (EloRating r : ratings.values()) {
            if (r.getRating() > rating.getRating()) rank++;
        }
        return rank;
    }

    /**
     * Reset a player's rating
     */
    public void resetRating(UUID uuid) {
        EloRating rating = getRating(uuid);
        rating.setRating(EloRating.DEFAULT_RATING);
        rating.setWins(0);
        rating.setLosses(0);
        rating.setDraws(0);
        saveAll();
    }

    public int getTotalPlayers() {
        return ratings.size();
    }

    /**
     * Leaderboard entry for display
     */
    public static class LeaderboardEntry {
        private final int rank;
        private final EloRating rating;

        public LeaderboardEntry(int rank, EloRating rating) {
            this.rank = rank;
            this.rating = rating;
        }

        public int getRank() { return rank; }
        public EloRating getRating() { return rating; }
        public UUID getPlayerUuid() { return rating.getPlayerUuid(); }
        public String getPlayerName() { return rating.getPlayerName(); }
        public int getElo() { return rating.getRating(); }
        public int getWins() { return rating.getWins(); }
        public int getLosses() { return rating.getLosses(); }
        public double getWinRate() { return rating.getWinRate(); }
    }
}
