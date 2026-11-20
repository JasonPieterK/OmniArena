package com.omniarena.banner;

import java.util.List;

/**
 * Everything the banner shows, gathered once on the main thread so rendering needs no Bukkit access.
 *
 * @param version      plugin version
 * @param platform     server software label (Paper, Purpur, Spigot...)
 * @param minecraft    Minecraft version
 * @param java         Java runtime version
 * @param arenas       loaded arenas
 * @param kits         loaded admin kits
 * @param players      players with lifetime stats
 * @param startupMs    time OmniArena needed to enable
 * @param integrations soft-dependency hooks, {@code ok} = hook is live
 * @param repairs      self-healing repairs recorded since start
 * @param webUrl       dashboard base URL, or null when the dashboard is not running
 */
public record BannerData(String version, String platform, String minecraft, String java,
                         int arenas, int kits, int players, long startupMs,
                         List<Hook> integrations, int repairs, String webUrl) {

    /** One integration hook and whether it is live. */
    public record Hook(String name, boolean ok) {}
}
