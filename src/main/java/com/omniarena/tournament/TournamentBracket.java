package com.omniarena.tournament;

import java.util.*;

public class TournamentBracket {

    private final String arenaName;
    private final List<List<TournamentMatch>> rounds = new ArrayList<>();
    private int currentRoundIndex = 0;

    public TournamentBracket(List<UUID> participants, String arenaName) {
        this.arenaName = arenaName;
        generateRound1(new ArrayList<>(participants));
    }

    private void generateRound1(List<UUID> seeded) {
        int n = seeded.size();
        int nextPow2 = nextPowerOfTwo(n);
        int byes = nextPow2 - n;

        List<TournamentMatch> round = new ArrayList<>();
        int idx = 0;
        for (int i = 0; i < byes; i++) {
            round.add(new TournamentMatch(seeded.get(idx++), null, arenaName, 1));
        }
        while (idx < seeded.size()) {
            UUID p1 = seeded.get(idx++);
            UUID p2 = seeded.get(idx++);
            round.add(new TournamentMatch(p1, p2, arenaName, 1));
        }
        rounds.add(round);
    }

    private static int nextPowerOfTwo(int n) {
        if (n <= 1) return 1;
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    public List<TournamentMatch> getCurrentRound() {
        if (currentRoundIndex >= rounds.size()) return List.of();
        return Collections.unmodifiableList(rounds.get(currentRoundIndex));
    }

    public int getCurrentRoundNumber() { return currentRoundIndex + 1; }

    public boolean isRoundComplete() {
        return getCurrentRound().stream().allMatch(TournamentMatch::isCompleted);
    }

    public boolean isFinished() {
        return isRoundComplete() && getWinners().size() == 1;
    }

    public UUID getFinalWinner() {
        if (!isFinished()) return null;
        return getWinners().get(0);
    }

    public void recordWinner(TournamentMatch match, UUID winner) {
        match.setWinner(winner);
    }

    /**
     * Advances to the next round. Call only after {@link #isRoundComplete()} returns true.
     * If the bracket is finished ({@link #isFinished()}), this is a no-op.
     */
    public void advanceRound() {
        if (!isRoundComplete()) return; // don't advance until all matches done
        List<UUID> winners = getWinners();
        if (winners.size() <= 1) return;
        currentRoundIndex++;
        int roundNum = currentRoundIndex + 1;
        List<TournamentMatch> next = new ArrayList<>();
        for (int i = 0; i < winners.size() - 1; i += 2) {
            next.add(new TournamentMatch(winners.get(i), winners.get(i + 1), arenaName, roundNum));
        }
        // ponytail: safety net bye — shouldn't occur with power-of-2 seeding
        if (winners.size() % 2 == 1) {
            next.add(new TournamentMatch(winners.get(winners.size() - 1), null, arenaName, roundNum));
        }
        rounds.add(next);
    }

    private List<UUID> getWinners() {
        List<UUID> winners = new ArrayList<>();
        for (TournamentMatch m : getCurrentRound()) {
            if (m.getWinner() != null) winners.add(m.getWinner());
        }
        return winners;
    }

    public Optional<TournamentMatch> getActiveMatchForArena(String arena) {
        return getCurrentRound().stream()
                .filter(m -> !m.isBye() && !m.isCompleted() && m.getArenaName().equals(arena))
                .findFirst();
    }

    public List<TournamentMatch> getPendingMatches() {
        return getCurrentRound().stream()
                .filter(m -> !m.isBye() && !m.isCompleted())
                .toList();
    }

    public List<List<TournamentMatch>> getAllRounds() {
        return Collections.unmodifiableList(rounds);
    }
}
