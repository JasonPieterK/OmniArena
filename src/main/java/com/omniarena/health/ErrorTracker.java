package com.omniarena.health;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure-logic error bookkeeping: rate-limits logging per distinct stack (once per 5 minutes) and counts errors per
 * component so a component that keeps throwing can be reported. Time is passed in so it is unit-testable.
 */
public final class ErrorTracker {

    public static final long LOG_INTERVAL_MS = 5 * 60_000L;
    public static final long HOT_WINDOW_MS = 60_000L;
    public static final int HOT_THRESHOLD = 20;
    private static final int MAX_SIGNATURES = 500;

    private final Map<String, Long> lastLogged = new HashMap<>();
    private final Map<String, Deque<Long>> recent = new HashMap<>();
    private final Map<String, Long> totals = new HashMap<>();
    private final Map<String, Long> lastHotReport = new HashMap<>();

    /** Records an error; returns true when this distinct stack should be logged now (first in 5 minutes). */
    public synchronized boolean record(String component, Throwable t, long now) {
        Deque<Long> times = recent.computeIfAbsent(component, k -> new ArrayDeque<>());
        times.addLast(now);
        prune(times, now);
        totals.merge(component, 1L, Long::sum);

        String sig = signature(component, t);
        Long prev = lastLogged.get(sig);
        if (prev != null && now - prev < LOG_INTERVAL_MS) return false;
        if (lastLogged.size() >= MAX_SIGNATURES) lastLogged.clear();
        lastLogged.put(sig, now);
        return true;
    }

    /** Errors of {@code component} within the last minute. */
    public synchronized int lastMinute(String component, long now) {
        Deque<Long> times = recent.get(component);
        if (times == null) return 0;
        prune(times, now);
        return times.size();
    }

    /** True when the component threw more than {@link #HOT_THRESHOLD} times in the last minute. */
    public synchronized boolean isHot(String component, long now) {
        return lastMinute(component, now) > HOT_THRESHOLD;
    }

    /**
     * True (once per 5 minutes per component) when the component just became/stayed hot and the caller should
     * announce it.
     */
    public synchronized boolean shouldReportHot(String component, long now) {
        if (!isHot(component, now)) return false;
        Long prev = lastHotReport.get(component);
        if (prev != null && now - prev < LOG_INTERVAL_MS) return false;
        lastHotReport.put(component, now);
        return true;
    }

    /** Components that are currently hot (sorted by name). */
    public synchronized Map<String, Integer> hotComponents(long now) {
        Map<String, Integer> out = new TreeMap<>();
        for (String c : new java.util.ArrayList<>(recent.keySet())) {
            int n = lastMinute(c, now);
            if (n > HOT_THRESHOLD) out.put(c, n);
        }
        return out;
    }

    /** Total errors per component since start (sorted by name). */
    public synchronized Map<String, Long> totals() {
        return new TreeMap<>(totals);
    }

    private static void prune(Deque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() > HOT_WINDOW_MS) times.pollFirst();
    }

    static String signature(String component, Throwable t) {
        StringBuilder sb = new StringBuilder(component).append('|').append(t.getClass().getName());
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < Math.min(4, st.length); i++) sb.append('|').append(st[i]);
        return sb.toString();
    }
}
