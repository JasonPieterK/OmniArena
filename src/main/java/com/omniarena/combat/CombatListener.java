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
        boolean override = cm().isOverriding(attacker);
        if (!override && !cm().isGlobalPvp()) { deny(event, attacker, "pvp-global-off"); return; }
        if (!override && (cm().isWorldDisabled(attacker) || cm().isWorldDisabled(victim))) { deny(event, attacker, "pvp-world-off"); return; }
        if (cm().isNewbie(victim.getUniqueId()) && !override) { deny(event, attacker, "pvp-newbie-victim"); return; }
        boolean attackerNewbie = cm().isNewbie(attacker.getUniqueId());
        boolean dropNewbie = cfg().getConfig().getBoolean("pvp.newbie.disable-on-attack", true);
        if (attackerNewbie && !dropNewbie && !override) { deny(event, attacker, "pvp-newbie-self"); return; }
        if (!override) {
            if (!cm().isPvpEnabled(victim))   { deny(event, attacker, "pvp-victim-disabled"); return; }
            if (!cm().isPvpEnabled(attacker)) { deny(event, attacker, "pvp-self-disabled"); return; }
        }
        // Only a hit that actually lands ends the attacker's own newbie shield.
        if (attackerNewbie && dropNewbie) cm().removeNewbie(attacker.getUniqueId());

        // Valid PvP hit — tag both and show blood.
        if (cfg().getConfig().getBoolean("pvp.combat.tag-on-damage", true)) cm().tag(victim, attacker);
        if (cfg().getConfig().getBoolean("pvp.combat.tag-on-attack", true)) cm().tag(attacker, victim);
        if (cfg().getConfig().getBoolean("pvp.blood.enabled", true)) spawnBlood(victim);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof org.bukkit.entity.EnderCrystal crystal) {
            java.util.UUID id = cm().getCrystalDamager(crystal.getUniqueId());
            return id == null ? null : Bukkit.getPlayer(id);
        }
        return resolveSource(event.getDamager());
    }

    // ── End crystal attribution: remember who hit the crystal ───────────────────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.EnderCrystal crystal)) return;
        Player source = resolveSource(event.getDamager());
        if (source != null) cm().recordCrystalDamager(crystal.getUniqueId(), source.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCrystalExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.EnderCrystal crystal)) return;
        java.util.UUID id = crystal.getUniqueId();
        // explosion damage may be applied after this event: clear next tick
        Bukkit.getScheduler().runTask(plugin, () -> cm().clearCrystal(id));
    }

    /** Credits the human behind a damager: shooter, tamed-pet owner, TNT igniter, lingering-cloud thrower. */
    public static Player resolveSource(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        if (damager instanceof org.bukkit.entity.Tameable t && t.isTamed() && t.getOwner() instanceof Player p) return p;
        if (damager instanceof org.bukkit.entity.TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        if (damager instanceof org.bukkit.entity.AreaEffectCloud c && c.getSource() instanceof Player p) return p;
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
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        boolean tagged = enabled() && cm().isTagged(player);
        // Shutdown, plugin/admin kicks and arena players are never combat-logs.
        boolean exempt = Bukkit.isStopping() || plugin.isStopped()
                || event.getReason() == PlayerQuitEvent.QuitReason.KICKED
                || inArena(player);
        if (!tagged || exempt
                || !cfg().getConfig().getBoolean("pvp.combat.punish-combat-log", true)) {
            cm().handleQuit(player.getUniqueId());
            return;
        }

        String action = cfg().getConfig().getString("pvp.combat.combat-log-action", "KILL").toUpperCase();
        if (action.equals("KILL")) {
            if (!cfg().getConfig().getBoolean("pvp.combat.drop-on-combat-log", true)) noDropDeaths.add(player.getUniqueId());
            player.setHealth(0.0); // dies on quit; drops handled by death event
            noDropDeaths.remove(player.getUniqueId());
        } else if (action.equals("COMMAND")) {
            for (String c : cfg().getConfig().getStringList("pvp.combat.combat-log-commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("{player}", player.getName()));
            }
        }
        if (cfg().getConfig().getBoolean("pvp.combat.broadcast-combat-log", true)) {
            Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                    cfg().getMessage("combat-log").replace("{player}", player.getName())));
        }
        cm().handleQuit(player.getUniqueId());
    }

    /** Combat-loggers killed on quit whose inventory must not drop (pvp.combat.drop-on-combat-log: false). */
    private final java.util.Set<java.util.UUID> noDropDeaths = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @EventHandler(priority = EventPriority.HIGH)
    public void onCombatLogDeath(PlayerDeathEvent event) {
        if (!noDropDeaths.remove(event.getEntity().getUniqueId())) return;
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    // ── Newbie protection: no item pickup ────────────────────────────────────────
    @EventHandler(ignoreCancelled = true)
    public void onNewbiePickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!enabled() || inArena(p)) return;
        if (cfg().getConfig().getBoolean("pvp.newbie.block-pickup", false) && cm().isNewbie(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── Block commands while tagged ──────────────────────────────────────────────
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || !cm().isTagged(player)) return;
        if (!cfg().getConfig().getBoolean("pvp.combat.block-commands", true)) return;

        String msg = event.getMessage().trim();
        String label = (msg.startsWith("/") ? msg.substring(1) : msg).split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        // namespaced form (/minecraft:tp) is judged by its bare label
        String bare = label.contains(":") ? label.substring(label.indexOf(':') + 1) : label;
        java.util.Set<String> names = new java.util.HashSet<>(List.of(label, bare));
        var cmd = Bukkit.getCommandMap().getCommand(bare);
        if (cmd != null) names.add(cmd.getName().toLowerCase(java.util.Locale.ROOT));
        boolean ok = cfg().getConfig().getStringList("pvp.combat.allowed-commands").stream()
                .map(a -> a.trim().toLowerCase(java.util.Locale.ROOT))
                .map(a -> a.startsWith("/") ? a.substring(1) : a)
                .anyMatch(names::contains);
        if (!ok) {
            event.setCancelled(true);
            MessageUtil.send(player, cfg().getMessage("combat-no-command"));
        }
    }

    // ── Block teleport / enderpearls / elytra while tagged ───────────────────────
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || !cm().isTagged(player)) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        // OmniArena's own teleports (and other plugins' API teleports) are never blocked.
        if (cause == PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        boolean block = (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                    && cfg().getConfig().getBoolean("pvp.combat.block-ender-pearl", false))
                || (cause == PlayerTeleportEvent.TeleportCause.COMMAND
                    && cfg().getConfig().getBoolean("pvp.combat.block-teleport", true));
        if (block) {
            event.setCancelled(true);
            MessageUtil.send(player, "<red>You can't teleport while in combat. <gray>(" + cm().getTagSeconds(player.getUniqueId()) + "s left)");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isGliding()) return;
        if (!enabled() || !cm().isTagged(player) || inArena(player)) return;
        if (!cfg().getConfig().getBoolean("pvp.combat.block-elytra", true)) return;
        event.setCancelled(true);
        MessageUtil.send(player, "<red>You can't use an elytra while in combat. <gray>(" + cm().getTagSeconds(player.getUniqueId()) + "s left)");
    }

    @EventHandler(ignoreCancelled = true)
    public void onElytraBoost(com.destroystokyo.paper.event.player.PlayerElytraBoostEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || !cm().isTagged(player) || inArena(player)) return;
        if (!cfg().getConfig().getBoolean("pvp.combat.block-elytra", true)) return;
        event.setCancelled(true);
        MessageUtil.send(player, "<red>You can't use an elytra while in combat. <gray>(" + cm().getTagSeconds(player.getUniqueId()) + "s left)");
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
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProtectedAttack(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event);
        if (attacker == null || !(event.getEntity() instanceof Player)) return;
        if (cm().isRespawnProtected(attacker.getUniqueId())) cm().clearRespawnProtection(attacker.getUniqueId());
    }

    // ── Transfer drops to killer + kill-abuse check ──────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled()) return;
        Player victim = event.getEntity();
        if (inArena(victim)) return; // arena death handled elsewhere
        Player killer = victim.getKiller();
        if (killer == null && victim.getLastDamageCause() instanceof EntityDamageByEntityEvent last) {
            killer = resolveAttacker(last);
            if (victim.equals(killer)) killer = null;
        }
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
