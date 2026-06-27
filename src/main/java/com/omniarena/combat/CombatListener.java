package com.omniarena.combat;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.MatchManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * All server-wide PvP / combat behaviour, PvPManager-style. Reads its state from
 * {@link CombatManager} and its tunables from config. Arena matches are left to
 * the arena engine — combat rules here only apply outside an active arena.
 */
public class CombatListener implements Listener {

    private final OmniArena plugin;

    public CombatListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    private ConfigManager cfg() { return ConfigManager.getInstance(); }
    private CombatManager cm() { return CombatManager.getInstance(); }
    private boolean enabled() { return cfg().getConfig().getBoolean("pvp.enabled", true); }
    private boolean inArena(Player p) { return MatchManager.getInstance().isInArena(p); }

    // ── Damage: gate PvP + apply tag + blood ───────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) return;

        // Arena combat is governed by the arena engine, not this subsystem.
        if (inArena(victim) || inArena(attacker)) return;

        if (cm().isRespawnProtected(victim.getUniqueId()) && !cm().isOverriding(attacker)) { deny(event, attacker, "pvp-respawn-protected"); return; }
        if (!cm().isGlobalPvp()) { deny(event, attacker, "pvp-global-off"); return; }
        if (cm().isWorldDisabled(attacker)) { deny(event, attacker, "pvp-world-off"); return; }
        if (cm().isNewbie(victim.getUniqueId()) && !cm().isOverriding(attacker)) { deny(event, attacker, "pvp-newbie-victim"); return; }
        if (cm().isNewbie(attacker.getUniqueId())) {
            if (cfg().getConfig().getBoolean("pvp.newbie.disable-on-attack", true)) cm().removeNewbie(attacker.getUniqueId());
            else { deny(event, attacker, "pvp-newbie-self"); return; }
        }
        if (!cm().isOverriding(attacker)) {
            if (!cm().isPvpEnabled(victim))   { deny(event, attacker, "pvp-victim-disabled"); return; }
            if (!cm().isPvpEnabled(attacker)) { deny(event, attacker, "pvp-self-disabled"); return; }
        }

        // Valid PvP hit — tag both and show blood.
        cm().tag(victim, attacker);
        cm().tag(attacker, victim);
        if (cfg().getConfig().getBoolean("pvp.blood.enabled", true)) spawnBlood(victim);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private void deny(EntityDamageByEntityEvent event, Player attacker, String msgKey) {
        event.setCancelled(true);
        MessageUtil.send(attacker, cfg().getMessage(msgKey));
    }

    private void spawnBlood(Player victim) {
        String matName = cfg().getConfig().getString("pvp.blood.block", "REDSTONE_BLOCK");
        Material mat;
        try { mat = Material.valueOf(matName.toUpperCase()); } catch (Exception e) { mat = Material.REDSTONE_BLOCK; }
        victim.getWorld().spawnParticle(Particle.BLOCK,
                victim.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, mat.createBlockData());
    }

    // ── Combat-log punishment ───────────────────────────────────────────────────
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || !cm().isTagged(player)) return;
        if (!cfg().getConfig().getBoolean("pvp.combat.punish-combat-log", true)) return;

        String action = cfg().getConfig().getString("pvp.combat.combat-log-action", "KILL").toUpperCase();
        if (action.equals("KILL")) {
            player.setHealth(0.0); // dies on quit; drops handled by death event
        } else if (action.equals("COMMAND")) {
            for (String c : cfg().getConfig().getStringList("pvp.combat.combat-log-commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("{player}", player.getName()));
            }
        }
        if (cfg().getConfig().getBoolean("pvp.combat.broadcast-combat-log", true)) {
            Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                    cfg().getMessage("combat-log").replace("{player}", player.getName())));
        }
        cm().untag(player);
    }

    // ── Block commands while tagged ──────────────────────────────────────────────
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || !cm().isTagged(player)) return;
        if (!cfg().getConfig().getBoolean("pvp.combat.block-commands", true)) return;

        String msg = event.getMessage().toLowerCase();
        List<String> allowed = cfg().getConfig().getStringList("pvp.combat.allowed-commands");
        boolean ok = allowed.stream().anyMatch(a -> msg.startsWith(a.toLowerCase()));
        if (!ok) {
            event.setCancelled(true);
            MessageUtil.send(player, cfg().getMessage("combat-no-command"));
        }
    }

    // ── Block teleport / enderpearls while tagged ────────────────────────────────
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || !cm().isTagged(player)) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && !cfg().getConfig().getBoolean("pvp.combat.block-enderpearls", true)) return;
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || (cause == PlayerTeleportEvent.TeleportCause.COMMAND
                    && cfg().getConfig().getBoolean("pvp.combat.cancel-teleport", true))) {
            event.setCancelled(true);
            MessageUtil.send(player, cfg().getMessage("combat-no-teleport"));
        }
    }

    // ── Block placement while tagged + auto-soup ─────────────────────────────────
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || !cm().isTagged(player)) return;
        if (cfg().getConfig().getBoolean("pvp.combat.block-block-place", true)) {
            event.setCancelled(true);
            MessageUtil.send(player, cfg().getMessage("combat-no-place"));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled() || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null) return;

        if (!cfg().getConfig().getBoolean("pvp.auto-soup.enabled", false)) return;
        String soup = cfg().getConfig().getString("pvp.auto-soup.soup-material", "MUSHROOM_STEW");
        if (!item.getType().name().equalsIgnoreCase(soup)) return;

        double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        if (player.getHealth() >= max) return;
        double heal = cfg().getConfig().getDouble("pvp.auto-soup.heal-amount", 3.5);
        player.setHealth(Math.min(max, player.getHealth() + heal));
        item.setType(Material.BOWL);
        event.setCancelled(true);
    }

    // ── Anti border-hopping (WorldGuard safe zones) ──────────────────────────────
    @EventHandler(ignoreCancelled = true)
    public void onBorderHop(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        // Only react when the block actually changes — cheap guard.
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        Player player = event.getPlayer();
        if (!enabled() || !cm().isTagged(player)) return;
        if (!cfg().getConfig().getBoolean("pvp.border-hopping.prevent", true)) return;

        var wg = plugin.getIntegrationManager() != null ? plugin.getIntegrationManager().getWorldGuard() : null;
        if (wg == null) return;

        // Blocked only when stepping INTO a safe zone from a non-safe spot.
        if (wg.isPvpDenied(event.getTo()) && !wg.isPvpDenied(event.getFrom())) {
            event.setTo(event.getFrom().clone());
            MessageUtil.send(player, cfg().getMessage("combat-no-safezone"));
        }
    }

    // ── Block lava / fire near PvP-disabled players ──────────────────────────────
    @EventHandler(ignoreCancelled = true)
    public void onLavaBucket(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        if (!enabled() || !cfg().getConfig().getBoolean("pvp.block-lava-fire-near-disabled", true)) return;
        if (event.getBucket() != Material.LAVA_BUCKET) return;
        if (protectedNearby(event.getBlockClicked().getRelative(event.getBlockFace()).getLocation(), event.getPlayer())) {
            event.setCancelled(true);
            MessageUtil.send(event.getPlayer(), cfg().getMessage("combat-no-lavafire"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(org.bukkit.event.block.BlockIgniteEvent event) {
        if (!enabled() || !cfg().getConfig().getBoolean("pvp.block-lava-fire-near-disabled", true)) return;
        Player igniter = event.getPlayer();
        if (igniter == null) return;
        if (protectedNearby(event.getBlock().getLocation(), igniter)) {
            event.setCancelled(true);
            MessageUtil.send(igniter, cfg().getMessage("combat-no-lavafire"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceHazard(BlockPlaceEvent event) {
        if (!enabled() || !cfg().getConfig().getBoolean("pvp.block-lava-fire-near-disabled", true)) return;
        Material m = event.getBlockPlaced().getType();
        if (m != Material.LAVA && m != Material.FIRE && m != Material.SOUL_FIRE
                && m != Material.CAMPFIRE && m != Material.SOUL_CAMPFIRE && m != Material.LAVA_BUCKET) return;
        if (protectedNearby(event.getBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
            MessageUtil.send(event.getPlayer(), cfg().getMessage("combat-no-lavafire"));
        }
    }

    /** True if any *other* PvP-protected player is within the configured radius. */
    private boolean protectedNearby(org.bukkit.Location loc, Player placer) {
        double r = cfg().getConfig().getDouble("pvp.lava-fire-radius", 5.0);
        double r2 = r * r;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.equals(placer)) continue;
            if (p.getLocation().distanceSquared(loc) <= r2 && cm().isProtected(p)) return true;
        }
        return false;
    }

    // ── Newbie protection on first join ──────────────────────────────────────────
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled()) return;
        Player player = event.getPlayer();
        if (cfg().getConfig().getBoolean("pvp.newbie.enabled", true) && !player.hasPlayedBefore()) {
            cm().grantNewbie(player);
            MessageUtil.send(player, cfg().getMessage("newbie-granted"));
        }
    }

    // ── Respawn protection ───────────────────────────────────────────────────────
    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        if (!enabled() || inArena(event.getPlayer())) return;
        cm().grantRespawnProtection(event.getPlayer());
    }

    // Drop protection the moment the player swings — no abusing the shield to attack.
    @EventHandler(ignoreCancelled = true)
    public void onProtectedAttack(EntityDamageByEntityEvent event) {
        if (!(resolveAttacker(event) instanceof Player attacker)) return;
        if (cm().isRespawnProtected(attacker.getUniqueId())) cm().clearRespawnProtection(attacker.getUniqueId());
    }

    // ── Transfer drops to killer + kill-abuse check ──────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled()) return;
        Player victim = event.getEntity();
        if (inArena(victim)) return; // arena death handled elsewhere
        Player killer = victim.getKiller();
        cm().untag(victim);

        if (killer != null) {
            cm().registerKillAndCheckAbuse(killer.getUniqueId(), victim.getUniqueId());
            if (cfg().getConfig().getBoolean("pvp.transfer-drops", false) && !event.getDrops().isEmpty()) {
                for (ItemStack drop : event.getDrops()) {
                    killer.getInventory().addItem(drop).values()
                            .forEach(left -> killer.getWorld().dropItemNaturally(killer.getLocation(), left));
                }
                event.getDrops().clear();
            }
        }
    }
}
