package com.omniarena.health;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class BackupAndSafeYamlTest {

    @TempDir
    File dir;

    @Test
    void backupKeepsOnlyNewest() throws Exception {
        File src = new File(dir, "config.yml");
        Files.writeString(src.toPath(), "a: 1\n");
        File backups = new File(dir, "backups");
        for (int i = 0; i < 6; i++) assertNotNull(BackupUtil.backup(src, backups, 3));
        File[] left = backups.listFiles();
        assertNotNull(left);
        assertEquals(3, left.length);
        for (File f : left) assertTrue(f.getName().startsWith("config-") && f.getName().endsWith(".yml"));
    }

    @Test
    void prunePerFileOnly() throws Exception {
        File a = new File(dir, "config.yml");
        File b = new File(dir, "messages.yml");
        Files.writeString(a.toPath(), "a: 1\n");
        Files.writeString(b.toPath(), "b: 1\n");
        File backups = new File(dir, "backups");
        for (int i = 0; i < 4; i++) { BackupUtil.backup(a, backups, 2); BackupUtil.backup(b, backups, 2); }
        File[] left = backups.listFiles();
        assertNotNull(left);
        assertEquals(4, left.length);
    }

    @Test
    void validFileLoadsUntouched() throws Exception {
        File f = new File(dir, "stats.yml");
        Files.writeString(f.toPath(), "stats:\n  x: 1\n");
        YamlConfiguration c = SafeYaml.load(f, new File(dir, "broken"), Logger.getAnonymousLogger());
        assertEquals(1, c.getInt("stats.x"));
        assertTrue(f.exists());
    }

    @Test
    void missingFileGivesEmptyConfig() {
        YamlConfiguration c = SafeYaml.load(new File(dir, "nope.yml"), new File(dir, "broken"), Logger.getAnonymousLogger());
        assertTrue(c.getKeys(true).isEmpty());
    }

    @Test
    void corruptFileIsMovedAsideAndEmptyConfigReturned() throws Exception {
        File f = new File(dir, "elo.yml");
        Files.writeString(f.toPath(), "ratings:\n  - [unclosed\n\t bad: : :\n");
        File broken = new File(dir, "broken");
        int before = HealthReport.get().repairCount();
        YamlConfiguration c = SafeYaml.load(f, broken, Logger.getAnonymousLogger());
        assertTrue(c.getKeys(true).isEmpty());
        assertFalse(f.exists(), "corrupt file must be moved away");
        File[] moved = broken.listFiles();
        assertNotNull(moved);
        assertEquals(1, moved.length);
        assertTrue(moved[0].getName().startsWith("elo-"));
        assertEquals(before + 1, HealthReport.get().repairCount());
    }
}
