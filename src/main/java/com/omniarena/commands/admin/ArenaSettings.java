package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ChatMode;
import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.gamemode.GameModeType;
import com.omniarena.match.GoalType;
import com.omniarena.match.RespawnType;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Arena setting editor behind {@code /oa edit <arena> set <key> <value>}. Covers every key the old
 * old config, goal, chat, announcements, module and regen-auto commands could set.
 */
public final class ArenaSettings {

    private ArenaSettings() {}

    /** Applies and saves one setting; sends feedback (success or a specific, fixable error). */
    public static boolean set(CommandSender sender, Arena arena, String key, String value) {
        try {
            return applyArenaConfig(sender, arena, key, value);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-number").replace("{input}", com.omniarena.util.Suggest.escape(value)));
        } catch (IllegalArgumentException e) {
            MessageUtil.send(sender, "<red>Could not set <yellow>" + com.omniarena.util.Suggest.escape(key) + "</yellow> to <yellow>" + com.omniarena.util.Suggest.escape(value)
                    + "</yellow> on arena <yellow>" + arena.getName() + "</yellow>: " + com.omniarena.util.Suggest.escape(e.getMessage())
                    + " <gray>Fix the value and run <white>/oa edit " + arena.getName() + " set " + key + " <value></white> again.");
        }
        return false;
    }

    private static boolean applyArenaConfig(CommandSender sender, Arena arena, String key, String value) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "enabled" -> {
                boolean on = parseBoolean(value);
                if (on == arena.isEnabled()) { /* no state change: don't reset a running arena */ }
                else if (on) arena.setState(com.omniarena.arena.ArenaState.WAITING);
                else { ArenaCommand.kickAll(arena); arena.setState(com.omniarena.arena.ArenaState.DISABLED); }
            }
            case "game-mode", "gamemode" -> arena.setGameMode(strictEnum(GameModeType.class, value));
            case "min-players" -> {
                int n = parseInt(value, 0);
                if (n < 1) throw new IllegalArgumentException("min-players must be at least 1 (you gave " + n + ").");
                if (arena.getMaxPlayers() > 0 && n > arena.getMaxPlayers()) throw new IllegalArgumentException("min-players cannot exceed max-players (" + arena.getMaxPlayers() + "). Raise max-players first: /oa edit " + arena.getName() + " set max-players <n>.");
                arena.setMinPlayers(n);
            }
            case "max-players" -> {
                int n = parseInt(value, 0);
                if (n < 1) throw new IllegalArgumentException("max-players must be at least 1 (you gave " + n + ").");
                if (n < arena.getMinPlayers()) throw new IllegalArgumentException("max-players cannot be below min-players (" + arena.getMinPlayers() + "). Lower min-players first: /oa edit " + arena.getName() + " set min-players <n>.");
                arena.setMaxPlayers(n);
            }
            case "match-duration" -> arena.setMatchDuration(parseInt(value, arena.getMatchDuration()));
            case "countdown" -> arena.setCountdown(parseInt(value, arena.getCountdown()));
            case "goal-type" -> arena.setGoalType(strictEnum(GoalType.class, value));
            case "goal-value" -> arena.setGoalValue(parseInt(value, arena.getGoalValue()));
            case "respawn-type" -> arena.setRespawnType(strictEnum(RespawnType.class, value));
            case "respawn-delay" -> arena.setRespawnDelay(parseInt(value, arena.getRespawnDelay()));
            case "respawn-wave-interval" -> arena.setRespawnWaveInterval(parseInt(value, arena.getRespawnWaveInterval()));
            case "spawn-protection" -> arena.setSpawnProtection(parseInt(value, arena.getSpawnProtection()));
            case "lives" -> {
                int n = parseInt(value, arena.getLives());
                if (n > 100) throw new NumberFormatException("lives must be 0-100");
                arena.setLives(n);
            }
            case "chat-mode" -> arena.setChatMode(strictEnum(ChatMode.class, value));
            case "announcements" -> arena.setAnnouncements(parseBoolean(value));
            case "mid-match-join" -> arena.setMidMatchJoin(parseBoolean(value));
            case "spectator-allowed" -> arena.setSpectatorAllowed(parseBoolean(value));
            case "whitelist-enabled" -> arena.setWhitelistEnabled(parseBoolean(value));
            case "team-enabled" -> arena.getTeamConfig().setEnabled(parseBoolean(value));
            case "team-count" -> arena.getTeamConfig().setTeamCount(parseInt(value, arena.getTeamConfig().getTeamCount()));
            case "fall-damage" -> arena.getRules().setFallDamage(parseBoolean(value));
            case "fire-damage" -> arena.getRules().setFireDamage(parseBoolean(value));
            case "drowning" -> arena.getRules().setDrowning(parseBoolean(value));
            case "suffocation" -> arena.getRules().setSuffocation(parseBoolean(value));
            case "friendly-fire" -> arena.getRules().setFriendlyFire(parseBoolean(value));
            case "block-break" -> arena.getRules().setBlockBreak(parseBoolean(value));
            case "block-place" -> arena.getRules().setBlockPlace(parseBoolean(value));
            case "item-drop" -> arena.getRules().setItemDrop(parseBoolean(value));
            case "hunger-drain" -> arena.getRules().setHungerDrain(parseBoolean(value));
            case "visual-titles" -> arena.getVisual().setTitles(parseBoolean(value));
            case "visual-sounds" -> arena.getVisual().setSounds(parseBoolean(value));
            case "visual-particles" -> arena.getVisual().setParticles(parseBoolean(value));
            case "visual-scoreboard" -> arena.getVisual().setScoreboard(parseBoolean(value));
            case "visual-boss-bar" -> arena.getVisual().setBossBar(parseBoolean(value));
            case "visual-action-bar" -> arena.getVisual().setActionBar(parseBoolean(value));
            case "visual-holograms" -> arena.getVisual().setHolograms(parseBoolean(value));
            case "region-world" -> arena.getRegion().setWorldName(value);
            case "region-min-x" -> arena.getRegion().setMinX(parseInt(value, arena.getRegion().getMinX()));
            case "region-min-y" -> arena.getRegion().setMinY(parseInt(value, arena.getRegion().getMinY()));
            case "region-min-z" -> arena.getRegion().setMinZ(parseInt(value, arena.getRegion().getMinZ()));
            case "region-max-x" -> arena.getRegion().setMaxX(parseInt(value, arena.getRegion().getMaxX()));
            case "region-max-y" -> arena.getRegion().setMaxY(parseInt(value, arena.getRegion().getMaxY()));
            case "region-max-z" -> arena.getRegion().setMaxZ(parseInt(value, arena.getRegion().getMaxZ()));
            case "regen-auto" -> {
                String path = "regen.arenas." + arena.getName().toLowerCase(Locale.ROOT) + ".auto";
                String v = value.trim().toLowerCase(Locale.ROOT);
                if (v.equals("default") || v.equals("global") || v.equals("reset")) ConfigManager.getInstance().setConfigValue(path, null);
                else ConfigManager.getInstance().setConfigValue(path, parseBoolean(value));
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-set")
                        .replace("{key}", key).replace("{value}", com.omniarena.util.Suggest.escape(value)).replace("{arena}", arena.getName()));
                return true;
            }
            case "module-killstreak", "module-combo", "module-power-up" -> {
                String module = key.toLowerCase(Locale.ROOT).substring("module-".length());
                if (parseBoolean(value)) arena.enableModule(module); else arena.disableModule(module);
            }
            default -> {
                String hint = com.omniarena.util.Suggest.closest(key, ARENA_KEYS)
                        .map(k -> " <gray>Did you mean " + com.omniarena.util.Suggest.click("/oa edit " + arena.getName() + " set " + k + " ") + "<gray>?").orElse("");
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-invalid-key")
                        .replace("{key}", com.omniarena.util.Suggest.escape(key)) + hint);
                return false;
            }
        }
        ArenaManager.getInstance().save(arena);
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-set")
                .replace("{key}", key)
                .replace("{value}", com.omniarena.util.Suggest.escape(value))
                .replace("{arena}", arena.getName()));
        return true;
    }

    public static Object get(Arena arena, String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "enabled" -> arena.isEnabled();
            case "game-mode", "gamemode" -> arena.getGameMode().name();
            case "min-players" -> arena.getMinPlayers();
            case "max-players" -> arena.getMaxPlayers();
            case "match-duration" -> arena.getMatchDuration();
            case "countdown" -> arena.getCountdown();
            case "goal-type" -> arena.getGoalType().name();
            case "goal-value" -> arena.getGoalValue();
            case "respawn-type" -> arena.getRespawnType().name();
            case "respawn-delay" -> arena.getRespawnDelay();
            case "respawn-wave-interval" -> arena.getRespawnWaveInterval();
            case "spawn-protection" -> arena.getSpawnProtection();
            case "lives" -> arena.getLives();
            case "chat-mode" -> arena.getChatMode().name();
            case "announcements" -> arena.isAnnouncements();
            case "mid-match-join" -> arena.isMidMatchJoin();
            case "spectator-allowed" -> arena.isSpectatorAllowed();
            case "whitelist-enabled" -> arena.isWhitelistEnabled();
            case "team-enabled" -> arena.getTeamConfig().isEnabled();
            case "team-count" -> arena.getTeamConfig().getTeamCount();
            case "fall-damage" -> arena.getRules().isFallDamage();
            case "fire-damage" -> arena.getRules().isFireDamage();
            case "drowning" -> arena.getRules().isDrowning();
            case "suffocation" -> arena.getRules().isSuffocation();
            case "friendly-fire" -> arena.getRules().isFriendlyFire();
            case "block-break" -> arena.getRules().isBlockBreak();
            case "block-place" -> arena.getRules().isBlockPlace();
            case "item-drop" -> arena.getRules().isItemDrop();
            case "hunger-drain" -> arena.getRules().isHungerDrain();
            case "visual-titles" -> arena.getVisual().isTitles();
            case "visual-sounds" -> arena.getVisual().isSounds();
            case "visual-particles" -> arena.getVisual().isParticles();
            case "visual-scoreboard" -> arena.getVisual().isScoreboard();
            case "visual-boss-bar" -> arena.getVisual().isBossBar();
            case "visual-action-bar" -> arena.getVisual().isActionBar();
            case "visual-holograms" -> arena.getVisual().isHolograms();
            case "region-world" -> arena.getRegion().getWorldName();
            case "region-min-x" -> arena.getRegion().getMinX();
            case "region-min-y" -> arena.getRegion().getMinY();
            case "region-min-z" -> arena.getRegion().getMinZ();
            case "region-max-x" -> arena.getRegion().getMaxX();
            case "region-max-y" -> arena.getRegion().getMaxY();
            case "region-max-z" -> arena.getRegion().getMaxZ();
            case "regen-auto" -> {
                var cfg = ConfigManager.getInstance().getConfig();
                String path = "regen.arenas." + arena.getName().toLowerCase(Locale.ROOT) + ".auto";
                yield cfg.isSet(path) ? cfg.getBoolean(path) : "default (" + cfg.getBoolean("regen.auto", true) + ")";
            }
            case "module-killstreak", "module-combo", "module-power-up" -> arena.hasModule(key.toLowerCase(Locale.ROOT).substring("module-".length()));
            default -> null;
        };
    }

    /** Strict: throws IllegalArgumentException on anything that is not clearly true/false. */
    private static boolean parseBoolean(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("yes") || v.equals("on") || v.equals("enabled")) return true;
        if (v.equals("false") || v.equals("no") || v.equals("off") || v.equals("disabled")) return false;
        throw new IllegalArgumentException("Expected true or false (also yes/no/on/off), got '" + value + "'.");
    }

    /** Strict: throws NumberFormatException; negatives rejected. */
    private static int parseInt(String value, int fallback) {
        int n = Integer.parseInt(value.trim());
        if (n < 0) throw new NumberFormatException("negative");
        return n;
    }

    private static double parseDouble(String value, double fallback) {
        return Double.parseDouble(value.trim());
    }

    private static <E extends Enum<E>> E strictEnum(Class<E> type, String value) {
        String v = value.trim().toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        for (E e : type.getEnumConstants()) if (e.name().equals(v)) return e;
        throw new IllegalArgumentException("Unknown value '" + value + "'. Valid values: "
                + Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(java.util.stream.Collectors.joining(", ")));
    }


    public static final List<String> ARENA_KEYS = List.of("enabled", "game-mode", "min-players", "max-players", "match-duration", "countdown",
            "goal-type", "goal-value", "respawn-type", "respawn-delay", "respawn-wave-interval", "spawn-protection", "lives", "chat-mode",
            "announcements", "mid-match-join", "spectator-allowed", "whitelist-enabled", "team-enabled",
            "team-count", "fall-damage", "fire-damage", "drowning", "suffocation", "friendly-fire",
            "block-break", "block-place", "item-drop", "hunger-drain", "visual-titles", "visual-sounds",
            "visual-particles", "visual-scoreboard", "visual-boss-bar", "visual-action-bar", "visual-holograms",
            "regen-auto", "module-killstreak", "module-combo", "module-power-up",
            "region-world", "region-min-x", "region-min-y", "region-min-z", "region-max-x", "region-max-y", "region-max-z");
    public static final java.util.Set<String> ARENA_BOOLEAN_KEYS = java.util.Set.of("enabled", "announcements", "mid-match-join",
            "spectator-allowed", "whitelist-enabled", "team-enabled", "fall-damage", "fire-damage", "drowning", "suffocation",
            "friendly-fire", "block-break", "block-place", "item-drop", "hunger-drain", "visual-titles", "visual-sounds",
            "visual-particles", "visual-scoreboard", "visual-boss-bar", "visual-action-bar", "visual-holograms",
            "module-killstreak", "module-combo", "module-power-up");


    /** Tab completions for the value of an arena key. */
    public static List<String> tabValue(String key, String cur) {
        String k = key.toLowerCase(Locale.ROOT);
        if (k.equals("regen-auto")) return TabUtil.filter(List.of("true", "false", "default"), cur);
        if (ARENA_BOOLEAN_KEYS.contains(k)) return TabUtil.bools(cur);
        return switch (k) {
            case "game-mode", "gamemode" -> TabUtil.enums(GameModeType.class, cur);
            case "goal-type" -> TabUtil.enums(GoalType.class, cur);
            case "respawn-type" -> TabUtil.enums(RespawnType.class, cur);
            case "chat-mode" -> TabUtil.enums(ChatMode.class, cur);
            case "region-world" -> TabUtil.worlds(cur);
            default -> ARENA_KEYS.contains(k) ? TabUtil.hint("<number>", cur) : List.of();
        };
    }
}
