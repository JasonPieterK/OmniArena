package com.omniarena.web;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaRegion;
import com.omniarena.arena.ArenaRules;
import com.omniarena.arena.ArenaTeamConfig;
import com.omniarena.arena.ArenaVisualConfig;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.GoalType;
import com.omniarena.match.RespawnType;
import com.omniarena.gamemode.GameModeType;
import com.omniarena.arena.ChatMode;
import com.omniarena.web.model.ArenaView;
import com.omniarena.web.model.ConfigSnapshot;
import com.omniarena.web.model.SettingDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ConfigSchemaService {

    private final OmniArena plugin;

    public ConfigSchemaService(OmniArena plugin) {
        this.plugin = plugin;
    }

    public ConfigSnapshot snapshot() {
        return new ConfigSnapshot(
                System.currentTimeMillis(),
                settings(),
                globals(),
                arenas(),
                "OmniArena Docs",
                List.of("Setup", "Commands", "Arenas", "Kits", "Regen", "Integrations", "Troubleshooting")
        );
    }

    public LinkedHashMap<String, Object> globals() {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (SettingDefinition def : settings()) {
            Object current = currentValue(def.key());
            values.put(def.key(), current != null ? current : defaultTypedValue(def));
        }
        return values;
    }

    public List<SettingDefinition> settings() {
        List<SettingDefinition> defs = new ArrayList<>();
        addFlattenedSection(defs, loadConfigSource(), "Config", false);
        addFlattenedSection(defs, loadMessagesSource(), "Messages", true);
        return defs;
    }

    public List<ArenaView> arenas() {
        List<ArenaView> list = new ArrayList<>();
        for (Arena arena : ArenaManager.getInstance().getAll()) {
            ArenaRegion r = arena.getRegion();
            ArenaRules rules = arena.getRules();
            ArenaTeamConfig team = arena.getTeamConfig();
            ArenaVisualConfig visual = arena.getVisual();
            list.add(new ArenaView(
                    arena.getName(),
                    arena.isEnabled(),
                    arena.getState().name(),
                    arena.getGameMode().name(),
                    arena.getMinPlayers(),
                    arena.getMaxPlayers(),
                    arena.getMatchDuration(),
                    arena.getCountdown(),
                    arena.getGoalType().name(),
                    arena.getGoalValue(),
                    arena.getRespawnType().name(),
                    arena.getRespawnDelay(),
                    arena.getSpawnProtection(),
                    arena.getLives(),
                    arena.getRespawnWaveInterval(),
                    arena.getChatMode().name(),
                    arena.isAnnouncements(),
                    arena.isMidMatchJoin(),
                    arena.isSpectatorAllowed(),
                    arena.isWhitelistEnabled(),
                    r.isDefined() ? r.getWorldName() : "",
                    r.isDefined() ? r.getMinX() : null,
                    r.isDefined() ? r.getMinY() : null,
                    r.isDefined() ? r.getMinZ() : null,
                    r.isDefined() ? r.getMaxX() : null,
                    r.isDefined() ? r.getMaxY() : null,
                    r.isDefined() ? r.getMaxZ() : null,
                    new ArrayList<>(arena.getEnabledModules()),
                    arena.getBattleSpawns().stream().map(Object::toString).toList(),
                    arena.getLobbySpawns().stream().map(Object::toString).toList(),
                    arena.getSpectatorSpawns().stream().map(Object::toString).toList(),
                    rules.isFallDamage(),
                    rules.isFireDamage(),
                    rules.isDrowning(),
                    rules.isSuffocation(),
                    rules.isFriendlyFire(),
                    rules.isBlockBreak(),
                    rules.isBlockPlace(),
                    rules.isItemDrop(),
                    rules.isHungerDrain(),
                    team.isEnabled(),
                    team.getTeamCount(),
                    visual.isTitles(),
                    visual.isSounds(),
                    visual.isParticles(),
                    visual.isScoreboard(),
                    visual.isBossBar(),
                    visual.isActionBar(),
                    visual.isHolograms()
            ));
        }
        return list;
    }

    private void addFlattenedSection(List<SettingDefinition> defs, FileConfiguration source, String sourceName, boolean messages) {
        if (source == null) return;
        Set<String> keys = new LinkedHashSet<>();
        flattenSection(source, "", keys);
        for (String key : keys) {
            Object value = source.get(key);
            String group = groupForKey(key, messages);
            String label = labelForKey(key, messages);
            defs.add(new SettingDefinition(
                    messages ? "messages." + key : key,
                    label,
                    group,
                    inferType(key, value),
                    describe(sourceName, key),
                    stringify(value),
                    true
            ));
        }
    }

    private void flattenSection(ConfigurationSection section, String prefix, Set<String> out) {
        for (String child : section.getKeys(false)) {
            String key = prefix.isEmpty() ? child : prefix + "." + child;
            ConfigurationSection nested = section.getConfigurationSection(child);
            if (nested != null && !nested.getKeys(false).isEmpty()) {
                flattenSection(nested, key, out);
            } else {
                out.add(key);
            }
        }
    }

    private SettingDefinition.SettingType inferType(String key, Object value) {
        if (value instanceof Boolean) return SettingDefinition.SettingType.BOOLEAN;
        if (value instanceof Integer || value instanceof Long) return SettingDefinition.SettingType.INTEGER;
        if (value instanceof Number) return SettingDefinition.SettingType.DECIMAL;
        if (value instanceof List<?> || value instanceof Set<?>) return SettingDefinition.SettingType.LIST;
        if (value instanceof String s) {
            if (looksLikeEnumKey(key, s)) return SettingDefinition.SettingType.SELECT;
            if (s.contains("#") || s.contains("rgb")) return SettingDefinition.SettingType.COLOR;
        }
        return SettingDefinition.SettingType.STRING;
    }

    private boolean looksLikeEnumKey(String key, String value) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.endsWith("type")
                || normalized.endsWith("mode")
                || normalized.endsWith("color")
                || normalized.endsWith("sound")
                || normalized.endsWith("particle")
                || normalized.endsWith("particle-type")
                || normalized.endsWith("game-mode")
                || normalized.endsWith("respawn-type")
                || normalized.endsWith("goal-type")
                || normalized.endsWith("chat-mode")
                || normalized.endsWith("timer-color")
                || normalized.endsWith("low-time-color")
                || value.matches("[A-Z0-9_\\-]+");
    }

    private String groupForKey(String key, boolean messages) {
        if (messages) {
            String prefix = key.contains("-") ? key.substring(0, key.indexOf('-')) : key;
            return titleCase(prefix);
        }
        String prefix = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
        return titleCase(prefix);
    }

    private String labelForKey(String key, boolean messages) {
        String base = key;
        if (key.contains(".")) {
            base = key.substring(key.indexOf('.') + 1);
        }
        if (messages && !key.contains(".")) {
            base = key;
        }
        return titleCase(base.replace('.', ' ').replace('-', ' '));
    }

    private String describe(String sourceName, String key) {
        return sourceName + " setting: " + key;
    }

    private String stringify(Object value) {
        if (value == null) return "";
        if (value instanceof List<?> list) return String.join("\n", list.stream().map(String::valueOf).toList());
        if (value instanceof Set<?> set) return String.join("\n", set.stream().map(String::valueOf).toList());
        return String.valueOf(value);
    }

    private Object defaultTypedValue(SettingDefinition def) {
        return switch (def.type()) {
            case BOOLEAN -> Boolean.parseBoolean(def.defaultValue());
            case INTEGER -> parseInt(def.defaultValue(), 0);
            case DECIMAL -> parseDouble(def.defaultValue(), 0d);
            case LIST -> def.defaultValue().isBlank() ? List.of() : List.of(def.defaultValue().split("\\R"));
            default -> def.defaultValue();
        };
    }

    public Object currentValue(String key) {
        ConfigManager manager = ConfigManager.getInstance();
        if (manager != null) {
            return manager.getValue(key);
        }
        FileConfiguration source = key != null && key.startsWith("messages.")
                ? loadMessagesSource()
                : loadConfigSource();
        if (source == null || key == null) return null;
        String actualKey = key.startsWith("messages.") ? key.substring("messages.".length()) : key;
        return source.get(actualKey);
    }

    private FileConfiguration loadConfigSource() {
        ConfigManager manager = ConfigManager.getInstance();
        if (manager != null && manager.getConfig() != null) {
            return manager.getConfig();
        }
        return loadResourceConfig("config.yml");
    }

    private FileConfiguration loadMessagesSource() {
        ConfigManager manager = ConfigManager.getInstance();
        if (manager != null && manager.getMessages() != null) {
            return manager.getMessages();
        }
        return loadResourceConfig("messages.yml");
    }

    private FileConfiguration loadResourceConfig(String resource) {
        try (InputStream in = ConfigSchemaService.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private String titleCase(String input) {
        StringBuilder out = new StringBuilder();
        for (String part : input.split("\\s+")) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
