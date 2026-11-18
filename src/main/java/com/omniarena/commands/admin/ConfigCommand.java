package com.omniarena.commands.admin;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaRegion;
import com.omniarena.arena.ArenaRules;
import com.omniarena.arena.ArenaTeamConfig;
import com.omniarena.arena.ArenaVisualConfig;
import com.omniarena.arena.ChatMode;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.gamemode.GameModeType;
import com.omniarena.match.GoalType;
import com.omniarena.match.RespawnType;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import com.omniarena.web.ConfigSchemaService;
import com.omniarena.web.model.SettingDefinition;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /oa config set <key> <value>  - edit global config live
 * /oa config get <key>          - view value
 * /oa config set <arena> <key> <value> - edit per-arena config
 */
public class ConfigCommand extends SubCommand {

    private final OmniArena plugin;
    private final ConfigSchemaService schema;

    public ConfigCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa config <set|get> [arena] <key> [value]", "Edit config live");
        this.plugin = plugin;
        this.schema = new ConfigSchemaService(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "get" -> handleGet(sender, args);
            case "set" -> handleSet(sender, args);
            default -> MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
        }
    }

    private void handleGet(CommandSender sender, String[] args) {
        String key = args[1];
        Optional<Arena> arena = ArenaManager.getInstance().get(key);
        if (arena.isPresent() && args.length >= 3) {
            String arenaKey = args[2];
            Object value = getArenaValue(arena.get(), arenaKey);
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-get")
                    .replace("{key}", key + "." + arenaKey)
                    .replace("{value}", value != null ? value.toString() : "null"));
            return;
        }
        Object value = ConfigManager.getInstance().getValue(key);
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-get")
                .replace("{key}", key)
                .replace("{value}", value != null ? value.toString() : "null"));
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }

        Optional<Arena> arena = ArenaManager.getInstance().get(args[1]);
        if (arena.isPresent() && args.length >= 4) {
            String key = args[2];
            String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            setArenaConfig(sender, arena.get(), key, value);
            return;
        }

        String key = args[1];
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        boolean known = schema.settings().stream().anyMatch(d -> d.key().equalsIgnoreCase(key))
                || ConfigManager.getInstance().getValue(key) != null;
        if (!known) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-invalid-key").replace("{key}", key));
            return;
        }
        Object parsed;
        try { parsed = parseGlobalValue(key, value); }
        catch (IllegalArgumentException e) {
            MessageUtil.send(sender, "<red>Invalid value <yellow>" + value + "</yellow> for <yellow>" + key + "</yellow>: " + e.getMessage() + " <gray>See valid values by pressing TAB after the key, or <white>/oa config get " + key + "</white>.");
            return;
        }
        ConfigManager.getInstance().setValue(key, parsed);
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-set")
                .replace("{key}", key)
                .replace("{value}", String.valueOf(parsed))
                .replace("{arena}", "global"));
    }

    private void setArenaConfig(CommandSender sender, Arena arena, String key, String value) {
        try {
            applyArenaConfig(sender, arena, key, value);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-number").replace("{input}", value));
        } catch (IllegalArgumentException e) {
            MessageUtil.send(sender, "<red>Could not set <yellow>" + key + "</yellow> to <yellow>" + value + "</yellow> on arena <yellow>" + arena.getName() + "</yellow>: " + e.getMessage() + " <gray>Fix the value and run <white>/oa config set " + arena.getName() + " " + key + " <value></white> again.");
        }
    }

    private void applyArenaConfig(CommandSender sender, Arena arena, String key, String value) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "enabled" -> {
                boolean on = parseBoolean(value);
                if (on == arena.isEnabled()) { /* no state change: don't reset a running arena */ }
                else if (on) arena.setState(com.omniarena.arena.ArenaState.WAITING);
                else { DisableArenaCommand.kickAll(arena); arena.setState(com.omniarena.arena.ArenaState.DISABLED); }
            }
            case "game-mode", "gamemode" -> arena.setGameMode(strictEnum(GameModeType.class, value));
            case "min-players" -> {
                int n = parseInt(value, 0);
                if (n < 1) throw new IllegalArgumentException("min-players must be at least 1 (you gave " + n + ").");
                if (arena.getMaxPlayers() > 0 && n > arena.getMaxPlayers()) throw new IllegalArgumentException("min-players cannot exceed max-players (" + arena.getMaxPlayers() + "). Raise max-players first: /oa config set " + arena.getName() + " max-players <n>.");
                arena.setMinPlayers(n);
            }
            case "max-players" -> {
                int n = parseInt(value, 0);
                if (n < 1) throw new IllegalArgumentException("max-players must be at least 1 (you gave " + n + ").");
                if (n < arena.getMinPlayers()) throw new IllegalArgumentException("max-players cannot be below min-players (" + arena.getMinPlayers() + "). Lower min-players first: /oa config set " + arena.getName() + " min-players <n>.");
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
            default -> {
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-invalid-key").replace("{key}", key));
                return;
            }
        }
        ArenaManager.getInstance().save(arena);
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-set")
                .replace("{key}", key)
                .replace("{value}", value)
                .replace("{arena}", arena.getName()));
    }

    private Object getArenaValue(Arena arena, String key) {
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
            default -> null;
        };
    }

    private Object parseGlobalValue(String key, String value) {
        SettingDefinition def = schema.settings().stream()
                .filter(setting -> setting.key().equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
        if (def == null) return parseLoose(value);
        return switch (def.type()) {
            case BOOLEAN -> parseBoolean(value);
            case INTEGER -> parseInt(value, 0);
            case DECIMAL -> parseDouble(value, 0d);
            case LIST -> Arrays.stream(value.split("\\R|,"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
            default -> value;
        };
    }

    private Object parseLoose(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(value); } catch (NumberFormatException ignored) {}
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return Boolean.parseBoolean(value);
        if (value.contains(",") || value.contains("\n")) {
            return Arrays.stream(value.split("\\R|,"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return value;
    }

    /** Strict: throws IllegalArgumentException on anything that is not clearly true/false. */
    private boolean parseBoolean(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("yes") || v.equals("on") || v.equals("enabled")) return true;
        if (v.equals("false") || v.equals("no") || v.equals("off") || v.equals("disabled")) return false;
        throw new IllegalArgumentException("Expected true or false (also yes/no/on/off), got '" + value + "'.");
    }

    /** Strict: throws NumberFormatException; negatives rejected. */
    private int parseInt(String value, int fallback) {
        int n = Integer.parseInt(value.trim());
        if (n < 0) throw new NumberFormatException("negative");
        return n;
    }

    private double parseDouble(String value, double fallback) {
        return Double.parseDouble(value.trim());
    }

    private <E extends Enum<E>> E strictEnum(Class<E> type, String value) {
        String v = value.trim().toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        for (E e : type.getEnumConstants()) if (e.name().equals(v)) return e;
        throw new IllegalArgumentException("Unknown value '" + value + "'. Valid values: "
                + Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(java.util.stream.Collectors.joining(", ")));
    }


    private static final List<String> ARENA_KEYS = List.of("enabled", "game-mode", "min-players", "max-players", "match-duration", "countdown",
            "goal-type", "goal-value", "respawn-type", "respawn-delay", "respawn-wave-interval", "spawn-protection", "lives", "chat-mode",
            "announcements", "mid-match-join", "spectator-allowed", "whitelist-enabled", "team-enabled",
            "team-count", "fall-damage", "fire-damage", "drowning", "suffocation", "friendly-fire",
            "block-break", "block-place", "item-drop", "hunger-drain", "visual-titles", "visual-sounds",
            "visual-particles", "visual-scoreboard", "visual-boss-bar", "visual-action-bar", "visual-holograms",
            "region-world", "region-min-x", "region-min-y", "region-min-z", "region-max-x", "region-max-y", "region-max-z");
    private static final java.util.Set<String> ARENA_BOOLEAN_KEYS = java.util.Set.of("enabled", "announcements", "mid-match-join",
            "spectator-allowed", "whitelist-enabled", "team-enabled", "fall-damage", "fire-damage", "drowning", "suffocation",
            "friendly-fire", "block-break", "block-place", "item-drop", "hunger-drain", "visual-titles", "visual-sounds",
            "visual-particles", "visual-scoreboard", "visual-boss-bar", "visual-action-bar", "visual-holograms");

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.filter(List.of("set", "get"), cur);
        String a = args[0].toLowerCase(Locale.ROOT);
        if (!a.equals("set") && !a.equals("get")) return List.of();
        boolean arenaScoped = args.length >= 2 && ArenaManager.getInstance().get(args[1]).isPresent();
        if (args.length == 2) {
            List<String> opts = new java.util.ArrayList<>(TabUtil.arenas(""));
            schema.settings().forEach(d -> opts.add(d.key()));
            return TabUtil.filter(opts, cur);
        }
        if (args.length == 3) {
            if (arenaScoped) return TabUtil.filter(ARENA_KEYS, cur);
            if (a.equals("set")) {
                SettingDefinition def = schema.settings().stream()
                        .filter(s -> s.key().equalsIgnoreCase(args[1])).findFirst().orElse(null);
                return TabUtil.settingValue(def, cur);
            }
            return List.of();
        }
        if (args.length == 4 && a.equals("set") && arenaScoped) {
            String key = args[2].toLowerCase(Locale.ROOT);
            if (ARENA_BOOLEAN_KEYS.contains(key)) return TabUtil.bools(cur);
            return switch (key) {
                case "game-mode", "gamemode" -> TabUtil.enums(GameModeType.class, cur);
                case "goal-type" -> TabUtil.enums(GoalType.class, cur);
                case "respawn-type" -> TabUtil.enums(RespawnType.class, cur);
                case "chat-mode" -> TabUtil.enums(ChatMode.class, cur);
                case "region-world" -> TabUtil.worlds(cur);
                default -> ARENA_KEYS.contains(key) ? TabUtil.hint("<number>", cur) : List.of();
            };
        }
        return List.of();
    }
}
