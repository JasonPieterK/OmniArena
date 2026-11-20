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
                .getConfigurationSection("visuals.killstreak.milestones");
        if (section == null) {
            milestones.put(5,  "KILLING SPREE");
            milestones.put(10, "DOMINATING");
            milestones.put(15, "UNSTOPPABLE");
            milestones.put(20, "GODLIKE");
        } else {
            for (String key : section.getKeys(false)) {
                try { String v = section.getString(key); if (v != null) milestones.put(Integer.parseInt(key), v); }
                catch (NumberFormatException ignored) {}
            }
        }
    }

    private void playConfiguredSound(Player p, int streak, String name) {
        if (!com.omniarena.visual.VisualGate.sounds(p)) return;
        org.bukkit.Sound sound;
        try { sound = org.bukkit.Sound.valueOf(name.toUpperCase()); }
        catch (Exception e) { sound = org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER; }
        p.playSound(p.getLocation(), sound, 0.6f, Math.min(2.0f, 0.8f + (streak * 0.1f)));
    }

    private void spawnConfiguredParticle(Player p, String name) {
        if (!com.omniarena.visual.VisualGate.particles(p)) return;
        org.bukkit.Particle particle;
        try { particle = org.bukkit.Particle.valueOf(name.toUpperCase()); }
        catch (Exception e) { particle = org.bukkit.Particle.FLAME; }
        p.getWorld().spawnParticle(particle, p.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
    }

    public void handleKill(Player killer, Match match) {
        if (!match.getArena().hasModule("killstreak")) return;

        int streak = match.getStreak(killer.getUniqueId());
        String title = getTitle(streak);

        if (title != null) {
            // Notify killer
            var cfg = ConfigManager.getInstance().getConfig();
            if (cfg.getBoolean("visuals.killstreak-titles", true)) {
                plugin.getVisual().getTitleManager().sendKillstreakTitle(killer, streak, title);
            }
            playConfiguredSound(killer, streak, cfg.getString("visuals.killstreak.sound", "ENTITY_LIGHTNING_BOLT_THUNDER"));

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
            spawnConfiguredParticle(killer, ConfigManager.getInstance().getConfig().getString("visuals.killstreak.particle", "FLAME"));
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
        // Only show on exact milestone
        return milestones.get(streak);
    }

    public void reload() { loadMilestones(); }
}
