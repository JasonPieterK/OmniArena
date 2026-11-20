package com.omniarena.combat;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide combat / PvP state, PvPManager-style.
 *
 * Tracks combat tags (with expiry), per-player PvP toggle, the global PvP flag,
 * newbie protection windows, attack-override, and the anti-kill-abuse ledger.
 * Listeners and commands read/write through this single hub.
 */
public class CombatManager {

    private static CombatManager instance;

    private final OmniArena plugin;

    // player -> tag expiry (epoch millis)
    private final Map<UUID, Long> combatTags = new ConcurrentHashMap<>();
    // who last hit this player (for combat-log drop transfer / messaging)
    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();
    // players who toggled their own PvP OFF
    private final Map<UUID, Boolean> pvpToggle = new ConcurrentHashMap<>();
    // newbie -> protection expiry (epoch millis)
    private final Map<UUID, Long> newbieUntil = new ConcurrentHashMap<>();
    // player -> respawn-protection expiry (epoch millis)
    private final Map<UUID, Long> respawnProtUntil = new ConcurrentHashMap<>();
    // players allowed to attack anywhere (admin override)
    private final Map<UUID, Boolean> override = new ConcurrentHashMap<>();
    // anti kill-abuse: "killerUUID:victimUUID" -> recent kill count
    private final Map<String, long[]> killLedger = new ConcurrentHashMap<>();

    private volatile boolean globalPvp = true;

    public CombatManager(OmniArena plugin) {
        this.plugin = plugin;
        instance = this;
        this.globalPvp = cfg().getConfig().getBoolean("pvp.server-pvp", true);
        loadNewbies();
        loadToggles();
        start();
    }

    public static CombatManager getInstance() { return instance; }

    private static ConfigManager cfg() { return ConfigManager.getInstance(); }

    // ── Combat tagging ────────────────────────────────────────────────────────

    public void tag(Player player, Player attacker) {
        tag(player, attacker, cfg().getConfig().getInt("pvp.combat.tag-duration", 15));
    }

    public void tag(Player player, Player attacker, int seconds) {
        // Arena combat is governed by the arena engine: never tag fighters/spectators.
        var mm = com.omniarena.match.MatchManager.getInstance();
        if (mm != null && mm.isInArena(player)) return;
        long dur = Math.max(1, seconds) * 1000L;
        boolean wasTagged = isTagged(player);
        combatTags.put(player.getUniqueId(), System.currentTimeMillis() + dur);
        if (attacker != null) lastAttacker.put(player.getUniqueId(), attacker.getUniqueId());

        if (!wasTagged) {
            MessageUtil.send(player, cfg().getMessage("combat-tagged"));
            applyCombatRestrictions(player);
        }
    }

    public boolean isTagged(UUID uuid) {
        Long until = combatTags.get(uuid);
        if (until == null) return false;
        return until >= System.currentTimeMillis();
    }

    public boolean isTagged(Player player) { return isTagged(player.getUniqueId()); }

    public int getTagSeconds(UUID uuid) {
        Long until = combatTags.get(uuid);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    public void untag(Player player) {
        lastAttacker.remove(player.getUniqueId());
        if (combatTags.remove(player.getUniqueId()) != null) {
            MessageUtil.send(player, cfg().getMessage("combat-untagged"));
        }
    }

    /** Silently drops combat state (no messages): used when a player enters an arena or leaves the server. */
    public void clearCombat(UUID uuid) {
        combatTags.remove(uuid);
        lastAttacker.remove(uuid);
    }

    /** Drops every per-player transient entry when a player quits (newbie timer and PvP toggle are kept). */
    public void handleQuit(UUID uuid) {
        combatTags.remove(uuid);
        lastAttacker.remove(uuid);
        respawnProtUntil.remove(uuid);
        override.remove(uuid);
        lastToggle.remove(uuid);
    }

    public UUID getLastAttacker(UUID uuid) { return lastAttacker.get(uuid); }

    /** Disables fly / flight / invis-eligible state the instant combat begins. */
    private void applyCombatRestrictions(Player player) {
        if (cfg().getConfig().getBoolean("pvp.combat.disable-fly", true) && player.isFlying()) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if (cfg().getConfig().getBoolean("pvp.combat.block-elytra", true) && player.isGliding()) {
            player.setGliding(false);
        }
        if (cfg().getConfig().getBoolean("pvp.combat.disable-gamemode", true)
                && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        if (cfg().getConfig().getBoolean("pvp.combat.disable-invisibility", true)) {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
            player.setInvisible(false);
        }
        if (cfg().getConfig().getBoolean("pvp.combat.disable-disguise", true)) {
            removeDisguise(player);
        }
    }

    /** Undisguises via LibsDisguises if it's installed — reflection keeps it a soft dep. */
    private void removeDisguise(Player player) {
        if (Bukkit.getPluginManager().getPlugin("LibsDisguises") == null) return;
        try {
            Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            boolean disguised = (boolean) api.getMethod("isDisguised", org.bukkit.entity.Entity.class).invoke(null, player);
            if (disguised) api.getMethod("undisguiseToAll", org.bukkit.entity.Entity.class).invoke(null, player);
        } catch (Throwable ignored) {
            // Different LibsDisguises version or API shape — fail silently.
        }
    }

    // ── PvP toggle (per player) ───────────────────────────────────────────────

    public boolean isPvpEnabled(Player player) {
        if (isOverriding(player)) return true;
        boolean def = cfg().getConfig().getBoolean("pvp.toggle.default-state", true);
        return pvpToggle.getOrDefault(player.getUniqueId(), def);
    }

    public void setPvp(Player player, boolean enabled) {
        pvpToggle.put(player.getUniqueId(), enabled);
        saveTogglesAsync();
    }

    // PvP-off players survive restarts (plugins/OmniArena/pvp-toggles.yml); only "off" players are stored
    private java.io.File toggleFile() { return new java.io.File(plugin.getDataFolder(), "pvp-toggles.yml"); }

    private void loadToggles() {
        try {
            java.io.File f = toggleFile();
            if (!f.exists()) return;
            org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            for (String k : y.getStringList("pvp-off")) {
                try { pvpToggle.put(UUID.fromString(k), false); } catch (IllegalArgumentException ignored) { }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load pvp-toggles.yml: " + e.getMessage());
        }
    }

    private void saveTogglesAsync() {
        if (!plugin.isEnabled()) { saveTogglesNow(); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveTogglesNow);
    }

    private synchronized void saveTogglesNow() {
        try {
            java.util.List<String> off = new java.util.ArrayList<>();
            for (Map.Entry<UUID, Boolean> e : pvpToggle.entrySet()) {
                if (Boolean.FALSE.equals(e.getValue())) off.add(e.getKey().toString());
            }
            org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
            y.set("pvp-off", off);
            java.io.File f = toggleFile();
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            y.save(f);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save pvp-toggles.yml: " + e.getMessage());
        }
    }

    // ── End crystal attribution ───────────────────────────────────────────────

    private static final long CRYSTAL_TTL_MS = 10_000L;
    // crystal entity -> {last player to damage it, expiry}
    private final Map<UUID, Object[]> crystalDamagers = new ConcurrentHashMap<>();

    public void recordCrystalDamager(UUID crystal, UUID player) {
        crystalDamagers.put(crystal, new Object[]{player, System.currentTimeMillis() + CRYSTAL_TTL_MS});
    }

    /** Player who last damaged this crystal within ~10s, or null. */
    public UUID getCrystalDamager(UUID crystal) {
        Object[] e = crystalDamagers.get(crystal);
        if (e == null) return null;
        if ((Long) e[1] < System.currentTimeMillis()) { crystalDamagers.remove(crystal); return null; }
        return (UUID) e[0];
    }

    public void clearCrystal(UUID crystal) { crystalDamagers.remove(crystal); }

    private final java.util.Map<UUID, Long> lastToggle = new java.util.concurrent.ConcurrentHashMap<>();

    /** Seconds left before this player may self-toggle PvP again (pvp.toggle.cooldown); 0 = ready. */
    public int toggleCooldownLeft(Player player) {
        int cd = cfg().getConfig().getInt("pvp.toggle.cooldown", 5);
        Long last = lastToggle.get(player.getUniqueId());
        if (cd <= 0 || last == null) return 0;
        long left = last + cd * 1000L - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    /** Self-service toggle honouring pvp.toggle.enabled / cooldown; null result = refused (message sent). */
    public Boolean selfTogglePvp(Player player) {
        if (!cfg().getConfig().getBoolean("pvp.toggle.enabled", true)) {
            MessageUtil.send(player, "<red>PvP toggling is disabled on this server.");
            return null;
        }
        if (isTagged(player)) {
            MessageUtil.send(player, "<red>You can't toggle PvP while in combat.");
            return null;
        }
        int left = toggleCooldownLeft(player);
        if (left > 0) {
            MessageUtil.send(player, "<red>Wait " + left + "s before toggling PvP again.");
            return null;
        }
        lastToggle.put(player.getUniqueId(), System.currentTimeMillis());
        return togglePvp(player);
    }

    public boolean togglePvp(Player player) {
        boolean now = !isPvpEnabled(player);
        setPvp(player, now);
        return now;
    }

    /** A player who should be shielded from environmental griefing (lava/fire). */
    public boolean isProtected(Player player) {
        return !isPvpEnabled(player) || isNewbie(player.getUniqueId()) || isRespawnProtected(player.getUniqueId());
    }

    // ── Global PvP ────────────────────────────────────────────────────────────

    public boolean isGlobalPvp() { return globalPvp; }
    public void setGlobalPvp(boolean on) {
        this.globalPvp = on;
        cfg().setValue("pvp.server-pvp", on);
    }

    // ── World check ───────────────────────────────────────────────────────────

    public boolean isWorldDisabled(Player player) {
        return cfg().getConfig().getStringList("pvp.disabled-worlds")
                .stream().anyMatch(w -> w.equalsIgnoreCase(player.getWorld().getName()));
    }

    // ── Newbie protection ─────────────────────────────────────────────────────

    public void grantNewbie(Player player) {
        if (!cfg().getConfig().getBoolean("pvp.newbie.enabled", true)) return;
        long dur = cfg().getConfig().getInt("pvp.newbie.duration", 300) * 1000L;
        newbieUntil.put(player.getUniqueId(), System.currentTimeMillis() + dur);
        saveNewbiesAsync();
    }

    public void grantNewbie(UUID uuid, int seconds) {
        newbieUntil.put(uuid, System.currentTimeMillis() + Math.max(1, seconds) * 1000L);
        saveNewbiesAsync();
    }

    public boolean isNewbie(UUID uuid) {
        Long until = newbieUntil.get(uuid);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) { newbieUntil.remove(uuid); return false; }
        return true;
    }

    public int getNewbieSeconds(UUID uuid) {
        Long until = newbieUntil.get(uuid);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    public void removeNewbie(UUID uuid) {
        if (newbieUntil.remove(uuid) != null) saveNewbiesAsync();
    }

    // newbie windows survive restarts (plugins/OmniArena/newbie.yml); saved async, inline on shutdown
    private java.io.File newbieFile() { return new java.io.File(plugin.getDataFolder(), "newbie.yml"); }

    private void loadNewbies() {
        try {
            java.io.File f = newbieFile();
            if (!f.exists()) return;
            org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            long now = System.currentTimeMillis();
            for (String k : y.getKeys(false)) {
                long until = y.getLong(k);
                if (until > now) {
                    try { newbieUntil.put(UUID.fromString(k), until); } catch (IllegalArgumentException ignored) { }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load newbie.yml: " + e.getMessage());
        }
    }

    private void saveNewbiesAsync() {
        if (!plugin.isEnabled()) { saveNewbiesNow(); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveNewbiesNow);
    }

    private synchronized void saveNewbiesNow() {
        try {
            org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> e : newbieUntil.entrySet()) {
                if (e.getValue() > now) y.set(e.getKey().toString(), e.getValue());
            }
            java.io.File f = newbieFile();
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            y.save(f);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save newbie.yml: " + e.getMessage());
        }
    }

    // ── Respawn protection ────────────────────────────────────────────────────

    public void grantRespawnProtection(Player player) {
        if (!cfg().getConfig().getBoolean("pvp.respawn-protection.enabled", true)) return;
        long dur = cfg().getConfig().getInt("pvp.respawn-protection.duration", 5) * 1000L;
        respawnProtUntil.put(player.getUniqueId(), System.currentTimeMillis() + dur);
    }

    public boolean isRespawnProtected(UUID uuid) {
        Long until = respawnProtUntil.get(uuid);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) { respawnProtUntil.remove(uuid); return false; }
        return true;
    }

    public void clearRespawnProtection(UUID uuid) { respawnProtUntil.remove(uuid); }

    // ── Attack override ───────────────────────────────────────────────────────

    public boolean isOverriding(Player player) {
        return override.getOrDefault(player.getUniqueId(), false);
    }
    public boolean toggleOverride(Player player) {
        boolean now = !isOverriding(player);
        override.put(player.getUniqueId(), now);
        return now;
    }

    // ── Anti kill-abuse ───────────────────────────────────────────────────────

    /** @return true if this kill should be suppressed (rewards/stats) as abuse. */
    public boolean registerKillAndCheckAbuse(UUID killer, UUID victim) {
        if (!cfg().getConfig().getBoolean("pvp.killabuse.enabled", true)) return false;
        int max = cfg().getConfig().getInt("pvp.killabuse.max-kills", 3);
        long windowMs = cfg().getConfig().getInt("pvp.killabuse.time-window", 300) * 1000L;
        String key = killer + ":" + victim;
        long now = System.currentTimeMillis();
        long[] entry = killLedger.get(key);
        if (entry == null || now - entry[1] > windowMs) {
            killLedger.put(key, new long[]{1, now});
            return false;
        }
        entry[0]++;
        return entry[0] > max;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void cleanup() {
        long now = System.currentTimeMillis();
        combatTags.entrySet().removeIf(e -> e.getValue() < now);
        newbieUntil.entrySet().removeIf(e -> e.getValue() < now);
        long windowMs = cfg().getConfig().getInt("pvp.killabuse.time-window", 300) * 1000L;
        killLedger.entrySet().removeIf(e -> now - e.getValue()[1] > windowMs);
        respawnProtUntil.entrySet().removeIf(e -> e.getValue() < now);
        crystalDamagers.entrySet().removeIf(e -> (Long) e.getValue()[1] < now);
    }

    public int trackedPlayers() {
        return combatTags.size() + newbieUntil.size() + pvpToggle.size();
    }

    private org.bukkit.scheduler.BukkitTask tagExpiryTask;

    /** (Re)starts the tag-expiry loop; idempotent (cancels any previous task first). */
    public synchronized void start() {
        if (tagExpiryTask != null) { tagExpiryTask.cancel(); tagExpiryTask = null; }
        tagExpiryTask = Bukkit.getScheduler().runTaskTimer(plugin, com.omniarena.health.ErrorGuard.wrap("combat", () -> {
            if (combatTags.isEmpty()) return; // idle: nothing to expire
            long now = System.currentTimeMillis();
            combatTags.entrySet().removeIf(e -> {
                if (e.getValue() >= now) return false;
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) MessageUtil.send(p, cfg().getMessage("combat-expired"));
                lastAttacker.remove(e.getKey());
                return true;
            });
        }), 20L, 20L);
    }

    public boolean isRunning() {
        return tagExpiryTask != null && !tagExpiryTask.isCancelled();
    }

    public synchronized void stop() {
        if (tagExpiryTask != null) { tagExpiryTask.cancel(); tagExpiryTask = null; }
    }

    /** Plugin disable: stop the task and write newbie data inline. */
    public void shutdown() {
        stop();
        saveNewbiesNow();
        saveTogglesNow();
    }
}
