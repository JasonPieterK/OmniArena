package com.omniarena.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorTrackerTest {

    private static RuntimeException boom() { return new IllegalStateException("x"); }
    private static RuntimeException other() { return new IllegalArgumentException("y"); }

    @Test
    void logsOncePerDistinctStackPerFiveMinutes() {
        ErrorTracker t = new ErrorTracker();
        long now = 1_000_000L;
        RuntimeException same = boom(); // one throw site => one stack signature
        assertTrue(t.record("c", same, now));
        assertFalse(t.record("c", same, now + 1_000));
        assertFalse(t.record("c", same, now + ErrorTracker.LOG_INTERVAL_MS - 1));
        assertTrue(t.record("c", other(), now + 2_000), "different stack logs separately");
        assertTrue(t.record("c", same, now + ErrorTracker.LOG_INTERVAL_MS + 1));
    }

    @Test
    void componentBecomesHotAboveThresholdPerMinute() {
        ErrorTracker t = new ErrorTracker();
        long now = 5_000_000L;
        for (int i = 0; i < ErrorTracker.HOT_THRESHOLD; i++) t.record("busy", boom(), now + i);
        assertFalse(t.isHot("busy", now + 100));
        t.record("busy", boom(), now + 200);
        assertTrue(t.isHot("busy", now + 300));
        assertTrue(t.shouldReportHot("busy", now + 300));
        assertFalse(t.shouldReportHot("busy", now + 400), "reported once per 5 minutes");
        assertEquals(ErrorTracker.HOT_THRESHOLD + 1, t.hotComponents(now + 300).get("busy"));
        assertFalse(t.isHot("busy", now + ErrorTracker.HOT_WINDOW_MS + 1_000), "window slides");
        assertFalse(t.isHot("quiet", now));
    }

    @Test
    void totalsCountEveryError() {
        ErrorTracker t = new ErrorTracker();
        for (int i = 0; i < 3; i++) t.record("a", boom(), 1000L + i);
        t.record("b", boom(), 2000L);
        assertEquals(3L, t.totals().get("a"));
        assertEquals(1L, t.totals().get("b"));
    }
}
