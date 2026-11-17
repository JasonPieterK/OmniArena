package com.omniarena.kit;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import com.omniarena.util.ItemUtil;
import com.omniarena.util.YamlUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KitManager {

    private static KitManager instance;

    private final OmniArena plugin;
    private final File adminKitsDir;
    private final File playerKitsDir;
    private final Map<String, AdminKit> adminKits  = new ConcurrentHashMap<>();
    private final Map<UUID, List<PlayerKit>> playerKits = new ConcurrentHashMap<>();

    public KitManager(OmniArena plugin) {
        this.plugin = plugin;
        this.adminKitsDir  = new File(plugin.getDataFolder(), "admin-kits");
        this.playerKitsDir = new File(plugin.getDataFolder(), "player-kits");
        if (!adminKitsDir.exists())  adminKitsDir.mkdirs();
        if (!playerKitsDir.exists()) playerKitsDir.mkdirs();
        instance = this;
    }

    public static KitManager getInstance() { return instance; }

    // ── Load ──────────────────────────────────────────────────────

    public void loadAll() {
        loadDefaultKits();
        loadAdminKits();
        plugin.getLogger().info("Loaded " + adminKits.size() + " admin kit(s).");
    }

    private void loadAdminKits() {
        adminKits.clear();
        File[] files = adminKitsDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            try {
                AdminKit kit = loadAdminKitFile(f);
                adminKits.put(kit.getName().toLowerCase(), kit);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load admin kit: " + f.getName());
            }
        }
    }

    private void loadDefaultKits() {
        String[] defaults = {"warrior", "archer", "tank", "assassin", "mage",
                "healer", "pyro", "ninja", "juggernaut", "gunner"};
        for (String name : defaults) {
            File target = new File(adminKitsDir, name + ".yml");
            if (target.exists()) continue;
            // Copy directly from the plugin jar's resource stream into admin-kits/
            try (java.io.InputStream in = plugin.getResource("kits/" + name + ".yml")) {
                if (in == null) {
                    plugin.getLogger().warning("Default kit resource not found: kits/" + name + ".yml");
                    continue;
                }
                java.nio.file.Files.copy(in, target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to install default kit '" + name + "': " + e.getMessage());
            }
        }
    }

    /** Drops the cached kits of a player (call on quit); they reload from disk on demand. */
    public void evict(UUID playerId) {
        if (playerId != null) playerKits.remove(playerId);
    }

    public List<PlayerKit> getPlayerKits(UUID playerId) {
        return playerKitsDir.exists()
                ? loadPlayerKitsFromDisk(playerId)
                : new ArrayList<>();
    }

    private List<PlayerKit> loadPlayerKitsFromDisk(UUID playerId) {
        return playerKits.computeIfAbsent(playerId, id -> {
            File file = new File(playerKitsDir, id + ".yml");
            if (!file.exists()) return new ArrayList<>();
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<PlayerKit> kits = new ArrayList<>();
            if (cfg.contains("kits")) {
                for (String name : cfg.getConfigurationSection("kits").getKeys(false)) {
                    try {
                        PlayerKit kit = loadPlayerKitSection(cfg, "kits." + name, id, name);
                        kits.add(kit);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to load player kit " + name + " for " + id);
                    }
                }
            }
            return kits;
        });
    }

    // ── Admin Kit CRUD ────────────────────────────────────────────

    public AdminKit createAdminKit(String name, ItemStack[] contents, ItemStack[] armor) {
        AdminKit kit = new AdminKit(name);
        kit.setContents(contents.clone());
        kit.setArmor(armor.clone());
        kit.setEffects(new ArrayList<>());
        kit.setIcon(findIcon(contents));
        adminKits.put(name.toLowerCase(), kit);
        saveAdminKit(kit);
        return kit;
    }

    public boolean deleteAdminKit(String name) {
        AdminKit removed = adminKits.remove(name.toLowerCase());
        if (removed != null) {
            new File(adminKitsDir, name.toLowerCase() + ".yml").delete();
            return true;
        }
        return false;
    }

    public Optional<AdminKit> getAdminKit(String name) {
        return Optional.ofNullable(adminKits.get(name.toLowerCase()));
    }

    public Collection<AdminKit> getAllAdminKits() {
        return Collections.unmodifiableCollection(adminKits.values());
    }

    public List<AdminKit> getPublicAdminKits() {
        return adminKits.values().stream().filter(AdminKit::isPublic).toList();
    }

    public void setAdminKitPublic(String name, boolean pub) {
        getAdminKit(name).ifPresent(k -> { k.setPublic(pub); saveAdminKit(k, false); });
    }

    public void setAdminKitIcon(String name, Material material) {
        getAdminKit(name).ifPresent(k -> {
            k.setIcon(new ItemStack(material));
            saveAdminKit(k, false);
        });
    }

    public void setAdminKitDescription(String name, String desc) {
        getAdminKit(name).ifPresent(k -> { k.setDescription(desc); saveAdminKit(k, false); });
    }

    public void setAdminKitCooldown(String name, int seconds) {
        getAdminKit(name).ifPresent(k -> { k.setCooldownSeconds(seconds); saveAdminKit(k, false); });
    }

    public void setAdminKitPermission(String name, String node) {
        getAdminKit(name).ifPresent(k -> { k.setRequiredPermission(node); saveAdminKit(k, false); });
    }

    // ── Player Kit CRUD ───────────────────────────────────────────

    public boolean createPlayerKit(UUID playerId, String name,
                                   ItemStack[] contents, ItemStack[] armor,
                                   int slotLimit) {
        List<PlayerKit> kits = getPlayerKits(playerId);
        if (kits.size() >= slotLimit) return false;
        if (kits.stream().anyMatch(k -> k.getName().equalsIgnoreCase(name))) return false;

        PlayerKit kit = new PlayerKit(name, playerId);
        kit.setContents(sanitize(contents));
        kit.setArmor(sanitize(armor));
        kit.setEffects(new ArrayList<>());
        kit.setIcon(findIcon(contents));
        kits.add(kit);
        savePlayerKits(playerId, kits);
        return true;
    }

    public boolean deletePlayerKit(UUID playerId, String name) {
        List<PlayerKit> kits = getPlayerKits(playerId);
        boolean removed = kits.removeIf(k -> k.getName().equalsIgnoreCase(name));
        if (removed) savePlayerKits(playerId, kits);
        return removed;
    }

    public Optional<PlayerKit> getPlayerKit(UUID playerId, String name) {
        return getPlayerKits(playerId).stream()
                .filter(k -> k.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public boolean renamePlayerKit(UUID playerId, String oldName, String newName) {
        List<PlayerKit> kits = getPlayerKits(playerId);
        for (PlayerKit kit : kits) {
            if (kit.getName().equalsIgnoreCase(oldName)) {
                kit.setName(newName);
                kit.setDisplayName(newName);
                savePlayerKits(playerId, kits);
                return true;
            }
        }
        return false;
    }

    // ── Equip ─────────────────────────────────────────────────────

    public void equipKit(org.bukkit.entity.Player player, Kit kit) {
        player.getInventory().clear();
        if (kit.getContents() != null) player.getInventory().setContents(kit.getContents().clone());
        if (kit.getArmor() != null)    player.getInventory().setArmorContents(kit.getArmor().clone());
        if (kit.getOffHand() != null)  player.getInventory().setItemInOffHand(kit.getOffHand().clone());
        if (kit.getEffects() != null) {
            for (PotionEffect effect : kit.getEffects()) {
                player.addPotionEffect(effect);
            }
        }
    }

    // ── Save ─────────────────────────────────────────────────────

    private void saveAdminKit(AdminKit kit) { saveAdminKit(kit, true); }

    /**
     * @param writeItems false = metadata-only update that keeps the existing file's
     *                   items/armor/effects sections (structured defaults would otherwise
     *                   be rewritten in legacy format, silently dropping their effects).
     */
    private void saveAdminKit(AdminKit kit, boolean writeItems) {
        File file = new File(adminKitsDir, kit.getName().toLowerCase() + ".yml");
        if (!writeItems && !file.exists()) writeItems = true;
        FileConfiguration cfg = writeItems ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(file);
        cfg.set("name", kit.getName());
        cfg.set("public", kit.isPublic());
        cfg.set("description", kit.getDescription());
        cfg.set("icon", kit.getIcon() != null ? kit.getIcon().getType().name() : "CHEST");
        cfg.set("cooldown", kit.getCooldownSeconds());
        cfg.set("permission", kit.getRequiredPermission());
        cfg.set("arena", kit.getArenaRestriction());
        if (!writeItems) { YamlUtil.save(cfg, file, plugin.getLogger()); return; }
        // Arrays.asList: List.of() throws NPE on the null (empty) slots
        cfg.set("contents", kit.getContents() != null ? Arrays.asList(kit.getContents()) : List.of());
        cfg.set("armor", kit.getArmor() != null ? Arrays.asList(kit.getArmor()) : List.of());
        YamlUtil.save(cfg, file, plugin.getLogger());
    }

    private void savePlayerKits(UUID playerId, List<PlayerKit> kits) {
        playerKits.put(playerId, kits);
        File file = new File(playerKitsDir, playerId + ".yml");
        FileConfiguration cfg = new YamlConfiguration();
        for (PlayerKit kit : kits) {
            String path = "kits." + kit.getName().toLowerCase().replace('.', '_');
            cfg.set(path + ".name", kit.getName());
            cfg.set(path + ".contents", kit.getContents() != null ? Arrays.asList(kit.getContents()) : List.of());
            cfg.set(path + ".armor", kit.getArmor() != null ? Arrays.asList(kit.getArmor()) : List.of());
        }
        YamlUtil.save(cfg, file, plugin.getLogger());
    }

    private AdminKit loadAdminKitFile(File file) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String fallbackName = file.getName().replace(".yml", "");
        return com.omniarena.util.KitYamlLoader.loadAdminKit(cfg, fallbackName);
    }

    private PlayerKit loadPlayerKitSection(FileConfiguration cfg, String path, UUID id, String name) {
        PlayerKit kit = new PlayerKit(cfg.getString(path + ".name", name), id);
        List<?> contents = cfg.getList(path + ".contents");
        if (contents != null) {
            kit.setContents(contents.stream()
                    .map(o -> o instanceof ItemStack ? (ItemStack) o : null)
                    .toArray(ItemStack[]::new));
        }
        List<?> armor = cfg.getList(path + ".armor");
        if (armor != null) {
            kit.setArmor(armor.stream()
                    .map(o -> o instanceof ItemStack ? (ItemStack) o : null)
                    .toArray(ItemStack[]::new));
        }
        kit.setEffects(new ArrayList<>());
        return kit;
    }

    // ── Power cap ─────────────────────────────────────────────────

    private ItemStack[] sanitize(ItemStack[] items) {
        if (items == null) return new ItemStack[0];
        boolean capEnabled = ConfigManager.getInstance().getConfig()
                .getBoolean("kits.power-cap.enabled", true);
        int maxLevel = ConfigManager.getInstance().getConfig()
                .getInt("kits.power-cap.max-enchant-level", 5);

        ItemStack[] result = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null) { result[i] = null; continue; }
            if (!ItemUtil.isSafe(item)) { result[i] = null; continue; }
            item = item.clone(); // never mutate the source kit / live inventory item
            if (capEnabled) ItemUtil.capEnchants(item, maxLevel);
            result[i] = item;
        }
        return result;
    }

    private ItemStack findIcon(ItemStack[] contents) {
        if (contents == null) return new ItemStack(Material.CHEST);
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) return item.clone();
        }
        return new ItemStack(Material.CHEST);
    }
}
