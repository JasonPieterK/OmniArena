package com.omniarena.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public final class LocationUtil {

    private LocationUtil() {}

    public static void saveLocation(ConfigurationSection section, String key, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        ConfigurationSection s = section.createSection(key);
        s.set("world", loc.getWorld().getName());
        s.set("x", loc.getX());
        s.set("y", loc.getY());
        s.set("z", loc.getZ());
        s.set("yaw", loc.getYaw());
        s.set("pitch", loc.getPitch());
    }

    public static Location loadLocation(ConfigurationSection section, String key) {
        ConfigurationSection s = section.getConfigurationSection(key);
        if (s == null) return null;
        String worldName = s.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public static String format(Location loc) {
        if (loc == null) return "none";
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
        return world + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public static String serialize(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + ":" + loc.getX() + ":" + loc.getY() + ":" + loc.getZ()
                + ":" + loc.getYaw() + ":" + loc.getPitch();
    }

    public static Location deserialize(String s) {
        if (s == null) return null;
        String[] parts = s.split(":");
        if (parts.length < 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw   = parts.length > 4 ? Float.parseFloat(parts[4]) : 0;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
