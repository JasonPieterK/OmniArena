package com.omniarena.health;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory ring of recent repairs and errors since the last restart. Thread-safe. */
public final class HealthReport {

    public enum Kind { REPAIR, ERROR }

    /** One recorded event. {@code category} groups repairs (config, file, watchdog, task, ...). */
    public record Entry(long time, Kind kind, String category, String message) {}

    private static final int CAPACITY = 100;
    private static final HealthReport INSTANCE = new HealthReport();

    public static HealthReport get() { return INSTANCE; }

    private final Deque<Entry> ring = new ArrayDeque<>();
    private final AtomicInteger repairs = new AtomicInteger();
    private final ErrorTracker tracker = new ErrorTracker();

    public HealthReport() {}

    public ErrorTracker tracker() { return tracker; }

    public void recordRepair(String category, String message) {
        repairs.incrementAndGet();
        add(new Entry(System.currentTimeMillis(), Kind.REPAIR, category, message));
    }

    public void recordError(String component, String message) {
        add(new Entry(System.currentTimeMillis(), Kind.ERROR, component, message));
    }

    private synchronized void add(Entry e) {
        if (ring.size() >= CAPACITY) ring.pollFirst();
        ring.addLast(e);
    }

    /** Number of problems repaired since the last restart (not capped by the ring size). */
    public int repairCount() { return repairs.get(); }

    /** Newest first. */
    public synchronized List<Entry> recent(Kind kind, int limit) {
        List<Entry> out = new ArrayList<>();
        var it = ring.descendingIterator();
        while (it.hasNext() && out.size() < limit) {
            Entry e = it.next();
            if (kind == null || e.kind() == kind) out.add(e);
        }
        return out;
    }

    public synchronized void clear() {
        ring.clear();
        repairs.set(0);
    }
}
