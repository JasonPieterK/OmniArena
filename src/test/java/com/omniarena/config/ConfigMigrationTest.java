package com.omniarena.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMigrationTest {

    private static YamlConfiguration bundled() throws Exception {
        try (var in = ConfigMigrationTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static YamlConfiguration old() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                plugin:
                  debug: true
                arena:
                  default-min-players: 4
                  default-game-mode: KOTH
                  lobby-timeout: 30
                  persist-player-state: false
                  custom-leftover: 1
                match:
                  end-delay: 9
                  end-commands: ["say {winner}"]
                regen:
                  auto: false
                  batch-size: 123
                  animation:
                    type: RISE
                    sound: false
                  progress:
                    enabled: false
                    interval-percent: 25
                  arenas:
                    duel:
                      auto: true
                pvp:
                  global:
                    pvp-enabled: false
                  combat:
                    cancel-teleport: false
                visual:
                  titles: false
                  boss-bar: false
                killstreak:
                  milestones:
                    3: "TRIPLE"
                boss-bar:
                  low-time-threshold: 30
                scoreboard:
                  title: "Hi"
                party:
                  max-size: 6
                health:
                  watchdog-seconds: 99
                  enabled: false
                """);
        return y;
    }

    @Test
    void movesOldKeysToNewPaths() throws Exception {
        YamlConfiguration y = old();
        assertTrue(ConfigMigration.needsMigration(y));
        ConfigMigration.Result r = ConfigMigration.migrate(y);
        assertTrue(r.changed());
        assertEquals(2, y.getInt("config-version"));
        assertTrue(y.getBoolean("general.debug"));
        assertEquals(4, y.getInt("arenas.min-players"));
        assertEquals("KOTH", y.getString("arenas.game-mode"));
        assertEquals(30, y.getInt("matches.lobby-timeout"));
        assertFalse(y.getBoolean("advanced.persist-player-state"));
        assertEquals(9, y.getInt("matches.end-delay"));
        assertEquals("RISE", y.getString("regen.style"));
        assertFalse(y.getBoolean("regen.sound"));
        assertFalse(y.getBoolean("regen.progress-messages"));
        assertEquals(25, y.getInt("advanced.regen.progress-interval-percent"));
        assertEquals(123, y.getInt("advanced.regen.batch-size"));
        assertFalse(y.getBoolean("pvp.server-pvp"));
        assertFalse(y.getBoolean("pvp.combat.block-teleport"));
        assertFalse(y.getBoolean("visuals.titles"));
        assertFalse(y.getBoolean("visuals.boss-bar"));
        assertEquals("TRIPLE", y.getString("visuals.killstreak.milestones.3"));
        assertEquals(30, y.getInt("visuals.boss-bar-colors.low-time-seconds"));
        assertEquals("Hi", y.getString("visuals.scoreboard-title"));
        assertEquals(6, y.getInt("ranked.party-max-size"));
        assertEquals(99, y.getInt("advanced.health.watchdog-seconds"));
        assertFalse(y.getBoolean("health.enabled")); // unchanged path
        // per-arena regen override keeps its path
        assertTrue(y.getBoolean("regen.arenas.duel.auto"));
        assertFalse(y.getBoolean("regen.auto"));
        // old keys are gone, empty old sections pruned
        for (String oldKey : ConfigMigration.MOVES.keySet()) assertFalse(y.contains(oldKey, true), oldKey);
        assertFalse(y.contains("plugin"));
        assertFalse(y.contains("visual"));
        assertFalse(y.contains("regen.animation"));
        assertFalse(y.contains("pvp.global"));
        // unknown user keys are not destroyed
        assertEquals(1, y.getInt("arena.custom-leftover"));
    }

    @Test
    void migrationIsIdempotentAndSkipsCurrentFiles() throws Exception {
        YamlConfiguration y = old();
        ConfigMigration.migrate(y);
        assertFalse(ConfigMigration.needsMigration(y));
        assertFalse(ConfigMigration.migrate(y).changed());
        assertFalse(ConfigMigration.applyFallback(y).changed());
    }

    @Test
    void existingNewValueWinsOverOldKey() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.set("plugin.debug", true);
        y.set("general.debug", false);
        ConfigMigration.applyFallback(y);
        assertFalse(y.getBoolean("general.debug"));
        assertFalse(y.contains("plugin.debug", true));
    }

    @Test
    void fallbackMovesOldKeysInMemoryWithoutBumpingVersion() throws Exception {
        YamlConfiguration y = old();
        ConfigMigration.applyFallback(y);
        assertEquals(4, y.getInt("arenas.min-players"));
        assertEquals(1, ConfigMigration.version(y));
    }

    @Test
    void overlayKeepsTemplateLayoutAndUserValues() throws Exception {
        YamlConfiguration y = old();
        ConfigMigration.migrate(y);
        YamlConfiguration template = bundled();
        ConfigMigration.overlay(template, y);
        assertEquals(2, template.getInt("config-version"));
        assertEquals(4, template.getInt("arenas.min-players"));
        assertEquals("RISE", template.getString("regen.style"));
        assertEquals(16, template.getInt("arenas.max-players")); // default kept
        assertEquals(java.util.List.of("say {winner}"), template.getStringList("matches.end-commands"));
        // user's milestone list replaces the defaults instead of merging
        assertEquals(java.util.Set.of("3"), template.getConfigurationSection("visuals.killstreak.milestones").getKeys(false));
        assertTrue(template.getBoolean("regen.arenas.duel.auto"));
    }

    @Test
    void bundledConfigUsesOnlyNewKeysAndCoversEveryMoveTarget() throws Exception {
        YamlConfiguration b = bundled();
        assertEquals(ConfigMigration.CURRENT_VERSION, b.getInt("config-version"));
        for (String oldKey : ConfigMigration.MOVES.keySet()) {
            assertFalse(b.contains(oldKey, true), "bundled config still has old key " + oldKey);
        }
        for (String target : ConfigMigration.MOVES.values()) {
            assertTrue(b.contains(target, true), "bundled config lacks " + target);
        }
        assertFalse(ConfigMigration.applyFallback(b).changed());
    }
}
