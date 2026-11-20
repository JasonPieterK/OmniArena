package com.omniarena.health;

import com.omniarena.OmniArena;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Tolerant YAML loading. Bukkit's {@code YamlConfiguration.loadConfiguration} silently returns an empty config for
 * a corrupt file (which a later save then overwrites). This loader detects the corruption, keeps the broken file
 * aside and lets the caller continue with an empty config.
 */
public final class SafeYaml {

    private SafeYaml() {}

    /**
     * Loads {@code file}. Missing file gives an empty config. A corrupt file is moved into {@code brokenDir}, the
     * event is logged and recorded, and an empty config is returned.
     */
    public static YamlConfiguration load(File file, File brokenDir, Logger log) {
        YamlConfiguration cfg = new YamlConfiguration();
        if (file == null || !file.isFile()) return cfg;
        try {
            cfg.load(file);
            return cfg;
        } catch (InvalidConfigurationException | RuntimeException e) {
            File moved = BackupUtil.moveAside(file, brokenDir);
            String where = moved != null ? "It was moved to " + rel(moved) : "It could not be moved (check folder permissions)";
            String msg = "Corrupt YAML in " + file.getName() + " (" + firstLine(e) + "). " + where
                    + " and OmniArena continues with empty data for it. Fix: open the moved file, repair the YAML and copy "
                    + "the entries back, or delete it to accept the loss.";
            if (log != null) log.severe(msg);
            HealthReport.get().recordRepair("broken-file", file.getName() + " was corrupt; " + where);
        } catch (IOException e) {
            File copy = BackupUtil.backup(file, new File(brokenDir, "unreadable"), 5);
            String msg = "Could not read " + file.getName() + " (" + e.getMessage() + "). "
                    + (copy != null ? "A copy was saved to " + rel(copy) + ". " : "")
                    + "Continuing with empty data for it. Fix: check file permissions and disk health.";
            if (log != null) log.severe(msg);
            HealthReport.get().recordRepair("broken-file", file.getName() + " was unreadable: " + e.getMessage());
        }
        return new YamlConfiguration();
    }

    /** Plugin data file: broken ones go to {@code plugins/OmniArena/broken/}. */
    public static YamlConfiguration loadData(File file) {
        OmniArena plugin = OmniArena.getInstance();
        if (plugin == null) return YamlConfiguration.loadConfiguration(file);
        return load(file, new File(plugin.getDataFolder(), "broken"), plugin.getLogger());
    }

    private static String rel(File f) {
        File parent = f.getParentFile();
        return (parent != null ? parent.getName() + "/" : "") + f.getName();
    }

    private static String firstLine(Throwable t) {
        String m = String.valueOf(t.getMessage());
        int nl = m.indexOf('\n');
        return nl > 0 ? m.substring(0, nl) : m;
    }
}
