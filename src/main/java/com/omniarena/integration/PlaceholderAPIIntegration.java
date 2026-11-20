package com.omniarena.integration;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.rating.EloManager;
import com.omniarena.rating.EloRating;
import com.omniarena.stats.PlayerStats;
import com.omniarena.stats.StatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class PlaceholderAPIIntegration extends PlaceholderExpansion {

    private final OmniArena plugin;

    public PlaceholderAPIIntegration(OmniArena plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "omniarena"; }
    @Override public @NotNull String getAuthor()     { return "OmniArena"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    /**
     * Every request runs through the integration wrapper: an exception is logged once, counted, and after
     * 3 failures in a row the expansion is unregistered instead of failing for PlaceholderAPI's callers.
     */
    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        IntegrationManager im = plugin.getIntegrationManager();
        if (im == null) return resolve(player, params);
        return im.safe("placeholderapi", () -> resolve(player, params), null);
    }

    /**
     * Resolves one placeholder. Everything here is read-only and cheap: lifetime values come from
     * {@code peekStats}/{@code peekElo} (never creating entries), the leaderboards and ELO rank from the
     * snapshot {@link LeaderboardCache} that is refreshed about every 30 s on the main thread.
     */
    @Nullable String resolve(OfflinePlayer player, String params) {
        String key = params.toLowerCase(java.util.Locale.ROOT);

        // ── Leaderboards: %omniarena_top_<kills|wins|kd|elo>_<1-10>_<name|value>% (no player needed) ──
        var lb = LeaderboardPlaceholder.parse(key);
        if (lb.isPresent()) {
            IntegrationManager im = plugin.getIntegrationManager();
            if (im == null) return LeaderboardCache.EMPTY_NAME;
            LeaderboardPlaceholder p = lb.get();
            return p.name() ? im.leaderboardCache().name(p.metric(), p.position())
                            : im.leaderboardCache().value(p.metric(), p.position());
        }

        if (player == null) return "";

        PlayerStats stats = StatsManager.getInstance().peekStats(player.getUniqueId());

        // ── Lifetime stats (work anywhere — good for TAB tablist/nametags) ──
        switch (key) {
            case "kills":       return String.valueOf(stats.getKills());
            case "deaths":      return String.valueOf(stats.getDeaths());
            case "wins":        return String.valueOf(stats.getWins());
            case "losses":      return String.valueOf(stats.getLosses());
            case "kd":          return String.valueOf(stats.getKDRatio());
            case "winrate":     return stats.getWinRate() + "%";
            case "time_played": return stats.getFormattedTimePlayed();
            case "elo": {
                EloManager em = EloManager.getInstance();
                return String.valueOf(em != null ? em.peekElo(player.getUniqueId()) : EloRating.DEFAULT_RATING);
            }
            case "rank": {
                IntegrationManager im = plugin.getIntegrationManager();
                int rank = im != null ? im.leaderboardCache().eloRank(player.getUniqueId()) : -1;
                return rank > 0 ? String.valueOf(rank) : "-";
            }
        }

        // ── Live match context (the rest need an online player in a match) ──
        org.bukkit.entity.Player online = player.getPlayer();
        MatchManager mm = MatchManager.getInstance();
        boolean inArena = online != null && mm.isInArena(online);

        if (key.equals("in_arena")) return String.valueOf(inArena);
        if (key.equals("in_match")) {
            // true only while a match is actually underway (not in the lobby, not spectating)
            if (!inArena || mm.isSpectating(online)) return "false";
            return String.valueOf(mm.getArenaName(online).flatMap(n -> ArenaManager.getInstance().get(n))
                    .flatMap(mm::getMatch).map(m -> m.getState() == com.omniarena.match.MatchState.RUNNING).orElse(false));
        }
        if (key.equals("arena_name") || key.equals("arena")) {
            return inArena ? mm.getArenaName(online).orElse("N/A") : "N/A";
        }

        if (!inArena) {
            // Sensible blanks so TAB lines collapse instead of showing nulls.
            return switch (key) {
                case "gamemode", "gamemode_name", "gamemode_desc", "state",
                     "team", "team_color" -> "";
                case "arena_kills", "arena_deaths", "alive", "players",
                     "max_players", "goal", "time_left", "time_left_seconds" -> "0";
                default -> null;
            };
        }

        Optional<Arena> arenaOpt = mm.getArenaName(online).flatMap(n -> ArenaManager.getInstance().get(n));
        if (arenaOpt.isEmpty()) return "";
        Arena arena = arenaOpt.get();
        Optional<Match> matchOpt = mm.getMatch(arena);

        switch (key) {
            case "gamemode":      return arena.getGameMode().name();
            case "gamemode_name": return arena.getGameMode().getDisplayName();
            case "gamemode_desc": return arena.getGameMode().getDescription();
            case "goal":          return arena.getGoalType().name();
        }
        if (matchOpt.isEmpty()) return "";
        Match match = matchOpt.get();

        return switch (key) {
            case "state"             -> match.getState().name();
            case "arena_kills"       -> String.valueOf(match.getKills(player.getUniqueId()));
            case "arena_deaths"      -> String.valueOf(match.getDeaths(player.getUniqueId()));
            case "arena_streak"      -> String.valueOf(match.getStreak(player.getUniqueId()));
            case "alive"             -> String.valueOf(match.getAlivePlayers().size());
            case "players"           -> String.valueOf(match.getPlayerCount());
            case "max_players"       -> String.valueOf(arena.getMaxPlayers());
            case "time_left"         -> formatTime(match.getTimeRemaining());
            case "time_left_seconds" -> String.valueOf(match.getTimeRemaining());
            case "team" -> {
                var t = match.getTeam(player.getUniqueId());
                yield t != null ? t.getName() : "";
            }
            case "team_color" -> {
                var t = match.getTeam(player.getUniqueId());
                yield t != null ? t.getColor().name() : "";
            }
            default -> null;
        };
    }

    private String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public boolean tryRegister() {
        return super.register();
    }

    /** Unregisters this expansion from PlaceholderAPI (safe to call more than once). */
    public void unregisterExpansion() {
        try {
            if (isRegistered()) unregister();
        } catch (Throwable t) {
            plugin.getLogger().warning("[Integrations] Failed to unregister PlaceholderAPI expansion: " + t.getMessage());
        }
    }
}
