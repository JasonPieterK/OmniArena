package com.omniarena.tournament;

import java.util.UUID;

public class TournamentMatch {

    private final UUID player1;
    private final UUID player2;   // null = bye (player1 auto-advances)
    private final String arenaName;
    private final int round;
    private UUID winner;
    private boolean completed;
    private boolean started;

    public TournamentMatch(UUID player1, UUID player2, String arenaName, int round) {
        this.player1 = player1;
        this.player2 = player2;
        this.arenaName = arenaName;
        this.round = round;
        this.completed = (player2 == null); // bye auto-completes
        this.winner = (player2 == null) ? player1 : null;
    }

    public UUID getPlayer1()  { return player1; }
    public UUID getPlayer2()  { return player2; }
    public String getArenaName() { return arenaName; }
    public int getRound()     { return round; }
    public UUID getWinner()   { return winner; }
    public boolean isBye()    { return player2 == null; }
    public boolean isCompleted() { return completed; }
    public boolean isStarted()   { return started; }
    public void setStarted(boolean started) { this.started = started; }

    /** Restore persisted result (no-op for unfinished matches). */
    public void restoreWinner(UUID winner) {
        if (winner != null) setWinner(winner);
    }

    public void setWinner(UUID winner) {
        this.winner = winner;
        this.completed = true;
    }

    /** True if this match involves the given player (either slot). */
    public boolean involves(UUID uuid) {
        return uuid.equals(player1) || uuid.equals(player2);
    }
}
