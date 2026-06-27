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
    private final Map<String, int[]> killLedger = new ConcurrentHashMap<>();

    private volatile boolean globalPvp = true;

    public CombatManager(OmniArena plugin) {
        this.plugin = plugin;
        instance = this;
        this.globalPvp = cfg().getConfig().getBoolean("pvp.global.pvp-enabled", true);
        startTagExpiryTask();
    }

    public static CombatManager getInstance() { return instance; }

    private static ConfigManager cfg() { return ConfigManager.getInstance(); }

    // ── Combat tagging ────────────────────────────────────────────────────────

    public void tag(Player player, Player attacker) {
        long dur = cfg().getConfig().getInt("pvp.combat.tag-duration", 15) * 1000L;
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
        if (until < System.currentTimeMillis()) { combatTags.remove(uuid); return false; }
        return true;
    }

    public boolean isTagged(Player player) { return isTagged(player.getUniqueId()); }

    public int getTagSeconds(UUID uuid) {
        Long until = combatTags.get(uuid);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    public void untag(Player player) {
        if (combatTags.remove(player.getUniqueId()) != null) {
            MessageUtil.send(player, cfg().getMessage("combat-untagged"));
        }
    }

    public UUID getLastAttacker(UUID uuid) { return lastAttacker.get(uuid); }

    /** Disables fly / flight / invis-eligible state the instant combat begins. */
    private void applyCombatRestrictions(Player player) {
        if (cfg().getConfig().getBoolean("pvp.combat.disable-fly", true) && player.isFlying()) {
            player.setFlying(false);
            player.setAllowFlight(false);
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
        cfg().setValue("pvp.global.pvp-enabled", on);
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
    }

    public void grantNewbie(UUID uuid, int seconds) {
        newbieUntil.put(uuid, System.currentTimeMillis() + seconds * 1000L);
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

    public void removeNewbie(UUID uuid) { newbieUntil.remove(uuid); }

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
        int windowMs = cfg().getConfig().getInt("pvp.killabuse.time-window", 300) * 1000;
        String key = killer + ":" + victim;
        long now = System.currentTimeMillis();
        int[] entry = killLedger.get(key);
        if (entry == null || now - entry[1] > windowMs) {
            killLedger.put(key, new int[]{1, (int) (now / 1000)});
            return false;
        }
        entry[0]++;
        entry[1] = (int) (now / 1000);
        return entry[0] > max;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void cleanup() {
        long now = System.currentTimeMillis();
        combatTags.entrySet().removeIf(e -> e.getValue() < now);
        newbieUntil.entrySet().removeIf(e -> e.getValue() < now);
        killLedger.clear();
    }

    public int trackedPlayers() {
        return combatTags.size() + newbieUntil.size() + pvpToggle.size();
    }

    private void startTagExpiryTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            combatTags.entrySet().removeIf(e -> {
                if (e.getValue() >= now) return false;
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) MessageUtil.send(p, cfg().getMessage("combat-expired"));
                return true;
            });
        }, 20L, 20L);
    }
}
