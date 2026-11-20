package com.omniarena.health;

import com.omniarena.OmniArena;

import java.util.logging.Level;

/**
 * Error isolation for OmniArena's own code: runs a risky section so one exception is logged (rate-limited to once
 * per distinct stack per 5 minutes) instead of spamming the console or killing a repeating task's caller, counts
 * errors per component and reports a component that keeps throwing.
 */
public final class ErrorGuard {

    private ErrorGuard() {}

    /** Runs {@code body}; any exception is recorded and swallowed. */
    public static void run(String component, Runnable body) {
        try {
            body.run();
        } catch (Exception | LinkageError | AssertionError t) {
            handle(component, t);
        }
    }

    /** Returns a runnable that executes {@code body} through {@link #run}. */
    public static Runnable wrap(String component, Runnable body) {
        return () -> run(component, body);
    }

    /** Records an error that the caller already caught itself. */
    public static void handle(String component, Throwable t) {
        long now = System.currentTimeMillis();
        HealthReport report = HealthReport.get();
        ErrorTracker tracker = report.tracker();
        boolean log = tracker.record(component, t, now);
        OmniArena plugin = OmniArena.getInstance();
        if (log) {
            report.recordError(component, t.getClass().getSimpleName() + ": " + t.getMessage());
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Error in " + component + " (" + t + "). Further identical errors are "
                        + "hidden for 5 minutes. Run /oa doctor for a summary; please report the trace below with your plugin version.", t);
            }
        }
        if (tracker.shouldReportHot(component, now)) {
            String msg = "Component '" + component + "' threw " + tracker.lastMinute(component, now)
                    + " errors in the last minute. It keeps running but is misbehaving: run /oa doctor and report the trace.";
            report.recordError(component, msg);
            if (plugin != null) plugin.getLogger().severe(msg);
        }
    }
}
