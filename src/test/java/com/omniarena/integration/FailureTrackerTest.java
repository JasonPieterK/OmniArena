package com.omniarena.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailureTrackerTest {

    @Test
    void tripsExactlyOnceOnThirdFailure() {
        FailureTracker t = new FailureTracker();
        assertFalse(t.recordFailure("citizens", "boom 1"));
        assertFalse(t.recordFailure("citizens", "boom 2"));
        assertFalse(t.isTripped("citizens"));
        assertTrue(t.recordFailure("citizens", "boom 3"));
        assertTrue(t.isTripped("citizens"));
        // further failures keep the error but do not report a second trip
        assertFalse(t.recordFailure("citizens", "boom 4"));
        assertEquals("boom 4", t.lastError("citizens"));
    }

    @Test
    void successResetsTheCounter() {
        FailureTracker t = new FailureTracker();
        t.recordFailure("vault", "a");
        t.recordFailure("vault", "b");
        t.recordSuccess("vault");
        assertEquals(0, t.failures("vault"));
        assertFalse(t.recordFailure("vault", "c"));
        assertFalse(t.recordFailure("vault", "d"));
        assertTrue(t.recordFailure("vault", "e"));
    }

    @Test
    void successDoesNotUntripAndResetDoes() {
        FailureTracker t = new FailureTracker(1);
        assertTrue(t.recordFailure("x", "err"));
        t.recordSuccess("x");
        assertTrue(t.isTripped("x"));
        t.reset("x");
        assertFalse(t.isTripped("x"));
        assertNull(t.lastError("x"));
        assertTrue(t.recordFailure("x", "again"));
    }

    @Test
    void hooksAreIndependent() {
        FailureTracker t = new FailureTracker();
        t.recordFailure("a", "1");
        t.recordFailure("a", "2");
        assertEquals(0, t.failures("b"));
        assertFalse(t.recordFailure("b", "1"));
        assertTrue(t.recordFailure("a", "3"));
        assertFalse(t.isTripped("b"));
    }

    @Test
    void limitIsAtLeastOne() {
        FailureTracker t = new FailureTracker(0);
        assertTrue(t.recordFailure("h", "e"));
    }
}
