package com.omniarena.stats;

import java.util.UUID;

public class PlayerStats {
    private final UUID playerId;
    private String playerName;

    private int kills;
    private int deaths;
    private int wins;
    private int losses;
    private int assists;
    private int draws;
    private long timePlayed; // seconds

    public PlayerStats(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public int getKills() { return kills; }
    public void setKills(int kills) { this.kills = kills; }
    public int getDeaths() { return deaths; }
    public void setDeaths(int deaths) { this.deaths = deaths; }
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }
    public int getAssists() { return assists; }
    public void setAssists(int assists) { this.assists = assists; }
    public int getDraws() { return draws; }
    public void setDraws(int draws) { this.draws = draws; }
    public long getTimePlayed() { return timePlayed; }
    public void setTimePlayed(long timePlayed) { this.timePlayed = timePlayed; }

    public double getKDRatio() {
        if (deaths == 0) return kills;
        return Math.round((double) kills / deaths * 100.0) / 100.0;
    }

    public double getWinRate() {
        int total = wins + losses;
        if (total == 0) return 0;
        return Math.round((double) wins / total * 100.0) / 100.0;
    }

    public void addKill()   { kills++; }
    public void addDeath()  { deaths++; }
    public void addWin()    { wins++; }
    public void addLoss()   { losses++; }
    public void addAssist() { assists++; }
    public void addDraw()   { draws++; }
    public void addTime(long seconds) { timePlayed += seconds; }

    public String getFormattedTimePlayed() {
        long h = timePlayed / 3600;
        long m = (timePlayed % 3600) / 60;
        long s = timePlayed % 60;
        return String.format("%dh %dm %ds", h, m, s);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerStats)) return false;
        PlayerStats that = (PlayerStats) o;
        return kills == that.kills && deaths == that.deaths && wins == that.wins
                && losses == that.losses && assists == that.assists && draws == that.draws
                && timePlayed == that.timePlayed
                && java.util.Objects.equals(playerId, that.playerId)
                && java.util.Objects.equals(playerName, that.playerName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(playerId, playerName, kills, deaths, wins, losses, assists, draws, timePlayed);
    }

    @Override
    public String toString() {
        return "PlayerStats(playerId=" + playerId + ", playerName=" + playerName
                + ", kills=" + kills + ", deaths=" + deaths + ", wins=" + wins
                + ", losses=" + losses + ", assists=" + assists + ", draws=" + draws
                + ", timePlayed=" + timePlayed + ")";
    }
}
