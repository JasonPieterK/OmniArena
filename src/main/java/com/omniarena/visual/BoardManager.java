package com.omniarena.visual;

import com.omniarena.match.Match;
import com.omniarena.match.MatchTeam;
import com.omniarena.util.ColorUtil;
import fr.mrmicky.fastboard.FastBoard;
import org.bukkit.entity.Player;

import java.util.*;

public class BoardManager {

    private final Map<UUID, FastBoard> boards = new HashMap<>();

    public void createBoard(Player player, Match match) {
        removeBoard(player);
        FastBoard board = new FastBoard(player);
        board.updateTitle(ColorUtil.toLegacy(ColorUtil.parse(
                "<gradient:#ff6b35:#f7c59f>OmniArena</gradient>")));
        boards.put(player.getUniqueId(), board);
        updateBoard(player, match);
    }

    public void updateBoard(Player player, Match match) {
        FastBoard board = boards.get(player.getUniqueId());
        if (board == null) return;

        List<String> lines = buildLines(player, match);
        board.updateLines(lines);
    }

    public void updateAll(Match match) {
        for (UUID uuid : match.getPlayers()) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) updateBoard(player, match);
        }
    }

    public void removeBoard(Player player) {
        FastBoard board = boards.remove(player.getUniqueId());
        if (board != null && !board.isDeleted()) board.delete();
    }

    public void removeAllBoards(Match match) {
        for (UUID uuid : match.getPlayers()) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) removeBoard(player);
        }
    }

    /** Deletes every tracked board regardless of online state, then clears the map. */
    public void removeAllBoards() {
        for (FastBoard board : new ArrayList<>(boards.values())) {
            try { if (board != null && !board.isDeleted()) board.delete(); } catch (Exception ignored) { }
        }
        boards.clear();
    }

    private List<String> buildLines(Player player, Match match) {
        List<String> lines = new ArrayList<>();
        UUID uuid = player.getUniqueId();
        String arenaName = match.getArena().getName();
        int timeLeft = match.getTimeRemaining();
        int kills = match.getKills(uuid);
        int deaths = match.getDeaths(uuid);

        lines.add("§7§m          ");
        lines.add("§7Arena: §e" + arenaName);
        lines.add("§7Mode: §f" + match.getArena().getGameMode().getDisplayName());
        lines.add("§7§m          ");
        lines.add("§7Time: §f" + formatTime(timeLeft));
        lines.add("§7Kills: §a" + kills);
        lines.add("§7Deaths: §c" + deaths);
        lines.add("§7K/D: §f" + (deaths == 0 ? kills : Math.round((double) kills / deaths * 100.0) / 100.0));

        // Streak
        int streak = match.getStreak(uuid);
        if (streak > 0) {
            lines.add("§7Streak: §6" + streak);
        }

        // Team
        MatchTeam team = match.getTeam(uuid);
        if (team != null) {
            lines.add("§7Team: " + ColorUtil.toLegacy(ColorUtil.parse(team.getColor().getMiniMessageTag())) + team.getName());
            lines.add("§7Team Kills: §f" + team.getKills());
        }

        // Ranking
        List<UUID> ranked = match.getRankedPlayers();
        int rank = ranked.indexOf(uuid) + 1;
        lines.add("§7Rank: §d#" + rank + " §8/ " + match.getPlayerCount());

        lines.add("§7§m          ");
        lines.add("§8omniarena.dev");

        return lines;
    }

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
