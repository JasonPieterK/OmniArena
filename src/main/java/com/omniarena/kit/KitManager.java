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
import org.bukkit.entity.Player;

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
    /** key "uuid|kitname" -> epoch millis when the cooldown ends */
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

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
        loadCooldowns();
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
                plugin.getLogger().warning("Failed to load admin kit: " + f.getName() + " (" + e + ")");
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

    /** Safe kit names: no path separators / MiniMessage tags / GUI-prefix colon. */
    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_.\\-]{1,32}") && !name.contains("..");
    }

    private static String fileName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-]", "_") + ".yml";
    }

    private static ItemStack[] deepCopy(ItemStack[] items, int length) {
        ItemStack[] out = new ItemStack[length];
        if (items == null) return out;
        for (int i = 0; i < Math.min(items.length, length); i++) {
            out[i] = items[i] == null || items[i].getType() == Material.AIR ? null : items[i].clone();
        }
        return out;
    }

    /** Player#getContents() has 41 entries (36 storage + 4 armor + offhand); offhand is index 40. */
    private static ItemStack offHandOf(ItemStack[] contents) {
        if (contents == null || contents.length <= 40) return null;
        ItemStack o = contents[40];
        return o == null || o.getType() == Material.AIR ? null : o.clone();
    }

    public AdminKit createAdminKit(String name, ItemStack[] contents, ItemStack[] armor) {
        AdminKit previous = adminKits.get(name.toLowerCase());
        AdminKit kit = new AdminKit(name);
        kit.setContents(deepCopy(contents, 36));
        kit.setArmor(deepCopy(armor, 4));
        kit.setOffHand(offHandOf(contents));
        kit.setEffects(new ArrayList<>());
        kit.setIcon(findIcon(kit.getContents()));
        if (previous != null) { // re-saving an existing kit keeps its settings
            kit.setPublic(previous.isPublic());
            kit.setDescription(previous.getDescription());
            kit.setCooldownSeconds(previous.getCooldownSeconds());
            kit.setRequiredPermission(previous.getRequiredPermission());
            kit.setArenaRestriction(previous.getArenaRestriction());
            kit.setEffects(previous.getEffects() != null ? previous.getEffects() : new ArrayList<>());
        }
        adminKits.put(name.toLowerCase(), kit);
        saveAdminKit(kit);
        return kit;
    }

    public boolean deleteAdminKit(String name) {
        AdminKit removed = adminKits.remove(name.toLowerCase());
        if (removed != null) {
            new File(adminKitsDir, fileName(removed.getName())).delete();
            new File(adminKitsDir, fileName(name)).delete();
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
        return createPlayerKit(playerId, name, contents, armor, null, slotLimit);
    }

    public boolean createPlayerKit(UUID playerId, String name,
                                   ItemStack[] contents, ItemStack[] armor,
                                   ItemStack offHand, int slotLimit) {
        if (!isValidName(name)) return false;
        List<PlayerKit> kits = getPlayerKits(playerId);
        if (kits.size() >= slotLimit) return false;
        if (kits.stream().anyMatch(k -> k.getName().equalsIgnoreCase(name))) return false;

        ItemStack off = offHand != null ? offHand : offHandOf(contents);
        PlayerKit kit = new PlayerKit(name, playerId);
        kit.setContents(sanitize(deepCopy(contents, 36)));
        kit.setArmor(sanitize(deepCopy(armor, 4)));
        ItemStack[] offArr = sanitize(new ItemStack[]{off});
        kit.setOffHand(offArr[0]);
        kit.setEffects(new ArrayList<>());
        kit.setIcon(findIcon(kit.getContents()));
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
        if (!isValidName(newName)) return false;
        List<PlayerKit> kits = getPlayerKits(playerId);
        for (PlayerKit k : kits) {
            if (k.getName().equalsIgnoreCase(newName) && !k.getName().equalsIgnoreCase(oldName)) return false;
        }
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

    public void equipKit(Player player, Kit kit) {
        player.getInventory().clear(); // storage, armor and offhand
        for (PotionEffect active : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(active.getType());
        }
        if (kit.getContents() != null) {
            ItemStack[] c = deepCopy(kit.getContents(), 36);
            for (int i = 0; i < c.length; i++) player.getInventory().setItem(i, c[i]);
        }
        if (kit.getArmor() != null)    player.getInventory().setArmorContents(deepCopy(kit.getArmor(), 4));
        if (kit.getOffHand() != null)  player.getInventory().setItemInOffHand(kit.getOffHand().clone());
        if (kit.getEffects() != null) {
            for (PotionEffect effect : kit.getEffects()) {
                player.addPotionEffect(effect);
            }
        }
        player.updateInventory();
    }

    /**
     * Player-initiated equip (GUI / /kit equip): enforces lobby-only, permission and cooldown.
     * @return true when the kit was equipped
     */
    public boolean tryEquip(Player player, Kit kit) {
        ConfigManager cm = ConfigManager.getInstance();
        com.omniarena.match.MatchManager mm = com.omniarena.match.MatchManager.getInstance();
        if (mm != null && mm.isInArena(player)) {
            boolean lobby = false;
            if (!mm.isSpectating(player)) {
                lobby = mm.getArenaName(player)
                        .flatMap(n -> com.omniarena.arena.ArenaManager.getInstance().get(n))
                        .flatMap(mm::getMatch)
                        .map(m -> m.getState() == com.omniarena.match.MatchState.WAITING
                                || m.getState() == com.omniarena.match.MatchState.STARTING)
                        .orElse(false);
            }
            if (!lobby) {
                com.omniarena.util.MessageUtil.send(player, cm.getMessage("kit-lobby-only"));
                return false;
            }
        }
        if (kit instanceof AdminKit admin
                && !com.omniarena.permission.PermissionChecker.canUseKit(player, admin.getRequiredPermission())) {
            com.omniarena.util.MessageUtil.send(player, cm.getMessage("kit-no-permission")
                    .replace("{kit}", kit.getName()));
            return false;
        }
        boolean bypass = player.hasPermission("omniarena.admin");
        if (kit instanceof AdminKit && kit.getCooldownSeconds() > 0 && !bypass) {
            long left = cooldownLeftSeconds(player.getUniqueId(), kit);
            if (left > 0) {
                com.omniarena.util.MessageUtil.send(player, cm.getMessage("kit-cooldown")
                        .replace("{seconds}", String.valueOf(left)));
                return false;
            }
            cooldowns.put(player.getUniqueId() + "|" + kit.getName().toLowerCase(),
                    System.currentTimeMillis() + kit.getCooldownSeconds() * 1000L);
            saveCooldowns();
        }
        equipKit(player, kit);
        return true;
    }

    private long cooldownLeftSeconds(UUID id, Kit kit) {
        Long until = cooldowns.get(id + "|" + kit.getName().toLowerCase());
        if (until == null) return 0;
        long ms = until - System.currentTimeMillis();
        return ms <= 0 ? 0 : (ms + 999) / 1000;
    }

    private void loadCooldowns() {
        cooldowns.clear();
        File file = new File(playerKitsDir.getParentFile(), "kit-cooldowns.yml");
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        long now = System.currentTimeMillis();
        for (String key : cfg.getKeys(false)) {
            long until = cfg.getLong(key);
            if (until > now) cooldowns.put(key.replace('~', '.'), until);
        }
    }

    private void saveCooldowns() {
        File file = new File(playerKitsDir.getParentFile(), "kit-cooldowns.yml");
        FileConfiguration cfg = new YamlConfiguration();
        long now = System.currentTimeMillis();
        cooldowns.forEach((k, v) -> { if (v > now) cfg.set(k.replace('.', '~'), v); });
        YamlUtil.save(cfg, file, plugin.getLogger());
    }

    // ── Save ─────────────────────────────────────────────────────

    private void saveAdminKit(AdminKit kit) { saveAdminKit(kit, true); }

    /**
     * @param writeItems false = metadata-only update that keeps the existing file's
     *                   items/armor/effects sections (structured defaults would otherwise
     *                   be rewritten in legacy format, silently dropping their effects).
     */
    private void saveAdminKit(AdminKit kit, boolean writeItems) {
        File file = new File(adminKitsDir, fileName(kit.getName()));
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
        cfg.set("offhand", kit.getOffHand());
        YamlUtil.save(cfg, file, plugin.getLogger());
    }

    private void savePlayerKits(UUID playerId, List<PlayerKit> kits) {
        playerKits.put(playerId, kits);
        File file = new File(playerKitsDir, playerId + ".yml");
        FileConfiguration cfg = new YamlConfiguration();
        int idx = 0;
        for (PlayerKit kit : kits) {
            String path = "kits.k" + (idx++);
            cfg.set(path + ".name", kit.getName());
            cfg.set(path + ".contents", kit.getContents() != null ? Arrays.asList(kit.getContents()) : List.of());
            cfg.set(path + ".armor", kit.getArmor() != null ? Arrays.asList(kit.getArmor()) : List.of());
            cfg.set(path + ".offhand", kit.getOffHand());
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
            ItemStack[] all = contents.stream()
                    .map(o -> o instanceof ItemStack ? (ItemStack) o : null)
                    .toArray(ItemStack[]::new);
            kit.setContents(deepCopy(all, 36));
            kit.setOffHand(offHandOf(all)); // older saves stored the full 41-slot array
        }
        List<?> armor = cfg.getList(path + ".armor");
        if (armor != null) {
            kit.setArmor(armor.stream()
                    .map(o -> o instanceof ItemStack ? (ItemStack) o : null)
                    .toArray(ItemStack[]::new));
        }
        Object off = cfg.get(path + ".offhand");
        if (off instanceof ItemStack o) kit.setOffHand(o);
        kit.setEffects(new ArrayList<>());
        kit.setIcon(findIcon(kit.getContents()));
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
