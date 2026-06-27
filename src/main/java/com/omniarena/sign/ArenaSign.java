package com.omniarena.sign;

import org.bukkit.Location;

public class ArenaSign {
    private final Location location;
    private final String arenaName;

    public ArenaSign(Location location, String arenaName) {
        this.location = location;
        this.arenaName = arenaName;
    }

    public Location getLocation() { return location; }
    public String getArenaName() { return arenaName; }

    public String getWorldName() {
        return location.getWorld() != null ? location.getWorld().getName() : "unknown";
    }

    public String getCoords() {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArenaSign)) return false;
        ArenaSign that = (ArenaSign) o;
        return java.util.Objects.equals(location, that.location)
                && java.util.Objects.equals(arenaName, that.arenaName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(location, arenaName);
    }

    @Override
    public String toString() {
        return "ArenaSign(location=" + location + ", arenaName=" + arenaName + ")";
    }
}
