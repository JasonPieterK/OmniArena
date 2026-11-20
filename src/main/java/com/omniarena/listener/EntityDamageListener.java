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
        com.omniarena.health.ErrorGuard.run("listener-damage", () -> onDamageGuarded(event));
    }

    private void onDamageGuarded(EntityDamageEvent event) {
        if (plugin.isStopped()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        MatchManager mm = MatchManager.getInstance();
        if (!mm.isInArena(victim)) return;

        Optional<String> arenaName = mm.getArenaName(victim);
        if (arenaName.isEmpty()) return;

        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(arenaName.get());
        if (arenaOpt.isEmpty()) return;

        Arena arena = arenaOpt.get();
        // Spectators are untouchable
        if (mm.isSpectating(victim)) {
            event.setCancelled(true);
            return;
        }
        Optional<Match> matchOpt = mm.getMatch(arena);
        if (matchOpt.isEmpty() || matchOpt.get().getState() != MatchState.RUNNING) {
            event.setCancelled(true);
            // Falling into the void in the lobby / end phase would otherwise loop forever
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && arena.getLobbySpawn() != null) {
                victim.setFallDistance(0f);
                mm.markInternalTeleport(victim, arena.getLobbySpawn());
            }
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
        com.omniarena.health.ErrorGuard.run("listener-pvp", () -> onPvPGuarded(event));
    }

    private void onPvPGuarded(EntityDamageByEntityEvent event) {
        if (plugin.isStopped()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        MatchManager mm = MatchManager.getInstance();
        // Player, projectile shooter, pet owner, TNT igniter, potion-cloud thrower
        Player shooter = com.omniarena.combat.CombatListener.resolveSource(event.getDamager());

        // Arena fighters can't hurt outsiders, and outsiders/other matches/spectators can't hurt them
        if (shooter != null && !shooter.equals(victim)) {
            Optional<String> va = mm.getArenaName(victim);
            Optional<String> sa = mm.getArenaName(shooter);
            if (!va.equals(sa) || mm.isSpectating(shooter)) {
                if (va.isPresent() || sa.isPresent()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
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
            Player realAttacker = shooter;

            // Self-inflicted damage (own arrow/TNT) is never friendly fire
            if (realAttacker != null && !realAttacker.equals(victim)) {
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
