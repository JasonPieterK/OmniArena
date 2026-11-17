package com.omniarena.config;

import com.omniarena.OmniArena;
import com.omniarena.arena.*;
import com.omniarena.gamemode.GameModeType;
import com.omniarena.match.GoalType;
import com.omniarena.match.RespawnType;
import com.omniarena.sign.ArenaSign;
import com.omniarena.util.LocationUtil;
import com.omniarena.util.YamlUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ArenaConfig {

    private final OmniArena plugin;
    private final File arenasFolder;

    public ArenaConfig(OmniArena plugin) {
        this.plugin = plugin;
        this.arenasFolder = new File(plugin.getDataFolder(), "arenas");
        if (!arenasFolder.exists()) arenasFolder.mkdirs();
    }

    public List<Arena> loadAll() {
        List<Arena> arenas = new ArrayList<>();
        File[] files = arenasFolder.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("example.yml"));
        if (files == null) return arenas;
        for (File file : files) {
            try {
                Arena arena = load(file);
                if (arena != null) arenas.add(arena);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load arena file plugins/OmniArena/arenas/" + file.getName() + " (" + e + "). The arena was skipped. Fix: open that file and correct the YAML (indentation, quotes, valid world/enum names) or delete it, then run /oa reload; recreate with /oa create <name> if needed.");
            }
        }
        return arenas;
    }

    public Arena load(File file) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String name = cfg.getString("name", file.getName().replace(".yml", ""));
        Arena arena = new Arena(name);

        arena.setState(cfg.getBoolean("enabled", true) ? ArenaState.WAITING : ArenaState.DISABLED);
        arena.setGameMode(GameModeType.fromString(cfg.getString("game-mode", "DEATHMATCH")));
        arena.setMinPlayers(cfg.getInt("min-players", 2));
        arena.setMaxPlayers(cfg.getInt("max-players", 16));
        arena.setMatchDuration(cfg.getInt("match-duration", 300));
        arena.setCountdown(cfg.getInt("countdown", 10));
        arena.setChatMode(ChatMode.fromString(cfg.getString("chat-mode", "ARENA")));
        arena.setAnnouncements(cfg.getBoolean("announcements", true));
        arena.setMidMatchJoin(cfg.getBoolean("mid-match-join", false));
        arena.setSpectatorAllowed(cfg.getBoolean("spectator-allowed", true));

        // Goal
        ConfigurationSection goal = cfg.getConfigurationSection("goal");
        if (goal != null) {
            // goal.type only overrides the game mode's goal when explicitly flagged (older files always wrote it)
            if (goal.getBoolean("type-override", false)) {
                arena.setGoalType(GoalType.fromString(goal.getString("type", "KILLS")));
            }
            arena.setGoalValue(goal.getInt("value", 20));
        }

        // Respawn
        ConfigurationSection respawn = cfg.getConfigurationSection("respawn");
        if (respawn != null) {
            arena.setRespawnType(RespawnType.fromString(respawn.getString("type", "INSTANT")));
            arena.setRespawnDelay(respawn.getInt("delay", 3));
            arena.setRespawnWaveInterval(respawn.getInt("wave-interval", 10));
        }
        arena.setSpawnProtection(cfg.getInt("spawn-protection", 3));
        arena.setLives(cfg.getInt("lives", 0));

        // Rules
        ConfigurationSection rules = cfg.getConfigurationSection("rules");
        if (rules != null) {
            ArenaRules r = arena.getRules();
            r.setFallDamage(rules.getBoolean("fall-damage", true));
            r.setFireDamage(rules.getBoolean("fire-damage", true));
            r.setDrowning(rules.getBoolean("drowning", true));
            r.setSuffocation(rules.getBoolean("suffocation", true));
            r.setFriendlyFire(rules.getBoolean("friendly-fire", false));
            r.setBlockBreak(rules.getBoolean("block-break", false));
            r.setBlockPlace(rules.getBoolean("block-place", false));
            r.setItemDrop(rules.getBoolean("item-drop", false));
            r.setHungerDrain(rules.getBoolean("hunger-drain", false));
        }

        // Teams
        ConfigurationSection team = cfg.getConfigurationSection("team");
        if (team != null) {
            ArenaTeamConfig t = arena.getTeamConfig();
            t.setEnabled(team.getBoolean("enabled", false));
            t.setTeamCount(team.getInt("count", 2));
        }

        // Modules
        List<String> mods = cfg.getStringList("modules.enabled");
        mods.forEach(arena::enableModule);

        // Region
        ConfigurationSection region = cfg.getConfigurationSection("region");
        if (region != null && !region.getString("world", "").isBlank()) {
            ArenaRegion r = arena.getRegion();
            r.setWorldName(region.getString("world", ""));
            r.setMinX(region.getInt("min-x")); r.setMinY(region.getInt("min-y")); r.setMinZ(region.getInt("min-z"));
            r.setMaxX(region.getInt("max-x")); r.setMaxY(region.getInt("max-y")); r.setMaxZ(region.getInt("max-z"));
        }

        // Spawns
        loadSpawns(cfg, arena, "spawns.battle",    SpawnType.BATTLE);
        loadSpawns(cfg, arena, "spawns.lobby",     SpawnType.LOBBY);
        loadSpawns(cfg, arena, "spawns.spectator", SpawnType.SPECTATOR);

        // Signs
        List<String> signList = cfg.getStringList("signs");
        for (String signStr : signList) {
            Location loc = LocationUtil.deserialize(signStr);
            if (loc != null) arena.getSigns().add(new ArenaSign(loc, name));
            else {
                arena.getUnparsedSigns().add(signStr); // preserved verbatim on save
                com.omniarena.OmniArena.getInstance().getLogger().warning("Arena '" + name + "': sign entry '" + signStr + "' is not active (bad format or world not loaded) but is kept in the file. Fix: edit the 'signs' list in arenas/" + name + ".yml, or re-create the sign with /oa sign create " + name + ".");
            }
        }

        // Access control (whitelist/blacklist of player UUIDs)
        arena.setWhitelistEnabled(cfg.getBoolean("access.whitelist-enabled", false));
        loadUuids(cfg.getStringList("access.whitelist"), arena.getWhitelist(), name, "whitelist");
        loadUuids(cfg.getStringList("access.blacklist"), arena.getBlacklist(), name, "blacklist");

        // End commands
        arena.getEndCommands().addAll(cfg.getStringList("end-commands"));

        // Visual
        ConfigurationSection vis = cfg.getConfigurationSection("visual");
        if (vis != null) {
            ArenaVisualConfig v = arena.getVisual();
            v.setTitles(vis.getBoolean("titles", true));
            v.setSounds(vis.getBoolean("sounds", true));
            v.setParticles(vis.getBoolean("particles", true));
            v.setScoreboard(vis.getBoolean("scoreboard", true));
            v.setBossBar(vis.getBoolean("boss-bar", true));
            v.setActionBar(vis.getBoolean("action-bar", true));
            v.setHolograms(vis.getBoolean("holograms", false));
        }

        return arena;
    }

    private void loadUuids(List<String> raw, java.util.Set<java.util.UUID> into, String arena, String what) {
        for (String s : raw) {
            try { into.add(java.util.UUID.fromString(s.trim())); }
            catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Arena '" + arena + "': ignoring invalid UUID '" + s + "' in access." + what + ". Fix: remove it from arenas/" + arena + ".yml or re-add the player with /oa access " + arena + " " + what + " add <player>.");
            }
        }
    }

    private void loadSpawns(FileConfiguration cfg, Arena arena, String path, SpawnType type) {
        List<String> list = cfg.getStringList(path);
        for (String s : list) {
            Location loc = LocationUtil.deserialize(s);
            if (loc != null) arena.addSpawn(type, loc);
        }
    }

    public void save(Arena arena) {
        File file = new File(arenasFolder, arena.getName() + ".yml");
        FileConfiguration cfg = new YamlConfiguration();

        cfg.set("name", arena.getName());
        cfg.set("enabled", arena.getState() != ArenaState.DISABLED);
        cfg.set("game-mode", arena.getGameMode().name());
        cfg.set("min-players", arena.getMinPlayers());
        cfg.set("max-players", arena.getMaxPlayers());
        cfg.set("match-duration", arena.getMatchDuration());
        cfg.set("countdown", arena.getCountdown());
        cfg.set("chat-mode", arena.getChatMode().name());
        cfg.set("announcements", arena.isAnnouncements());
        cfg.set("mid-match-join", arena.isMidMatchJoin());
        cfg.set("spectator-allowed", arena.isSpectatorAllowed());
        cfg.set("spawn-protection", arena.getSpawnProtection());
        cfg.set("lives", arena.getLives());

        cfg.set("goal.type", arena.getGoalType().name());
        if (arena.hasGoalTypeOverride()) cfg.set("goal.type-override", true);
        cfg.set("goal.value", arena.getGoalValue());

        cfg.set("respawn.type", arena.getRespawnType().name());
        cfg.set("respawn.delay", arena.getRespawnDelay());
        cfg.set("respawn.wave-interval", arena.getRespawnWaveInterval());

        ArenaRules r = arena.getRules();
        cfg.set("rules.fall-damage", r.isFallDamage());
        cfg.set("rules.fire-damage", r.isFireDamage());
        cfg.set("rules.drowning", r.isDrowning());
        cfg.set("rules.suffocation", r.isSuffocation());
        cfg.set("rules.friendly-fire", r.isFriendlyFire());
        cfg.set("rules.block-break", r.isBlockBreak());
        cfg.set("rules.block-place", r.isBlockPlace());
        cfg.set("rules.item-drop", r.isItemDrop());
        cfg.set("rules.hunger-drain", r.isHungerDrain());

        ArenaTeamConfig t = arena.getTeamConfig();
        cfg.set("team.enabled", t.isEnabled());
        cfg.set("team.count", t.getTeamCount());

        cfg.set("modules.enabled", new ArrayList<>(arena.getEnabledModules()));

        ArenaRegion reg = arena.getRegion();
        if (reg.isDefined()) {
            cfg.set("region.world", reg.getWorldName());
            cfg.set("region.min-x", reg.getMinX()); cfg.set("region.min-y", reg.getMinY()); cfg.set("region.min-z", reg.getMinZ());
            cfg.set("region.max-x", reg.getMaxX()); cfg.set("region.max-y", reg.getMaxY()); cfg.set("region.max-z", reg.getMaxZ());
        }

        // Spawns
        saveSpawnList(cfg, arena, "spawns.battle",    SpawnType.BATTLE);
        saveSpawnList(cfg, arena, "spawns.lobby",     SpawnType.LOBBY);
        saveSpawnList(cfg, arena, "spawns.spectator", SpawnType.SPECTATOR);

        // Signs
        List<String> signStrs = new java.util.ArrayList<>(arena.getSigns().stream()
                .map(s -> LocationUtil.serialize(s.getLocation()))
                .toList());
        signStrs.addAll(arena.getUnparsedSigns()); // entries we could not load stay in the file
        cfg.set("signs", signStrs);

        cfg.set("end-commands", arena.getEndCommands());

        cfg.set("access.whitelist-enabled", arena.isWhitelistEnabled());
        cfg.set("access.whitelist", arena.getWhitelist().stream().map(java.util.UUID::toString).sorted().toList());
        cfg.set("access.blacklist", arena.getBlacklist().stream().map(java.util.UUID::toString).sorted().toList());

        ArenaVisualConfig v = arena.getVisual();
        cfg.set("visual.titles", v.isTitles());
        cfg.set("visual.sounds", v.isSounds());
        cfg.set("visual.particles", v.isParticles());
        cfg.set("visual.scoreboard", v.isScoreboard());
        cfg.set("visual.boss-bar", v.isBossBar());
        cfg.set("visual.action-bar", v.isActionBar());
        cfg.set("visual.holograms", v.isHolograms());

        YamlUtil.save(cfg, file, plugin.getLogger());
    }

    private void saveSpawnList(FileConfiguration cfg, Arena arena, String path, SpawnType type) {
        List<String> list = arena.getSpawns(type).stream()
                .map(LocationUtil::serialize)
                .toList();
        cfg.set(path, list);
    }

    public void delete(String name) {
        File file = new File(arenasFolder, name + ".yml");
        if (file.exists()) file.delete();
    }
}
