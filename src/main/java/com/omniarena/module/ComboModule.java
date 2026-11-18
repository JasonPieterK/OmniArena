package com.omniarena.module;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ComboModule {

    private final OmniArena plugin;
    private final Map<UUID, BukkitTask> comboResetTasks = new HashMap<>();
    private static final int COMBO_WINDOW_TICKS = 100; // 5 seconds to chain kills

    public ComboModule(OmniArena plugin) {
        this.plugin = plugin;
    }

    public void handleKill(Player killer, Match match) {
        UUID uid = killer.getUniqueId();
        if (!match.getArena().hasModule("combo")) {
            // Module off: nothing would ever reset the counter the action bar displays
            match.resetCombo(uid);
            return;
        }
        int combo = match.getCombo(uid);

        if (combo > 1) {
            String msg = ConfigManager.getInstance().getMessage("combo-counter")
                    .replace("{combo}", String.valueOf(combo));
            MessageUtil.send(killer, msg);
            plugin.getVisual().getActionBarManager().update(killer, match);
        }

        // Reset or restart the combo-break timer
        BukkitTask existing = comboResetTasks.remove(uid);
        if (existing != null) existing.cancel();

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match.getCombo(uid) > 1 && killer.isOnline()) {
                plugin.getVisual().getActionBarManager().sendComboBroken(killer);
            }
            match.resetCombo(uid);
            comboResetTasks.remove(uid);
        }, COMBO_WINDOW_TICKS);

        comboResetTasks.put(uid, task);
    }

    /** The player died or left: their chain is over and no reset timer should linger. */
    public void clear(UUID uid) {
        BukkitTask t = comboResetTasks.remove(uid);
        if (t != null) t.cancel();
    }

    public void cancelAll() {
        comboResetTasks.values().forEach(BukkitTask::cancel);
        comboResetTasks.clear();
    }
}
