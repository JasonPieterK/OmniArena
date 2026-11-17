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
        plugin.reloadConfig();
        config = plugin.getConfig();

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        plugin.getLogger().info("Configuration reloaded.");
    }

    public String getMessage(String key) {
        String prefix = messages.getString("prefix", "[OmniArena] ");
        String msg = messages.getString(key, "<red>Missing message: " + key);
        return prefix + msg;
    }

    public String getRawMessage(String key) {
        return messages.getString(key, "<red>Missing message: " + key);
    }

    public Object getValue(String key) {
        if (key != null && key.startsWith("messages.")) {
            return messages.get(key.substring("messages.".length()));
        }
        return config.get(key);
    }

    public boolean isDebug() {
        return config.getBoolean("plugin.debug", false);
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
        if (key != null && key.startsWith("messages.")) {
            messages.set(key.substring("messages.".length()), value);
            try {
                messages.save(messagesFile);
                messages = YamlConfiguration.loadConfiguration(messagesFile);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to save messages: " + e.getMessage());
            }
            return;
        }

        config.set(key, value);
        try {
            config.save(configFile);
            // Reload through the plugin so plugin.getConfig() and our reference stay the same object
            plugin.reloadConfig();
            config = plugin.getConfig();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save config: " + e.getMessage());
        }
    }
}
