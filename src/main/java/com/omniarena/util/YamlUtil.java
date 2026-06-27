package com.omniarena.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public final class YamlUtil {

    private YamlUtil() {}

    public static FileConfiguration load(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }

    public static void save(FileConfiguration config, File file, Logger logger) {
        try {
            config.save(file);
        } catch (IOException e) {
            logger.severe("Failed to save " + file.getName() + ": " + e.getMessage());
        }
    }

    public static File getOrCreate(File parent, String name, Logger logger) {
        File file = new File(parent, name);
        if (!file.exists()) {
            try {
                if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                logger.severe("Failed to create " + name + ": " + e.getMessage());
            }
        }
        return file;
    }
}
