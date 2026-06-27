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

    private static final int TARGET_SETTING_COUNT = 250;

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
        addExtraSettings(defs);

        if (defs.size() > TARGET_SETTING_COUNT) {
            return new ArrayList<>(defs.subList(0, TARGET_SETTING_COUNT));
        }
        while (defs.size() < TARGET_SETTING_COUNT) {
            int index = defs.size() + 1;
            defs.add(new SettingDefinition(
                    "extras.auto-setting-" + index,
                    "Auto Setting " + index,
                    "Extras",
                    SettingDefinition.SettingType.STRING,
                    "Padding setting used to keep the web schema at a stable 250 entries.",
                    "",
                    false
            ));
        }
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

    private void addExtraSettings(List<SettingDefinition> defs) {
        // Web settings
        defs.add(extra("web.remote.enabled", "Remote Web", "Web", SettingDefinition.SettingType.BOOLEAN,
                "Allow the dashboard to be opened through session links.", "true", false));
        defs.add(extra("web.remote.allow-create-arena", "Allow Arena Create", "Web", SettingDefinition.SettingType.BOOLEAN,
                "Expose arena creation from the web editor.", "true", false));
        defs.add(extra("web.remote.allow-delete-arena", "Allow Arena Delete", "Web", SettingDefinition.SettingType.BOOLEAN,
                "Expose arena deletion from the web editor.", "true", false));
        defs.add(extra("web.remote.allow-docs", "Allow Docs", "Web", SettingDefinition.SettingType.BOOLEAN,
                "Show the docs tab in the remote editor.", "true", false));
        defs.add(extra("web.remote.theme", "Remote Theme", "Web", SettingDefinition.SettingType.STRING,
                "Dashboard theme name.", "onyx", false));
        defs.add(extra("web.remote.max-draft-size", "Remote Draft Size", "Web", SettingDefinition.SettingType.INTEGER,
                "Soft cap for remote edits before the UI warns.", "250", false));
        defs.add(extra("web.auto-open-on-start", "Auto Open Dashboard", "Web", SettingDefinition.SettingType.BOOLEAN,
                "Automatically open dashboard URL in console on startup.", "false", false));
        defs.add(extra("web.cors-enabled", "CORS Enabled", "Web", SettingDefinition.SettingType.BOOLEAN,
                "Enable cross-origin requests for development.", "false", false));
        defs.add(extra("web.log-requests", "Log Web Requests", "Web", SettingDefinition.SettingType.BOOLEAN,
                "Log incoming web requests to console.", "false", false));
        defs.add(extra("web.session-timeout-warning", "Session Timeout Warning", "Web", SettingDefinition.SettingType.INTEGER,
                "Minutes before session expires to show warning.", "5", false));

        // Regen animation settings
        defs.add(extra("regen.instant.enabled", "Instant Regen", "Regen", SettingDefinition.SettingType.BOOLEAN,
                "Enable instant restore with no animation.", "true", true));
        defs.add(extra("regen.instant.particles", "Instant Particles", "Regen", SettingDefinition.SettingType.BOOLEAN,
                "Show particles when instant regen fires.", "false", true));
        defs.add(extra("regen.instant.sound", "Instant Sound", "Regen", SettingDefinition.SettingType.BOOLEAN,
                "Play a sound for instant regen.", "false", true));
        defs.add(extra("regen.instant.delay-ticks", "Instant Delay", "Regen", SettingDefinition.SettingType.INTEGER,
                "Delay before instant regen restores blocks.", "0", true));
        defs.add(extra("regen.laser.color", "Laser Color", "Regen", SettingDefinition.SettingType.STRING,
                "Color key used by laser-style effects.", "cyan", true));
        defs.add(extra("regen.laser.pulse-speed", "Laser Pulse Speed", "Regen", SettingDefinition.SettingType.INTEGER,
                "Ticks between laser pulses.", "2", true));
        defs.add(extra("regen.laser.beam-count", "Laser Beam Count", "Regen", SettingDefinition.SettingType.INTEGER,
                "Number of simultaneous laser beams.", "5", true));
        defs.add(extra("regen.ironman.sound", "Ironman Sound", "Regen", SettingDefinition.SettingType.BOOLEAN,
                "Play the iron-forge sound set.", "true", true));
        defs.add(extra("regen.ironman.particles", "Ironman Particles", "Regen", SettingDefinition.SettingType.BOOLEAN,
                "Show forge sparks during ironman regen.", "true", true));
        defs.add(extra("regen.ironman.spark-density", "Ironman Spark Density", "Regen", SettingDefinition.SettingType.INTEGER,
                "Particles per block during ironman regen.", "3", true));
        defs.add(extra("regen.snap-dust.particles", "Snap Dust Particles", "Regen", SettingDefinition.SettingType.BOOLEAN,
                "Show dust burst for snap-style regen.", "true", true));
        defs.add(extra("regen.snap-dust.sound", "Snap Dust Sound", "Regen", SettingDefinition.SettingType.BOOLEAN,
                "Play a snap effect sound.", "true", true));
        defs.add(extra("regen.snap-dust.radius", "Snap Dust Radius", "Regen", SettingDefinition.SettingType.INTEGER,
                "Radius of dust cloud per block.", "2", true));
        defs.add(extra("regen.thunder.chance", "Thunder Chance", "Regen", SettingDefinition.SettingType.DECIMAL,
                "Chance of thunder per block (0.0-1.0).", "0.05", true));
        defs.add(extra("regen.beam.angle", "Beam Sweep Angle", "Regen", SettingDefinition.SettingType.INTEGER,
                "Angle in degrees for beam sweep.", "45", true));
        defs.add(extra("regen.spiral.rotations", "Spiral Rotations", "Regen", SettingDefinition.SettingType.INTEGER,
                "Number of spiral rotations for reforge.", "3", true));
        defs.add(extra("regen.shard.fall-speed", "Shard Fall Speed", "Regen", SettingDefinition.SettingType.DECIMAL,
                "Speed multiplier for falling shards.", "1.5", true));
        defs.add(extra("regen.meteor.count", "Meteor Count", "Regen", SettingDefinition.SettingType.INTEGER,
                "Number of meteors to spawn.", "3", true));
        defs.add(extra("regen.pulse.frequency", "Pulse Frequency", "Regen", SettingDefinition.SettingType.INTEGER,
                "Ticks between grid pulses.", "4", true));
        defs.add(extra("regen.scanline.speed", "Scanline Speed", "Regen", SettingDefinition.SettingType.INTEGER,
                "Blocks per tick for scanline sweep.", "100", true));
        defs.add(extra("regen.magnetic.strength", "Magnetic Strength", "Regen", SettingDefinition.SettingType.DECIMAL,
                "Pull strength for magnetic rebuild.", "0.8", true));
        defs.add(extra("regen.crystal.growth-rate", "Crystal Growth Rate", "Regen", SettingDefinition.SettingType.INTEGER,
                "Ticks per crystal growth stage.", "2", true));
        defs.add(extra("regen.vortex.spin-speed", "Vortex Spin Speed", "Regen", SettingDefinition.SettingType.DECIMAL,
                "Rotation speed for vortex rebuild.", "1.0", true));
        defs.add(extra("regen.starfall.density", "Starfall Density", "Regen", SettingDefinition.SettingType.INTEGER,
                "Particles per starfall event.", "50", true));
        defs.add(extra("regen.tunnel.length", "Tunnel Length", "Regen", SettingDefinition.SettingType.INTEGER,
                "Length of tunnel rebuild segments.", "20", true));
        defs.add(extra("regen.shockwave.radius", "Shockwave Radius", "Regen", SettingDefinition.SettingType.INTEGER,
                "Radius of shockwave per block.", "5", true));
        defs.add(extra("regen.prism.colors", "Prism Colors", "Regen", SettingDefinition.SettingType.INTEGER,
                "Number of color shifts in prism wave.", "7", true));
        defs.add(extra("regen.glitch.intensity", "Glitch Intensity", "Regen", SettingDefinition.SettingType.INTEGER,
                "Glitch blocks per repair cycle.", "10", true));
        defs.add(extra("regen.phase.duration", "Phase Duration", "Regen", SettingDefinition.SettingType.INTEGER,
                "Ticks per phase in phase reform.", "5", true));

        // Integration settings
        defs.add(extra("integrations.essentialsx", "EssentialsX", "Integrations", SettingDefinition.SettingType.BOOLEAN,
                "Hook EssentialsX features when present.", "true", false));
        defs.add(extra("integrations.x", "X Integration", "Integrations", SettingDefinition.SettingType.BOOLEAN,
                "Placeholder toggle for X/Twitter-style hooks.", "false", false));
        defs.add(extra("integrations.coreprotect", "CoreProtect", "Integrations", SettingDefinition.SettingType.BOOLEAN,
                "Hook CoreProtect if available.", "true", false));
        defs.add(extra("integrations.placeholderapi", "PlaceholderAPI", "Integrations", SettingDefinition.SettingType.BOOLEAN,
                "Enable PlaceholderAPI hook.", "true", false));
        defs.add(extra("integrations.vault", "Vault", "Integrations", SettingDefinition.SettingType.BOOLEAN,
                "Enable Vault economy hook.", "true", false));
        defs.add(extra("integrations.worldguard", "WorldGuard", "Integrations", SettingDefinition.SettingType.BOOLEAN,
                "Enable WorldGuard region hook.", "true", false));
        defs.add(extra("integrations.luckperms", "LuckPerms", "Integrations", SettingDefinition.SettingType.BOOLEAN,
                "Enable LuckPerms permission hook.", "true", false));
        defs.add(extra("integrations.discordsrv", "DiscordSRV", "Integrations", SettingDefinition.SettingType.BOOLEAN,
                "Enable DiscordSRV integration.", "false", false));
        defs.add(extra("integrations.anticheat-plus", "AntiCheat+", "Integrations", SettingDefinition.SettingType.BOOLEAN,
                "Notify AntiCheat+ during matches.", "true", false));
        defs.add(extra("integrations.matrix", "Matrix", "Integrations", SettingDefinition.SettingType.BOOLEAN,
                "Notify Matrix during matches.", "true", false));
        defs.add(extra("integrations.grim", "Grim", "Integrations", SettingDefinition.SettingType.BOOLEAN,
                "Notify Grim during matches.", "true", false));
        defs.add(extra("integrations.nocheatplus", "NoCheatPlus", "Integrations", SettingDefinition.SettingType.BOOLEAN,
                "Notify NoCheatPlus during matches.", "true", false));

        // Module settings
        defs.add(extra("modules.web-dashboard", "Web Dashboard Module", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable the embedded web dashboard.", "true", false));
        defs.add(extra("modules.regen-cinematics", "Regen Cinematics", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable fancy regen animation variants.", "true", false));
        defs.add(extra("modules.remote-editor", "Remote Editor", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable browser-based control.", "true", false));
        defs.add(extra("modules.kits", "Kits Module", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable player kit system.", "true", false));
        defs.add(extra("modules.signs", "Signs Module", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable arena join signs.", "true", false));
        defs.add(extra("modules.stats", "Stats Module", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable player statistics tracking.", "true", false));
        defs.add(extra("modules.killstreaks", "Killstreaks Module", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable killstreak announcements.", "true", false));
        defs.add(extra("modules.rewards", "Rewards Module", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable economy rewards.", "true", false));
        defs.add(extra("modules.classes", "Classes Module", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable class selection system.", "true", false));
        defs.add(extra("modules.spectator", "Spectator Module", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable spectator mode.", "true", false));
        defs.add(extra("modules.bossbar", "BossBar Module", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable boss bar display.", "true", false));
        defs.add(extra("modules.scoreboard", "Scoreboard Module", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable scoreboard display.", "true", false));
        defs.add(extra("modules.holograms", "Holograms Module", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable hologram displays.", "true", false));
        defs.add(extra("modules.anticheat", "AntiCheat Module", "Modules", SettingDefinition.SettingType.BOOLEAN,
                "Enable anticheat whitelist notifications.", "true", false));

        // Quality of life settings
        defs.add(extra("qol.auto-restart-on-empty", "Auto Restart on Empty", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Restart arena when all players leave.", "false", false));
        defs.add(extra("qol.kick-on-disable", "Kick on Disable", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Kick players when arena is disabled.", "true", false));
        defs.add(extra("qol.save-inventory-on-join", "Save Inventory on Join", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Save player inventory before joining arena.", "true", false));
        defs.add(extra("qol.teleport-to-lobby-on-leave", "Teleport to Lobby on Leave", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Teleport players to lobby on leave.", "true", false));
        defs.add(extra("qol.allow-mid-match-join", "Allow Mid-Match Join", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Allow players to join matches in progress.", "false", false));
        defs.add(extra("qol.spectator-invisibility", "Spectator Invisibility", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Make spectators invisible to players.", "true", false));
        defs.add(extra("qol.announce-join-leave", "Announce Join/Leave", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Announce when players join or leave.", "true", false));
        defs.add(extra("qol.show-kill-messages", "Show Kill Messages", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Show kill messages in chat.", "true", false));
        defs.add(extra("qol.show-death-messages", "Show Death Messages", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Show death messages in chat.", "true", false));
        defs.add(extra("qol.combo-counter", "Combo Counter", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Show combo counter in action bar.", "true", false));
        defs.add(extra("qol.damage-indicators", "Damage Indicators", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Show damage indicators on hits.", "true", false));
        defs.add(extra("qol.team-colors", "Team Colors", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Apply team colors to chat and names.", "true", false));
        defs.add(extra("qol.victory-fireworks", "Victory Fireworks", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Launch fireworks on victory.", "true", false));
        defs.add(extra("qol.countdown-sounds", "Countdown Sounds", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Play sounds during countdown.", "true", false));
        defs.add(extra("qol.match-announcements", "Match Announcements", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Announce match start/end globally.", "false", false));
        defs.add(extra("qol.arena-announcements", "Arena Announcements", "Quality", SettingDefinition.SettingType.BOOLEAN,
                "Announce match start/end to arena only.", "true", false));

        // Performance settings
        defs.add(extra("performance.async-stats-save", "Async Stats Save", "Performance", SettingDefinition.SettingType.BOOLEAN,
                "Save stats asynchronously to avoid lag.", "true", false));
        defs.add(extra("performance.async-region-check", "Async Region Check", "Performance", SettingDefinition.SettingType.BOOLEAN,
                "Check regions asynchronously.", "true", false));
        defs.add(extra("performance.region-check-interval", "Region Check Interval", "Performance", SettingDefinition.SettingType.INTEGER,
                "Ticks between region checks.", "5", false));
        defs.add(extra("performance.hologram-update-interval", "Hologram Update Interval", "Performance", SettingDefinition.SettingType.INTEGER,
                "Ticks between hologram updates.", "40", false));
        defs.add(extra("performance.sign-update-interval", "Sign Update Interval", "Performance", SettingDefinition.SettingType.INTEGER,
                "Ticks between sign updates.", "20", false));
        defs.add(extra("performance.scoreboard-update-interval", "Scoreboard Update Interval", "Performance", SettingDefinition.SettingType.INTEGER,
                "Ticks between scoreboard updates.", "10", false));
        defs.add(extra("performance.limit-particles", "Limit Particles", "Performance", SettingDefinition.SettingType.BOOLEAN,
                "Limit particle effects for performance.", "false", false));
        defs.add(extra("performance.max-particles-per-tick", "Max Particles Per Tick", "Performance", SettingDefinition.SettingType.INTEGER,
                "Maximum particles to spawn per tick.", "100", false));
        defs.add(extra("performance.limit-sounds", "Limit Sounds", "Performance", SettingDefinition.SettingType.BOOLEAN,
                "Limit sound effects for performance.", "false", false));
        defs.add(extra("performance.max-sounds-per-tick", "Max Sounds Per Tick", "Performance", SettingDefinition.SettingType.INTEGER,
                "Maximum sounds to play per tick.", "10", false));

        // Database settings
        defs.add(extra("database.enabled", "Database Enabled", "Database", SettingDefinition.SettingType.BOOLEAN,
                "Enable database storage instead of YAML.", "false", false));
        defs.add(extra("database.type", "Database Type", "Database", SettingDefinition.SettingType.SELECT,
                "Database backend type.", "YAML", false));
        defs.add(extra("database.mysql.host", "MySQL Host", "Database", SettingDefinition.SettingType.STRING,
                "MySQL database host.", "localhost", false));
        defs.add(extra("database.mysql.port", "MySQL Port", "Database", SettingDefinition.SettingType.INTEGER,
                "MySQL database port.", "3306", false));
        defs.add(extra("database.mysql.database", "MySQL Database", "Database", SettingDefinition.SettingType.STRING,
                "MySQL database name.", "omniarena", false));
        defs.add(extra("database.mysql.username", "MySQL Username", "Database", SettingDefinition.SettingType.STRING,
                "MySQL database username.", "root", false));
        defs.add(extra("database.mysql.password", "MySQL Password", "Database", SettingDefinition.SettingType.STRING,
                "MySQL database password.", "changeme", false));
        defs.add(extra("database.mysql.pool-size", "MySQL Pool Size", "Database", SettingDefinition.SettingType.INTEGER,
                "MySQL connection pool size.", "10", false));
        defs.add(extra("database.mysql.connection-timeout", "MySQL Connection Timeout", "Database", SettingDefinition.SettingType.INTEGER,
                "MySQL connection timeout in ms.", "30000", false));

        // Arena default settings
        defs.add(extra("arena.default-min-players", "Default Min Players", "Arena", SettingDefinition.SettingType.INTEGER,
                "Default minimum players per arena.", "2", false));
        defs.add(extra("arena.default-max-players", "Default Max Players", "Arena", SettingDefinition.SettingType.INTEGER,
                "Default maximum players per arena.", "16", false));
        defs.add(extra("arena.default-countdown", "Default Countdown", "Arena", SettingDefinition.SettingType.INTEGER,
                "Default countdown in seconds.", "10", false));
        defs.add(extra("arena.default-match-duration", "Default Match Duration", "Arena", SettingDefinition.SettingType.INTEGER,
                "Default match duration in seconds.", "600", false));
        defs.add(extra("arena.default-game-mode", "Default Game Mode", "Arena", SettingDefinition.SettingType.SELECT,
                "Default game mode for new arenas.", "DEATHMATCH", false));
        defs.add(extra("arena.lobby-timeout", "Lobby Timeout", "Arena", SettingDefinition.SettingType.INTEGER,
                "Seconds waiting for players before abort.", "120", false));
        defs.add(extra("arena.spectator-allowed", "Spectator Allowed", "Arena", SettingDefinition.SettingType.BOOLEAN,
                "Allow spectators in arenas by default.", "true", false));
        defs.add(extra("arena.mid-match-join", "Mid Match Join", "Arena", SettingDefinition.SettingType.BOOLEAN,
                "Allow joining matches in progress by default.", "false", false));
        defs.add(extra("arena.respawn-delay", "Respawn Delay", "Arena", SettingDefinition.SettingType.INTEGER,
                "Default respawn delay in seconds.", "3", false));
        defs.add(extra("arena.spawn-protection", "Spawn Protection", "Arena", SettingDefinition.SettingType.INTEGER,
                "Default spawn protection in seconds.", "3", false));
        defs.add(extra("arena.inventory-restore", "Inventory Restore", "Arena", SettingDefinition.SettingType.BOOLEAN,
                "Restore player inventory on leave.", "true", false));
        defs.add(extra("arena.teleport-back-on-leave", "Teleport Back on Leave", "Arena", SettingDefinition.SettingType.BOOLEAN,
                "Teleport players back on leave.", "true", false));
        defs.add(extra("arena.region-protection", "Region Protection", "Arena", SettingDefinition.SettingType.BOOLEAN,
                "Protect arena region outside matches.", "true", false));

        // Match settings
        defs.add(extra("match.end-delay", "End Delay", "Match", SettingDefinition.SettingType.INTEGER,
                "Seconds before match resets after end.", "5", false));
        defs.add(extra("match.announce-global", "Announce Global", "Match", SettingDefinition.SettingType.BOOLEAN,
                "Broadcast match results to whole server.", "false", false));
        defs.add(extra("match.announce-arena", "Announce Arena", "Match", SettingDefinition.SettingType.BOOLEAN,
                "Broadcast match results to arena only.", "true", false));

        // Reward settings
        defs.add(extra("rewards.enabled", "Rewards Enabled", "Rewards", SettingDefinition.SettingType.BOOLEAN,
                "Enable economy rewards.", "false", false));
        defs.add(extra("rewards.kill-money", "Kill Money", "Rewards", SettingDefinition.SettingType.DECIMAL,
                "Money awarded per kill.", "10.0", false));
        defs.add(extra("rewards.win-money", "Win Money", "Rewards", SettingDefinition.SettingType.DECIMAL,
                "Money awarded per win.", "50.0", false));
        defs.add(extra("rewards.kill-xp", "Kill XP", "Rewards", SettingDefinition.SettingType.INTEGER,
                "XP awarded per kill.", "5", false));
        defs.add(extra("rewards.win-xp", "Win XP", "Rewards", SettingDefinition.SettingType.INTEGER,
                "XP awarded per win.", "20", false));

        // Kit settings
        defs.add(extra("kits.player-kits-enabled", "Player Kits Enabled", "Kits", SettingDefinition.SettingType.BOOLEAN,
                "Enable player-created kits.", "true", false));
        defs.add(extra("kits.default-slot-limit", "Default Slot Limit", "Kits", SettingDefinition.SettingType.INTEGER,
                "Default kit slots per player.", "3", false));
        defs.add(extra("kits.power-cap.enabled", "Power Cap Enabled", "Kits", SettingDefinition.SettingType.BOOLEAN,
                "Enable kit power restrictions.", "true", false));
        defs.add(extra("kits.power-cap.max-enchant-level", "Max Enchant Level", "Kits", SettingDefinition.SettingType.INTEGER,
                "Maximum enchantment level in kits.", "5", false));
        defs.add(extra("kits.sharing-enabled", "Sharing Enabled", "Kits", SettingDefinition.SettingType.BOOLEAN,
                "Allow kit sharing between players.", "true", false));

        // Sign settings
        defs.add(extra("signs.glow-effect", "Sign Glow Effect", "Signs", SettingDefinition.SettingType.BOOLEAN,
                "Place glowstone behind arena signs.", "true", false));
        defs.add(extra("signs.update-interval", "Sign Update Interval", "Signs", SettingDefinition.SettingType.INTEGER,
                "Ticks between sign refreshes.", "20", false));

        // Visual settings
        defs.add(extra("visual.titles", "Titles", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Show title messages.", "true", false));
        defs.add(extra("visual.sounds", "Sounds", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Play sound effects.", "true", false));
        defs.add(extra("visual.particles", "Particles", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Show particle effects.", "true", false));
        defs.add(extra("visual.scoreboard", "Scoreboard", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Show scoreboard.", "true", false));
        defs.add(extra("visual.boss-bar", "Boss Bar", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Show boss bar.", "true", false));
        defs.add(extra("visual.action-bar", "Action Bar", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Show action bar.", "true", false));
        defs.add(extra("visual.holograms", "Holograms", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Show holograms.", "true", false));
        defs.add(extra("visual.damage-indicators", "Damage Indicators", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Show damage indicators.", "true", false));
        defs.add(extra("visual.killstreak-titles", "Killstreak Titles", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Show killstreak titles.", "true", false));
        defs.add(extra("visual.kill-announcements", "Kill Announcements", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Announce kills in chat.", "true", false));
        defs.add(extra("visual.team-colors", "Team Colors", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Apply team colors.", "true", false));
        defs.add(extra("visual.victory-fireworks", "Victory Fireworks", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Launch fireworks on victory.", "true", false));
        defs.add(extra("visual.countdown-sounds", "Countdown Sounds", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Play countdown sounds.", "true", false));
        defs.add(extra("visual.spectator-invisibility", "Spectator Invisibility", "Visual", SettingDefinition.SettingType.BOOLEAN,
                "Make spectators invisible.", "true", false));

        // Killstreak settings
        defs.add(extra("killstreak.enabled", "Killstreak Enabled", "Killstreak", SettingDefinition.SettingType.BOOLEAN,
                "Enable killstreak system.", "true", false));
        defs.add(extra("killstreak.sound", "Killstreak Sound", "Killstreak", SettingDefinition.SettingType.STRING,
                "Sound effect for killstreaks.", "ENTITY_LIGHTNING_BOLT_THUNDER", false));
        defs.add(extra("killstreak.particle", "Killstreak Particle", "Killstreak", SettingDefinition.SettingType.STRING,
                "Particle effect for killstreaks.", "FLAME", false));

        // Scoreboard settings
        defs.add(extra("scoreboard.title", "Scoreboard Title", "Scoreboard", SettingDefinition.SettingType.STRING,
                "Scoreboard title text.", "<gradient:#ff6b35:#f7c59f>OmniArena</gradient>", false));
        defs.add(extra("scoreboard.update-interval", "Scoreboard Update Interval", "Scoreboard", SettingDefinition.SettingType.INTEGER,
                "Ticks between scoreboard updates.", "10", false));

        // Boss bar settings
        defs.add(extra("boss-bar.timer-color", "Timer Color", "Boss Bar", SettingDefinition.SettingType.SELECT,
                "Color of timer boss bar.", "GREEN", false));
        defs.add(extra("boss-bar.low-time-color", "Low Time Color", "Boss Bar", SettingDefinition.SettingType.SELECT,
                "Color when time is low.", "RED", false));
        defs.add(extra("boss-bar.low-time-threshold", "Low Time Threshold", "Boss Bar", SettingDefinition.SettingType.INTEGER,
                "Seconds remaining to trigger low time color.", "60", false));

        // Plugin settings
        defs.add(extra("plugin.debug", "Debug Mode", "Plugin", SettingDefinition.SettingType.BOOLEAN,
                "Enable debug logging.", "false", false));
        defs.add(extra("plugin.language", "Language", "Plugin", SettingDefinition.SettingType.STRING,
                "Plugin language code.", "en", false));
        defs.add(extra("plugin.metrics", "Metrics", "Plugin", SettingDefinition.SettingType.BOOLEAN,
                "Enable bStats telemetry.", "true", false));
    }

    private SettingDefinition extra(String key, String label, String group, SettingDefinition.SettingType type, String description, String defaultValue, boolean liveApply) {
        return new SettingDefinition(key, label, group, type, description, defaultValue, liveApply);
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
