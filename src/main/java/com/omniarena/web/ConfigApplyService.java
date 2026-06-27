package com.omniarena.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.omniarena.arena.*;
import com.omniarena.config.ConfigManager;
import com.omniarena.gamemode.GameModeType;
import com.omniarena.match.GoalType;
import com.omniarena.match.RespawnType;

import java.util.List;
import java.util.Map;

public class ConfigApplyService {

    public void apply(JsonObject payload, ConfigSchemaService schemaService) {
        if (payload == null) return;
        applyGlobals(payload.getAsJsonObject("globals"), schemaService);
        applyArenas(payload.getAsJsonObject("arena"), schemaService);
    }

    private void applyGlobals(JsonObject globals, ConfigSchemaService schemaService) {
        if (globals == null) return;
        for (Map.Entry<String, JsonElement> entry : globals.entrySet()) {
            String key = entry.getKey();
            Object value = convert(schemaService, key, entry.getValue());
            ConfigManager.getInstance().setValue(key, value);
        }
    }

    private void applyArenas(JsonObject arenaObject, ConfigSchemaService schemaService) {
        if (arenaObject == null) return;
        for (Map.Entry<String, JsonElement> entry : arenaObject.entrySet()) {
            String arenaName = entry.getKey();
            Arena arena = ArenaManager.getInstance().get(arenaName).orElse(null);
            if (arena == null || !entry.getValue().isJsonObject()) continue;
            JsonObject obj = entry.getValue().getAsJsonObject();
            applyArena(arena, obj);
            ArenaManager.getInstance().save(arena);
        }
    }

    private void applyArena(Arena arena, JsonObject obj) {
        if (obj.has("enabled")) arena.setState(obj.get("enabled").getAsBoolean() ? ArenaState.WAITING : ArenaState.DISABLED);
        if (obj.has("gameMode")) arena.setGameMode(GameModeType.fromString(obj.get("gameMode").getAsString()));
        if (obj.has("minPlayers")) arena.setMinPlayers(obj.get("minPlayers").getAsInt());
        if (obj.has("maxPlayers")) arena.setMaxPlayers(obj.get("maxPlayers").getAsInt());
        if (obj.has("matchDuration")) arena.setMatchDuration(obj.get("matchDuration").getAsInt());
        if (obj.has("countdown")) arena.setCountdown(obj.get("countdown").getAsInt());
        if (obj.has("goalType")) arena.setGoalType(GoalType.fromString(obj.get("goalType").getAsString()));
        if (obj.has("goalValue")) arena.setGoalValue(obj.get("goalValue").getAsInt());
        if (obj.has("respawnType")) arena.setRespawnType(RespawnType.fromString(obj.get("respawnType").getAsString()));
        if (obj.has("respawnDelay")) arena.setRespawnDelay(obj.get("respawnDelay").getAsInt());
        if (obj.has("spawnProtection")) arena.setSpawnProtection(obj.get("spawnProtection").getAsInt());
        if (obj.has("chatMode")) arena.setChatMode(ChatMode.fromString(obj.get("chatMode").getAsString()));
        if (obj.has("announcements")) arena.setAnnouncements(obj.get("announcements").getAsBoolean());
        if (obj.has("midMatchJoin")) arena.setMidMatchJoin(obj.get("midMatchJoin").getAsBoolean());
        if (obj.has("spectatorAllowed")) arena.setSpectatorAllowed(obj.get("spectatorAllowed").getAsBoolean());
        if (obj.has("whitelistEnabled")) arena.setWhitelistEnabled(obj.get("whitelistEnabled").getAsBoolean());

        if (obj.has("region") && obj.get("region").isJsonObject()) {
            JsonObject region = obj.getAsJsonObject("region");
            ArenaRegion r = arena.getRegion();
            if (region.has("worldName")) r.setWorldName(region.get("worldName").getAsString());
            if (region.has("minX")) r.setMinX(region.get("minX").getAsInt());
            if (region.has("minY")) r.setMinY(region.get("minY").getAsInt());
            if (region.has("minZ")) r.setMinZ(region.get("minZ").getAsInt());
            if (region.has("maxX")) r.setMaxX(region.get("maxX").getAsInt());
            if (region.has("maxY")) r.setMaxY(region.get("maxY").getAsInt());
            if (region.has("maxZ")) r.setMaxZ(region.get("maxZ").getAsInt());
        }

        if (obj.has("rules") && obj.get("rules").isJsonObject()) {
            JsonObject rules = obj.getAsJsonObject("rules");
            ArenaRules r = arena.getRules();
            if (rules.has("fallDamage")) r.setFallDamage(rules.get("fallDamage").getAsBoolean());
            if (rules.has("fireDamage")) r.setFireDamage(rules.get("fireDamage").getAsBoolean());
            if (rules.has("drowning")) r.setDrowning(rules.get("drowning").getAsBoolean());
            if (rules.has("suffocation")) r.setSuffocation(rules.get("suffocation").getAsBoolean());
            if (rules.has("friendlyFire")) r.setFriendlyFire(rules.get("friendlyFire").getAsBoolean());
            if (rules.has("blockBreak")) r.setBlockBreak(rules.get("blockBreak").getAsBoolean());
            if (rules.has("blockPlace")) r.setBlockPlace(rules.get("blockPlace").getAsBoolean());
            if (rules.has("itemDrop")) r.setItemDrop(rules.get("itemDrop").getAsBoolean());
            if (rules.has("hungerDrain")) r.setHungerDrain(rules.get("hungerDrain").getAsBoolean());
        }

        if (obj.has("team") && obj.get("team").isJsonObject()) {
            JsonObject team = obj.getAsJsonObject("team");
            ArenaTeamConfig t = arena.getTeamConfig();
            if (team.has("enabled")) t.setEnabled(team.get("enabled").getAsBoolean());
            if (team.has("count")) t.setTeamCount(team.get("count").getAsInt());
        }

        if (obj.has("visual") && obj.get("visual").isJsonObject()) {
            JsonObject visual = obj.getAsJsonObject("visual");
            ArenaVisualConfig v = arena.getVisual();
            if (visual.has("titles")) v.setTitles(visual.get("titles").getAsBoolean());
            if (visual.has("sounds")) v.setSounds(visual.get("sounds").getAsBoolean());
            if (visual.has("particles")) v.setParticles(visual.get("particles").getAsBoolean());
            if (visual.has("scoreboard")) v.setScoreboard(visual.get("scoreboard").getAsBoolean());
            if (visual.has("bossBar")) v.setBossBar(visual.get("bossBar").getAsBoolean());
            if (visual.has("actionBar")) v.setActionBar(visual.get("actionBar").getAsBoolean());
            if (visual.has("holograms")) v.setHolograms(visual.get("holograms").getAsBoolean());
        }
    }

    private Object convert(ConfigSchemaService schemaService, String key, JsonElement value) {
        for (var def : schemaService.settings()) {
            if (!def.key().equalsIgnoreCase(key)) continue;
            return switch (def.type()) {
                case BOOLEAN -> value.getAsBoolean();
                case INTEGER -> value.getAsInt();
                case DECIMAL -> value.getAsDouble();
                case LIST -> {
                    if (value.isJsonArray()) {
                        List<String> items = new java.util.ArrayList<>();
                        value.getAsJsonArray().forEach(item -> items.add(item.getAsString()));
                        yield items;
                    }
                    String raw = value.getAsString();
                    yield java.util.Arrays.stream(raw.split("\\R|,"))
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .toList();
                }
                default -> value.getAsString();
            };
        }
        if (value.isJsonPrimitive()) {
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) {
                double d = primitive.getAsDouble();
                return d == Math.rint(d) ? (int) d : d;
            }
            return primitive.getAsString();
        }
        return value.toString();
    }
}
