package com.omniarena.config;

import com.omniarena.OmniArena;
import com.omniarena.util.YamlUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ConfigManager {

    private static ConfigManager instance;

    private final OmniArena plugin;
    private FileConfiguration config;
    private FileConfiguration messages;

    private File configFile;
    private File messagesFile;

    public ConfigManager(OmniArena plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static ConfigManager getInstance() { return instance; }
    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getMessages() { return messages; }

    public void load() {
        plugin.saveDefaultConfig();
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) plugin.saveResource("messages.yml", false);
        configFile = new File(plugin.getDataFolder(), "config.yml");

        // Layout migration (old keys -> config-version 2) must run before the health back-fill
        migrateLayout();
        // Self-healing: back-fill missing keys, fix wrong types, regenerate unparseable files (with backups)
        com.omniarena.health.ConfigRepair.run(plugin);
        readFiles();
    }

    public void reload() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        migrateLayout();
        com.omniarena.health.ConfigRepair.run(plugin);
        readFiles();
        plugin.getLogger().info("Configuration reloaded.");
    }

    private void readFiles() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        // Single place that still knows the old key names: if the file could not be rewritten, old keys keep working in memory
        ConfigMigration.applyFallback(config);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        applyMessageDefaults();
    }

    /**
     * Upgrades an old-layout config.yml to {@link ConfigMigration#CURRENT_VERSION}: backs the file up, moves every old key
     * to its new path, writes the result on top of the bundled commented default file. Never touches an unparseable
     * file (ConfigRepair handles that) and gives up without writing if the backup cannot be made.
     */
    private void migrateLayout() {
        try {
            if (configFile == null || !configFile.isFile()) return;
            YamlConfiguration old = new YamlConfiguration();
            try {
                old.load(configFile);
            } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException | RuntimeException e) {
                return;
            }
            if (!ConfigMigration.needsMigration(old)) return;

            int keep = Math.max(1, old.getInt("health.backups-to-keep", 10));
            File backup = com.omniarena.health.BackupUtil.backup(configFile, new File(plugin.getDataFolder(), "backups"), keep);
            if (backup == null) {
                plugin.getLogger().warning("config.yml uses the old layout but a backup could not be made, so it was NOT migrated "
                        + "(old keys still work for now). Fix: make plugins/OmniArena/backups writable, then /oa reload.");
                return;
            }
            ConfigMigration.Result r = ConfigMigration.migrate(old);
            YamlConfiguration template = new YamlConfiguration();
            try (java.io.InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    template = YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            ConfigMigration.overlay(template, old);
            template.save(configFile);
            String msg = "config.yml was upgraded to the new layout (config-version " + ConfigMigration.CURRENT_VERSION + "): "
                    + r.moved().size() + " setting(s) moved. Your original is saved as backups/" + backup.getName() + ".";
            plugin.getLogger().warning(msg);
            com.omniarena.health.HealthReport.get().recordRepair("config", msg);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Config migration failed (" + e
                    + "). The file is used as it is; old keys still work. Fix: check plugins/OmniArena is writable.", e);
        }
    }

    public String getMessage(String key) {
        return rawOrDefault("prefix", "[OmniArena] ") + getRawMessage(key);
    }

    private final java.util.Set<String> warnedMissing = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private FileConfiguration jarMessages;

    private String rawOrDefault(String key, String fallback) {
        String v = messages.getString(key); // no default arg: Bukkit then consults setDefaults()
        if (v == null && jarMessages != null) v = jarMessages.getString(key);
        return v != null ? v : fallback;
    }

    public String getRawMessage(String key) {
        String v = rawOrDefault(key, null);
        if (v != null) return v;
        if (warnedMissing.add(key)) {
            plugin.getLogger().warning("Message key '" + key + "' is missing from plugins/OmniArena/messages.yml and from the "
                    + "bundled default. Fix: add a line '" + key + ": \"<red>your text\"' to messages.yml (or delete messages.yml "
                    + "to regenerate it) and run /oa reload.");
        }
        return "<red>This message is not configured <gray>(missing key <white>" + key
                + "</white> in messages.yml). Tell an admin to add it or delete messages.yml and run <white>/oa reload</white>.";
    }

    public Object getValue(String key) {
        if (key != null && key.startsWith("messages.")) {
            return messages.get(key.substring("messages.".length()));
        }
        return config.get(key);
    }

    public boolean isDebug() {
        return config.getBoolean("general.debug", false);
    }

    /** Logs {@code [debug] msg} to the console only when {@code general.debug: true}. Cheap no-op otherwise. */
    public void debug(String message) {
        if (isDebug()) plugin.getLogger().info("[debug] " + message);
    }

    /** Static convenience for call sites that may run before/without a ConfigManager. */
    public static void debugLog(java.util.function.Supplier<String> message) {
        ConfigManager cm = getInstance();
        if (cm != null && cm.isDebug()) cm.plugin.getLogger().info("[debug] " + message.get());
    }

    public boolean isModuleEnabled(String module) {
        return config.getBoolean("modules." + module, true);
    }

    public boolean isIntegrationEnabled(String integration) {
        return config.getBoolean("integrations." + integration, true);
    }

    public void setConfigValue(String key, Object value) {
        setValue(key, value);
    }

    public void setValue(String key, Object value) {
        setValues(java.util.Collections.singletonMap(key, value));
    }

    /** Sets many keys, then saves each backing file (config.yml / messages.yml) at most once. */
    public void setValues(java.util.Map<String, ?> values) {
        if (values == null || values.isEmpty()) return;
        boolean configDirty = false, messagesDirty = false;
        for (java.util.Map.Entry<String, ?> e : values.entrySet()) {
            String key = e.getKey();
            if (key == null) continue;
            if (key.startsWith("messages.")) {
                messages.set(key.substring("messages.".length()), e.getValue());
                messagesDirty = true;
            } else {
                config.set(key, e.getValue());
                configDirty = true;
            }
        }
        if (messagesDirty) {
            try {
                messages.save(messagesFile);
                messages = YamlConfiguration.loadConfiguration(messagesFile);
        applyMessageDefaults();
            } catch (Exception ex) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save " + messagesFile.getPath() + " (" + ex.getMessage() + "). Fix: check the file is not read-only and the disk has free space, then retry.", ex);
            }
        }
        if (configDirty) {
            try {
                config.save(configFile);
                // Reload through the plugin so plugin.getConfig() and our reference stay the same object
                plugin.reloadConfig();
                config = plugin.getConfig();
            } catch (Exception ex) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save plugins/OmniArena/config.yml (" + ex.getMessage() + "). Fix: check the file is not read-only and the disk has free space, then retry.", ex);
            }
        }
    }

    /** Falls back to the jar's bundled messages.yml for keys missing on disk (e.g. after upgrades). */
    private void applyMessageDefaults() {
        try (java.io.InputStream in = plugin.getResource("messages.yml")) {
            if (in == null) {
                plugin.getLogger().warning("The bundled messages.yml is missing from the plugin jar, so missing message keys cannot "
                        + "be auto-filled. Fix: re-download OmniArena.jar.");
                return;
            }
            jarMessages = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            messages.setDefaults(jarMessages);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not read the bundled messages.yml (" + e.getMessage()
                    + "); keys missing from your messages.yml will show a placeholder. Fix: re-download OmniArena.jar.");
        }
    }
}
