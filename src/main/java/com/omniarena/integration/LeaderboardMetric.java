package com.omniarena.integration;

import java.util.Locale;
import java.util.Optional;

/** The four leaderboards OmniArena exposes through placeholders and leaderboard holograms. */
public enum LeaderboardMetric {
    KILLS("TOP KILLS"),
    WINS("TOP WINS"),
    KD("TOP K/D"),
    ELO("TOP ELO");

    private final String title;

    LeaderboardMetric(String title) { this.title = title; }

    /** Hologram heading, e.g. "TOP KILLS". */
    public String title() { return title; }

    /** Lower-case id used in commands and placeholders ("kills", "wins", "kd", "elo"). */
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Optional<LeaderboardMetric> parse(String s) {
        if (s == null) return Optional.empty();
        for (LeaderboardMetric m : values()) if (m.id().equals(s.trim().toLowerCase(Locale.ROOT))) return Optional.of(m);
        return Optional.empty();
    }
}
