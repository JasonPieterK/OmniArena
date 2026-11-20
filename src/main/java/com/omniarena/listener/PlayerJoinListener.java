package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.stats.StatsManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final OmniArena plugin;

    public PlayerJoinListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    /** Self-healing: tells admins once per join that repairs happened since the last restart. */
    private void notifyAdminOfRepairs(org.bukkit.entity.Player player) {
        com.omniarena.health.ErrorGuard.run("join-notice", () -> {
            var cfg = com.omniarena.config.ConfigManager.getInstance().getConfig();
            if (!cfg.getBoolean("health.enabled", true) || !cfg.getBoolean("health.notify-admins", true)) return;
            int fixed = com.omniarena.health.HealthReport.get().repairCount();
            if (fixed <= 0 || !com.omniarena.permission.PermissionChecker.hasAdmin(player,
                    com.omniarena.permission.PermissionChecker.ADMIN_DOCTOR)) return;
            com.omniarena.util.MessageUtil.send(player, com.omniarena.config.ConfigManager.getInstance()
                    .getMessage("health-admin-notice").replace("{count}", String.valueOf(fixed)));
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Pre-load stats cache for this player
        StatsManager.getInstance().getStats(event.getPlayer().getUniqueId());
        notifyAdminOfRepairs(event.getPlayer());

        // Crash recovery: a leftover pre-arena state file means the server died mid-match
        final org.bukkit.entity.Player player = event.getPlayer();
        com.omniarena.match.MatchManager mm = com.omniarena.match.MatchManager.getInstance();
        if (mm == null || !mm.getStateStore().exists(player.getUniqueId())) return;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || plugin.isStopped()) return;
            if (mm.isInArena(player)) return;
            if (mm.getStateStore().restore(player)) {
                com.omniarena.util.MessageUtil.send(player,
                        "<yellow>The server restarted while you were in an arena. <green>Your previous inventory and location were restored.");
            }
        });
    }
}
