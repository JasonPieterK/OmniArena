package com.omniarena.match;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MatchResult {
    public enum Outcome { WIN, LOSS, DRAW, FORFEIT, TIMEOUT }

    private final String arenaName;
    private final UUID winnerId;          // null if draw/team win
    private final String winnerTeamName;  // null if solo win
    private final Outcome outcome;
    private final long durationSeconds;
    private final List<UUID> participants;

    public MatchResult(String arenaName, UUID winnerId, String winnerTeamName,
                       Outcome outcome, long durationSeconds, List<UUID> participants) {
        this.arenaName = arenaName;
        this.winnerId = winnerId;
        this.winnerTeamName = winnerTeamName;
        this.outcome = outcome;
        this.durationSeconds = durationSeconds;
        this.participants = participants;
    }

    public String getArenaName() { return arenaName; }
    public UUID getWinnerId() { return winnerId; }
    public String getWinnerTeamName() { return winnerTeamName; }
    public Outcome getOutcome() { return outcome; }
    public long getDurationSeconds() { return durationSeconds; }
    public List<UUID> getParticipants() { return participants; }

    public boolean isDraw() { return outcome == Outcome.DRAW || outcome == Outcome.TIMEOUT; }
    public boolean hasWinner() { return winnerId != null || winnerTeamName != null; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MatchResult)) return false;
        MatchResult that = (MatchResult) o;
        return durationSeconds == that.durationSeconds
                && Objects.equals(arenaName, that.arenaName)
                && Objects.equals(winnerId, that.winnerId)
                && Objects.equals(winnerTeamName, that.winnerTeamName)
                && outcome == that.outcome
                && Objects.equals(participants, that.participants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(arenaName, winnerId, winnerTeamName, outcome, durationSeconds, participants);
    }

    @Override
    public String toString() {
        return "MatchResult(arenaName=" + arenaName + ", winnerId=" + winnerId
                + ", winnerTeamName=" + winnerTeamName + ", outcome=" + outcome
                + ", durationSeconds=" + durationSeconds + ")";
    }
}
