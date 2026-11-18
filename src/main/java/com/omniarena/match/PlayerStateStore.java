package com.omniarena.match;

import com.omniarena.OmniArena;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Crash-safe copy of a player's pre-arena state: plugins/OmniArena/player-states/&lt;uuid&gt;.yml.
 * Snapshots are taken on the main thread and written/deleted on a single writer thread
 * (so a delete can never overtake the save it follows). If the server dies mid-match the
 * file survives and is applied on the player's next join.
 */
public class PlayerStateStore {

    private final OmniArena plugin;
    private final File dir;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OmniArena-PlayerStates");
        t.setDaemon(true);
        return t;
    });

    public PlayerStateStore(OmniArena plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "player-states");
    }

    private File file(UUID id) { return new File(dir, id + ".yml"); }

    public boolean exists(UUID id) { return file(id).isFile(); }

    private void submit(Runnable r) {
        try { io.execute(r); }
        catch (RejectedExecutionException e) { r.run(); }
    }

    /** Snapshots the player now (main thread) and persists it asynchronously. */
    public void save(Player p, boolean includeInventory) {
        final YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.set("location", p.getLocation().clone());
            cfg.set("gamemode", p.getGameMode().name());
            cfg.set("health", p.getHealth());
            cfg.set("food", p.getFoodLevel());
            cfg.set("saturation", (double) p.getSaturation());
            cfg.set("level", p.getLevel());
            cfg.set("exp", (double) p.getExp());
            cfg.set("effects", new ArrayList<>(p.getActivePotionEffects()));
            cfg.set("allow-flight", p.getAllowFlight());
            cfg.set("flying", p.isFlying());
            cfg.set("walk-speed", (double) p.getWalkSpeed());
            cfg.set("fly-speed", (double) p.getFlySpeed());
            cfg.set("invisible", p.isInvisible());
            if (includeInventory) {
                cfg.set("contents", cloneList(p.getInventory().getContents()));
                cfg.set("armor", cloneList(p.getInventory().getArmorContents()));
                ItemStack off = p.getInventory().getItemInOffHand();
                cfg.set("offhand", off == null ? null : off.clone());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not snapshot the inventory/state of " + p.getName() + " before the match (" + t + "). Their items may not be restored after the match; check for conflicting inventory plugins.");
            return;
        }
        final File f = file(p.getUniqueId());
        final String data = cfg.saveToString(); // serialization happens on the main thread: items are not shared with the writer
        submit(() -> {
            try {
                dir.mkdirs();
                File tmp = new File(dir, f.getName() + ".tmp");
                java.nio.file.Files.writeString(tmp.toPath(), data, java.nio.charset.StandardCharsets.UTF_8);
                java.nio.file.Files.move(tmp.toPath(), f.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save player state file " + f.getPath() + " (" + e + "). Fix: make sure the folder is writable and the disk is not full, otherwise this player's items cannot be restored after a crash.");
            }
        });
    }

    public void delete(UUID id) {
        final File f = file(id);
        submit(() -> { try { java.nio.file.Files.deleteIfExists(f.toPath()); } catch (Exception ignored) { } });
    }

    private static List<ItemStack> cloneList(ItemStack[] arr) {
        List<ItemStack> out = new ArrayList<>(arr.length);
        for (ItemStack i : arr) out.add(i == null ? null : i.clone());
        return out;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack[] toArray(List<?> list) {
        ItemStack[] out = new ItemStack[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i) instanceof ItemStack is ? is : null;
        return out;
    }

    /**
     * Applies a stored state to the player and deletes the file.
     * @return true if a state was found and applied
     */
    public boolean restore(Player p) {
        File f = file(p.getUniqueId());
        if (!f.isFile()) return false;
        YamlConfiguration cfg;
        try {
            cfg = YamlConfiguration.loadConfiguration(f);
        } catch (Throwable t) {
            plugin.getLogger().warning("Corrupt player state file " + f.getPath() + " (" + t + "). It was deleted and the player's saved inventory could not be restored. Fix: restore it from a backup if you need those items.");
            delete(p.getUniqueId());
            return false;
        }
        if (p.isDead()) { try { p.spigot().respawn(); } catch (Throwable ignored) { } }
        for (PotionEffect fx : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(fx.getType());
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

        Object loc = cfg.get("location");
        if (loc instanceof Location l && l.getWorld() != null) {
            MatchManager mm = MatchManager.getInstance();
            if (mm != null) mm.markInternalTeleport(p, l); else p.teleport(l);
        }
        if (cfg.contains("contents")) {
            p.getInventory().setContents(toArray(cfg.getList("contents", List.of())));
            p.getInventory().setArmorContents(toArray(cfg.getList("armor", List.of())));
            if (cfg.get("offhand") instanceof ItemStack off) p.getInventory().setItemInOffHand(off);
        }
        try { p.setGameMode(GameMode.valueOf(cfg.getString("gamemode", "SURVIVAL"))); }
        catch (IllegalArgumentException e) { p.setGameMode(GameMode.SURVIVAL); }
        boolean allow = cfg.getBoolean("allow-flight", false);
        p.setAllowFlight(allow);
        if (allow && cfg.getBoolean("flying", false)) p.setFlying(true);
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.max(0.5, Math.min(max, cfg.getDouble("health", max))));
        p.setFoodLevel(cfg.getInt("food", 20));
        p.setSaturation((float) cfg.getDouble("saturation", 5.0));
        p.setLevel(cfg.getInt("level", 0));
        p.setExp((float) Math.max(0, Math.min(1, cfg.getDouble("exp", 0))));
        for (Object o : cfg.getList("effects", List.of())) if (o instanceof PotionEffect fx) p.addPotionEffect(fx);
        p.setWalkSpeed((float) cfg.getDouble("walk-speed", 0.2));
        p.setFlySpeed((float) cfg.getDouble("fly-speed", 0.1));
        p.setInvisible(cfg.getBoolean("invisible", false));
        delete(p.getUniqueId());
        return true;
    }

    /** Waits for pending writes/deletes (call on plugin disable). */
    public void shutdown() {
        io.shutdown();
        try { io.awaitTermination(10, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
