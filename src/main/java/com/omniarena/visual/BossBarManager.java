package com.omniarena.visual;

import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchState;
import com.omniarena.util.ColorUtil;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** One boss bar per match (main thread only), shown to players and spectators. */
public class BossBarManager {

    private final Map<Match, BossBar> matchBars = new IdentityHashMap<>();

    private BossBar.Color color(String key, BossBar.Color fallback) {
        String raw = ConfigManager.getInstance().getConfig().getString(key, fallback.name());
        if (raw != null) {
            try {
                return BossBar.Color.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) { /* warn below */ }
        }
        if (warnedColors.add(key + "=" + raw)) {
            Bukkit.getLogger().warning("[OmniArena] Invalid boss bar color '" + raw + "' for " + key
                    + "; using " + fallback.name() + ". Valid options: "
                    + java.util.Arrays.toString(BossBar.Color.values()));
        }
        return fallback;
    }

    private final java.util.Set<String> warnedColors = new java.util.HashSet<>();

    private boolean enabled(Match match) {
        return match.getArena().getVisual().isBossBar()
                && ConfigManager.getInstance().getConfig().getBoolean("visual.boss-bar", true);
    }

    private BossBar getOrCreate(Match match) {
        BossBar bar = matchBars.get(match);
        if (bar == null) {
            bar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(), 1.0f,
                    BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
            matchBars.put(match, bar);
            updateTimer(match);
        }
        return bar;
    }

    /** Creates (or reuses) the match's bar and shows it to every current viewer. */
    public void createTimer(Match match) {
        if (!enabled(match)) return;
        BossBar bar = getOrCreate(match);
        updateTimer(match);
        for (UUID uuid : match.getPlayers()) show(uuid, bar);
        for (UUID uuid : match.getSpectators()) show(uuid, bar);
    }

    private void show(UUID uuid, BossBar bar) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) com.omniarena.util.MessageUtil.showBossBar(p, bar); // idempotent for the same bar instance
    }

    public void updateTimer(Match match) {
        BossBar bar = matchBars.get(match);
        if (bar == null) return;

        MatchState state = match.getState();
        if (state == MatchState.WAITING) {
            bar.name(ColorUtil.parse("<yellow>Waiting for players..."));
            bar.color(color("boss-bar.waiting-color", BossBar.Color.YELLOW));
            bar.progress(1.0f);
        } else if (state == MatchState.STARTING) {
            int total = Math.max(1, match.getArena().getCountdown());
            int left = Math.max(0, Math.min(total, match.getCountdownSeconds()));
            bar.name(ColorUtil.parse("<yellow>Starting in " + left + "s"));
            bar.color(color("boss-bar.waiting-color", BossBar.Color.YELLOW));
            bar.progress(clamp((float) left / total));
        } else if (state == MatchState.RUNNING) {
            int total = Math.max(1, match.getArena().getMatchDuration());
            int remaining = Math.max(0, match.getTimeRemaining());
            int lowThreshold = ConfigManager.getInstance().getConfig()
                    .getInt("boss-bar.low-time-threshold", 60);
            boolean low = remaining <= lowThreshold;
            bar.color(low ? color("boss-bar.low-time-color", BossBar.Color.RED)
                    : color("boss-bar.timer-color", BossBar.Color.GREEN));
            bar.progress(clamp((float) remaining / total));
            bar.name(ColorUtil.parse((low ? "<red>" : "<green>") + "Time Remaining: " + formatTime(remaining)));
        } else {
            bar.name(ColorUtil.parse("<gold>Match Over"));
            bar.color(color("boss-bar.ending-color", BossBar.Color.PURPLE));
            bar.progress(1.0f);
        }
    }

    private static float clamp(float f) {
        if (Float.isNaN(f)) return 0f;
        return Math.max(0f, Math.min(1f, f));
    }

    /** Shows the match bar to a joining player/spectator (creating it if needed). */
    public void addPlayer(Player player, Match match) {
        if (!enabled(match)) return;
        com.omniarena.util.MessageUtil.showBossBar(player, getOrCreate(match));
    }

    public void removePlayer(Player player, Match match) {
        BossBar bar = matchBars.get(match);
        if (bar != null) com.omniarena.util.MessageUtil.hideBossBar(player, bar);
    }

    public void removeAll(Match match) {
        BossBar bar = matchBars.remove(match);
        if (bar == null) return;
        for (UUID uuid : new ArrayList<>(match.getPlayers())) hide(uuid, bar);
        for (UUID uuid : new ArrayList<>(match.getSpectators())) hide(uuid, bar);
        for (Player p : Bukkit.getOnlinePlayers()) com.omniarena.util.MessageUtil.hideBossBar(p, bar); // anyone who already left
    }

    private void hide(UUID uuid, BossBar bar) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) com.omniarena.util.MessageUtil.hideBossBar(p, bar);
    }

    /** Hides every tracked bar from all online players, then clears the map. */
    public void removeAll() {
        for (BossBar bar : new ArrayList<>(matchBars.values())) {
            for (Player player : Bukkit.getOnlinePlayers()) com.omniarena.util.MessageUtil.hideBossBar(player, bar);
        }
        matchBars.clear();
    }

    /** Hides all tracked bars from one player (e.g. on quit/leave). */
    public void removePlayer(Player player) {
        for (BossBar bar : matchBars.values()) com.omniarena.util.MessageUtil.hideBossBar(player, bar);
    }

    private static String formatTime(int seconds) {
        int s = Math.max(0, seconds);
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
