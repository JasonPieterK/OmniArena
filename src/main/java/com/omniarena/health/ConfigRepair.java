package com.omniarena.health;

import com.omniarena.OmniArena;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

/**
 * Startup/reload repair of config.yml and messages.yml: back-fills missing keys from the bundled defaults, restores
 * defaults for wrong-typed values and regenerates files that no longer parse. The original is always backed up to
 * {@code backups/<file>-<timestamp>.yml} before anything is written.
 */
public final class ConfigRepair {

    private static final List<String> FILES = List.of("config.yml", "messages.yml");

    private ConfigRepair() {}

    /** @return true if any file was rewritten (callers should reload their in-memory copies) */
    public static boolean run(OmniArena plugin) {
        boolean changed = false;
        for (String name : FILES) {
            try {
                changed |= repairFile(plugin, name);
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Config repair of " + name + " failed (" + e
                        + "). The file is used as it is. Fix: check plugins/OmniArena is writable.", e);
            }
        }
        return changed;
    }

    private static boolean repairFile(OmniArena plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.isFile()) return false;
        Logger log = plugin.getLogger();
        int keep = backupsToKeep(file);
        File backups = new File(plugin.getDataFolder(), "backups");

        YamlConfiguration defaults = bundled(plugin, name);
        if (defaults == null) return false; // no jar default to repair against

        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(file);
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            File copy = BackupUtil.backup(file, backups, keep);
            plugin.saveResource(name, true);
            String msg = name + " could not be parsed (" + firstLine(e) + "). The broken file was backed up to "
                    + (copy != null ? "backups/" + copy.getName() : "(backup failed)") + " and a fresh default " + name
                    + " was written. Fix: copy your settings from the backup into the new file.";
            log.severe("!!! " + msg);
            HealthReport.get().recordRepair("config", msg);
            return true;
        }

        YamlRepair.Result result = YamlRepair.repair(cfg, defaults);
        if (!result.changed()) return false;

        File copy = BackupUtil.backup(file, backups, keep);
        try {
            cfg.save(file);
        } catch (IOException e) {
            log.warning("Could not write repaired " + name + " (" + e.getMessage() + "); keeping the original. Fix: check disk space and permissions.");
            return false;
        }
        String backupNote = copy != null ? " (original saved as backups/" + copy.getName() + ")" : "";
        if (!result.added().isEmpty()) {
            String msg = name + ": added " + result.added().size() + " missing key(s): " + preview(result.added()) + backupNote;
            log.warning(msg);
            HealthReport.get().recordRepair("config", msg);
        }
        if (!result.retyped().isEmpty()) {
            String msg = name + ": restored default for " + result.retyped().size() + " wrong-typed value(s): "
                    + preview(result.retyped()) + backupNote;
            log.warning(msg);
            HealthReport.get().recordRepair("config", msg);
        }
        return true;
    }

    private static YamlConfiguration bundled(OmniArena plugin, String name) {
        try (var in = plugin.getResource(name)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    /** Reads health.backups-to-keep from the file itself (the loaded config may not exist yet), default 10. */
    private static int backupsToKeep(File configOrMessages) {
        try {
            File cfgFile = new File(configOrMessages.getParentFile(), "config.yml");
            YamlConfiguration y = YamlConfiguration.loadConfiguration(cfgFile);
            return Math.max(1, y.getInt("health.backups-to-keep", 10));
        } catch (RuntimeException e) {
            return 10;
        }
    }

    private static String preview(List<String> keys) {
        return keys.size() <= 5 ? String.join(", ", keys)
                : String.join(", ", keys.subList(0, 5)) + " and " + (keys.size() - 5) + " more";
    }

    private static String firstLine(Throwable t) {
        String m = String.valueOf(t.getMessage());
        int nl = m.indexOf('\n');
        return nl > 0 ? m.substring(0, nl) : m;
    }
}
