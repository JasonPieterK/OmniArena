package com.omniarena.visual;

import com.omniarena.match.Match;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ActionBarManager {

    public void updateAll(Match match) {
        for (UUID uuid : match.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            update(player, match);
        }
    }

    public void update(Player player, Match match) {
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

        // Rank
        int rank = match.getRankedPlayers().indexOf(uuid) + 1;
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
