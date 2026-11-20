package com.omniarena.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.omniarena.arena.*;
import com.omniarena.config.ConfigManager;
import com.omniarena.gamemode.GameModeType;
import com.omniarena.match.GoalType;
import com.omniarena.match.RespawnType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validates then applies dashboard changes. Nothing is applied unless every value is valid.
 */
public class ConfigApplyService {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_.:\\- ]{1,64}");
    private static final Pattern WORLD = Pattern.compile("[A-Za-z0-9_.\\- ]{1,64}");
    private static final int MAX_INT = 1_000_000_000;

    /** Thrown-free result: empty list means everything was applied. */
    public List<String> apply(JsonObject payload, ConfigSchemaService schemaService) {
        List<String> errors = new ArrayList<>();
        if (payload == null) return errors;

        Map<String, Object> globals = validateGlobals(payload.get("globals"), schemaService, errors);
        Map<String, JsonObject> arenas = collectArenas(payload, errors);
        for (Map.Entry<String, JsonObject> e : arenas.entrySet()) {
            Arena arena = ArenaManager.getInstance().get(e.getKey()).orElse(null);
            if (arena == null) { errors.add("arena " + e.getKey() + ": unknown arena"); continue; }
            validateArena(e.getKey(), arena, e.getValue(), errors);
        }
        if (!errors.isEmpty()) return errors;

        if (!globals.isEmpty()) ConfigManager.getInstance().setValues(globals); // one save for all keys
        for (Map.Entry<String, JsonObject> e : arenas.entrySet()) {
            Arena arena = ArenaManager.getInstance().get(e.getKey()).orElse(null);
            if (arena == null) continue;
            applyArena(arena, e.getValue());
            ArenaManager.getInstance().save(arena);
        }
        return errors;
    }

    // ── Collection ─────────────────────────────────────────────────────

    /** Accepts {"arena": {"name": {...}}} or the dashboard form {"arena": "name", "arenaSettings": {...}}. */
    private Map<String, JsonObject> collectArenas(JsonObject payload, List<String> errors) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        JsonElement arena = payload.get("arena");
        if (arena == null || arena.isJsonNull()) return out;
        if (arena.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : arena.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonObject()) out.put(e.getKey(), e.getValue().getAsJsonObject());
                else errors.add("arena " + e.getKey() + ": settings must be an object");
            }
        } else if (arena.isJsonPrimitive() && arena.getAsJsonPrimitive().isString()) {
            JsonElement settings = payload.get("arenaSettings");
            if (settings != null && settings.isJsonObject()) out.put(arena.getAsString(), settings.getAsJsonObject());
        } else {
            errors.add("arena: invalid format");
        }
        return out;
    }

    // ── Globals ────────────────────────────────────────────────────────

    private Map<String, Object> validateGlobals(JsonElement el, ConfigSchemaService schemaService, List<String> errors) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (el == null || el.isJsonNull()) return out;
        if (!el.isJsonObject()) { errors.add("globals: must be an object"); return out; }
        for (Map.Entry<String, JsonElement> entry : el.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            var def = schemaService.settings().stream().filter(d -> d.key().equalsIgnoreCase(key)).findFirst().orElse(null);
            if (def == null) { errors.add(key + ": unknown setting"); continue; }
            int before = errors.size();
            Object value = convertAndValidate(def, entry.getValue(), errors);
            if (errors.size() != before) continue;
            Object current = ConfigManager.getInstance().getValue(def.key());
            if (sameValue(current, value)) continue; // unchanged: skip
            out.put(def.key(), value);
        }
        return out;
    }

    private static boolean sameValue(Object a, Object b) {
        if (Objects.equals(a, b)) return true;
        if (a instanceof Number x && b instanceof Number y) return x.doubleValue() == y.doubleValue();
        if (a instanceof List<?> l1 && b instanceof List<?> l2 && l1.size() == l2.size()) {
            for (int i = 0; i < l1.size(); i++) if (!Objects.equals(String.valueOf(l1.get(i)), String.valueOf(l2.get(i)))) return false;
            return true;
        }
        return false;
    }

    private Object convertAndValidate(com.omniarena.web.model.SettingDefinition def, JsonElement value, List<String> errors) {
        String key = def.key();
        if (value == null || value.isJsonNull()) { errors.add(key + ": value is required"); return null; }
        if ("general.startup-banner".equals(key)) {
            String s = value.isJsonPrimitive() ? value.getAsString().trim().toLowerCase(java.util.Locale.ROOT) : "";
            if (!s.equals("full") && !s.equals("compact") && !s.equals("off")) {
                errors.add(key + ": must be full, compact or off");
                return null;
            }
            return s;
        }
        switch (def.type()) {
            case BOOLEAN -> {
                if (value.isJsonPrimitive()) {
                    JsonPrimitive p = value.getAsJsonPrimitive();
                    if (p.isBoolean()) return p.getAsBoolean();
                    if (p.isString() && ("true".equalsIgnoreCase(p.getAsString()) || "false".equalsIgnoreCase(p.getAsString())))
                        return Boolean.parseBoolean(p.getAsString());
                }
                errors.add(key + ": must be true or false");
                return null;
            }
            case INTEGER -> {
                Double d = number(value);
                if (d == null || d != Math.rint(d)) { errors.add(key + ": must be a whole number"); return null; }
                long max = key.toLowerCase().endsWith("port") ? 65535 : MAX_INT;
                long min = key.toLowerCase().endsWith("port") ? 1 : 0;
                if (d < min || d > max) { errors.add(key + ": must be between " + min + " and " + max); return null; }
                return d.intValue();
            }
            case DECIMAL -> {
                Double d = number(value);
                if (d == null || d.isNaN() || d.isInfinite()) { errors.add(key + ": must be a number"); return null; }
                if (d < 0 || d > 1_000_000_000d) { errors.add(key + ": must be between 0 and 1000000000"); return null; }
                return d;
            }
            case LIST -> {
                List<String> items = new ArrayList<>();
                if (value.isJsonArray()) {
                    JsonArray arr = value.getAsJsonArray();
                    for (JsonElement item : arr) {
                        if (!item.isJsonPrimitive()) { errors.add(key + ": list entries must be text"); return null; }
                        items.add(item.getAsString());
                    }
                } else if (value.isJsonPrimitive()) {
                    for (String part : value.getAsString().split("\\R|,")) if (!part.isBlank()) items.add(part.trim());
                } else { errors.add(key + ": must be a list"); return null; }
                if (items.size() > 500) { errors.add(key + ": too many entries (max 500)"); return null; }
                for (String it : items) if (it.length() > 256 || hasControl(it)) { errors.add(key + ": invalid list entry"); return null; }
                return items;
            }
            case SELECT -> {
                if (!value.isJsonPrimitive()) { errors.add(key + ": must be text"); return null; }
                String s = value.getAsString().trim();
                if (!TOKEN.matcher(s).matches()) { errors.add(key + ": invalid option '" + abbreviate(s) + "'"); return null; }
                String lk = key.toLowerCase();
                if ((lk.endsWith("game-mode")) && !enumValid(GameModeType.class, s)) {
                    errors.add(key + ": unknown game mode '" + abbreviate(s) + "'"); return null;
                }
                if (lk.endsWith("goal-type") && !enumValid(GoalType.class, s)) {
                    errors.add(key + ": unknown goal type '" + abbreviate(s) + "'"); return null;
                }
                if (lk.endsWith("respawn-type") && !enumValid(RespawnType.class, s)) {
                    errors.add(key + ": unknown respawn type '" + abbreviate(s) + "'"); return null;
                }
                if (lk.endsWith("chat-mode") && !enumValid(ChatMode.class, s)) {
                    errors.add(key + ": unknown chat mode '" + abbreviate(s) + "'"); return null;
                }
                return s;
            }
            default -> {
                if (!value.isJsonPrimitive()) { errors.add(key + ": must be text"); return null; }
                String s = value.getAsString();
                if (s.length() > 2048 || (hasControl(s) && !s.contains("\n"))) { errors.add(key + ": invalid text"); return null; }
                return s;
            }
        }
    }

    private static Double number(JsonElement v) {
        if (!v.isJsonPrimitive()) return null;
        JsonPrimitive p = v.getAsJsonPrimitive();
        try {
            if (p.isNumber()) return p.getAsDouble();
            if (p.isString() && !p.getAsString().isBlank()) return Double.parseDouble(p.getAsString().trim());
        } catch (NumberFormatException ignored) { }
        return null;
    }

    private static boolean hasControl(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 && c != '\n' && c != '\t') return true;
        }
        return false;
    }

    private static String abbreviate(String s) { return s.length() > 40 ? s.substring(0, 40) + "..." : s; }

    private static <E extends Enum<E>> boolean enumValid(Class<E> type, String s) {
        try { Enum.valueOf(type, s.toUpperCase().replace("-", "_").replace(" ", "_")); return true; }
        catch (IllegalArgumentException e) { return false; }
    }

    // ── Arena validation ───────────────────────────────────────────────

    private void validateArena(String name, Arena arena, JsonObject obj, List<String> errors) {
        String p = "arena " + name + ": ";
        if (present(obj, "enabled")) boolOk(obj.get("enabled"), p + "enabled", errors);
        for (String k : new String[]{"announcements", "midMatchJoin", "spectatorAllowed", "whitelistEnabled"})
            if (present(obj, k)) boolOk(obj.get(k), p + k, errors);

        if (present(obj, "gameMode") && !enumOk(GameModeType.class, obj.get("gameMode"))) errors.add(p + "gameMode: unknown game mode");
        if (present(obj, "goalType") && !enumOk(GoalType.class, obj.get("goalType"))) errors.add(p + "goalType: unknown goal type");
        if (present(obj, "respawnType") && !enumOk(RespawnType.class, obj.get("respawnType"))) errors.add(p + "respawnType: unknown respawn type");
        if (present(obj, "chatMode") && !enumOk(ChatMode.class, obj.get("chatMode"))) errors.add(p + "chatMode: unknown chat mode");

        Integer min = intIn(obj, "minPlayers", 1, 200, p, errors);
        Integer max = intIn(obj, "maxPlayers", 1, 200, p, errors);
        int effMin = min != null ? min : arena.getMinPlayers();
        int effMax = max != null ? max : arena.getMaxPlayers();
        if ((min != null || max != null) && effMin > effMax) errors.add(p + "minPlayers cannot exceed maxPlayers");
        intIn(obj, "matchDuration", 1, 86400, p, errors);
        intIn(obj, "countdown", 0, 3600, p, errors);
        intIn(obj, "goalValue", 0, MAX_INT, p, errors);
        intIn(obj, "respawnDelay", 0, 3600, p, errors);
        intIn(obj, "spawnProtection", 0, 3600, p, errors);
        intIn(obj, "lives", 0, 100, p, errors);
        intIn(obj, "respawnWaveInterval", 1, 3600, p, errors);

        if (obj.has("region") && obj.get("region").isJsonObject()) {
            JsonObject r = obj.getAsJsonObject("region");
            String rp = p + "region.";
            if (present(r, "worldName")) {
                JsonElement w = r.get("worldName");
                if (!w.isJsonPrimitive() || !WORLD.matcher(w.getAsString()).matches()) errors.add(rp + "worldName: invalid world name");
            }
            for (String k : new String[]{"minX", "minZ", "maxX", "maxZ"}) intIn(r, k, -30_000_000, 30_000_000, rp, errors);
            for (String k : new String[]{"minY", "maxY"}) intIn(r, k, -2048, 2048, rp, errors);
        }
        if (obj.has("rules") && obj.get("rules").isJsonObject()) {
            JsonObject r = obj.getAsJsonObject("rules");
            for (String k : new String[]{"fallDamage", "fireDamage", "drowning", "suffocation", "friendlyFire",
                    "blockBreak", "blockPlace", "itemDrop", "hungerDrain"})
                if (present(r, k)) boolOk(r.get(k), p + "rules." + k, errors);
        }
        if (obj.has("visual") && obj.get("visual").isJsonObject()) {
            JsonObject v = obj.getAsJsonObject("visual");
            for (String k : new String[]{"titles", "sounds", "particles", "scoreboard", "bossBar", "actionBar"})
                if (present(v, k)) boolOk(v.get(k), p + "visual." + k, errors);
        }
        if (obj.has("team") && obj.get("team").isJsonObject()) {
            JsonObject t = obj.getAsJsonObject("team");
            if (present(t, "enabled")) boolOk(t.get("enabled"), p + "team.enabled", errors);
            intIn(t, "count", 1, 16, p + "team.", errors);
        }
    }

    private static boolean present(JsonObject o, String k) {
        if (!o.has(k)) return false;
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) return false;
        return !(e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && e.getAsString().isBlank());
    }

    private static void boolOk(JsonElement e, String label, List<String> errors) {
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean()) return;
            if (p.isString() && ("true".equalsIgnoreCase(p.getAsString()) || "false".equalsIgnoreCase(p.getAsString()))) return;
        }
        errors.add(label + ": must be true or false");
    }

    private static <E extends Enum<E>> boolean enumOk(Class<E> type, JsonElement e) {
        return e.isJsonPrimitive() && enumValid(type, e.getAsString().trim());
    }

    private static Integer intIn(JsonObject o, String k, int min, int max, String prefix, List<String> errors) {
        if (!present(o, k)) return null;
        Double d = number(o.get(k));
        if (d == null || d != Math.rint(d)) { errors.add(prefix + k + ": must be a whole number"); return null; }
        if (d < min || d > max) { errors.add(prefix + k + ": must be between " + min + " and " + max); return null; }
        return d.intValue();
    }

    private static boolean bool(JsonElement e) { return e.getAsBoolean(); }
    private static int num(JsonElement e) { return (int) Math.rint(e.getAsDouble()); }

    // ── Apply (values already validated) ───────────────────────────────

    private void applyArena(Arena arena, JsonObject obj) {
        if (present(obj, "enabled")) {
            boolean want = bool(obj.get("enabled"));
            // Only flip on a real change: never knock a running/starting arena back to WAITING.
            if (want != arena.isEnabled()) arena.setState(want ? ArenaState.WAITING : ArenaState.DISABLED);
        }
        if (present(obj, "gameMode")) arena.setGameMode(GameModeType.fromString(obj.get("gameMode").getAsString()));
        if (present(obj, "minPlayers")) arena.setMinPlayers(num(obj.get("minPlayers")));
        if (present(obj, "maxPlayers")) arena.setMaxPlayers(num(obj.get("maxPlayers")));
        if (present(obj, "matchDuration")) arena.setMatchDuration(num(obj.get("matchDuration")));
        if (present(obj, "countdown")) arena.setCountdown(num(obj.get("countdown")));
        if (present(obj, "goalType")) arena.setGoalType(GoalType.fromString(obj.get("goalType").getAsString()));
        if (present(obj, "goalValue")) arena.setGoalValue(num(obj.get("goalValue")));
        if (present(obj, "respawnType")) arena.setRespawnType(RespawnType.fromString(obj.get("respawnType").getAsString()));
        if (present(obj, "respawnDelay")) arena.setRespawnDelay(num(obj.get("respawnDelay")));
        if (present(obj, "spawnProtection")) arena.setSpawnProtection(num(obj.get("spawnProtection")));
        if (present(obj, "lives")) arena.setLives(num(obj.get("lives")));
        if (present(obj, "respawnWaveInterval")) arena.setRespawnWaveInterval(num(obj.get("respawnWaveInterval")));
        if (present(obj, "chatMode")) arena.setChatMode(ChatMode.fromString(obj.get("chatMode").getAsString()));
        if (present(obj, "announcements")) arena.setAnnouncements(bool(obj.get("announcements")));
        if (present(obj, "midMatchJoin")) arena.setMidMatchJoin(bool(obj.get("midMatchJoin")));
        if (present(obj, "spectatorAllowed")) arena.setSpectatorAllowed(bool(obj.get("spectatorAllowed")));
        if (present(obj, "whitelistEnabled")) arena.setWhitelistEnabled(bool(obj.get("whitelistEnabled")));

        if (obj.has("region") && obj.get("region").isJsonObject()) {
            JsonObject region = obj.getAsJsonObject("region");
            ArenaRegion r = arena.getRegion();
            if (present(region, "worldName")) r.setWorldName(region.get("worldName").getAsString());
            if (present(region, "minX")) r.setMinX(num(region.get("minX")));
            if (present(region, "minY")) r.setMinY(num(region.get("minY")));
            if (present(region, "minZ")) r.setMinZ(num(region.get("minZ")));
            if (present(region, "maxX")) r.setMaxX(num(region.get("maxX")));
            if (present(region, "maxY")) r.setMaxY(num(region.get("maxY")));
            if (present(region, "maxZ")) r.setMaxZ(num(region.get("maxZ")));
        }

        if (obj.has("rules") && obj.get("rules").isJsonObject()) {
            JsonObject rules = obj.getAsJsonObject("rules");
            ArenaRules r = arena.getRules();
            if (present(rules, "fallDamage")) r.setFallDamage(bool(rules.get("fallDamage")));
            if (present(rules, "fireDamage")) r.setFireDamage(bool(rules.get("fireDamage")));
            if (present(rules, "drowning")) r.setDrowning(bool(rules.get("drowning")));
            if (present(rules, "suffocation")) r.setSuffocation(bool(rules.get("suffocation")));
            if (present(rules, "friendlyFire")) r.setFriendlyFire(bool(rules.get("friendlyFire")));
            if (present(rules, "blockBreak")) r.setBlockBreak(bool(rules.get("blockBreak")));
            if (present(rules, "blockPlace")) r.setBlockPlace(bool(rules.get("blockPlace")));
            if (present(rules, "itemDrop")) r.setItemDrop(bool(rules.get("itemDrop")));
            if (present(rules, "hungerDrain")) r.setHungerDrain(bool(rules.get("hungerDrain")));
        }

        if (obj.has("team") && obj.get("team").isJsonObject()) {
            JsonObject team = obj.getAsJsonObject("team");
            ArenaTeamConfig t = arena.getTeamConfig();
            if (present(team, "enabled")) t.setEnabled(bool(team.get("enabled")));
            if (present(team, "count")) t.setTeamCount(num(team.get("count")));
        }

        if (obj.has("visual") && obj.get("visual").isJsonObject()) {
            JsonObject visual = obj.getAsJsonObject("visual");
            ArenaVisualConfig v = arena.getVisual();
            if (present(visual, "titles")) v.setTitles(bool(visual.get("titles")));
            if (present(visual, "sounds")) v.setSounds(bool(visual.get("sounds")));
            if (present(visual, "particles")) v.setParticles(bool(visual.get("particles")));
            if (present(visual, "scoreboard")) v.setScoreboard(bool(visual.get("scoreboard")));
            if (present(visual, "bossBar")) v.setBossBar(bool(visual.get("bossBar")));
            if (present(visual, "actionBar")) v.setActionBar(bool(visual.get("actionBar")));
        }
    }
}
