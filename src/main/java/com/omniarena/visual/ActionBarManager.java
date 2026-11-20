package com.omniarena.visual;

import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class ActionBarManager {

    private boolean enabled(Match match) {
        return match.getArena().getVisual().isActionBar()
                && ConfigManager.getInstance().getConfig().getBoolean("visuals.action-bar", true);
    }

    /** Called once per second by the match timer (content is per-second); rankings computed once. */
    public void updateAll(Match match) {
        if (!enabled(match)) return;
        List<UUID> ranked = match.getRankedPlayers();
        Map<UUID, Integer> rank = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) rank.put(ranked.get(i), i + 1);
        for (UUID uuid : new ArrayList<>(match.getPlayers())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            send(player, match, rank.getOrDefault(uuid, 0));
        }
    }

    public void update(Player player, Match match) {
        if (!enabled(match)) return;
        send(player, match, match.getRankedPlayers().indexOf(player.getUniqueId()) + 1);
    }

    private void send(Player player, Match match, int rank) {
        UUID uuid = player.getUniqueId();
        StringBuilder bar = new StringBuilder();

        // Spawn protection
        if (match.hasSpawnProtection(uuid)) {
            bar.append("<yellow>Spawn Protection Active ");
        }

        // Combo
        int combo = match.getCombo(uuid);
        if (combo > 1) {
            bar.append("<gold>x").append(combo).append(" Combo! ");
        }

        // Streak
        int streak = match.getStreak(uuid);
        if (streak >= 3) {
            bar.append("<red>").append(streak).append("x Streak ");
        }

        // Lives (only for running matches on arenas with lives enabled)
        if (match.getState() == com.omniarena.match.MatchState.RUNNING && match.getArena().getLives() > 0) {
            int lives = match.getLives(uuid);
            bar.append("<red>\u2764 ").append(lives).append(lives == 1 ? " life " : " lives ");
        }

        // Rank
        bar.append("<gray>Rank: <white>#").append(rank);

        if (!bar.isEmpty()) {
            MessageUtil.sendActionBar(player, bar.toString());
        }
    }

    public void sendObjectiveHint(Player player, String hint) {
        MessageUtil.sendActionBar(player, "<yellow>Objective: <white>" + hint);
    }

    public void sendComboBroken(Player player) {
        MessageUtil.sendActionBar(player, "<red>Combo Broken!");
    }

    public void sendSpawnProtectionExpired(Player player) {
        MessageUtil.sendActionBar(player, "<red>Spawn protection ended!");
    }
}
