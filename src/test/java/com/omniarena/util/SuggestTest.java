package com.omniarena.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SuggestTest {

    private static final List<String> SUBS = List.of("join", "leave", "spectate", "stats", "list", "queue");

    @Test
    void distanceBasics() {
        assertEquals(0, Suggest.distance("join", "JOIN"));
        assertEquals(2, Suggest.distance("jion", "join"));
        assertEquals(3, Suggest.distance("kitten", "sitting"));
    }

    @Test
    void closestFindsTypos() {
        assertEquals("join", Suggest.closest("jion", SUBS).orElseThrow());
        assertEquals("leave", Suggest.closest("leav", SUBS).orElseThrow());
        assertEquals("stats", Suggest.closest("STAT", SUBS).orElseThrow());
        assertEquals("spectate", Suggest.closest("spec", SUBS).orElseThrow());
    }

    @Test
    void closestRejectsFarInput() {
        assertTrue(Suggest.closest("zzzzzzzz", SUBS).isEmpty());
        assertTrue(Suggest.closest("", SUBS).isEmpty());
        assertTrue(Suggest.closest("join", List.of()).isEmpty());
    }

    @Test
    void closestNIsOrderedAndLimited() {
        List<String> r = Suggest.closestN("lst", List.of("lost", "list", "leave", "stats"), 2);
        assertEquals(2, r.size());
        assertTrue(r.contains("list"));
    }

    @Test
    void escapeStopsTagInjection() {
        assertEquals("\\<red>x", Suggest.escape("<red>x"));
        String c = Suggest.click("/oa join a'b");
        assertTrue(c.contains("\\'"));
        assertTrue(c.startsWith("<click:suggest_command:'"));
    }
}
