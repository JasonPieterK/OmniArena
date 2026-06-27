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

public class HologramManager {

    private final OmniArena plugin;
    private final Map<String, List<ArmorStand>> holograms = new HashMap<>();

    public HologramManager(OmniArena plugin) {
        this.plugin = plugin;
    }

    public void createLeaderboard(String id, Location base) {
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
        if (old != null) old.forEach(ArmorStand::remove);
    }

    public void removeAll() {
        holograms.values().forEach(list -> list.forEach(ArmorStand::remove));
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
        stands.add(stand);
    }
}
