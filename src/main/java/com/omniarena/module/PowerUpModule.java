package com.omniarena.module;

import com.omniarena.OmniArena;
import com.omniarena.match.Match;
import com.omniarena.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class PowerUpModule {

    private final OmniArena plugin;
    private final Map<String, BukkitTask> spawnTasks = new HashMap<>();
    private final Map<UUID, Long> activeEffects    = new HashMap<>();

    public PowerUpModule(OmniArena plugin) {
        this.plugin = plugin;
    }

    public void startSpawning(Match match, List<Location> spawnPoints) {
        if (!match.getArena().hasModule("power-up") || spawnPoints.isEmpty()) return;

        BukkitTask previous = spawnTasks.remove(match.getArena().getName());
        if (previous != null) previous.cancel();

        BukkitTask task = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!match.getArena().isRunning()) return;
            Location loc = spawnPoints.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(spawnPoints.size()));
            spawnPowerUp(loc, match);
        }, 200L, 400L); // every 20s after first 10s

        spawnTasks.put(match.getArena().getName(), task);
    }

    public void stopSpawning(Match match) {
        BukkitTask task = spawnTasks.remove(match.getArena().getName());
        if (task != null) task.cancel();
    }

    public void cancelAll() {
        spawnTasks.values().forEach(BukkitTask::cancel);
        spawnTasks.clear();
        activeEffects.clear();
    }

    private void spawnPowerUp(Location loc, Match match) {
        if (loc == null || loc.getWorld() == null) return;
        PowerUpType type = PowerUpType.random();
        ItemStack item = new ItemStack(type.material());
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            com.omniarena.platform.Platform.get().setDisplayName(meta, com.omniarena.util.ColorUtil.parse(type.displayName()));
            item.setItemMeta(meta);
        }

        Item dropped = loc.getWorld().dropItem(loc, item);
        dropped.setCustomName(type.displayName());
        dropped.setCustomNameVisible(true);
        dropped.setPickupDelay(0);

        plugin.getVisual().getEffectManager().playBloodEffect(loc); // reuse sparkle particle
        plugin.getVisual().getParticleManager().spawnPowerUpEffect(loc);

        // Listen for pickup in this task cycle
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (dropped.isDead()) return;
            dropped.remove();
        }, 200L); // despawn after 10s if not picked up
    }

    public void onPickup(Player player, ItemStack item, Match match) {
        if (!match.getArena().hasModule("power-up")) return;
        String name = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(com.omniarena.platform.Platform.get().getDisplayName(item.getItemMeta())) : "";

        for (PowerUpType type : PowerUpType.values()) {
            if (name.contains(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().stripTags(type.displayName()))) {
                applyEffect(player, type);
                plugin.getVisual().getSoundManager().playPowerUpPickup(player);
                MessageUtil.send(player, "<gold>Power-Up! <yellow>" + type.displayName());
                return;
            }
        }
    }

    private void applyEffect(Player player, PowerUpType type) {
        player.addPotionEffect(new PotionEffect(type.effect(), type.durationTicks(), type.level()));
        long now = System.currentTimeMillis();
        activeEffects.values().removeIf(expiry -> expiry < now);
        activeEffects.put(player.getUniqueId(), System.currentTimeMillis() + (type.durationTicks() * 50L));
    }

    enum PowerUpType {
        SPEED("Speed Boost", Material.SUGAR, PotionEffectType.SPEED, 200, 1),
        STRENGTH("Strength", Material.BLAZE_POWDER, PotionEffectType.STRENGTH, 200, 1),
        REGENERATION("Regeneration", Material.GHAST_TEAR, PotionEffectType.REGENERATION, 200, 1),
        RESISTANCE("Resistance", Material.IRON_INGOT, PotionEffectType.RESISTANCE, 200, 0),
        JUMP_BOOST("Jump Boost", Material.RABBIT_FOOT, PotionEffectType.JUMP_BOOST, 200, 1);

        private final String displayName;
        private final Material material;
        private final PotionEffectType effect;
        private final int durationTicks;
        private final int level;

        PowerUpType(String displayName, Material material, PotionEffectType effect, int durationTicks, int level) {
            this.displayName = "<gold><bold>" + displayName + "</bold>";
            this.material = material;
            this.effect = effect;
            this.durationTicks = durationTicks;
            this.level = level;
        }

        public String displayName() { return displayName; }
        public Material material()  { return material; }
        public PotionEffectType effect() { return effect; }
        public int durationTicks()  { return durationTicks; }
        public int level()          { return level; }

        public static PowerUpType random() {
            PowerUpType[] values = values();
            return values[java.util.concurrent.ThreadLocalRandom.current().nextInt(values.length)];
        }
    }
}
