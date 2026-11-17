package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.match.MatchState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Optional;

public class EntityDamageListener implements Listener {

    private final OmniArena plugin;

    public EntityDamageListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.isStopped()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        MatchManager mm = MatchManager.getInstance();
        if (!mm.isInArena(victim)) return;

        Optional<String> arenaName = mm.getArenaName(victim);
        if (arenaName.isEmpty()) return;

        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(arenaName.get());
        if (arenaOpt.isEmpty()) return;

        Arena arena = arenaOpt.get();
        Optional<Match> matchOpt = mm.getMatch(arena);
        if (matchOpt.isEmpty() || matchOpt.get().getState() != MatchState.RUNNING) {
            event.setCancelled(true);
            return;
        }

        Match match = matchOpt.get();

        if (mm.isGodMode(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Spawn protection
        if (match.hasSpawnProtection(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Rule checks
        switch (event.getCause()) {
            case FALL       -> { if (!arena.getRules().isFallDamage())   event.setCancelled(true); }
            case FIRE, FIRE_TICK, LAVA -> { if (!arena.getRules().isFireDamage()) event.setCancelled(true); }
            case DROWNING   -> { if (!arena.getRules().isDrowning())     event.setCancelled(true); }
            case SUFFOCATION -> { if (!arena.getRules().isSuffocation()) event.setCancelled(true); }
            default -> {}
        }

        // Blood effect on hit
        if (!event.isCancelled()) {
            plugin.getVisual().getEffectManager().playBloodEffect(victim.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (plugin.isStopped()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker) &&
            !(event.getDamager() instanceof org.bukkit.entity.Projectile)) return;

        MatchManager mm = MatchManager.getInstance();
        if (!mm.isInArena(victim)) return;

        Optional<String> arenaName = mm.getArenaName(victim);
        if (arenaName.isEmpty()) return;

        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(arenaName.get());
        if (arenaOpt.isEmpty()) return;

        Arena arena = arenaOpt.get();
        Optional<Match> matchOpt = mm.getMatch(arena);
        if (matchOpt.isEmpty() || matchOpt.get().getState() != MatchState.RUNNING) {
            event.setCancelled(true);
            return;
        }

        Match match = matchOpt.get();

        if (mm.isGodMode(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Friendly fire check
        if (!arena.getRules().isFriendlyFire() && arena.getTeamConfig().isEnabled()) {
            Player realAttacker = null;
            if (event.getDamager() instanceof Player) realAttacker = (Player) event.getDamager();
            else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj &&
                     proj.getShooter() instanceof Player) realAttacker = (Player) proj.getShooter();

            if (realAttacker != null) {
                var victimTeam   = match.getTeam(victim.getUniqueId());
                var attackerTeam = match.getTeam(realAttacker.getUniqueId());
                if (victimTeam != null && victimTeam.equals(attackerTeam)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Spawn protection on victim
        if (match.hasSpawnProtection(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
