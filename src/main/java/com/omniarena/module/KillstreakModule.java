package com.omniarena.module;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.TreeMap;

public class KillstreakModule {

    private static KillstreakModule instance;

    private final OmniArena plugin;
    private final TreeMap<Integer, String> milestones = new TreeMap<>();

    public KillstreakModule(OmniArena plugin) {
        this.plugin = plugin;
        instance = this;
        loadMilestones();
    }

    public static KillstreakModule getInstance() { return instance; }

    private void loadMilestones() {
        milestones.clear();
        ConfigurationSection section = ConfigManager.getInstance().getConfig()
                .getConfigurationSection("killstreak.milestones");
        if (section == null) {
            milestones.put(5,  "KILLING SPREE");
            milestones.put(10, "DOMINATING");
            milestones.put(15, "UNSTOPPABLE");
            milestones.put(20, "GODLIKE");
        } else {
            for (String key : section.getKeys(false)) {
                try { milestones.put(Integer.parseInt(key), section.getString(key)); }
                catch (NumberFormatException ignored) {}
            }
        }
    }

    public void handleKill(Player killer, Match match) {
        if (!match.getArena().hasModule("killstreak")) return;

        int streak = match.getStreak(killer.getUniqueId());
        String title = getTitle(streak);

        if (title != null) {
            // Notify killer
            plugin.getVisual().getTitleManager().sendKillstreakTitle(killer, streak, title);
            plugin.getVisual().getSoundManager().playKillstreak(killer, streak);

            // Broadcast to arena
            String broadcastMsg = ConfigManager.getInstance().getMessage("killstreak-broadcast")
                    .replace("{player}", killer.getName())
                    .replace("{streak}", String.valueOf(streak))
                    .replace("{title}", title);

            for (java.util.UUID uid : match.getPlayers()) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null && !p.equals(killer)) MessageUtil.send(p, broadcastMsg);
            }

            // Trail effect every 5 kills
            plugin.getVisual().getEffectManager().playKillstreakTrail(killer);
        }

        // Notify self of streak
        String selfMsg = ConfigManager.getInstance().getMessage("killstreak-self")
                .replace("{streak}", String.valueOf(streak))
                .replace("{title}", title != null ? title : "");
        if (title != null) MessageUtil.send(killer, selfMsg);
    }

    public void handleStreakBroken(Player breaker, Player streakHolder, int streak) {
        String msg = ConfigManager.getInstance().getMessage("killstreak-broken")
                .replace("{player}", streakHolder.getName())
                .replace("{streak}", String.valueOf(streak));
        MessageUtil.send(breaker, msg);
    }

    private String getTitle(int streak) {
        String last = null;
        for (java.util.Map.Entry<Integer, String> entry : milestones.entrySet()) {
            if (streak >= entry.getKey()) last = entry.getValue();
        }
        // Only show on exact milestone
        return milestones.containsKey(streak) ? milestones.get(streak) : null;
    }

    public void reload() { loadMilestones(); }
}
