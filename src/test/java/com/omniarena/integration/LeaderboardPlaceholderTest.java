package com.omniarena.integration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LeaderboardPlaceholderTest {

    @Test
    void parsesNameAndValueKeys() {
        var n = LeaderboardPlaceholder.parse("top_kills_1_name").orElseThrow();
        assertEquals(LeaderboardMetric.KILLS, n.metric());
        assertEquals(1, n.position());
        assertTrue(n.name());

        var v = LeaderboardPlaceholder.parse("top_elo_10_value").orElseThrow();
        assertEquals(LeaderboardMetric.ELO, v.metric());
        assertEquals(10, v.position());
        assertFalse(v.name());

        assertEquals(LeaderboardMetric.KD, LeaderboardPlaceholder.parse("TOP_KD_3_NAME").orElseThrow().metric());
        assertEquals(LeaderboardMetric.WINS, LeaderboardPlaceholder.parse("top_wins_7_value").orElseThrow().metric());
    }

    @Test
    void rejectsInvalidKeys() {
        assertTrue(LeaderboardPlaceholder.parse(null).isEmpty());
        assertTrue(LeaderboardPlaceholder.parse("kills").isEmpty());
        assertTrue(LeaderboardPlaceholder.parse("top_kills_0_name").isEmpty());
        assertTrue(LeaderboardPlaceholder.parse("top_kills_11_name").isEmpty());
        assertTrue(LeaderboardPlaceholder.parse("top_kills_1").isEmpty());
        assertTrue(LeaderboardPlaceholder.parse("top_deaths_1_name").isEmpty());
        assertTrue(LeaderboardPlaceholder.parse("top_kills_1_score").isEmpty());
        assertTrue(LeaderboardPlaceholder.parse("top_kills_x_name").isEmpty());
        assertTrue(LeaderboardPlaceholder.parse("top_kills_99999999999_name").isEmpty());
    }

    @Test
    void tokenRoundTrips() {
        String token = LeaderboardPlaceholder.token(LeaderboardMetric.WINS, 4, false);
        assertEquals("%omniarena_top_wins_4_value%", token);
        String key = token.substring("%omniarena_".length(), token.length() - 1);
        var p = LeaderboardPlaceholder.parse(key).orElseThrow();
        assertEquals(LeaderboardMetric.WINS, p.metric());
        assertEquals(4, p.position());
        assertFalse(p.name());
    }

    @Test
    void cacheServesRowsAndBlanks() {
        LeaderboardCache c = new LeaderboardCache();
        assertEquals(LeaderboardCache.EMPTY_NAME, c.name(LeaderboardMetric.KILLS, 1)); // never refreshed
        UUID id = UUID.randomUUID();
        c.install(Map.of(LeaderboardMetric.KILLS, List.of(
                new LeaderboardCache.Row("Alice", "12"), new LeaderboardCache.Row("Bob", "7"))), Map.of(id, 3));
        assertEquals("Alice", c.name(LeaderboardMetric.KILLS, 1));
        assertEquals("7", c.value(LeaderboardMetric.KILLS, 2));
        assertEquals(LeaderboardCache.EMPTY_NAME, c.name(LeaderboardMetric.KILLS, 3));
        assertEquals(LeaderboardCache.EMPTY_VALUE, c.value(LeaderboardMetric.WINS, 1));
        assertEquals(2, c.rows(LeaderboardMetric.KILLS, 10).size());
        assertEquals(1, c.rows(LeaderboardMetric.KILLS, 1).size());
        assertEquals(3, c.eloRank(id));
        assertEquals(-1, c.eloRank(UUID.randomUUID()));
        assertTrue(c.ageMillis() < 5000);
        assertEquals("2.50", LeaderboardCache.formatKd(2.5));
    }

    @Test
    void placeholderLinesUseTokensAndClampSize() {
        List<String> lines = LeaderboardLines.build(LeaderboardMetric.KILLS, 3, false, null);
        assertEquals(4, lines.size());
        assertEquals("&6&lTOP KILLS", lines.get(0));
        assertTrue(lines.get(1).contains("%omniarena_top_kills_1_name%"));
        assertTrue(lines.get(1).contains("%omniarena_top_kills_1_value%"));
        assertEquals(11, LeaderboardLines.build(LeaderboardMetric.ELO, 99, true, null).size()); // clamped to 10 + title
        assertEquals(2, LeaderboardLines.build(LeaderboardMetric.ELO, -5, true, null).size());   // clamped to 1 + title
        assertTrue(LeaderboardLines.build(LeaderboardMetric.KD, 1, true, null).get(0).startsWith("<gold>"));
    }

    @Test
    void staticLinesBakeValuesAndPadEmptyPlaces() {
        List<String> lines = LeaderboardLines.build(LeaderboardMetric.WINS, 2, false,
                List.of(new LeaderboardCache.Row("Alice", "5")));
        assertTrue(lines.get(1).contains("Alice") && lines.get(1).contains("5"));
        assertTrue(lines.get(2).contains(LeaderboardCache.EMPTY_NAME));
        assertFalse(lines.get(1).contains("%"));
    }
}
