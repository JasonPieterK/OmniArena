package com.omniarena.integration;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure bookkeeping for the integration isolation wrapper: counts consecutive failures per hook and
 * reports when a hook crosses the threshold (default 3) and must be switched off. A success resets
 * the counter; {@link #reset(String)} also forgets the last error (used when a hook is re-enabled).
 * Thread-safe (hooks can be called from async threads, e.g. PlaceholderAPI).
 */
public final class FailureTracker {

    public static final int DEFAULT_LIMIT = 3;

    private final int limit;
    private final Map<String, Integer> failures = new HashMap<>();
    private final Map<String, String> lastError = new HashMap<>();
    private final Map<String, Boolean> tripped = new HashMap<>();

    public FailureTracker() { this(DEFAULT_LIMIT); }

    public FailureTracker(int limit) {
        this.limit = Math.max(1, limit);
    }

    /**
     * Records a failure.
     *
     * @return true exactly once: when this failure makes the hook reach the limit (it must now be disabled)
     */
    public synchronized boolean recordFailure(String hook, String error) {
        lastError.put(hook, error);
        if (tripped.getOrDefault(hook, false)) return false;
        int n = failures.merge(hook, 1, Integer::sum);
        if (n >= limit) {
            tripped.put(hook, true);
            return true;
        }
        return false;
    }

    /** A successful call: clears the consecutive-failure counter (the last error is kept for diagnostics). */
    public synchronized void recordSuccess(String hook) {
        if (!tripped.getOrDefault(hook, false)) failures.remove(hook);
    }

    /** True once the hook reached the failure limit and until {@link #reset(String)}. */
    public synchronized boolean isTripped(String hook) {
        return tripped.getOrDefault(hook, false);
    }

    public synchronized int failures(String hook) {
        return failures.getOrDefault(hook, 0);
    }

    /** Most recent error text, or null. */
    public synchronized String lastError(String hook) {
        return lastError.get(hook);
    }

    /** Forgets counter, trip state and last error (hook re-enabled by reload or by its plugin being enabled again). */
    public synchronized void reset(String hook) {
        failures.remove(hook);
        tripped.remove(hook);
        lastError.remove(hook);
    }
}
