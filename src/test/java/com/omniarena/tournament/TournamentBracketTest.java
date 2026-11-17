package com.omniarena.tournament;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TournamentBracketTest {

    private static List<UUID> uuids(int n) {
        List<UUID> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(UUID.randomUUID());
        return list;
    }

    @Test
    void powerOfTwo_noByesGenerated() {
        List<UUID> players = uuids(4);
        TournamentBracket bracket = new TournamentBracket(players, "arena");
        List<TournamentMatch> round1 = bracket.getCurrentRound();
        // 4 players → 2 matches, 0 byes
        assertEquals(2, round1.size());
        assertTrue(round1.stream().noneMatch(TournamentMatch::isBye));
    }

    @Test
    void sixPlayers_twoByes() {
        List<UUID> players = uuids(6);
        TournamentBracket bracket = new TournamentBracket(players, "arena");
        List<TournamentMatch> round1 = bracket.getCurrentRound();
        // next power of 2 above 6 is 8 → 2 byes, so 4 "matches" in round1: 2 byes + 2 real
        assertEquals(4, round1.size());
        long byeCount = round1.stream().filter(TournamentMatch::isBye).count();
        assertEquals(2, byeCount);
    }

    @Test
    void advanceWinner_roundCompletes() {
        List<UUID> players = uuids(4);
        TournamentBracket bracket = new TournamentBracket(players, "arena");
        List<TournamentMatch> round1 = bracket.getCurrentRound();

        UUID w1 = round1.get(0).getPlayer1();
        UUID w2 = round1.get(1).getPlayer1();
        bracket.recordWinner(round1.get(0), w1);
        bracket.recordWinner(round1.get(1), w2);

        assertTrue(bracket.isRoundComplete());
        assertFalse(bracket.isFinished());

        bracket.advanceRound();
        List<TournamentMatch> round2 = bracket.getCurrentRound();
        assertEquals(1, round2.size());
        assertTrue(round2.get(0).involves(w1));
        assertTrue(round2.get(0).involves(w2));
    }

    @Test
    void twoPlayers_finishesInOneRound() {
        List<UUID> players = uuids(2);
        TournamentBracket bracket = new TournamentBracket(players, "arena");
        TournamentMatch final_ = bracket.getCurrentRound().get(0);
        UUID winner = final_.getPlayer1();
        bracket.recordWinner(final_, winner);
        assertTrue(bracket.isRoundComplete());
        assertTrue(bracket.isFinished());
        assertEquals(winner, bracket.getFinalWinner());
    }

    @Test
    void getMatchForArena_returnsCorrectMatch() {
        List<UUID> players = uuids(4);
        TournamentBracket bracket = new TournamentBracket(players, "arena");
        Optional<TournamentMatch> match = bracket.getActiveMatchForArena("arena");
        assertTrue(match.isPresent());
        assertFalse(match.get().isBye());
    }

    @Test
    void fullRun_variousSizes_alwaysProducesChampion() {
        for (int n : new int[]{2, 3, 4, 5, 6, 7, 8, 9, 16}) {
            TournamentBracket b = new TournamentBracket(uuids(n), "arena");
            int guard = 0;
            while (!b.isFinished() && guard++ < 10) {
                for (TournamentMatch m : b.getPendingMatches()) b.recordWinner(m, m.getPlayer1());
                b.advanceRound();
            }
            assertTrue(b.isFinished(), "n=" + n);
            assertNotNull(b.getFinalWinner(), "n=" + n);
            assertNotNull(b.getFinalLoser(), "n=" + n);
        }
    }
}
