package com.omniarena.arena;

import com.omniarena.config.ConfigManager;
import com.omniarena.gamemode.GameModeType;
import com.omniarena.match.GoalType;
import com.omniarena.match.RespawnType;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reusable per-arena setting mutation (used by the edit menu and by {@code /oa edit <arena> set <key> <value>}).
 * Parsing is strict; nothing is changed when a value is rejected. On success the arena is saved.
 */
public final class ArenaSettingsService {

    private static final List<String> MODULES = List.of("killstreak", "combo", "power-up");

    /** key -> kind (BOOL, INT, TEXT, or ENUM:<class>) in display order. */
    private static final Map<String, String> KEYS = new LinkedHashMap<>();
    static {
        for (String k : new String[]{"enabled", "announcements", "mid-match-join", "spectator-allowed", "whitelist-enabled",
                "team-enabled", "fall-damage", "fire-damage", "drowning", "suffocation", "friendly-fire", "block-break",
                "block-place", "item-drop", "hunger-drain", "visual-titles", "visual-sounds", "visual-particles",
                "visual-scoreboard", "visual-boss-bar", "visual-action-bar",
                "module-killstreak", "module-combo", "module-power-up"}) KEYS.put(k, "BOOL");
        for (String k : new String[]{"min-players", "max-players", "match-duration", "countdown", "goal-value", "respawn-delay",
                "respawn-wave-interval", "spawn-protection", "lives", "team-count", "region-min-x", "region-min-y",
                "region-min-z", "region-max-x", "region-max-y", "region-max-z"}) KEYS.put(k, "INT");
        KEYS.put("game-mode", "ENUM");
        KEYS.put("goal-type", "ENUM");
        KEYS.put("respawn-type", "ENUM");
        KEYS.put("chat-mode", "ENUM");
        KEYS.put("regen-auto", "TRISTATE");
        KEYS.put("region-world", "TEXT");
    }

    private ArenaSettingsService() {}

    /** All keys {@link #set} understands, in display order. */
    public static Collection<String> keys() {
        return List.copyOf(KEYS.keySet());
    }

    public static boolean isBooleanKey(String key) { return "BOOL".equals(KEYS.get(norm(key))); }

    /** Valid value names for enum-like keys (empty for others). */
    public static List<String> valuesFor(String key) {
        return switch (norm(key)) {
            case "game-mode" -> names(GameModeType.class);
            case "goal-type" -> names(GoalType.class);
            case "respawn-type" -> names(RespawnType.class);
            case "chat-mode" -> names(ChatMode.class);
            case "regen-auto" -> List.of("true", "false", "default");
            default -> List.of();
        };
    }

    /** Current value as text, or null for an unknown key. */
    public static String get(Arena a, String key) {
        Object v = switch (norm(key)) {
            case "enabled" -> a.isEnabled();
            case "game-mode", "gamemode" -> a.getGameMode().name();
            case "min-players" -> a.getMinPlayers();
            case "max-players" -> a.getMaxPlayers();
            case "match-duration" -> a.getMatchDuration();
            case "countdown" -> a.getCountdown();
            case "goal-type" -> a.getGoalType().name();
            case "goal-value" -> a.getGoalValue();
            case "respawn-type" -> a.getRespawnType().name();
            case "respawn-delay" -> a.getRespawnDelay();
            case "respawn-wave-interval" -> a.getRespawnWaveInterval();
            case "spawn-protection" -> a.getSpawnProtection();
            case "lives" -> a.getLives();
            case "chat-mode" -> a.getChatMode().name();
            case "announcements" -> a.isAnnouncements();
            case "mid-match-join" -> a.isMidMatchJoin();
            case "spectator-allowed" -> a.isSpectatorAllowed();
            case "whitelist-enabled" -> a.isWhitelistEnabled();
            case "team-enabled" -> a.getTeamConfig().isEnabled();
            case "team-count" -> a.getTeamConfig().getTeamCount();
            case "fall-damage" -> a.getRules().isFallDamage();
            case "fire-damage" -> a.getRules().isFireDamage();
            case "drowning" -> a.getRules().isDrowning();
            case "suffocation" -> a.getRules().isSuffocation();
            case "friendly-fire" -> a.getRules().isFriendlyFire();
            case "block-break" -> a.getRules().isBlockBreak();
            case "block-place" -> a.getRules().isBlockPlace();
            case "item-drop" -> a.getRules().isItemDrop();
            case "hunger-drain" -> a.getRules().isHungerDrain();
            case "visual-titles" -> a.getVisual().isTitles();
            case "visual-sounds" -> a.getVisual().isSounds();
            case "visual-particles" -> a.getVisual().isParticles();
            case "visual-scoreboard" -> a.getVisual().isScoreboard();
            case "visual-boss-bar" -> a.getVisual().isBossBar();
            case "visual-action-bar" -> a.getVisual().isActionBar();
            case "module-killstreak" -> a.hasModule("killstreak");
            case "module-combo" -> a.hasModule("combo");
            case "module-power-up" -> a.hasModule("power-up");
            case "regen-auto" -> regenOverride(a);
            case "region-world" -> a.getRegion().getWorldName();
            case "region-min-x" -> a.getRegion().getMinX();
            case "region-min-y" -> a.getRegion().getMinY();
            case "region-min-z" -> a.getRegion().getMinZ();
            case "region-max-x" -> a.getRegion().getMaxX();
            case "region-max-y" -> a.getRegion().getMaxY();
            case "region-max-z" -> a.getRegion().getMaxZ();
            default -> null;
        };
        return v == null ? null : String.valueOf(v);
    }

    /** "true", "false" or "default" (follows the global regen.auto). */
    public static String regenOverride(Arena a) {
        String path = regenPath(a);
        var cfg = ConfigManager.getInstance().getConfig();
        return cfg.isSet(path) ? String.valueOf(cfg.getBoolean(path)) : "default";
    }

    /** Effective auto-regen for the arena (override, else global). */
    public static boolean regenEffective(Arena a) {
        var cfg = ConfigManager.getInstance().getConfig();
        String path = regenPath(a);
        return cfg.isSet(path) ? cfg.getBoolean(path) : cfg.getBoolean("regen.auto", true);
    }

    private static String regenPath(Arena a) {
        return "regen.arenas." + a.getName().toLowerCase(Locale.ROOT) + ".auto";
    }

    /**
     * Sets one arena setting. Returns true and saves the arena on success; on failure returns false and appends a
     * human-readable reason (MiniMessage) to {@code errorsOut}.
     */
    public static boolean set(Arena arena, String key, String value, List<String> errorsOut) {
        String k = norm(key);
        if (!KEYS.containsKey(k) && !k.equals("gamemode")) {
            errorsOut.add("Unknown setting <yellow>" + key + "</yellow>. Valid keys: <white>" + String.join(", ", KEYS.keySet()) + "</white>.");
            return false;
        }
        if (value == null) value = "";
        try {
            apply(arena, k, value.trim());
        } catch (NumberFormatException e) {
            errorsOut.add("<yellow>" + value + "</yellow> is not a valid whole number for <yellow>" + key + "</yellow> (use 0 or higher).");
            return false;
        } catch (IllegalArgumentException e) {
            errorsOut.add("Could not set <yellow>" + key + "</yellow> to <yellow>" + value + "</yellow>: " + e.getMessage());
            return false;
        }
        ArenaManager.getInstance().save(arena);
        return true;
    }

    private static void apply(Arena a, String k, String v) {
        switch (k) {
            case "enabled" -> {
                boolean on = bool(v);
                if (on == a.isEnabled()) return;
                if (on) {
                    if (a.getBattleSpawns().isEmpty()) throw new IllegalArgumentException("the arena has no battle spawn yet. Run the setup wizard first (/oa setup " + a.getName() + ").");
                    a.setState(ArenaState.WAITING);
                }
                else ArenaSetupService.disable(a);
            }
            case "game-mode", "gamemode" -> a.setGameMode(enumOf(GameModeType.class, v));
            case "min-players" -> {
                int n = num(v);
                if (n < 1) throw new IllegalArgumentException("min-players must be at least 1.");
                if (a.getMaxPlayers() > 0 && n > a.getMaxPlayers()) throw new IllegalArgumentException("min-players cannot exceed max-players (" + a.getMaxPlayers() + "). Raise max-players first.");
                a.setMinPlayers(n);
            }
            case "max-players" -> {
                int n = num(v);
                if (n < 1) throw new IllegalArgumentException("max-players must be at least 1.");
                if (n < a.getMinPlayers()) throw new IllegalArgumentException("max-players cannot be below min-players (" + a.getMinPlayers() + "). Lower min-players first.");
                a.setMaxPlayers(n);
            }
            case "match-duration" -> a.setMatchDuration(num(v));
            case "countdown" -> a.setCountdown(num(v));
            case "goal-type" -> a.setGoalType(enumOf(GoalType.class, v));
            case "goal-value" -> a.setGoalValue(num(v));
            case "respawn-type" -> a.setRespawnType(enumOf(RespawnType.class, v));
            case "respawn-delay" -> a.setRespawnDelay(num(v));
            case "respawn-wave-interval" -> a.setRespawnWaveInterval(num(v));
            case "spawn-protection" -> a.setSpawnProtection(num(v));
            case "lives" -> {
                int n = num(v);
                if (n > 100) throw new IllegalArgumentException("lives must be between 0 and 100.");
                a.setLives(n);
            }
            case "chat-mode" -> a.setChatMode(enumOf(ChatMode.class, v));
            case "announcements" -> a.setAnnouncements(bool(v));
            case "mid-match-join" -> a.setMidMatchJoin(bool(v));
            case "spectator-allowed" -> a.setSpectatorAllowed(bool(v));
            case "whitelist-enabled" -> a.setWhitelistEnabled(bool(v));
            case "team-enabled" -> a.getTeamConfig().setEnabled(bool(v));
            case "team-count" -> {
                int n = num(v);
                if (n < 2) throw new IllegalArgumentException("team-count must be at least 2.");
                a.getTeamConfig().setTeamCount(n);
            }
            case "fall-damage" -> a.getRules().setFallDamage(bool(v));
            case "fire-damage" -> a.getRules().setFireDamage(bool(v));
            case "drowning" -> a.getRules().setDrowning(bool(v));
            case "suffocation" -> a.getRules().setSuffocation(bool(v));
            case "friendly-fire" -> a.getRules().setFriendlyFire(bool(v));
            case "block-break" -> a.getRules().setBlockBreak(bool(v));
            case "block-place" -> a.getRules().setBlockPlace(bool(v));
            case "item-drop" -> a.getRules().setItemDrop(bool(v));
            case "hunger-drain" -> a.getRules().setHungerDrain(bool(v));
            case "visual-titles" -> a.getVisual().setTitles(bool(v));
            case "visual-sounds" -> a.getVisual().setSounds(bool(v));
            case "visual-particles" -> a.getVisual().setParticles(bool(v));
            case "visual-scoreboard" -> a.getVisual().setScoreboard(bool(v));
            case "visual-boss-bar" -> a.getVisual().setBossBar(bool(v));
            case "visual-action-bar" -> a.getVisual().setActionBar(bool(v));
            case "module-killstreak" -> module(a, "killstreak", bool(v));
            case "module-combo" -> module(a, "combo", bool(v));
            case "module-power-up" -> module(a, "power-up", bool(v));
            case "regen-auto" -> {
                String lower = v.toLowerCase(Locale.ROOT);
                String path = regenPath(a);
                switch (lower) {
                    case "true", "yes", "on" -> ConfigManager.getInstance().setConfigValue(path, true);
                    case "false", "no", "off" -> ConfigManager.getInstance().setConfigValue(path, false);
                    case "default", "reset", "global" -> ConfigManager.getInstance().setConfigValue(path, null);
                    default -> throw new IllegalArgumentException("Use true, false or default (follow the global regen.auto).");
                }
            }
            case "region-world" -> a.getRegion().setWorldName(v);
            case "region-min-x" -> a.getRegion().setMinX(num(v));
            case "region-min-y" -> a.getRegion().setMinY(num(v));
            case "region-min-z" -> a.getRegion().setMinZ(num(v));
            case "region-max-x" -> a.getRegion().setMaxX(num(v));
            case "region-max-y" -> a.getRegion().setMaxY(num(v));
            case "region-max-z" -> a.getRegion().setMaxZ(num(v));
            default -> throw new IllegalArgumentException("Unsupported key.");
        }
    }

    private static void module(Arena a, String name, boolean on) {
        if (on) a.enableModule(name); else a.disableModule(name);
    }

    /** Known module names (killstreak, combo, power-up). */
    public static List<String> modules() { return MODULES; }

    private static String norm(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static boolean bool(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("yes") || v.equals("on") || v.equals("enabled")) return true;
        if (v.equals("false") || v.equals("no") || v.equals("off") || v.equals("disabled")) return false;
        throw new IllegalArgumentException("Expected true or false (also yes/no/on/off), got '" + value + "'.");
    }

    private static int num(String value) {
        int n = Integer.parseInt(value.trim());
        if (n < 0) throw new NumberFormatException("negative");
        return n;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String value) {
        String v = value.trim().toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        for (E e : type.getEnumConstants()) if (e.name().equals(v)) return e;
        throw new IllegalArgumentException("Unknown value '" + value + "'. Valid values: " + String.join(", ", names(type)));
    }

    private static <E extends Enum<E>> List<String> names(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }
}
