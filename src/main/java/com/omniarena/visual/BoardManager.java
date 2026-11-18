package com.omniarena.visual;

import com.omniarena.config.ConfigManager;
import com.omniarena.match.GoalType;
import com.omniarena.match.Match;
import com.omniarena.match.MatchState;
import com.omniarena.match.MatchTeam;
import com.omniarena.util.ColorUtil;
import fr.mrmicky.fastboard.FastBoard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/** Per-player sidebar scoreboards (main thread only). One board per player. */
public class BoardManager {

    private static final String DEFAULT_TITLE = "<gradient:#ff6b35:#f7c59f>OmniArena</gradient>";
    private static final int MAX_LINES = 15;
    private static final int MAX_LINE_LEN = 40;

    private final Map<UUID, FastBoard> boards = new HashMap<>();
    /** Last throttled updateAll time (ms) per match. */
    private final Map<Match, Long> lastUpdate = new IdentityHashMap<>();

    /** Per-update data computed once per match, shared by every viewer. */
    private static final class Snapshot {
        String arenaName, mode, time;
        int playerCount, alive;
        Map<UUID, Integer> rank = new HashMap<>();
        GoalType goal;
        int goalValue;
        boolean lives;
        List<MatchTeam> teams = new ArrayList<>();
        boolean waiting, countdown;
    }

    private boolean enabled(Match match) {
        return match.getArena().getVisual().isScoreboard()
                && ConfigManager.getInstance().getConfig().getBoolean("visual.scoreboard", true);
    }

    public void createBoard(Player player, Match match) {
        removeBoard(player);
        if (!enabled(match)) return;
        FastBoard board = new FastBoard(player);
        board.updateTitle(title());
        boards.put(player.getUniqueId(), board);
        updateBoard(player, match);
    }

    public void updateBoard(Player player, Match match) {
        FastBoard board = boards.get(player.getUniqueId());
        if (board == null) return;
        if (board.isDeleted()) { boards.remove(player.getUniqueId()); return; }
        render(board, player, match, snapshot(match));
    }

    /** Updates every viewer (players and spectators) of the match; rankings computed once. */
    public void updateAll(Match match) {
        if (!enabled(match)) return;
        // scoreboard.update-interval is in ticks; callers tick once per second, so never go below 1s.
        long intervalMs = Math.max(1000L, ConfigManager.getInstance().getConfig()
                .getLong("scoreboard.update-interval", 20L) * 50L);
        long now = System.currentTimeMillis();
        Long last = lastUpdate.get(match);
        // 150ms tolerance for scheduler jitter so a steady 1s caller is never skipped.
        if (last != null && now - last < intervalMs - 150L) return;
        lastUpdate.put(match, now);
        Snapshot s = snapshot(match);
        List<UUID> viewers = new ArrayList<>(match.getPlayers());
        viewers.addAll(match.getSpectators());
        for (UUID uuid : viewers) {
            FastBoard board = boards.get(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (board == null || player == null) continue;
            if (board.isDeleted()) { boards.remove(uuid); continue; }
            render(board, player, match, s);
        }
    }

    public void removeBoard(Player player) {
        FastBoard board = boards.remove(player.getUniqueId());
        if (board != null && !board.isDeleted()) {
            try { board.delete(); } catch (Exception ignored) { }
        }
        restoreBukkitScoreboard(player.getUniqueId());
    }

    /**
     * FastBoard is packet-based: it never touches the server-side Bukkit scoreboard, but deleting
     * it removes the client's sidebar objective, which can leave a sidebar owned by another plugin
     * (TAB etc.) blank. Re-sending the player's current Bukkit scoreboard on the next tick makes
     * the client resync that state.
     */
    private void restoreBukkitScoreboard(UUID uuid) {
        try {
            com.omniarena.util.AsyncUtil.runSync(() -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) return;
                org.bukkit.scoreboard.Scoreboard sb = p.getScoreboard();
                p.setScoreboard(sb == null ? Bukkit.getScoreboardManager().getMainScoreboard() : sb);
            });
        } catch (Exception ignored) { /* plugin disabling: cannot schedule */ }
    }

    public void removeAllBoards(Match match) {
        List<UUID> all = new ArrayList<>(match.getPlayers());
        all.addAll(match.getSpectators());
        for (UUID uuid : all) {
            FastBoard board = boards.remove(uuid);
            if (board != null && !board.isDeleted()) {
                try { board.delete(); } catch (Exception ignored) { }
            }
            if (board != null) restoreBukkitScoreboard(uuid);
        }
        lastUpdate.remove(match);
    }

    /** Deletes every tracked board regardless of online state, then clears the map. */
    public void removeAllBoards() {
        for (FastBoard board : new ArrayList<>(boards.values())) {
            try { if (board != null && !board.isDeleted()) board.delete(); } catch (Exception ignored) { }
        }
        boards.clear();
        lastUpdate.clear();
    }

    private String title() {
        String t = ConfigManager.getInstance().getConfig().getString("scoreboard.title", DEFAULT_TITLE);
        return trim(ColorUtil.toLegacy(ColorUtil.parse(t)), 64);
    }

    private Snapshot snapshot(Match match) {
        Snapshot s = new Snapshot();
        s.arenaName = match.getArena().getName();
        s.mode = match.getArena().getGameMode().getDisplayName();
        s.waiting = match.getState() == MatchState.WAITING;
        s.countdown = match.getState() == MatchState.STARTING;
        s.time = formatTime(match.getState() == MatchState.RUNNING || match.getState() == MatchState.ENDING
                ? match.getTimeRemaining() : match.getArena().getMatchDuration());
        s.playerCount = match.getPlayerCount();
        s.alive = match.getAlivePlayers().size();
        s.goal = match.getArena().getGoalType();
        s.goalValue = match.getArena().getGoalValue();
        s.lives = match.getArena().getLives() > 0;
        List<UUID> ranked = match.getRankedPlayers();
        for (int i = 0; i < ranked.size(); i++) s.rank.put(ranked.get(i), i + 1);
        if (match.getArena().getTeamConfig().isEnabled()) s.teams.addAll(match.getTeams());
        return s;
    }

    private void render(FastBoard board, Player player, Match match, Snapshot s) {
        UUID uuid = player.getUniqueId();
        boolean spectator = match.getSpectators().contains(uuid);
        List<String> lines = new ArrayList<>();
        lines.add("§7§m          ");
        lines.add("§7Arena: §e" + s.arenaName);
        lines.add("§7Mode: §f" + s.mode);
        if (s.waiting) {
            lines.add("§7Status: §eWaiting");
        } else if (s.countdown) {
            lines.add("§7Starting in: §e" + Math.max(0, match.getCountdownSeconds()) + "s");
        } else {
            lines.add("§7Time: §f" + s.time);
        }
        switch (s.goal) {
            case KILLS -> lines.add("§7Goal: §f" + s.goalValue + " kills");
            case LAST_ALIVE -> lines.add("§7Alive: §f" + s.alive + "§7/" + s.playerCount);
            case OBJECTIVE -> lines.add("§7Goal: §fObjective");
            default -> { }
        }
        lines.add("§7§m          ");

        if (spectator) {
            lines.add("§7Spectating");
            lines.add("§7Players: §f" + s.playerCount);
        } else {
            int kills = match.getKills(uuid);
            int deaths = match.getDeaths(uuid);
            lines.add("§7Kills: §a" + kills);
            lines.add("§7Deaths: §c" + deaths);
            if (s.lives) lines.add("§7Lives: §c" + match.getLives(uuid));
            int streak = match.getStreak(uuid);
            if (streak > 0) lines.add("§7Streak: §6" + streak);
            MatchTeam team = match.getTeam(uuid);
            if (team != null) {
                lines.add("§7Team: " + ColorUtil.toLegacy(ColorUtil.parse(
                        team.getColor().getMiniMessageTag() + team.getName())));
                lines.add("§7Team Score: §f" + team.getScore());
            }
            Integer rank = s.rank.get(uuid);
            if (rank != null) lines.add("§7Rank: §d#" + rank + " §8/ " + s.playerCount);
            if (match.isRanked() && com.omniarena.rating.EloManager.getInstance() != null) {
                lines.add("§7ELO: §b" + com.omniarena.rating.EloManager.getInstance().peekElo(uuid));
            }
        }

        // Always keep the footer; drop optional middle lines if over the limit.
        lines.add("§7§m          ");
        while (lines.size() > MAX_LINES) lines.remove(lines.size() - 2);
        lines.replaceAll(l -> trim(l, MAX_LINE_LEN));
        board.updateLines(lines);
    }

    /** Truncates without leaving a dangling section sign. */
    private static String trim(String s, int max) {
        if (s.length() <= max) return s;
        String t = s.substring(0, max);
        return t.endsWith("§") ? t.substring(0, t.length() - 1) : t;
    }

    static String formatTime(int seconds) {
        int s = Math.max(0, seconds);
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
