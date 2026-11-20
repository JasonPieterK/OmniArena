package com.omniarena.integration;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the text lines of a leaderboard hologram. Pure (no Bukkit), unit tested.
 * Two flavours: placeholder lines (a hologram plugin resolves {@code %omniarena_top_..%} itself, needs
 * PlaceholderAPI) and static lines (current values baked in, refreshed by OmniArena).
 * Two colour syntaxes: legacy {@code &} codes (DecentHolograms) and MiniMessage (FancyHolograms).
 */
public final class LeaderboardLines {

    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = LeaderboardPlaceholder.MAX_POSITION;
    public static final int DEFAULT_SIZE = 10;

    private LeaderboardLines() {}

    public static int clampSize(int size) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    }

    /**
     * @param staticRows rows to bake in, or null to emit PlaceholderAPI placeholders instead
     */
    public static List<String> build(LeaderboardMetric metric, int size, boolean miniMessage, List<LeaderboardCache.Row> staticRows) {
        int n = clampSize(size);
        List<String> lines = new ArrayList<>(n + 1);
        lines.add(miniMessage ? "<gold><bold>" + metric.title() : "&6&l" + metric.title());
        for (int i = 1; i <= n; i++) {
            String name, value;
            if (staticRows == null) {
                name = LeaderboardPlaceholder.token(metric, i, true);
                value = LeaderboardPlaceholder.token(metric, i, false);
            } else {
                LeaderboardCache.Row r = i <= staticRows.size() ? staticRows.get(i - 1) : null;
                name = r != null ? r.name() : LeaderboardCache.EMPTY_NAME;
                value = r != null ? r.value() : LeaderboardCache.EMPTY_VALUE;
            }
            lines.add(miniMessage
                    ? "<yellow>#" + i + " <white>" + name + " <gray>- <green>" + value
                    : "&e#" + i + " &f" + name + " &7- &a" + value);
        }
        return lines;
    }
}
