package com.omniarena.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.World;

public class WorldGuardIntegration {

    /**
     * True when WorldGuard's PVP flag resolves to DENY at this location — i.e.
     * the spot is a "safe zone". Used by anti-border-hopping so a combat-tagged
     * player can't sprint into a no-PvP region to escape.
     */
    public boolean isPvpDenied(Location loc) {
        try {
            if (loc == null || loc.getWorld() == null) return false;
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            StateFlag.State state = query.queryState(BukkitAdapter.adapt(loc), (com.sk89q.worldguard.protection.association.RegionAssociable) null, Flags.PVP);
            return state == StateFlag.State.DENY;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isInRegion(Location loc, String regionId) {
        try {
            World world = loc.getWorld();
            if (world == null) return false;
            RegionManager rm = WorldGuard.getInstance()
                    .getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
            if (rm == null) return false;
            ProtectedRegion region = rm.getRegion(regionId);
            if (region == null) return false;
            com.sk89q.worldedit.math.BlockVector3 vec = BukkitAdapter.adapt(loc).toVector().toBlockPoint();
            return region.contains(vec);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean regionExists(World world, String regionId) {
        try {
            RegionManager rm = WorldGuard.getInstance()
                    .getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
            return rm != null && rm.hasRegion(regionId);
        } catch (Exception e) {
            return false;
        }
    }
}
