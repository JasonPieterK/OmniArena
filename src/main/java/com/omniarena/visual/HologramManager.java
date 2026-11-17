package com.omniarena.visual;

import com.omniarena.OmniArena;
import com.omniarena.stats.PlayerStats;
import com.omniarena.stats.StatsManager;
import com.omniarena.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HologramManager implements org.bukkit.event.Listener {

    private final OmniArena plugin;
    private final Map<String, List<ArmorStand>> holograms = new HashMap<>();

    private final org.bukkit.NamespacedKey tagKey;

    public HologramManager(OmniArena plugin) {
        this.plugin = plugin;
        this.tagKey = new org.bukkit.NamespacedKey(plugin, "hologram");
    }

    /** Registers the chunk-load sweep and removes leftovers from a previous run/crash in loaded chunks. */
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
            for (org.bukkit.Chunk c : w.getLoadedChunks()) removeStale(c);
        }
    }

    /** Removes OmniArena-tagged armor stands that this manager no longer tracks (stale after restart/crash). */
    private void removeStale(org.bukkit.Chunk chunk) {
        for (org.bukkit.entity.Entity e : chunk.getEntities()) {
            if (e instanceof ArmorStand as
                    && as.getPersistentDataContainer().has(tagKey, org.bukkit.persistence.PersistentDataType.BYTE)
                    && !isTracked(as)) {
                as.remove();
            }
        }
    }

    private boolean isTracked(ArmorStand as) {
        for (List<ArmorStand> l : holograms.values()) if (l.contains(as)) return true;
        return false;
    }

    @org.bukkit.event.EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        if (event.isNewChunk()) return;
        removeStale(event.getChunk());
    }

    public void createLeaderboard(String id, Location base) {
        if (base == null || base.getWorld() == null) return;
        // Never force-load a chunk just to spawn a hologram
        if (!base.getWorld().isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) return;
        removeHologram(id);
        List<ArmorStand> stands = new ArrayList<>();

        List<PlayerStats> top = StatsManager.getInstance().getTopKills(10);
        double yOffset = top.size() * 0.3 + 0.5;

        spawnLine(base, yOffset, "<gradient:#ff6b35:#f7c59f><bold>TOP KILLS</bold></gradient>", id, stands);
        yOffset -= 0.35;

        for (int i = 0; i < top.size(); i++) {
            PlayerStats s = top.get(i);
            String rank = switch (i) {
                case 0 -> "<gold>#1";
                case 1 -> "<gray>#2";
                case 2 -> "<dark_red>#3";
                default -> "<dark_gray>#" + (i + 1);
            };
            String line = rank + " <white>" + s.getPlayerName() + " <gray>- <yellow>" + s.getKills() + " kills";
            spawnLine(base, yOffset, line, id, stands);
            yOffset -= 0.3;
        }

        holograms.put(id, stands);
    }

    public void removeHologram(String id) {
        List<ArmorStand> old = holograms.remove(id);
        if (old != null) old.forEach(a -> { if (a.isValid()) a.remove(); });
    }

    public void removeAll() {
        holograms.values().forEach(list -> list.forEach(a -> { if (a.isValid()) a.remove(); }));
        holograms.clear();
    }

    public void updateLeaderboard(String id, Location base) {
        removeHologram(id);
        createLeaderboard(id, base);
    }

    private void spawnLine(Location base, double yOffset, String text, String id, List<ArmorStand> stands) {
        Location loc = base.clone().add(0, yOffset, 0);
        ArmorStand stand = (ArmorStand) base.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCanPickupItems(false);
        stand.setCustomNameVisible(true);
        stand.customName(ColorUtil.parse(text));
        stand.setSmall(true);
        stand.setPersistent(false);
        stand.getPersistentDataContainer().set(tagKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        stands.add(stand);
    }
}
