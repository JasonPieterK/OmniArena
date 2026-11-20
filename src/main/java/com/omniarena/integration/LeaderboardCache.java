package com.omniarena.integration;

import com.omniarena.rating.EloManager;
import com.omniarena.rating.EloRating;
import com.omniarena.stats.PlayerStats;
import com.omniarena.stats.StatsManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only snapshot of the top-10 lists and ELO ranks used by the leaderboard placeholders and by
 * static leaderboard holograms. It is rebuilt on the main thread by {@link #refresh()} (the
 * IntegrationManager schedules that about every 30 s); lookups only read immutable snapshots, so they
 * are cheap, safe from any thread (PlaceholderAPI consumers such as TAB call asynchronously) and never
 * create stats/ELO entries.
 */
public final class LeaderboardCache {

    /** One leaderboard line. */
    public record Row(String name, String value) {}

    public static final String EMPTY_NAME = "---";
    public static final String EMPTY_VALUE = "-";

    private volatile Map<LeaderboardMetric, List<Row>> tops = Map.of();
    private volatile Map<UUID, Integer> eloRanks = Map.of();
    private volatile long refreshedAt;

    /** Rebuilds the snapshots from StatsManager/EloManager. Main thread only. */
    public void refresh() {
        Map<LeaderboardMetric, List<Row>> t = new EnumMap<>(LeaderboardMetric.class);
        int n = LeaderboardPlaceholder.MAX_POSITION;

        StatsManager sm = StatsManager.getInstance();
        if (sm != null) {
            t.put(LeaderboardMetric.KILLS, rows(sm.getTopKills(n), s -> String.valueOf(s.getKills())));
            t.put(LeaderboardMetric.WINS, rows(sm.getTopWins(n), s -> String.valueOf(s.getWins())));
            t.put(LeaderboardMetric.KD, rows(sm.getTopKD(n), s -> formatKd(s.getKDRatio())));
        }

        Map<UUID, Integer> ranks = new HashMap<>();
        EloManager em = EloManager.getInstance();
        if (em != null) {
            List<EloRating> all = em.getLeaderboard(Integer.MAX_VALUE).stream().map(EloManager.LeaderboardEntry::getRating).toList();
            List<Row> eloRows = new ArrayList<>();
            int rank = 0, shown = 0, prev = Integer.MIN_VALUE;
            for (int i = 0; i < all.size(); i++) {
                EloRating r = all.get(i);
                if (r.getRating() != prev) { rank = i + 1; prev = r.getRating(); }
                ranks.put(r.getPlayerUuid(), rank);
                if (shown < n) {
                    eloRows.add(new Row(nameOrEmpty(r.getPlayerName()), String.valueOf(r.getRating())));
                    shown++;
                }
            }
            t.put(LeaderboardMetric.ELO, List.copyOf(eloRows));
        }
        install(t, ranks);
    }

    /** Replaces the snapshots (used by refresh and by tests). */
    void install(Map<LeaderboardMetric, List<Row>> newTops, Map<UUID, Integer> newRanks) {
        Map<LeaderboardMetric, List<Row>> copy = new EnumMap<>(LeaderboardMetric.class);
        newTops.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        this.tops = copy;
        this.eloRanks = Map.copyOf(newRanks);
        this.refreshedAt = System.currentTimeMillis();
    }

    private static List<Row> rows(List<PlayerStats> stats, java.util.function.Function<PlayerStats, String> value) {
        List<Row> out = new ArrayList<>();
        for (PlayerStats s : stats) out.add(new Row(nameOrEmpty(s.getPlayerName()), value.apply(s)));
        return out;
    }

    private static String nameOrEmpty(String name) {
        return name == null || name.isBlank() ? "Unknown" : name;
    }

    /** "2.50" style, locale independent. */
    public static String formatKd(double kd) {
        return String.format(Locale.ROOT, "%.2f", kd);
    }

    /** The row at 1-based {@code position}, or null when that place is empty. */
    public Row row(LeaderboardMetric metric, int position) {
        List<Row> l = tops.get(metric);
        return l != null && position >= 1 && position <= l.size() ? l.get(position - 1) : null;
    }

    public String name(LeaderboardMetric metric, int position) {
        Row r = row(metric, position);
        return r != null ? r.name() : EMPTY_NAME;
    }

    public String value(LeaderboardMetric metric, int position) {
        Row r = row(metric, position);
        return r != null ? r.value() : EMPTY_VALUE;
    }

    /** Up to {@code size} rows; missing places are not padded. */
    public List<Row> rows(LeaderboardMetric metric, int size) {
        List<Row> l = tops.getOrDefault(metric, List.of());
        return l.subList(0, Math.min(Math.max(0, size), l.size()));
    }

    /** ELO rank (1 = best, ties share a rank) as of the last refresh, or -1 when the player has no rating. */
    public int eloRank(UUID uuid) {
        Integer r = eloRanks.get(uuid);
        return r != null ? r : -1;
    }

    /** Milliseconds since the last refresh, or Long.MAX_VALUE when it never ran. */
    public long ageMillis() {
        return refreshedAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - refreshedAt;
    }
}
