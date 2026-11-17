package com.omniarena.platform;

import org.bukkit.plugin.Plugin;

/**
 * Entry point of the platform layer. Detects the running server and exposes the compiled-in {@link PlatformAdapter}.
 */
public final class Platform {

    private static volatile PlatformAdapter adapter;
    private static volatile Boolean paper;

    private Platform() {}

    /** True on Paper and Paper forks (Purpur, Pufferfish, Folia...). Detected by class presence, once. */
    public static boolean isPaper() {
        Boolean p = paper;
        if (p == null) {
            p = classExists("io.papermc.paper.configuration.Configuration")
                    || classExists("io.papermc.paper.event.player.AsyncChatEvent")
                    || classExists("com.destroystokyo.paper.PaperConfig");
            paper = p;
        }
        return p;
    }

    public static boolean classExists(String name) {
        try {
            Class.forName(name, false, Platform.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Creates and initialises the adapter. Throws IllegalStateException with an actionable message when a
     * paper-flavored jar runs on a server without Paper's API (use the spigot jar there).
     */
    public static synchronized void init(Plugin plugin) {
        if (adapter != null) return;
        PlatformAdapter impl;
        try {
            impl = new PlatformImpl();
        } catch (Throwable t) {
            throw new IllegalStateException("This OmniArena jar was built for another server software ("
                    + t + "). Download the matching jar (paper/purpur vs spigot/bukkit).", t);
        }
        if (impl.flavor().equals("paper") && !isPaper()) {
            throw new IllegalStateException("This is the Paper/Purpur build of OmniArena but the server is not Paper. "
                    + "Use the OmniArena-spigot or OmniArena-bukkit jar instead.");
        }
        impl.init(plugin);
        adapter = impl;
    }

    public static PlatformAdapter get() {
        PlatformAdapter a = adapter;
        if (a == null) throw new IllegalStateException("Platform not initialised (Platform.init was not called)");
        return a;
    }

    public static synchronized void shutdown() {
        PlatformAdapter a = adapter;
        adapter = null;
        if (a != null) a.shutdown();
    }
}
