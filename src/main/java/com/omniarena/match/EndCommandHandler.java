package com.omniarena.match;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Executes configurable end-match commands.
 * Supports placeholders: {winner}, {loser}, {arena}, {mode}, {winner_kills}, {duration}
 * Prefix "console:" → run as console. Prefix "player:" → run as winner player.
 */
public class EndCommandHandler {

    private final OmniArena plugin;

    public EndCommandHandler(OmniArena plugin) {
        this.plugin = plugin;
    }

    public void execute(Match match, MatchResult result) {
        List<String> commands = getCommands(match);
        if (commands.isEmpty()) return;

        String winnerName  = resolveWinnerName(result);
        String loserName   = resolveLoserName(match, result);
        String arenaName   = match.getArena().getName();
        String modeName    = match.getArena().getGameMode().name();
        String duration    = String.valueOf(result.getDurationSeconds());
        String winKills    = winnerName.isEmpty() ? "0" : String.valueOf(match.getKills(resolveWinnerId(result)));

        for (String raw : commands) {
            String cmd = raw
                    .replace("{winner}", winnerName)
                    .replace("{loser}", loserName)
                    .replace("{arena}", arenaName)
                    .replace("{mode}", modeName)
                    .replace("{duration}", duration)
                    .replace("{winner_kills}", winKills);

            if (cmd.startsWith("player:") && result.getWinnerId() != null) {
                Player winner = Bukkit.getPlayer(result.getWinnerId());
                if (winner != null) {
                    String playerCmd = cmd.substring("player:".length()).trim();
                    winner.performCommand(playerCmd);
                }
            } else {
                String consoleCmd = cmd.startsWith("console:") ? cmd.substring("console:".length()).trim() : cmd;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCmd);
            }
        }
    }

    private List<String> getCommands(Match match) {
        List<String> arenaCommands = match.getArena().getEndCommands();
        if (!arenaCommands.isEmpty()) return arenaCommands;
        return ConfigManager.getInstance().getConfig().getStringList("match.end-commands");
    }

    private String resolveWinnerName(MatchResult result) {
        if (result.getWinnerTeamName() != null) return result.getWinnerTeamName();
        if (result.getWinnerId() != null) {
            Player p = Bukkit.getPlayer(result.getWinnerId());
            return p != null ? p.getName() : "Unknown";
        }
        return "Draw";
    }

    private String resolveLoserName(Match match, MatchResult result) {
        if (result.isDraw()) return "N/A";
        UUID winnerId = result.getWinnerId();
        if (winnerId == null) return "N/A";
        return match.getPlayers().stream()
                .filter(u -> !u.equals(winnerId))
                .map(u -> { Player p = Bukkit.getPlayer(u); return p != null ? p.getName() : "Unknown"; })
                .findFirst().orElse("N/A");
    }

    private UUID resolveWinnerId(MatchResult result) {
        return result.getWinnerId() != null ? result.getWinnerId() : new UUID(0, 0);
    }
}
