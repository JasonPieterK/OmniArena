package com.omniarena.util;

import com.omniarena.OmniArena;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

public final class AsyncUtil {

    private AsyncUtil() {}

    public static BukkitTask runAsync(Runnable task) {
        if (OmniArena.getInstance() == null || !OmniArena.getInstance().isEnabled()) {
            return null;
        }
        return Bukkit.getScheduler().runTaskAsynchronously(OmniArena.getInstance(), task);
    }

    public static BukkitTask runSync(Runnable task) {
        if (OmniArena.getInstance() == null || !OmniArena.getInstance().isEnabled()) {
            return null;
        }
        return Bukkit.getScheduler().runTask(OmniArena.getInstance(), task);
    }

    public static BukkitTask runSyncLater(Runnable task, long delayTicks) {
        if (OmniArena.getInstance() == null || !OmniArena.getInstance().isEnabled()) {
            return null;
        }
        return Bukkit.getScheduler().runTaskLater(OmniArena.getInstance(), task, delayTicks);
    }

    public static BukkitTask runSyncRepeating(Runnable task, long delayTicks, long periodTicks) {
        if (OmniArena.getInstance() == null || !OmniArena.getInstance().isEnabled()) {
            return null;
        }
        return Bukkit.getScheduler().runTaskTimer(OmniArena.getInstance(), task, delayTicks, periodTicks);
    }

    public static BukkitTask runAsyncRepeating(Runnable task, long delayTicks, long periodTicks) {
        if (OmniArena.getInstance() == null || !OmniArena.getInstance().isEnabled()) {
            return null;
        }
        return Bukkit.getScheduler().runTaskTimerAsynchronously(OmniArena.getInstance(), task, delayTicks, periodTicks);
    }

    public static <T> void runAsyncThenSync(java.util.concurrent.Callable<T> asyncWork, Consumer<T> syncConsumer) {
        if (OmniArena.getInstance() == null || !OmniArena.getInstance().isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(OmniArena.getInstance(), () -> {
            try {
                T result = asyncWork.call();
                if (OmniArena.getInstance() != null && OmniArena.getInstance().isEnabled()) {
                    Bukkit.getScheduler().runTask(OmniArena.getInstance(), () -> {
                        try {
                            syncConsumer.accept(result);
                        } catch (Exception e) {
                            OmniArena.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "A background task finished but its main-thread follow-up failed (" + e + "). Full trace below; please report it with your plugin version.", e);
                        }
                    });
                }
            } catch (Exception e) {
                OmniArena.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "A background (async) task failed (" + e + "). Full trace below; the action was not completed. Please report it with your plugin version.", e);
            }
        });
    }
}
