package com.omniarena.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure logic (no server needed) that upgrades a config.yml from the old layout (no {@code config-version}, version 1)
 * to layout version 2. Old key names live ONLY here: {@link #MOVES} is the single old-to-new table, and
 * {@link #applyFallback} lets a still-old file work in memory if it could not be rewritten.
 */
public final class ConfigMigration {

    public static final String VERSION_KEY = "config-version";
    public static final int CURRENT_VERSION = 2;

    /** Whole sections whose children are user data: on overlay they replace the template's defaults instead of merging. */
    public static final Set<String> REPLACE_SECTIONS = Set.of("visuals.killstreak.milestones");

    /** Old path to new path. Values (and whole sections) are moved; an existing value at the new path wins. */
    public static final Map<String, String> MOVES = new LinkedHashMap<>();
    /** Old keys that no longer exist (nothing reads them any more). Currently none. */
    public static final List<String> REMOVED = List.of();

    static {
        // general
        MOVES.put("plugin.debug", "general.debug");
        MOVES.put("tools.wand-material", "general.wand-material");
        // arenas (defaults for new arenas + arena-wide switches)
        MOVES.put("arena.default-min-players", "arenas.min-players");
        MOVES.put("arena.default-max-players", "arenas.max-players");
        MOVES.put("arena.default-countdown", "arenas.countdown");
        MOVES.put("arena.default-match-duration", "arenas.match-duration");
        MOVES.put("arena.default-game-mode", "arenas.game-mode");
        MOVES.put("arena.spectator-allowed", "arenas.spectator-allowed");
        MOVES.put("arena.mid-match-join", "arenas.mid-match-join");
        MOVES.put("arena.respawn-delay", "arenas.respawn-delay");
        MOVES.put("arena.spawn-protection", "arenas.spawn-protection");
        MOVES.put("arena.region-protection", "arenas.region-protection");
        MOVES.put("signs.glow-effect", "arenas.sign-glow");
        // matches
        MOVES.put("match.end-delay", "matches.end-delay");
        MOVES.put("arena.lobby-timeout", "matches.lobby-timeout");
        MOVES.put("arena.inventory-restore", "matches.inventory-restore");
        MOVES.put("match.announce-arena", "matches.announce-arena");
        MOVES.put("match.announce-global", "matches.announce-global");
        MOVES.put("match.end-commands", "matches.end-commands");
        // regen (regen.auto and regen.arenas.<name>.auto keep their paths)
        MOVES.put("regen.animation.type", "regen.style");
        MOVES.put("regen.animation.particles", "regen.particles");
        MOVES.put("regen.animation.sound", "regen.sound");
        MOVES.put("regen.progress.enabled", "regen.progress-messages");
        // ranked
        MOVES.put("party.max-size", "ranked.party-max-size");
        // tournaments
        MOVES.put("tournament.walkover-grace-seconds", "tournaments.walkover-grace-seconds");
        // pvp
        MOVES.put("pvp.global.pvp-enabled", "pvp.server-pvp");
        MOVES.put("pvp.combat.block-enderpearls", "pvp.combat.block-ender-pearl"); // pre-1.0 name
        MOVES.put("pvp.combat.cancel-teleport", "pvp.combat.block-teleport");      // pre-1.0 name
        // visuals
        for (String k : new String[]{"titles", "sounds", "particles", "scoreboard", "boss-bar", "action-bar",
                "killstreak-titles", "kill-announcements", "victory-fireworks", "countdown-sounds",
                "spectator-invisibility"}) {
            MOVES.put("visual." + k, "visuals." + k);
        }
        MOVES.put("scoreboard.title", "visuals.scoreboard-title");
        MOVES.put("boss-bar.timer-color", "visuals.boss-bar-colors.timer");
        MOVES.put("boss-bar.waiting-color", "visuals.boss-bar-colors.waiting");
        MOVES.put("boss-bar.ending-color", "visuals.boss-bar-colors.ending");
        MOVES.put("boss-bar.low-time-color", "visuals.boss-bar-colors.low-time");
        MOVES.put("boss-bar.low-time-threshold", "visuals.boss-bar-colors.low-time-seconds");
        MOVES.put("killstreak.sound", "visuals.killstreak.sound");
        MOVES.put("killstreak.particle", "visuals.killstreak.particle");
        MOVES.put("killstreak.milestones", "visuals.killstreak.milestones");
        // advanced
        MOVES.put("arena.persist-player-state", "advanced.persist-player-state");
        MOVES.put("regen.batch-size", "advanced.regen.batch-size");
        MOVES.put("regen.progress.interval-percent", "advanced.regen.progress-interval-percent");
        MOVES.put("signs.update-interval", "advanced.signs.update-interval");
        MOVES.put("scoreboard.update-interval", "advanced.scoreboard.update-interval");
        MOVES.put("health.watchdog-seconds", "advanced.health.watchdog-seconds");
        MOVES.put("health.stuck-arena-seconds", "advanced.health.stuck-arena-seconds");
        MOVES.put("web.session-minutes", "advanced.web.session-minutes");
        MOVES.put("web.max-sessions", "advanced.web.max-sessions");
    }

    /** Old top-level sections that are deleted once they are empty after the moves. */
    private static final List<String> OLD_SECTIONS = List.of("plugin", "tools", "arena", "match", "party", "signs",
            "tournament", "visual", "scoreboard", "boss-bar", "killstreak");
    /** Old nested sections that are deleted once empty. */
    private static final List<String> OLD_NESTED = List.of("regen.animation", "regen.progress", "pvp.global");

    private ConfigMigration() {}

    public record Result(boolean changed, List<String> moved, List<String> removed) {}

    public static int version(ConfigurationSection cfg) {
        return cfg.getInt(VERSION_KEY, 1);
    }

    public static boolean needsMigration(ConfigurationSection cfg) {
        return version(cfg) < CURRENT_VERSION;
    }

    /** Full migration of an old file's contents, in place: moves keys, drops removed ones, sets the version. */
    public static Result migrate(ConfigurationSection cfg) {
        if (!needsMigration(cfg)) return new Result(false, List.of(), List.of());
        Result r = applyFallback(cfg);
        cfg.set(VERSION_KEY, CURRENT_VERSION);
        return new Result(true, r.moved(), r.removed());
    }

    /**
     * Version-independent, idempotent: moves any old key that is still present and whose new path is unset.
     * ConfigManager runs it in memory after every load so a file that could not be rewritten still works.
     */
    public static Result applyFallback(ConfigurationSection cfg) {
        List<String> moved = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (Map.Entry<String, String> e : MOVES.entrySet()) {
            String from = e.getKey(), to = e.getValue();
            if (!cfg.contains(from, true)) continue;
            Object value = cfg.get(from);
            if (!cfg.contains(to, true)) {
                if (value instanceof ConfigurationSection s) {
                    for (String child : s.getKeys(true)) {
                        if (!s.isConfigurationSection(child)) cfg.set(to + "." + child, s.get(child));
                    }
                } else {
                    cfg.set(to, value);
                }
                moved.add(from + " -> " + to);
            } else {
                removed.add(from + " (already set at " + to + ")");
            }
            cfg.set(from, null);
        }
        for (String key : REMOVED) {
            if (cfg.contains(key, true)) { cfg.set(key, null); removed.add(key); }
        }
        for (String path : OLD_NESTED) pruneIfEmpty(cfg, path);
        for (String path : OLD_SECTIONS) pruneIfEmpty(cfg, path);
        return new Result(!moved.isEmpty() || !removed.isEmpty(), moved, removed);
    }

    private static void pruneIfEmpty(ConfigurationSection cfg, String path) {
        ConfigurationSection s = cfg.getConfigurationSection(path);
        if (s != null && s.getKeys(false).isEmpty()) cfg.set(path, null);
    }

    /**
     * Copies every value of {@code user} onto {@code template} (the bundled commented default file), so the result
     * keeps the new file's layout and comments but the admin's values. Unknown user keys are kept.
     */
    public static void overlay(ConfigurationSection template, ConfigurationSection user) {
        for (String path : REPLACE_SECTIONS) {
            if (user.isConfigurationSection(path)) template.set(path, null);
        }
        for (String path : user.getKeys(true)) {
            if (user.isConfigurationSection(path)) continue;
            template.set(path, user.get(path));
        }
        template.set(VERSION_KEY, CURRENT_VERSION);
    }
}
