package com.omniarena.integration;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
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

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        PlayerStats stats = StatsManager.getInstance().getStats(player.getUniqueId());
        String key = params.toLowerCase();

        // ── Lifetime stats (work anywhere — good for TAB tablist/nametags) ──
        switch (key) {
            case "kills":       return String.valueOf(stats.getKills());
            case "deaths":      return String.valueOf(stats.getDeaths());
            case "wins":        return String.valueOf(stats.getWins());
            case "losses":      return String.valueOf(stats.getLosses());
            case "kd":          return String.valueOf(stats.getKDRatio());
            case "winrate":     return stats.getWinRate() + "%";
            case "time_played": return stats.getFormattedTimePlayed();
        }

        // ── Live match context (the rest need an online player in a match) ──
        org.bukkit.entity.Player online = player.getPlayer();
        MatchManager mm = MatchManager.getInstance();
        boolean inArena = online != null && mm.isInArena(online);

        if (key.equals("in_arena")) return String.valueOf(inArena);
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
            case "goal":          return arena.getGameMode().getGoalType().name();
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
}
