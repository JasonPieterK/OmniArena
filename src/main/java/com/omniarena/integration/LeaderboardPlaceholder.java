package com.omniarena.integration;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed form of a leaderboard placeholder key, i.e. the part after "omniarena_":
 * {@code top_<kills|wins|kd|elo>_<1-10>_<name|value>}. Pure (no Bukkit/PlaceholderAPI), unit tested.
 */
public record LeaderboardPlaceholder(LeaderboardMetric metric, int position, boolean name) {

    public static final int MAX_POSITION = 10;

    private static final Pattern KEY = Pattern.compile("^top_([a-z]+)_(\\d{1,3})_(name|value)$");

    /** Parses a key such as {@code top_kills_3_name}; empty when it is not a valid leaderboard key (position 1-10). */
    public static Optional<LeaderboardPlaceholder> parse(String key) {
        if (key == null) return Optional.empty();
        Matcher m = KEY.matcher(key.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) return Optional.empty();
        Optional<LeaderboardMetric> metric = LeaderboardMetric.parse(m.group(1));
        if (metric.isEmpty()) return Optional.empty();
        int pos;
        try { pos = Integer.parseInt(m.group(2)); } catch (NumberFormatException e) { return Optional.empty(); }
        if (pos < 1 || pos > MAX_POSITION) return Optional.empty();
        return Optional.of(new LeaderboardPlaceholder(metric.get(), pos, m.group(3).equals("name")));
    }

    /** The placeholder text for a position, e.g. {@code %omniarena_top_kills_1_name%}. */
    public static String token(LeaderboardMetric metric, int position, boolean name) {
        return "%omniarena_top_" + metric.id() + "_" + position + (name ? "_name%" : "_value%");
    }
}
