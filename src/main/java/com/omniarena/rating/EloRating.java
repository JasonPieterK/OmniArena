package com.omniarena.rating;

import java.util.UUID;

/**
 * ELO Rating System for competitive PvP
 * Based on the standard ELO algorithm used in chess and competitive gaming
 */
public class EloRating {

    private final UUID playerUuid;
    private String playerName;
    private int rating;
    private int wins;
    private int losses;
    private int draws;
    private int highestRating;
    private long lastMatchTime;

    // Default starting ELO rating
    public static final int DEFAULT_RATING = 1000;
    // Minimum rating floor
    public static final int MIN_RATING = 100;
    // K-factor determines how much ratings change (higher = more volatile)
    public static final int K_FACTOR = 32;

    public EloRating(UUID playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.rating = DEFAULT_RATING;
        this.wins = 0;
        this.losses = 0;
        this.draws = 0;
        this.highestRating = DEFAULT_RATING;
        this.lastMatchTime = System.currentTimeMillis();
    }

    /**
     * Calculate new ELO ratings after a match
     * @param winnerRating The winner's current rating
     * @param loserRating The loser's current rating
     * @return Array containing [newWinnerRating, newLoserRating]
     */
    public static int[] calculateRatingChange(int winnerRating, int loserRating) {
        // Calculate expected scores
        double expectedWinner = expectedScore(winnerRating, loserRating);
        double expectedLoser = expectedScore(loserRating, winnerRating);

        // Actual scores (1 for win, 0 for loss)
        double actualWinner = 1.0;
        double actualLoser = 0.0;

        // Calculate new ratings
        int newWinnerRating = (int) Math.round(winnerRating + K_FACTOR * (actualWinner - expectedWinner));
        int newLoserRating = (int) Math.round(loserRating + K_FACTOR * (actualLoser - expectedLoser));

        // Apply minimum rating floor
        newWinnerRating = Math.max(MIN_RATING, newWinnerRating);
        newLoserRating = Math.max(MIN_RATING, newLoserRating);

        return new int[]{newWinnerRating, newLoserRating};
    }

    /**
     * Calculate expected score based on ELO difference
     */
    private static double expectedScore(int ratingA, int ratingB) {
        return 1.0 / (1.0 + Math.pow(10, (ratingB - ratingA) / 400.0));
    }

    /**
     * Update rating after a win
     */
    public void recordWin(int opponentRating) {
        int[] newRatings = calculateRatingChange(this.rating, opponentRating);
        this.rating = newRatings[0];
        this.wins++;
        if (this.rating > this.highestRating) {
            this.highestRating = this.rating;
        }
        this.lastMatchTime = System.currentTimeMillis();
    }

    /**
     * Update rating after a loss
     */
    public void recordLoss(int opponentRating) {
        int[] newRatings = calculateRatingChange(opponentRating, this.rating);
        this.rating = newRatings[1];
        this.losses++;
        this.lastMatchTime = System.currentTimeMillis();
    }

    /**
     * Update rating after a draw
     */
    public void recordDraw(int opponentRating) {
        // For draws, both players get the average of expected scores
        double expectedA = expectedScore(this.rating, opponentRating);
        double expectedB = expectedScore(opponentRating, this.rating);
        double actual = 0.5; // Draw

        int newRating = (int) Math.round(this.rating + K_FACTOR * (actual - expectedA));
        this.rating = Math.max(MIN_RATING, newRating);
        this.draws++;
        this.lastMatchTime = System.currentTimeMillis();
    }

    /**
     * Get win rate as percentage
     */
    public double getWinRate() {
        int total = wins + losses + draws;
        if (total == 0) return 0.0;
        return (wins * 100.0) / total;
    }

    /**
     * Get K/D ratio
     */
    public double getKDRatio() {
        if (losses == 0) return wins > 0 ? Double.POSITIVE_INFINITY : 0.0;
        return (double) wins / losses;
    }

    // Getters and Setters
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = Math.max(MIN_RATING, rating); }
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }
    public int getDraws() { return draws; }
    public void setDraws(int draws) { this.draws = draws; }
    public int getHighestRating() { return highestRating; }
    public long getLastMatchTime() { return lastMatchTime; }
    public int getTotalMatches() { return wins + losses + draws; }
}
