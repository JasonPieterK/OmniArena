package com.omniarena.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Multiverse-Core hook (reflection only): loads a world that exists on disk but is not loaded yet, so
 * arenas in such worlds can be teleported to / set up on demand. Tries the v5 API, then the v4 API,
 * then falls back to the {@code mv load} console command. Success is always verified with
 * {@code Bukkit.getWorld(name) != null}.
 */
public final class MultiverseIntegration {

    private final Plugin multiverse;

    public MultiverseIntegration(Plugin multiverse) {
        this.multiverse = multiverse;
    }

    /** Attempts to load the world; returns true when it is loaded afterwards. Main thread. */
    public boolean loadWorld(String worldName) {
        if (Bukkit.getWorld(worldName) != null) return true;
        ClassLoader cl = multiverse.getClass().getClassLoader();

        // Multiverse-Core 5.x: MultiverseCoreApi.get().getWorldManager().loadWorld(String)
        try {
            Class<?> api = Reflect.load("org.mvplugins.multiverse.core.MultiverseCoreApi", cl);
            Object core = Reflect.invokeStatic(api, "get");
            Object worldManager = Reflect.invoke(core, "getWorldManager");
            Reflect.invoke(worldManager, "loadWorld", worldName);
            if (Bukkit.getWorld(worldName) != null) return true;
        } catch (ClassNotFoundException | RuntimeException ignored) {
            // not 5.x or world unknown to Multiverse: try the next strategy
        }

        // Multiverse-Core 4.x: getMVWorldManager().loadWorld(String)
        try {
            Object mgr = Reflect.invoke(multiverse, "getMVWorldManager");
            Reflect.invoke(mgr, "loadWorld", worldName);
            if (Bukkit.getWorld(worldName) != null) return true;
        } catch (RuntimeException ignored) {
            // not 4.x
        }

        // Version independent fallback (only for plain world names: never splice odd text into a console command)
        if (!worldName.matches("[A-Za-z0-9_.-]{1,64}")) return false;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv load " + worldName);
        } catch (RuntimeException ignored) {
            // reported through the world still being missing
        }
        return Bukkit.getWorld(worldName) != null;
    }
}
