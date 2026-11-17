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
            logger.severe("Failed to save " + file.getPath() + " (" + e.getMessage() + "). Fix: make sure the plugins/OmniArena folder is writable by the server user and the disk is not full; unsaved changes will be lost on restart.");
        }
    }

    public static File getOrCreate(File parent, String name, Logger logger) {
        File file = new File(parent, name);
        if (!file.exists()) {
            try {
                if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                logger.severe("Failed to create " + new File(parent, name).getPath() + " (" + e.getMessage() + "). Fix: check that the folder exists and is writable by the server user, and that the disk has free space.");
            }
        }
        return file;
    }
}
