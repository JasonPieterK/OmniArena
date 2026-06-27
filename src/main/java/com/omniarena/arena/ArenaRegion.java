package com.omniarena.arena;

import org.bukkit.Location;

public class ArenaRegion {
    private String worldName;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;

    public ArenaRegion() {}

    public ArenaRegion(Location loc1, Location loc2) {
        if (loc1.getWorld() == null) throw new IllegalArgumentException("World cannot be null");
        this.worldName = loc1.getWorld().getName();
        this.minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        this.minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        this.minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        this.maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        this.maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        this.maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());
    }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
    public int getMinX() { return minX; }
    public void setMinX(int minX) { this.minX = minX; }
    public int getMinY() { return minY; }
    public void setMinY(int minY) { this.minY = minY; }
    public int getMinZ() { return minZ; }
    public void setMinZ(int minZ) { this.minZ = minZ; }
    public int getMaxX() { return maxX; }
    public void setMaxX(int maxX) { this.maxX = maxX; }
    public int getMaxY() { return maxY; }
    public void setMaxY(int maxY) { this.maxY = maxY; }
    public int getMaxZ() { return maxZ; }
    public void setMaxZ(int maxZ) { this.maxZ = maxZ; }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean isDefined() {
        return worldName != null && !worldName.isBlank();
    }

    public String getDisplayString() {
        return worldName + " (" + minX + "," + minY + "," + minZ + ") -> (" + maxX + "," + maxY + "," + maxZ + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArenaRegion)) return false;
        ArenaRegion that = (ArenaRegion) o;
        return minX == that.minX && minY == that.minY && minZ == that.minZ
                && maxX == that.maxX && maxY == that.maxY && maxZ == that.maxZ
                && java.util.Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(worldName, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public String toString() {
        return "ArenaRegion(worldName=" + worldName + ", min=" + minX + "," + minY + "," + minZ
                + ", max=" + maxX + "," + maxY + "," + maxZ + ")";
    }
}
