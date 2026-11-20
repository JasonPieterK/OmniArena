package com.omniarena.visual;

import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Decides whether a presentation channel (titles, sounds, particles) is active.
 * A channel is on only when BOTH the global switch (config.yml {@code visuals.<key>}) and the arena's own
 * switch ({@code visual.<key>} in arenas/&lt;name&gt;.yml, /oa config &lt;arena&gt; visual-&lt;key&gt;) are on.
 * Subjects outside any arena (unknown player / location) only obey the global switch.
 */
public final class VisualGate {

    private VisualGate() {}

    public enum Channel { TITLES, SOUNDS, PARTICLES }

    public static boolean titles(Match m)    { return on(m == null ? null : m.getArena(), Channel.TITLES); }
    public static boolean sounds(Match m)    { return on(m == null ? null : m.getArena(), Channel.SOUNDS); }
    public static boolean particles(Match m) { return on(m == null ? null : m.getArena(), Channel.PARTICLES); }

    public static boolean titles(Player p)    { return on(arenaOf(p), Channel.TITLES); }
    public static boolean sounds(Player p)    { return on(arenaOf(p), Channel.SOUNDS); }
    public static boolean particles(Player p) { return on(arenaOf(p), Channel.PARTICLES); }
    public static boolean particles(Location l) { return on(arenaAt(l), Channel.PARTICLES); }

    public static boolean on(Arena arena, Channel c) {
        ConfigManager cm = ConfigManager.getInstance();
        String key = "visuals." + c.name().toLowerCase(java.util.Locale.ROOT);
        if (cm != null && !cm.getConfig().getBoolean(key, true)) return false;
        if (arena == null) return true;
        var v = arena.getVisual();
        return switch (c) {
            case TITLES -> v.isTitles();
            case SOUNDS -> v.isSounds();
            case PARTICLES -> v.isParticles();
        };
    }

    private static Arena arenaOf(Player p) {
        MatchManager mm = MatchManager.getInstance();
        ArenaManager am = ArenaManager.getInstance();
        if (p == null || mm == null || am == null) return null;
        return mm.getArenaName(p).flatMap(am::get).orElse(null);
    }

    private static Arena arenaAt(Location l) {
        ArenaManager am = ArenaManager.getInstance();
        if (l == null || l.getWorld() == null || am == null) return null;
        return am.getArenaAt(l).orElse(null);
    }
}
