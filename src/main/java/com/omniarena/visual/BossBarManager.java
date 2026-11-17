package com.omniarena.visual;

import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.util.ColorUtil;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarManager {

    private final Map<String, BossBar> matchBars = new HashMap<>();

    public void createTimer(Match match) {
        BossBar bar = BossBar.bossBar(
                ColorUtil.parse("<green>Time Remaining: " + formatTime(match.getArena().getMatchDuration())),
                1.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS
        );
        matchBars.put(match.getArena().getName(), bar);

        for (UUID uuid : match.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.showBossBar(bar);
        }
    }

    public void updateTimer(Match match) {
        BossBar bar = matchBars.get(match.getArena().getName());
        if (bar == null) return;

        int totalSeconds = match.getArena().getMatchDuration();
        int remaining = match.getTimeRemaining();
        float progress = (float) remaining / totalSeconds;

        int lowThreshold = ConfigManager.getInstance().getConfig()
                .getInt("boss-bar.low-time-threshold", 60);

        BossBar.Color color = remaining <= lowThreshold ? BossBar.Color.RED : BossBar.Color.GREEN;
        bar.color(color);
        bar.progress(Math.max(0, Math.min(1, progress)));
        bar.name(ColorUtil.parse((remaining <= lowThreshold ? "<red>" : "<green>") +
                "Time Remaining: " + formatTime(remaining)));
    }

    public void addPlayer(Player player, Match match) {
        BossBar bar = matchBars.get(match.getArena().getName());
        if (bar != null) player.showBossBar(bar);
    }

    public void removePlayer(Player player, Match match) {
        BossBar bar = matchBars.get(match.getArena().getName());
        if (bar != null) player.hideBossBar(bar);
    }

    public void removeAll(Match match) {
        BossBar bar = matchBars.remove(match.getArena().getName());
        if (bar == null) return;
        for (UUID uuid : match.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.hideBossBar(bar);
        }
    }

    /** Hides every tracked bar from all online players, then clears the map. */
    public void removeAll() {
        for (BossBar bar : new java.util.ArrayList<>(matchBars.values())) {
            for (Player player : Bukkit.getOnlinePlayers()) player.hideBossBar(bar);
        }
        matchBars.clear();
    }

    /** Hides all tracked bars from one player (e.g. on quit). */
    public void removePlayer(Player player) {
        for (BossBar bar : matchBars.values()) player.hideBossBar(bar);
    }

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
